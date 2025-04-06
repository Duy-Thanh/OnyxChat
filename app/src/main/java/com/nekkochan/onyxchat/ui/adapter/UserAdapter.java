package com.nekkochan.onyxchat.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.model.UserProfile;

import java.util.List;

/**
 * Adapter for displaying users in a RecyclerView
 */
public class UserAdapter extends ListAdapter<UserProfile, UserAdapter.UserViewHolder> {

    private final OnUserClickListener chatClickListener;
    private final OnUserClickListener addFriendClickListener;

    /**
     * Interface for handling user clicks
     */
    public interface OnUserClickListener {
        void onUserClick(UserProfile user);
    }

    /**
     * Constructor for the adapter
     * @param chatClickListener Listener for starting chat
     * @param addFriendClickListener Listener for adding friend
     */
    public UserAdapter(OnUserClickListener chatClickListener, OnUserClickListener addFriendClickListener) {
        super(new UserDiffCallback());
        this.chatClickListener = chatClickListener;
        this.addFriendClickListener = addFriendClickListener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view, chatClickListener, addFriendClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /**
     * Custom DiffUtil for more efficient list updates
     */
    static class UserDiffCallback extends DiffUtil.ItemCallback<UserProfile> {
        @Override
        public boolean areItemsTheSame(@NonNull UserProfile oldItem, @NonNull UserProfile newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull UserProfile oldItem, @NonNull UserProfile newItem) {
            // Check if any relevant properties have changed
            return oldItem.getId().equals(newItem.getId()) &&
                   oldItem.getUsername().equals(newItem.getUsername()) &&
                   oldItem.getDisplayName().equals(newItem.getDisplayName()) &&
                   oldItem.isActive() == newItem.isActive() &&
                   oldItem.getFriendStatus() == newItem.getFriendStatus();
        }
        
        @Nullable
        @Override
        public Object getChangePayload(@NonNull UserProfile oldItem, @NonNull UserProfile newItem) {
            // If only the active status changed, we can do a partial update
            if (oldItem.getId().equals(newItem.getId()) &&
                oldItem.getUsername().equals(newItem.getUsername()) &&
                oldItem.getDisplayName().equals(newItem.getDisplayName()) &&
                oldItem.getFriendStatus() == newItem.getFriendStatus() &&
                oldItem.isActive() != newItem.isActive()) {
                
                Bundle payload = new Bundle();
                payload.putBoolean("active_changed", true);
                payload.putBoolean("is_active", newItem.isActive());
                return payload;
            }
            return null;
        }
    }

    /**
     * ViewHolder for user items
     */
    class UserViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView userAvatar;
        private final TextView userDisplayName;
        private final TextView userUsername;
        private final Chip statusChip;
        private final MaterialButton actionButton;
        private final MaterialButton secondaryButton;
        private final OnUserClickListener chatClickListener;
        private final OnUserClickListener addFriendClickListener;

        public UserViewHolder(@NonNull View itemView, OnUserClickListener chatClickListener, OnUserClickListener addFriendClickListener) {
            super(itemView);
            this.chatClickListener = chatClickListener;
            this.addFriendClickListener = addFriendClickListener;
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userDisplayName = itemView.findViewById(R.id.userDisplayName);
            userUsername = itemView.findViewById(R.id.userUsername);
            statusChip = itemView.findViewById(R.id.statusChip);
            actionButton = itemView.findViewById(R.id.actionButton);
            secondaryButton = itemView.findViewById(R.id.secondaryButton);
        }

        public void bind(UserProfile user) {
            userDisplayName.setText(user.getDisplayName());
            userUsername.setText("@" + user.getUsername());
            
            // Set online status
            updateOnlineStatus(user.isActive());
            
            // Configure action buttons based on friend status
            configureActionButtons(user);
        }
        
        /**
         * Bind only changed data using payload
         */
        public void bind(UserProfile user, Bundle payload) {
            if (payload.getBoolean("active_changed", false)) {
                updateOnlineStatus(payload.getBoolean("is_active", false));
            } else {
                // Fall back to full bind if we have an unknown payload
                bind(user);
            }
        }
        
        /**
         * Update only the online status indicator
         */
        private void updateOnlineStatus(boolean isActive) {
            statusChip.setVisibility(View.VISIBLE);
            
            if (isActive) {
                statusChip.setText(R.string.online);
                statusChip.setChipBackgroundColor(
                        ContextCompat.getColorStateList(itemView.getContext(), R.color.accent));
            } else {
                statusChip.setText(R.string.offline);
                statusChip.setChipBackgroundColor(
                        ContextCompat.getColorStateList(itemView.getContext(), R.color.text_secondary_dark));
            }
        }
        
        /**
         * Configure the action buttons based on friend status
         */
        private void configureActionButtons(UserProfile user) {
            // Handle friend status and configure buttons
            switch (user.getFriendStatus()) {
                case CONTACT:
                    // Already a contact, show message button
                    actionButton.setText(R.string.message);
                    actionButton.setOnClickListener(v -> chatClickListener.onUserClick(user));
                    secondaryButton.setVisibility(View.GONE);
                    break;
                    
                case SENT:
                    // Friend request pending
                    actionButton.setText(R.string.pending);
                    actionButton.setEnabled(false);
                    secondaryButton.setVisibility(View.GONE);
                    break;
                    
                case RECEIVED:
                    // Received friend request, show accept/decline
                    actionButton.setText(R.string.accept);
                    actionButton.setOnClickListener(v -> {
                        // Handle accept friend request
                        // This would typically call a method in the ViewModel
                        // For now we'll just add as friend
                        addFriendClickListener.onUserClick(user);
                    });
                    secondaryButton.setVisibility(View.VISIBLE);
                    secondaryButton.setText(R.string.decline);
                    // Configure decline button click handler
                    break;
                    
                case NONE:
                default:
                    // Not a contact, show add friend button
                    actionButton.setText(R.string.add_friend);
                    actionButton.setOnClickListener(v -> addFriendClickListener.onUserClick(user));
                    secondaryButton.setVisibility(View.GONE);
                    break;
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            // Apply partial bind with the payload
            Bundle payload = (Bundle) payloads.get(0);
            holder.bind(getItem(position), payload);
        }
    }
} 