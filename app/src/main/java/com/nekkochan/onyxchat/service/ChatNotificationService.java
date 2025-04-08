package com.nekkochan.onyxchat.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ChatService;
import com.nekkochan.onyxchat.network.WebSocketClient;
import com.nekkochan.onyxchat.ui.chat.ChatActivity;
import com.nekkochan.onyxchat.utils.NotificationUtil;
import com.nekkochan.onyxchat.util.UserSessionManager;

/**
 * A foreground service that maintains a WebSocket connection to receive chat notifications
 * even when the app is in the background.
 */
public class ChatNotificationService extends Service {
    private static final String TAG = "ChatNotificationService";
    
    // Notification channels
    private static final String FOREGROUND_CHANNEL_ID = "onyxchat_foreground_service";
    private static final String MESSAGE_CHANNEL_ID = "onyxchat_new_messages";
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int MESSAGE_NOTIFICATION_START_ID = 1000;
    
    // Service actions
    public static final String ACTION_START_SERVICE = "com.nekkochan.onyxchat.START_NOTIFICATION_SERVICE";
    public static final String ACTION_STOP_SERVICE = "com.nekkochan.onyxchat.STOP_NOTIFICATION_SERVICE";
    public static final String ACTION_START_FROM_BOOT = "com.nekkochan.onyxchat.START_FROM_BOOT";
    public static final String ACTION_RESTART_SERVICE = "com.nekkochan.onyxchat.RESTART_SERVICE";
    public static final String ACTION_ALARM_PING = "com.nekkochan.onyxchat.ALARM_PING";
    
    // Alarm constants
    private static final int ALARM_ID = 12345;
    private static final long ALARM_INTERVAL = 60 * 1000; // 1 minute
    
    // WakeLock for keeping service alive
    private PowerManager.WakeLock wakeLock;
    
    // Alarm manager for periodic wake up
    private AlarmManager alarmManager;
    
    // Alarm ping receiver
    private BroadcastReceiver alarmReceiver;
    
    // Delay before promoting to foreground after boot (in milliseconds)
    private static final long BOOT_TO_FOREGROUND_DELAY = 3000; // 3 seconds (reduced from 30 seconds)
    
    // Memory management
    private static final long MEMORY_CLEANUP_INTERVAL_MS = 300000; // 5 minutes
    private Handler mainHandler;
    private Runnable memoryCleanupRunnable;
    
    private ChatService chatService;
    private UserSessionManager sessionManager;
    private int nextNotificationId = MESSAGE_NOTIFICATION_START_ID;
    private boolean isRunningAsForeground = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Creating Chat Notification Service");
        
        // Create notification channels
        createNotificationChannels();
        
        // Initialize user session manager
        sessionManager = new UserSessionManager(this);
        
        // Initialize chat service
        chatService = ChatService.getInstance(this);
        
        // Initialize handler for delayed tasks
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Set up periodic memory cleanup
        setupMemoryCleanup();
        
        // Set up the alarm manager for periodic wake-up
        setupAlarmManager();
        
        // Set up the wake lock
        setupWakeLock();
        
        // Register alarm receiver
        registerAlarmReceiver();
    }
    
    /**
     * Set up the wake lock to keep service alive
     */
    private void setupWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OnyxChat:ChatServiceWakeLock");
        wakeLock.setReferenceCounted(false);
    }
    
    /**
     * Set up alarm manager for periodic wake-up
     */
    private void setupAlarmManager() {
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        
        // Start periodic alarm
        startAlarm();
    }
    
    /**
     * Register the alarm receiver
     */
    private void registerAlarmReceiver() {
        alarmReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_ALARM_PING.equals(intent.getAction())) {
                    Log.d(TAG, "Received alarm ping to keep service alive");
                    
                    // Check connection and reconnect if needed
                    String userId = sessionManager.getUserId();
                    if (userId != null && !userId.isEmpty()) {
                        // Check WebSocket status
                        if (chatService != null) {
                            if (!chatService.checkConnectionStatus()) {
                                Log.d(TAG, "Connection needs to be reestablished");
                                chatService.connect(userId);
                            } else {
                                Log.d(TAG, "Connection is still active");
                            }
                        }
                    }
                    
                    // Ensure the service is still in foreground
                    if (!isRunningAsForeground) {
                        startServiceAsForeground();
                    }
                }
            }
        };
        
        // Register for alarm pings
        registerReceiver(alarmReceiver, new IntentFilter(ACTION_ALARM_PING), RECEIVER_EXPORTED);
    }
    
    /**
     * Start the alarm for periodic wake-up
     */
    private void startAlarm() {
        Intent intent = new Intent(this, ChatNotificationService.class);
        intent.setAction(ACTION_ALARM_PING);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                ALARM_ID,
                new Intent(ACTION_ALARM_PING),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        try {
            // Schedule repeating alarm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle for newer Android versions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12 (API 31) and above requires SCHEDULE_EXACT_ALARM permission
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                                pendingIntent
                        );
                        Log.d(TAG, "Scheduled exact alarm successfully");
                    } else {
                        // Fall back to inexact alarm if we don't have permission
                        Log.w(TAG, "No permission for exact alarms, using inexact alarm instead");
                        alarmManager.set(
                                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                                pendingIntent
                        );
                    }
                } else {
                    // For Android versions < 12
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                            pendingIntent
                    );
                }
            } else {
                // Use setExact for older Android versions
                alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                        pendingIntent
                );
            }
            
            Log.d(TAG, "Alarm scheduled to wake up service every " + (ALARM_INTERVAL / 1000) + " seconds");
        } catch (SecurityException e) {
            // Handle the case where we don't have permission to set exact alarms
            Log.e(TAG, "No permission to set exact alarms, falling back to inexact alarm", e);
            
            // Fall back to using an inexact alarm
            alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                    pendingIntent
            );
        }
    }
    
    /**
     * Set up periodic memory cleanup to reduce RAM usage
     */
    private void setupMemoryCleanup() {
        memoryCleanupRunnable = new Runnable() {
            @Override
            public void run() {
                performMemoryCleanup();
                // Schedule next cleanup
                mainHandler.postDelayed(this, MEMORY_CLEANUP_INTERVAL_MS);
            }
        };
        
        // Start the periodic memory cleanup
        mainHandler.postDelayed(memoryCleanupRunnable, MEMORY_CLEANUP_INTERVAL_MS);
    }
    
    /**
     * Perform memory cleanup to reduce RAM usage
     */
    private void performMemoryCleanup() {
        Log.d(TAG, "Performing service memory cleanup");
        
        // Clear any cached data that might be holding references
        System.gc();
        
//        // Request WebSocketClient to clean up any unused resources
//        if (chatService != null) {
//            // If we have access to the WebSocketClient, tell it to clean up
//            WebSocketClient client = chatService.getWebSocketClient();
//            if (client != null) {
//                client.forceMemoryCleanup();
//            }
//        }
        
        // Log memory usage
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        Log.d(TAG, String.format("Memory usage: %.2f MB / %.2f MB (%.1f%%)", 
                usedMemory / (1024.0 * 1024.0), 
                maxMemory / (1024.0 * 1024.0),
                usedMemory * 100.0 / maxMemory));
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting Chat Notification Service");
        
        // Acquire wake lock to keep service alive
        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10 * 60 * 1000L); // 10 minutes max
                Log.d(TAG, "WakeLock acquired");
            }
        } catch (SecurityException e) {
            // WAKE_LOCK permission not granted
            Log.e(TAG, "Failed to acquire wake lock: " + e.getMessage());
            // Continue without wake lock
        }
        
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "Service action: " + action);
            
            if (ACTION_STOP_SERVICE.equals(action)) {
                Log.d(TAG, "Stopping service per request");
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_START_FROM_BOOT.equals(action)) {
                // When starting from boot, we first connect as a background service
                Log.d(TAG, "Starting from boot completed broadcast");
                handleStartFromBoot();
                return START_STICKY;
            } else if (ACTION_RESTART_SERVICE.equals(action)) {
                Log.d(TAG, "Restarting service");
                // Handle restart specially
                startServiceAsForeground();
                return START_STICKY;
            } else if (ACTION_ALARM_PING.equals(action)) {
                Log.d(TAG, "Received alarm ping in onStartCommand");
                
                // Schedule next alarm
                startAlarm();
                
                return START_STICKY;
            }
        }
        
        // For regular starts (not from boot), start as foreground immediately
        startServiceAsForeground();
        
        // Connect to the chat service if we have a valid user
        String userId = sessionManager.getUserId();
        if (userId != null && !userId.isEmpty()) {
            connectToChatService(userId);
        } else {
            Log.w(TAG, "No user ID available, service will wait for login");
        }
        
        // If this service is killed, restart it
        return START_STICKY;
    }
    
    /**
     * Handle a start request coming from boot completed
     */
    private void handleStartFromBoot() {
        // Start as foreground IMMEDIATELY to avoid system killing service
        startServiceAsForeground();
        
        // Connect to chat service in the background
        String userId = sessionManager.getUserId();
        if (userId != null && !userId.isEmpty()) {
            connectToChatService(userId);
        }
    }
    
    /**
     * Start the service as a foreground service with proper notification
     */
    private void startServiceAsForeground() {
        if (isRunningAsForeground) {
            Log.d(TAG, "Service is already running as foreground, no need to promote");
            return;
        }
        
        Log.d(TAG, "Starting as foreground service");
        Notification notification = createForegroundNotification();
        
        try {
            // For Android 14+ (API 34+), specify a foreground service type
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification);
            }
            isRunningAsForeground = true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
            // Fallback to non-typed foreground service if possible
            try {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification);
                isRunningAsForeground = true;
            } catch (Exception e2) {
                Log.e(TAG, "Error starting fallback foreground service", e2);
            }
        }
    }
    
    /**
     * Connect to the chat service with the given user ID
     */
    private void connectToChatService(String userId) {
        Log.d(TAG, "Connecting to chat service as user: " + userId);
        
        // Listen for new messages
        chatService.getLatestMessage().observeForever(chatMessage -> {
            if (chatMessage != null) {
                Log.d(TAG, "New message received in service: " + chatMessage.getContent() 
                    + ", type: " + chatMessage.getType() 
                    + ", isSelf: " + chatMessage.isSelf()
                    + ", senderId: " + chatMessage.getSenderId());
                
                // Don't show notifications for our own messages
                if (!chatMessage.isSelf() && !chatMessage.getSenderId().equals(userId)) {
                    // Handle different message types
                    if (chatMessage.getType() == ChatService.ChatMessage.MessageType.DIRECT 
                            || chatMessage.getType() == ChatService.ChatMessage.MessageType.MESSAGE) {
                        Log.d(TAG, "Showing notification for message from: " + chatMessage.getSenderId());
                        showMessageNotification(chatMessage);
                    }
                }
            }
        });
        
        // Connect to the WebSocket
        chatService.connect(userId);
        
        // Listen for connection state changes
        chatService.getConnectionState().observeForever(state -> {
            Log.d(TAG, "WebSocket connection state changed: " + state);
            
            // If disconnected, try to reconnect automatically
            if (state == WebSocketClient.WebSocketState.DISCONNECTED) {
                // Service will attempt to reconnect automatically through the ChatService
                Log.d(TAG, "WebSocket disconnected - the client will automatically try to reconnect with exponential backoff");
            } else if (state == WebSocketClient.WebSocketState.CONNECTED) {
                Log.d(TAG, "WebSocket connected - the service is now active");
                // Update the notification to show connected status
                updateServiceNotification("Connected to chat server");
            } else if (state == WebSocketClient.WebSocketState.CONNECTING) {
                Log.d(TAG, "WebSocket connecting...");
                // Update the notification to show connecting status
                updateServiceNotification("Connecting to chat server...");
            }
        });
    }
    
    /**
     * Update the foreground service notification text
     */
    private void updateServiceNotification(String contentText) {
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        Notification notification = createForegroundNotification(contentText);
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification);
    }
    
    /**
     * Create the foreground notification for the service with custom text
     */
    private Notification createForegroundNotification(String contentText) {
        // Create an intent for opening the app
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create a notification for the foreground service
        return new NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                .setContentTitle("OnyxChat")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
    
    /**
     * Create the foreground notification for the service
     */
    private Notification createForegroundNotification() {
        return createForegroundNotification("Connected to chat server");
    }
    
    /**
     * Show a notification for a new message
     */
    private void showMessageNotification(ChatService.ChatMessage message) {
        if (message == null || message.getSenderId() == null) {
            Log.e(TAG, "Cannot show notification: message or sender ID is null");
            return;
        }
        
        // Don't show notifications for our own messages
        if (message.isSelf() || message.getSenderId().equals(sessionManager.getUserId())) {
            Log.d(TAG, "Skipping notification for own message");
            return;
        }
        
        // Only show notifications for direct messages (not system or error messages)
        if (message.getType() != ChatService.ChatMessage.MessageType.DIRECT &&
            message.getType() != ChatService.ChatMessage.MessageType.MESSAGE) {
            Log.d(TAG, "Skipping notification for non-direct message type: " + message.getType());
            return;
        }
        
        Log.d(TAG, "Creating notification for message from " + message.getSenderId() + ": " + message.getContent());
        
        // Use the NotificationUtil to handle the notification
        NotificationUtil.showMessageNotification(this, message);
        
        // Send a broadcast to refresh any open chat screens
        Intent refreshIntent = new Intent("com.nekkochan.onyxchat.REFRESH_MESSAGES");
        refreshIntent.putExtra("senderId", message.getSenderId());
        refreshIntent.putExtra("recipientId", message.getRecipientId());
        refreshIntent.putExtra("timestamp", message.getTimestamp());
        sendBroadcast(refreshIntent);
    }
    
    /**
     * Create the notification channels for Android O and above
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the foreground service channel
            NotificationChannel serviceChannel = new NotificationChannel(
                    FOREGROUND_CHANNEL_ID,
                    "OnyxChat Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps OnyxChat connected in the background");
            
            // Create the new message channel
            NotificationChannel messageChannel = new NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    "New Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            messageChannel.setDescription("Notifications for new messages");
            
            // Register the channels
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(messageChannel);
        }
    }
    
    /**
     * Schedule a restart of the service using AlarmManager
     */
    private void scheduleServiceRestart() {
        Intent restartIntent = new Intent(this, ChatNotificationService.class);
        restartIntent.setAction(ACTION_RESTART_SERVICE);
        
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 
                1, 
                restartIntent, 
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Schedule service restart in 5 seconds
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    // Use exact alarms if we have permission (Android 12+)
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + 5000,
                            pendingIntent
                    );
                    Log.d(TAG, "Service restart scheduled precisely in 5 seconds");
                } else {
                    // Fall back to inexact alarm 
                    alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + 5000,
                            pendingIntent
                    );
                    Log.d(TAG, "Service restart scheduled approximately in 5 seconds (inexact)");
                }
            } else {
                // Pre-marshmallow devices
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 5000,
                        pendingIntent
                );
                Log.d(TAG, "Service restart scheduled in 5 seconds");
            }
        } catch (SecurityException e) {
            // Handle permission denial
            Log.e(TAG, "Failed to schedule exact restart due to missing permission, using inexact", e);
            
            // Fall back to inexact alarm that doesn't require special permissions
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 5000,
                    pendingIntent
            );
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying Chat Notification Service");
        
        // Stop memory cleanup
        if (mainHandler != null && memoryCleanupRunnable != null) {
            mainHandler.removeCallbacks(memoryCleanupRunnable);
        }
        
        // Release wake lock
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "WakeLock released");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing wake lock", e);
        }
        
        // Unregister alarm receiver
        try {
            if (alarmReceiver != null) {
                unregisterReceiver(alarmReceiver);
                alarmReceiver = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering alarm receiver", e);
        }
        
        // Disconnect from chat service
        chatService.disconnect();
        
        // Schedule service restart
        scheduleServiceRestart();
        
        // Clear references
        chatService = null;
        sessionManager = null;
        mainHandler = null;
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed, scheduling service restart");
        
        // Schedule service restart when the app is removed from recent apps
        scheduleServiceRestart();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 