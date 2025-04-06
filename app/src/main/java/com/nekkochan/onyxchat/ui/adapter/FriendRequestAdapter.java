package com.nekkochan.onyxchat.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.model.FriendRequest;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying friend requests in a RecyclerView
 */
public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.FriendRequestViewHolder> {

    private final List<FriendRequest> requests;
    private final boolean isReceivedRequests;
    private final OnRequestActionListener acceptListener;
    private final OnRequestActionListener declineListener;
    
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    /**
     * Interface for handling request actions
     */
    public interface OnRequestActionListener {
        void onRequestAction(FriendRequest request);
    }

    /**
     * Constructor for the adapter
     * @param requests List of friend requests
     * @param isReceivedRequests Whether these are received requests or sent requests
     * @param acceptListener Listener for accept action (can be null for sent requests)
     * @param declineListener Listener for decline/cancel action
     */
    public FriendRequestAdapter(List<FriendRequest> requests, boolean isReceivedRequests,
                               OnRequestActionListener acceptListener,
                               OnRequestActionListener declineListener) {
        this.requests = requests;
        this.isReceivedRequests = isReceivedRequests;
        this.acceptListener = acceptListener;
        this.declineListener = declineListener;
    }

    @NonNull
    @Override
    public FriendRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_request, parent, false);
        return new FriendRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendRequestViewHolder holder, int position) {
        FriendRequest request = requests.get(position);
        holder.bind(request);
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    /**
     * ViewHolder for friend request items
     */
    class FriendRequestViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView profileImage;
        private final TextView nameText;
        private final TextView messageText;
        private final TextView dateText;
        private final Button acceptButton;
        private final Button declineButton;

        public FriendRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.requestProfileImage);
            nameText = itemView.findViewById(R.id.requestName);
            messageText = itemView.findViewById(R.id.requestMessage);
            dateText = itemView.findViewById(R.id.requestDate);
            acceptButton = itemView.findViewById(R.id.acceptButton);
            declineButton = itemView.findViewById(R.id.declineButton);
        }

        public void bind(FriendRequest request) {
            // Set user name
            nameText.setText(request.getDisplayName());
            
            // Set optional message
            if (request.getMessage() != null && !request.getMessage().isEmpty()) {
                messageText.setVisibility(View.VISIBLE);
                messageText.setText(request.getMessage());
            } else {
                messageText.setVisibility(View.GONE);
            }
            
            // Set request date
            if (request.getCreatedAt() != null) {
                dateText.setText(dateFormat.format(request.getCreatedAt()));
            } else {
                dateText.setVisibility(View.GONE);
            }
            
            // Configure button text based on request type
            if (isReceivedRequests) {
                // Received requests have accept/decline
                acceptButton.setText(R.string.accept);
                declineButton.setText(R.string.decline);
                acceptButton.setVisibility(View.VISIBLE);
            } else {
                // Sent requests only have cancel
                acceptButton.setVisibility(View.GONE);
                declineButton.setText(R.string.cancel);
            }
            
            // Set button click listeners
            if (acceptListener != null) {
                acceptButton.setOnClickListener(v -> acceptListener.onRequestAction(request));
            }
            
            if (declineListener != null) {
                declineButton.setOnClickListener(v -> declineListener.onRequestAction(request));
            }
        }
    }
} 