package com.nekkochan.onyxchat.ui.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.model.ConversationDisplay;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {
    
    private static final String TAG = "ConversationAdapter";
    private List<ConversationDisplay> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    public interface OnConversationClickListener {
        void onConversationClick(ConversationDisplay conversation);
    }
    
    public ConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
        // Ensure formatters use device timezone
        TIME_FORMAT.setTimeZone(TimeZone.getDefault());
        DATE_FORMAT.setTimeZone(TimeZone.getDefault());
    }
    
    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view, listener);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        holder.bind(conversations.get(position));
    }
    
    @Override
    public int getItemCount() {
        return conversations.size();
    }
    
    public void submitList(List<ConversationDisplay> newConversations) {
        this.conversations = new ArrayList<>(newConversations);
        notifyDataSetChanged();
    }
    
    /**
     * Format a time value to display consistently in 24-hour format
     * @param date The date to format
     * @return The formatted time string in HH:mm format
     */
    private String formatTime(Date date) {
        if (date == null) return "";
        
        // Create a new formatter for thread safety
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        timeFormat.setTimeZone(TimeZone.getDefault());
        
        String formattedTime = timeFormat.format(date);
        Log.d(TAG, "Formatting time: " + date.toString() + " -> " + formattedTime);
        return formattedTime;
    }
    
    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView messageText;
        private final TextView timeText;
        private final TextView unreadCountText;
        private final ImageView statusIcon;
        private final OnConversationClickListener clickListener;
        
        public ConversationViewHolder(@NonNull View itemView, OnConversationClickListener listener) {
            super(itemView);
            nameText = itemView.findViewById(R.id.conversationName);
            messageText = itemView.findViewById(R.id.conversationMessage);
            timeText = itemView.findViewById(R.id.conversationTime);
            unreadCountText = itemView.findViewById(R.id.unreadCount);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            this.clickListener = listener;
        }
        
        public void bind(ConversationDisplay conversation) {
            nameText.setText(conversation.getDisplayName());
            messageText.setText(conversation.getLastMessage());
            
            // Format the timestamp using the adapter's formatTime method
            if (conversation.getLastMessageTime() != null) {
                // Use 24-hour time format exclusively for consistency
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                timeFormat.setTimeZone(TimeZone.getDefault());
                
                timeText.setText(timeFormat.format(conversation.getLastMessageTime()));
                
                Log.d("ConversationAdapter", "Setting timestamp: " + 
                      conversation.getLastMessageTime() + " -> " + 
                      timeFormat.format(conversation.getLastMessageTime()));
            } else {
                timeText.setText("");
                Log.d("ConversationAdapter", "No timestamp available for conversation");
            }
            
            // Show unread count if any
            if (conversation.getUnreadCount() > 0) {
                unreadCountText.setVisibility(View.VISIBLE);
                unreadCountText.setText(String.valueOf(conversation.getUnreadCount()));
            } else {
                unreadCountText.setVisibility(View.GONE);
            }
            
            // Show online status
            statusIcon.setVisibility(conversation.isOnline() ? View.VISIBLE : View.GONE);
            
            // Set click listener
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onConversationClick(conversation);
                }
            });
        }
    }
} 