package com.nekkochan.onyxchat.ui.media;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.VideoView;
import android.widget.MediaController;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import android.os.Build;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.view.Gravity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ApiClient;
import com.nekkochan.onyxchat.utils.FileUtils;
import com.nekkochan.onyxchat.utils.MediaUtils;
import com.nekkochan.onyxchat.utils.MimeTypeUtils;
import com.nekkochan.onyxchat.ui.chat.ChatMessageItem;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

/**
 * Activity for processing images and videos before sending
 */
public class MediaProcessingActivity extends AppCompatActivity {
    private static final String TAG = "MediaProcessingActivity";
    
    public static final String EXTRA_MEDIA_URI = "media_uri";
    public static final String EXTRA_MEDIA_TYPE = "media_type";
    public static final String EXTRA_CHAT_ID = "chat_id";
    
    public static final String MEDIA_TYPE_IMAGE = "image";
    public static final String MEDIA_TYPE_VIDEO = "video";
    public static final String MEDIA_TYPE_DOCUMENT = "document";
    
    private ImageView imagePreview;
    private PlayerView videoPreview;
    private Button sendButton;
    private Button cancelButton;
    private TextView captionInput;
    private ProgressBar progressBar;
    private TextView documentInfo;
    private ImageView playPauseButton;
    
    private Uri mediaUri;
    private String mediaType;
    private String chatId;
    private ApiClient apiClient;
    
    // ExoPlayer related fields
    private ExoPlayer player;
    private boolean isPlaying = false;
    
    // Fallback related fields
    private VideoView standardVideoView;
    private boolean isUsingFallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set content view first before accessing window controller to avoid NPE
        setContentView(R.layout.activity_media_processing);
        
        // Configure window to properly handle system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        
        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Edit Media");
        
        // Initialize views
        imagePreview = findViewById(R.id.imagePreview);
        videoPreview = findViewById(R.id.videoPreview);
        sendButton = findViewById(R.id.sendButton);
        cancelButton = findViewById(R.id.cancelButton);
        captionInput = findViewById(R.id.captionInput);
        progressBar = findViewById(R.id.progressBar);
        documentInfo = findViewById(R.id.documentInfo);
        playPauseButton = findViewById(R.id.playPauseButton);
        
        // Set play/pause button click listener
        if (playPauseButton != null) {
            playPauseButton.setOnClickListener(v -> togglePlayback());
        }
        
        // Get API client
        apiClient = ApiClient.getInstance(this);
        
        // Get extras from intent
        Intent intent = getIntent();
        if (intent != null) {
            mediaUri = intent.getParcelableExtra(EXTRA_MEDIA_URI);
            mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE);
            chatId = intent.getStringExtra(EXTRA_CHAT_ID);
            
            if (mediaUri == null || mediaType == null) {
                Toast.makeText(this, "Error: Media not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            // Set up preview based on media type
            if (MEDIA_TYPE_IMAGE.equals(mediaType)) {
                setupImagePreview();
            } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
                setupVideoPreview();
            } else if (MEDIA_TYPE_DOCUMENT.equals(mediaType)) {
                setupDocumentPreview();
            } else {
                Toast.makeText(this, "Unsupported media type", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            finish();
            return;
        }
        
        // Set up buttons
        sendButton.setOnClickListener(v -> sendMedia());
        cancelButton.setOnClickListener(v -> finish());
    }
    
    /**
     * Set up image preview
     */
    private void setupImagePreview() {
        imagePreview.setVisibility(View.VISIBLE);
        videoPreview.setVisibility(View.GONE);
        documentInfo.setVisibility(View.GONE);
        if (playPauseButton != null) {
            playPauseButton.setVisibility(View.GONE);
        }
        
        try {
            imagePreview.setImageURI(mediaUri);
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    /**
     * Set up video preview with ExoPlayer
     */
    private void setupVideoPreview() {
        imagePreview.setVisibility(View.GONE);
        videoPreview.setVisibility(View.VISIBLE);
        documentInfo.setVisibility(View.GONE);
        
        // Hide custom play/pause button since we're using ExoPlayer controls
        if (playPauseButton != null) {
            playPauseButton.setVisibility(View.GONE);
        }
        
        // Show loading initially
        progressBar.setVisibility(View.VISIBLE);
        
        try {
            // Initialize ExoPlayer
            initializePlayer();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up video preview", e);
            // Fallback to standard VideoView
            useStandardVideoViewFallback();
        }
    }
    
    /**
     * Initialize ExoPlayer for video playback
     */
    private void initializePlayer() {
        // Create a player instance
        player = new ExoPlayer.Builder(this).build();
        
        // Attach player to the view
        videoPreview.setPlayer(player);
        
        // Configure player
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        
        // Add a listener to update UI
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    progressBar.setVisibility(View.GONE);
                    // Update send button when video is ready
                    updateSendButtonState(true);
                } else if (state == Player.STATE_BUFFERING) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
            
            @Override
            public void onIsPlayingChanged(boolean isCurrentlyPlaying) {
                isPlaying = isCurrentlyPlaying;
            }
        });
        
        // Create a MediaItem from the URI
        MediaItem mediaItem = MediaItem.fromUri(mediaUri);
        
        // Set the media item to play
        player.setMediaItem(mediaItem);
        
        // Prepare the player
        player.prepare();
        
        // Don't start playing automatically, wait for user to press play
        player.setPlayWhenReady(false);
    }
    
    /**
     * Update the send button state based on media readiness
     */
    private void updateSendButtonState(boolean enabled) {
        if (sendButton != null) {
            sendButton.setEnabled(enabled);
        }
    }
    
    /**
     * Toggle video playback (play/pause)
     */
    private void togglePlayback() {
        if (isUsingFallback) {
            // Handle fallback VideoView
            if (standardVideoView != null) {
                if (standardVideoView.isPlaying()) {
                    standardVideoView.pause();
                    playPauseButton.setVisibility(View.VISIBLE);
                    playPauseButton.setImageResource(R.drawable.ic_play);
                } else {
                    standardVideoView.start();
                    playPauseButton.setVisibility(View.VISIBLE);
                    playPauseButton.setImageResource(R.drawable.ic_pause);
                }
            }
        } else if (player != null) {
            // Handle ExoPlayer
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
        }
    }
    
    /**
     * Fallback to standard Android VideoView if ExoPlayer fails
     */
    private void useStandardVideoViewFallback() {
        try {
            // Hide our ExoPlayer view
            videoPreview.setVisibility(View.GONE);
            
            // Show play/pause button
            if (playPauseButton != null) {
                playPauseButton.setVisibility(View.VISIBLE);
                playPauseButton.setImageResource(R.drawable.ic_play);
            }
            
            // Hide progress temporarily
            progressBar.setVisibility(View.GONE);
            
            // Create a VideoView dynamically
            standardVideoView = new VideoView(this);
            standardVideoView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            
            // Add to the container
            FrameLayout container = findViewById(R.id.mediaPreviewContainer);
            container.addView(standardVideoView);
            
            // Set up the video view
            standardVideoView.setVideoURI(mediaUri);
            MediaController mediaController = new MediaController(this);
            mediaController.setAnchorView(standardVideoView);
            standardVideoView.setMediaController(mediaController);
            
            // Show progress during preparation
            progressBar.setVisibility(View.VISIBLE);
            
            // Start when ready
            standardVideoView.setOnPreparedListener(mp -> {
                progressBar.setVisibility(View.GONE);
                mp.setLooping(true);
                // Don't start automatically
                playPauseButton.setVisibility(View.VISIBLE);
            });
            
            // Show error message if playback fails
            standardVideoView.setOnErrorListener((mp, what, extra) -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show();
                return true;
            });
            
            standardVideoView.requestFocus();
            
            // Save state for cleanup
            isUsingFallback = true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up fallback video view", e);
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Unable to preview video", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    /**
     * Set up document preview
     */
    private void setupDocumentPreview() {
        imagePreview.setVisibility(View.GONE);
        videoPreview.setVisibility(View.GONE);
        documentInfo.setVisibility(View.VISIBLE);
        if (playPauseButton != null) {
            playPauseButton.setVisibility(View.GONE);
        }
        
        try {
            // Show document information
            String fileName = mediaUri.getLastPathSegment();
            long fileSize = MediaUtils.getFileSize(this, mediaUri);
            String formattedSize = formatFileSize(fileSize);
            
            documentInfo.setText(String.format("%s (%s)", fileName, formattedSize));
        } catch (Exception e) {
            Log.e(TAG, "Error showing document info", e);
            Toast.makeText(this, "Error showing document info", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    /**
     * Format file size in human-readable format
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroup = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroup), units[digitGroup]);
    }
    
    /**
     * Send the media to the server and then send a message with the media URL
     */
    private void sendMedia() {
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        sendButton.setEnabled(false);
        
        // Get the caption if any
        String caption = captionInput.getText().toString().trim();
        
        // Safety check for media URI
        if (mediaUri == null) {
            Toast.makeText(this, "Error: Invalid media", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            sendButton.setEnabled(true);
            return;
        }
        
        // Get MIME type with fallback to default
        String mimeType = null;
        try {
            mimeType = MimeTypeUtils.getMimeType(this, mediaUri);
        } catch (Exception e) {
            Log.e(TAG, "Error determining mime type", e);
        }
        
        if (mimeType == null) {
            // Fallback based on media type
            if (MEDIA_TYPE_IMAGE.equals(mediaType)) {
                mimeType = "image/jpeg";
            } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
                mimeType = "video/mp4";
            } else {
                mimeType = "application/octet-stream";
            }
        }
        
        // Upload to server
        final String finalMimeType = mimeType; // For use in lambda
        apiClient.uploadMedia(mediaUri, finalMimeType, new ApiClient.ApiCallback<ApiClient.MediaUploadResponse>() {
            @Override
            public void onSuccess(ApiClient.MediaUploadResponse response) {
                // Media uploaded successfully
                String mediaUrl = response.data.url;
                String fileName = response.data.filename;
                
                // Create a message with the media URL
                ChatMessageItem.MessageType messageType;
                if (MEDIA_TYPE_IMAGE.equals(mediaType)) {
                    messageType = ChatMessageItem.MessageType.IMAGE;
                } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
                    messageType = ChatMessageItem.MessageType.VIDEO;
                } else {
                    messageType = ChatMessageItem.MessageType.DOCUMENT;
                }
                
                // Create a JSON content with media information
                JSONObject contentJson = new JSONObject();
                try {
                    contentJson.put("url", mediaUrl);
                    contentJson.put("type", messageType.toString());
                    contentJson.put("filename", fileName);
                    if (!caption.isEmpty()) {
                        contentJson.put("caption", caption);
                    }
                    
                    // Log the created JSON for debugging
                    String jsonContent = contentJson.toString();
                    Log.d(TAG, "Created media message JSON: " + jsonContent);
                    
                    // Get ChatViewModel to send the message
                    ChatViewModel viewModel = new ChatViewModel(getApplication());
                    viewModel.setCurrentRecipient(chatId);
                    viewModel.sendMessage(jsonContent, messageType);
                    
                    // Finish activity
                    runOnUiThread(() -> {
                        Toast.makeText(MediaProcessingActivity.this, "Media sent", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error creating media message JSON", e);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        sendButton.setEnabled(true);
                        Toast.makeText(MediaProcessingActivity.this, 
                                "Failed to send media: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
            
            @Override
            public void onFailure(String errorMessage) {
                // Error uploading media
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    sendButton.setEnabled(true);
                    Toast.makeText(MediaProcessingActivity.this, 
                            "Failed to upload media: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isUsingFallback && standardVideoView != null) {
            standardVideoView.pause();
        } else if (player != null) {
            player.pause();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            // Do not auto-resume playback
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        if (isUsingFallback && standardVideoView != null) {
            standardVideoView.stopPlayback();
            standardVideoView = null;
        }
    }
    
    /**
     * Release ExoPlayer resources
     */
    private void releasePlayer() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 