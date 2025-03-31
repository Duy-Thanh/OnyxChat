package com.nekkochan.onyxchat.tor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import info.guardianproject.netcipher.proxy.OrbotHelper;

/**
 * Helper class for Orbot installation and status checking
 */
public class OrbotInstaller {
    private static final String TAG = "OrbotInstaller";

    /**
     * Check if Orbot is installed on the device
     * @param context Application context
     * @return true if Orbot is installed
     */
    public static boolean isOrbotInstalled(@NonNull Context context) {
        return OrbotHelper.isOrbotInstalled(context);
    }

    /**
     * Check if Orbot is currently running
     * @param context Application context
     * @return true if Orbot is running
     */
    public static boolean isOrbotRunning(@NonNull Context context) {
        return OrbotHelper.isOrbotRunning(context);
    }

    /**
     * Prompt the user to install Orbot
     * @param activity Activity making the request
     * @param preferFDroid Whether to prefer F-Droid over Google Play
     */
    public static void promptToInstallOrbot(@NonNull Activity activity, boolean preferFDroid) {
        try {
            if (preferFDroid) {
                try {
                    // Try F-Droid first
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("market://details?id=org.torproject.android"));
                    intent.setPackage("org.fdroid.fdroid");
                    activity.startActivity(intent);
                    return;
                } catch (Exception e) {
                    Log.d(TAG, "F-Droid not available, falling back to Google Play or direct download");
                }
            }
            
            // NetCipher 2.1.0 doesn't have promptToInstall
            // OrbotHelper.get(activity).promptToInstall(activity);
            
            // Use Google Play instead
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://details?id=org.torproject.android"));
                activity.startActivity(intent);
            } catch (Exception e) {
                // Fallback to direct download
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://guardianproject.info/apps/org.torproject.android/"));
                activity.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error prompting to install Orbot", e);
        }
    }

    /**
     * Request Orbot to start with a specific app identifier
     * @param activity Activity making the request
     * @param appId App identifier to request Tor for (package name)
     */
    public static void requestOrbotStart(@NonNull Activity activity, String appId) {
        // NetCipher 2.1.0 doesn't support these methods
        // OrbotHelper.get(activity)
        //         .statusTimeout(60)
        //         .setExitNodes("*,*,*,*")
        //         .requestStart(activity, appId);
        
        // Use basic intent instead
        Intent intent = OrbotHelper.getOrbotStartIntent(activity);
        activity.startActivity(intent);
    }
} 