package com.nekkochan.onyxchat.ui.adapters;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.media.MediaViewerActivity;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.json.JSONException;
import org.json.JSONObject;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder> {
    private static final String TAG = "ChatMessageAdapter";
    private List<ChatViewModel.ChatMessage> messages = new ArrayList<>();
    private final String currentUserId;
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final long ANIMATION_DURATION = 300;
    private int lastAnimatedPosition = -1;
    
    // Consistent time formatter for the entire app
    private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DATE_TIME_FORMATTER = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
    
    static {
        // Initialize formatters with device timezone
        TimeZone deviceTimeZone = TimeZone.getDefault();
        TIME_FORMATTER.setTimeZone(deviceTimeZone);
        DATE_TIME_FORMATTER.setTimeZone(deviceTimeZone);
        Log.d(TAG, "Initialized formatters with timezone: " + deviceTimeZone.getID());
    }

    public ChatMessageAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == VIEW_TYPE_SENT) 
                ? R.layout.item_message_sent 
                : R.layout.item_message_received;
        
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new ChatMessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        ChatViewModel.ChatMessage message = messages.get(position);
        ChatViewModel.ChatMessage previousMessage = position > 0 ? messages.get(position - 1) : null;
        ChatViewModel.ChatMessage nextMessage = position < messages.size() - 1 ? messages.get(position + 1) : null;
        
        holder.bind(message, previousMessage, nextMessage);
        
        // Add animation for new items
        setAnimation(holder.itemView, position);
    }
    
    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastAnimatedPosition) {
            AlphaAnimation animation = new AlphaAnimation(0.0f, 1.0f);
            animation.setDuration(ANIMATION_DURATION);
            viewToAnimate.startAnimation(animation);
            lastAnimatedPosition = position;
        }
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    @Override
    public int getItemViewType(int position) {
        ChatViewModel.ChatMessage message = messages.get(position);
        if (message.getSenderId().equals(currentUserId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    public void submitList(List<ChatViewModel.ChatMessage> newMessages) {
        // Log timestamps of messages for debugging
        if (newMessages != null && !newMessages.isEmpty()) {
            Log.d(TAG, "Adding " + newMessages.size() + " messages");
            for (ChatViewModel.ChatMessage message : newMessages) {
                if (message.getTimestamp() != null) {
                    String formattedTime = formatTimeConsistently(message.getTimestamp());
                    Log.d(TAG, String.format("Message [%s] timestamp: %s -> %s [%d ms]", 
                            message.getContent(),
                            message.getTimestamp(),
                            formattedTime,
                            message.getTimestamp().getTime()));
                } else {
                    Log.d(TAG, "Message [" + message.getContent() + "] has null timestamp!");
                }
            }
        }
        
        this.messages = new ArrayList<>(newMessages);
        notifyDataSetChanged();
    }
    
    /**
     * Format a time value consistently throughout the app
     * @param date The date to format
     * @return The formatted time string in the standard format (HH:mm)
     */
    private static String formatTimeConsistently(Date date) {
        if (date == null) return "";
        synchronized (TIME_FORMATTER) {
            return TIME_FORMATTER.format(date);
        }
    }
    
    /**
     * Format a date and time value consistently throughout the app
     * @param date The date to format
     * @return The formatted date and time string in the standard format (MMM dd, HH:mm)
     */
    private static String formatDateTimeConsistently(Date date) {
        if (date == null) return "";
        synchronized (DATE_TIME_FORMATTER) {
            return DATE_TIME_FORMATTER.format(date);
        }
    }
    
    /**
     * Check if two messages were sent on the same day
     */
    private boolean isSameDay(ChatViewModel.ChatMessage m1, ChatViewModel.ChatMessage m2) {
        if (m1 == null || m2 == null) return false;
        
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(m1.getTimestamp());
        cal2.setTime(m2.getTimestamp());
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Check if a date is today
     * @param date The date to check
     * @return true if the date is today, false otherwise
     */
    private boolean isToday(Date date) {
        if (date == null) return false;
        
        Calendar today = Calendar.getInstance();
        Calendar messageDate = Calendar.getInstance();
        messageDate.setTime(date);
        
        return today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Check if a message is part of a sequence from the same sender
     */
    private boolean isPartOfSequence(ChatViewModel.ChatMessage current, ChatViewModel.ChatMessage other) {
        if (current == null || other == null) return false;
        
        // Messages from same sender and timestamps within 3 minutes
        boolean sameSender = current.getSenderId().equals(other.getSenderId());
        boolean closeTime = Math.abs(current.getTimestamp().getTime() - other.getTimestamp().getTime()) < 3 * 60 * 1000;
        
        return sameSender && closeTime && isSameDay(current, other);
    }
    
    /**
     * Parse a message to determine if it contains media content
     */
    private MediaContent parseMediaContent(String content) {
        try {
            Log.d(TAG, "Attempting to parse message content: " + content);
            
            // First try parsing as direct JSON
            try {
                JSONObject jsonObject = new JSONObject(content);
                if (jsonObject.has("type") && jsonObject.has("url")) {
                    String type = jsonObject.getString("type");
                    String url = jsonObject.getString("url");
                    String caption = jsonObject.optString("caption", "");
                    
                    Log.d(TAG, String.format("Successfully parsed media message - Type: %s, URL: %s, Caption: %s", 
                        type, url, caption));
                    
                    return new MediaContent(type, url, caption);
                }
            } catch (JSONException e) {
                // If direct JSON parsing fails, try with JsonParser
                JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
                
                if (jsonObject.has("type") && jsonObject.has("url")) {
                    String type = jsonObject.get("type").getAsString();
                    String url = jsonObject.get("url").getAsString();
                    String caption = jsonObject.has("caption") ? jsonObject.get("caption").getAsString() : "";
                    
                    Log.d(TAG, String.format("Successfully parsed media message (using JsonParser) - Type: %s, URL: %s, Caption: %s", 
                        type, url, caption));
                    
                    return new MediaContent(type, url, caption);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse media message: " + e.getMessage());
            Log.e(TAG, "Raw content was: " + content);
        }
        
        return null;
    }
    
    /**
     * Media content holder class
     */
    private static class MediaContent {
        String type;
        String url;
        String caption;
        
        MediaContent(String type, String url, String caption) {
            this.type = type;
            this.url = url;
            this.caption = caption;
        }
    }
    
    class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final TextView timeText;
        private final CardView messageCardView;
        private final ImageView mediaImageView;
        private final ImageView playButtonView;
        
        public ChatMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            timeText = itemView.findViewById(R.id.message_time);
            messageCardView = itemView.findViewById(R.id.messageCardView);
            mediaImageView = itemView.findViewById(R.id.media_image_view);
            playButtonView = itemView.findViewById(R.id.play_button);
        }
        
        public void bind(ChatViewModel.ChatMessage message, ChatViewModel.ChatMessage previousMessage, ChatViewModel.ChatMessage nextMessage) {
            // Try to parse media content
            MediaContent mediaContent = parseMediaContent(message.getContent());
            
            if (mediaContent != null) {
                // This is a media message
                if (mediaContent.type.equalsIgnoreCase("VIDEO")) {
                    Log.d(TAG, "Setting up video message view with URL: " + mediaContent.url);
                    // Set up video message view
                    messageText.setVisibility(View.GONE);
                    mediaImageView.setVisibility(View.VISIBLE);
                    playButtonView.setVisibility(View.VISIBLE);
                    
                    // Set video thumbnail
                    Glide.with(itemView.getContext())
                            .load(mediaContent.url)
                            .apply(RequestOptions.centerCropTransform())
                            .thumbnail(0.1f)
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    Log.e(TAG, "Failed to load video thumbnail: " + (e != null ? e.getMessage() : "unknown error"));
                                    Log.e(TAG, "Video URL was: " + mediaContent.url);
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                    Log.d(TAG, "Successfully loaded video thumbnail for URL: " + mediaContent.url);
                                    return false;
                                }
                            })
                            .into(mediaImageView);
                            
                    // Make sure the play button is visible and properly styled
                    playButtonView.setVisibility(View.VISIBLE);
                    playButtonView.setAlpha(1.0f);
                    
                    // Set click listeners for both the image and play button
                    View.OnClickListener videoClickListener = v -> {
                        Log.d(TAG, "Video clicked, opening MediaViewerActivity with URL: " + mediaContent.url);
                        Intent intent = new Intent(itemView.getContext(), MediaViewerActivity.class);
                        intent.putExtra("mediaUrl", mediaContent.url);
                        intent.putExtra("mediaType", "VIDEO");
                        intent.putExtra("mediaSenderId", message.getSenderId());
                        itemView.getContext().startActivity(intent);
                    };
                    
                    mediaImageView.setOnClickListener(videoClickListener);
                    playButtonView.setOnClickListener(videoClickListener);
                } else if (mediaContent.type.equalsIgnoreCase("IMAGE")) {
                    // Set up image message view
                    messageText.setVisibility(View.GONE);
                    mediaImageView.setVisibility(View.VISIBLE);
                    playButtonView.setVisibility(View.GONE);
                    
                    // Load image
                    Glide.with(itemView.getContext())
                            .load(mediaContent.url)
                            .apply(RequestOptions.centerCropTransform())
                            .into(mediaImageView);
                            
                    // Set click listener to open image
                    mediaImageView.setOnClickListener(v -> {
                        Intent intent = new Intent(itemView.getContext(), MediaViewerActivity.class);
                        intent.putExtra("mediaUrl", mediaContent.url);
                        intent.putExtra("mediaType", "IMAGE");
                        intent.putExtra("mediaSenderId", message.getSenderId());
                        itemView.getContext().startActivity(intent);
                    });
                } else {
                    // Unknown media type - show caption or url as text
                    messageText.setVisibility(View.VISIBLE);
                    mediaImageView.setVisibility(View.GONE);
                    playButtonView.setVisibility(View.GONE);
                    
                    messageText.setText(mediaContent.caption.isEmpty() 
                            ? mediaContent.url : mediaContent.caption);
                }
            } else {
                // Regular text message
                messageText.setText(message.getContent());
                messageText.setVisibility(View.VISIBLE);
                if (mediaImageView != null) {
                    mediaImageView.setVisibility(View.GONE);
                }
                if (playButtonView != null) {
                    playButtonView.setVisibility(View.GONE);
                }
            }
            
            // Log the time of this specific message
            Date timestamp = message.getTimestamp();
            String formattedTime = formatTimeConsistently(timestamp);
            Log.d(TAG, String.format("Binding message [%s]: timestamp=%s, formatted=%s, ms=%d", 
                    message.getContent(), timestamp, formattedTime, timestamp.getTime()));
            
            // Manage message appearance based on sequence
            boolean isPartOfPreviousSequence = isPartOfSequence(message, previousMessage);
            boolean isPartOfNextSequence = isPartOfSequence(message, nextMessage);
            
            // Show timestamp for last message in sequence or when time gap is significant
            boolean showTime = !isPartOfNextSequence;
            timeText.setVisibility(showTime ? View.VISIBLE : View.GONE);
            
            // Format timestamp
            if (showTime && timestamp != null) {
                // Use consistent time formatting
                String timeString;
                if (isToday(timestamp)) {
                    // Only show time for today's messages
                    timeString = formatTimeConsistently(timestamp);
                } else {
                    // Show date and time for older messages
                    timeString = formatDateTimeConsistently(timestamp);
                }
                
                // Log the formatted time
                Log.d(TAG, "Formatted time for message [" + message.getContent() + "]: " + timeString);
                
                timeText.setText(timeString);
            }
            
            // If available, adjust bubble corners based on sequence
            if (messageCardView != null) {
                float cornerRadius = itemView.getContext().getResources().getDimension(R.dimen.message_corner_radius);
                
                // Set the base corner radius
                messageCardView.setRadius(cornerRadius);
                
                // Try to apply custom corner radius for grouped bubbles if supported by device
                try {
                    // We can adjust visual appearance based on sequence without manipulating each corner
                    // For example, we can add top or bottom margin for different messages in a sequence
                    int topMargin = isPartOfPreviousSequence ? 2 : 8;
                    int bottomMargin = isPartOfNextSequence ? 2 : 8;
                    
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) messageCardView.getLayoutParams();
                    if (params != null) {
                        params.topMargin = topMargin;
                        params.bottomMargin = bottomMargin;
                        messageCardView.setLayoutParams(params);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to adjust message margins", e);
                }
            }
        }
    }
} 