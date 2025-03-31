package com.nekkochan.onyxchat.ui.adapter;

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
import com.nekkochan.onyxchat.model.Contact;
import com.nekkochan.onyxchat.model.Conversation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter for displaying conversations in a RecyclerView.
 */
public class ConversationAdapter extends ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder> {
    
    private final OnConversationClickListener conversationClickListener;
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dateFormat;
    
    // Map to store contact info for each conversation
    private final Map<String, Contact> contactMap = new HashMap<>();

    /**
     * Interface for handling conversation clicks
     */
    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
        void onConversationLongClick(Conversation conversation, View view);
    }

    /**
     * Constructor for the ConversationAdapter
     *
     * @param listener Click listener for conversations
     */
    public ConversationAdapter(OnConversationClickListener listener) {
        super(new ConversationDiffCallback());
        this.conversationClickListener = listener;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        this.dateFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(itemView, conversationClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        Conversation conversation = getItem(position);
        Contact contact = contactMap.get(conversation.getContactAddress());
        holder.bind(conversation, contact);
    }

    /**
     * Update the contacts associated with conversations
     *
     * @param contacts Map of contact addresses to Contact objects
     */
    public void updateContacts(Map<String, Contact> contacts) {
        contactMap.clear();
        contactMap.putAll(contacts);
        notifyDataSetChanged();
    }

    /**
     * ViewHolder for a conversation item
     */
    class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ImageView contactPhoto;
        private final TextView contactName;
        private final TextView lastMessageText;
        private final TextView lastMessageTime;
        private final TextView unreadCount;
        private final ImageView encryptionIcon;
        private final OnConversationClickListener listener;

        public ConversationViewHolder(@NonNull View itemView, OnConversationClickListener listener) {
            super(itemView);
            
            contactPhoto = itemView.findViewById(R.id.contact_photo);
            contactName = itemView.findViewById(R.id.contact_name);
            lastMessageText = itemView.findViewById(R.id.last_message);
            lastMessageTime = itemView.findViewById(R.id.message_time);
            unreadCount = itemView.findViewById(R.id.unread_count);
            encryptionIcon = itemView.findViewById(R.id.encryption_icon);
            this.listener = listener;
            
            // Set click listeners
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onConversationClick(getItem(position));
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onConversationLongClick(getItem(position), v);
                    return true;
                }
                return false;
            });
        }

        /**
         * Bind a conversation to the ViewHolder
         *
         * @param conversation The conversation to bind
         * @param contact The contact associated with the conversation
         */
        public void bind(Conversation conversation, Contact contact) {
            // Set contact name
            if (contact != null) {
                contactName.setText(contact.getDisplayName());
                
                // Set contact photo (placeholder for now)
                contactPhoto.setImageResource(R.drawable.ic_person);
                
                // Show verification status
                if (contact.isVerified()) {
                    contactName.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_verified, 0);
                } else {
                    contactName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                }
            } else {
                // If contact is not available, use the address
                contactName.setText(Contact.shortenOnionAddress(conversation.getContactAddress()));
                contactPhoto.setImageResource(R.drawable.ic_person);
                contactName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
            
            // Set last message text
            String lastMessage = conversation.getLastMessageContent();
            if (lastMessage != null && !lastMessage.isEmpty()) {
                lastMessageText.setText(lastMessage);
                lastMessageText.setVisibility(View.VISIBLE);
            } else {
                lastMessageText.setVisibility(View.GONE);
            }
            
            // Set time
            lastMessageTime.setText(formatConversationTime(conversation.getLastMessageTimestamp()));
            
            // Set unread count
            int count = conversation.getUnreadCount();
            if (count > 0) {
                unreadCount.setText(String.valueOf(count));
                unreadCount.setVisibility(View.VISIBLE);
            } else {
                unreadCount.setVisibility(View.GONE);
            }
            
            // Show encryption status
            if (conversation.isEncrypted()) {
                encryptionIcon.setVisibility(View.VISIBLE);
            } else {
                encryptionIcon.setVisibility(View.GONE);
            }
            
            // Visual indication for pinned conversations
            if (conversation.isPinned()) {
                itemView.setBackgroundResource(R.drawable.bg_conversation_pinned);
            } else {
                itemView.setBackgroundResource(R.drawable.bg_conversation_normal);
            }
        }
    }

    /**
     * Format a timestamp for display in a conversation list
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted time string
     */
    private String formatConversationTime(long timestamp) {
        // If conversation is from today, show time only
        if (DateUtils.isToday(timestamp)) {
            return timeFormat.format(new Date(timestamp));
        } 
        // Otherwise show date
        else {
            return dateFormat.format(new Date(timestamp));
        }
    }

    /**
     * DiffUtil callback for optimizing updates
     */
    private static class ConversationDiffCallback extends DiffUtil.ItemCallback<Conversation> {
        @Override
        public boolean areItemsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
            return oldItem.getLastMessageTimestamp() == newItem.getLastMessageTimestamp() &&
                   oldItem.getUnreadCount() == newItem.getUnreadCount() &&
                   oldItem.isPinned() == newItem.isPinned() &&
                   oldItem.isArchived() == newItem.isArchived() &&
                   oldItem.getLastMessageContent().equals(newItem.getLastMessageContent());
        }
    }
} 