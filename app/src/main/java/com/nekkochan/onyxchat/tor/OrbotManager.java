package com.nekkochan.onyxchat.tor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import info.guardianproject.netcipher.proxy.OrbotHelper;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages connectivity to Orbot for Tor networking.
 * This class uses Guardian Project's NetCipher to interact with the Orbot app.
 */
public class OrbotManager {
    private static final String TAG = "OrbotManager";
    private static final int REQUEST_CODE_ORBOT = 100;
    
    private final Context context;
    private final AtomicBoolean orbotConnected = new AtomicBoolean(false);
    private OrbotStatusListener statusListener;
    
    public interface OrbotStatusListener {
        void onOrbotConnected();
        void onOrbotDisconnected();
        void onOrbotError(String error);
    }
    
    public OrbotManager(Context context) {
        this.context = context;
    }
    
    public void setStatusListener(OrbotStatusListener listener) {
        this.statusListener = listener;
    }
    
    /**
     * Checks if Orbot is installed, and requests installation if needed.
     * @return true if Orbot is installed
     */
    public boolean checkOrbot() {
        if (OrbotHelper.isOrbotInstalled(context)) {
            Log.d(TAG, "Orbot is installed");
            return true;
        } else {
            Log.d(TAG, "Orbot is not installed");
            promptToInstallOrbot();
            return false;
        }
    }
    
    /**
     * Opens Play Store to install Orbot.
     */
    public void promptToInstallOrbot() {
        Toast.makeText(context, "Orbot is required for secure communication", Toast.LENGTH_LONG).show();
        OrbotHelper.get(context).promptToInstall(context);
    }
    
    /**
     * Starts Orbot and requests VPN mode.
     * @param activity The activity to receive the result
     */
    public void startOrbot(Activity activity) {
        if (!checkOrbot()) {
            return;
        }
        
        if (OrbotHelper.isOrbotRunning(context)) {
            orbotConnected.set(true);
            if (statusListener != null) {
                statusListener.onOrbotConnected();
            }
            return;
        }
        
        OrbotHelper.get(context).requestStartTor(activity);
        OrbotHelper.get(context).addStatusCallback(new OrbotHelper.StatusCallback() {
            @Override
            public void onEnabled(Intent intent) {
                orbotConnected.set(true);
                if (statusListener != null) {
                    statusListener.onOrbotConnected();
                }
            }

            @Override
            public void onStarting() {
                // Tor is starting
            }

            @Override
            public void onStopping() {
                orbotConnected.set(false);
                if (statusListener != null) {
                    statusListener.onOrbotDisconnected();
                }
            }

            @Override
            public void onDisabled() {
                orbotConnected.set(false);
                if (statusListener != null) {
                    statusListener.onOrbotDisconnected();
                }
            }

            @Override
            public void onStatusTimeout() {
                if (statusListener != null) {
                    statusListener.onOrbotError("Connection timeout");
                }
            }

            @Override
            public void onNotYetInstalled() {
                promptToInstallOrbot();
            }
        });
    }
    
    /**
     * Handle the activity result from Orbot.
     * @param requestCode The request code
     * @param resultCode The result code
     * @param data The intent data
     * @return true if this was an Orbot result
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OrbotHelper.REQUEST_CODE_STATUS) {
            if (resultCode == Activity.RESULT_OK) {
                orbotConnected.set(true);
                if (statusListener != null) {
                    statusListener.onOrbotConnected();
                }
            } else {
                orbotConnected.set(false);
                if (statusListener != null) {
                    statusListener.onOrbotError("Failed to start Orbot. Please try again.");
                }
            }
            return true;
        }
        return false;
    }
    
    /**
     * @return true if Orbot is connected
     */
    public boolean isOrbotConnected() {
        return orbotConnected.get() && OrbotHelper.isOrbotRunning(context);
    }
    
    /**
     * Disconnects from Orbot.
     */
    public void disconnect() {
        orbotConnected.set(false);
        if (statusListener != null) {
            statusListener.onOrbotDisconnected();
        }
        // Note: We don't actually stop Orbot, just disconnect from it
    }
} 