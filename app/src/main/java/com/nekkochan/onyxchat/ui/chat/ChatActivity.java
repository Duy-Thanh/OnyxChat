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
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.adapters.ChatMessageAdapter;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;

public class ChatActivity extends AppCompatActivity {
    
    private static final String TAG = "ChatActivity";
    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    
    private ChatViewModel viewModel;
    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private TextView statusTextView;
    private ChatMessageAdapter adapter;
    private String contactId;
    private String contactName;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        // Get contact information from intent
        contactId = getIntent().getStringExtra(EXTRA_CONTACT_ID);
        contactName = getIntent().getStringExtra(EXTRA_CONTACT_NAME);
        
        if (contactId == null) {
            Toast.makeText(this, "Error: No contact specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize view model
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.setCurrentRecipient(contactId);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(contactName != null ? contactName : contactId);
        }
        
        // Initialize views
        recyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        statusTextView = findViewById(R.id.chatStatusText);
        
        // Setup recycler view
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        
        // Create adapter for messages
        adapter = new ChatMessageAdapter(viewModel.getUserId());
        recyclerView.setAdapter(adapter);
        
        // Set up message input
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                sendButton.setEnabled(s.length() > 0 && viewModel.isChatConnected().getValue() == Boolean.TRUE);
            }
        });
        
        // Set up send button
        sendButton.setOnClickListener(v -> sendMessage());
        
        // Update connection status immediately
        updateConnectionStatus(viewModel.isChatConnected().getValue() == Boolean.TRUE);
        
        // Observe chat connection state
        viewModel.isChatConnected().observe(this, this::updateConnectionStatus);
        
        // Observe chat messages
        viewModel.getChatMessages().observe(this, chatMessages -> {
            if (chatMessages != null && !chatMessages.isEmpty()) {
                adapter.submitList(chatMessages);
                recyclerView.scrollToPosition(chatMessages.size() - 1);
            }
        });
        
        // Connect to chat service if not connected
        if (viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            viewModel.connectToChat();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Force UI update on resume
        updateConnectionStatus(viewModel.isChatConnected().getValue() == Boolean.TRUE);
        
        // Only try to reconnect if disconnected
        if (viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            viewModel.connectToChat();
        }
    }
    
    /**
     * Update the connection status UI
     */
    private void updateConnectionStatus(boolean isConnected) {
        if (isConnected) {
            statusTextView.setText(R.string.status_connected);
            statusTextView.setTextColor(getResources().getColor(R.color.success_green, null));
            sendButton.setEnabled(messageInput.getText().length() > 0);
        } else {
            statusTextView.setText(R.string.status_disconnected);
            statusTextView.setTextColor(getResources().getColor(R.color.error_red, null));
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