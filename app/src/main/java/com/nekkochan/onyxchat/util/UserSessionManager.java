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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

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
        
        // Initialize SSL trust for development
        setupUnsafeSSL();
    }
    
    /**
     * Set up unsafe SSL configuration for development (DO NOT USE IN PRODUCTION)
     */
    private void setupUnsafeSSL() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    }
                    
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    }
                }
            };
            
            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            
            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            
            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            Log.d(TAG, "Set up unsafe SSL for development");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up unsafe SSL", e);
        }
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
     * Check if user has a refresh token
     */
    public boolean hasRefreshToken() {
        String refreshToken = getRefreshToken();
        return refreshToken != null && !refreshToken.isEmpty();
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
     * Refresh auth token using refresh token
     * @return CompletableFuture with success/failure result
     */
    public CompletableFuture<Boolean> refreshToken() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Check if refresh token is available
        String refreshToken = getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            Log.e(TAG, "Cannot refresh token: No refresh token available");
            future.complete(false);
            return future;
        }
        
        // Get server URL from shared preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String serverUrl = prefs.getString("server_url", "https://10.0.2.2:443");
        
        // Fix /ws/ in URL if present
        if (serverUrl.endsWith("/ws") || serverUrl.endsWith("/ws/")) {
            serverUrl = serverUrl.replace("/ws/", "/").replace("/ws", "/");
        }
        
        // Make sure URL ends with a slash
        if (!serverUrl.endsWith("/")) {
            serverUrl += "/";
        }
        
        final String apiUrl = serverUrl + "api/auth/refresh";
        Log.d(TAG, "Refreshing token from: " + apiUrl);
        
        // Use OkHttpClient for better handling of SSL
        executor.execute(() -> {
            try {
                // Create OkHttpClient with proper SSL handling
                OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    // For development only - allow self-signed certs
                    .hostnameVerifier((hostname, session) -> true)
                    .sslSocketFactory(getUnsafeSSLSocketFactory(), getUnsafeTrustManager())
                    .build();
                
                // Create request body
                JSONObject requestBody = new JSONObject();
                requestBody.put("refreshToken", refreshToken);
                
                // Build request
                RequestBody body = RequestBody.create(
                    okhttp3.MediaType.parse("application/json"), 
                    requestBody.toString()
                );
                
                Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();
                
                // Execute request
                Response response = client.newCall(request).execute();
                
                // Process response
                if (response.isSuccessful() && response.body() != null) {
                    String responseString = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseString);
                    JSONObject data = jsonResponse.getJSONObject("data");
                    JSONObject tokens = data.getJSONObject("tokens");
                    
                    // Extract new tokens
                    String newAuthToken = tokens.getString("accessToken");
                    String newRefreshToken = tokens.getString("refreshToken");
                    
                    // Update tokens in preferences
                    editor.putString(KEY_AUTH_TOKEN, newAuthToken);
                    editor.putString(KEY_REFRESH_TOKEN, newRefreshToken);
                    editor.apply();
                    
                    Log.d(TAG, "Token refreshed successfully with OkHttpClient");
                    future.complete(true);
                } else {
                    int code = response.code();
                    Log.e(TAG, "Failed to refresh token: HTTP " + code);
                    
                    // If unauthorized, clear tokens
                    if (code == 401) {
                        editor.remove(KEY_AUTH_TOKEN);
                        editor.remove(KEY_REFRESH_TOKEN);
                        editor.apply();
                    }
                    
                    future.complete(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing token with OkHttpClient", e);
                future.complete(false);
            }
        });
        
        return future;
    }
    
    /**
     * Get an SSL socket factory that trusts all certificates
     * Only for development!
     */
    private javax.net.ssl.SSLSocketFactory getUnsafeSSLSocketFactory() throws Exception {
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, new javax.net.ssl.TrustManager[]{getUnsafeTrustManager()}, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }
    
    /**
     * Get a trust manager that trusts all certificates
     * Only for development!
     */
    private javax.net.ssl.X509TrustManager getUnsafeTrustManager() {
        return new javax.net.ssl.X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }
            
            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }
            
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[]{};
            }
        };
    }
    
    /**
     * Get user details
     * @return User details as key-value pairs
     */
    public UserDetails getUserDetails() {
        if (!isLoggedIn()) {
            return null;
        }
        
        UserDetails details = new UserDetails();
        details.username = getUsername();
        details.userId = getUserId();
        details.authToken = getAuthToken();
        
        return details;
    }
    
    /**
     * Attempt to fetch a refresh token for a user that doesn't have one.
     * This should be called when we detect a user is logged in but missing a refresh token.
     * 
     * @param username The username to request new tokens for
     * @param password The user's password
     * @return A future that resolves to true if successful, false otherwise
     */
    public CompletableFuture<Boolean> refreshTokensForExistingUser(String username, String password) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // If the user already has a refresh token, just return true
        if (hasRefreshToken()) {
            Log.d(TAG, "User already has a refresh token, no need to fetch a new one");
            future.complete(true);
            return future;
        }
        
        Log.d(TAG, "Attempting to fetch refresh token for existing user: " + username);
        
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
        
        final String apiUrl = serverUrl + "api/auth/login";
        Log.d(TAG, "Requesting new tokens from: " + apiUrl);
        
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
                
                // Create request body with username and password
                JSONObject requestBody = new JSONObject();
                requestBody.put("username", username);
                requestBody.put("password", password);
                
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
                    JSONObject user = data.getJSONObject("user");
                    
                    // Extract tokens
                    String newAuthToken = tokens.getString("accessToken");
                    String newRefreshToken = tokens.getString("refreshToken");
                    String userId = user.getString("id");
                    
                    // Save tokens and user info
                    createLoginSession(username, userId, newAuthToken, newRefreshToken);
                    
                    Log.d(TAG, "Successfully obtained new tokens for existing user");
                    future.complete(true);
                } else {
                    // Handle error response
                    Log.e(TAG, "Failed to obtain tokens: HTTP " + responseCode);
                    future.complete(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error obtaining tokens", e);
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
     * Helper class for user details
     */
    public static class UserDetails {
        private String username;
        private String userId;
        private String authToken;
        
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