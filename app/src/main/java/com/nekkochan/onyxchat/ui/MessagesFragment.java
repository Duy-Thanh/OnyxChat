package com.nekkochan.onyxchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ai.AIFeatureManager;
import com.nekkochan.onyxchat.ai.ContentModerationManager;
import com.nekkochan.onyxchat.ai.SmartReplyManager;
import com.nekkochan.onyxchat.model.Message;
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
    
    // AI features
    private AIFeatureManager aiFeatureManager;
    private ChipGroup smartRepliesChipGroup;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        
        // Initialize AI feature manager
        aiFeatureManager = AIFeatureManager.getInstance(requireContext());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Initialize view model
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        
        // Initialize AI feature manager
        aiFeatureManager = AIFeatureManager.getInstance(requireContext());
        
        View view = inflater.inflate(R.layout.fragment_messages, container, false);
        
        // Force smart replies container to be visible
        View smartRepliesContainer = view.findViewById(R.id.smartRepliesContainer);
        if (smartRepliesContainer != null) {
            smartRepliesContainer.setVisibility(View.VISIBLE);
        }
        
        return view;
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
        
        // Find smart replies components directly
        smartRepliesChipGroup = view.findViewById(R.id.smartRepliesChipGroup);
        View aiSettingsButton = view.findViewById(R.id.aiSettingsButton);
        
        // Always force smart replies to be visible regardless of AI settings
        forceShowSmartReplies();
        
        // Ensure the smart replies card is visible
        View smartRepliesCard = view.findViewById(R.id.smartRepliesCard);
        if (smartRepliesCard != null) {
            smartRepliesCard.setVisibility(View.VISIBLE);
            smartRepliesCard.bringToFront();
        }
        
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
        
        // Set up AI settings button
        aiSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AISettingsActivity.class);
            startActivity(intent);
        });
        
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
        
        // Force show smart replies with a delay to ensure they're visible
        new Handler(Looper.getMainLooper()).postDelayed(this::forceShowSmartReplies, 500);
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
        if (chatMessages == null) {
            updateEmptyViewVisibility(true);
            return;
        }
        
        if (!chatMessages.isEmpty()) {
            updateEmptyViewVisibility(false);
            recyclerView.setVisibility(View.VISIBLE);
            
            // Process messages with AI features
            List<MainViewModel.ChatMessage> processedMessages = new ArrayList<>();
            
            for (MainViewModel.ChatMessage chatMessage : chatMessages) {
                // Convert to Message model for AI processing
                Message message = new Message();
                message.setContent(chatMessage.getContent());
                message.setSenderAddress(chatMessage.getSenderId());
                message.setTimestamp(chatMessage.getTimestamp().getTime() / 1000); // Fixing the timestamp conversion
                
                // Add message to smart reply context
                if (aiFeatureManager.isSmartRepliesEnabled()) {
                    aiFeatureManager.setCurrentUserId(viewModel.getUserAddress().getValue());
                    aiFeatureManager.addMessageToSmartReplyContext(message);
                }
                
                // Add to processed list
                processedMessages.add(chatMessage);
            }
            
            // Update adapter with processed messages
            adapter.submitList(processedMessages);
            
            // Scroll to bottom
            recyclerView.scrollToPosition(processedMessages.size() - 1);
            
            // Always force smart replies to be visible regardless of AI settings
            forceShowSmartReplies();
            
            // Force a layout pass to ensure the smart replies are visible
            getView().post(() -> {
                View smartRepliesCard = getView().findViewById(R.id.smartRepliesCard);
                if (smartRepliesCard != null) {
                    smartRepliesCard.setVisibility(View.VISIBLE);
                    smartRepliesCard.bringToFront();
                }
            });
        } else {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
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
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            return;
        }
        
        // Clear input field
        messageInput.setText("");
        
        // Clear smart replies
        if (smartRepliesChipGroup != null) {
            smartRepliesChipGroup.removeAllViews();
            View smartRepliesContainer = smartRepliesChipGroup.getParent() instanceof View ? 
                    (View) smartRepliesChipGroup.getParent() : null;
            if (smartRepliesContainer != null) {
                smartRepliesContainer.setVisibility(View.GONE);
            }
        }
        
        // Check if content moderation is enabled
        if (aiFeatureManager.isContentModerationEnabled()) {
            moderateMessage(messageText);
        } else {
            sendMessageToServer(messageText);
        }
    }
    
    /**
     * Moderate a message before sending
     */
    private void moderateMessage(String messageText) {
        aiFeatureManager.moderateContent(messageText, (original, result) -> {
            if (getActivity() == null || !isAdded()) return;
            
            if (result == ContentModerationManager.ModerationResult.FLAGGED) {
                // Show warning dialog for flagged content
                requireActivity().runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.moderation_warning_title)
                            .setMessage(R.string.moderation_warning_message)
                            .setPositiveButton(R.string.send_anyway, (dialog, which) -> {
                                sendMessageToServer(messageText);
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                                // Return the text to the input field
                                messageInput.setText(messageText);
                            })
                            .show();
                });
            } else {
                // Content is safe, send it
                sendMessageToServer(messageText);
            }
        });
    }
    
    /**
     * Send message to the server
     */
    private void sendMessageToServer(String messageText) {
        // Create a new message
        Message message = new Message();
        message.setContent(messageText);
        message.setSenderAddress(viewModel.getUserAddress().getValue());
        message.setTimestamp(System.currentTimeMillis());
        
        // Process outgoing message with AI features (for translation if needed)
        if (aiFeatureManager.isTranslationEnabled()) {
            aiFeatureManager.processOutgoingMessage(message, (originalMsg, translatedMsg, moderationResult) -> {
                // Send the original message (translation is only for display purposes)
                sendProcessedMessage(originalMsg);
            });
        } else {
            sendProcessedMessage(message);
        }
    }
    
    /**
     * Send the processed message to the server
     */
    private void sendProcessedMessage(Message message) {
        if (getActivity() == null || !isAdded()) return;
        
        // Add to smart reply context if enabled
        if (aiFeatureManager.isSmartRepliesEnabled()) {
            aiFeatureManager.addMessageToSmartReplyContext(message);
        }
        
        // Send to server
        if (currentRecipientId != null) {
            // Direct message
            viewModel.sendDirectMessage(currentRecipientId, message.getContent());
        } else {
            // Broadcast message
            viewModel.sendBroadcastMessage(message.getContent());
        }
    }
    
    /**
     * Generate smart reply suggestions
     */
    private void generateSmartReplies() {
        if (smartRepliesChipGroup == null) return;
        
        // Always show smart replies container
        View smartRepliesContainer = getView().findViewById(R.id.smartRepliesContainer);
        if (smartRepliesContainer != null) {
            smartRepliesContainer.setVisibility(View.VISIBLE);
        }
        
        smartRepliesChipGroup.removeAllViews();
        
        aiFeatureManager.generateSmartReplies(suggestions -> {
            if (getActivity() == null || !isAdded()) return;
            
            List<String> replySuggestions = suggestions;
            if (replySuggestions == null || replySuggestions.isEmpty()) {
                replySuggestions = new ArrayList<>();
                replySuggestions.add("Hello!");
                replySuggestions.add("How are you?");
                replySuggestions.add("Nice to chat with you!");
                replySuggestions.add("What's up?");
                replySuggestions.add("Tell me more");
            }
            
            // Add chips for each suggestion
            for (String suggestion : replySuggestions) {
                Chip chip = new Chip(requireContext());
                chip.setText(suggestion);
                chip.setClickable(true);
                chip.setCheckable(false);
                
                // Set up click listener to use the suggestion
                chip.setOnClickListener(v -> {
                    messageInput.setText(suggestion);
                    sendMessage();
                });
                
                smartRepliesChipGroup.addView(chip);
            }
        });
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
            MainViewModel.ChatMessage chatMessage = messages.get(position);
            
            // If translation is enabled and this is an incoming message, process it
            if (aiFeatureManager.isTranslationEnabled() && 
                    !chatMessage.getSenderId().equals(viewModel.getUserAddress().getValue())) {
                
                // Create a Message object from ChatMessage
                Message message = new Message();
                message.setContent(chatMessage.getContent());
                message.setSenderAddress(chatMessage.getSenderId());
                message.setTimestamp(chatMessage.getTimestamp().getTime());
                
                aiFeatureManager.processIncomingMessage(message, (originalMessage, translatedMessage, moderationResult) -> {
                    if (translatedMessage != null) {
                        // Update UI on main thread with translated content
                        requireActivity().runOnUiThread(() -> {
                            holder.bind(chatMessage, translatedMessage.getContent());
                        });
                    } else {
                        // No translation needed
                        requireActivity().runOnUiThread(() -> {
                            holder.bind(chatMessage, null);
                        });
                    }
                });
            } else {
                // No translation needed
                holder.bind(chatMessage, null);
            }
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
            private final TextView translationInfoText;
            private String originalContent;
            private String translatedContent;
            private boolean showingTranslation = true;
            
            public ChatMessageViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.message_text);
                senderText = itemView.findViewById(R.id.message_sender);
                timeText = itemView.findViewById(R.id.message_time);
                translationInfoText = itemView.findViewById(R.id.translation_info);
                
                // Set up click listener for translation toggle
                if (translationInfoText != null) {
                    translationInfoText.setOnClickListener(v -> toggleTranslation());
                }
            }
            
            public void bind(MainViewModel.ChatMessage message, String translatedText) {
                // Store original and translated content
                originalContent = message.getContent();
                translatedContent = translatedText;
                showingTranslation = translatedText != null;
                
                // Set message content (translated if available)
                if (showingTranslation && translatedContent != null) {
                    messageText.setText(translatedContent);
                    translationInfoText.setText(R.string.show_original);
                    translationInfoText.setVisibility(View.VISIBLE);
                } else {
                    messageText.setText(originalContent);
                    if (translatedContent != null) {
                        translationInfoText.setText(R.string.show_translation);
                        translationInfoText.setVisibility(View.VISIBLE);
                    } else {
                        translationInfoText.setVisibility(View.GONE);
                    }
                }
                
                // Format timestamp
                String timeString = android.text.format.DateFormat.format("hh:mm a", message.getTimestamp().getTime()).toString();
                timeText.setText(timeString);
                
                // Set message sender
                String senderId = message.getSenderId();
                if (senderId.equals(viewModel.getUserAddress().getValue())) {
                    senderText.setText(R.string.you);
                    // Align to the right for own messages
                    messageText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    senderText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    timeText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    if (translationInfoText != null) {
                        translationInfoText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    }
                } else {
                    senderText.setText(senderId);
                    // Align to the left for others' messages
                    messageText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    senderText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    timeText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    if (translationInfoText != null) {
                        translationInfoText.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    }
                }
            }
            
            private void toggleTranslation() {
                if (translatedContent == null) return;
                
                showingTranslation = !showingTranslation;
                
                if (showingTranslation) {
                    messageText.setText(translatedContent);
                    translationInfoText.setText(R.string.show_original);
                } else {
                    messageText.setText(originalContent);
                    translationInfoText.setText(R.string.show_translation);
                }
            }
        }
    }
    
    private void forceShowSmartReplies() {
        if (getView() == null) return;
        
        // Find the smart replies scroll view
        View smartRepliesScrollView = getView().findViewById(R.id.smartRepliesScrollView);
        if (smartRepliesScrollView != null) {
            smartRepliesScrollView.setVisibility(View.VISIBLE);
        }
        
        if (smartRepliesChipGroup != null) {
            smartRepliesChipGroup.removeAllViews();
            
            // Add default smart replies
            List<String> replySuggestions = new ArrayList<>();
            replySuggestions.add("Hello!");
            replySuggestions.add("How are you?");
            replySuggestions.add("Nice to chat with you!");
            replySuggestions.add("What's up?");
            replySuggestions.add("Tell me more");
            
            // Add chips for each suggestion
            for (String suggestion : replySuggestions) {
                Chip chip = new Chip(requireContext());
                chip.setText(suggestion);
                chip.setClickable(true);
                chip.setCheckable(false);
                
                // Make chips more visually distinctive
                chip.setChipBackgroundColorResource(android.R.color.white);
                chip.setTextColor(getResources().getColor(R.color.colorPrimary, null));
                chip.setChipStrokeWidth(2);
                chip.setChipStrokeColorResource(R.color.colorAccent);
                chip.setElevation(4);
                
                // Set up click listener to use the suggestion
                chip.setOnClickListener(v -> {
                    messageInput.setText(suggestion);
                    sendMessage();
                });
                
                smartRepliesChipGroup.addView(chip);
            }
            
            // Force a layout pass to ensure visibility
            smartRepliesChipGroup.post(() -> {
                smartRepliesChipGroup.requestLayout();
                smartRepliesChipGroup.invalidate();
            });
        }
    }
}