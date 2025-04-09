package com.nekkochan.onyxchat.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.media.MediaViewerActivity;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;
import com.nekkochan.onyxchat.util.UserSessionManager;
import com.nekkochan.onyxchat.ui.chat.ChatDocumentHandler;
import com.nekkochan.onyxchat.utils.MimeTypeUtils;

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

    private final com.google.gson.JsonParser jsonParser = new com.google.gson.JsonParser();

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
        // Quick check to avoid trying to parse regular text messages as JSON
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        // Check if this is a video file path
        if (content.contains("/api/media/file/") || content.matches("^/.*\\.mp4$")) {
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
                
                Log.d(TAG, String.format("Successfully parsed media message (using JsonParser) - Type: %s, URL: %s, Caption: %s", 
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
        private final CardView messageCardView;
        private final ImageView mediaImageView;
        private final ImageView playButtonView;
        private View documentView;
        private TextView documentName;
        private TextView documentInfo;
        private ImageView documentIcon;
        private TextView captionText;

        ChatMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            messageCardView = itemView.findViewById(R.id.messageCardView);
            mediaImageView = itemView.findViewById(R.id.media_image_view);
            playButtonView = itemView.findViewById(R.id.play_button);
            captionText = itemView.findViewById(R.id.caption_text);
            
            // Find document view if it exists
            documentView = itemView.findViewById(R.id.document_attachment);
            if (documentView != null) {
                documentName = documentView.findViewById(R.id.document_name);
                documentInfo = documentView.findViewById(R.id.document_info);
                documentIcon = documentView.findViewById(R.id.document_icon);
            }
        }
        
        /**
         * Convert a potentially relative server path to a proper HTTP URL
         */
        private String getProperMediaUrl(String mediaUrl) {
            if (mediaUrl == null || mediaUrl.isEmpty()) {
                return "";
            }
            
            try {
                // Clean up the mediaUrl to handle special characters or spaces
                mediaUrl = mediaUrl.trim();
                
                // If already a full URL, use as is
                if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) {
                    return mediaUrl;
                }
                
                // If starts with / but not with //, assume it's a server path
                if (mediaUrl.startsWith("/") && !mediaUrl.startsWith("//")) {
                    // Get the server URL from API client
                    String serverUrl = getBaseServerUrl(itemView.getContext());
                    
                    // Remove trailing slash from server URL if present
                    if (serverUrl.endsWith("/")) {
                        serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
                    }
                    
                    // Remove leading slash from media URL if present
                    String mediaPath = mediaUrl;
                    if (mediaPath.startsWith("/")) {
                        mediaPath = mediaPath.substring(1);
                    }
                    
                    // Combine to form full URL
                    String fullUrl = serverUrl + "/" + mediaPath;
                    Log.d(TAG, "Converted relative URL " + mediaUrl + " to absolute URL " + fullUrl);
                    
                    // For local emulator testing, check if we need to use HTTP instead of HTTPS
                    if (fullUrl.contains("10.0.2.2") && fullUrl.startsWith("https://")) {
                        String httpUrl = fullUrl.replace("https://", "http://");
                        Log.d(TAG, "Using HTTP for emulator: " + httpUrl);
                        return httpUrl;
                    }
                    
                    return fullUrl;
                }
                
                // For file paths or other URIs, use as is
                return mediaUrl;
            } catch (Exception e) {
                Log.e(TAG, "Error parsing media URL: " + mediaUrl, e);
                return "";
            }
        }
        
        /**
         * Get the base server URL from shared preferences
         */
        private String getBaseServerUrl(Context context) {
            // Default server URL (used in API client)
            String defaultUrl = "https://10.0.2.2:443";
            
            try {
                // Try to get the actual server URL from shared preferences
                SharedPreferences sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
                String serverUrl = sharedPreferences.getString("server_url", defaultUrl);
                
                // If empty or null, use default
                if (serverUrl == null || serverUrl.isEmpty()) {
                    return defaultUrl;
                }
                
                // Remove api path if present
                if (serverUrl.endsWith("/api")) {
                    serverUrl = serverUrl.substring(0, serverUrl.length() - 4);
                }
                
                return serverUrl;
            } catch (Exception e) {
                Log.e(TAG, "Error getting server URL", e);
                return defaultUrl;
            }
        }
        
        /**
         * Convert a potentially relative server path to a proper HTTP URL
         * and add authentication if needed
         */
        private GlideUrl getAuthenticatedUrl(String mediaUrl, Context context) {
            String absoluteUrl = getProperMediaUrl(mediaUrl);
            if (absoluteUrl.isEmpty()) {
                // Return a dummy URL for error handling
                return new GlideUrl("https://example.com/invalid");
            }
            
            // Get auth token from session manager
            UserSessionManager sessionManager = new UserSessionManager(context);
            String authToken = sessionManager.getAuthToken();
            
            if (authToken != null && !authToken.isEmpty()) {
                // Return URL with auth headers
                return new GlideUrl(
                    absoluteUrl,
                    new LazyHeaders.Builder()
                        .addHeader("Authorization", "Bearer " + authToken)
                        .build()
                );
            } else {
                // Fallback to URL without auth headers (will likely fail)
                Log.w(TAG, "No auth token available for media URL: " + absoluteUrl);
                return new GlideUrl(absoluteUrl);
            }
        }
        
        public void bind(ChatViewModel.ChatMessage message, ChatViewModel.ChatMessage previousMessage, ChatViewModel.ChatMessage nextMessage) {
            // Check if this is a media message
            MediaContent mediaContent = parseMediaContent(message.getContent());
            
            if (mediaContent != null) {
                if (mediaContent.isDocument()) {
                    // Handle document message
                    handleDocumentMessage(mediaContent);
                } else if (mediaContent.type.equals("image") || mediaContent.type.equals("video")) {
                    // Handle image or video message
                    handleMediaMessage(mediaContent);
                } else {
                    // Regular text message
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
                if (documentView != null) {
                    documentView.setVisibility(View.GONE);
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
        
        private void handleMediaMessage(MediaContent mediaContent) {
            // Hide text, show media
            messageText.setVisibility(View.GONE);
            if (mediaImageView != null) {
                mediaImageView.setVisibility(View.VISIBLE);
            }
            
            // Show play button for videos
            if (playButtonView != null) {
                playButtonView.setVisibility(mediaContent.type.equals("VIDEO") ? View.VISIBLE : View.GONE);
            }
            
            // Hide document view if it exists
            if (documentView != null) {
                documentView.setVisibility(View.GONE);
            }
            
            // Set caption if available
            if (!android.text.TextUtils.isEmpty(mediaContent.caption)) {
                captionText.setText(mediaContent.caption);
                captionText.setVisibility(View.VISIBLE);
            } else {
                captionText.setVisibility(View.GONE);
            }
            
            // Get the media URL
            String mediaUrl = mediaContent.url;
            Log.d(TAG, "Loading media from URL: " + mediaUrl);
            
            // Check if the URL is a server path or a local file path
            if (mediaUrl.startsWith("/api/media/file/")) {
                // Server path - prepend base URL
                mediaUrl = ApiClient.getBaseUrl() + mediaUrl;
                Log.d(TAG, "Converted to full URL: " + mediaUrl);
            } else if (mediaUrl.startsWith("/")) {
                // Local file path
                mediaUrl = "file://" + mediaUrl;
                Log.d(TAG, "Converted to file URL: " + mediaUrl);
            }
            
            // Load the image or video thumbnail
            Glide.with(itemView.getContext())
                .load(mediaUrl)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed to load media: " + mediaUrl, e);
                        return false;
                    }
                    
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Successfully loaded media: " + mediaUrl);
                        return false;
                    }
                })
                .into(mediaImageView);
            
            // Set click listener to open media viewer
            mediaImageView.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), MediaViewerActivity.class);
                intent.putExtra("mediaUrl", mediaUrl);
                intent.putExtra("mediaType", mediaContent.type);
                intent.putExtra("mediaCaption", mediaContent.caption);
                itemView.getContext().startActivity(intent);
            });
        }
        
        private void handleDocumentMessage(MediaContent mediaContent) {
            // Hide text and media, show document
            messageText.setVisibility(View.GONE);
            if (mediaImageView != null) {
                mediaImageView.setVisibility(View.GONE);
            }
            if (playButtonView != null) {
                playButtonView.setVisibility(View.GONE);
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
                
                // Set click listener to open document viewer
                documentView.setOnClickListener(v -> {
                    Context context = itemView.getContext();
                    String mediaUrl = getProperMediaUrl(mediaContent.url);
                    Uri fileUri = Uri.parse(mediaUrl);
                    
                    // Open document viewer
                    ChatDocumentHandler.openDocument(context, fileUri, fileName);
                });
            }
        }
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