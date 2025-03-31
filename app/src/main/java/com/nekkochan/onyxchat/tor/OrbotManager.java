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
     * Prompt to install Orbot if not already installed.
     */
    public void promptToInstallOrbot() {
        // NetCipher 2.1.0 doesn't support this method
        // OrbotHelper.get(context).promptToInstall(context);
        
        // Instead, use direct intent
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=org.torproject.android"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error prompting to install Orbot", e);
            // Fallback to direct download
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://guardianproject.info/apps/org.torproject.android/"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e2) {
                Log.e(TAG, "Error launching direct download", e2);
            }
        }
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
        
        Intent intent = OrbotHelper.getOrbotStartIntent(context);
        activity.startActivity(intent);
        
        orbotConnected.set(true);
        if (statusListener != null) {
            statusListener.onOrbotConnected();
        }
    }
    
    /**
     * Handle the activity result from Orbot.
     * @param requestCode The request code
     * @param resultCode The result code
     * @param data The intent data
     * @return true if this was an Orbot result
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
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