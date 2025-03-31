package com.nekkochan.onyxchat.tor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import info.guardianproject.netcipher.NetCipher;
import info.guardianproject.netcipher.client.StrongConnectionBuilder;
import info.guardianproject.netcipher.proxy.OrbotHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/**
 * Manages network requests through Tor using NetCipher.
 */
public class TorNetworkManager {
    private static final String TAG = "TorNetworkManager";
    private static final int TIMEOUT_SECONDS = 60;
    
    private final Context context;
    private final OrbotManager orbotManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    public TorNetworkManager(Context context, OrbotManager orbotManager) {
        this.context = context;
        this.orbotManager = orbotManager;
    }
    
    /**
     * Makes an HTTP GET request through Tor.
     * @param urlString The URL to request
     * @return Future containing the response string
     */
    public Future<String> get(final String urlString) {
        return executor.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                if (!orbotManager.isOrbotConnected()) {
                    Log.e(TAG, "Orbot is not connected");
                    throw new IOException("Orbot is not connected");
                }
                
                HttpURLConnection connection = null;
                BufferedReader reader = null;
                StringBuilder response = new StringBuilder();
                
                try {
                    StrongConnectionBuilder builder = NetCipher.getHttpsURLConnection()
                            .connectTo(urlString)
                            .withTorValidation()
                            .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    
                    connection = builder.build();
                    connection.setRequestMethod("GET");
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new IOException("HTTP error: " + responseCode);
                    }
                    
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    return response.toString();
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing reader", e);
                        }
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }
    
    /**
     * Makes an HTTP POST request through Tor.
     * @param urlString The URL to request
     * @param postData The data to send
     * @return Future containing the response string
     */
    public Future<String> post(final String urlString, final String postData) {
        return executor.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                if (!orbotManager.isOrbotConnected()) {
                    Log.e(TAG, "Orbot is not connected");
                    throw new IOException("Orbot is not connected");
                }
                
                HttpURLConnection connection = null;
                BufferedReader reader = null;
                StringBuilder response = new StringBuilder();
                
                try {
                    StrongConnectionBuilder builder = NetCipher.getHttpsURLConnection()
                            .connectTo(urlString)
                            .withTorValidation()
                            .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    
                    connection = builder.build();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    
                    OutputStream os = connection.getOutputStream();
                    os.write(postData.getBytes());
                    os.flush();
                    os.close();
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new IOException("HTTP error: " + responseCode);
                    }
                    
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    return response.toString();
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing reader", e);
                        }
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }
    
    /**
     * Checks if Tor network is available by connecting to Orbot.
     * @return Future containing true if Tor is available
     */
    public Future<Boolean> isTorAvailable() {
        return executor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return OrbotHelper.isOrbotRunning(context);
            }
        });
    }
    
    /**
     * Cleanup resources when no longer needed.
     */
    public void shutdown() {
        executor.shutdown();
    }
} 