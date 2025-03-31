package com.nekkochan.onyxchat.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity representing a user in the SecureComm app
 */
@Entity(tableName = "users")
public class User {
    
    @PrimaryKey
    @NonNull
    private String onionAddress; // Tor onion address used as unique identifier
    
    private String displayName;
    private String profilePicture; // Path to profile image or base64 encoded image
    private String publicKey; // Post-quantum public key for encryption
    private long lastSeen;
    private boolean isCurrentUser; // Flag to mark current user

    // Default constructor required by Room
    public User() {
    }

    public User(@NonNull String onionAddress, String displayName, String publicKey) {
        this.onionAddress = onionAddress;
        this.displayName = displayName;
        this.publicKey = publicKey;
        this.lastSeen = System.currentTimeMillis();
    }

    @NonNull
    public String getOnionAddress() {
        return onionAddress;
    }

    public void setOnionAddress(@NonNull String onionAddress) {
        this.onionAddress = onionAddress;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isCurrentUser() {
        return isCurrentUser;
    }

    public void setCurrentUser(boolean currentUser) {
        isCurrentUser = currentUser;
    }
} 