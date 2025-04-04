package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing chat functionality
 */
public class ChatService {
    private static final String TAG = "ChatService";
    private static final String PREF_SERVER_URL = "chat_server_url";
    private static final String DEFAULT_SERVER_URL = "ws://10.0.2.2:8082/ws/"; // localhost for emulator
    
    private static ChatService instance;
    
    private final Context context;
    private final WebSocketClient webSocketClient;
    private final MutableLiveData<WebSocketClient.WebSocketState> connectionState;
    private final MutableLiveData<Map<String, String>> onlineUsers;
    private final MutableLiveData<ChatMessage> latestMessage;
    private final Gson gson = new Gson();
    private String userId;
    
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
                try {
                    // Try to parse as JSON
                    JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
                    
                    // Process different types of messages
                    if (jsonMessage.has("type")) {
                        String type = jsonMessage.get("type").getAsString();
                        
                        if ("direct".equals(type)) {
                            // Direct message received
                            String sender = jsonMessage.get("sender").getAsString();
                            String content = jsonMessage.get("content").getAsString();
                            
                            ChatMessage chatMessage = new ChatMessage(
                                    ChatMessage.MessageType.DIRECT,
                                    sender,
                                    userId, // me as recipient
                                    content,
                                    System.currentTimeMillis()
                            );
                            latestMessage.postValue(chatMessage);
                        } else if ("status".equals(type)) {
                            // User status message
                            String statusUserId = jsonMessage.get("user").getAsString();
                            String status = jsonMessage.get("status").getAsString();
                            
                            // Update online users map
                            Map<String, String> users = onlineUsers.getValue();
                            if (users == null) {
                                users = new HashMap<>();
                            }
                            
                            if ("online".equals(status)) {
                                users.put(statusUserId, status);
                            } else if ("offline".equals(status)) {
                                users.remove(statusUserId);
                            }
                            
                            onlineUsers.postValue(users);
                            
                            // Also post a system message about the status change
                            ChatMessage chatMessage = new ChatMessage(
                                    ChatMessage.MessageType.SYSTEM,
                                    "server",
                                    userId,
                                    "User " + statusUserId + " is " + status,
                                    System.currentTimeMillis()
                            );
                            latestMessage.postValue(chatMessage);
                        } else if ("echo".equals(type)) {
                            // Echo message (confirmation of our message)
                            String content = jsonMessage.get("content").getAsString();
                            
                            ChatMessage chatMessage = new ChatMessage(
                                    ChatMessage.MessageType.ECHO,
                                    userId, // me as sender
                                    "server",
                                    content,
                                    System.currentTimeMillis()
                            );
                            chatMessage.setSelf(true);
                            latestMessage.postValue(chatMessage);
                        } else if ("error".equals(type)) {
                            // Error message
                            String content = jsonMessage.get("content").getAsString();
                            
                            ChatMessage chatMessage = new ChatMessage(
                                    ChatMessage.MessageType.ERROR,
                                    "server",
                                    userId,
                                    content,
                                    System.currentTimeMillis()
                            );
                            latestMessage.postValue(chatMessage);
                        } else {
                            // Other message types
                            ChatMessage chatMessage = new ChatMessage(
                                    ChatMessage.MessageType.SYSTEM,
                                    "server",
                                    userId,
                                    message,
                                    System.currentTimeMillis()
                            );
                            latestMessage.postValue(chatMessage);
                        }
                    } else {
                        // Plain text message as a system message
                        ChatMessage chatMessage = new ChatMessage(
                                ChatMessage.MessageType.SYSTEM,
                                "server",
                                userId,
                                message,
                                System.currentTimeMillis()
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
                            System.currentTimeMillis()
                    );
                    latestMessage.postValue(chatMessage);
                }
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
     * Connect to the chat server with the specified user ID
     * @param userId The user ID to connect with
     * @return true if connection attempt started, false otherwise
     */
    public boolean connect(String userId) {
        this.userId = userId;
        connectionState.setValue(WebSocketClient.WebSocketState.CONNECTING);
        return webSocketClient.connect(userId);
    }
    
    /**
     * Disconnect from the chat server
     */
    public void disconnect() {
        webSocketClient.disconnect();
        connectionState.setValue(WebSocketClient.WebSocketState.DISCONNECTED);
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
    public LiveData<Map<String, String>> getOnlineUsers() {
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
     * Update the server URL in preferences
     * @param serverUrl The new server URL
     */
    public void updateServerUrl(String serverUrl) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(PREF_SERVER_URL, serverUrl).apply();
        
        // Reconnect with new URL
        boolean wasConnected = webSocketClient.getState() == WebSocketClient.WebSocketState.CONNECTED;
        disconnect();
        
        if (wasConnected && userId != null) {
            connect(userId);
        }
    }
    
    /**
     * Get the online users
     * @return LiveData containing a list of online users
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
     * A chat message
     */
    public static class ChatMessage {
        public enum MessageType {
            DIRECT,
            ECHO,
            SYSTEM,
            ERROR
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
} 