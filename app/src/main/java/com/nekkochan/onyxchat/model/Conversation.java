package com.nekkochan.onyxchat.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity representing a conversation in the OnyxChat application.
 * A conversation is a collection of messages between the user and a contact.
 */
@Entity(
    tableName = "conversations",
    indices = {
        @Index("contactAddress")
    },
    foreignKeys = {
        @ForeignKey(
            entity = Contact.class,
            parentColumns = "onionAddress",
            childColumns = "contactAddress",
            onDelete = ForeignKey.CASCADE
        )
    }
)
public class Conversation {
    @PrimaryKey
    @NonNull
    private String id;
    
    private String contactAddress;
    private long createdTimestamp;
    private long lastMessageTimestamp;
    private String lastMessageContent;
    private boolean isEncrypted;
    private boolean isArchived;
    private boolean isPinned;
    private int unreadCount;

    /**
     * Default constructor required by Room
     */
    public Conversation() {
    }

    /**
     * Constructor for creating a new conversation
     * 
     * @param id The conversation's unique identifier
     * @param contactAddress The contact's onion address
     * @param isEncrypted Whether this conversation is encrypted
     */
    public Conversation(@NonNull String id, String contactAddress, boolean isEncrypted) {
        this.id = id;
        this.contactAddress = contactAddress;
        this.createdTimestamp = System.currentTimeMillis();
        this.lastMessageTimestamp = this.createdTimestamp;
        this.lastMessageContent = "";
        this.isEncrypted = isEncrypted;
        this.isArchived = false;
        this.isPinned = false;
        this.unreadCount = 0;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getContactAddress() {
        return contactAddress;
    }

    public void setContactAddress(String contactAddress) {
        this.contactAddress = contactAddress;
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public long getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public void setLastMessageTimestamp(long lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public String getLastMessageContent() {
        return lastMessageContent;
    }

    public void setLastMessageContent(String lastMessageContent) {
        this.lastMessageContent = lastMessageContent;
    }

    public boolean isEncrypted() {
        return isEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public void setArchived(boolean archived) {
        isArchived = archived;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    /**
     * Increment the unread count when a new message is received
     */
    public void incrementUnreadCount() {
        this.unreadCount++;
    }

    /**
     * Clear the unread count when conversation is read
     */
    public void clearUnreadCount() {
        this.unreadCount = 0;
    }

    /**
     * Update the conversation with new message information
     * 
     * @param message The new message
     * @param incrementUnread Whether to increment unread count
     */
    public void updateWithMessage(Message message, boolean incrementUnread) {
        this.lastMessageTimestamp = message.getTimestamp();
        this.lastMessageContent = message.getContent();
        
        if (incrementUnread && !message.isSelf()) {
            incrementUnreadCount();
        }
    }
}