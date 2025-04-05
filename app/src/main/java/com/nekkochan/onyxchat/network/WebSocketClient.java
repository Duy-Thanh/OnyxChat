package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
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
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

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
import okhttp3.ConnectionPool;
import okio.ByteString;

/**
 * WebSocket client for real-time messaging
 */
public class WebSocketClient {
    private static final String TAG = "WebSocketClient";
    
    // Default WebSocket endpoint
    private static final String DEFAULT_WS_ENDPOINT = "wss://10.0.2.2:443/ws/";
    
    // Connection parameters
    private static final int MAX_BACKOFF_SECONDS = 300; // Max backoff of 5 minutes
    private static final long RECONNECT_DELAY_MS = 2000; // Start with 2 seconds
    private static final long MIN_RECONNECT_DELAY_MS = 1000; // Minimum delay between reconnects
    
    private final OkHttpClient client;
    private final String wsEndpoint;
    private WebSocket webSocket;
    // Use a more memory-efficient list for listeners with weak references
    private final List<WeakReference<MessageListener>> listeners = new CopyOnWriteArrayList<>();
    private WebSocketState state = WebSocketState.DISCONNECTED;
    private Gson gson; // Lazy initialize to save memory
    
    // Connection tracking
    private String currentUserId;
    private int reconnectAttempts = 0;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private boolean disconnectRequested = false;
    
    // Cooldown tracking to prevent excessive reconnection attempts
    private long lastConnectAttemptTime = 0;
    private static final long RECONNECT_COOLDOWN_MS = 5000; // 5 seconds between connection attempts
    private static final int MAX_CONSECUTIVE_ATTEMPTS = 3;
    private int consecutiveAttempts = 0;
    
    // Track the last time we received any activity from the server
    private long lastServerActivityTime = 0;
    private static final long ACTIVITY_TIMEOUT_MS = 30000; // 30 seconds without activity is considered inactive
    
    private final UserSessionManager sessionManager;
    private final SharedPreferences sharedPreferences;
    
    // Add this as a class field
    private ScheduledExecutorService heartbeatExecutor;
    
    // Memory management
    private static final long MEMORY_CLEANUP_INTERVAL_MS = 60000; // 1 minute
    private long lastMemoryCleanupTime = 0;
    
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
            
            // Build OkHttpClient with better connection settings
            return builder
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)  // Increase read timeout
                    .writeTimeout(30, TimeUnit.SECONDS) // Increase write timeout
                    .pingInterval(3, TimeUnit.SECONDS)  // More frequent protocol-level pings
                    .retryOnConnectionFailure(true)     // Retry automatically 
                    // Avoid connection pooling issues by using a dedicated connection
                    .connectionPool(new okhttp3.ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating unsafe OkHttpClient", e);
            
            // Fall back to default client without SSL customization
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS) 
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .pingInterval(3, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(new okhttp3.ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
                    .build();
        }
    }
    
    /**
     * Connect to the WebSocket server for a user
     */
    public boolean connect(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "Cannot connect: no user ID provided");
            return false;
        }
        
        Log.d(TAG, "Connecting to WebSocket for user: " + userId);
        
        // Update state
        disconnectRequested = false;
        
        // Check if we're already connected
        if (state == WebSocketState.CONNECTED) {
            Log.d(TAG, "Already connected, no need to reconnect");
            return true;
        }
        
        // Check if we're in CONNECTING state
        if (state == WebSocketState.CONNECTING) {
            Log.d(TAG, "Already connecting - forcing connection reset");
            // Close any existing connection and reset state to try connection again
            if (webSocket != null) {
                try {
                    webSocket.cancel();
                    webSocket = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error canceling existing websocket", e);
                }
            }
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
        }
        
        // Rate limit connect attempts
        long now = System.currentTimeMillis();
        long timeSinceLastAttempt = now - lastConnectAttemptTime;
        
        // If we're reconnecting too quickly, implement backoff
        if (timeSinceLastAttempt < MIN_RECONNECT_DELAY_MS) {
            // Count consecutive attempts for exponential backoff
            if (consecutiveAttempts < MAX_CONSECUTIVE_ATTEMPTS) {
                consecutiveAttempts++;
            } else {
                Log.w(TAG, "Too many consecutive connection attempts, backing off");
                return false;
            }
        } else {
            consecutiveAttempts = 1;
        }
        
        lastConnectAttemptTime = now;
        
        // Store the current user ID
        this.currentUserId = userId;
        
        // Try to refresh the token first if we have a refresh token
        String refreshToken = sessionManager.getRefreshToken();
        if (refreshToken != null && !refreshToken.isEmpty()) {
            Log.d(TAG, "Refresh token available, attempting to refresh before connecting");
            sessionManager.refreshToken().thenAccept(refreshSuccess -> {
                if (refreshSuccess) {
                    Log.d(TAG, "Successfully refreshed token before connecting");
                } else {
                    Log.w(TAG, "Failed to refresh token, will continue with current token");
                }
                
                // Continue with connection after token refresh attempt
                connectWithCurrentToken();
            });
        } else {
            // No refresh token available, connect with current access token
            Log.d(TAG, "No refresh token available, connecting with current access token");
            connectWithCurrentToken();
        }
        
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
        
        // Build WebSocket URL with normalized path
        String baseUrl = wsEndpoint;
        
        // Ensure we're using secure WebSocket when needed
        if (baseUrl.startsWith("http://")) {
            baseUrl = baseUrl.replace("http://", "ws://");
        } else if (baseUrl.startsWith("https://")) {
            baseUrl = baseUrl.replace("https://", "wss://");
        }

        // Fix any duplicate '/ws' paths and ensure proper URL formatting
        if (baseUrl.contains("/ws")) {
            // The URL already contains "/ws", so remove any trailing slashes
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            // Add the query parameter directly
            baseUrl = baseUrl + "?token=" + token;
        } else {
            // The URL doesn't contain "/ws", so ensure it ends with exactly one slash
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            // Add the "/ws" path and query parameter
            baseUrl = baseUrl + "/ws?token=" + token;
        }
        
        Log.d(TAG, "Connecting to WebSocket URL: " + baseUrl);
        
        // Create WebSocket request
        Request request = new Request.Builder()
                .url(baseUrl)
                .build();
        
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
        
        // Build WebSocket URL with normalized path
        String baseUrl = wsEndpoint;
        
        // Ensure we're using secure WebSocket when needed
        if (baseUrl.startsWith("http://")) {
            baseUrl = baseUrl.replace("http://", "ws://");
        } else if (baseUrl.startsWith("https://")) {
            baseUrl = baseUrl.replace("https://", "wss://");
        }

        // Fix any duplicate '/ws' paths and ensure proper URL formatting
        if (baseUrl.contains("/ws")) {
            // The URL already contains "/ws", so remove any trailing slashes
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            // Add the query parameter directly
            baseUrl = baseUrl + "?token=" + token;
        } else {
            // The URL doesn't contain "/ws", so ensure it ends with exactly one slash
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            // Add the "/ws" path and query parameter
            baseUrl = baseUrl + "/ws?token=" + token;
        }
        
        Log.d(TAG, "Reconnecting with WebSocket URL: " + baseUrl);
        
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
                    .url(baseUrl)
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
                    webSocket.send(getGson().toJson(ping));
                    Log.d(TAG, "Heartbeat ping sent");
                } catch (Exception e) {
                    Log.e(TAG, "Error sending heartbeat", e);
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
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
     * Schedule a reconnection attempt with a delay
     * 
     * @param delayMs the delay in milliseconds
     */
    private void scheduleReconnect(final long delayMs) {
        if (reconnecting.compareAndSet(false, true)) {
            // Calculate backoff time - increase with each attempt but cap at MAX_BACKOFF_SECONDS
            // Use exponential backoff with a maximum delay
            long delay = Math.min(delayMs, MAX_BACKOFF_SECONDS * 1000);
            reconnectAttempts++;
            
            Log.d(TAG, "Scheduling reconnect attempt " + reconnectAttempts + 
                    " with delay " + delay + "ms");
            
            // Use a handler to schedule the reconnect on the main thread
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Log.d(TAG, "Executing scheduled reconnect, attempt " + reconnectAttempts);
                    // Try to reconnect
                    boolean success = connectWithCurrentToken();
                    
                    // Reset the reconnecting flag
                    reconnecting.set(false);
                    
                    // If this attempt failed, schedule another one with increased delay
                    if (!success) {
                        // Double the delay for next attempt, up to the maximum
                        long nextDelay = Math.min(delay * 2, MAX_BACKOFF_SECONDS * 1000);
                        scheduleReconnect(nextDelay);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during scheduled reconnect", e);
                    reconnecting.set(false);
                    
                    // Even if there was an exception, try again with increased delay
                    long nextDelay = Math.min(delay * 2, MAX_BACKOFF_SECONDS * 1000);
                    scheduleReconnect(nextDelay);
                }
            }, delay);
        } else {
            Log.d(TAG, "Reconnection already in progress, skipping");
        }
    }
    
    /**
     * Attempt to reconnect immediately
     */
    private void attemptReconnect() {
        if (reconnecting.compareAndSet(false, true)) {
            Log.d(TAG, "Attempting reconnect, attempt " + (reconnectAttempts + 1));
            
            // Increment the reconnect attempts counter
            reconnectAttempts++;
            
            // Try to reconnect after a short delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    // Try to connect
                    boolean success = startConnection();
                    
                    // Reset reconnecting flag
                    reconnecting.set(false);
                    
                    // If connection failed, schedule another attempt with backoff
                    if (!success) {
                        // Calculate next delay using exponential backoff
                        long nextDelay = Math.min(
                            RECONNECT_DELAY_MS * (long)Math.pow(1.5, Math.min(reconnectAttempts, 10)),
                            MAX_BACKOFF_SECONDS * 1000
                        );
                        scheduleReconnect(nextDelay);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error attempting reconnect", e);
                    reconnecting.set(false);
                    
                    // Schedule another attempt even after an exception
                    long nextDelay = Math.min(
                        RECONNECT_DELAY_MS * 2,
                        MAX_BACKOFF_SECONDS * 1000
                    );
                    scheduleReconnect(nextDelay);
                }
            }, RECONNECT_DELAY_MS);
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
            performMemoryCleanupIfNeeded();
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
            
            performMemoryCleanupIfNeeded();
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
        if (listener == null) return;
        
        // Check if listener already exists
        for (WeakReference<MessageListener> ref : listeners) {
            MessageListener existingListener = ref.get();
            if (existingListener != null && existingListener.equals(listener)) {
                return;
            }
        }
        
        // Add as a weak reference
        listeners.add(new WeakReference<>(listener));
        
        // Periodically clean up dead references
        performMemoryCleanupIfNeeded();
    }
    
    /**
     * Remove a listener for WebSocket events
     * 
     * @param listener the listener to remove
     */
    public void removeListener(MessageListener listener) {
        if (listener == null) return;
        
        Iterator<WeakReference<MessageListener>> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            WeakReference<MessageListener> ref = iterator.next();
            MessageListener existingListener = ref.get();
            if (existingListener == null || existingListener.equals(listener)) {
                listeners.remove(ref);
                break;
            }
        }
    }
    
    /**
     * Clean up expired weak references to reduce memory usage
     */
    private void cleanupExpiredListeners() {
        Iterator<WeakReference<MessageListener>> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().get() == null) {
                listeners.remove(iterator.next());
            }
        }
    }
    
    /**
     * Perform memory cleanup operations if enough time has passed since last cleanup
     */
    private void performMemoryCleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastMemoryCleanupTime > MEMORY_CLEANUP_INTERVAL_MS) {
            lastMemoryCleanupTime = now;
            
            // Clean up expired weak references
            cleanupExpiredListeners();
            
            // Hint to the garbage collector that now would be a good time to run
            System.gc();
            
            Log.d(TAG, "Performed memory cleanup, remaining listeners: " + listeners.size());
        }
    }
    
    /**
     * Force a memory cleanup operation
     */
    public void forceMemoryCleanup() {
        cleanupExpiredListeners();
        System.gc();
        Log.d(TAG, "Forced memory cleanup, remaining listeners: " + listeners.size());
    }
    
    /**
     * Get Gson instance (lazy initialization to save memory)
     */
    private Gson getGson() {
        if (gson == null) {
            gson = new Gson();
        }
        return gson;
    }
    
    /**
     * Notify all listeners of a state change
     */
    private void notifyStateChanged() {
        List<MessageListener> activeListeners = new ArrayList<>(listeners.size());
        
        // First collect all valid listeners
        for (WeakReference<MessageListener> ref : listeners) {
            MessageListener listener = ref.get();
            if (listener != null) {
                activeListeners.add(listener);
            }
        }
        
        // Then notify them
        for (MessageListener listener : activeListeners) {
            try {
                listener.onStateChanged(state);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of state change", e);
            }
        }
    }
    
    /**
     * Notify all listeners of a message
     */
    private void notifyMessageReceived(String message) {
        List<MessageListener> activeListeners = new ArrayList<>(listeners.size());
        
        // First collect all valid listeners
        for (WeakReference<MessageListener> ref : listeners) {
            MessageListener listener = ref.get();
            if (listener != null) {
                activeListeners.add(listener);
            }
        }
        
        // Then notify them
        for (MessageListener listener : activeListeners) {
            try {
                listener.onMessageReceived(message);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of message", e);
            }
        }
    }
    
    /**
     * Notify all listeners of an error
     */
    private void notifyError(String error) {
        List<MessageListener> activeListeners = new ArrayList<>(listeners.size());
        
        // First collect all valid listeners
        for (WeakReference<MessageListener> ref : listeners) {
            MessageListener listener = ref.get();
            if (listener != null) {
                activeListeners.add(listener);
            }
        }
        
        // Then notify them
        for (MessageListener listener : activeListeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of error", e);
            }
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
     * WebSocket event listener implementation
     */
    private class WebSocketListenerImpl extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket connection opened");
            
            // Reset reconnect attempts on successful connection
            reconnectAttempts = 0;
            
            // Server already identified the user from the token in URL
            // No need to send an identification message
            
            state = WebSocketState.CONNECTED;
            notifyStateChanged();
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "WebSocket message received: " + text);
            
            // Record that we received activity from the server
            recordServerActivity();
            
            try {
                // Parse the message to check for token refresh requests
                JsonObject jsonMessage = JsonParser.parseString(text).getAsJsonObject();
                if (jsonMessage.has("type") && "TOKEN_REFRESH_REQUIRED".equals(jsonMessage.get("type").getAsString())) {
                    Log.d(TAG, "Received token refresh request from server");
                    
                    // Handle token refresh
                    handleTokenRefresh();
                    return;
                }
                
                // Special handling for pong messages to update activity time
                if (jsonMessage.has("type") && "pong".equals(jsonMessage.get("type").getAsString())) {
                    // We already recorded activity, just notify listeners
                }
                
                // Notify listeners for normal messages
                notifyMessageReceived(text);
            } catch (Exception e) {
                Log.e(TAG, "Error processing WebSocket message", e);
                
                // If the message couldn't be parsed as JSON, just pass it through
                notifyMessageReceived(text);
            }
        }
        
        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            // Not handling binary messages
        }
        
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closing gracefully: code=" + code + ", reason=" + reason);
            
            // Server is requesting a clean close, so close our side as well
            webSocket.close(1000, "Client closing");
            
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            
            // Schedule a reconnection since this might be server restart or maintenance
            scheduleReconnect(RECONNECT_DELAY_MS);
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closed gracefully: code=" + code + ", reason=" + reason);
            
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            
            // Clean close, but we still might want to reconnect after some delay
            // if this was an intentional close due to server maintenance, etc.
            if (code != 1000 && code != 1001) { // Not normal closure or going away
                scheduleReconnect(RECONNECT_DELAY_MS);
            }
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            try {
                int code = response != null ? response.code() : 0;
                String responseBody = "";
                
                // Try to extract the response body to get more detailed error info
                if (response != null && response.body() != null) {
                    try {
                        responseBody = response.body().string();
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading response body", e);
                    }
                }
                
                // Get detailed connection information
                String url = response != null ? response.request().url().toString() : "unknown";
                String headers = response != null ? response.request().headers().toString() : "unknown";
                
                // Log the complete response info and full stack trace
                if (t instanceof EOFException) {
                    // EOFException typically means the connection was closed from the other side
                    Log.w(TAG, "WebSocket connection closed unexpectedly (EOFException)" +
                          "\nURL: " + url +
                          "\nHeaders: " + headers +
                          "\nStack trace:", t);
                } else {
                    // For other errors, log as errors
                    Log.e(TAG, "WebSocket error: " + t.getMessage() + 
                          ", code: " + code + 
                          ", response: " + (response != null ? response.toString() : "null") +
                          ", body: " + responseBody +
                          "\nURL: " + url +
                          "\nHeaders: " + headers, t);
                }
                
                state = WebSocketState.DISCONNECTED;
                notifyStateChanged();
                notifyError(t.getMessage());
                
                // Attempt reconnect with backoff strategy for most connection failures
                if (t instanceof IOException || t instanceof SocketException || t instanceof EOFException) {
                    // Calculate delay with exponential backoff, but don't give up
                    long delay = RECONNECT_DELAY_MS * (long)Math.min(Math.pow(1.5, Math.min(reconnectAttempts, 10)), MAX_BACKOFF_SECONDS / 2);
                    scheduleReconnect(delay);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling WebSocket failure", e);
            }
        }
    }
    
    /**
     * Handle token refresh request from server
     */
    private void handleTokenRefresh() {
        Log.d(TAG, "Handling token refresh request");
        
        // First disconnect the current WebSocket
        if (webSocket != null) {
            webSocket.close(1000, "Token refresh required");
            webSocket = null;
        }
        
        // Update state
        state = WebSocketState.DISCONNECTED;
        notifyStateChanged();
        
        // Check if we have a refresh token before attempting to refresh
        if (sessionManager.getRefreshToken() == null || sessionManager.getRefreshToken().isEmpty()) {
            Log.e(TAG, "No refresh token available, cannot refresh");
            
            // Notify listeners about the error
            notifyError("Authentication error: No refresh token available");
            
            // Even without a refresh token, try to reconnect with the existing token
            // It might still be valid or the server might have different validation rules
            Log.d(TAG, "Attempting to reconnect with existing token despite refresh failure");
            scheduleReconnect(RECONNECT_DELAY_MS);
            return;
        }
        
        // Refresh the token
        sessionManager.refreshToken().thenAccept(refreshSuccess -> {
            if (refreshSuccess) {
                Log.d(TAG, "Token refreshed successfully, reconnecting");
                
                // Reconnect with new token
                new Handler(Looper.getMainLooper()).post(() -> {
                    reconnectAttempts = 0; // Reset counter since this is a fresh start with new token
                    connectWithCurrentToken();
                });
            } else {
                Log.e(TAG, "Failed to refresh token");
                
                // Notify listeners about the error
                notifyError("Failed to refresh authentication token");
                
                // Schedule a reconnect attempt in case token refreshing failed
                scheduleReconnect(RECONNECT_DELAY_MS);
            }
        });
    }
    
    /**
     * Record that server activity was received
     * This should be called whenever we get a message or pong from the server
     */
    private void recordServerActivity() {
        lastServerActivityTime = System.currentTimeMillis();
    }
    
    /**
     * Check if there has been recent activity from the server
     * @return true if there has been activity within ACTIVITY_TIMEOUT_MS
     */
    public boolean hasRecentActivity() {
        if (state != WebSocketState.CONNECTED || webSocket == null) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        return (now - lastServerActivityTime) < ACTIVITY_TIMEOUT_MS;
    }
    
    /**
     * Send a ping to verify the connection is active
     * @return true if the ping was sent successfully
     */
    public boolean sendPing() {
        if (webSocket == null || state != WebSocketState.CONNECTED) {
            return false;
        }
        
        try {
            // Send a ping message
            JsonObject ping = new JsonObject();
            ping.addProperty("type", "ping");
            ping.addProperty("timestamp", System.currentTimeMillis());
            return webSocket.send(getGson().toJson(ping));
        } catch (Exception e) {
            Log.e(TAG, "Error sending ping", e);
            return false;
        }
    }
}