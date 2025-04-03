package com.nekkochan.onyxchat.ui.adapter;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.model.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adapter for displaying messages in a RecyclerView.
 */
public class MessageAdapter extends ListAdapter<Message, MessageAdapter.MessageViewHolder> {
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    
    private final OnMessageClickListener messageClickListener;
    private final String currentUserAddress;
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dateFormat;

    /**
     * Interface for handling message clicks
     */
    public interface OnMessageClickListener {
        void onMessageClick(Message message);
        void onMessageLongClick(Message message, View view);
    }

    /**
     * Constructor for the MessageAdapter
     *
     * @param currentUserAddress The current user's onion address
     * @param listener Click listener for messages
     */
    public MessageAdapter(String currentUserAddress, OnMessageClickListener listener) {
        super(new MessageDiffCallback());
        this.currentUserAddress = currentUserAddress;
        this.messageClickListener = listener;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        this.dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView;
        
        if (viewType == VIEW_TYPE_SENT) {
            itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sent, parent, false);
        } else {
            itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_received, parent, false);
        }
        
        return new MessageViewHolder(itemView, messageClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = getItem(position);
        holder.bind(message);
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        
        if (message.isSelf() || message.getSenderAddress().equals(currentUserAddress)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    /**
     * ViewHolder for a message item
     */
    class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final TextView timeText;
        private final ImageView statusIcon;
        private final OnMessageClickListener listener;

        public MessageViewHolder(@NonNull View itemView, OnMessageClickListener listener) {
            super(itemView);
            
            messageText = itemView.findViewById(R.id.message_text);
            timeText = itemView.findViewById(R.id.message_time);
            statusIcon = itemView.findViewById(R.id.message_status);
            this.listener = listener;
            
            // Set click listeners
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onMessageClick(getItem(position));
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onMessageLongClick(getItem(position), v);
                    return true;
                }
                return false;
            });
        }

        /**
         * Bind a message to the ViewHolder
         *
         * @param message The message to bind
         */
        public void bind(Message message) {
            // Set message content
            messageText.setText(message.getContent());
            
            // Format and set time
            timeText.setText(formatMessageTime(message.getTimestamp()));
            
            // Set message status icon
            if (getItemViewType() == VIEW_TYPE_SENT && statusIcon != null) {
                if (message.isDelivered()) {
                    statusIcon.setImageResource(R.drawable.ic_delivered);
                    statusIcon.setVisibility(View.VISIBLE);
                } else if (message.isSent()) {
                    statusIcon.setImageResource(R.drawable.ic_sent);
                    statusIcon.setVisibility(View.VISIBLE);
                } else {
                    statusIcon.setVisibility(View.GONE);
                }
            } else if (statusIcon != null) {
                statusIcon.setVisibility(View.GONE);
            }
            
            // Set encrypted message indication
            if (message.isEncrypted()) {
                itemView.setTag(R.id.tag_encrypted, true);
                // Add some visual indication that the message is encrypted
                // This could be a background tint, icon, etc.
            }
        }
    }

    /**
     * Format a timestamp for display in a message
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted time string
     */
    private String formatMessageTime(long timestamp) {
        // Simplify to just return the time string since we may not have context
        return timeFormat.format(new Date(timestamp));
    }

    /**
     * Get the context from any ViewHolder that's currently attached
     */
    private Context getContext() {
        // Since we can't access ViewHolders directly in newer RecyclerView versions,
        // try to get context from the first item if available, or return null
        if (getItemCount() > 0 && getCurrentList().size() > 0) {
            // We'll need a fallback to get context
            return null; // In a real implementation, we'd store context from onAttachedToRecyclerView
        }
        return null;
    }

    /**
     * DiffUtil callback for optimizing updates
     */
    private static class MessageDiffCallback extends DiffUtil.ItemCallback<Message> {
        @Override
        public boolean areItemsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
            return oldItem.getContent().equals(newItem.getContent()) &&
                   oldItem.isRead() == newItem.isRead() &&
                   oldItem.isDelivered() == newItem.isDelivered() &&
                   oldItem.isSent() == newItem.isSent();
        }
    }
}