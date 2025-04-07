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

import androidx.appcompat.app.AppCompatActivity;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.utils.MediaUtils;

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
    
    private ImageView imagePreview;
    private VideoView videoPreview;
    private Button sendButton;
    private Button cancelButton;
    private TextView captionInput;
    private ProgressBar progressBar;
    
    private Uri mediaUri;
    private String mediaType;
    private String chatId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_processing);
        
        // Initialize views
        imagePreview = findViewById(R.id.imagePreview);
        videoPreview = findViewById(R.id.videoPreview);
        sendButton = findViewById(R.id.sendButton);
        cancelButton = findViewById(R.id.cancelButton);
        captionInput = findViewById(R.id.captionInput);
        progressBar = findViewById(R.id.progressBar);
        
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
     * Send the media back to the chat activity
     */
    private void sendMedia() {
        // Process media caption
        String caption = captionInput.getText().toString().trim();
        
        // Set result with the processed media
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_MEDIA_URI, mediaUri.toString());
        resultIntent.putExtra(EXTRA_MEDIA_TYPE, mediaType);
        if (!caption.isEmpty()) {
            resultIntent.putExtra("caption", caption);
        }
        
        setResult(RESULT_OK, resultIntent);
        finish();
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
} 