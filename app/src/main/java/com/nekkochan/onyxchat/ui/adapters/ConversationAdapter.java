package com.nekkochan.onyxchat.ui.adapters;

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
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {
    
    private List<ConversationDisplay> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    public interface OnConversationClickListener {
        void onConversationClick(ConversationDisplay conversation);
    }
    
    public ConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
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
            
            // Format the timestamp
            if (conversation.getLastMessageTime() != null) {
                timeText.setText(formatTime(conversation.getLastMessageTime().getTime()));
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
        
        private String formatTime(long timeMillis) {
            // If the message is from today, show the time, otherwise show the date
            long now = System.currentTimeMillis();
            if (now - timeMillis < 24 * 60 * 60 * 1000) {
                return TIME_FORMAT.format(timeMillis);
            } else {
                return DATE_FORMAT.format(timeMillis);
            }
        }
    }
} 