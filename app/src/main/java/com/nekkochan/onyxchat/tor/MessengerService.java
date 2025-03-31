package com.nekkochan.onyxchat.tor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.data.Message;
import com.nekkochan.onyxchat.data.Repository;
import com.nekkochan.onyxchat.data.SafeHelperFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling secure message exchange over Tor network
 * Note: This is a stub implementation for building purposes
 */
public class MessengerService extends Service implements TorManager.TorConnectionListener {
    private static final String TAG = "MessengerService";
    private static final int MESSAGE_SERVER_PORT = 9030;
    private static final int CONNECTION_TIMEOUT = 60000; // 60 seconds
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "OnyxChat_Service_Channel";

    private final IBinder binder = new MessengerBinder();
    private TorManager torManager;
    private Repository repository;
    private boolean isRunning = false;
    private Handler mainHandler;
    private Map<String, MessageListener> messageListeners = new HashMap<>();
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Create notification channel for foreground service
        createNotificationChannel();
        
        try {
            // Initialize SQLCipher before using Repository
            Context appContext = getApplicationContext();
            SafeHelperFactory.initSQLCipher(appContext);
            
            // Start as foreground service
            startForeground(NOTIFICATION_ID, createNotification("OnyxChat is starting..."));
            
            torManager = TorManager.getInstance(appContext);
            repository = new Repository(appContext);
            mainHandler = new Handler(Looper.getMainLooper());
            Log.d(TAG, "MessengerService created");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MessengerService", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startService();
        return START_STICKY;
    }

    /**
     * Create the notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "OnyxChat Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps OnyxChat connected to the Tor network");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Create a notification for the foreground service
     */
    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OnyxChat")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();
    }
    
    /**
     * Update the foreground notification
     */
    private void updateNotification(String contentText) {
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(contentText));
        }
    }

    /**
     * Start the messenger service and connect to Tor
     */
    public void startService() {
        if (isRunning || torManager == null) {
            return;
        }
        
        // Start Tor and register for callbacks
        updateNotification("Connecting to Tor network...");
        torManager.startTor(this);
    }

    /**
     * Stop the messenger service
     */
    public void stopService() {
        isRunning = false;
        
        // Stop Tor
        if (torManager != null) {
            torManager.stopTor();
        }
        
        // Update notification
        updateNotification("Disconnecting from Tor network...");
        
        // Stop foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    @Override
    public void onDestroy() {
        stopService();
        super.onDestroy();
    }

    @Override
    public void onTorConnected(String onionAddress) {
        Log.d(TAG, "Tor connected with address: " + onionAddress);
        isRunning = true;
        updateNotification("Connected to Tor network as " + onionAddress);
    }

    @Override
    public void onTorDisconnected() {
        Log.d(TAG, "Tor disconnected");
        isRunning = false;
        updateNotification("Disconnected from Tor network");
    }

    @Override
    public void onTorError(String errorMessage) {
        Log.e(TAG, "Tor error: " + errorMessage);
        isRunning = false;
        updateNotification("Tor error: " + errorMessage);
    }

    /**
     * Send a message to a contact - stub implementation 
     */
    public boolean sendMessage(String recipientAddress, String content, long expirationTime) {
        Log.d(TAG, "Would send message to " + recipientAddress + ": " + content);
        return true;
    }

    /**
     * Register a listener for messages from a specific contact
     */
    public void registerMessageListener(String contactAddress, MessageListener listener) {
        synchronized (messageListeners) {
            messageListeners.put(contactAddress, listener);
        }
    }

    /**
     * Unregister a message listener
     */
    public void unregisterMessageListener(String contactAddress) {
        synchronized (messageListeners) {
            messageListeners.remove(contactAddress);
        }
    }

    /**
     * Binder class for clients to get the service
     */
    public class MessengerBinder extends Binder {
        public MessengerService getService() {
            return MessengerService.this;
        }
    }

    /**
     * Interface for receiving messages
     */
    public interface MessageListener {
        void onMessageReceived(Message message);
    }
} 