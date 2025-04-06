package com.nekkochan.onyxchat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ChatService;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fragment displaying chat messages
 */
public class MessagesFragment extends Fragment {
    
    private static final String TAG = "MessagesFragment";
    private MainViewModel viewModel;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private EditText messageInput;
    private Button sendButton;
    private TextView statusTextView;
    private ChatMessageAdapter adapter;
    private String currentRecipientId = null;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        recyclerView = view.findViewById(R.id.messagesRecyclerView);
        emptyView = view.findViewById(R.id.emptyMessagesText);
        messageInput = view.findViewById(R.id.messageInput);
        sendButton = view.findViewById(R.id.sendButton);
        statusTextView = view.findViewById(R.id.chatStatusText);
        
        // Setup recycler view
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        
        // Create adapter for messages
        adapter = new ChatMessageAdapter();
        recyclerView.setAdapter(adapter);
        
        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        
        // Update connection status immediately
        updateConnectionStatus(viewModel.isChatConnected().getValue() == Boolean.TRUE);
        
        // Set up send button
        sendButton.setOnClickListener(v -> sendMessage());
        
        // Observe chat connection state
        viewModel.isChatConnected().observe(getViewLifecycleOwner(), isConnected -> 
            updateConnectionStatus(isConnected));
        
        // Observe chat messages
        viewModel.getChatMessages().observe(getViewLifecycleOwner(), this::updateMessages);
        
        // Observe online users
        viewModel.getOnlineUsers().observe(getViewLifecycleOwner(), this::updateOnlineUsers);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Force UI update on resume
        updateConnectionStatus(viewModel.isChatConnected().getValue() == Boolean.TRUE);
        updateMessages(viewModel.getChatMessages().getValue());
        
        // Only try to reconnect if disconnected and visible
        if (isAdded() && isVisible() && viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            viewModel.connectToChat();
        }
    }
    
    /**
     * Update the connection status UI
     */
    private void updateConnectionStatus(boolean isConnected) {
        if (!isAdded()) return;
        
        if (isConnected) {
            statusTextView.setText(R.string.status_connected);
            statusTextView.setTextColor(getResources().getColor(R.color.success_green, null));
            sendButton.setEnabled(true);
        } else {
            statusTextView.setText(R.string.status_disconnected);
            statusTextView.setTextColor(getResources().getColor(R.color.error_red, null));
            sendButton.setEnabled(false);
        }
    }
    
    /**
     * Update the messages list
     */
    private void updateMessages(List<MainViewModel.ChatMessage> chatMessages) {
        if (!isAdded()) return;
        
        if (chatMessages != null && !chatMessages.isEmpty()) {
            updateEmptyViewVisibility(false);
            adapter.submitList(chatMessages);
            recyclerView.scrollToPosition(chatMessages.size() - 1);
        } else {
            updateEmptyViewVisibility(true);
        }
    }
    
    /**
     * Update the visibility of the empty view based on whether there are conversations
     */
    private void updateEmptyViewVisibility(boolean isEmpty) {
        if (isEmpty) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Send a message
     */
    private void sendMessage() {
        if (!isAdded()) return;
        
        String message = messageInput.getText().toString().trim();
        if (message.isEmpty()) return;
        
        // Check if we have a connection
        if (!viewModel.isChatConnected().getValue()) {
            Toast.makeText(getContext(), "Not connected to chat server", Toast.LENGTH_SHORT).show();
            
            // Try to reconnect
            if (viewModel.connectToChat()) {
                Toast.makeText(getContext(), "Reconnecting...", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        // Check if we have a recipient (if not in a group chat)
        boolean messageSent = false;
        
        if (currentRecipientId != null) {
            messageSent = viewModel.sendDirectMessage(currentRecipientId, message);
        } else {
            messageSent = viewModel.sendChatMessage(message);
        }
        
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
                            Toast.makeText(getContext(), "Failed to reconnect", Toast.LENGTH_SHORT).show();
                        }
                    }, 1000);
                } else {
                    Toast.makeText(getContext(), "Failed to reconnect", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Failed to send message", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Update online users display
     */
    private void updateOnlineUsers(List<String> onlineUsers) {
        if (!isAdded()) return;
        
        if (onlineUsers != null && !onlineUsers.isEmpty()) {
            // Update UI with online users count
            String statusText = getString(R.string.status_connected) + 
                " (" + onlineUsers.size() + " " + 
                getString(R.string.online_users) + ")";
            statusTextView.setText(statusText);
        }
    }
    
    /**
     * Set the current recipient for direct messages
     */
    public void setCurrentRecipient(String recipientId) {
        this.currentRecipientId = recipientId;
        // Update UI to show who we're chatting with
        if (recipientId != null) {
            statusTextView.setText(getString(R.string.chatting_with, recipientId));
        } else {
            if (viewModel.isChatConnected().getValue() == Boolean.TRUE) {
                statusTextView.setText(R.string.status_connected);
            } else {
                statusTextView.setText(R.string.status_disconnected);
            }
        }
    }
    
    /**
     * Adapter for chat messages
     */
    private class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder> {
        private List<MainViewModel.ChatMessage> messages = new ArrayList<>();
        
        @NonNull
        @Override
        public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new ChatMessageViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
            holder.bind(messages.get(position));
        }
        
        @Override
        public int getItemCount() {
            return messages.size();
        }
        
        public void submitList(List<MainViewModel.ChatMessage> newMessages) {
            this.messages = new ArrayList<>(newMessages);
            notifyDataSetChanged();
        }
        
        class ChatMessageViewHolder extends RecyclerView.ViewHolder {
            private final TextView messageText;
            private final TextView senderText;
            private final TextView timeText;
            
            public ChatMessageViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.message_text);
                senderText = itemView.findViewById(R.id.message_sender);
                timeText = itemView.findViewById(R.id.message_time);
            }
            
            public void bind(MainViewModel.ChatMessage message) {
                messageText.setText(message.getContent());
                
                // Format timestamp
                String timeString = android.text.format.DateFormat.format("HH:mm", message.getTimestamp()).toString();
                timeText.setText(timeString);
                
                // Set message sender
                String senderId = message.getSenderId();
                if (senderId.equals(viewModel.getUserAddress().getValue())) {
                    senderText.setText(R.string.you);
                    // Align to the right for own messages
                    messageText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    senderText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    timeText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                } else {
                    senderText.setText(senderId);
                    // Align to the left for others' messages
                    messageText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    senderText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    timeText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                }
            }
        }
    }
} 