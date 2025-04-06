package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nekkochan.onyxchat.util.UserSessionManager;
import com.nekkochan.onyxchat.model.UserStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.os.Message;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Service for managing chat functionality
 */
public class ChatService {
    private static final String TAG = "ChatService";
    private static final String PREF_SERVER_URL = "chat_server_url";
    private static final String DEFAULT_SERVER_URL = "wss://10.0.2.2:443/ws/"; // localhost for emulator
    
    private static ChatService instance;
    
    private final Context context;
    private final WebSocketClient webSocketClient;
    private final MutableLiveData<WebSocketClient.WebSocketState> connectionState;
    private final MutableLiveData<Map<String, UserStatus>> onlineUsers;
    private final MutableLiveData<ChatMessage> latestMessage;
    private final MutableLiveData<ChatEvent> chatEvents;
    private final Gson gson = new Gson();
    private String userId;
    
    /**
     * Event types for chat service events
     */
    public enum ChatEventType {
        AUTH_ERROR,
        CONNECTION_ERROR,
        SERVER_ERROR
    }
    
    /**
     * Chat event class for handling system-level events
     */
    public static class ChatEvent {
        private final ChatEventType type;
        private final String message;
        
        public ChatEvent(ChatEventType type, String message) {
            this.type = type;
            this.message = message;
        }
        
        public ChatEventType getType() {
            return type;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * Get the singleton instance of the ChatService
     * @param context Application context
     * @return The ChatService instance
     */
    public static synchronized ChatService getInstance(Context context) {
        if (instance == null) {
            instance = new ChatService(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Private constructor for singleton pattern
     * @param context Application context
     */
    private ChatService(Context context) {
        this.context = context;
        this.connectionState = new MutableLiveData<>(WebSocketClient.WebSocketState.DISCONNECTED);
        this.onlineUsers = new MutableLiveData<>(new HashMap<>());
        this.latestMessage = new MutableLiveData<>();
        this.chatEvents = new MutableLiveData<>();
        
        // Get server URL from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String serverUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL);
        
        // Create WebSocket client
        this.webSocketClient = new WebSocketClient(context);
        
        // Register for WebSocket events
        this.webSocketClient.addListener(new WebSocketClient.MessageListener() {
            @Override
            public void onStateChanged(WebSocketClient.WebSocketState state) {
                connectionState.postValue(state);
                
                if (state == WebSocketClient.WebSocketState.DISCONNECTED) {
                    // Clear online users when disconnected
                    onlineUsers.postValue(new HashMap<>());
                }
            }
            
            @Override
            public void onMessageReceived(String message) {
                Log.d(TAG, "Received: " + message);
                processMessage(message);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "WebSocket error: " + error);
                
                ChatMessage errorMessage = new ChatMessage(
                        ChatMessage.MessageType.ERROR,
                        "system",
                        userId,
                        "Connection error: " + error,
                        System.currentTimeMillis()
                );
                latestMessage.postValue(errorMessage);
            }
        });
    }
    
    /**
     * Process a new message from the server
     * @param message The message content
     */
    private void processMessage(String message) {
        try {
            // Try to parse as JSON
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(message);
            
            if (jsonElement.isJsonObject()) {
                JsonObject jsonMessage = jsonElement.getAsJsonObject();
                
                String type = jsonMessage.get("type").getAsString();
                Log.d(TAG, "Received message of type: " + type);
                
                // Extract and log timestamp for debugging
                long messageTimestamp = System.currentTimeMillis();
                boolean hasServerTimestamp = false;
                
                if (jsonMessage.has("timestamp")) {
                    try {
                        messageTimestamp = jsonMessage.get("timestamp").getAsLong();
                        hasServerTimestamp = true;
                        Log.d(TAG, "Message has server timestamp: " + messageTimestamp + 
                                " (" + new Date(messageTimestamp) + ")");
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing timestamp from server, using system time", e);
                    }
                } else if (jsonMessage.has("createdAt")) {
                    try {
                        // Try to parse ISO date string
                        String timestampStr = jsonMessage.get("createdAt").getAsString();
                        SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                        iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));
                        Date date = iso8601Format.parse(timestampStr);
                        if (date != null) {
                            messageTimestamp = date.getTime();
                            hasServerTimestamp = true;
                            Log.d(TAG, "Message has createdAt timestamp: " + messageTimestamp + 
                                    " (" + new Date(messageTimestamp) + ")");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing createdAt timestamp from server, using system time", e);
                    }
                }
                
                if (!hasServerTimestamp) {
                    Log.d(TAG, "No timestamp found in message, using local time: " + 
                            messageTimestamp + " (" + new Date(messageTimestamp) + ")");
                }

                if ("message".equals(type)) {
                    // Process chat message
                    String senderId = jsonMessage.get("senderId").getAsString();
                    String recipientId = jsonMessage.get("recipientId").getAsString();
                    String content = jsonMessage.get("content").getAsString();
                    
                    // Check for formattedTime field from enhanced server
                    if (jsonMessage.has("formattedTime")) {
                        String formattedTime = jsonMessage.get("formattedTime").getAsString();
                        Log.d(TAG, "Message has formatted time from server: " + formattedTime);
                    }
                    
                    // Log the important message details including timestamp
                    Log.d(TAG, String.format("Chat message: content=%s, senderId=%s, timestamp=%d (%s)", 
                            content, senderId, messageTimestamp, new Date(messageTimestamp)));
                    
                    // Create message object
                    ChatMessage chatMessage = new ChatMessage(
                            ChatMessage.MessageType.MESSAGE,
                            senderId,
                            recipientId,
                            content,
                            messageTimestamp // Use parsed timestamp
                    );
                    
                    // Mark if it's our own message reflected back
                    chatMessage.setSelf(senderId.equals(userId));
                    
                    // Post to LiveData
                    latestMessage.postValue(chatMessage);
                } else if ("user_status".equals(type)) {
                    // User status update
                    String status = jsonMessage.get("status").getAsString();
                    String statusUserId = jsonMessage.get("user_id").getAsString();
                    
                    // Create a system message about the status update
                    ChatMessage chatMessage = new ChatMessage(
                            ChatMessage.MessageType.SYSTEM,
                            "server",
                            userId,
                            "User " + statusUserId + " is " + status,
                            messageTimestamp
                    );
                    latestMessage.postValue(chatMessage);
                } else if ("echo".equals(type)) {
                    // Echo message (confirmation of our message)
                    String content = jsonMessage.get("content").getAsString();
                    
                    // Log echo message details
                    Log.d(TAG, String.format("Echo message: content=%s, timestamp=%d (%s)", 
                            content, messageTimestamp, new Date(messageTimestamp)));
                    
                    ChatMessage chatMessage = new ChatMessage(
                            ChatMessage.MessageType.ECHO,
                            userId, // me as sender
                            "server",
                            content,
                            messageTimestamp // Use parsed timestamp
                    );
                    chatMessage.setSelf(true);
                    latestMessage.postValue(chatMessage);
                } else if ("error".equals(type)) {
                    // Error message
                    String content = jsonMessage.get("content").getAsString();
                    
                    // Log error message details
                    Log.d(TAG, String.format("Error message: content=%s, timestamp=%d (%s)", 
                            content, messageTimestamp, new Date(messageTimestamp)));
                    
                    ChatMessage chatMessage = new ChatMessage(
                            ChatMessage.MessageType.ERROR,
                            "server",
                            userId,
                            content,
                            messageTimestamp // Use parsed timestamp
                    );
                    latestMessage.postValue(chatMessage);
                    
                    // Also post to the error event stream
                    chatEvents.postValue(new ChatEvent(ChatEventType.SERVER_ERROR, content));
                } else {
                    // Other message types
                    Log.d(TAG, String.format("System message: content=%s, timestamp=%d (%s)", 
                            message, messageTimestamp, new Date(messageTimestamp)));
                    
                    ChatMessage chatMessage = new ChatMessage(
                            ChatMessage.MessageType.SYSTEM,
                            "server",
                            userId,
                            message,
                            messageTimestamp // Use parsed timestamp
                    );
                    latestMessage.postValue(chatMessage);
                }
            } else {
                // Plain text message as a system message
                Log.d(TAG, "Plain text message: " + message);
                
                ChatMessage chatMessage = new ChatMessage(
                        ChatMessage.MessageType.SYSTEM,
                        "server",
                        userId,
                        message,
                        System.currentTimeMillis() // Use current time for non-JSON messages
                );
                latestMessage.postValue(chatMessage);
            }
        } catch (Exception e) {
            // Not a JSON message, treat as a system message
            Log.d(TAG, "Not JSON: " + message);
            ChatMessage chatMessage = new ChatMessage(
                    ChatMessage.MessageType.SYSTEM,
                    "server",
                    userId,
                    message,
                    System.currentTimeMillis() // Use current time for non-JSON messages
            );
            latestMessage.postValue(chatMessage);
        }
    }
    
    /**
     * Connect to the chat server with the specified user ID
     * @param userId The user ID to connect with
     * @return true if connection started, false otherwise
     */
    public boolean connect(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "Cannot connect: User ID is null or empty");
            return false;
        }
        
        // First check if we already have an active connection
        if (checkConnectionStatus()) {
            Log.d(TAG, "WebSocket is already connected in background service, updating UI state");
            connectionState.setValue(WebSocketClient.WebSocketState.CONNECTED);
            return true;
        }
        
        // Store user ID
        this.userId = userId;
        
        // Update connection state
        connectionState.setValue(WebSocketClient.WebSocketState.CONNECTING);
        
        // Get token and verify it exists
        UserSessionManager sessionManager = new UserSessionManager(context);
        String token = sessionManager.getAuthToken();
        
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "No auth token available, attempting to refresh token");
            
            // Check if we have a refresh token
            if (sessionManager.hasRefreshToken()) {
                // Attempt to refresh the token before connecting
                sessionManager.refreshToken().thenAccept(refreshResult -> {
                    if (refreshResult) {
                        Log.d(TAG, "Token refresh successful, attempting to connect with new token");
                        // Get the new token and retry connection
                        String newToken = sessionManager.getAuthToken();
                        if (newToken != null && !newToken.isEmpty()) {
                            // Connect to WebSocket with new token
                            webSocketClient.connect(userId);
                        } else {
                            Log.e(TAG, "Token refresh succeeded but new token is empty");
                            connectionState.setValue(WebSocketClient.WebSocketState.DISCONNECTED);
                            
                            // Log the user out if token refresh fails - it's likely the refresh token is invalid
                            // and the user needs to re-authenticate
                            Log.w(TAG, "Logging user out due to invalid refresh token");
                            sessionManager.logout();
                            
                            // Post an error event so the UI can show a proper message
                            chatEvents.postValue(new ChatEvent(
                                ChatEventType.AUTH_ERROR,
                                "Your session has expired. Please log in again."
                            ));
                        }
                    } else {
                        Log.e(TAG, "Token refresh failed, cannot connect");
                        connectionState.setValue(WebSocketClient.WebSocketState.DISCONNECTED);
                        
                        // Log the user out if token refresh fails - it's likely the refresh token is invalid
                        // and the user needs to re-authenticate
                        Log.w(TAG, "Logging user out due to invalid refresh token");
                        sessionManager.logout();
                        
                        // Post an error event
                        chatEvents.postValue(new ChatEvent(
                            ChatEventType.AUTH_ERROR,
                            "Your session is invalid. Please log in again."
                        ));
                    }
                });
                
                // Return true to indicate we've started the connection process
                return true;
            } else {
                Log.e(TAG, "Cannot connect: No auth token available and no refresh token to try");
                connectionState.setValue(WebSocketClient.WebSocketState.DISCONNECTED);
                
                // Log the user out since we have no valid tokens
                Log.w(TAG, "Logging user out due to no valid tokens");
                sessionManager.logout();
                
                // Post an error event
                chatEvents.postValue(new ChatEvent(
                    ChatEventType.AUTH_ERROR,
                    "Your session is invalid. Please log in again."
                ));
                
                return false;
            }
        }
        
        // Check if refresh token is available and log a warning if it's not
        if (!sessionManager.hasRefreshToken()) {
            Log.w(TAG, "Warning: No refresh token available. Token refresh will fail if needed.");
            Log.w(TAG, "To fix this, please log out and log in again to obtain a refresh token.");
        }
        
        // Connect to WebSocket
        Log.d(TAG, "Connecting to chat server with user ID: " + userId);
        return webSocketClient.connect(userId);
    }
    
    /**
     * Check the actual connection status and update the state if needed
     * @return true if the WebSocket is connected and functional
     */
    public boolean checkConnectionStatus() {
        WebSocketClient.WebSocketState currentState = webSocketClient.getState();
        
        // If the client thinks it's connected, verify with a quick test
        if (currentState == WebSocketClient.WebSocketState.CONNECTED) {
            // If we have recent heartbeat responses, we're definitely connected
            if (webSocketClient.hasRecentActivity()) {
                // Make sure our state reflects this
                if (connectionState.getValue() != WebSocketClient.WebSocketState.CONNECTED) {
                    Log.d(TAG, "Updating connection state to match actual connected state");
                    connectionState.setValue(WebSocketClient.WebSocketState.CONNECTED);
                }
                return true;
            } else {
                // Try sending a ping to verify connection is still active
                boolean pingSuccess = webSocketClient.sendPing();
                if (!pingSuccess) {
                    // Connection appears broken, update state
                    Log.w(TAG, "WebSocket reports connected but failed to send ping, resetting state");
                    connectionState.setValue(WebSocketClient.WebSocketState.DISCONNECTED);
                    return false;
                }
                return true;
            }
        } else if (connectionState.getValue() == WebSocketClient.WebSocketState.CONNECTED && 
                   currentState != WebSocketClient.WebSocketState.CONNECTED) {
            // Fix mismatched state
            Log.w(TAG, "Connection state mismatch - updating to: " + currentState);
            connectionState.setValue(currentState);
            return false;
        }
        
        return currentState == WebSocketClient.WebSocketState.CONNECTED;
    }
    
    /**
     * Disconnect from the chat server
     */
    public void disconnect() {
        webSocketClient.disconnect();
    }
    
    /**
     * Get the WebSocket client
     * @return The WebSocket client
     */
    public WebSocketClient getWebSocketClient() {
        return webSocketClient;
    }
    
    /**
     * Perform memory cleanup to reduce RAM usage
     */
    public void performMemoryCleanup() {
        // Ask the WebSocketClient to clean up any unused resources
        if (webSocketClient != null) {
            webSocketClient.forceMemoryCleanup();
        }
        
        // Clear any cached data
        if (onlineUsers.getValue() != null && onlineUsers.getValue().isEmpty()) {
            // If there are no online users, set to null to free memory
            onlineUsers.setValue(null);
        }
        
        // Suggest garbage collection
        System.gc();
    }
    
    /**
     * Send a direct message to a specific user
     * @param recipientId ID of the user to send the message to
     * @param message The message content
     * @return true if send was successful, false otherwise
     */
    public boolean sendDirectMessage(String recipientId, String message) {
        boolean success = webSocketClient.sendDirectMessage(recipientId, message);
        
        if (success) {
            // Add sent message to latest message for local display
            ChatMessage chatMessage = new ChatMessage(
                    ChatMessage.MessageType.DIRECT,
                    userId,
                    recipientId,
                    message,
                    System.currentTimeMillis()
            );
            chatMessage.setSelf(true);
            latestMessage.setValue(chatMessage);
        }
        
        return success;
    }
    
    /**
     * Send a text message
     * @param message The message content
     * @return true if send was successful, false otherwise
     */
    public boolean sendMessage(String message) {
        boolean success = webSocketClient.sendMessage(message);
        
        if (success) {
            // Message will be echoed back from server and handled in listener
        }
        
        return success;
    }
    
    /**
     * Get the connection state as LiveData
     * @return LiveData of the connection state
     */
    public LiveData<WebSocketClient.WebSocketState> getConnectionState() {
        return connectionState;
    }
    
    /**
     * Get online users as LiveData
     * @return LiveData map of user ID to status
     */
    public LiveData<Map<String, UserStatus>> getOnlineUsers() {
        return onlineUsers;
    }
    
    /**
     * Get the latest message as LiveData
     * @return LiveData of the latest message
     */
    public LiveData<ChatMessage> getLatestMessage() {
        return latestMessage;
    }
    
    /**
     * Get the user ID of the current connection
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * Get the current server URL from preferences
     * @return The current server URL
     */
    public String getCurrentServerUrl() {
        // We need to use the constant that's already defined in this class
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("server_url", DEFAULT_SERVER_URL);
    }
    
    /**
     * Update the server URL setting
     * @param serverUrl The new server URL
     */
    public void updateServerUrl(String serverUrl) {
        // Store server URL in preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("server_url", serverUrl).apply();
        
        // Reconnect with new URL
        boolean wasConnected = webSocketClient.getState() == WebSocketClient.WebSocketState.CONNECTED;
        disconnect();
        
        if (wasConnected && userId != null) {
            connect(userId);
        }
    }
    
    /**
     * Request to refresh conversations list from the server
     * @return true if request was sent successfully, false otherwise
     */
    public boolean requestConversations() {
        // Check if connected
        if (connectionState.getValue() != WebSocketClient.WebSocketState.CONNECTED) {
            Log.d(TAG, "Cannot request conversations: Not connected");
            return false;
        }
        
        // Create a conversation list request message
        JsonObject request = new JsonObject();
        request.addProperty("type", "request");
        request.addProperty("resource", "conversations");
        
        // Send the request
        return webSocketClient.send(request.toString());
    }
    
    /**
     * Get online users as a list
     * @return LiveData containing a list of user IDs
     */
    public LiveData<List<String>> getOnlineUsersList() {
        // Convert the map to a list for compatibility with old code
        MutableLiveData<List<String>> userList = new MutableLiveData<>(new ArrayList<>());
        
        // Update the list whenever the map changes
        onlineUsers.observeForever(users -> {
            if (users != null) {
                userList.setValue(new ArrayList<>(users.keySet()));
            } else {
                userList.setValue(new ArrayList<>());
            }
        });
        
        return userList;
    }
    
    /**
     * Reset the server URL to default
     * This can be called when connection issues are detected
     */
    public void resetServerUrlToDefault() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("server_url", DEFAULT_SERVER_URL).apply();
        Log.d(TAG, "Reset server URL to default: " + DEFAULT_SERVER_URL);
        
        // Also update the PREF_SERVER_URL key
        prefs.edit().putString(PREF_SERVER_URL, DEFAULT_SERVER_URL).apply();
        
        // Notify user that settings have been reset
        disconnect();
        
        // Attempt to reconnect if there's a user ID
        if (userId != null && !userId.isEmpty()) {
            connect(userId);
        }
    }
    
    /**
     * A chat message
     */
    public static class ChatMessage {
        public enum MessageType {
            DIRECT,
            ECHO,
            SYSTEM,
            ERROR,
            MESSAGE
        }
        
        private final MessageType type;
        private final String senderId;
        private final String recipientId;
        private final String content;
        private final long timestamp;
        private boolean self = false;
        
        public ChatMessage(MessageType type, String senderId, String recipientId, String content, long timestamp) {
            this.type = type;
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.content = content;
            this.timestamp = timestamp;
        }
        
        public MessageType getType() {
            return type;
        }
        
        public String getSenderId() {
            return senderId;
        }
        
        public String getRecipientId() {
            return recipientId;
        }
        
        public String getContent() {
            return content;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public boolean isSelf() {
            return self;
        }
        
        public void setSelf(boolean self) {
            this.self = self;
        }
    }
    
    /**
     * Get chat events LiveData
     */
    public LiveData<ChatEvent> getChatEvents() {
        return chatEvents;
    }
} 