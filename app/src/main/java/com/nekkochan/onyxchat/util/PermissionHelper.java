package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Helper class for checking and requesting permissions
 */
public class PermissionHelper {

    /**
     * Check if the app has the specified permission
     *
     * @param context    The context
     * @param permission The permission to check
     * @return True if the permission is granted, false otherwise
     */
    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if the app has all the specified permissions
     *
     * @param context     The context
     * @param permissions The permissions to check
     * @return True if all permissions are granted, false otherwise
     */
    public static boolean hasPermissions(Context context, String... permissions) {
        if (context == null || permissions == null) {
            return false;
        }
        
        for (String permission : permissions) {
            if (!hasPermission(context, permission)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Request permissions from an activity
     *
     * @param activity          The activity
     * @param permissions       The permissions to request
     * @param onResult          Callback when permissions are granted or denied
     */
    public static void requestPermissions(AppCompatActivity activity, String[] permissions, Consumer<Boolean> onResult) {
        // Check which permissions we need to request
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(activity, permission)) {
                permissionsToRequest.add(permission);
            }
        }
        
        // If all permissions are already granted, return success
        if (permissionsToRequest.isEmpty()) {
            if (onResult != null) {
                onResult.accept(true);
            }
            return;
        }
        
        // Create permission launcher
        ActivityResultLauncher<String[]> requestPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    // Check if all permissions are granted
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    
                    if (onResult != null) {
                        onResult.accept(allGranted);
                    }
                });
        
        // Request permissions
        requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
    }

    /**
     * Request permissions from a fragment
     *
     * @param fragment     The fragment
     * @param permissions  The permissions to request
     * @param onResult     Callback when permissions are granted or denied
     */
    public static void requestPermissions(Fragment fragment, String[] permissions, Consumer<Boolean> onResult) {
        Context context = fragment.getContext();
        if (context == null) {
            if (onResult != null) {
                onResult.accept(false);
            }
            return;
        }
        
        // Check which permissions we need to request
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(context, permission)) {
                permissionsToRequest.add(permission);
            }
        }
        
        // If all permissions are already granted, return success
        if (permissionsToRequest.isEmpty()) {
            if (onResult != null) {
                onResult.accept(true);
            }
            return;
        }
        
        // Create permission launcher
        ActivityResultLauncher<String[]> requestPermissionLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    // Check if all permissions are granted
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    
                    if (onResult != null) {
                        onResult.accept(allGranted);
                    }
                });
        
        // Request permissions
        requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
    }

    /**
     * Check if we should show a rationale for requesting permissions
     *
     * @param activity    The activity
     * @param permissions The permissions to check
     * @return True if we should show a rationale for any of the permissions, false otherwise
     */
    public static boolean shouldShowRationale(AppCompatActivity activity, String... permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }
        return false;
    }
} 