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
import com.nekkochan.onyxchat.ui.chat.ChatMessageItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONObject;

/**
 * ViewModel for the chat screen
 */
public class ChatViewModel extends AndroidViewModel {
    
    private static final String TAG = "ChatViewModel";
    
    private final ChatService chatService;
    private final MutableLiveData<List<ChatMessage>> chatMessages;
    private final String userId;
    private String currentRecipientId;
    
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    
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
        isLoading.setValue(true);
        
        Log.d(TAG, "Loading messages for contact: " + recipientId);
        
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
                            // Log timestamp details for debugging
                            Date createdAt = message.getCreatedAt();
                            long timestamp = createdAt != null ? createdAt.getTime() : System.currentTimeMillis();
                            
                            Log.d(TAG, String.format(
                                "Message from API - ID: %s, Content: %s, Timestamp: %d (%s)", 
                                message.getId(), 
                                message.getContent(),
                                timestamp,
                                createdAt != null ? formatDateForLogging(createdAt) : "null"));
                            
                            // Convert API message to ChatMessage
                            messages.add(new ChatMessage(
                                "DIRECT", // Assuming all are direct messages
                                message.getSenderId(),
                                message.getRecipientId(),
                                message.getContent(),
                                new Date(timestamp) // Ensure we use the server timestamp
                            ));
                        }
                        
                        // Sort messages by timestamp
                        Collections.sort(messages, (m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
                        
                        // Log the sorted message timestamps for debugging
                        if (!messages.isEmpty()) {
                            Log.d(TAG, "Loaded and sorted " + messages.size() + " messages");
                            Log.d(TAG, "First message timestamp: " + formatDateForLogging(messages.get(0).getTimestamp()));
                            Log.d(TAG, "Last message timestamp: " + formatDateForLogging(messages.get(messages.size() - 1).getTimestamp()));
                        } else {
                            Log.d(TAG, "No messages loaded from API");
                        }
                        
                        // Update UI on main thread
                        new Handler(Looper.getMainLooper()).post(() -> {
                            chatMessages.setValue(messages);
                        });
                    } else {
                        Log.e(TAG, "API returned null messages");
                        errorMessage.setValue("Failed to load messages");
                    }
                    isLoading.setValue(false);
                }

                @Override
                public void onFailure(String errorMsg) {
                    Log.e(TAG, "Error loading messages: " + errorMsg);
                    errorMessage.setValue(errorMsg);
                    isLoading.setValue(false);
                }
            });
    }
    
    /**
     * Format a date for consistent logging
     */
    private String formatDateForLogging(Date date) {
        if (date == null) return "null";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date) + " [" + date.getTime() + "ms]";
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
        if (currentRecipientId == null || currentRecipientId.isEmpty()) {
            return false;
        }
        return sendDirectMessage(currentRecipientId, message);
    }
    
    /**
     * Send a media message to the current recipient
     */
    public boolean sendMediaMessage(String mediaUrl, String mediaType, String caption) {
        if (currentRecipientId == null || currentRecipientId.isEmpty()) {
            return false;
        }
        
        try {
            // Create a JSON object for the media message
            JSONObject mediaContent = new JSONObject();
            mediaContent.put("type", mediaType);
            mediaContent.put("url", mediaUrl);
            if (caption != null && !caption.isEmpty()) {
                mediaContent.put("caption", caption);
            }
            
            return sendMessage(mediaContent.toString(), ChatMessageItem.MessageType.valueOf(mediaType));
        } catch (Exception e) {
            Log.e(TAG, "Error creating media message", e);
            errorMessage.setValue("Failed to send media: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send a message with specific content type
     */
    public boolean sendMessage(String content, ChatMessageItem.MessageType messageType) {
        if (currentRecipientId == null || currentRecipientId.isEmpty()) {
            return false;
        }
        
        // Send the message to the recipient
        boolean sent = chatService.sendDirectMessage(currentRecipientId, content);
        
        if (sent) {
            // Add to local messages (optimistic update)
            List<ChatMessage> messages = chatMessages.getValue();
            if (messages == null) {
                messages = new ArrayList<>();
            }
            
            Date now = new Date();
            messages.add(new ChatMessage(
                messageType.name(),
                userId,
                currentRecipientId,
                content,
                now
            ));
            
            // Update LiveData
            chatMessages.setValue(messages);
        } else {
            errorMessage.setValue("Failed to send message");
        }
        
        return sent;
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
     * Get the error message
     * @return LiveData containing the error message
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Get the loading state
     * @return LiveData containing the loading state
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
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