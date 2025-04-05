package com.nekkochan.onyxchat.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.utils.MediaUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for demonstrating media processing capabilities using FFmpeg
 */
public class MediaProcessingActivity extends AppCompatActivity {
    private static final String TAG = "MediaProcessingActivity";
    
    private static final int REQUEST_VIDEO_CAPTURE = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 101;
    private static final int REQUEST_VIDEO_PICK = 102;
    private static final int REQUEST_AUDIO_PICK = 103;
    private static final int REQUEST_IMAGE_PICK = 104;
    
    private ImageView imagePreview;
    private VideoView videoPreview;
    private Button processButton;
    
    private Uri currentMediaUri;
    private String mediaType;
    private ProgressDialog progressDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_processing);
        
        imagePreview = findViewById(R.id.image_preview);
        videoPreview = findViewById(R.id.video_preview);
        processButton = findViewById(R.id.process_button);
        
        Button captureVideoButton = findViewById(R.id.capture_video_button);
        Button captureImageButton = findViewById(R.id.capture_image_button);
        Button selectVideoButton = findViewById(R.id.select_video_button);
        Button selectAudioButton = findViewById(R.id.select_audio_button);
        Button selectImageButton = findViewById(R.id.select_image_button);
        
        captureVideoButton.setOnClickListener(v -> captureVideo());
        captureImageButton.setOnClickListener(v -> captureImage());
        selectVideoButton.setOnClickListener(v -> selectVideo());
        selectAudioButton.setOnClickListener(v -> selectAudio());
        selectImageButton.setOnClickListener(v -> selectImage());
        
        processButton.setOnClickListener(v -> processMedia());
        processButton.setEnabled(false);
    }
    
    private void captureVideo() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void captureImage() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VIDEO_PICK);
    }
    
    private void selectAudio() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_AUDIO_PICK);
    }
    
    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            switch (requestCode) {
                case REQUEST_VIDEO_CAPTURE:
                case REQUEST_VIDEO_PICK:
                    handleVideoResult(data.getData());
                    break;
                case REQUEST_IMAGE_CAPTURE:
                case REQUEST_IMAGE_PICK:
                    handleImageResult(data.getData());
                    break;
                case REQUEST_AUDIO_PICK:
                    handleAudioResult(data.getData());
                    break;
            }
        }
    }
    
    private void handleVideoResult(Uri videoUri) {
        if (videoUri != null) {
            currentMediaUri = videoUri;
            mediaType = "video";
            
            videoPreview.setVisibility(View.VISIBLE);
            imagePreview.setVisibility(View.GONE);
            
            videoPreview.setVideoURI(videoUri);
            videoPreview.start();
            
            processButton.setEnabled(true);
            processButton.setText("Compress Video");
        }
    }
    
    private void handleImageResult(Uri imageUri) {
        if (imageUri != null) {
            currentMediaUri = imageUri;
            mediaType = "image";
            
            imagePreview.setVisibility(View.VISIBLE);
            videoPreview.setVisibility(View.GONE);
            
            imagePreview.setImageURI(imageUri);
            
            processButton.setEnabled(true);
            processButton.setText("Compress Image");
        }
    }
    
    private void handleAudioResult(Uri audioUri) {
        if (audioUri != null) {
            currentMediaUri = audioUri;
            mediaType = "audio";
            
            imagePreview.setVisibility(View.GONE);
            videoPreview.setVisibility(View.GONE);
            
            Toast.makeText(this, "Audio file selected", Toast.LENGTH_SHORT).show();
            
            processButton.setEnabled(true);
            processButton.setText("Convert Audio");
        }
    }
    
    private void processMedia() {
        if (currentMediaUri == null || mediaType == null) {
            Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showProgressDialog("Processing...");
        
        switch (mediaType) {
            case "video":
                processVideo();
                break;
            case "image":
                processImage();
                break;
            case "audio":
                processAudio();
                break;
        }
    }
    
    private void processVideo() {
        MediaUtils.compressVideo(this, currentMediaUri, new MediaUtils.MediaProcessCallback() {
            @Override
            public void onSuccess(Uri outputUri) {
                hideProgressDialog();
                showResult("Video compressed successfully", outputUri);
                
                // Create a thumbnail
                createVideoThumbnail(outputUri);
            }
            
            @Override
            public void onError(String errorMessage) {
                hideProgressDialog();
                showError(errorMessage);
            }
        });
    }
    
    private void createVideoThumbnail(Uri videoUri) {
        showProgressDialog("Creating thumbnail...");
        
        MediaUtils.createVideoThumbnail(this, videoUri, new MediaUtils.MediaProcessCallback() {
            @Override
            public void onSuccess(Uri outputUri) {
                hideProgressDialog();
                
                runOnUiThread(() -> {
                    imagePreview.setVisibility(View.VISIBLE);
                    videoPreview.setVisibility(View.GONE);
                    imagePreview.setImageURI(outputUri);
                    
                    Toast.makeText(MediaProcessingActivity.this, 
                            "Thumbnail created", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                hideProgressDialog();
                showError("Thumbnail error: " + errorMessage);
            }
        });
    }
    
    private void processImage() {
        MediaUtils.compressImage(this, currentMediaUri, new MediaUtils.MediaProcessCallback() {
            @Override
            public void onSuccess(Uri outputUri) {
                hideProgressDialog();
                showResult("Image compressed successfully", outputUri);
                
                runOnUiThread(() -> {
                    imagePreview.setImageURI(outputUri);
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                hideProgressDialog();
                showError(errorMessage);
            }
        });
    }
    
    private void processAudio() {
        MediaUtils.convertAudio(this, currentMediaUri, new MediaUtils.MediaProcessCallback() {
            @Override
            public void onSuccess(Uri outputUri) {
                hideProgressDialog();
                showResult("Audio converted successfully", outputUri);
            }
            
            @Override
            public void onError(String errorMessage) {
                hideProgressDialog();
                showError(errorMessage);
            }
        });
    }
    
    private void showProgressDialog(String message) {
        runOnUiThread(() -> {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(message);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.show();
        });
    }
    
    private void hideProgressDialog() {
        runOnUiThread(() -> {
            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }
    
    private void showResult(String message, Uri fileUri) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            
            // Show file info
            Log.d(TAG, "Processed file: " + fileUri.toString());
            String mediaInfo = MediaUtils.getMediaInfo(this, fileUri);
            Log.d(TAG, "Media info: " + mediaInfo);
            
            // Enable sending this processed file
            Button sendButton = findViewById(R.id.send_button);
            sendButton.setVisibility(View.VISIBLE);
            sendButton.setOnClickListener(v -> {
                // Simulate sending the processed file to a chat
                Toast.makeText(this, "Media ready to send in chat", Toast.LENGTH_SHORT).show();
            });
        });
    }
    
    private void showError(String errorMessage) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Processing error: " + errorMessage);
        });
    }
} 