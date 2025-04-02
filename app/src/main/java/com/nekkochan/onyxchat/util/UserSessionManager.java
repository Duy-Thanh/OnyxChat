package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

/**
 * User session manager to handle login state and user data persistence
 */
public class UserSessionManager {
    
    private static final String TAG = "UserSessionManager";
    
    // Shared preferences file name
    private static final String PREF_NAME = "OnyxChatUserSession";
    
    // Shared preferences mode
    private static final int PRIVATE_MODE = Context.MODE_PRIVATE;
    
    // Shared preferences keys
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_LOGIN_TIME = "loginTime";
    
    // Shared preferences editor
    private final SharedPreferences pref;
    private final Editor editor;
    private final Context context;
    
    /**
     * Constructor
     * @param context Application context
     */
    public UserSessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }
    
    /**
     * Create login session
     * @param username User's username or email
     * @param userId User's unique ID
     */
    public void createLoginSession(String username, String userId) {
        Log.d(TAG, "Creating login session for " + username + " with ID " + userId);
        
        // Store login values
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_USER_ID, userId);
        editor.putLong(KEY_LOGIN_TIME, System.currentTimeMillis());
        
        // Commit changes
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
        long loginTime = pref.getLong(KEY_LOGIN_TIME, 0);
        
        return new UserDetails(username, userId, loginTime);
    }
    
    /**
     * Clear session details and log user out
     */
    public void logout() {
        Log.d(TAG, "Logging out user");
        
        // Clear all data from shared preferences
        editor.clear();
        editor.apply();
    }
    
    /**
     * Check if user is logged in
     * @return true if user is logged in, false otherwise
     */
    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }
    
    /**
     * Get current user ID
     * @return User ID or null if not logged in
     */
    public String getUserId() {
        if (!isLoggedIn()) {
            return null;
        }
        return pref.getString(KEY_USER_ID, null);
    }
    
    /**
     * User details data class
     */
    public static class UserDetails {
        private final String username;
        private final String userId;
        private final long loginTime;
        
        public UserDetails(String username, String userId, long loginTime) {
            this.username = username;
            this.userId = userId;
            this.loginTime = loginTime;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public long getLoginTime() {
            return loginTime;
        }
    }
} 