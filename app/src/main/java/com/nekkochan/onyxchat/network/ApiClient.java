package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.nekkochan.onyxchat.model.User;
import com.nekkochan.onyxchat.model.UserProfile;
import com.nekkochan.onyxchat.util.UserSessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * API client for interacting with the OnyxChat server
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    
    // Default server URL - for testing using Android emulator
    private static final String DEFAULT_API_URL = "https://10.0.2.2:443/";
    
    // Singleton instance
    private static ApiClient instance;
    
    // Retrofit and API service
    private final Retrofit retrofit;
    private final ApiService apiService;
    private final UserSessionManager sessionManager;
    private final SharedPreferences sharedPreferences;
    private final Executor executor;
    private String apiUrl;
    
    /**
     * Get the singleton instance
     */
    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Private constructor
     */
    private ApiClient(Context context) {
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.sessionManager = new UserSessionManager(context);
        this.executor = Executors.newCachedThreadPool();
        
        // Get API URL from preferences or use default
        apiUrl = sharedPreferences.getString("server_url", DEFAULT_API_URL);
        
        // Convert WebSocket URLs to HTTP/HTTPS for REST API calls
        if (apiUrl.startsWith("ws://")) {
            apiUrl = apiUrl.replace("ws://", "http://");
            Log.d(TAG, "Converting WebSocket URL to HTTP: " + apiUrl);
        } else if (apiUrl.startsWith("wss://")) {
            apiUrl = apiUrl.replace("wss://", "https://");
            Log.d(TAG, "Converting WebSocket URL to HTTPS: " + apiUrl);
        }
        
        // Remove WebSocket path if present
        if (apiUrl.endsWith("/ws") || apiUrl.endsWith("/ws/")) {
            // If the URL points to a WebSocket endpoint, strip the /ws
            apiUrl = apiUrl.replace("/ws/", "/").replace("/ws", "/");
        }
        
        // Ensure URL ends with a slash
        if (!apiUrl.endsWith("/")) {
            apiUrl = apiUrl + "/";
        }
        
        Log.d(TAG, "Using API URL: " + apiUrl);
        
        // Configure TLS for development (allows self-signed certificates)
        trustAllCertificates();
        
        // Create OkHttpClient with interceptors
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        
        // Add logging interceptor for debug builds
        boolean isDebug = true; // Set to true for debugging, false for release
        if (isDebug) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            httpClient.addInterceptor(loggingInterceptor);
        }
        
        // Add auth interceptor to include token in requests
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            
            // Skip auth for login and register endpoints
            String url = original.url().toString();
            if (url.contains("/auth/login") || url.contains("/auth/refresh") ||
                (url.contains("/users") && original.method().equals("POST"))) {
                return chain.proceed(original);
            }
            
            // Add authorization header
            String token = sessionManager.getAuthToken();
            if (token != null && !token.isEmpty()) {
                Request.Builder requestBuilder = original.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .method(original.method(), original.body());
                
                Response response = chain.proceed(requestBuilder.build());
                
                // Check if the response code is 401 (Unauthorized) which indicates token expiration
                if (response.code() == 401) {
                    String responseBody = "";
                    
                    try {
                        if (response.body() != null) {
                            responseBody = response.body().string();
                            
                            // Parse responseBody to check message
                            if (responseBody.contains("\"message\"")) {
                                try {
                                    JSONObject json = new JSONObject(responseBody);
                                    String message = json.optString("message", "");
                                    if (message.contains("Token expired") || 
                                        message.contains("jwt expired") ||
                                        message.contains("invalid token")) {
                                        Log.d(TAG, "Token error detected: " + message);
                                    }
                                } catch (JSONException e) {
                                    Log.e(TAG, "Error parsing JSON response", e);
                                }
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error response body", e);
                    }
                    
                    // Close the consumed response
                    response.close();
                    
                    // Check if we have a refresh token and should attempt refresh
                    if (sessionManager.hasRefreshToken()) {
                        Log.d(TAG, "401 Unauthorized response, attempting to refresh token");
                        
                        // Try to refresh the token
                        boolean tokenRefreshed = false;
                        try {
                            tokenRefreshed = sessionManager.refreshToken().get(); // Blocking call to wait for refresh
                        } catch (Exception e) {
                            Log.e(TAG, "Error refreshing token", e);
                        }
                        
                        if (tokenRefreshed) {
                            // Token refreshed successfully, retry the request with the new token
                            Log.d(TAG, "Token refreshed successfully, retrying the request");
                            
                            // Get the new token
                            String newToken = sessionManager.getAuthToken();
                            
                            // Create new request with the new token
                            Request newRequest = original.newBuilder()
                                    .header("Authorization", "Bearer " + newToken)
                                    .method(original.method(), original.body())
                                    .build();
                            
                            // Retry the request
                            return chain.proceed(newRequest);
                        } else {
                            Log.e(TAG, "Failed to refresh token");
                        }
                    } else {
                        Log.w(TAG, "No refresh token available to handle 401 response");
                    }
                    
                    // If we couldn't refresh the token or it wasn't a token expiration issue,
                    // create a new response with the original error
                    return new Response.Builder()
                            .request(original)
                            .protocol(response.protocol())
                            .code(401)
                            .message("Unauthorized")
                            .body(ResponseBody.create(null, responseBody))
                            .build();
                }
                
                return response;
            }
            
            return chain.proceed(original);
        });
        
        // Configure timeouts
        httpClient.connectTimeout(15, TimeUnit.SECONDS);
        httpClient.readTimeout(15, TimeUnit.SECONDS);
        httpClient.writeTimeout(15, TimeUnit.SECONDS);
        
        // Apply SSL trust settings for development
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            httpClient.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            httpClient.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // Allow all hostnames
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up SSL trust for OkHttpClient", e);
        }
        
        // Create Gson converter
        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .create();
        
        // Create Retrofit instance
        retrofit = new Retrofit.Builder()
                .baseUrl(apiUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(httpClient.build())
                .build();
        
        // Create API service
        apiService = retrofit.create(ApiService.class);
    }
    
    /**
     * Set up trust for all SSL certificates in development mode
     * DO NOT USE IN PRODUCTION
     */
    private void trustAllCertificates() {
        try {
            // Create a trust manager that does not validate certificate chains
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
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // Allow all hostnames
                }
            });
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to trust all certificates", e);
        }
    }
    
    /**
     * Register a new user
     */
    public void registerUser(String username, String email, String password, String displayName,
                           final ApiCallback<AuthResponse> callback) {
        // Create register request
        RegisterRequest request = new RegisterRequest(username, email, password, displayName);
        
        // Make API call
        apiService.register(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, retrofit2.Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    // Save auth token and user details
                    AuthResponse authResponse = response.body();
                    sessionManager.createLoginSession(
                            authResponse.data.user.username,
                            authResponse.data.user.id,
                            authResponse.data.tokens.accessToken,
                            authResponse.data.tokens.refreshToken);
                    
                    // Notify callback
                    callback.onSuccess(authResponse);
                } else {
                    // Parse error message
                    String errorMsg = "Registration failed";
                    
                    // Check if we have a body with an error message
                    if (response.body() != null && response.body().message != null) {
                        errorMsg = response.body().message;
                    }
                    // Otherwise try to parse the error body
                    else if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            // Try to extract the message from error body if it's in JSON format
                            if (errorBody.contains("\"message\"")) {
                                Gson gson = new Gson();
                                try {
                                    ErrorResponse error = gson.fromJson(errorBody, ErrorResponse.class);
                                    if (error != null && error.message != null) {
                                        errorMsg = error.message;
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing JSON error response", e);
                                }
                            } else {
                                errorMsg = errorBody;
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error parsing error response", e);
                        }
                    }
                    
                    callback.onFailure(errorMsg);
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Login with username/email and password
     */
    public void login(String usernameOrEmail, String password, final ApiCallback<AuthResponse> callback) {
        // Create login request
        LoginRequest request = new LoginRequest(usernameOrEmail, password);
        
        // Make API call
        apiService.login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, retrofit2.Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    // Save auth token and user details
                    AuthResponse authResponse = response.body();
                    sessionManager.createLoginSession(
                            authResponse.data.user.username,
                            authResponse.data.user.id,
                            authResponse.data.tokens.accessToken,
                            authResponse.data.tokens.refreshToken);
                    
                    // Notify callback
                    callback.onSuccess(authResponse);
                } else {
                    // Parse error message
                    String errorMsg = "Login failed";
                    
                    // Check if we have a body with an error message
                    if (response.body() != null && response.body().message != null) {
                        errorMsg = response.body().message;
                    }
                    // Otherwise try to parse the error body
                    else if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            // Try to extract the message from error body if it's in JSON format
                            if (errorBody.contains("\"message\"")) {
                                Gson gson = new Gson();
                                try {
                                    ErrorResponse error = gson.fromJson(errorBody, ErrorResponse.class);
                                    if (error != null && error.message != null) {
                                        errorMsg = error.message;
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing JSON error response", e);
                                }
                            } else {
                                errorMsg = errorBody;
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error parsing error response", e);
                        }
                    }
                    
                    callback.onFailure(errorMsg);
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Update the server URL
     */
    public void updateServerUrl(String serverUrl) {
        sharedPreferences.edit().putString("server_url", serverUrl).apply();
        // Force recreate the client
        instance = null;
    }
    
    /**
     * Sync contacts with server to find which ones are app users
     * @param contactAddresses List of contact addresses to check
     * @param callback Callback for the response
     */
    public void syncContacts(List<String> contactAddresses, final ApiCallback<ContactSyncResponse> callback) {
        // Create sync request
        ContactSyncRequest request = new ContactSyncRequest(contactAddresses);
        
        // Make API call
        apiService.syncContacts(request).enqueue(new Callback<ContactSyncResponse>() {
            @Override
            public void onResponse(Call<ContactSyncResponse> call, retrofit2.Response<ContactSyncResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    // Notify callback of success
                    callback.onSuccess(response.body());
                } else {
                    // Parse error message
                    String errorMsg = "Contact sync failed";
                    
                    // Check if we have a body with an error message
                    if (response.body() != null && response.body().message != null) {
                        errorMsg = response.body().message;
                    }
                    // Otherwise try to parse the error body
                    else if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            // Try to extract the message from error body if it's in JSON format
                            if (errorBody.contains("\"message\"")) {
                                Gson gson = new Gson();
                                try {
                                    ErrorResponse error = gson.fromJson(errorBody, ErrorResponse.class);
                                    if (error != null && error.message != null) {
                                        errorMsg = error.message;
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing JSON error response", e);
                                }
                            } else {
                                errorMsg = errorBody;
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error parsing error response", e);
                        }
                    }
                    
                    callback.onFailure(errorMsg);
                }
            }
            
            @Override
            public void onFailure(Call<ContactSyncResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Get all users for friend discovery
     */
    public void getUsers(final ApiCallback<UsersResponse> callback) {
        apiService.getUsers().enqueue(new Callback<UsersResponse>() {
            @Override
            public void onResponse(Call<UsersResponse> call, retrofit2.Response<UsersResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    callback.onSuccess(response.body());
                } else {
                    handleErrorResponse(response, callback);
                }
            }
            
            @Override
            public void onFailure(Call<UsersResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Get friend requests for the current user
     */
    public void getFriendRequests(final ApiCallback<FriendRequestsResponse> callback) {
        apiService.getFriendRequests().enqueue(new Callback<FriendRequestsResponse>() {
            @Override
            public void onResponse(Call<FriendRequestsResponse> call, retrofit2.Response<FriendRequestsResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    callback.onSuccess(response.body());
                } else {
                    handleErrorResponse(response, callback);
                }
            }
            
            @Override
            public void onFailure(Call<FriendRequestsResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Send a friend request
     */
    public void sendFriendRequest(String receiverId, String message, final ApiCallback<FriendRequestResponse> callback) {
        FriendRequestRequest request = new FriendRequestRequest(receiverId, message);
        apiService.sendFriendRequest(request).enqueue(new Callback<FriendRequestResponse>() {
            @Override
            public void onResponse(Call<FriendRequestResponse> call, retrofit2.Response<FriendRequestResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    callback.onSuccess(response.body());
                } else {
                    handleErrorResponse(response, callback);
                }
            }
            
            @Override
            public void onFailure(Call<FriendRequestResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Accept a friend request
     */
    public void acceptFriendRequest(String requestId, final ApiCallback<AcceptFriendResponse> callback) {
        apiService.acceptFriendRequest(requestId).enqueue(new Callback<AcceptFriendResponse>() {
            @Override
            public void onResponse(Call<AcceptFriendResponse> call, retrofit2.Response<AcceptFriendResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    callback.onSuccess(response.body());
                } else {
                    handleErrorResponse(response, callback);
                }
            }
            
            @Override
            public void onFailure(Call<AcceptFriendResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Reject a friend request
     */
    public void rejectFriendRequest(String requestId, final ApiCallback<BaseResponse> callback) {
        apiService.rejectFriendRequest(requestId).enqueue(new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, retrofit2.Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    callback.onSuccess(response.body());
                } else {
                    handleErrorResponse(response, callback);
                }
            }
            
            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Cancel a sent friend request
     */
    public void cancelFriendRequest(String requestId, final ApiCallback<BaseResponse> callback) {
        apiService.cancelFriendRequest(requestId).enqueue(new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, retrofit2.Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    callback.onSuccess(response.body());
                } else {
                    handleErrorResponse(response, callback);
                }
            }
            
            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }
    
    /**
     * Helper method to handle error responses
     */
    private <T> void handleErrorResponse(retrofit2.Response<T> response, ApiCallback<?> callback) {
        String errorMsg = "Request failed";
        
        // Check if we have a body with an error message
        if (response.body() instanceof BaseResponse) {
            BaseResponse baseResponse = (BaseResponse) response.body();
            if (baseResponse != null && baseResponse.message != null) {
                errorMsg = baseResponse.message;
            }
        }
        // Otherwise try to parse the error body
        else if (response.errorBody() != null) {
            try {
                String errorBody = response.errorBody().string();
                // Try to extract the message from error body if it's in JSON format
                if (errorBody.contains("\"message\"")) {
                    Gson gson = new Gson();
                    try {
                        ErrorResponse error = gson.fromJson(errorBody, ErrorResponse.class);
                        if (error != null && error.message != null) {
                            errorMsg = error.message;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing JSON error response", e);
                    }
                } else {
                    errorMsg = errorBody;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error parsing error response", e);
            }
        }
        
        callback.onFailure(errorMsg);
    }
    
    /**
     * API callback interface
     */
    public interface ApiCallback<T> {
        void onSuccess(T response);
        void onFailure(String errorMessage);
    }
    
    /**
     * API service interface
     */
    private interface ApiService {
        @POST("api/auth/register")
        Call<AuthResponse> register(@Body RegisterRequest request);
        
        @POST("api/auth/login")
        Call<AuthResponse> login(@Body LoginRequest request);
        
        @POST("api/auth/refresh")
        Call<AuthResponse> refreshToken(@Body RefreshTokenRequest request);
        
        @POST("api/contacts/sync")
        Call<ContactSyncResponse> syncContacts(@Body ContactSyncRequest request);
        
        // Friend request endpoints
        @GET("api/friend-requests")
        Call<FriendRequestsResponse> getFriendRequests();
        
        @POST("api/friend-requests")
        Call<FriendRequestResponse> sendFriendRequest(@Body FriendRequestRequest request);
        
        @PUT("api/friend-requests/{id}/accept")
        Call<AcceptFriendResponse> acceptFriendRequest(@Path("id") String requestId);
        
        @PUT("api/friend-requests/{id}/reject")
        Call<BaseResponse> rejectFriendRequest(@Path("id") String requestId);
        
        @DELETE("api/friend-requests/{id}")
        Call<BaseResponse> cancelFriendRequest(@Path("id") String requestId);
        
        @GET("api/friend-requests/users")
        Call<UsersResponse> getUsers();
    }
    
    /**
     * Login request model
     */
    private static class LoginRequest {
        @SerializedName("username")
        private final String usernameOrEmail;
        
        @SerializedName("password")
        private final String password;
        
        public LoginRequest(String usernameOrEmail, String password) {
            this.usernameOrEmail = usernameOrEmail;
            this.password = password;
        }
    }
    
    /**
     * Register request model
     */
    private static class RegisterRequest {
        @SerializedName("username")
        private final String username;
        
        @SerializedName("email")
        private final String email;
        
        @SerializedName("password")
        private final String password;
        
        @SerializedName("display_name")
        private final String displayName;
        
        public RegisterRequest(String username, String email, String password, String displayName) {
            this.username = username;
            this.email = email;
            this.password = password;
            this.displayName = displayName;
        }
    }
    
    /**
     * Auth response model
     */
    public static class AuthResponse {
        @SerializedName("status")
        public String status;
        
        @SerializedName("message")
        public String message;
        
        @SerializedName("data")
        public AuthData data;
        
        public static class AuthData {
            @SerializedName("user")
            public UserProfile user;
            
            @SerializedName("tokens")
            public TokenInfo tokens;
        }
        
        public static class TokenInfo {
            @SerializedName("accessToken")
            public String accessToken;
            
            @SerializedName("refreshToken")
            public String refreshToken;
        }
    }
    
    /**
     * User profile model
     */
    public static class UserProfile {
        @SerializedName("id")
        public String id;
        
        @SerializedName("username")
        public String username;
        
        @SerializedName("displayName")
        public String displayName;
        
        @SerializedName("isActive")
        public boolean isActive;
        
        @SerializedName("friendStatus")
        public String friendStatus;
    }
    
    /**
     * Error response model
     */
    private static class ErrorResponse {
        @SerializedName("status")
        public String status;
        
        @SerializedName("message")
        public String message;
    }
    
    /**
     * Contact sync request model
     */
    private static class ContactSyncRequest {
        @SerializedName("contacts")
        private final List<String> contactAddresses;
        
        public ContactSyncRequest(List<String> contactAddresses) {
            this.contactAddresses = contactAddresses;
        }
    }
    
    /**
     * Contact sync response model
     */
    public static class ContactSyncResponse {
        @SerializedName("status")
        public String status;
        
        @SerializedName("message")
        public String message;
        
        @SerializedName("data")
        public ContactSyncData data;
        
        public static class ContactSyncData {
            @SerializedName("appUsers")
            public List<String> appUsers;
        }
    }
    
    /**
     * Base response model
     */
    public static class BaseResponse {
        @SerializedName("status")
        public String status;
        
        @SerializedName("message")
        public String message;
    }
    
    /**
     * Friend request request model
     */
    private static class FriendRequestRequest {
        @SerializedName("receiverId")
        private final String receiverId;
        
        @SerializedName("message")
        private final String message;
        
        public FriendRequestRequest(String receiverId, String message) {
            this.receiverId = receiverId;
            this.message = message;
        }
    }
    
    /**
     * Friend request response model
     */
    public static class FriendRequestResponse extends BaseResponse {
        @SerializedName("data")
        public FriendRequestData data;
        
        public static class FriendRequestData {
            @SerializedName("request")
            public FriendRequestObj request;
        }
        
        public static class FriendRequestObj {
            @SerializedName("id")
            public String id;
            
            @SerializedName("receiver")
            public UserProfile receiver;
            
            @SerializedName("message")
            public String message;
            
            @SerializedName("status")
            public String status;
            
            @SerializedName("createdAt")
            public Date createdAt;
        }
    }
    
    /**
     * Friend requests response model
     */
    public static class FriendRequestsResponse extends BaseResponse {
        @SerializedName("data")
        public FriendRequestsData data;
        
        public static class FriendRequestsData {
            @SerializedName("received")
            public List<ReceivedRequest> received;
            
            @SerializedName("sent")
            public List<SentRequest> sent;
        }
        
        public static class ReceivedRequest {
            @SerializedName("id")
            public String id;
            
            @SerializedName("sender")
            public UserProfile sender;
            
            @SerializedName("message")
            public String message;
            
            @SerializedName("status")
            public String status;
            
            @SerializedName("createdAt")
            public Date createdAt;
        }
        
        public static class SentRequest {
            @SerializedName("id")
            public String id;
            
            @SerializedName("receiver")
            public UserProfile receiver;
            
            @SerializedName("message")
            public String message;
            
            @SerializedName("status")
            public String status;
            
            @SerializedName("createdAt")
            public Date createdAt;
        }
    }
    
    /**
     * Accept friend response model
     */
    public static class AcceptFriendResponse extends BaseResponse {
        @SerializedName("data")
        public AcceptFriendData data;
        
        public static class AcceptFriendData {
            @SerializedName("contact")
            public UserProfile contact;
        }
    }
    
    /**
     * Users response model
     */
    public static class UsersResponse extends BaseResponse {
        @SerializedName("data")
        public UsersData data;
        
        public static class UsersData {
            @SerializedName("users")
            public List<UserProfile> users;
        }
    }
    
    /**
     * Refresh token request model
     */
    private static class RefreshTokenRequest {
        @SerializedName("refreshToken")
        private final String refreshToken;
        
        public RefreshTokenRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }
    
    /**
     * Refresh token method
     */
    public void refreshToken(final ApiCallback<AuthResponse> callback) {
        String refreshToken = sessionManager.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            callback.onFailure("No refresh token available");
            return;
        }
        
        // Log attempt with partial token for debugging
        if (refreshToken.length() > 10) {
            Log.d(TAG, "ApiClient attempting to refresh token starting with: " + 
                    refreshToken.substring(0, 10) + "...");
        }
        
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        apiService.refreshToken(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, retrofit2.Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null && "success".equals(response.body().status)) {
                    // Save auth token and user details
                    AuthResponse authResponse = response.body();
                    
                    // Check if we have complete response data
                    if (authResponse.data != null && 
                        authResponse.data.tokens != null &&
                        authResponse.data.tokens.accessToken != null &&
                        authResponse.data.tokens.refreshToken != null) {
                        
                        // Create login session with the tokens
                        if (authResponse.data.user != null) {
                            // We have user info - use it for a complete session
                            sessionManager.createLoginSession(
                                    authResponse.data.user.username,
                                    authResponse.data.user.id,
                                    authResponse.data.tokens.accessToken,
                                    authResponse.data.tokens.refreshToken);
                        } else {
                            // No user data in response - update tokens only
                            // Keep existing user info and just update tokens
                            String currentUsername = sessionManager.getUsername();
                            String currentUserId = sessionManager.getUserId();
                            
                            if (currentUsername != null && currentUserId != null) {
                                sessionManager.createLoginSession(
                                        currentUsername,
                                        currentUserId,
                                        authResponse.data.tokens.accessToken,
                                        authResponse.data.tokens.refreshToken);
                            } else {
                                // We don't have user data - this is unexpected but let's handle it
                                Log.w(TAG, "Token refresh successful but no user data found");
                                sessionManager.createLoginSession(
                                        "user", // Placeholder
                                        "unknown", // Placeholder
                                        authResponse.data.tokens.accessToken,
                                        authResponse.data.tokens.refreshToken);
                            }
                        }
                        
                        Log.d(TAG, "Token refresh successful via ApiClient");
                        
                        // Notify callback
                        callback.onSuccess(authResponse);
                    } else {
                        // Incomplete response data
                        Log.e(TAG, "Token refresh response missing required data");
                        callback.onFailure("Invalid token response from server");
                    }
                } else {
                    // Failed response
                    Log.e(TAG, "Token refresh failed with response code: " + response.code());
                    
                    // Detailed error body logging for debugging
                    if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            Log.e(TAG, "Token refresh error body: " + errorBody);
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to read token refresh error body", e);
                        }
                    }
                    
                    // Handle the common error cases
                    if (response.code() == 401) {
                        callback.onFailure("Refresh token invalid or expired");
                    } else {
                        handleErrorResponse(response, callback);
                    }
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e(TAG, "Network error during token refresh", t);
                callback.onFailure("Network error during token refresh: " + t.getMessage());
            }
        });
    }
} 