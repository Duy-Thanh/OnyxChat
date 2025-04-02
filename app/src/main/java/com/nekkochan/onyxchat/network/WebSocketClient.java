package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    
    private final OkHttpClient client;
    private final String wsEndpoint;
    private WebSocket webSocket;
    private final List<MessageListener> listeners = new ArrayList<>();
    private WebSocketState state = WebSocketState.DISCONNECTED;
    private final Gson gson = new Gson();
    
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
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Connect to the WebSocket server
     * 
     * @param userId the ID of the user connecting
     * @return true if connection started, false otherwise
     */
    public boolean connect(String userId) {
        if (state != WebSocketState.DISCONNECTED) {
            Log.d(TAG, "Already connecting or connected");
            return false;
        }
        
        String url = wsEndpoint + userId;
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
     * Disconnect from the WebSocket server
     */
    public void disconnect() {
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
            // Default empty implementation
        }
        
        @Override
        public void onMessageReceived(String message) {
            // Default empty implementation
        }
        
        @Override
        public void onError(String error) {
            // Default empty implementation
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
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "Received message: " + text);
            notifyMessageReceived(text);
        }
        
        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Log.d(TAG, "Received bytes message");
            // Most WebSocket APIs use text, but handle binary data just in case
            notifyMessageReceived(bytes.utf8());
        }
        
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closing: " + code + " - " + reason);
            webSocket.close(code, reason);
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closed: " + code + " - " + reason);
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "WebSocket failure", t);
            state = WebSocketState.DISCONNECTED;
            notifyStateChanged();
            notifyError(t.getMessage());
        }
    }
}