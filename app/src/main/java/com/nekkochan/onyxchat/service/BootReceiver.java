package com.nekkochan.onyxchat.service;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import com.nekkochan.onyxchat.service.ChatNotificationService;
import com.nekkochan.onyxchat.util.UserSessionManager;

/**
 * Broadcast receiver that starts the chat service when the device boots up.
 * This ensures that users can receive chat notifications even after a device restart.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final int STARTUP_JOB_ID = 1001;
    private static final int INITIAL_STARTUP_DELAY = 15; // seconds

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed received. Will start chat service if user is logged in.");
            
            UserSessionManager sessionManager = new UserSessionManager(context);
            if (sessionManager.isLoggedIn()) {
                Log.d(TAG, "User is logged in. Starting chat service...");
                scheduleServiceStart(context);
            } else {
                Log.d(TAG, "User is not logged in. Will not start chat service.");
            }
        }
    }

    /**
     * Schedule the service to start after a delay using JobScheduler
     * This is more reliable on newer Android versions
     */
    private void scheduleServiceStart(Context context) {
        Log.d(TAG, "Scheduling chat service start using JobScheduler");
        
        ComponentName componentName = new ComponentName(context, StartupJobService.class);
        
        JobInfo.Builder builder = new JobInfo.Builder(STARTUP_JOB_ID, componentName)
                .setMinimumLatency(INITIAL_STARTUP_DELAY * 1000) // delay in milliseconds
                .setOverrideDeadline((INITIAL_STARTUP_DELAY + 5) * 1000) // maximum delay
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // require network
                .setPersisted(true); // survive reboots
        
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        int resultCode = jobScheduler.schedule(builder.build());
        
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Successfully scheduled chat service start job");
        } else {
            Log.e(TAG, "Failed to schedule chat service start job, falling back to direct start");
            fallbackToDirectServiceStart(context);
        }
    }
    
    /**
     * Fallback method to start the service directly if job scheduling fails
     */
    private void fallbackToDirectServiceStart(Context context) {
        Log.d(TAG, "Attempting to start service directly as fallback");
        Intent serviceIntent = new Intent(context, ChatNotificationService.class);
        serviceIntent.setAction(ChatNotificationService.ACTION_START_FROM_BOOT);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
                Log.d(TAG, "Started service with startForegroundService (fallback)");
            } else {
                context.startService(serviceIntent);
                Log.d(TAG, "Started service with startService (fallback)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service directly", e);
        }
    }
} 