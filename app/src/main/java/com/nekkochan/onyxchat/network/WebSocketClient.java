package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
    private static final String DEFAULT_WS_ENDPOINT = "ws://10.0.2.2:8081/ws/";
    
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
    public WebSocketClient() {
        this(DEFAULT_WS_ENDPOINT);
    }
    
    /**
     * Create a new WebSocket client with a custom endpoint
     * 
     * @param wsEndpoint the WebSocket endpoint URL
     */
    public WebSocketClient(String wsEndpoint) {
        this.wsEndpoint = wsEndpoint;
        
        // Build OkHttp client with reasonable timeouts
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS) // Longer read timeout to prevent unnecessary disconnections
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // Add ping to keep connection alive
                .retryOnConnectionFailure(true)
                .build();
    }
    
    /**
     * Connect to the WebSocket server
     * 
     * @param userId the ID of the user connecting
     * @return true if connection started, false otherwise
     */
    public boolean connect(String userId) {
        if (state == WebSocketState.CONNECTED) {
            Log.d(TAG, "Already connected");
            return true;
        }
        
        if (state == WebSocketState.CONNECTING && !reconnecting.get()) {
            Log.d(TAG, "Already connecting");
            return true;
        }
        
        // Check if we've attempted to connect too recently
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastConnectAttemptTime < RECONNECT_COOLDOWN_MS) {
            consecutiveAttempts++;
            if (consecutiveAttempts > MAX_CONSECUTIVE_ATTEMPTS) {
                Log.d(TAG, "Too many connection attempts, cooling down");
                return false;
            }
        } else {
            // Reset consecutive attempts counter if enough time has passed
            consecutiveAttempts = 0;
        }
        
        // Update last attempt time
        lastConnectAttemptTime = currentTime;
        
        currentUserId = userId;
        reconnectAttempts = 0;
        return startConnection();
    }
    
    /**
     * Start a WebSocket connection
     */
    private boolean startConnection() {
        String url = wsEndpoint + currentUserId;
        Log.d(TAG, "Connecting to WebSocket at " + url);
        
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            
            state = WebSocketState.CONNECTING;
            notifyStateChanged();
            
            webSocket = client.newWebSocket(request, new WebSocketHandler());
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
     * WebSocket event handler
     */
    private class WebSocketHandler extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket connection opened");
            state = WebSocketState.CONNECTED;
            notifyStateChanged();
            reconnectAttempts = 0;
            reconnecting.set(false);
            consecutiveAttempts = 0;  // Reset consecutive attempts on successful connection
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "Message received: " + text);
            notifyMessageReceived(text);
        }
        
        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Log.d(TAG, "Binary message received");
            // Convert to string for simplicity
            notifyMessageReceived(bytes.utf8());
        }
        
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closing: " + code + " " + reason);
            webSocket.close(code, reason);
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closed: " + code + " " + reason);
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            // Don't log EOFException as error since it's common when closing connections
            if (t instanceof EOFException) {
                Log.d(TAG, "WebSocket connection closed");
            } else {
                Log.e(TAG, "WebSocket failure", t);
            }
            
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            
            String errorMessage = t != null ? t.getMessage() : "Unknown error";
            notifyError(errorMessage);
            
            // Attempt to reconnect
            attemptReconnect();
        }
    }
}