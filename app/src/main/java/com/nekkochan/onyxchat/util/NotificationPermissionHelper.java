package com.nekkochan.onyxchat.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

/**
 * Helper class to manage notification permissions for Android 13+ devices
 */
public class NotificationPermissionHelper {
    private static final String TAG = "NotificationHelper";
    
    /**
     * Check if notification permission is granted
     * @param context The context to check in
     * @return true if permission is granted or not required (Android < 13), false otherwise
     */
    public static boolean hasNotificationPermission(Context context) {
        // No permission needed for Android < 13 (API 33)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        
        return ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Register an activity launcher for notification permission requests
     * @param activity The activity to register with (must implement ActivityResultCaller)
     * @param callback Callback to be invoked when the permission request is complete
     * @return ActivityResultLauncher that can be used to request permission
     */
    public static ActivityResultLauncher<String> registerForPermissionResult(
            ActivityResultCaller activity,
            PermissionCallback callback) {
            
        return activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    Log.d(TAG, "Notification permission granted: " + isGranted);
                    callback.onPermissionResult(isGranted);
                });
    }
    
    /**
     * Request notification permission if needed
     * @param activity The activity to request from
     * @param permissionLauncher The launcher registered with registerForPermissionResult
     * @return true if permission is already granted or not required, false if request was initiated
     */
    public static boolean requestNotificationPermissionIfNeeded(
            Activity activity,
            ActivityResultLauncher<String> permissionLauncher) {
            
        // No permission needed for Android < 13 (API 33)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        
        // Check if permission is already granted
        if (hasNotificationPermission(activity)) {
            return true;
        }
        
        // Request permission
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        return false;
    }
    
    /**
     * Callback interface for permission results
     */
    public interface PermissionCallback {
        /**
         * Called when the permission request is complete
         * @param isGranted Whether the permission was granted
         */
        void onPermissionResult(boolean isGranted);
    }
} 