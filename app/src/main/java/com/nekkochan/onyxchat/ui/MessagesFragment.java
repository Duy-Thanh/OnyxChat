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
        
        // Connect to chat server if not already connected
        if (viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            boolean connectStarted = viewModel.connectToChat();
            if (!connectStarted) {
                Toast.makeText(getContext(), "Failed to connect to chat", Toast.LENGTH_SHORT).show();
            }
        }
        
        // Set up send button
        sendButton.setOnClickListener(v -> sendMessage());
        
        // Observe chat connection state
        viewModel.isChatConnected().observe(getViewLifecycleOwner(), isConnected -> {
            if (isConnected) {
                statusTextView.setText(R.string.status_connected);
                statusTextView.setTextColor(getResources().getColor(R.color.success_green, null));
                sendButton.setEnabled(true);
            } else {
                statusTextView.setText(R.string.status_disconnected);
                statusTextView.setTextColor(getResources().getColor(R.color.error_red, null));
                sendButton.setEnabled(false);
            }
        });
        
        // Observe chat messages
        viewModel.getChatMessages().observe(getViewLifecycleOwner(), chatMessages -> {
            if (chatMessages != null && !chatMessages.isEmpty()) {
                updateEmptyViewVisibility(false);
                adapter.submitList(chatMessages);
                recyclerView.scrollToPosition(chatMessages.size() - 1);
            } else {
                updateEmptyViewVisibility(true);
            }
        });
        
        // Observe online users
        viewModel.getOnlineUsers().observe(getViewLifecycleOwner(), onlineUsers -> {
            if (onlineUsers != null && !onlineUsers.isEmpty()) {
                // Update UI with online users
                updateOnlineUsers(onlineUsers);
            }
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Reconnect to chat if disconnected
        if (viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            viewModel.connectToChat();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        // Optionally disconnect from chat when the fragment is not visible
        // viewModel.disconnectFromChat();
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
     * Send a message to the current recipient
     */
    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        
        if (messageText.isEmpty()) {
            return;
        }
        
        if (currentRecipientId != null) {
            boolean sent = viewModel.sendDirectMessage(currentRecipientId, messageText);
            if (sent) {
                messageInput.setText("");
            } else {
                Toast.makeText(getContext(), "Failed to send message", Toast.LENGTH_SHORT).show();
            }
        } else {
            boolean sent = viewModel.sendChatMessage(messageText);
            if (sent) {
                messageInput.setText("");
            } else {
                Toast.makeText(getContext(), "Failed to send message", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Update the UI with online users
     */
    private void updateOnlineUsers(Map<String, String> onlineUsers) {
        // For now, just show a toast with the number of online users
        Toast.makeText(getContext(), 
            getString(R.string.online_users_count, onlineUsers.size()), 
            Toast.LENGTH_SHORT).show();
            
        // TODO: Add UI for selecting a recipient for direct messages
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
                
                // Show sender ID for messages from others
                if (message.getType() == ChatService.ChatMessage.MessageType.DIRECT && 
                    !message.getMessage().isSelf()) {
                    senderText.setText(message.getSenderId());
                    senderText.setVisibility(View.VISIBLE);
                } else {
                    senderText.setVisibility(View.GONE);
                }
                
                // Style the message based on type
                switch (message.getType()) {
                    case DIRECT:
                        if (message.getMessage().isSelf()) {
                            itemView.setBackgroundResource(R.drawable.bg_message_sent);
                        } else {
                            itemView.setBackgroundResource(R.drawable.bg_message_received);
                        }
                        break;
                    case SYSTEM:
                        itemView.setBackgroundResource(R.drawable.bg_message_system);
                        break;
                    case ERROR:
                        itemView.setBackgroundResource(R.drawable.bg_message_error);
                        break;
                    default:
                        itemView.setBackgroundResource(R.drawable.bg_message_received);
                        break;
                }
            }
        }
    }
} 