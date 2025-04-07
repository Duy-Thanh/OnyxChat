package com.nekkochan.onyxchat.ui.media;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
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

        // Load image with Glide
        progressBar.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(mediaUrl)
                .error(R.drawable.ic_error)
                .into(imageView);
        progressBar.setVisibility(View.GONE);
    }

    private void initializePlayer() {
        if (player == null && mediaType != null && mediaType.equalsIgnoreCase("VIDEO")) {
            player = new SimpleExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            
            // Create media item
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(mediaUrl));
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