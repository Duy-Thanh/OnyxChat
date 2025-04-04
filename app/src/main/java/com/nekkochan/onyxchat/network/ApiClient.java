package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.nekkochan.onyxchat.model.User;
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
import retrofit2.http.POST;

/**
 * API client for interacting with the OnyxChat server
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    
    // Default server URL - for testing using Android emulator
    private static final String DEFAULT_API_URL = "http://10.0.2.2:8082/";
    
    // Singleton instance
    private static ApiClient instance;
    
    // Retrofit and API service
    private final Retrofit retrofit;
    private final ApiService apiService;
    private final UserSessionManager sessionManager;
    private final SharedPreferences sharedPreferences;
    
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
        
        // Get server URL from preferences
        String serverUrl = sharedPreferences.getString("server_url", DEFAULT_API_URL);
        
        // Create OkHttpClient with interceptors
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        
        // Add logging interceptor for debug builds
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            httpClient.addInterceptor(loggingInterceptor);
        }
        
        // Add auth interceptor to include token in requests
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();
            
            // Skip auth for login and register endpoints
            String url = original.url().toString();
            if (url.contains("/auth/login") || 
                (url.contains("/users") && original.method().equals("POST"))) {
                return chain.proceed(original);
            }
            
            // Add authorization header
            String token = sessionManager.getAuthToken();
            if (token != null && !token.isEmpty()) {
                Request.Builder requestBuilder = original.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .method(original.method(), original.body());
                
                return chain.proceed(requestBuilder.build());
            }
            
            return chain.proceed(original);
        });
        
        // Configure timeouts
        httpClient.connectTimeout(15, TimeUnit.SECONDS);
        httpClient.readTimeout(15, TimeUnit.SECONDS);
        httpClient.writeTimeout(15, TimeUnit.SECONDS);
        
        // Create Gson converter
        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .create();
        
        // Create Retrofit instance
        retrofit = new Retrofit.Builder()
                .baseUrl(serverUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(httpClient.build())
                .build();
        
        // Create API service
        apiService = retrofit.create(ApiService.class);
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
                if (response.isSuccessful() && response.body() != null) {
                    // Save auth token and user details
                    AuthResponse authResponse = response.body();
                    sessionManager.createLoginSession(
                            authResponse.user.username,
                            authResponse.user.id,
                            authResponse.token);
                    
                    // Notify callback
                    callback.onSuccess(authResponse);
                } else {
                    // Parse error message
                    String errorMsg = "Registration failed";
                    if (response.errorBody() != null) {
                        try {
                            errorMsg = response.errorBody().string();
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
     * Login with username and password
     */
    public void login(String username, String password, final ApiCallback<AuthResponse> callback) {
        // Create login request
        LoginRequest request = new LoginRequest(username, password);
        
        // Make API call
        apiService.login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, retrofit2.Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Save auth token and user details
                    AuthResponse authResponse = response.body();
                    sessionManager.createLoginSession(
                            authResponse.user.username,
                            authResponse.user.id,
                            authResponse.token);
                    
                    // Notify callback
                    callback.onSuccess(authResponse);
                } else {
                    // Parse error message
                    String errorMsg = "Login failed";
                    if (response.errorBody() != null) {
                        try {
                            errorMsg = response.errorBody().string();
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
        @POST("api/users")
        Call<AuthResponse> register(@Body RegisterRequest request);
        
        @POST("api/auth/login")
        Call<AuthResponse> login(@Body LoginRequest request);
    }
    
    /**
     * Login request model
     */
    private static class LoginRequest {
        @SerializedName("username")
        private final String username;
        
        @SerializedName("password")
        private final String password;
        
        public LoginRequest(String username, String password) {
            this.username = username;
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
        @SerializedName("token")
        public String token;
        
        @SerializedName("user")
        public UserProfile user;
        
        @SerializedName("token_type")
        public String tokenType;
        
        @SerializedName("expires_in")
        public long expiresIn;
    }
    
    /**
     * User profile model
     */
    public static class UserProfile {
        @SerializedName("id")
        public String id;
        
        @SerializedName("username")
        public String username;
        
        @SerializedName("display_name")
        public String displayName;
        
        @SerializedName("is_active")
        public boolean isActive;
    }
} 