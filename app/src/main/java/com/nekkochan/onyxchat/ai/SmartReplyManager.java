package com.nekkochan.onyxchat.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.nl.smartreply.SmartReply;
import com.google.mlkit.nl.smartreply.SmartReplyGenerator;
import com.google.mlkit.nl.smartreply.SmartReplySuggestion;
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult;
import com.google.mlkit.nl.smartreply.TextMessage;
import com.nekkochan.onyxchat.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager class for handling smart reply suggestions using ML Kit
 */
public class SmartReplyManager {
    private static final String TAG = "SmartReplyManager";
    private static SmartReplyManager instance;
    
    private final SmartReplyGenerator smartReplyGenerator;
    private final List<TextMessage> conversation = new ArrayList<>();
    private String currentUserId;
    
    /**
     * Get the singleton instance of SmartReplyManager
     * 
     * @param context The application context
     * @return The SmartReplyManager instance
     */
    public static synchronized SmartReplyManager getInstance(Context context) {
        if (instance == null) {
            instance = new SmartReplyManager();
        }
        return instance;
    }
    
    private SmartReplyManager() {
        smartReplyGenerator = SmartReply.getClient();
    }
    
    /**
     * Set the current user ID for identifying sent vs received messages
     * 
     * @param userId The current user's ID
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
    
    /**
     * Add a message to the conversation history for smart reply context
     * 
     * @param message The message to add
     */
    public void addMessage(Message message) {
        if (message == null || message.getContent() == null) {
            return;
        }
        
        boolean isLocalUser = message.getSenderAddress().equals(currentUserId);
        TextMessage textMessage;
        if (isLocalUser) {
            textMessage = TextMessage.createForLocalUser(
                    message.getContent(),
                    message.getTimestamp()
            );
        } else {
            textMessage = TextMessage.createForRemoteUser(
                    message.getContent(),
                    message.getTimestamp(),
                    message.getSenderAddress()
            );
        }
        
        conversation.add(textMessage);
        
        // Keep conversation history manageable (last 10 messages)
        if (conversation.size() > 10) {
            conversation.remove(0);
        }
    }
    
    /**
     * Clear the conversation history
     */
    public void clearConversation() {
        conversation.clear();
    }
    
    /**
     * Generate smart reply suggestions based on conversation history
     * 
     * @param callback Callback to receive the smart reply suggestions
     */
    public void generateReplies(final SmartReplyCallback callback) {
        if (conversation.isEmpty()) {
            callback.onSmartRepliesGenerated(new ArrayList<>());
            return;
        }
        
        smartReplyGenerator.suggestReplies(conversation)
                .addOnSuccessListener(result -> {
                    if (result.getStatus() == SmartReplySuggestionResult.STATUS_SUCCESS) {
                        List<String> suggestions = new ArrayList<>();
                        for (SmartReplySuggestion suggestion : result.getSuggestions()) {
                            suggestions.add(suggestion.getText());
                        }
                        callback.onSmartRepliesGenerated(suggestions);
                    } else {
                        Log.d(TAG, "No suggestions available: " + result.getStatus());
                        callback.onSmartRepliesGenerated(new ArrayList<>());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error generating smart replies", e);
                    callback.onSmartRepliesGenerated(new ArrayList<>());
                });
    }
    
    /**
     * Release resources when no longer needed
     */
    public void shutdown() {
        smartReplyGenerator.close();
    }
    
    /**
     * Callback interface for receiving smart reply suggestions
     */
    public interface SmartReplyCallback {
        void onSmartRepliesGenerated(List<String> suggestions);
    }
}
