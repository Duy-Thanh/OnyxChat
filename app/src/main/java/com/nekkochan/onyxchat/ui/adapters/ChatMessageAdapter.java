package com.nekkochan.onyxchat.ui.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.viewmodel.ChatViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder> {
    private static final String TAG = "ChatMessageAdapter";
    private List<ChatViewModel.ChatMessage> messages = new ArrayList<>();
    private final String currentUserId;
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd", Locale.getDefault());
    private static final long ANIMATION_DURATION = 300;
    private int lastAnimatedPosition = -1;

    public ChatMessageAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
        // Ensure time format uses device timezone
        TIME_FORMAT.setTimeZone(TimeZone.getDefault());
        DATE_FORMAT.setTimeZone(TimeZone.getDefault());
    }

    @NonNull
    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == VIEW_TYPE_SENT) 
                ? R.layout.item_message_sent 
                : R.layout.item_message_received;
        
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new ChatMessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        ChatViewModel.ChatMessage message = messages.get(position);
        ChatViewModel.ChatMessage previousMessage = position > 0 ? messages.get(position - 1) : null;
        ChatViewModel.ChatMessage nextMessage = position < messages.size() - 1 ? messages.get(position + 1) : null;
        
        holder.bind(message, previousMessage, nextMessage);
        
        // Add animation for new items
        setAnimation(holder.itemView, position);
    }
    
    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastAnimatedPosition) {
            AlphaAnimation animation = new AlphaAnimation(0.0f, 1.0f);
            animation.setDuration(ANIMATION_DURATION);
            viewToAnimate.startAnimation(animation);
            lastAnimatedPosition = position;
        }
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    @Override
    public int getItemViewType(int position) {
        ChatViewModel.ChatMessage message = messages.get(position);
        if (message.getSenderId().equals(currentUserId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    public void submitList(List<ChatViewModel.ChatMessage> newMessages) {
        // Log timestamps of messages for debugging
        if (newMessages != null && !newMessages.isEmpty()) {
            Log.d(TAG, "Adding " + newMessages.size() + " messages");
            for (ChatViewModel.ChatMessage message : newMessages) {
                if (message.getTimestamp() != null) {
                    Log.d(TAG, "Message timestamp: " + message.getTimestamp() + " (" + formatTime(message.getTimestamp()) + ")");
                } else {
                    Log.d(TAG, "Message has null timestamp!");
                }
            }
        }
        
        this.messages = new ArrayList<>(newMessages);
        notifyDataSetChanged();
    }
    
    /**
     * Format a timestamp for display, ensuring proper timezone handling
     * @param date The date to format
     * @return Properly formatted time string
     */
    private String formatTime(Date date) {
        if (date == null) return "";
        
        // Create new formatter for thread safety
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        timeFormat.setTimeZone(TimeZone.getDefault());
        return timeFormat.format(date);
    }
    
    /**
     * Format a date for display, ensuring proper timezone handling
     * @param date The date to format
     * @return Properly formatted date string
     */
    private String formatDate(Date date) {
        if (date == null) return "";
        
        // Create new formatter for thread safety
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(date);
    }
    
    /**
     * Check if two messages were sent on the same day
     */
    private boolean isSameDay(ChatViewModel.ChatMessage m1, ChatViewModel.ChatMessage m2) {
        if (m1 == null || m2 == null) return false;
        
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(m1.getTimestamp());
        cal2.setTime(m2.getTimestamp());
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Check if a message is part of a sequence from the same sender
     */
    private boolean isPartOfSequence(ChatViewModel.ChatMessage current, ChatViewModel.ChatMessage other) {
        if (current == null || other == null) return false;
        
        // Messages from same sender and timestamps within 3 minutes
        boolean sameSender = current.getSenderId().equals(other.getSenderId());
        boolean closeTime = Math.abs(current.getTimestamp().getTime() - other.getTimestamp().getTime()) < 3 * 60 * 1000;
        
        return sameSender && closeTime && isSameDay(current, other);
    }
    
    class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final TextView timeText;
        private final CardView messageCardView;
        
        public ChatMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            timeText = itemView.findViewById(R.id.message_time);
            messageCardView = itemView.findViewById(R.id.messageCardView);
        }
        
        public void bind(ChatViewModel.ChatMessage message, ChatViewModel.ChatMessage previousMessage, ChatViewModel.ChatMessage nextMessage) {
            messageText.setText(message.getContent());
            
            // Log the time of this specific message
            Log.d(TAG, "Binding message: " + message.getContent() + " with timestamp: " + message.getTimestamp());
            
            // Manage message appearance based on sequence
            boolean isPartOfPreviousSequence = isPartOfSequence(message, previousMessage);
            boolean isPartOfNextSequence = isPartOfSequence(message, nextMessage);
            
            // Show timestamp for last message in sequence or when time gap is significant
            boolean showTime = !isPartOfNextSequence;
            timeText.setVisibility(showTime ? View.VISIBLE : View.GONE);
            
            // Format timestamp
            if (showTime) {
                // Check if the message timestamp is today
                Calendar today = Calendar.getInstance();
                Calendar messageDate = Calendar.getInstance();
                messageDate.setTime(message.getTimestamp());
                
                // Log the calendar values for debugging
                Log.d(TAG, "Today: " + today.get(Calendar.YEAR) + "-" + today.get(Calendar.DAY_OF_YEAR) + 
                      " Message: " + messageDate.get(Calendar.YEAR) + "-" + messageDate.get(Calendar.DAY_OF_YEAR));
                
                boolean isToday = today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                                 today.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR);
                
                String formattedTime;
                if (isToday) {
                    // Only show time for today's messages
                    formattedTime = formatTime(message.getTimestamp());
                } else {
                    // Show date and time for older messages
                    formattedTime = formatDate(message.getTimestamp()) + " " + formatTime(message.getTimestamp());
                }
                
                // Log the formatted time
                Log.d(TAG, "Formatted time: " + formattedTime);
                
                timeText.setText(formattedTime);
            }
            
            // If available, adjust bubble corners based on sequence
            if (messageCardView != null) {
                float cornerRadius = itemView.getContext().getResources().getDimension(R.dimen.message_corner_radius);
                
                // Set the base corner radius
                messageCardView.setRadius(cornerRadius);
                
                // Try to apply custom corner radius for grouped bubbles if supported by device
                try {
                    // We can adjust visual appearance based on sequence without manipulating each corner
                    // For example, we can add top or bottom margin for different messages in a sequence
                    int topMargin = isPartOfPreviousSequence ? 2 : 8;
                    int bottomMargin = isPartOfNextSequence ? 2 : 8;
                    
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) messageCardView.getLayoutParams();
                    if (params != null) {
                        params.topMargin = topMargin;
                        params.bottomMargin = bottomMargin;
                        messageCardView.setLayoutParams(params);
                    }
                } catch (Exception e) {
                    // Fallback silently if this doesn't work
                }
            }
        }
    }
} 