package com.nekkochan.onyxchat.tor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import info.guardianproject.netcipher.NetCipher;
import info.guardianproject.netcipher.client.StrongBuilder;
import info.guardianproject.netcipher.client.StrongConnectionBuilder;
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
     * Set up the OkHttp client with NetCipher integration
     */
    private void setupClient() {
        try {
            StrongConnectionBuilder
                .forMaxSecurity(context)
                .withTorValidation()
                .build(new StrongBuilder.Callback<OkHttpClient>() {
                    @Override
                    public void onConnected(OkHttpClient okHttpClient) {
                        client = okHttpClient;
                        Log.d(TAG, "Tor-enabled HTTP client ready");
                    }

                    @Override
                    public void onConnectionException(Exception e) {
                        Log.e(TAG, "Error setting up Tor HTTP client", e);
                        // Fallback to standard client with proxy
                        client = new OkHttpClient.Builder()
                                .proxy(NetCipher.getHttpsProxy())
                                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .build();
                    }

                    @Override
                    public void onTimeout() {
                        Log.e(TAG, "Timeout setting up Tor HTTP client");
                        // Fallback to standard client with proxy
                        client = new OkHttpClient.Builder()
                                .proxy(NetCipher.getHttpsProxy())
                                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .build();
                    }

                    @Override
                    public void onInvalid() {
                        Log.e(TAG, "Invalid Tor setup for HTTP client");
                        // Fallback to standard client with proxy
                        client = new OkHttpClient.Builder()
                                .proxy(NetCipher.getHttpsProxy())
                                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                                .build();
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Tor HTTP client", e);
            // Fallback to standard client with proxy
            client = new OkHttpClient.Builder()
                    .proxy(NetCipher.getHttpsProxy())
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
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "GET request failed: " + e.getMessage());
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            callback.onFailure(new IOException("Unexpected response: " + response));
                            return;
                        }
                        
                        String responseBody = response.body().string();
                        callback.onSuccess(responseBody);
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
            
            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            
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
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "POST request failed: " + e.getMessage());
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            callback.onFailure(new IOException("Unexpected response: " + response));
                            return;
                        }
                        
                        String responseBody = response.body().string();
                        callback.onSuccess(responseBody);
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