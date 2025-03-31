package com.nekkochan.onyxchat.tor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import info.guardianproject.netcipher.proxy.OrbotHelper;
import okhttp3.OkHttpClient;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages Tor network connectivity for anonymous communication using NetCipher and Orbot
 */
public class TorManager {
    private static final String TAG = "TorManager";
    private static final String ONION_ADDRESS_PREF_KEY = "onion_address";
    private static final String PRIVATE_KEY_PREF_KEY = "onion_private_key";
    
    // Default Tor SOCKS port
    public static final int TOR_SOCKS_PORT = 9050;
    
    private static TorManager INSTANCE;
    private final Context context;
    private String onionAddress;
    private boolean isRunning = false;
    private TorConnectionListener connectionListener;

    private TorManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Get the singleton instance of TorManager
     */
    public static synchronized TorManager getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new TorManager(context);
        }
        return INSTANCE;
    }

    /**
     * Initialize and start Orbot
     * @param listener Listener for connection events
     */
    public void startTor(TorConnectionListener listener) {
        this.connectionListener = listener;
        
        if (isRunning && OrbotHelper.isOrbotRunning(context)) {
            if (connectionListener != null) {
                connectionListener.onTorConnected(getOnionAddress());
            }
            return;
        }
        
        try {
            Log.d(TAG, "Starting Orbot...");
            
            if (!OrbotHelper.isOrbotInstalled(context)) {
                Log.e(TAG, "Orbot is not installed");
                if (connectionListener != null) {
                    connectionListener.onTorError("Orbot is not installed. Please install Orbot first.");
                }
                return;
            }
            
            // Setup Orbot status callback
            OrbotHelper.get(context).addStatusCallback(new OrbotHelper.StatusCallback() {
                @Override
                public void onEnabled(Intent intent) {
                    isRunning = true;
                    setupHiddenService();
                }

                @Override
                public void onStarting() {
                    Log.d(TAG, "Orbot is starting");
                }

                @Override
                public void onStopping() {
                    Log.d(TAG, "Orbot is stopping");
                    isRunning = false;
                    if (connectionListener != null) {
                        connectionListener.onTorDisconnected();
                    }
                }

                @Override
                public void onDisabled() {
                    Log.d(TAG, "Orbot has been disabled");
                    isRunning = false;
                    if (connectionListener != null) {
                        connectionListener.onTorDisconnected();
                    }
                }

                @Override
                public void onStatusTimeout() {
                    Log.e(TAG, "Orbot status timeout");
                    if (connectionListener != null) {
                        connectionListener.onTorError("Orbot status timeout");
                    }
                }

                @Override
                public void onNotYetInstalled() {
                    Log.e(TAG, "Orbot not yet installed");
                    if (connectionListener != null) {
                        connectionListener.onTorError("Orbot is not installed. Please install Orbot first.");
                    }
                }
            });
            
            // Request Orbot to start
            if (context instanceof Activity) {
                OrbotHelper.get(context).requestStartTor((Activity) context);
            } else {
                OrbotHelper.get(context).requestStart(context);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Orbot", e);
            if (connectionListener != null) {
                connectionListener.onTorError("Failed to start Orbot: " + e.getMessage());
            }
        }
    }

    /**
     * Set up a hidden service to generate an onion address
     */
    private void setupHiddenService() {
        try {
            // Check if we already have an onion address
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            onionAddress = prefs.getString(ONION_ADDRESS_PREF_KEY, null);
            
            if (onionAddress != null && !onionAddress.isEmpty()) {
                Log.d(TAG, "Using existing onion address");
                
                if (connectionListener != null) {
                    connectionListener.onTorConnected(onionAddress);
                }
                return;
            }
            
            // When using Orbot, generating an actual onion address requires using
            // Orbot's OnionService APIs or running a Tor hidden service configuration
            // For now, we'll use a simulated address for testing purposes
            onionAddress = generateSimulatedOnionAddress();
            
            // Save the address for future use
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(ONION_ADDRESS_PREF_KEY, onionAddress);
            editor.apply();
            
            if (connectionListener != null) {
                connectionListener.onTorConnected(onionAddress);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up hidden service", e);
            if (connectionListener != null) {
                connectionListener.onTorError("Failed to set up hidden service: " + e.getMessage());
            }
        }
    }

    /**
     * Generate a simulated onion address for testing
     * In a real app, you would use Orbot's APIs to create a real onion service
     */
    private String generateSimulatedOnionAddress() {
        String random = UUID.randomUUID().toString().substring(0, 16).replace("-", "");
        return "onyxchat" + random + ".onion";
    }

    /**
     * Stop Orbot
     */
    public void stopTor() {
        if (!isRunning) {
            return;
        }
        
        try {
            OrbotHelper.get(context).requestStop(context);
            isRunning = false;
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Orbot", e);
        }
    }

    /**
     * Get the current onion address
     * @return The onion address or null if not connected
     */
    public String getOnionAddress() {
        if (onionAddress == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            onionAddress = prefs.getString(ONION_ADDRESS_PREF_KEY, null);
        }
        return onionAddress;
    }

    /**
     * Check if Tor is currently running
     * @return True if running
     */
    public boolean isRunning() {
        return isRunning && OrbotHelper.isOrbotRunning(context);
    }

    /**
     * Handle activity result from Orbot
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OrbotHelper.REQUEST_CODE_STATUS) {
            if (resultCode == Activity.RESULT_OK) {
                isRunning = true;
                setupHiddenService();
            } else {
                if (connectionListener != null) {
                    connectionListener.onTorError("Failed to start Orbot");
                }
            }
        }
    }

    /**
     * Listener interface for Tor connection events
     */
    public interface TorConnectionListener {
        void onTorConnected(String onionAddress);
        void onTorDisconnected();
        void onTorError(String errorMessage);
    }
} 