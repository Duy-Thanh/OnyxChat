package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.nekkochan.onyxchat.network.ApiClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Session manager to store and manage user session data.
 * Stores user info and login/logout functionality.
 */
public class UserSessionManager {
    private static final String TAG = "UserSessionManager";
    
    // Shared Preferences reference
    private final SharedPreferences pref;
    private final Editor editor;
    private final Context context;
    private final ExecutorService executor;
    
    // Shared preferences file name
    private static final String PREF_NAME = "OnyxChatPref";
    private static final int PRIVATE_MODE = 0;
    
    // Shared preferences keys
    private static final String IS_LOGIN = "isLoggedIn";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_AUTH_TOKEN = "authToken";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";
    
    /**
     * Constructor
     */
    public UserSessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
        executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Create login session
     */
    public void createLoginSession(String username, String userId, String authToken) {
        Log.d(TAG, "Creating login session for " + username + " with ID " + userId);
        
        // Store login values
        editor.putBoolean(IS_LOGIN, true);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_AUTH_TOKEN, authToken);
        
        // Commit changes
        editor.apply();
    }
    
    /**
     * Create login session with refresh token
     */
    public void createLoginSession(String username, String userId, String authToken, String refreshToken) {
        Log.d(TAG, "Creating login session for " + username + " with ID " + userId + " and refresh token");
        
        // Store login values
        editor.putBoolean(IS_LOGIN, true);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_AUTH_TOKEN, authToken);
        editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        
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
     * Get stored refresh token
     */
    public String getRefreshToken() {
        return pref.getString(KEY_REFRESH_TOKEN, null);
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
        Log.d(TAG, "Logging out user");
        
        // Clear all data from preferences
        editor.clear();
        editor.apply();
    }
    
    /**
     * Refresh the auth token using refresh token
     * Returns a CompletableFuture that resolves to true if successful, false otherwise
     */
    public CompletableFuture<Boolean> refreshToken() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Get refresh token
        String refreshToken = getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            Log.e(TAG, "Cannot refresh: no refresh token available");
            future.complete(false);
            return future;
        }
        
        // Get saved server URL from shared preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String serverUrl = prefs.getString("server_url", "https://10.0.2.2:443");
        if (serverUrl.endsWith("/ws") || serverUrl.endsWith("/ws/")) {
            // If the URL points to a WebSocket endpoint, strip the /ws
            serverUrl = serverUrl.replace("/ws/", "/").replace("/ws", "/");
        }
        
        // Make sure URL ends with a slash
        if (!serverUrl.endsWith("/")) {
            serverUrl += "/";
        }
        
        final String apiUrl = serverUrl + "api/auth/refresh-token";
        Log.d(TAG, "Refreshing token using URL: " + apiUrl);
        
        // Execute network request in background
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Create connection
                URL url = new URL(apiUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                
                // Create request body with refresh token
                JSONObject requestBody = new JSONObject();
                requestBody.put("refreshToken", refreshToken);
                
                // Write request body
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                
                // Get response
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    // Read response
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }
                    
                    // Parse response
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONObject data = jsonResponse.getJSONObject("data");
                    JSONObject tokens = data.getJSONObject("tokens");
                    
                    // Extract new tokens
                    String newAuthToken = tokens.getString("accessToken");
                    String newRefreshToken = tokens.getString("refreshToken");
                    
                    // Update tokens in preferences
                    editor.putString(KEY_AUTH_TOKEN, newAuthToken);
                    editor.putString(KEY_REFRESH_TOKEN, newRefreshToken);
                    editor.apply();
                    
                    Log.d(TAG, "Token refreshed successfully");
                    future.complete(true);
                } else {
                    // Handle error response
                    Log.e(TAG, "Failed to refresh token: HTTP " + responseCode);
                    future.complete(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing token", e);
                future.complete(false);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        
        return future;
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