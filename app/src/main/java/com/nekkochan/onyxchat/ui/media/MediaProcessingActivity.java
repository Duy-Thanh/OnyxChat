package com.nekkochan.onyxchat.ui.media;

import android.net.Uri;
import android.os.Bundle;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ApiClient;
import com.nekkochan.onyxchat.utils.MediaUtils;
import com.nekkochan.onyxchat.utils.MimeTypeUtils;
import com.nekkochan.onyxchat.ui.chat.ChatMessageItem;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

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
    private VideoView videoPreview;
    private Button sendButton;
    private Button cancelButton;
    private TextView captionInput;
    private ProgressBar progressBar;
    private TextView documentInfo;
    
    private Uri mediaUri;
    private String mediaType;
    private String chatId;
    private ApiClient apiClient;

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
        
        try {
            imagePreview.setImageURI(mediaUri);
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    /**
     * Set up video preview
     */
    private void setupVideoPreview() {
        imagePreview.setVisibility(View.GONE);
        videoPreview.setVisibility(View.VISIBLE);
        documentInfo.setVisibility(View.GONE);
        
        try {
            videoPreview.setVideoURI(mediaUri);
            
            // Set up media controller for video
            MediaController mediaController = new MediaController(this);
            mediaController.setAnchorView(videoPreview);
            videoPreview.setMediaController(mediaController);
            
            // Start video playback when ready
            videoPreview.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                videoPreview.start();
            });
            
            videoPreview.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Video playback error: " + what);
                Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show();
                return true;
            });
            
            videoPreview.requestFocus();
        } catch (Exception e) {
            Log.e(TAG, "Error loading video", e);
            Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show();
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
        
        try {
            // Show document information
            String fileName = mediaUri.getLastPathSegment();
            long fileSize = MediaUtils.getFileSize(this, mediaUri);
            String formattedSize = formatFileSize(fileSize);
            
            documentInfo.setText(String.format("%s (%s)", fileName, formattedSize));
        } catch (Exception e) {
            Log.e(TAG, "Error loading document info", e);
            Toast.makeText(this, "Error loading document", Toast.LENGTH_SHORT).show();
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
        
        // Get MIME type
        String mimeType = MimeTypeUtils.getMimeType(this, mediaUri);
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        
        // Upload to server
        apiClient.uploadMedia(mediaUri, mimeType, new ApiClient.ApiCallback<ApiClient.MediaUploadResponse>() {
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
                String contentJson = String.format(
                    "{\"url\":\"%s\",\"filename\":\"%s\",\"caption\":\"%s\",\"type\":\"%s\"}",
                    mediaUrl, fileName, caption, messageType.toString()
                );
                
                // Get ChatViewModel to send the message
                ChatViewModel viewModel = new ChatViewModel(getApplication());
                viewModel.setCurrentRecipient(chatId);
                viewModel.sendMessage(contentJson, messageType);
                
                // Finish activity
                runOnUiThread(() -> {
                    Toast.makeText(MediaProcessingActivity.this, "Media sent", Toast.LENGTH_SHORT).show();
                    finish();
                });
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
        if (videoPreview.isPlaying()) {
            videoPreview.pause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoPreview != null) {
            videoPreview.stopPlayback();
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 