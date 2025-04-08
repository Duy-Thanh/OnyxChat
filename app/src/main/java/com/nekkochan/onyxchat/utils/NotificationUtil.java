package com.nekkochan.onyxchat.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ChatService;
import com.nekkochan.onyxchat.ui.chat.ChatActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class NotificationUtil {
    
    private static final String CHANNEL_ID_MESSAGES = "onyxchat_messages";
    private static final String CHANNEL_ID_FOREGROUND = "onyxchat_service";
    
    /**
     * Create notification channels required by the app
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel messagesChannel = new NotificationChannel(
                    CHANNEL_ID_MESSAGES,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            messagesChannel.setDescription("Notifications for new chat messages");
            messagesChannel.enableVibration(true);
            messagesChannel.setShowBadge(true);
            
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_FOREGROUND,
                    "Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Notification for keeping chat service running");
            serviceChannel.setShowBadge(false);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(messagesChannel);
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }
    
    /**
     * Show notification for a new message
     */
    public static void showMessageNotification(Context context, ChatService.ChatMessage message) {
        if (message == null || message.getSenderId() == null) {
            return;
        }
        
        // Create a unique notification ID for this conversation
        int notificationId = message.getSenderId().hashCode();
        
        // Create an intent to open the chat with this sender
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONTACT_ID, message.getSenderId());
        intent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, message.getSenderId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get message content to display in notification
        String content = message.getContent();
        String messageContent = content;
        
        // Check if this is a media message (JSON)
        if (content != null && content.startsWith("{") && content.endsWith("}")) {
            try {
                // Parse the JSON to get the media type
                JSONObject jsonObject = new JSONObject(content);
                if (jsonObject.has("type")) {
                    String mediaType = jsonObject.getString("type");
                    String caption = jsonObject.optString("caption", "");
                    
                    if (!caption.isEmpty()) {
                        messageContent = caption;
                    } else {
                        messageContent = "📎 " + mediaType + " message";
                    }
                }
            } catch (JSONException e) {
                // Not JSON or invalid JSON, use as is
            }
        }
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_message_notification)
                .setContentTitle(message.getSenderId())
                .setContentText(messageContent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);
        
        // Show the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // Permission to show notification was denied
        }
    }
    
    /**
     * Create a foreground service notification
     */
    public static NotificationCompat.Builder createForegroundServiceNotification(Context context) {
        // Create intent to open the app when notification is tapped
        Intent notificationIntent = new Intent(context, ChatActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the notification
        return new NotificationCompat.Builder(context, CHANNEL_ID_FOREGROUND)
                .setContentTitle("OnyxChat")
                .setContentText("Connected to chat server")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true);
    }
} 