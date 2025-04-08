package com.nekkochan.onyxchat.ui.chat;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.ProgressBar;
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
import com.nekkochan.onyxchat.model.Contact;
import com.nekkochan.onyxchat.network.WebSocketClient;
import com.nekkochan.onyxchat.ui.adapters.ChatMessageAdapter;
import com.nekkochan.onyxchat.ui.media.MediaProcessingActivity;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;
import com.nekkochan.onyxchat.utils.EmojiUtils;
import com.nekkochan.onyxchat.utils.FileUtils;
import com.nekkochan.onyxchat.utils.MediaUtils;
import com.nekkochan.onyxchat.utils.MimeTypeUtils;
import com.vanniktech.emoji.EmojiPopup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import de.hdodenhof.circleimageview.CircleImageView;
import androidx.cardview.widget.CardView;

public class ChatActivity extends AppCompatActivity {
    
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    private static final int MEDIA_MAX_SIZE_MB = 500;
    private static final int PERMISSION_REQUEST_MEDIA = 100;
    
    // Constants for saving instance state
    private static final String KEY_IS_PROCESSING_MEDIA = "is_processing_media";
    private static final String KEY_PROCESSING_MEDIA_TYPE = "processing_media_type";
    private static final String KEY_PROCESSING_MEDIA_MESSAGE = "processing_media_message";
    
    // Media processing state variables
    private boolean isProcessingMedia = false;
    private String processingMediaType = null;
    private String processingMediaMessage = null;
    
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
    
    // Media status UI components
    private CardView mediaStatusCard;
    private ImageView mediaStatusIcon;
    private TextView mediaStatusText;
    private ProgressBar mediaStatusProgress;
    private ImageButton mediaStatusCancel;
    
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
    
    // Add this as a field in the ChatActivity class
    private BroadcastReceiver messageReceiver;
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Save media processing state
        outState.putBoolean(KEY_IS_PROCESSING_MEDIA, isProcessingMedia);
        if (isProcessingMedia) {
            outState.putString(KEY_PROCESSING_MEDIA_TYPE, processingMediaType);
            outState.putString(KEY_PROCESSING_MEDIA_MESSAGE, processingMediaMessage);
        }
    }
    
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
        
        // Initialize media status UI
        mediaStatusCard = findViewById(R.id.mediaStatusCard);
        mediaStatusIcon = findViewById(R.id.mediaStatusIcon);
        mediaStatusText = findViewById(R.id.mediaStatusText);
        mediaStatusProgress = findViewById(R.id.mediaStatusProgress);
        mediaStatusCancel = findViewById(R.id.mediaStatusCancel);
        
        mediaStatusCancel.setOnClickListener(v -> hideMediaStatus());
        
        // Add logging to track timestamps of messages
        // Set up contact info in header format similar to Discover Users screen
        contactNameText.setText(getFormattedContactName(contactId, contactName));
        
        // Find the statusChip's parent LinearLayout
        ViewParent statusChipParent = statusChip.getParent();
        if (statusChipParent instanceof LinearLayout) {
            // Get a properly formatted display name for the contact
            String displayName = getFormattedContactName(contactId, contactName);
            contactNameText.setText(displayName);
            
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
        
        // Set up observers for view model data
        observeViewModelData();
        
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
        
        // Restore media processing state if needed
        if (savedInstanceState != null) {
            isProcessingMedia = savedInstanceState.getBoolean(KEY_IS_PROCESSING_MEDIA, false);
            if (isProcessingMedia) {
                processingMediaType = savedInstanceState.getString(KEY_PROCESSING_MEDIA_TYPE);
                processingMediaMessage = savedInstanceState.getString(KEY_PROCESSING_MEDIA_MESSAGE);
                
                // Restore the UI state
                showMediaStatus(processingMediaMessage, processingMediaType, true);
            }
        }
        
        // Register broadcast receiver for message updates
        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    String senderId = intent.getStringExtra("senderId");
                    String recipientId = intent.getStringExtra("recipientId");
                    long timestamp = intent.getLongExtra("timestamp", 0);
                    
                    Log.d(TAG, "Received broadcast to refresh messages:");
                    Log.d(TAG, "  senderId: " + senderId);
                    Log.d(TAG, "  recipientId: " + recipientId);
                    Log.d(TAG, "  timestamp: " + timestamp);
                    Log.d(TAG, "  currentContactId: " + contactId);
                    
                    // Only refresh if this is the relevant chat
                    // Chat is relevant if:
                    // 1. We received a message from the contact we're chatting with
                    // 2. The recipient is the contact we're chatting with (we sent it)
                    boolean isRelevant = (senderId != null && senderId.equals(contactId)) || 
                                       (recipientId != null && recipientId.equals(contactId));
                    
                    if (isRelevant) {
                        Log.d(TAG, "Message is relevant to current chat, refreshing...");
                        runOnUiThread(() -> {
                            viewModel.refreshMessages();
                            // Observe the updated messages
                            observeViewModelData();
                        });
                    } else {
                        Log.d(TAG, "Message is NOT relevant to current chat, ignoring");
                    }
                }
            }
        };

        // Register receiver with broader intent filter
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.nekkochan.onyxchat.REFRESH_MESSAGES");
        registerReceiver(messageReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Refresh messages when activity comes to foreground
        if (viewModel != null) {
            viewModel.refreshMessages();
        }
        
        // Also check for media processing state
        if (FileUtils.isMediaProcessing()) {
            // Show media status UI with the current processing type
            String mediaType = FileUtils.getMediaProcessingType();
            String statusMessage = "Processing " + mediaType + "...";
            showMediaStatus(statusMessage, mediaType, true);
        } else if (isProcessingMedia) {
            // If we think we're processing but FileUtils disagrees, hide the UI
            hideMediaStatus();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dismiss emoji popup to prevent memory leaks
        EmojiUtils.dismissEmojiPopup();
        
        // Unregister broadcast receiver
        if (messageReceiver != null) {
            try {
                unregisterReceiver(messageReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering message receiver", e);
            }
        }
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
        // We're allowing files of any size now, so no need to check
        // Just log the file size for reference
        try {
            long fileSize = MediaUtils.getFileSize(this, fileUri);
            Log.d(TAG, "Selected file size: " + (fileSize / (1024.0 * 1024.0)) + " MB");
        } catch (Exception e) {
            Log.e(TAG, "Error getting file size", e);
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
     * Show the media processing status UI with the specified message
     * 
     * @param message Status message to show
     * @param mediaType Type of media (video, image, document)
     * @param isIndeterminate Whether the progress should be indeterminate
     */
    private void showMediaStatus(String message, String mediaType, boolean isIndeterminate) {
        // Save the current processing state
        isProcessingMedia = true;
        processingMediaType = mediaType;
        processingMediaMessage = message;
        
        // Set the appropriate icon based on media type
        if ("video".equals(mediaType)) {
            mediaStatusIcon.setImageResource(R.drawable.ic_video);
        } else if ("image".equals(mediaType)) {
            mediaStatusIcon.setImageResource(R.drawable.ic_image);
        } else if ("document".equals(mediaType)) {
            mediaStatusIcon.setImageResource(R.drawable.ic_document);
        }
        
        // Set the status text
        mediaStatusText.setText(message);
        
        // Configure the progress bar
        mediaStatusProgress.setIndeterminate(isIndeterminate);
        
        // Show the card if not already visible
        if (mediaStatusCard.getVisibility() != View.VISIBLE) {
            mediaStatusCard.setVisibility(View.VISIBLE);
            
            // Use animation to make it appear smoothly
            mediaStatusCard.setAlpha(0f);
            mediaStatusCard.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start();
        }
    }
    
    /**
     * Update the progress for the media status
     * 
     * @param progress Current progress value (0-100)
     */
    private void updateMediaStatusProgress(int progress) {
        if (mediaStatusCard.getVisibility() == View.VISIBLE) {
            mediaStatusProgress.setIndeterminate(false);
            mediaStatusProgress.setProgress(progress);
        }
    }
    
    /**
     * Hide the media status UI
     */
    private void hideMediaStatus() {
        // Clear the processing state
        isProcessingMedia = false;
        processingMediaType = null;
        processingMediaMessage = null;
        
        if (mediaStatusCard.getVisibility() == View.VISIBLE) {
            mediaStatusCard.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> mediaStatusCard.setVisibility(View.GONE))
                    .start();
        }
    }
    
    /**
     * Process an image file
     */
    private void processImageFile(Uri imageUri) {
        // Show media status instead of toast
        showMediaStatus("Processing image...", "image", true);
        
        // Start compressing in the background
        CompletableFuture<String> future = FileUtils.compressImage(this, imageUri);
        future.thenAcceptAsync(compressedPath -> {
            // Hide the status UI before launching the new activity
            hideMediaStatus();
            
            // Launch media preview activity
            Intent intent = new Intent(this, MediaProcessingActivity.class);
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_URI, Uri.parse(compressedPath));
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_TYPE, MediaProcessingActivity.MEDIA_TYPE_IMAGE);
            intent.putExtra(MediaProcessingActivity.EXTRA_CHAT_ID, contactId);
            startActivity(intent);
        }, runnable -> runOnUiThread(runnable))
        .exceptionally(ex -> {
            // Handle error with media status UI
            runOnUiThread(() -> {
                mediaStatusProgress.setIndeterminate(false);
                mediaStatusText.setText("Error: " + ex.getMessage());
                
                // Change UI to indicate error
                mediaStatusCard.setCardBackgroundColor(getResources().getColor(R.color.error_red, null));
                
                // Auto-dismiss after 3 seconds
                mediaStatusCard.postDelayed(this::hideMediaStatus, 3000);
                
                Log.e(TAG, "Error processing image", ex);
            });
            return null;
        });
    }
    
    /**
     * Process a video file
     */
    private void processVideoFile(Uri videoUri) {
        // Show media status instead of toast
        showMediaStatus("Processing video...", "video", true);
        
        // Start compressing in the background
        CompletableFuture<String> future = FileUtils.compressVideo(this, videoUri);
        future.thenAcceptAsync(compressedPath -> {
            // Hide the status UI before launching the new activity
            hideMediaStatus();
            
            // Launch media preview activity
            Intent intent = new Intent(this, MediaProcessingActivity.class);
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_URI, Uri.parse(compressedPath));
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_TYPE, MediaProcessingActivity.MEDIA_TYPE_VIDEO);
            intent.putExtra(MediaProcessingActivity.EXTRA_CHAT_ID, contactId);
            startActivity(intent);
        }, runnable -> runOnUiThread(runnable))
        .exceptionally(ex -> {
            // Handle error with media status UI
            runOnUiThread(() -> {
                mediaStatusProgress.setIndeterminate(false);
                mediaStatusText.setText("Error: " + ex.getMessage());
                
                // Change UI to indicate error
                mediaStatusCard.setCardBackgroundColor(getResources().getColor(R.color.error_red, null));
                
                // Auto-dismiss after 3 seconds
                mediaStatusCard.postDelayed(this::hideMediaStatus, 3000);
                
                Log.e(TAG, "Error processing video", ex);
            });
            return null;
        });
    }
    
    /**
     * Process a document file
     */
    private void processDocumentFile(Uri documentUri) {
        // Show media status
        showMediaStatus("Processing document...", "document", true);
        
        // For documents, we don't need to compress, just copy to app's cache
        String filePath = FileUtils.getPath(this, documentUri);
        if (filePath != null) {
            // Hide the status UI before launching the new activity
            hideMediaStatus();
            
            // Launch media processing activity directly
            Intent intent = new Intent(this, MediaProcessingActivity.class);
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_URI, Uri.parse(filePath));
            intent.putExtra(MediaProcessingActivity.EXTRA_MEDIA_TYPE, "document");
            intent.putExtra(MediaProcessingActivity.EXTRA_CHAT_ID, contactId);
            startActivity(intent);
        } else {
            // Show error in media status
            mediaStatusProgress.setIndeterminate(false);
            mediaStatusText.setText("Error: Failed to process document");
            
            // Change UI to indicate error
            mediaStatusCard.setCardBackgroundColor(getResources().getColor(R.color.error_red, null));
            
            // Auto-dismiss after 3 seconds
            mediaStatusCard.postDelayed(this::hideMediaStatus, 3000);
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
    
    /**
     * Observe the view model data and update the UI accordingly
     */
    private void observeViewModelData() {
        // Observe connection state
        viewModel.isChatConnected().observe(this, isConnected -> {
            updateConnectionStatus(isConnected);
        });
        
        // Observe chat messages
        viewModel.getChatMessages().observe(this, messages -> {
            if (messages != null && !messages.isEmpty()) {
                Log.d(TAG, "Received " + messages.size() + " messages from ViewModel");
                
                // Update the adapter with the new messages
                adapter.setChatMessages(messages);
                
                // Scroll to the bottom if we're already near it
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                    int messageCount = adapter.getItemCount();
                    
                    if (lastVisiblePosition >= messageCount - 2 || messageCount <= 5) {
                        recyclerView.post(() -> {
                            recyclerView.smoothScrollToPosition(messageCount - 1);
                        });
                    } else {
                        // Show scroll button
                        scrollDownButton.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        
        // Observe error messages
        viewModel.getErrorMessage().observe(this, errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // Add a method to format contact names consistently
    private String getFormattedContactName(String contactId, String contactName) {
        if (contactName != null && !contactName.isEmpty() && !contactName.equals(contactId)) {
            // If we already have a display name that's different from the ID, use it
            return contactName;
        }
        
        // Otherwise, format the contact ID appropriately
        try {
            // If it's an email, format it nicely
            if (contactId.contains("@")) {
                // Get the part before @ symbol
                String username = contactId.split("@")[0];
                // Capitalize first letter and format
                return username.substring(0, 1).toUpperCase() + username.substring(1);
            }
            
            // If it looks like a UUID (contains hyphens and is the right length)
            if (contactId.contains("-") && contactId.length() > 8) {
                // Try to create something more user-friendly
                String shortId = contactId.split("-")[0];
                if (shortId.length() >= 8) {
                    // Create a user-friendly name
                    return "Test User-" + shortId;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting contact name", e);
        }
        
        // Fallback to original ID
        return contactId;
    }
} 