package com.nekkochan.onyxchat.ui.media;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Log;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Util;
import com.nekkochan.onyxchat.R;

/**
 * Activity for viewing media content (images and videos)
 */
public class MediaViewerActivity extends AppCompatActivity {

    private static final String TAG = "MediaViewerActivity";

    private PlayerView playerView;
    private ImageView imageView;
    private ProgressBar progressBar;
    private ExoPlayer player;
    private String mediaUrl;
    private String mediaType;
    private boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);

        // Get media information from intent
        mediaUrl = getIntent().getStringExtra("mediaUrl");
        mediaType = getIntent().getStringExtra("mediaType");

        // Initialize views
        playerView = findViewById(R.id.player_view);
        imageView = findViewById(R.id.image_view);
        progressBar = findViewById(R.id.progress_bar);

        // Set up based on media type
        if (mediaType != null && mediaType.equalsIgnoreCase("VIDEO")) {
            setupVideoPlayer();
        } else {
            setupImageViewer();
        }

        // Set up close button
        findViewById(R.id.close_button).setOnClickListener(v -> finish());
    }

    private void setupVideoPlayer() {
        playerView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        
        // Configure player view
        playerView.setControllerShowTimeoutMs(3000);
        playerView.setControllerHideOnTouch(true);
    }

    private void setupImageViewer() {
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);

        // Load image with Glide using proper URL handling
        progressBar.setVisibility(View.VISIBLE);
        
        // Get proper URI for the media URL
        Uri mediaUri = getProperMediaUri(mediaUrl);
        Log.d(TAG, "Loading image with URI: " + mediaUri);
        
        Glide.with(this)
                .load(mediaUri.toString())
                .error(R.drawable.ic_error)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed to load image: " + (e != null ? e.getMessage() : "unknown error"), e);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MediaViewerActivity.this, "Error loading image", Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Image loaded successfully");
                        progressBar.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(imageView);
    }

    private void initializePlayer() {
        if (player == null && mediaType != null && mediaType.equalsIgnoreCase("VIDEO")) {
            player = new SimpleExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            
            // Create media item with proper URL handling
            Uri mediaUri = getProperMediaUri(mediaUrl);
            Log.d(TAG, "Loading video with URI: " + mediaUri);
            
            MediaItem mediaItem = MediaItem.fromUri(mediaUri);
            player.setMediaItem(mediaItem);
            
            // Set playback parameters
            player.setPlayWhenReady(playWhenReady);
            player.seekTo(currentWindow, playbackPosition);
            
            // Add player listener
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_BUFFERING) {
                        progressBar.setVisibility(View.VISIBLE);
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                    
                    if (state == Player.STATE_ENDED) {
                        // Loop video
                        player.seekTo(0);
                        player.play();
                    }
                }
                
                @Override
                public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                    Log.e(TAG, "Player error: " + error.getMessage(), error);
                    Toast.makeText(MediaViewerActivity.this, 
                            "Error playing video: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                }
            });
            
            // Prepare player
            player.prepare();
        }
    }

    /**
     * Convert a potentially relative server path to a proper HTTP URL
     */
    private Uri getProperMediaUri(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isEmpty()) {
            return Uri.EMPTY;
        }
        
        // If already a full URL, use as is
        if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) {
            return Uri.parse(mediaUrl);
        }
        
        // If starts with / but not with //, assume it's a server path
        if (mediaUrl.startsWith("/") && !mediaUrl.startsWith("//")) {
            // Get the server URL from API client
            String serverUrl = getBaseServerUrl();
            
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
            return Uri.parse(fullUrl);
        }
        
        // For file paths or other URIs, use as is
        return Uri.parse(mediaUrl);
    }
    
    /**
     * Get the base server URL from API client
     */
    private String getBaseServerUrl() {
        // Default server URL (used in API client)
        String defaultUrl = "https://10.0.2.2:443";
        
        try {
            // Try to get the actual server URL from shared preferences
            SharedPreferences sharedPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE);
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

    private void releasePlayer() {
        if (player != null) {
            playWhenReady = player.getPlayWhenReady();
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            player.release();
            player = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Util.SDK_INT >= 24) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Util.SDK_INT < 24 || player == null) {
            initializePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Util.SDK_INT < 24) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Util.SDK_INT >= 24) {
            releasePlayer();
        }
    }
} 