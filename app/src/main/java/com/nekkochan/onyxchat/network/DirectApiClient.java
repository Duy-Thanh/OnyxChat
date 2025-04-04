package com.nekkochan.onyxchat.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final String DEFAULT_API_ENDPOINT = "http://10.0.2.2:8082";
    
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final String apiBaseUrl;
    
    /**
     * Create a new API client with default endpoint
     * @param context Application context
     */
    public DirectApiClient(Context context) {
        this(context, DEFAULT_API_ENDPOINT);
    }
    
    /**
     * Create a new API client with custom endpoint
     * @param context Application context
     * @param apiBaseUrl Base URL for API access
     */
    public DirectApiClient(Context context, String apiBaseUrl) {
        this.httpClient = new OkHttpClient();
        this.executor = Executors.newCachedThreadPool();
        this.apiBaseUrl = apiBaseUrl;
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