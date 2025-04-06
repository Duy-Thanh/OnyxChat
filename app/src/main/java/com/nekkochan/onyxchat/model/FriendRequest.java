package com.nekkochan.onyxchat.model;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

/**
 * Represents a friend request in the application
 */
public class FriendRequest {
    
    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED
    }
    
    @SerializedName("id")
    private String id;
    
    @SerializedName("sender")
    private UserProfile sender;
    
    @SerializedName("receiver")
    private UserProfile receiver;
    
    @SerializedName("message")
    private String message;
    
    @SerializedName("status")
    private String status;
    
    @SerializedName("createdAt")
    private Date createdAt;
    
    /**
     * Constructor for received requests
     */
    public FriendRequest(String id, UserProfile sender, String message, String status, Date createdAt) {
        this.id = id;
        this.sender = sender;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }
    
    /**
     * Constructor for sent requests
     */
    public FriendRequest(String id, UserProfile receiver, String message, String status, Date createdAt, boolean isSent) {
        this.id = id;
        this.receiver = receiver;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public UserProfile getSender() {
        return sender;
    }
    
    public void setSender(UserProfile sender) {
        this.sender = sender;
    }
    
    public UserProfile getReceiver() {
        return receiver;
    }
    
    public void setReceiver(UserProfile receiver) {
        this.receiver = receiver;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Status getStatus() {
        if (status == null) {
            return Status.PENDING;
        }
        
        switch (status.toLowerCase()) {
            case "accepted":
                return Status.ACCEPTED;
            case "rejected":
                return Status.REJECTED;
            default:
                return Status.PENDING;
        }
    }
    
    public void setStatus(Status status) {
        switch (status) {
            case ACCEPTED:
                this.status = "accepted";
                break;
            case REJECTED:
                this.status = "rejected";
                break;
            default:
                this.status = "pending";
                break;
        }
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Get the name to display for this request
     */
    @NonNull
    public String getDisplayName() {
        if (sender != null) {
            return sender.getDisplayName();
        } else if (receiver != null) {
            return receiver.getDisplayName();
        } else {
            return "Unknown User";
        }
    }
    
    /**
     * Get the profile to display for this request
     */
    public UserProfile getProfile() {
        return sender != null ? sender : receiver;
    }
    
    /**
     * Is this a received request
     */
    public boolean isReceived() {
        return sender != null;
    }
    
    /**
     * Is this a sent request
     */
    public boolean isSent() {
        return receiver != null;
    }
} 