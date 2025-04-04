package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.nekkochan.onyxchat.util.UserSessionManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Client for accessing API endpoints
 */
public class DirectApiClient {
    private static final String TAG = "DirectApiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    // Default server endpoint
    private static final String DEFAULT_API_ENDPOINT = "https://10.0.2.2:443";
    
    private final Context context;
    private final ExecutorService executor;
    private final String apiBaseUrl;
    private final UserSessionManager sessionManager;
    private final OkHttpClient httpClient;
    
    /**
     * Create a new API client with the default endpoint
     */
    public DirectApiClient(Context context) {
        this.context = context;
        this.executor = Executors.newCachedThreadPool();
        this.sessionManager = new UserSessionManager(context);
        
        // Get API URL from preferences or use default
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String apiUrl = sharedPreferences.getString("server_url", DEFAULT_API_ENDPOINT);
        if (apiUrl.endsWith("/ws") || apiUrl.endsWith("/ws/")) {
            // If the URL points to a WebSocket endpoint, strip the /ws
            apiUrl = apiUrl.replace("/ws/", "/").replace("/ws", "/");
        }
        this.apiBaseUrl = apiUrl;
        
        // Configure OkHttpClient with SSL trust for development
        this.httpClient = createTrustingOkHttpClient();
        
        // Configure TLS for development (allows self-signed certificates)
        trustAllCertificates();
    }
    
    /**
     * Create a new API client with a custom endpoint
     */
    public DirectApiClient(String apiBaseUrl, Context context) {
        this.context = context;
        this.executor = Executors.newCachedThreadPool();
        this.apiBaseUrl = apiBaseUrl;
        this.sessionManager = new UserSessionManager(context);
        
        // Configure OkHttpClient with SSL trust for development
        this.httpClient = createTrustingOkHttpClient();
        
        // Configure TLS for development (allows self-signed certificates)
        trustAllCertificates();
    }
    
    /**
     * Create an OkHttpClient that trusts all SSL certificates
     */
    private OkHttpClient createTrustingOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS);
            
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
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // Allow all hostnames
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up SSL trust for OkHttpClient", e);
        }
        
        return builder.build();
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
     * Perform a GET request asynchronously
     * @param endpoint API endpoint (will be appended to base URL)
     * @param headers Optional HTTP headers
     * @param callback Callback for the response
     */
    public void get(String endpoint, Map<String, String> headers, ApiCallback callback) {
        executor.execute(() -> {
            try {
                String url = apiBaseUrl + endpoint;
                Log.d(TAG, "GET " + url);
                
                Request.Builder requestBuilder = new Request.Builder()
                        .url(url)
                        .get();
                
                // Add headers if provided
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        requestBuilder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
                
                Request request = requestBuilder.build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Error response: " + response);
                        if (callback != null) {
                            callback.onFailure(new IOException("Unexpected response: " + response));
                        }
                        return;
                    }
                    
                    String responseBody = response.body() != null ? response.body().string() : "{}";
                    Log.d(TAG, "Response: " + responseBody);
                    
                    if (callback != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            callback.onSuccess(jsonResponse);
                        } catch (JSONException e) {
                            Log.e(TAG, "Failed to parse JSON response", e);
                            callback.onFailure(e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error executing GET request", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            }
        });
    }
    
    /**
     * Perform a GET request asynchronously with no headers
     * @param endpoint API endpoint (will be appended to base URL)
     * @param callback Callback for the response
     */
    public void get(String endpoint, ApiCallback callback) {
        get(endpoint, null, callback);
    }
    
    /**
     * Perform a POST request asynchronously
     * @param endpoint API endpoint (will be appended to base URL)
     * @param data Data to send as JSON
     * @param headers Optional HTTP headers
     * @param callback Callback for the response
     */
    public void post(String endpoint, JSONObject data, Map<String, String> headers, ApiCallback callback) {
        executor.execute(() -> {
            try {
                String url = apiBaseUrl + endpoint;
                Log.d(TAG, "POST " + url);
                
                RequestBody body = RequestBody.create(JSON, data.toString());
                
                Request.Builder requestBuilder = new Request.Builder()
                        .url(url)
                        .post(body);
                
                // Add headers if provided
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        requestBuilder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
                
                Request request = requestBuilder.build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Error response: " + response);
                        if (callback != null) {
                            callback.onFailure(new IOException("Unexpected response: " + response));
                        }
                        return;
                    }
                    
                    String responseBody = response.body() != null ? response.body().string() : "{}";
                    Log.d(TAG, "Response: " + responseBody);
                    
                    if (callback != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            callback.onSuccess(jsonResponse);
                        } catch (JSONException e) {
                            Log.e(TAG, "Failed to parse JSON response", e);
                            callback.onFailure(e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error executing POST request", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            }
        });
    }
    
    /**
     * Perform a POST request asynchronously with no headers
     * @param endpoint API endpoint (will be appended to base URL)
     * @param data Data to send as JSON
     * @param callback Callback for the response
     */
    public void post(String endpoint, JSONObject data, ApiCallback callback) {
        post(endpoint, data, null, callback);
    }
    
    /**
     * Add standard headers to API requests
     * @param apiKey The API key to use
     * @return A map of headers
     */
    @NonNull
    public static Map<String, String> getStandardHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        
        return headers;
    }
    
    /**
     * Shutdown the client and clean up resources
     */
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * Callback interface for API requests
     */
    public interface ApiCallback {
        void onSuccess(JSONObject response);
        void onFailure(Exception e);
    }
} 