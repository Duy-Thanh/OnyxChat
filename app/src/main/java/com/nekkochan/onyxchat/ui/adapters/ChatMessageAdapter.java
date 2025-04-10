package com.nekkochan.onyxchat.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ApiClient;
import com.nekkochan.onyxchat.ui.media.MediaViewerActivity;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;
import com.nekkochan.onyxchat.utils.MimeTypeUtils;
import com.nekkochan.onyxchat.util.UserSessionManager;
import com.nekkochan.onyxchat.ui.chat.ChatDocumentHandler;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.Target;

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

    private final JsonParser jsonParser = new JsonParser();

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
        
        // Check if this is a video file path before binding
        String content = message.getContent();
        
        // Direct detection of video paths in raw text messages
        if (content != null && 
            (content.contains("/api/media/file/") || content.startsWith("/api/media/")) && 
            (content.endsWith(".mp4") || content.endsWith(".mov") || content.endsWith(".avi"))) {
            
            Log.d(TAG, "Detected raw video path: " + content);
            
            // Create a proper media JSON message for videos
            try {
                // Use Gson for consistent JSON handling
                JsonObject videoJson = new JsonObject();
                videoJson.addProperty("type", "VIDEO");
                videoJson.addProperty("url", content);
                String jsonContent = videoJson.toString();
                
                // Create a new message with the JSON content
                ChatViewModel.ChatMessage videoMessage = new ChatViewModel.ChatMessage(
                    message.getType(),
                    message.getSenderId(),
                    message.getRecipientId(),
                    jsonContent,
                    message.getTimestamp()
                );
                
                // Bind the new message
                holder.bind(videoMessage, previousMessage, nextMessage);
                Log.d(TAG, "Converted raw video path to JSON: " + jsonContent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to convert video path to JSON", e);
                holder.bind(message, previousMessage, nextMessage);
            }
        } else {
            // Bind the original message
            holder.bind(message, previousMessage, nextMessage);
        }
        
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
        // Quick check to avoid trying to parse regular text messages as JSON
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        // Check if this is a video file path
        if (content.contains("/api/media/file/") && 
            (content.endsWith(".mp4") || content.endsWith(".mov") || content.endsWith(".avi"))) {
            Log.d(TAG, "Detected video file path: " + content);
            return new MediaContent("VIDEO", content, "");
        }
        
        // Only attempt to parse as JSON if the content starts with a curly brace
        // This prevents regular text messages from causing parse errors
        if (!content.trim().startsWith("{")) {
            return null;
        }
        
        try {
            Log.d(TAG, "Attempting to parse message content: " + content);
            
            // Try with JsonParser
            com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(content));
            reader.setLenient(true);
            JsonObject jsonObject = jsonParser.parse(reader).getAsJsonObject();
            
            if (jsonObject.has("type") && jsonObject.has("url")) {
                String type = jsonObject.get("type").getAsString().toUpperCase();
                String url = jsonObject.get("url").getAsString();
                String caption = jsonObject.has("caption") ? jsonObject.get("caption").getAsString() : "";
                
                // Check if this is a video message
                if (type.equals("VIDEO") || url.toLowerCase().endsWith(".mp4") || 
                    url.toLowerCase().endsWith(".mov") || url.toLowerCase().endsWith(".avi")) {
                    type = "VIDEO";
                }
                
                Log.d(TAG, String.format("Successfully parsed media message - Type: %s, URL: %s, Caption: %s", 
                    type, url, caption));
                
                return new MediaContent(type, url, caption);
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
        
        // Check if this is a document type
        boolean isDocument() {
            if (type == null) {
                return false;
            }
            
            // Direct check for document type
            if (type.equals("document")) {
                return true;
            }
            
            // Check if the type corresponds to a document MIME type
            String mimeType = getMimeTypeFromExtension(type);
            return mimeType != null && MimeTypeUtils.isDocument(mimeType);
        }
        
        // Convert file extension to MIME type
        private String getMimeTypeFromExtension(String extension) {
            switch (extension.toLowerCase()) {
                case "pdf": return "application/pdf";
                case "doc": return "application/msword";
                case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xls": return "application/vnd.ms-excel";
                case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "ppt": return "application/vnd.ms-powerpoint";
                case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "odt": return "application/vnd.oasis.opendocument.text";
                case "ods": return "application/vnd.oasis.opendocument.spreadsheet";
                case "odp": return "application/vnd.oasis.opendocument.presentation";
                case "txt": return "text/plain";
                case "rtf": return "text/rtf";
                case "csv": return "text/csv";
                default: return null;
            }
        }
    }
    
    class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final TextView timeText;
        private final TextView captionText;
        private final ImageView mediaImageView;
        private final ImageView playButtonView;
        private final PlayerView videoPlayerView;
        private final View documentView;
        private final TextView documentName;
        private final TextView documentInfo;
        private final ImageView documentIcon;
        
        // ExoPlayer instance for video playback
        private ExoPlayer player;
        
        // Track which message this player is associated with
        private String currentVideoUrl;
        
        public ChatMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            captionText = itemView.findViewById(R.id.caption_text);
            mediaImageView = itemView.findViewById(R.id.media_image_view);
            playButtonView = itemView.findViewById(R.id.play_button);
            videoPlayerView = itemView.findViewById(R.id.video_player_view);
            
            // Document view elements
            documentView = itemView.findViewById(R.id.document_attachment);
            documentName = documentView != null ? documentView.findViewById(R.id.document_name) : null;
            documentInfo = documentView != null ? documentView.findViewById(R.id.document_info) : null;
            documentIcon = documentView != null ? documentView.findViewById(R.id.document_icon) : null;
        }
        
        /**
         * Process a media URL to ensure it's in the correct format
         * @param url The original URL
         * @return The processed URL
         */
        private String processMediaUrl(String url) {
            if (url == null || url.isEmpty()) {
                return "";
            }
            
            Log.d(TAG, "Processing media URL: " + url);
            
            // If it's already a full URL, return it
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
            
            // If it's a server path starting with /api
            if (url.startsWith("/api/")) {
                try {
                    // Get base URL from ApiClient
                    String baseUrl = ApiClient.getBaseUrl();
                    if (baseUrl.endsWith("/")) {
                        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                    }
                    
                    String fullUrl = baseUrl + url;
                    Log.d(TAG, "Converted server path to full URL: " + fullUrl);
                    return fullUrl;
                } catch (Exception e) {
                    Log.e(TAG, "Error getting base URL", e);
                    return url;
                }
            }
            
            // If it's a local file path
            if (url.startsWith("/")) {
                return "file://" + url;
            }
            
            // Default case, return as is
            return url;
        }
        
        /**
         * Add authentication to a URL if needed
         * @param url The URL to authenticate
         * @param context The context
         * @return The authenticated URL
         */
        private String getAuthenticatedUrl(String url, Context context) {
            if (url == null || url.isEmpty()) {
                return "";
            }
            
            // If it's a server URL, add authentication token
            if (url.startsWith("http://") || url.startsWith("https://")) {
                try {
                    // Get token from UserSessionManager
                    UserSessionManager sessionManager = new UserSessionManager(context);
                    String token = sessionManager.getAuthToken();
                    
                    // Add token as query parameter if not already present
                    if (token != null && !token.isEmpty() && !url.contains("token=")) {
                        String separator = url.contains("?") ? "&" : "?";
                        return url + separator + "token=" + token;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error adding authentication to URL", e);
                }
            }
            
            // Return original URL if no authentication needed or if there was an error
            return url;
        }
        
        public void bind(ChatViewModel.ChatMessage message, ChatViewModel.ChatMessage previousMessage, ChatViewModel.ChatMessage nextMessage) {
            // Check if this is a media message
            MediaContent mediaContent = parseMediaContent(message.getContent());
            
            if (mediaContent != null) {
                Log.d(TAG, "Binding media content: " + mediaContent.type + " - " + mediaContent.url);
                
                if (mediaContent.isDocument()) {
                    // Handle document message
                    handleDocumentMessage(mediaContent);
                } else if (mediaContent.type.equals("VIDEO")) {
                    // Handle video message with ExoPlayer
                    handleVideoContent(mediaContent.url, mediaContent.caption);
                } else if (mediaContent.type.equals("IMAGE")) {
                    // Handle image message with Glide
                    handleImageContent(mediaContent.url, mediaContent.caption);
                } else {
                    // Regular text message
                    messageText.setText(mediaContent.caption.isEmpty() 
                            ? mediaContent.url : mediaContent.caption);
                    messageText.setVisibility(View.VISIBLE);
                    
                    // Hide media views
                    if (mediaImageView != null) {
                        mediaImageView.setVisibility(View.GONE);
                    }
                    if (videoPlayerView != null) {
                        videoPlayerView.setVisibility(View.GONE);
                    }
                    if (playButtonView != null) {
                        playButtonView.setVisibility(View.GONE);
                    }
                }
            } else {
                // Regular text message
                messageText.setText(message.getContent());
                messageText.setVisibility(View.VISIBLE);
                
                // Hide media views
                if (mediaImageView != null) {
                    mediaImageView.setVisibility(View.GONE);
                }
                if (videoPlayerView != null) {
                    videoPlayerView.setVisibility(View.GONE);
                }
                if (playButtonView != null) {
                    playButtonView.setVisibility(View.GONE);
                }
                if (documentView != null) {
                    documentView.setVisibility(View.GONE);
                }
            }
            
            // Log the time of this specific message
            Date timestamp = message.getTimestamp();
            String formattedTime = formatTimeConsistently(timestamp);
            Log.d(TAG, String.format("Binding message [%s]: timestamp=%s, formatted=%s, ms=%d", 
                    message.getContent(),
                    timestamp,
                    formattedTime,
                    timestamp.getTime()));
            
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
            View messageCardView = itemView.findViewById(R.id.messageCardView);
            if (messageCardView != null) {
                float cornerRadius = itemView.getContext().getResources().getDimension(R.dimen.message_corner_radius);
                
                // Set the base corner radius
                if (messageCardView instanceof androidx.cardview.widget.CardView) {
                    ((androidx.cardview.widget.CardView) messageCardView).setRadius(cornerRadius);
                }
                
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
        
        /**
         * Handle video content with ExoPlayer
         */
        private void handleVideoContent(String videoUrl, String caption) {
            // Hide text and show video player
            messageText.setVisibility(View.GONE);
            
            if (mediaImageView != null) {
                mediaImageView.setVisibility(View.GONE);
            }
            
            if (videoPlayerView != null) {
                videoPlayerView.setVisibility(View.VISIBLE);
            }
            
            // Hide play button as ExoPlayer has its own controls
            if (playButtonView != null) {
                playButtonView.setVisibility(View.GONE);
            }
            
            // Hide document view if it exists
            if (documentView != null) {
                documentView.setVisibility(View.GONE);
            }
            
            // Set caption if available
            if (captionText != null) {
                if (caption != null && !caption.isEmpty()) {
                    captionText.setText(caption);
                    captionText.setVisibility(View.VISIBLE);
                } else {
                    captionText.setVisibility(View.GONE);
                }
            }
            
            // Process the URL (handle server paths, local paths, etc.)
            String processedUrl = processMediaUrl(videoUrl);
            Log.d(TAG, "Processing video URL: " + processedUrl + " from original: " + videoUrl);
            
            // Initialize ExoPlayer if needed
            initializePlayer(processedUrl);
            
            // Set click listener for fullscreen view
            videoPlayerView.setOnClickListener(v -> {
                // Pause the player
                if (player != null) {
                    player.pause();
                }
                
                // Open in full screen viewer
                Intent intent = new Intent(itemView.getContext(), MediaViewerActivity.class);
                intent.putExtra("mediaUrl", processedUrl);
                intent.putExtra("mediaType", "VIDEO");
                intent.putExtra("mediaCaption", caption);
                itemView.getContext().startActivity(intent);
            });
        }
        
        /**
         * Initialize ExoPlayer for video playback
         */
        private void initializePlayer(String videoUrl) {
            // Check if we're already playing this video
            if (videoUrl.equals(currentVideoUrl) && player != null) {
                return;
            }
            
            // Release any existing player
            releasePlayer();
            
            try {
                // Create a new ExoPlayer instance
                player = new ExoPlayer.Builder(itemView.getContext()).build();
                
                // Set player to the view
                videoPlayerView.setPlayer(player);
                
                // Create a MediaItem
                String authenticatedUrl = getAuthenticatedUrl(videoUrl, itemView.getContext());
                MediaItem mediaItem = MediaItem.fromUri(authenticatedUrl);
                
                // Set the media item to be played
                player.setMediaItem(mediaItem);
                
                // Prepare the player
                player.prepare();
                
                // Set playback parameters
                player.setPlayWhenReady(false); // Don't auto-play
                player.setRepeatMode(Player.REPEAT_MODE_ONE); // Loop the video
                
                // Save the current video URL
                currentVideoUrl = videoUrl;
                
                Log.d(TAG, "ExoPlayer initialized for URL: " + videoUrl);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing ExoPlayer", e);
                // Fallback to thumbnail if player fails
                fallbackToThumbnail(videoUrl);
            }
        }
        
        /**
         * Release ExoPlayer resources
         */
        private void releasePlayer() {
            if (player != null) {
                player.release();
                player = null;
                currentVideoUrl = null;
                Log.d(TAG, "ExoPlayer released");
            }
        }
        
        /**
         * Fallback to showing a thumbnail if video playback fails
         */
        private void fallbackToThumbnail(String videoUrl) {
            // Show image view and play button instead
            if (mediaImageView != null) {
                mediaImageView.setVisibility(View.VISIBLE);
            }
            
            if (videoPlayerView != null) {
                videoPlayerView.setVisibility(View.GONE);
            }
            
            if (playButtonView != null) {
                playButtonView.setVisibility(View.VISIBLE);
            }
            
            // Load thumbnail with Glide
            try {
                RequestOptions requestOptions = new RequestOptions()
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.error_image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .frame(1000000); // Use a frame from 1 second in
                
                Glide.with(itemView.getContext())
                        .load(videoUrl)
                        .apply(requestOptions)
                        .into(mediaImageView);
                
                Log.d(TAG, "Fallback to thumbnail for: " + videoUrl);
            } catch (Exception e) {
                Log.e(TAG, "Error loading video thumbnail", e);
            }
        }
        
        /**
         * Handle image content with Glide
         */
        private void handleImageContent(String imageUrl, String caption) {
            // Show image view, hide video player
            if (mediaImageView != null) {
                mediaImageView.setVisibility(View.VISIBLE);
            }
            
            if (videoPlayerView != null) {
                videoPlayerView.setVisibility(View.GONE);
            }
            
            // Hide play button for images
            if (playButtonView != null) {
                playButtonView.setVisibility(View.GONE);
            }
            
            // Set caption if available
            if (captionText != null) {
                if (caption != null && !caption.isEmpty()) {
                    captionText.setText(caption);
                    captionText.setVisibility(View.VISIBLE);
                } else {
                    captionText.setVisibility(View.GONE);
                }
            }
            
            // Load the image with Glide
            try {
                RequestOptions requestOptions = new RequestOptions()
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.error_image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL);
                
                Glide.with(itemView.getContext())
                        .load(getAuthenticatedUrl(imageUrl, itemView.getContext()))
                        .apply(requestOptions)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                Log.e(TAG, "Failed to load image: " + imageUrl, e);
                                return false;
                            }
                            
                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                Log.d(TAG, "Successfully loaded image: " + imageUrl);
                                return false;
                            }
                        })
                        .into(mediaImageView);
                
                // Set click listener to open media viewer
                mediaImageView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), MediaViewerActivity.class);
                    intent.putExtra("mediaUrl", imageUrl);
                    intent.putExtra("mediaType", "IMAGE");
                    intent.putExtra("mediaCaption", caption);
                    itemView.getContext().startActivity(intent);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading image", e);
            }
        }
        
        private void handleDocumentMessage(MediaContent mediaContent) {
            // Hide text and media, show document
            messageText.setVisibility(View.GONE);
            if (mediaImageView != null) {
                mediaImageView.setVisibility(View.GONE);
            }
            
            // Show document view if it exists
            if (documentView != null) {
                documentView.setVisibility(View.VISIBLE);
                
                // Extract filename from URL or use caption
                String fileNameValue = mediaContent.caption;
                if (fileNameValue == null || fileNameValue.isEmpty()) {
                    String url = mediaContent.url;
                    int lastSlash = url.lastIndexOf('/');
                    if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                        fileNameValue = url.substring(lastSlash + 1);
                    } else {
                        fileNameValue = "Document";
                    }
                }
                
                final String fileName = fileNameValue;
                
                // Set document name
                if (documentName != null) {
                    documentName.setText(fileName);
                }
                
                // Set document info
                if (documentInfo != null) {
                    String docType = mediaContent.type.toUpperCase();
                    documentInfo.setText(docType + " Document");
                }
                
                // Set document icon based on type
                if (documentIcon != null) {
                    int iconResId = R.drawable.ic_document; // Default document icon
                    
                    // Set specific icon based on document type
                    String type = mediaContent.type.toLowerCase();
                    if (type.equals("pdf")) {
                        iconResId = R.drawable.ic_pdf;
                    } else if (type.equals("doc") || type.equals("docx") || type.equals("odt") || type.equals("rtf")) {
                        iconResId = R.drawable.ic_word;
                    } else if (type.equals("xls") || type.equals("xlsx") || type.equals("ods") || type.equals("csv")) {
                        iconResId = R.drawable.ic_excel;
                    } else if (type.equals("ppt") || type.equals("pptx") || type.equals("odp")) {
                        iconResId = R.drawable.ic_powerpoint;
                    } else if (type.equals("txt")) {
                        iconResId = R.drawable.ic_text;
                    }
                    
                    documentIcon.setImageResource(iconResId);
                }
                
                // Set click listener to open document
                documentView.setOnClickListener(v -> {
                    // Process the URL to get the final form
                    String mediaUrl = processMediaUrl(mediaContent.url);
                    
                    // Convert to Uri and open document viewer
                    Uri fileUri = Uri.parse(mediaUrl);
                    ChatDocumentHandler.openDocument(itemView.getContext(), fileUri, fileName);
                });
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull ChatMessageViewHolder holder) {
        super.onViewRecycled(holder);
        // Release ExoPlayer resources when view is recycled
        holder.releasePlayer();
    }

    /**
     * Update the adapter with a new list of messages
     * @param messages The new list of messages
     */
    public void setChatMessages(List<ChatViewModel.ChatMessage> messages) {
        Log.d(TAG, "Setting " + messages.size() + " messages");
        this.messages = new ArrayList<>(messages);
        notifyDataSetChanged();
        lastAnimatedPosition = messages.size() - 5; // Only animate the last few messages
    }
} 