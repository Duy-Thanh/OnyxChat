package com.nekkochan.onyxchat.ai;

import android.content.Context;
import android.util.Log;

import com.nekkochan.onyxchat.model.Message;
import com.nekkochan.onyxchat.util.PreferenceManager;

import java.util.List;

/**
 * Central manager class for coordinating all AI features in the application
 */
public class AIFeatureManager {
    private static final String TAG = "AIFeatureManager";
    private static AIFeatureManager instance;
    
    private final Context context;
    private final SmartReplyManager smartReplyManager;
    private final TranslationManager translationManager;
    private final ContentModerationManager contentModerationManager;
    private final PreferenceManager preferenceManager;
    
    // Feature flags
    private boolean smartRepliesEnabled = true;
    private boolean translationEnabled = true;
    private boolean contentModerationEnabled = true;
    
    /**
     * Get the singleton instance of AIFeatureManager
     * 
     * @param context The application context
     * @return The AIFeatureManager instance
     */
    public static synchronized AIFeatureManager getInstance(Context context) {
        if (instance == null) {
            instance = new AIFeatureManager(context.getApplicationContext());
        }
        return instance;
    }
    
    private AIFeatureManager(Context context) {
        this.context = context;
        this.smartReplyManager = SmartReplyManager.getInstance(context);
        this.translationManager = TranslationManager.getInstance(context);
        this.contentModerationManager = ContentModerationManager.getInstance(context);
        this.preferenceManager = new PreferenceManager(context);
        
        // Load user preferences
        loadPreferences();
    }
    
    /**
     * Load user preferences for AI features
     */
    private void loadPreferences() {
        smartRepliesEnabled = preferenceManager.getBoolean("pref_smart_replies_enabled", true);
        translationEnabled = preferenceManager.getBoolean("pref_translation_enabled", true);
        contentModerationEnabled = preferenceManager.getBoolean("pref_content_moderation_enabled", true);
        
        // Set user ID for smart replies
        String userId = preferenceManager.getString("pref_user_id", "");
        if (!userId.isEmpty()) {
            smartReplyManager.setCurrentUserId(userId);
        }
        
        // Set preferred language for translation
        String preferredLanguage = preferenceManager.getString("pref_preferred_language", "en");
        translationManager.setPreferredLanguage(preferredLanguage);
    }
    
    /**
     * Set the current user ID for smart replies
     * 
     * @param userId The current user's ID
     */
    public void setCurrentUserId(String userId) {
        smartReplyManager.setCurrentUserId(userId);
        preferenceManager.putString("pref_user_id", userId);
    }
    
    /**
     * Set the user's preferred language for translations
     * 
     * @param languageCode The language code (e.g., "en", "es", "fr")
     */
    public void setPreferredLanguage(String languageCode) {
        translationManager.setPreferredLanguage(languageCode);
        preferenceManager.putString("pref_preferred_language", languageCode);
    }
    
    /**
     * Enable or disable smart replies
     * 
     * @param enabled True to enable, false to disable
     */
    public void setSmartRepliesEnabled(boolean enabled) {
        this.smartRepliesEnabled = enabled;
        preferenceManager.putBoolean("pref_smart_replies_enabled", enabled);
    }
    
    /**
     * Enable or disable translation
     * 
     * @param enabled True to enable, false to disable
     */
    public void setTranslationEnabled(boolean enabled) {
        this.translationEnabled = enabled;
        preferenceManager.putBoolean("pref_translation_enabled", enabled);
    }
    
    /**
     * Enable or disable content moderation
     * 
     * @param enabled True to enable, false to disable
     */
    public void setContentModerationEnabled(boolean enabled) {
        this.contentModerationEnabled = enabled;
        contentModerationManager.setModerationEnabled(enabled);
        preferenceManager.putBoolean("pref_content_moderation_enabled", enabled);
    }
    
    /**
     * Set the content moderation level
     * 
     * @param level The moderation level
     */
    public void setModerationLevel(ContentModerationManager.ModerationLevel level) {
        contentModerationManager.setModerationLevel(level);
    }
    
    /**
     * Check if smart replies are enabled
     * 
     * @return True if enabled, false otherwise
     */
    public boolean isSmartRepliesEnabled() {
        return smartRepliesEnabled;
    }
    
    /**
     * Check if translation is enabled
     * 
     * @return True if enabled, false otherwise
     */
    public boolean isTranslationEnabled() {
        return translationEnabled;
    }
    
    /**
     * Check if content moderation is enabled
     * 
     * @return True if enabled, false otherwise
     */
    public boolean isContentModerationEnabled() {
        return contentModerationEnabled;
    }
    
    /**
     * Process an incoming message with all enabled AI features
     * 
     * @param message The message to process
     * @param callback Callback to receive processing results
     */
    public void processIncomingMessage(Message message, final MessageProcessingCallback callback) {
        if (message == null || message.getContent() == null || message.getContent().isEmpty()) {
            callback.onMessageProcessed(message, null, null);
            return;
        }
        
        // Add message to smart reply context
        if (smartRepliesEnabled) {
            smartReplyManager.addMessage(message);
        }
        
        // If translation is enabled, translate the message
        if (translationEnabled) {
            translationManager.translateToPreferredLanguage(message.getContent(), translatedText -> {
                // If the text was translated (different from original), provide both versions
                if (!translatedText.equals(message.getContent())) {
                    Message translatedMessage = new Message(message);
                    translatedMessage.setContent(translatedText);
                    translatedMessage.setTranslated(true);
                    
                    callback.onMessageProcessed(message, translatedMessage, null);
                } else {
                    callback.onMessageProcessed(message, null, null);
                }
            });
        } else {
            callback.onMessageProcessed(message, null, null);
        }
    }
    
    /**
     * Process an outgoing message with content moderation
     * 
     * @param message The message to process
     * @param callback Callback to receive processing results
     */
    public void processOutgoingMessage(Message message, final MessageProcessingCallback callback) {
        if (message == null || message.getContent() == null || message.getContent().isEmpty()) {
            callback.onMessageProcessed(message, null, null);
            return;
        }
        
        // Add message to smart reply context
        if (smartRepliesEnabled) {
            smartReplyManager.addMessage(message);
        }
        
        // If content moderation is enabled, check the message
        if (contentModerationEnabled) {
            contentModerationManager.moderateMessage(message.getContent(), result -> {
                if (result.isFlagged()) {
                    callback.onMessageProcessed(message, null, result);
                } else {
                    callback.onMessageProcessed(message, null, null);
                }
            });
        } else {
            callback.onMessageProcessed(message, null, null);
        }
    }
    
    /**
     * Generate smart reply suggestions for the current conversation
     * 
     * @param callback Callback to receive smart reply suggestions
     */
    public void generateSmartReplies(final SmartReplyManager.SmartReplyCallback callback) {
        if (!smartRepliesEnabled) {
            callback.onSmartRepliesGenerated(null);
            return;
        }
        
        smartReplyManager.generateReplies(callback);
    }
    
    /**
     * Moderate content and provide a result through the callback
     * 
     * @param content The content to moderate
     * @param callback Callback to receive moderation results
     */
    public void moderateContent(String content, final ContentModerationCallback callback) {
        if (!contentModerationEnabled || content == null || content.isEmpty()) {
            callback.onContentModerated(content, ContentModerationManager.ModerationResult.SAFE);
            return;
        }
        
        contentModerationManager.moderateMessage(content, result -> {
            ContentModerationManager.ModerationResult moderationResult = 
                    result.isFlagged() ? 
                    ContentModerationManager.ModerationResult.FLAGGED : 
                    ContentModerationManager.ModerationResult.SAFE;
            
            callback.onContentModerated(content, moderationResult);
        });
    }
    
    /**
     * Add a message to the smart reply context without processing
     * 
     * @param message The message to add
     */
    public void addMessageToSmartReplyContext(Message message) {
        if (smartRepliesEnabled && message != null) {
            smartReplyManager.addMessage(message);
        }
    }
    
    /**
     * Release resources when no longer needed
     */
    public void shutdown() {
        smartReplyManager.shutdown();
        translationManager.shutdown();
    }
    
    /**
     * Callback interface for message processing
     */
    public interface MessageProcessingCallback {
        /**
         * Called when message processing is complete
         * 
         * @param originalMessage The original message
         * @param translatedMessage The translated message (if translation was performed), or null
         * @param moderationResult The moderation result (if moderation was performed), or null
         */
        void onMessageProcessed(Message originalMessage, 
                               Message translatedMessage,
                               ContentModerationManager.ModeratedContent moderationResult);
    }
    
    /**
     * Callback interface for content moderation
     */
    public interface ContentModerationCallback {
        /**
         * Called when content moderation is complete
         * 
         * @param originalContent The original content
         * @param result The moderation result
         */
        void onContentModerated(String originalContent, ContentModerationManager.ModerationResult result);
    }
}
