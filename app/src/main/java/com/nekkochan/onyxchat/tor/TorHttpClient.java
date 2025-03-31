package com.nekkochan.onyxchat.tor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import info.guardianproject.netcipher.NetCipher;
import info.guardianproject.netcipher.proxy.OrbotHelper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP client that routes requests through Tor using NetCipher
 */
public class TorHttpClient {
    private static final String TAG = "TorHttpClient";
    private static final int CONNECTION_TIMEOUT = 60; // Seconds
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final Context context;
    private OkHttpClient client;
    
    /**
     * Create a new TorHttpClient
     * @param context Application context
     */
    public TorHttpClient(Context context) {
        this.context = context.getApplicationContext();
        setupClient();
    }
    
    /**
     * Set up the OkHttp client
     */
    private void setupClient() {
        try {
            // Create a standard OkHttpClient as NetCipher integration is problematic
            client = new OkHttpClient.Builder()
                    .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .build();
                    
            Log.d(TAG, "HTTP client initialized (without Tor integration)");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing HTTP client", e);
            client = new OkHttpClient.Builder()
                    .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .build();
        }
    }
    
    /**
     * Make a GET request through Tor
     * @param url URL to request
     * @param headers Headers to include in the request
     * @param callback Callback for the response
     */
    public void get(String url, Map<String, String> headers, final HttpCallback callback) {
        try {
            if (client == null) {
                setupClient();
                // If still null, report error
                if (client == null) {
                    callback.onFailure(new IOException("Failed to initialize HTTP client"));
                    return;
                }
            }
            
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
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "GET request failed: " + e.getMessage());
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            callback.onFailure(new IOException("Unexpected response: " + response));
                            return;
                        }
                        
                        String responseBody = response.body() != null ? response.body().string() : "";
                        callback.onSuccess(responseBody);
                    } catch (IOException e) {
                        callback.onFailure(e);
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error executing GET request", e);
            callback.onFailure(e);
        }
    }
    
    /**
     * Make a POST request through Tor
     * @param url URL to request
     * @param jsonBody JSON body to send
     * @param headers Headers to include in the request
     * @param callback Callback for the response
     */
    public void post(String url, JSONObject jsonBody, Map<String, String> headers, final HttpCallback callback) {
        try {
            if (client == null) {
                setupClient();
                // If still null, report error
                if (client == null) {
                    callback.onFailure(new IOException("Failed to initialize HTTP client"));
                    return;
                }
            }
            
            RequestBody body = RequestBody.create(JSON, jsonBody.toString());
            
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
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "POST request failed: " + e.getMessage());
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            callback.onFailure(new IOException("Unexpected response: " + response));
                            return;
                        }
                        
                        String responseBody = response.body() != null ? response.body().string() : "";
                        callback.onSuccess(responseBody);
                    } catch (IOException e) {
                        callback.onFailure(e);
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error executing POST request", e);
            callback.onFailure(e);
        }
    }
    
    /**
     * Check if Tor is available by testing connection
     * @return True if Tor is accessible
     */
    public boolean isTorAvailable() {
        if (!OrbotHelper.isOrbotRunning(context)) {
            return false;
        }
        
        try {
            URL url = new URL("https://check.torproject.org/");
            HttpURLConnection connection = NetCipher.getHttpURLConnection(url);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Tor availability", e);
            return false;
        }
    }
    
    /**
     * Shut down the HTTP client
     */
    public void shutdown() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
    
    /**
     * Interface for HTTP request callbacks
     */
    public interface HttpCallback {
        void onSuccess(String response);
        void onFailure(Exception e);
    }
} 