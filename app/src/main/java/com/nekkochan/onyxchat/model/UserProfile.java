package com.nekkochan.onyxchat.model;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Date;

/**
 * Represents a user profile in the application
 */
public class UserProfile implements Serializable {
    
    public enum FriendStatus {
        NONE,       // Not a friend and no pending requests
        CONTACT,    // Already a contact/friend
        SENT,       // Sent a friend request to this user
        RECEIVED    // Received a friend request from this user
    }
    
    @SerializedName("id")
    private String id;
    
    @SerializedName("username")
    private String username;
    
    @SerializedName("displayName")
    private String displayName;
    
    @SerializedName("isActive")
    private boolean isActive;
    
    @SerializedName("lastActiveAt")
    private Date lastActiveAt;
    
    @SerializedName("friendStatus")
    private String friendStatus;
    
    public UserProfile(String id, String username, String displayName, boolean isActive, Date lastActiveAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.isActive = isActive;
        this.lastActiveAt = lastActiveAt;
        this.friendStatus = "none";
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getDisplayName() {
        return displayName != null && !displayName.isEmpty() ? displayName : username;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public Date getLastActiveAt() {
        return lastActiveAt;
    }
    
    public void setLastActiveAt(Date lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
    
    public FriendStatus getFriendStatus() {
        if (friendStatus == null) {
            return FriendStatus.NONE;
        }
        
        switch (friendStatus.toLowerCase()) {
            case "contact":
                return FriendStatus.CONTACT;
            case "sent":
                return FriendStatus.SENT;
            case "received":
                return FriendStatus.RECEIVED;
            default:
                return FriendStatus.NONE;
        }
    }
    
    public void setFriendStatus(FriendStatus status) {
        switch (status) {
            case CONTACT:
                this.friendStatus = "contact";
                break;
            case SENT:
                this.friendStatus = "sent";
                break;
            case RECEIVED:
                this.friendStatus = "received";
                break;
            default:
                this.friendStatus = "none";
                break;
        }
    }
    
    @NonNull
    @Override
    public String toString() {
        return getDisplayName();
    }
} 