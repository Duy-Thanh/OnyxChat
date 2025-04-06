package com.nekkochan.onyxchat.ui.adapter;

import android.content.Context;
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
import com.nekkochan.onyxchat.data.Contact;
import com.nekkochan.onyxchat.data.Message;
import com.nekkochan.onyxchat.data.User;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adapter for displaying contacts in a RecyclerView
 */
public class ContactAdapter extends ListAdapter<Contact, ContactAdapter.ContactViewHolder> {
    
    private final OnContactClickListener clickListener;
    private final SimpleDateFormat timeFormat;
    
    /**
     * Interface for handling contact item clicks
     */
    public interface OnContactClickListener {
        void onContactClick(Contact contact);
        void onContactLongClick(Contact contact, View view);
    }
    
    /**
     * Constructor for the adapter
     * @param listener Click listener for contact items
     */
    public ContactAdapter(OnContactClickListener listener) {
        super(DIFF_CALLBACK);
        this.clickListener = listener;
        this.timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    }
    
    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view, clickListener);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        Contact contact = getItem(position);
        holder.bind(contact);
    }
    
    /**
     * ViewHolder for contact items
     */
    class ContactViewHolder extends RecyclerView.ViewHolder {
        private final TextView contactNameView;
        private final TextView lastMessageView;
        private final TextView lastMessageTimeView;
        private final ImageView contactAvatarView;
        private final ImageView verifiedIconView;
        private final ImageView appUserIconView;
        private final TextView unreadCountView;
        
        ContactViewHolder(@NonNull View itemView, OnContactClickListener listener) {
            super(itemView);
            
            contactNameView = itemView.findViewById(R.id.contactName);
            lastMessageView = itemView.findViewById(R.id.lastMessage);
            lastMessageTimeView = itemView.findViewById(R.id.lastMessageTime);
            contactAvatarView = itemView.findViewById(R.id.contactAvatar);
            verifiedIconView = itemView.findViewById(R.id.verifiedIcon);
            appUserIconView = itemView.findViewById(R.id.appUserIcon);
            unreadCountView = itemView.findViewById(R.id.unreadCount);
            
            // Set click listeners
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onContactClick(getItem(position));
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onContactLongClick(getItem(position), v);
                    return true;
                }
                return false;
            });
        }
        
        /**
         * Bind contact data to the ViewHolder
         * @param contact The contact to display
         */
        void bind(Contact contact) {
            // In Contacts tab, always use the real contact address (not nickname)
            String displayAddress = formatAddressForDisplay(contact.getContactAddress());
            contactNameView.setText(displayAddress);
            
            // Set verification status
            verifiedIconView.setVisibility(contact.isVerified() ? View.VISIBLE : View.GONE);
            
            // Show app user indicator if the contact is an app user
            if (contact.isAppUser()) {
                appUserIconView.setVisibility(View.VISIBLE);
                // Highlight the contact name for app users
                contactNameView.setTextColor(itemView.getContext().getResources().getColor(R.color.colorPrimary, null));
            } else {
                appUserIconView.setVisibility(View.GONE);
                contactNameView.setTextColor(itemView.getContext().getResources().getColor(android.R.color.primary_text_light, null));
            }
            
            // Last interaction time
            lastMessageTimeView.setText(timeFormat.format(new Date(contact.getLastInteractionTime())));
            
            // Default message if no message history
            lastMessageView.setText("No messages yet");
            
            // TODO: Set real profile picture when available
            contactAvatarView.setImageResource(android.R.drawable.ic_menu_myplaces);
            
            // TODO: Set unread count when implemented
            unreadCountView.setVisibility(View.GONE);
        }
        
        /**
         * Format onion address for display (truncate and add ellipsis)
         */
        private String formatAddressForDisplay(String address) {
            if (address == null || address.length() <= 12) {
                return address;
            }
            return address.substring(0, 10) + "...";
        }
    }
    
    /**
     * DiffUtil callback for efficient RecyclerView updates
     */
    private static final DiffUtil.ItemCallback<Contact> DIFF_CALLBACK = 
            new DiffUtil.ItemCallback<Contact>() {
        @Override
        public boolean areItemsTheSame(@NonNull Contact oldItem, @NonNull Contact newItem) {
            return oldItem.getId() == newItem.getId();
        }
        
        @Override
        public boolean areContentsTheSame(@NonNull Contact oldItem, @NonNull Contact newItem) {
            return oldItem.getLastInteractionTime() == newItem.getLastInteractionTime() &&
                   oldItem.isVerified() == newItem.isVerified() &&
                   oldItem.isBlocked() == newItem.isBlocked() && 
                   oldItem.isAppUser() == newItem.isAppUser() &&
                   ((oldItem.getNickName() == null && newItem.getNickName() == null) ||
                    (oldItem.getNickName() != null && oldItem.getNickName().equals(newItem.getNickName())));
        }
    };
} 