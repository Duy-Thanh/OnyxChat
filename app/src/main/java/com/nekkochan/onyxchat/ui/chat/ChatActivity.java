package com.nekkochan.onyxchat.ui.chat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.media.MediaProcessingActivity;
import com.nekkochan.onyxchat.ui.adapters.ChatMessageAdapter;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;
import com.nekkochan.onyxchat.utils.EmojiUtils;
import com.nekkochan.onyxchat.utils.FileUtils;
import com.nekkochan.onyxchat.utils.MimeTypeUtils;
import com.vanniktech.emoji.EmojiPopup;

import java.util.concurrent.CompletableFuture;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatActivity extends AppCompatActivity {
    
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    private static final int MEDIA_MAX_SIZE_MB = 15;
    private static final int PERMISSION_REQUEST_MEDIA = 100;
    
    private ChatViewModel viewModel;
    private RecyclerView recyclerView;
    private EditText messageInput;
    private FloatingActionButton sendButton;
    private Chip statusChip;
    private ChatMessageAdapter adapter;
    private String contactId;
    private String contactName;
    private ImageButton backButton;
    private TextView contactNameText;
    private CircleImageView contactAvatar;
    private FloatingActionButton scrollDownButton;
    private ImageButton attachButton;
    private ImageButton emojiButton;
    private ImageButton voiceCallButton;
    private ImageButton videoCallButton;
    private ImageButton chatSettingsButton;
    private EmojiPopup emojiPopup;
    private Button emojiDoneButton;
    private Uri selectedFileUri;
    
    // Activity result launcher for picking images
    private final ActivityResultLauncher<Intent> pickImageLauncher = 
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            selectedFileUri = result.getData().getData();
            if (selectedFileUri != null) {
                handleSelectedFile(selectedFileUri);
            }
        }
    });
    
    // Activity result launcher for picking videos
    private final ActivityResultLauncher<Intent> pickVideoLauncher = 
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            selectedFileUri = result.getData().getData();
            if (selectedFileUri != null) {
                handleSelectedFile(selectedFileUri);
            }
        }
    });
    
    // Activity result launcher for picking documents
    private final ActivityResultLauncher<Intent> pickDocumentLauncher = 
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            selectedFileUri = result.getData().getData();
            if (selectedFileUri != null) {
                handleSelectedFile(selectedFileUri);
            }
        }
    });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make status bar transparent and draw behind it
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        
        setContentView(R.layout.activity_chat);
        
        // Get contact ID and name from intent
        contactId = getIntent().getStringExtra(EXTRA_CONTACT_ID);
        contactName = getIntent().getStringExtra(EXTRA_CONTACT_NAME);
        
        if (contactId == null) {
            Log.e(TAG, "No contact ID provided");
            finish();
            return;
        }
        
        // Check if contactId is an email address (contains @)
        boolean isEmail = contactId.contains("@");
        Log.d(TAG, "Opening chat with " + (isEmail ? "email: " : "userId: ") + contactId);
        
        // Get view model
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.setCurrentRecipient(contactId);
        
        // Initialize views
        recyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        statusChip = findViewById(R.id.chatStatusChip);
        backButton = findViewById(R.id.backButton);
        contactNameText = findViewById(R.id.contactNameText);
        contactAvatar = findViewById(R.id.contactAvatar);
        scrollDownButton = findViewById(R.id.scrollDownButton);
        attachButton = findViewById(R.id.attachButton);
        emojiButton = findViewById(R.id.emojiButton);
        voiceCallButton = findViewById(R.id.voiceCallButton);
        videoCallButton = findViewById(R.id.videoCallButton);
        chatSettingsButton = findViewById(R.id.chatSettingsButton);
        emojiDoneButton = findViewById(R.id.emojiDoneButton);
        
        // Add logging to track timestamps of messages
        // Set up contact info in header format similar to Discover Users screen
        contactNameText.setText(contactName != null ? contactName : contactId);
        
        // Find the statusChip's parent LinearLayout
        ViewParent statusChipParent = statusChip.getParent();
        if (statusChipParent instanceof LinearLayout) {
            // Get the username from the contact ID (email)
            if (isEmail) {
                String username = contactId.split("@")[0];
                // Create a new TextView for the username
                TextView usernameText = new TextView(this);
                usernameText.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                usernameText.setText("@" + username);
                usernameText.setTextColor(getResources().getColor(R.color.white, null));
                usernameText.setTextSize(12);
                
                // Replace the statusChip with the username text
                LinearLayout parent = (LinearLayout) statusChipParent;
                parent.removeView(statusChip);
                parent.addView(usernameText);
                
                // Add the status chip back after the username
                parent.addView(statusChip);
            }
        }
        
        // Fetch messages for this contact
        viewModel.setCurrentRecipient(contactId);
        
        // Set up back button
        backButton.setOnClickListener(v -> finish());
        
        // Set up RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        
        // Create and set adapter with the required userId parameter
        adapter = new ChatMessageAdapter(viewModel.getUserId());
        recyclerView.setAdapter(adapter);
        
        // Set up scroll button
        scrollDownButton.setOnClickListener(v -> {
            if (adapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                scrollDownButton.setVisibility(View.GONE);
            }
        });
        
        // Prevent automatic keyboard display and focus issues
        messageInput.clearFocus();
        
        // Prevent keyboard from showing automatically when emoji is displayed
        messageInput.setShowSoftInputOnFocus(false);
        
        // Add custom control for keyboard show/hide based on emoji popup state
        messageInput.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d(TAG, "Message input focus changed: " + hasFocus);
            if (hasFocus) {
                if (EmojiUtils.isEmojiPopupShowing()) {
                    // If emoji popup is showing, prevent keyboard
                    Log.d(TAG, "Preventing keyboard from showing during emoji popup");
                    messageInput.setShowSoftInputOnFocus(false);
                    ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(messageInput.getWindowToken(), 0);
                } else {
                    // If emoji popup is not showing, allow keyboard
                    Log.d(TAG, "Allowing keyboard to show (no emoji popup)");
                    messageInput.setShowSoftInputOnFocus(true);
                    ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(messageInput, 0);
                }
            }
        });
        
        // Custom click listener for message input to manage keyboard/emoji state
        messageInput.setOnClickListener(v -> {
            if (EmojiUtils.isEmojiPopupShowing()) {
                // If emoji popup is showing, don't show keyboard
                Log.d(TAG, "Message input clicked while emoji showing - suppressing keyboard");
                messageInput.setShowSoftInputOnFocus(false);
            } else {
                // Normal behavior - show keyboard
                Log.d(TAG, "Message input clicked - showing keyboard");
                messageInput.setShowSoftInputOnFocus(true);
            }
        });
        
        // Set up emoji support 
        emojiButton.setOnClickListener(null);
        Log.d(TAG, "Setting up emoji popup");
        
        // Wait for layout to be completely ready
        findViewById(android.R.id.content).postDelayed(() -> {
            // Set up emoji popup after layout is ready to ensure it works right away
            emojiPopup = EmojiUtils.setupEmojiPopup(this, findViewById(android.R.id.content), messageInput, (ImageView) emojiButton);
            Log.d(TAG, "Emoji popup setup complete");
            
            // Set up tap outside to dismiss
            View rootView = findViewById(android.R.id.content);
            rootView.setOnTouchListener((v, event) -> {
                if (EmojiUtils.isEmojiPopupShowing()) {
                    // Get tap coordinates
                    float x = event.getX();
                    float y = event.getY();
                    
                    // Check if tap is in the message input area (we don't want to dismiss if tapping there)
                    int[] messageInputLocation = new int[2];
                    messageInput.getLocationOnScreen(messageInputLocation);
                    
                    boolean isTapInMessageInput = 
                        x >= messageInputLocation[0] && 
                        x <= messageInputLocation[0] + messageInput.getWidth() &&
                        y >= messageInputLocation[1] && 
                        y <= messageInputLocation[1] + messageInput.getHeight();
                    
                    // Check if tap is in the emoji button
                    int[] emojiButtonLocation = new int[2];
                    emojiButton.getLocationOnScreen(emojiButtonLocation);
                    
                    boolean isTapOnEmojiButton = 
                        x >= emojiButtonLocation[0] && 
                        x <= emojiButtonLocation[0] + emojiButton.getWidth() &&
                        y >= emojiButtonLocation[1] && 
                        y <= emojiButtonLocation[1] + emojiButton.getHeight();
                    
                    // Dismiss if tap is outside message input and emoji button
                    if (!isTapInMessageInput && !isTapOnEmojiButton) {
                        Log.d(TAG, "Tap outside - dismissing emoji popup");
                        EmojiUtils.dismissEmojiPopup();
                        return true; // Consume the event
                    }
                }
                return false; // Pass through if not handling
            });
            
            // Set up the Done button for dismissing emoji popup
            emojiDoneButton.setOnClickListener(v -> {
                Log.d(TAG, "Done button clicked - dismissing emoji popup");
                EmojiUtils.dismissEmojiPopup();
            });
        }, 300);
        
        // Watch for scrolling to show/hide scroll button
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                        int lastItem = adapter.getItemCount() - 1;
                        
                        // Show button if not at bottom
                        scrollDownButton.setVisibility(lastVisiblePosition < lastItem - 2 ? View.VISIBLE : View.GONE);
                    }
                }
            }
        });
        
        // Set up attachment button
        attachButton.setOnClickListener(v -> showAttachmentOptions());
        
        // Set up voice call button
        voiceCallButton.setOnClickListener(v -> {
            Toast.makeText(this, "Voice call feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Set up video call button
        videoCallButton.setOnClickListener(v -> {
            Toast.makeText(this, "Video call feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Set up chat settings button
        chatSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatSettingsActivity.class);
            intent.putExtra(ChatSettingsActivity.EXTRA_CONTACT_ID, contactId);
            intent.putExtra(ChatSettingsActivity.EXTRA_CONTACT_NAME, contactName);
            startActivity(intent);
        });
        
        // Set up send button
        sendButton.setOnClickListener(v -> sendMessage());
        
        // Monitor text input to enable/disable send button
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                // Only enable send button if we have text AND are connected
                boolean connected = viewModel.isChatConnected().getValue() == Boolean.TRUE;
                sendButton.setEnabled(connected && s.length() > 0);
            }
        });
        
        // Update connection status immediately
        updateConnectionStatus(viewModel.isChatConnected().getValue() == Boolean.TRUE);
        
        // Observe chat connection state
        viewModel.isChatConnected().observe(this, this::updateConnectionStatus);
        
        // Observe chat messages
        viewModel.getChatMessages().observe(this, chatMessages -> {
            if (chatMessages != null && !chatMessages.isEmpty()) {
                adapter.submitList(chatMessages);
                
                // Only auto-scroll if already at bottom
                LinearLayoutManager messageLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (messageLayoutManager != null) {
                    int lastVisiblePosition = messageLayoutManager.findLastVisibleItemPosition();
                    int lastItem = adapter.getItemCount() - 1;
                    
                    if (lastVisiblePosition >= lastItem - 2) {
                        recyclerView.scrollToPosition(chatMessages.size() - 1);
                    } else {
                        scrollDownButton.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        
        // Connect to chat service if not connected
        if (viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            viewModel.connectToChat();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dismiss emoji popup to prevent memory leaks
        EmojiUtils.dismissEmojiPopup();
    }
    
    @Override
    public void onBackPressed() {
        // Handle back press when emoji popup is showing
        if (EmojiUtils.isEmojiPopupShowing()) {
            EmojiUtils.dismissEmojiPopup();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_MEDIA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retry the action
                showAttachmentOptions();
            } else {
                Toast.makeText(this, "Permission denied. Cannot access media files.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Update the connection status UI
     */
    private void updateConnectionStatus(boolean isConnected) {
        if (isConnected) {
            statusChip.setText(R.string.status_connected);
            statusChip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.status_online));
            sendButton.setEnabled(messageInput.getText().length() > 0);
        } else {
            statusChip.setText(R.string.status_disconnected);
            statusChip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.error_red));
            sendButton.setEnabled(false);
        }
    }
    
    /**
     * Send a message to the current recipient
     */
    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        
        if (messageText.isEmpty()) {
            return;
        }
        
        boolean messageSent = viewModel.sendDirectMessage(contactId, messageText);
        
        if (messageSent) {
            messageInput.setText("");
        } else {
            // If connection was lost, try to reconnect and send again
            if (!viewModel.isChatConnected().getValue()) {
                boolean reconnected = viewModel.connectToChat();
                if (reconnected) {
                    // Wait a bit for the connection to establish before retrying
                    messageInput.postDelayed(() -> {
                        if (viewModel.isChatConnected().getValue()) {
                            sendMessage(); // Retry sending the message
                        } else {
                            Toast.makeText(this, "Failed to reconnect", Toast.LENGTH_SHORT).show();
                        }
                    }, 1000);
                } else {
                    Toast.makeText(this, "Failed to reconnect", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Show the attachment options bottom sheet
     */
    private void showAttachmentOptions() {
        // Check if we have the necessary permissions first
        if (!checkMediaPermissions()) {
            return;
        }
        
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_attachments, null);
        dialog.setContentView(sheetView);
        
        // Set up click listeners for attachment options
        sheetView.findViewById(R.id.option_photo).setOnClickListener(v -> {
            dialog.dismiss();
            pickImage();
        });
        
        sheetView.findViewById(R.id.option_video).setOnClickListener(v -> {
            dialog.dismiss();
            pickVideo();
        });
        
        sheetView.findViewById(R.id.option_file).setOnClickListener(v -> {
            dialog.dismiss();
            pickDocument();
        });
        
        dialog.show();
    }
    
    /**
     * Pick an image from gallery
     */
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }
    
    /**
     * Pick a video from gallery
     */
    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        pickVideoLauncher.launch(intent);
    }
    
    /**
     * Pick a document file
     */
    private void pickDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain"
        });
        pickDocumentLauncher.launch(intent);
    }
    
    /**
     * Handle the selected file
     * 
     * @param fileUri The URI of the selected file
     */
    private void handleSelectedFile(Uri fileUri) {
        // Check file size first
        if (!FileUtils.isFileSizeValid(this, fileUri)) {
            Toast.makeText(this, 
                   "File exceeds maximum size limit of " + MEDIA_MAX_SIZE_MB + "MB", 
                   Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get the file's MIME type
        String mimeType = MimeTypeUtils.getMimeType(this, fileUri);
        if (mimeType == null) {
            Toast.makeText(this, "Unknown file type", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Handle the file based on its type
        if (MimeTypeUtils.isImage(mimeType)) {
            processImageFile(fileUri);
        } else if (MimeTypeUtils.isVideo(mimeType)) {
            processVideoFile(fileUri);
        } else if (MimeTypeUtils.isDocument(mimeType)) {
            processDocumentFile(fileUri);
        } else {
            Toast.makeText(this, "Unsupported file type", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Process an image file
     */
    private void processImageFile(Uri imageUri) {
        Toast.makeText(this, "Processing image...", Toast.LENGTH_SHORT).show();
        
        // Start compressing in the background
        CompletableFuture<String> future = FileUtils.compressImage(this, imageUri);
        future.thenAcceptAsync(compressedPath -> {
            // Launch media preview activity
            Intent intent = new Intent(this, MediaProcessingActivity.class);
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_URI, Uri.parse(compressedPath));
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_TYPE, MediaProcessingActivity.MEDIA_TYPE_IMAGE);
            intent.putExtra(MediaProcessingActivity.EXTRA_CHAT_ID, contactId);
            startActivity(intent);
        }, runnable -> runOnUiThread(runnable))
        .exceptionally(ex -> {
            // Handle error
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to process image: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error processing image", ex);
            });
            return null;
        });
    }
    
    /**
     * Process a video file
     */
    private void processVideoFile(Uri videoUri) {
        Toast.makeText(this, "Processing video...", Toast.LENGTH_SHORT).show();
        
        // Start compressing in the background
        CompletableFuture<String> future = FileUtils.compressVideo(this, videoUri);
        future.thenAcceptAsync(compressedPath -> {
            // Launch media preview activity
            Intent intent = new Intent(this, MediaProcessingActivity.class);
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_URI, Uri.parse(compressedPath));
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_TYPE, MediaProcessingActivity.MEDIA_TYPE_VIDEO);
            intent.putExtra(MediaProcessingActivity.EXTRA_CHAT_ID, contactId);
            startActivity(intent);
        }, runnable -> runOnUiThread(runnable))
        .exceptionally(ex -> {
            // Handle error
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to process video: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error processing video", ex);
            });
            return null;
        });
    }
    
    /**
     * Process a document file
     */
    private void processDocumentFile(Uri documentUri) {
        // For documents, we don't need to compress, just copy to app's cache
        String filePath = FileUtils.getPath(this, documentUri);
        if (filePath != null) {
            // Launch media processing activity directly
            Intent intent = new Intent(this, MediaProcessingActivity.class);
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_URI, Uri.parse(filePath));
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_TYPE, "document");
            intent.putExtra(MediaProcessingActivity.EXTRA_CHAT_ID, contactId);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Failed to process document", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Check if we have the necessary permissions for media access
     */
    private boolean checkMediaPermissions() {
        // For Android 13+ (API 33+), we need to check READ_MEDIA_IMAGES, READ_MEDIA_VIDEO
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                }, PERMISSION_REQUEST_MEDIA);
                return false;
            }
        } else {
            // For older Android versions, we need to check READ_EXTERNAL_STORAGE
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_MEDIA);
                return false;
            }
        }
        return true;
    }
} 