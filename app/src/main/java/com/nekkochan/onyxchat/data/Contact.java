package com.nekkochan.onyxchat.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity representing a contact in the user's contact list
 */
@Entity(tableName = "contacts",
        foreignKeys = {
                @ForeignKey(
                        entity = User.class,
                        parentColumns = "onionAddress",
                        childColumns = "contactAddress",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = User.class,
                        parentColumns = "onionAddress",
                        childColumns = "ownerAddress",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("contactAddress"),
                @Index("ownerAddress")
        }
)
public class Contact {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    @NonNull
    private String ownerAddress; // The onion address of the user who owns this contact
    
    @NonNull
    private String contactAddress; // The onion address of the contact
    
    private String nickName; // Optional nickname for the contact
    
    private boolean isBlocked; // Whether contact is blocked
    
    private boolean isVerified; // Whether contact has been verified face-to-face
    
    private long createdAt; // When contact was added
    
    private long lastInteractionTime; // Last time user interacted with this contact

    // Default constructor required by Room
    public Contact() {
    }

    @Ignore
    public Contact(@NonNull String ownerAddress, @NonNull String contactAddress, String nickName) {
        this.ownerAddress = ownerAddress;
        this.contactAddress = contactAddress;
        this.nickName = nickName;
        this.isBlocked = false;
        this.isVerified = false;
        this.createdAt = System.currentTimeMillis();
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getOwnerAddress() {
        return ownerAddress;
    }

    public void setOwnerAddress(@NonNull String ownerAddress) {
        this.ownerAddress = ownerAddress;
    }

    @NonNull
    public String getContactAddress() {
        return contactAddress;
    }

    public void setContactAddress(@NonNull String contactAddress) {
        this.contactAddress = contactAddress;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastInteractionTime() {
        return lastInteractionTime;
    }

    public void setLastInteractionTime(long lastInteractionTime) {
        this.lastInteractionTime = lastInteractionTime;
    }
} 