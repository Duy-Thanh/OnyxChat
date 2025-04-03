package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

/**
 * Session manager to store and manage user session data.
 * Stores user info and login/logout functionality.
 */
public class UserSessionManager {
    // Shared Preferences reference
    private final SharedPreferences pref;
    private final Editor editor;
    private final Context context;
    
    // Shared preferences file name
    private static final String PREF_NAME = "OnyxChatPref";
    private static final int PRIVATE_MODE = 0;
    
    // Shared preferences keys
    private static final String IS_LOGIN = "isLoggedIn";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_AUTH_TOKEN = "authToken";
    
    /**
     * Constructor
     */
    public UserSessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }
    
    /**
     * Create login session
     */
    public void createLoginSession(String username, String userId, String authToken) {
        Log.d("UserSessionManager", "Creating login session for " + username + " with ID " + userId);
        
        // Store login values
        editor.putBoolean(IS_LOGIN, true);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_AUTH_TOKEN, authToken);
        
        // Commit changes
        editor.apply();
    }
    
    /**
     * Create legacy login session (for backward compatibility)
     */
    public void createLoginSession(String username, String userId) {
        createLoginSession(username, userId, "");
    }
    
    /**
     * Get stored username
     */
    public String getUsername() {
        return pref.getString(KEY_USERNAME, null);
    }
    
    /**
     * Get stored user ID
     */
    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }
    
    /**
     * Get stored auth token
     */
    public String getAuthToken() {
        return pref.getString(KEY_AUTH_TOKEN, null);
    }
    
    /**
     * Check login status
     */
    public boolean isLoggedIn() {
        return pref.getBoolean(IS_LOGIN, false);
    }
    
    /**
     * Clear session details
     */
    public void logout() {
        Log.d("UserSessionManager", "Logging out user");
        
        // Clear all data from preferences
        editor.clear();
        editor.apply();
    }
    
    /**
     * Get user details
     * @return User details as key-value pairs
     */
    public UserDetails getUserDetails() {
        if (!isLoggedIn()) {
            return null;
        }
        
        String username = pref.getString(KEY_USERNAME, null);
        String userId = pref.getString(KEY_USER_ID, null);
        String authToken = pref.getString(KEY_AUTH_TOKEN, null);
        
        return new UserDetails(username, userId, authToken);
    }
    
    /**
     * User details data class
     */
    public static class UserDetails {
        private final String username;
        private final String userId;
        private final String authToken;
        
        public UserDetails(String username, String userId, String authToken) {
            this.username = username;
            this.userId = userId;
            this.authToken = authToken;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getAuthToken() {
            return authToken;
        }
    }
} 