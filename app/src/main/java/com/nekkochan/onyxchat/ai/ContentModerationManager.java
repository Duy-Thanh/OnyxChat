package com.nekkochan.onyxchat.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.nekkochan.onyxchat.model.Message;
import com.nekkochan.onyxchat.util.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Manager class for handling content moderation using a combination of
 * local pattern matching and cloud-based content moderation API
 */
public class ContentModerationManager {
    private static final String TAG = "ContentModerationManager";
    private static ContentModerationManager instance;
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String MODERATION_API_URL = "https://api.onyxchat.com/moderation"; // Replace with actual API endpoint
    
    private final OkHttpClient httpClient;
    private final Context context;
    private final Map<String, Pattern> patternMap = new HashMap<>();
    private boolean moderationEnabled = true;
    private ModerationLevel moderationLevel = ModerationLevel.MEDIUM;
    
    /**
     * Get the singleton instance of ContentModerationManager
     * 
     * @param context The application context
     * @return The ContentModerationManager instance
     */
    public static synchronized ContentModerationManager getInstance(Context context) {
        if (instance == null) {
            instance = new ContentModerationManager(context.getApplicationContext());
        }
        return instance;
    }
    
    private ContentModerationManager(Context context) {
        this.context = context;
        
        // Initialize HTTP client with timeouts
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Initialize regex patterns for local content moderation
        initializePatterns();
        
        // Load user preferences
        loadPreferences();
    }
    
    /**
     * Initialize regex patterns for local content moderation
     */
    private void initializePatterns() {
        // Basic profanity filter patterns (can be expanded)
        patternMap.put("profanity", Pattern.compile("\\b(fuck|shit|ass|bitch|damn|cunt|dick)\\b", 
                Pattern.CASE_INSENSITIVE));
        
        // Personal information patterns
        patternMap.put("credit_card", Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b"));
        patternMap.put("phone", Pattern.compile("\\b(?:\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b"));
        patternMap.put("email", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"));
        
        // URL patterns for potentially malicious links
        patternMap.put("suspicious_url", Pattern.compile("\\b(?:https?://)?(?:www\\.)?([^\\s.]+\\.(?:xyz|info|top|gq|ml|ga|cf|tk|biz)(?:/\\S*)?)\\b", 
                Pattern.CASE_INSENSITIVE));
    }
    
    /**
     * Load user preferences for content moderation
     */
    private void loadPreferences() {
        PreferenceManager prefManager = new PreferenceManager(context);
        moderationEnabled = prefManager.getBoolean("pref_content_moderation_enabled", true);
        
        String levelStr = prefManager.getString("pref_moderation_level", ModerationLevel.MEDIUM.name());
        try {
            moderationLevel = ModerationLevel.valueOf(levelStr);
        } catch (IllegalArgumentException e) {
            moderationLevel = ModerationLevel.MEDIUM;
        }
    }
    
    /**
     * Set whether content moderation is enabled
     * 
     * @param enabled True to enable moderation, false to disable
     */
    public void setModerationEnabled(boolean enabled) {
        this.moderationEnabled = enabled;
        PreferenceManager prefManager = new PreferenceManager(context);
        prefManager.putBoolean("pref_content_moderation_enabled", enabled);
    }
    
    /**
     * Set the moderation level
     * 
     * @param level The moderation level
     */
    public void setModerationLevel(ModerationLevel level) {
        this.moderationLevel = level;
        PreferenceManager prefManager = new PreferenceManager(context);
        prefManager.putString("pref_moderation_level", level.name());
    }
    
    /**
     * Check if content moderation is enabled
     * 
     * @return True if enabled, false otherwise
     */
    public boolean isModerationEnabled() {
        return moderationEnabled;
    }
    
    /**
     * Get the current moderation level
     * 
     * @return The moderation level
     */
    public ModerationLevel getModerationLevel() {
        return moderationLevel;
    }
    
    /**
     * Moderate a message locally using pattern matching
     * 
     * @param message The message to moderate
     * @return A ModeratedContent object with the moderation results
     */
    public ModeratedContent moderateLocally(String message) {
        if (!moderationEnabled || message == null || message.isEmpty()) {
            return new ModeratedContent(message, false, new ArrayList<>());
        }
        
        List<String> flaggedCategories = new ArrayList<>();
        
        // Check against each pattern based on moderation level
        for (Map.Entry<String, Pattern> entry : patternMap.entrySet()) {
            String category = entry.getKey();
            Pattern pattern = entry.getValue();
            
            // Skip certain categories based on moderation level
            if (moderationLevel == ModerationLevel.LOW && 
                    (category.equals("profanity"))) {
                continue;
            }
            
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                flaggedCategories.add(category);
            }
        }
        
        boolean isFlagged = !flaggedCategories.isEmpty();
        return new ModeratedContent(message, isFlagged, flaggedCategories);
    }
    
    /**
     * Moderate a message using cloud-based API for more advanced detection
     * 
     * @param message The message to moderate
     * @param callback Callback to receive the moderation results
     */
    public void moderateWithApi(String message, final ModerationCallback callback) {
        if (!moderationEnabled || message == null || message.isEmpty()) {
            callback.onModerationComplete(new ModeratedContent(message, false, new ArrayList<>()));
            return;
        }
        
        // First perform local moderation
        ModeratedContent localResult = moderateLocally(message);
        
        // If already flagged locally or moderation level is LOW, return local result
        if (localResult.isFlagged() || moderationLevel == ModerationLevel.LOW) {
            callback.onModerationComplete(localResult);
            return;
        }
        
        try {
            // Prepare request body
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("text", message);
            jsonBody.put("level", moderationLevel.name().toLowerCase());
            
            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(MODERATION_API_URL)
                    .post(body)
                    .build();
            
            // Execute the request asynchronously
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "API moderation failed", e);
                    // Fall back to local result on API failure
                    callback.onModerationComplete(localResult);
                }
                
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected response code: " + response);
                        }
                        
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        boolean isFlagged = jsonResponse.getBoolean("flagged");
                        List<String> categories = new ArrayList<>();
                        
                        if (isFlagged && jsonResponse.has("categories")) {
                            JSONArray categoriesArray = jsonResponse.getJSONArray("categories");
                            for (int i = 0; i < categoriesArray.length(); i++) {
                                categories.add(categoriesArray.getString(i));
                            }
                        }
                        
                        ModeratedContent apiResult = new ModeratedContent(message, isFlagged, categories);
                        callback.onModerationComplete(apiResult);
                        
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing moderation API response", e);
                        callback.onModerationComplete(localResult);
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating moderation API request", e);
            callback.onModerationComplete(localResult);
        }
    }
    
    /**
     * Moderate a message using the appropriate method based on settings
     * 
     * @param message The message to moderate
     * @param callback Callback to receive the moderation results
     */
    public void moderateMessage(String message, final ModerationCallback callback) {
        if (!moderationEnabled) {
            callback.onModerationComplete(new ModeratedContent(message, false, new ArrayList<>()));
            return;
        }
        
        // For HIGH level, use API; otherwise use local moderation
        if (moderationLevel == ModerationLevel.HIGH) {
            moderateWithApi(message, callback);
        } else {
            ModeratedContent result = moderateLocally(message);
            callback.onModerationComplete(result);
        }
    }
    
    /**
     * Moderation levels
     */
    public enum ModerationLevel {
        LOW,     // Basic filtering (sensitive personal information)
        MEDIUM,  // Standard filtering (profanity + personal information)
        HIGH     // Strict filtering (uses cloud API for advanced detection)
    }
    
    /**
     * Moderation results
     */
    public enum ModerationResult {
        SAFE,    // Content is safe to send
        FLAGGED  // Content has been flagged for review
    }
    
    /**
     * Class representing moderated content with results
     */
    public static class ModeratedContent {
        private final String content;
        private final boolean flagged;
        private final List<String> categories;
        
        public ModeratedContent(String content, boolean flagged, List<String> categories) {
            this.content = content;
            this.flagged = flagged;
            this.categories = categories;
        }
        
        public String getContent() {
            return content;
        }
        
        public boolean isFlagged() {
            return flagged;
        }
        
        public List<String> getCategories() {
            return categories;
        }
        
        /**
         * Get a user-friendly description of why the content was flagged
         * 
         * @return A description of the flagged categories
         */
        public String getFlaggedReason() {
            if (!flagged || categories.isEmpty()) {
                return "";
            }
            
            StringBuilder reason = new StringBuilder("Content flagged for: ");
            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i);
                // Convert category name to user-friendly format
                String friendlyName = category.replace("_", " ");
                reason.append(friendlyName);
                
                if (i < categories.size() - 1) {
                    reason.append(", ");
                }
            }
            
            return reason.toString();
        }
    }
    
    /**
     * Callback interface for content moderation
     */
    public interface ModerationCallback {
        void onModerationComplete(ModeratedContent result);
    }
}
