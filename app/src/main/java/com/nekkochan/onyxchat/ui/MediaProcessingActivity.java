package com.nekkochan.onyxchat.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.utils.FileUtils;
import com.nekkochan.onyxchat.utils.MediaUtils;
import com.nekkochan.onyxchat.utils.MimeTypeUtils;

import java.io.File;
import java.io.IOException;

/**
 * Activity for demonstrating media processing capabilities using FFmpeg
 */
public class MediaProcessingActivity extends AppCompatActivity {
    private static final String TAG = "MediaProcessingActivity";
    
    // UI elements
    private Button captureVideoButton, captureImageButton;
    private Button selectVideoButton, selectAudioButton, selectImageButton;
    private Button processButton, sendButton;
    private VideoView videoPreview;
    private ImageView imagePreview;
    private TextView previewPlaceholder;
    
    // Media data
    private Uri currentMediaUri;
    private String mediaType;
    private File tempCameraFile;
    private ProgressDialog progressDialog;
    
    // Activity result launchers
    private ActivityResultLauncher<Intent> pickMediaLauncher;
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<Intent> recordVideoLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Fix status bar overlap
        setStatusBarTransparent();
        
        setContentView(R.layout.activity_media_processing);
        
        // Initialize views and callbacks
        initViews();
        setupActivityResultLaunchers();
        setupClickListeners();
    }
    
    /**
     * Set up transparent status bar
     */
    private void setStatusBarTransparent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.black));
            getWindow().setDecorFitsSystemWindows(true);
        } else {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.black));
        }
    }
    
    /**
     * Initialize UI elements
     */
    private void initViews() {
        captureVideoButton = findViewById(R.id.capture_video_button);
        captureImageButton = findViewById(R.id.capture_image_button);
        selectVideoButton = findViewById(R.id.select_video_button);
        selectAudioButton = findViewById(R.id.select_audio_button);
        selectImageButton = findViewById(R.id.select_image_button);
        processButton = findViewById(R.id.process_button);
        sendButton = findViewById(R.id.send_button);
        videoPreview = findViewById(R.id.video_preview);
        imagePreview = findViewById(R.id.image_preview);
        previewPlaceholder = findViewById(R.id.preview_placeholder);
        
        // Initially disable process button
        processButton.setEnabled(false);
    }
    
    /**
     * Set up Activity Result API launchers for media selection
     */
    private void setupActivityResultLaunchers() {
        // For picking media files
        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri mediaUri = result.getData().getData();
                        if (mediaUri != null) {
                            currentMediaUri = mediaUri;
                            showMediaPreview();
                            processButton.setEnabled(true);
                        }
                    }
                });
        
        // For capturing images
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (tempCameraFile != null && tempCameraFile.exists()) {
                            currentMediaUri = FileProvider.getUriForFile(
                                    this,
                                    getApplicationContext().getPackageName() + ".provider",
                                    tempCameraFile);
                            mediaType = "image";
                            showMediaPreview();
                            processButton.setEnabled(true);
                        }
                    }
                });
        
        // For recording videos
        recordVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (tempCameraFile != null && tempCameraFile.exists()) {
                            currentMediaUri = FileProvider.getUriForFile(
                                    this,
                                    getApplicationContext().getPackageName() + ".provider",
                                    tempCameraFile);
                            mediaType = "video";
                            showMediaPreview();
                            processButton.setEnabled(true);
                        }
                    }
                });
    }
    
    /**
     * Set up click listeners for all UI buttons
     */
    private void setupClickListeners() {
        // Media selection buttons
        selectVideoButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            mediaType = "video";
            pickMediaLauncher.launch(intent);
        });
        
        selectAudioButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            mediaType = "audio";
            pickMediaLauncher.launch(intent);
        });
        
        selectImageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            mediaType = "image";
            pickMediaLauncher.launch(intent);
        });
        
        // Camera capture buttons
        captureImageButton.setOnClickListener(v -> {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                try {
                    // Create temp file for the image
                    File storageDir = getExternalFilesDir("camera");
                    tempCameraFile = File.createTempFile(
                            "IMG_" + System.currentTimeMillis(),
                            ".jpg",
                            storageDir
                    );
                    
                    Uri photoURI = FileProvider.getUriForFile(
                            this,
                            getApplicationContext().getPackageName() + ".provider",
                            tempCameraFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    takePictureLauncher.launch(intent);
                } catch (IOException e) {
                    Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error creating temp file", e);
                }
            } else {
                Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
            }
        });
        
        captureVideoButton.setOnClickListener(v -> {
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                try {
                    // Create temp file for the video
                    File storageDir = getExternalFilesDir("camera");
                    tempCameraFile = File.createTempFile(
                            "VID_" + System.currentTimeMillis(),
                            ".mp4",
                            storageDir
                    );
                    
                    Uri videoURI = FileProvider.getUriForFile(
                            this,
                            getApplicationContext().getPackageName() + ".provider",
                            tempCameraFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, videoURI);
                    recordVideoLauncher.launch(intent);
                } catch (IOException e) {
                    Toast.makeText(this, "Error creating video file", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error creating temp file", e);
                }
            } else {
                Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Processing button
        processButton.setOnClickListener(v -> processMedia());
        
        // Send button
        sendButton.setOnClickListener(v -> {
            if (currentMediaUri != null) {
                // Return the processed media URI to the calling activity
                Intent resultIntent = new Intent();
                resultIntent.setData(currentMediaUri);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }
    
    /**
     * Show media preview based on type
     */
    private void showMediaPreview() {
        if (currentMediaUri == null) {
            previewPlaceholder.setVisibility(View.VISIBLE);
            videoPreview.setVisibility(View.GONE);
            imagePreview.setVisibility(View.GONE);
            return;
        }

        previewPlaceholder.setVisibility(View.GONE);
        
        // Set up the preview based on media type
        try {
            if ("video".equals(mediaType)) {
                // Show video preview
                imagePreview.setVisibility(View.GONE);
                videoPreview.setVisibility(View.VISIBLE);
                videoPreview.setVideoURI(currentMediaUri);
                videoPreview.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    videoPreview.start();
                });
            } else if ("audio".equals(mediaType)) {
                // For audio, show a default image
                videoPreview.setVisibility(View.GONE);
                imagePreview.setVisibility(View.VISIBLE);
                imagePreview.setImageResource(R.drawable.ic_audio_file);
            } else if ("image".equals(mediaType)) {
                // Show image preview
                videoPreview.setVisibility(View.GONE);
                imagePreview.setVisibility(View.VISIBLE);
                imagePreview.setImageURI(currentMediaUri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying preview", e);
            Toast.makeText(this, "Error displaying preview: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            previewPlaceholder.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Process the selected media
     */
    private void processMedia() {
        if (currentMediaUri == null) {
            Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Processing Media");
        progressDialog.setMessage("Please wait while we optimize your media...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Process based on media type
        try {
            if ("video".equals(mediaType)) {
                processVideo(currentMediaUri);
            } else if ("audio".equals(mediaType)) {
                processAudio(currentMediaUri);
            } else if ("image".equals(mediaType)) {
                processImage(currentMediaUri);
            } else {
                handleProcessingFailure("Unknown media type");
            }
        } catch (Exception e) {
            Log.e(TAG, "Processing error", e);
            handleProcessingFailure("Error: " + e.getMessage());
        }
    }
    
    /**
     * Process video using FFmpeg
     */
    private void processVideo(Uri videoUri) {
        try {
            File outputDir = getExternalFilesDir("processed");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            File outputFile = new File(outputDir, "compressed_" + System.currentTimeMillis() + ".mp4");
            String inputPath = FileUtils.getPath(this, videoUri);
            String outputPath = outputFile.getAbsolutePath();
            
            // Build FFmpeg command for video compression
            String[] command = {
                "-i", inputPath,
                "-c:v", "libx264",
                "-crf", "28",
                "-preset", "fast",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                outputPath
            };
            
            executeFFmpegCommand(command, outputFile);
        } catch (Exception e) {
            Log.e(TAG, "Error processing video", e);
            handleProcessingFailure("Video processing error: " + e.getMessage());
        }
    }
    
    /**
     * Process audio using FFmpeg
     */
    private void processAudio(Uri audioUri) {
        try {
            File outputDir = getExternalFilesDir("processed");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            File outputFile = new File(outputDir, "converted_" + System.currentTimeMillis() + ".mp3");
            String inputPath = FileUtils.getPath(this, audioUri);
            String outputPath = outputFile.getAbsolutePath();
            
            // Build FFmpeg command for audio conversion
            String[] command = {
                "-i", inputPath,
                "-c:a", "libmp3lame",
                "-b:a", "192k",
                outputPath
            };
            
            executeFFmpegCommand(command, outputFile);
        } catch (Exception e) {
            Log.e(TAG, "Error processing audio", e);
            handleProcessingFailure("Audio processing error: " + e.getMessage());
        }
    }
    
    /**
     * Process image using FFmpeg
     */
    private void processImage(Uri imageUri) {
        try {
            File outputDir = getExternalFilesDir("processed");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            File outputFile = new File(outputDir, "optimized_" + System.currentTimeMillis() + ".jpg");
            String inputPath = FileUtils.getPath(this, imageUri);
            String outputPath = outputFile.getAbsolutePath();
            
            // Build FFmpeg command for image optimization
            String[] command = {
                "-i", inputPath,
                "-q:v", "2",  // Quality level (1-31, 1 is best)
                outputPath
            };
            
            executeFFmpegCommand(command, outputFile);
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            handleProcessingFailure("Image processing error: " + e.getMessage());
        }
    }
    
    /**
     * Execute FFmpeg command and handle result
     */
    private void executeFFmpegCommand(String[] command, File outputFile) {
        try {
            // Join the command array into a single string
            StringBuilder sb = new StringBuilder();
            for (String part : command) {
                sb.append(part).append(" ");
            }
            String commandString = sb.toString().trim();
            
            // Execute FFmpeg command asynchronously
            FFmpegKit.executeAsync(commandString, session -> {
                ReturnCode returnCode = session.getReturnCode();
                
                if (returnCode.isValueSuccess()) {
                    // Success
                    Uri outputUri = Uri.fromFile(outputFile);
                    runOnUiThread(() -> handleProcessingSuccess(outputUri));
                } else {
                    // Failure
                    String errorMessage = session.getFailStackTrace();
                    runOnUiThread(() -> handleProcessingFailure("FFmpeg error: " + errorMessage));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error executing FFmpeg command", e);
            handleProcessingFailure("Error: " + e.getMessage());
        }
    }
    
    /**
     * Handle successful media processing
     */
    private void handleProcessingSuccess(Uri outputUri) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        
        currentMediaUri = outputUri;
        showMediaPreview();
        
        // Show the send button
        sendButton.setVisibility(View.VISIBLE);
        
        Toast.makeText(this, "Media processed successfully", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Handle failed media processing
     */
    private void handleProcessingFailure(String errorMessage) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Processing failure: " + errorMessage);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
} 