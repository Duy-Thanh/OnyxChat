package com.nekkochan.onyxchat.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.nekkochan.onyxchat.util.UserSessionManager;

/**
 * Receiver for device boot event to start the chat notification service
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed, checking if user is logged in");
            
            // Check if user is logged in
            UserSessionManager sessionManager = new UserSessionManager(context);
            if (sessionManager.isLoggedIn()) {
                Log.d(TAG, "User is logged in, starting notification service");
                
                // Start the service
                Intent serviceIntent = new Intent(context, ChatNotificationService.class);
                serviceIntent.setAction(ChatNotificationService.ACTION_START_SERVICE);
                
                // For Android O and above, we need to start as a foreground service
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.d(TAG, "User is not logged in, not starting service");
            }
        }
    }
} 