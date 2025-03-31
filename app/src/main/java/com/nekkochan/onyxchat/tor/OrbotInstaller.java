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
     * Prompt the user to install Orbot via Google Play Store or F-Droid
     * @param activity Activity to launch installation from
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
            
            // Use NetCipher's built-in prompt
            OrbotHelper.get(activity).promptToInstall(activity);
        } catch (Exception e) {
            Log.e(TAG, "Error prompting to install Orbot", e);
            
            // Fallback to direct download
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://guardianproject.info/apps/org.torproject.android/"));
                activity.startActivity(intent);
            } catch (Exception e2) {
                Log.e(TAG, "Error launching direct download", e2);
            }
        }
    }

    /**
     * Request Orbot to start with a specific app identifier
     * @param activity Activity making the request
     * @param appId App identifier to request Tor for (package name)
     */
    public static void requestOrbotStart(@NonNull Activity activity, String appId) {
        OrbotHelper.get(activity)
                .statusTimeout(60)
                .setExitNodes("*,*,*,*")
                .requestStart(activity, appId);
    }
} 