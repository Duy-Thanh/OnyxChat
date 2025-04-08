package com.nekkochan.onyxchat.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ChatService;
import com.nekkochan.onyxchat.ui.chat.ChatActivity;
import com.nekkochan.onyxchat.db.AppDatabase;
import com.nekkochan.onyxchat.model.Contact;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class NotificationUtil {
    
    private static final String CHANNEL_ID_MESSAGES = "onyxchat_messages";
    private static final String CHANNEL_ID_FOREGROUND = "onyxchat_service";
    private static final String TAG = "NotificationUtil";
    
    // Store active notification conversations to update them
    private static final Map<String, List<NotificationMessage>> activeNotifications = new HashMap<>();
    
    // Class to store message details for notifications
    private static class NotificationMessage {
        String text;
        long timestamp;
        
        NotificationMessage(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
        }
    }
    
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
            Log.e(TAG, "Cannot show notification: message or sender ID is null");
            return;
        }
        
        // Create a unique notification ID for this conversation
        String senderId = message.getSenderId();
        int notificationId = senderId.hashCode();
        
        // Find contact display name instead of using raw sender ID
        String senderName = getContactDisplayName(context, senderId);
        
        Log.d(TAG, "Preparing notification for message from " + senderName + 
            " (ID: " + senderId + ")" +
            ", notificationId=" + notificationId + ", content=" + message.getContent());
        
        // Create an intent to open the chat with this sender
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONTACT_ID, senderId);
        intent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, senderName);
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
                Log.w(TAG, "Error parsing JSON message content", e);
            }
        }
        
        // Get or create the messages list for this sender
        List<NotificationMessage> messages = activeNotifications.get(senderId);
        if (messages == null) {
            messages = new ArrayList<>();
            activeNotifications.put(senderId, messages);
        }
        
        // Add the new message to the list
        messages.add(new NotificationMessage(messageContent, message.getTimestamp()));
        
        // Keep only last 5 messages
        if (messages.size() > 5) {
            messages = messages.subList(messages.size() - 5, messages.size());
            activeNotifications.put(senderId, messages);
        }
        
        Log.d(TAG, "Building notification with title=" + senderName + 
            ", messages count=" + messages.size());
        
        // Create notification with messaging style
        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle("Me")
                .setConversationTitle(senderName);
        
        // Add messages to the style
        for (NotificationMessage msg : messages) {
            // Messages from others should use the sender name
            style.addMessage(new NotificationCompat.MessagingStyle.Message(
                    msg.text, 
                    msg.timestamp, 
                    senderName)); // Using human-readable sender name
        }
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_message_notification)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);
        
        // Show the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            Log.d(TAG, "Posting notification with ID " + notificationId);
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Notification posted successfully");
        } catch (SecurityException e) {
            // Permission to show notification was denied
            Log.e(TAG, "Failed to show notification: Permission denied", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error showing notification", e);
        }
    }
    
    /**
     * Get display name for a contact ID
     * 
     * @param context Application context
     * @param contactId The contact's ID
     * @return The display name of the contact, or the ID if not found
     */
    private static String getContactDisplayName(Context context, String contactId) {
        try {
            // If it's an email, format it nicely
            if (contactId.contains("@")) {
                // Get the part before @ symbol
                String username = contactId.split("@")[0];
                // Capitalize first letter and format
                return username.substring(0, 1).toUpperCase() + username.substring(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting contact display name", e);
        }
        
        // Fallback to contact ID
        return contactId;
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