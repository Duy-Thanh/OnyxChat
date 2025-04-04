package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nekkochan.onyxchat.util.UserSessionManager;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final String DEFAULT_WS_ENDPOINT = "ws://10.0.2.2:8082/ws/";
    
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
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Create a new WebSocket client with a custom endpoint
     */
    public WebSocketClient(String wsEndpoint, Context context) {
        this.sessionManager = new UserSessionManager(context);
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.wsEndpoint = wsEndpoint;
        
        // Configure OkHttp client
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
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
        
        // Build WebSocket URL
        String url = wsEndpoint;
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "ws/" + userId;
        
        // Create WebSocket request
        Request.Builder requestBuilder = new Request.Builder()
                .url(url);
        
        // Add auth token if available
        String token = sessionManager.getAuthToken();
        if (token != null && !token.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }
        
        // Create request
        Request request = requestBuilder.build();
        
        // Update state
        state = WebSocketState.CONNECTING;
        notifyStateChanged();
        
        try {
            // Attempt connection
            webSocket = client.newWebSocket(request, new WebSocketListenerImpl());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket", e);
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            return false;
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
        reconnectAttempts = RECONNECT_ATTEMPTS; // Prevent auto-reconnect
        
        if (webSocket != null) {
            webSocket.close(1000, "User initiated disconnect");
            webSocket = null;
        }
        
        if (state != WebSocketState.DISCONNECTED) {
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
        }
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
            state = WebSocketState.CONNECTED;
            reconnectAttempts = 0;
            notifyStateChanged();
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "WebSocket message received: " + text);
            try {
                notifyMessageReceived(text);
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
            webSocket.close(1000, null);
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closed: " + code + " " + reason);
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            // Don't log EOFException as error since it's common when disconnecting
            if (!(t instanceof EOFException)) {
                Log.e(TAG, "WebSocket failure", t);
            }
            
            String errorMessage = t.getMessage();
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            notifyError(errorMessage != null ? errorMessage : "Unknown error");
            
            // Schedule reconnect if needed
            if (currentUserId != null && !(t instanceof EOFException)) {
                scheduleReconnect();
            }
        }
    }
}