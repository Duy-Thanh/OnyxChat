package com.nekkochan.onyxchat.ui.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.adapters.ChatMessageAdapter;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatActivity extends AppCompatActivity {
    
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    
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
        
        // Set up contact info
        contactNameText.setText(contactName != null ? contactName : contactId);
        
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
        attachButton.setOnClickListener(v -> {
            Toast.makeText(this, "Attachment feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Set up emoji button
        emojiButton.setOnClickListener(v -> {
            Toast.makeText(this, "Emoji picker coming soon", Toast.LENGTH_SHORT).show();
        });
        
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
            Toast.makeText(this, "Chat settings coming soon", Toast.LENGTH_SHORT).show();
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
} 