package com.nekkochan.onyxchat.tor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.nekkochan.onyxchat.data.SafeHelperFactory;

/**
 * BroadcastReceiver that starts the MessengerService when the device boots
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed, starting MessengerService");
            
            try {
                // Initialize SQLCipher first to avoid crashes
                final Context appContext = context.getApplicationContext();
                SafeHelperFactory.initSQLCipher(appContext);
                
                // Add a delay to make sure system is fully loaded
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        // Start the MessengerService
                        Intent serviceIntent = new Intent(appContext, MessengerService.class);
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            appContext.startForegroundService(serviceIntent);
                        } else {
                            appContext.startService(serviceIntent);
                        }
                        
                        Log.d(TAG, "MessengerService started successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting MessengerService", e);
                    }
                }, 5000); // 5 second delay
            } catch (Exception e) {
                Log.e(TAG, "Error in BootReceiver", e);
            }
        }
    }
} 