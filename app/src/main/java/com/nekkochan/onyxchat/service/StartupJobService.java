package com.nekkochan.onyxchat.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * JobService that starts the chat service after a delay.
 * This helps work around restrictions on foreground services at boot.
 */
public class StartupJobService extends JobService {
    private static final String TAG = "StartupJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Chat service startup job started");
        
        try {
            // Start the service from the job
            Intent serviceIntent = new Intent(this, ChatNotificationService.class);
            serviceIntent.setAction(ChatNotificationService.ACTION_START_FROM_BOOT);
            
            // The service will handle the foreground promotion internally
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting foreground service from job service");
                startForegroundService(serviceIntent);
            } else {
                Log.d(TAG, "Starting regular service from job service");
                startService(serviceIntent);
            }
            
            // Wait a moment to ensure service starts properly
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Sleep interrupted", e);
            }
            
            // Job is complete
            jobFinished(params, false);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error starting service from job", e);
            jobFinished(params, true); // Request retry
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // The system has determined that this job should stop running
        Log.d(TAG, "Chat service startup job stopped by system");
        return false; // Don't reschedule
    }
} 