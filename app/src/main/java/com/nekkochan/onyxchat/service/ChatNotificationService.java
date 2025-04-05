package com.nekkochan.onyxchat.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ChatService;
import com.nekkochan.onyxchat.network.WebSocketClient;
import com.nekkochan.onyxchat.ui.chat.ChatActivity;
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
    
    // Delay before promoting to foreground after boot (in milliseconds)
    private static final long BOOT_TO_FOREGROUND_DELAY = 3000; // 3 seconds (reduced from 30 seconds)
    
    private ChatService chatService;
    private UserSessionManager sessionManager;
    private int nextNotificationId = MESSAGE_NOTIFICATION_START_ID;
    private Handler mainHandler;
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
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting Chat Notification Service");
        
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
                startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification);
            }
            isRunningAsForeground = true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
        }
    }
    
    /**
     * Connect to the chat service with the given user ID
     */
    private void connectToChatService(String userId) {
        Log.d(TAG, "Connecting to chat service as user: " + userId);
        
        // Listen for new messages
        chatService.getLatestMessage().observeForever(chatMessage -> {
            if (chatMessage != null && !chatMessage.isSelf()) {
                // Only show notifications for direct messages from others
                if (chatMessage.getType() == ChatService.ChatMessage.MessageType.DIRECT) {
                    showMessageNotification(chatMessage);
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
        // Don't show notifications for our own messages
        if (message.isSelf()) {
            return;
        }
        
        // Create an intent to open the chat with this sender
        Intent chatIntent = new Intent(this, ChatActivity.class);
        chatIntent.putExtra("userId", message.getSenderId());
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New message from " + message.getSenderId())
                .setContentText(message.getContent())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        
        // Show the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        try {
            notificationManager.notify(nextNotificationId++, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to show notification", e);
        }
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
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying Chat Notification Service");
        
        // Disconnect from chat service
        chatService.disconnect();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 