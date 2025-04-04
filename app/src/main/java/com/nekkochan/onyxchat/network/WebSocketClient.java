package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nekkochan.onyxchat.util.UserSessionManager;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * WebSocket client for real-time messaging
 */
public class WebSocketClient {
    private static final String TAG = "WebSocketClient";
    
    // Default WebSocket endpoint
    private static final String DEFAULT_WS_ENDPOINT = "wss://10.0.2.2:443/ws/";
    
    // Connection parameters
    private static final int RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_DELAY_MS = 2000; // 2 seconds
    
    private final OkHttpClient client;
    private final String wsEndpoint;
    private WebSocket webSocket;
    private final List<MessageListener> listeners = new ArrayList<>();
    private WebSocketState state = WebSocketState.DISCONNECTED;
    private final Gson gson = new Gson();
    
    // Connection tracking
    private String currentUserId;
    private int reconnectAttempts = 0;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    
    // Cooldown tracking to prevent excessive reconnection attempts
    private long lastConnectAttemptTime = 0;
    private static final long RECONNECT_COOLDOWN_MS = 5000; // 5 seconds between connection attempts
    private static final int MAX_CONSECUTIVE_ATTEMPTS = 3;
    private int consecutiveAttempts = 0;
    
    private final UserSessionManager sessionManager;
    private final SharedPreferences sharedPreferences;
    
    // Add this as a class field
    private ScheduledExecutorService heartbeatExecutor;
    
    /**
     * Enumeration for WebSocket connection states
     */
    public enum WebSocketState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
    
    /**
     * Create a new WebSocket client with the default endpoint
     */
    public WebSocketClient(Context context) {
        this.sessionManager = new UserSessionManager(context);
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Get server URL from preferences
        String serverUrl = sharedPreferences.getString("server_url", DEFAULT_WS_ENDPOINT);
        this.wsEndpoint = serverUrl;
        
        // Configure OkHttp client
        client = getUnsafeOkHttpClient();
    }
    
    /**
     * Create a new WebSocket client with a custom endpoint
     */
    public WebSocketClient(String wsEndpoint, Context context) {
        this.sessionManager = new UserSessionManager(context);
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.wsEndpoint = wsEndpoint;
        
        // Configure OkHttp client
        client = getUnsafeOkHttpClient();
    }
    
    /**
     * Creates an OkHttpClient that trusts all certificates for development
     * DO NOT USE IN PRODUCTION
     */
    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // Allow all hostnames
                }
            });
            
            return builder
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .pingInterval(20, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating unsafe OkHttpClient", e);
            
            // Fall back to default client without SSL customization
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .pingInterval(20, TimeUnit.SECONDS)
                    .build();
        }
    }
    
    /**
     * Connect to the WebSocket server
     * @param userId The user ID to connect with
     * @return true if connection started, false otherwise
     */
    public boolean connect(String userId) {
        if (webSocket != null) {
            Log.w(TAG, "WebSocket already exists, disconnecting first");
            disconnect();
        }
        
        // Track connection attempt cooldown
        long now = System.currentTimeMillis();
        if (now - lastConnectAttemptTime < RECONNECT_COOLDOWN_MS) {
            consecutiveAttempts++;
            if (consecutiveAttempts > MAX_CONSECUTIVE_ATTEMPTS) {
                Log.w(TAG, "Too many connection attempts, cooling down");
                return false;
            }
        } else {
            consecutiveAttempts = 1;
        }
        
        lastConnectAttemptTime = now;
        
        // Store the current user ID
        this.currentUserId = userId;
        
        // Try to refresh the token first
        sessionManager.refreshToken().thenAccept(refreshSuccess -> {
            if (refreshSuccess) {
                Log.d(TAG, "Successfully refreshed token before connecting");
            } else {
                Log.w(TAG, "Failed to refresh token, will continue with current token");
            }
            
            // Continue with connection after token refresh attempt
            connectWithCurrentToken();
        });
        
        return true;
    }
    
    /**
     * Connect to the WebSocket server with the current auth token
     */
    private boolean connectWithCurrentToken() {
        // Get auth token
        String token = sessionManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "Cannot connect: no auth token available");
            return false;
        }
        
        // For debugging, print the first 10 chars of the token
        if (token.length() > 10) {
            Log.d(TAG, "Auth token starts with: " + token.substring(0, 10) + "...");
        }
        
        // Build WebSocket URL - We need to use the correct WebSocket endpoint
        String url = wsEndpoint;
        
        // Ensure we're using secure WebSocket when needed
        if (url.startsWith("http://")) {
            url = url.replace("http://", "ws://");
        } else if (url.startsWith("https://")) {
            url = url.replace("https://", "wss://");
        }

        // Make sure URL is correctly formatted
        if (url.endsWith("/")) {
            // The URL should end with "/ws" not just "/"
            if (!url.endsWith("ws/")) {
                url = url.substring(0, url.length() - 1) + "ws/";
            }
        } else {
            // If no trailing slash, add the "/ws/" suffix
            url += "/ws/";
        }
        
        // Create WebSocket request with auth header instead of URL parameter
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .build();
        
        Log.d(TAG, "Connecting to WebSocket URL: " + url + " with auth header");
        
        // Update state
        state = WebSocketState.CONNECTING;
        notifyStateChanged();
        
        try {
            // Attempt connection
            webSocket = client.newWebSocket(request, new WebSocketListenerImpl());
            
            // Start heartbeat to keep connection alive
            startHeartbeat();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket", e);
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            return false;
        }
    }
    
    /**
     * Try to reconnect using URL parameters instead of header
     */
    public boolean reconnectWithUrlToken() {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot reconnect: no user ID");
            return false;
        }
        
        // Get auth token
        String token = sessionManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "Cannot reconnect: no auth token available");
            return false;
        }
        
        // Build WebSocket URL with token in URL parameter
        String url = wsEndpoint;
        
        // Ensure we're using secure WebSocket when needed
        if (url.startsWith("http://")) {
            url = url.replace("http://", "ws://");
        } else if (url.startsWith("https://")) {
            url = url.replace("https://", "wss://");
        }

        // Make sure URL is correctly formatted
        if (url.endsWith("/")) {
            // The URL should end with "/ws" not just "/"
            if (!url.endsWith("ws/")) {
                url = url.substring(0, url.length() - 1) + "ws/";
            }
        } else {
            // If no trailing slash, add the "/ws/" suffix
            url += "/ws/";
        }
        
        // Add token as URL parameter instead of header
        url = url + "?token=" + token;
        
        Log.d(TAG, "Reconnecting to WebSocket URL with token parameter: " + url);
        
        // Disconnect existing WebSocket if any
        if (webSocket != null) {
            Log.w(TAG, "WebSocket already exists, disconnecting first");
            disconnect();
        }
        
        // Update state
        state = WebSocketState.CONNECTING;
        notifyStateChanged();
        
        try {
            // Attempt connection
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            webSocket = client.newWebSocket(request, new WebSocketListenerImpl());
            
            // Start heartbeat to keep connection alive
            startHeartbeat();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket with URL parameter", e);
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            return false;
        }
    }
    
    /**
     * Start a periodic heartbeat to keep the connection alive
     */
    private void startHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (webSocket != null && state == WebSocketState.CONNECTED) {
                try {
                    // Send a ping message to keep the connection alive
                    JsonObject ping = new JsonObject();
                    ping.addProperty("type", "ping");
                    ping.addProperty("timestamp", System.currentTimeMillis());
                    webSocket.send(gson.toJson(ping));
                    Log.d(TAG, "Heartbeat ping sent");
                } catch (Exception e) {
                    Log.e(TAG, "Error sending heartbeat", e);
                }
            }
        }, 20, 20, TimeUnit.SECONDS);
    }
    
    /**
     * Stop the heartbeat task
     */
    private void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
    }
    
    /**
     * Attempt to reconnect to the WebSocket server
     */
    private void attemptReconnect() {
        if (reconnecting.compareAndSet(false, true)) {
            Log.d(TAG, "Attempting reconnect, attempt " + (reconnectAttempts + 1));
            
            // Check if we've exceeded reconnect attempts
            if (reconnectAttempts >= RECONNECT_ATTEMPTS) {
                Log.d(TAG, "Max reconnect attempts reached");
                reconnecting.set(false);
                return;
            }
            
            // Check if we've been trying too frequently
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastConnectAttemptTime < RECONNECT_COOLDOWN_MS) {
                consecutiveAttempts++;
                if (consecutiveAttempts > MAX_CONSECUTIVE_ATTEMPTS) {
                    Log.d(TAG, "Too many connection attempts in a short period, cooling down");
                    reconnecting.set(false);
                    return;
                }
            }
            
            new Thread(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                    
                    reconnectAttempts++;
                    lastConnectAttemptTime = System.currentTimeMillis();
                    
                    boolean connected = startConnection();
                    if (!connected) {
                        reconnecting.set(false);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Reconnect thread interrupted", e);
                    reconnecting.set(false);
                }
            }).start();
        }
    }
    
    /**
     * Start connection to WebSocket server with the current user ID
     * @return true if connection started successfully
     */
    private boolean startConnection() {
        if (currentUserId != null) {
            return connect(currentUserId);
        }
        return false;
    }
    
    /**
     * Disconnect from the WebSocket server
     */
    public void disconnect() {
        if (webSocket != null) {
            try {
                // Stop heartbeat
                stopHeartbeat();
                
                // Close normally
                webSocket.close(1000, "Closed by client");
                webSocket = null;
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting WebSocket", e);
            }
        }
        
        state = WebSocketState.DISCONNECTED;
        notifyStateChanged();
        reconnectAttempts = 0;
    }
    
    /**
     * Send a message to the server
     * 
     * @param message the message to send
     * @return true if the message was sent, false otherwise
     */
    public boolean sendMessage(String message) {
        if (webSocket == null || state != WebSocketState.CONNECTED) {
            Log.e(TAG, "Cannot send message: not connected");
            return false;
        }
        
        try {
            return webSocket.send(message);
        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
            return false;
        }
    }
    
    /**
     * Send a direct message to a specific user
     * 
     * @param recipientId the ID of the recipient
     * @param message the message content
     * @return true if the message was sent, false otherwise
     */
    public boolean sendDirectMessage(String recipientId, String message) {
        if (webSocket == null || state != WebSocketState.CONNECTED) {
            Log.e(TAG, "Cannot send direct message: not connected");
            return false;
        }
        
        try {
            JsonObject json = new JsonObject();
            json.addProperty("type", "direct");
            json.addProperty("recipient", recipientId);
            json.addProperty("content", message);
            
            return webSocket.send(gson.toJson(json));
        } catch (Exception e) {
            Log.e(TAG, "Error sending direct message", e);
            return false;
        }
    }
    
    /**
     * Get the current connection state
     * 
     * @return the WebSocket connection state
     */
    public WebSocketState getState() {
        return state;
    }
    
    /**
     * Add a listener for WebSocket events
     * 
     * @param listener the listener to add
     */
    public void addListener(MessageListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a listener for WebSocket events
     * 
     * @param listener the listener to remove
     */
    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners of a state change
     */
    private void notifyStateChanged() {
        for (MessageListener listener : new ArrayList<>(listeners)) {
            listener.onStateChanged(state);
        }
    }
    
    /**
     * Notify all listeners of a message
     */
    private void notifyMessageReceived(String message) {
        for (MessageListener listener : new ArrayList<>(listeners)) {
            listener.onMessageReceived(message);
        }
    }
    
    /**
     * Notify all listeners of an error
     */
    private void notifyError(String error) {
        for (MessageListener listener : new ArrayList<>(listeners)) {
            listener.onError(error);
        }
    }
    
    /**
     * WebSocket event listener interface
     */
    public interface MessageListener {
        /**
         * Called when the WebSocket connection state changes
         * 
         * @param state the new connection state
         */
        void onStateChanged(WebSocketState state);
        
        /**
         * Called when a message is received
         * 
         * @param message the received message
         */
        void onMessageReceived(String message);
        
        /**
         * Called when an error occurs
         * 
         * @param error the error message
         */
        void onError(String error);
    }
    
    /**
     * Adapter implementation of MessageListener that provides empty implementations
     * of the methods. Can be extended to override only the methods of interest.
     */
    public static abstract class MessageListenerAdapter implements MessageListener {
        @Override
        public void onStateChanged(WebSocketState state) {
            // Default implementation does nothing
        }
        
        @Override
        public void onMessageReceived(String message) {
            // Default implementation does nothing
        }
        
        @Override
        public void onError(String error) {
            // Default implementation does nothing
        }
    }
    
    /**
     * Schedule a reconnection attempt
     */
    private void scheduleReconnect() {
        reconnectAttempts++;
        
        if (reconnectAttempts > RECONNECT_ATTEMPTS) {
            Log.d(TAG, "Maximum reconnection attempts reached");
            return;
        }
        
        long delay = RECONNECT_DELAY_MS * reconnectAttempts;
        Log.d(TAG, "Scheduling reconnect in " + delay + "ms (attempt " + reconnectAttempts + ")");
        
        // Use a background thread for reconnection
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                Log.d(TAG, "Attempting to reconnect now");
                
                if (currentUserId != null) {
                    connect(currentUserId);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Reconnect thread interrupted", e);
            }
        }).start();
    }
    
    /**
     * WebSocket event handler
     */
    private class WebSocketListenerImpl extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket connection opened");
            
            // Server already identified the user from the token in URL
            // No need to send an identification message
            
            state = WebSocketState.CONNECTED;
            reconnectAttempts = 0;
            notifyStateChanged();
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                Log.d(TAG, "WebSocket message received: " + text);
                
                // Notify listeners
                for (MessageListener listener : listeners) {
                    listener.onMessageReceived(text);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing WebSocket message", e);
            }
        }
        
        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            // Not handling binary messages
        }
        
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closing: " + code + " " + reason);
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closed: " + code + " " + reason);
            
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            
            // If closed with unauthorized code, try to reconnect using URL parameter
            if (code == 4001 && reason.equals("Unauthorized")) {
                Log.d(TAG, "Authentication failed with header, trying URL parameter...");
                reconnectWithUrlToken();
                return;
            }
            
            // Attempt reconnect (if code indicates a temporary failure)
            if ((code == 1001 || code == 1006 || code == 1012 || code == 1013) &&
                reconnectAttempts < RECONNECT_ATTEMPTS) {
                attemptReconnect();
            }
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            try {
                int code = response != null ? response.code() : 0;
                
                // Don't log EOFException as a serious error since it's common when server closes connection
                if (t instanceof EOFException) {
                    Log.w(TAG, "WebSocket closed by server (EOFException)");
                } else {
                    Log.e(TAG, "WebSocket error: " + t.getMessage() + ", code: " + code, t);
                }
                
                state = WebSocketState.DISCONNECTED;
                notifyStateChanged();
                
                // Attempt reconnect for connection failures
                if (t instanceof IOException || t instanceof SocketException) {
                    attemptReconnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling WebSocket failure", e);
            }
        }
    }
}