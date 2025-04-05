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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
        if (apiUrl.endsWith("/ws") || apiUrl.endsWith("/ws/")) {
            // If the URL points to a WebSocket endpoint, strip the /ws
            apiUrl = apiUrl.replace("/ws/", "/").replace("/ws", "/");
        }
        
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
     * Login with username and password
     */
    public void login(String username, String password, final ApiCallback<AuthResponse> callback) {
        // Create login request
        LoginRequest request = new LoginRequest(username, password);
        
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
} 