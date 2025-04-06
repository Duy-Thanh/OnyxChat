package com.nekkochan.onyxchat.ui.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.nekkochan.onyxchat.network.ApiClient;
import com.nekkochan.onyxchat.network.ChatService;
import com.nekkochan.onyxchat.network.WebSocketClient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ViewModel for the chat screen
 */
public class ChatViewModel extends AndroidViewModel {
    
    private static final String TAG = "ChatViewModel";
    
    private final ChatService chatService;
    private final MutableLiveData<List<ChatMessage>> chatMessages;
    private final String userId;
    private String currentRecipientId;
    
    public ChatViewModel(@NonNull Application application) {
        super(application);
        
        // Initialize chat service
        chatService = ChatService.getInstance(application);
        
        // Store the current user ID
        this.userId = chatService.getUserId();
        
        // Initialize messages list
        chatMessages = new MutableLiveData<>(new ArrayList<>());
        
        // Observe latest message from the chat service
        chatService.getLatestMessage().observeForever(message -> {
            if (message != null) {
                // Only process messages for the current recipient
                if (currentRecipientId != null && 
                        (message.getSenderId().equals(currentRecipientId) || 
                         message.getRecipientId() != null && message.getRecipientId().equals(currentRecipientId))) {
                    
                    // Add message to the list
                    List<ChatMessage> messages = chatMessages.getValue();
                    if (messages == null) {
                        messages = new ArrayList<>();
                    }
                    
                    // Add new message
                    messages.add(new ChatMessage(
                            message.getType().name(),
                            message.getSenderId(),
                            message.getRecipientId(),
                            message.getContent(),
                            new Date(message.getTimestamp())
                    ));
                    
                    // Update LiveData
                    chatMessages.postValue(messages);
                }
            }
        });
    }
    
    /**
     * Set the current recipient for direct messages
     */
    public void setCurrentRecipient(String recipientId) {
        this.currentRecipientId = recipientId;
        
        // Load messages for this recipient
        if (recipientId != null) {
            loadMessages(recipientId);
        }
    }
    
    /**
     * Load messages between the current user and the specified recipient
     */
    private void loadMessages(String recipientId) {
        // Clear existing messages first
        chatMessages.setValue(new ArrayList<>());
        
        // Load messages from API
        ApiClient.getInstance(getApplication().getApplicationContext())
            .getMessages(recipientId, new ApiClient.ApiCallback<List<ApiClient.MessageResponse>>() {
                @Override
                public void onSuccess(List<ApiClient.MessageResponse> result) {
                    if (result != null) {
                        List<ChatMessage> messages = new ArrayList<>();
                        
                        for (ApiClient.MessageResponse message : result) {
                            // Convert API message to ChatMessage
                            messages.add(new ChatMessage(
                                "DIRECT", // Assuming all are direct messages
                                message.getSenderId(),
                                message.getRecipientId(),
                                message.getContent(),
                                message.getCreatedAt()
                            ));
                        }
                        
                        // Update UI on main thread
                        new Handler(Looper.getMainLooper()).post(() -> {
                            chatMessages.setValue(messages);
                        });
                    }
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "Error loading messages: " + errorMessage);
                }
            });
    }
    
    /**
     * Get the current user ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * Connect to the chat server
     * @return true if connection was successful
     */
    public boolean connectToChat() {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        return chatService.connect(userId);
    }
    
    /**
     * Disconnect from the chat server
     */
    public void disconnectFromChat() {
        chatService.disconnect();
    }
    
    /**
     * Send a direct message to the specified recipient
     * @param recipientId the recipient's ID
     * @param message the message to send
     * @return true if the message was sent successfully
     */
    public boolean sendDirectMessage(String recipientId, String message) {
        return chatService.sendDirectMessage(recipientId, message);
    }
    
    /**
     * Send a message to the chat room
     * @param message the message to send
     * @return true if the message was sent successfully
     */
    public boolean sendChatMessage(String message) {
        return chatService.sendMessage(message);
    }
    
    /**
     * Get the chat connection status
     * @return LiveData containing the connection status
     */
    public LiveData<Boolean> isChatConnected() {
        return Transformations.map(
            chatService.getConnectionState(),
            state -> state == WebSocketClient.WebSocketState.CONNECTED
        );
    }
    
    /**
     * Get the chat messages
     * @return LiveData containing the list of chat messages
     */
    public LiveData<List<ChatMessage>> getChatMessages() {
        return chatMessages;
    }
    
    /**
     * A chat message class that represents a message in the chat
     */
    public static class ChatMessage {
        private final String type;
        private final String senderId;
        private final String recipientId;
        private final String content;
        private final Date timestamp;
        
        public ChatMessage(String type, String senderId, String recipientId, String content, Date timestamp) {
            this.type = type;
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.content = content;
            this.timestamp = timestamp;
        }
        
        public String getType() {
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
        
        public Date getTimestamp() {
            return timestamp;
        }
    }
} 