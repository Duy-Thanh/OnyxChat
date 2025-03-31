package com.nekkochan.onyxchat.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity representing a message in the SecureComm app
 */
@Entity(tableName = "messages",
        indices = {
            @Index("senderId"),
            @Index("recipientId"),
            @Index("timestamp")
        })
public class Message {
    
    @PrimaryKey
    @NonNull
    private String id; // Unique message identifier
    
    @NonNull
    private String senderId; // Onion address of the sender
    
    @NonNull
    private String recipientId; // Onion address of the recipient
    
    @NonNull
    private String encryptedContent; // Post-quantum encrypted message content
    
    private String mediaUrl; // URL or local path to media (if any)
    
    private int mediaType; // 0 = none, 1 = image, 2 = video, 3 = audio, 4 = file
    
    private long timestamp; // Message creation timestamp
    
    private long expirationTime; // Self-destruct timestamp (if enabled)
    
    private boolean isRead; // Whether message has been read
    
    private boolean isDeleted; // Soft deletion flag
    
    private boolean isSent; // Message delivery status
    
    private String replyToMessageId; // ID of message being replied to (if any)
    
    private String conversationId; // ID of the conversation this message belongs to
    
    private boolean isSelf; // Whether this message was sent by the user
    
    private boolean isEncrypted; // Whether this message is encrypted

    // Default constructor required by Room
    public Message() {
    }

    @Ignore
    public Message(@NonNull String id, @NonNull String senderId, @NonNull String recipientId, 
                   @NonNull String encryptedContent) {
        this.id = id;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.encryptedContent = encryptedContent;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.isDeleted = false;
        this.isSent = false;
    }
    
    /**
     * Constructor used in Repository.sendMessage
     */
    @Ignore
    public Message(@NonNull String id, @NonNull String content, @NonNull String senderAddress, 
                  @NonNull String receiverAddress, String conversationId, boolean isSelf, boolean isEncrypted) {
        this.id = id;
        this.encryptedContent = content;
        this.senderId = senderAddress;
        this.recipientId = receiverAddress;
        this.conversationId = conversationId;
        this.isSelf = isSelf;
        this.isEncrypted = isEncrypted;
        this.timestamp = System.currentTimeMillis();
        this.isRead = isSelf; // Messages sent by self are automatically marked as read
        this.isDeleted = false;
        this.isSent = false;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(@NonNull String senderId) {
        this.senderId = senderId;
    }

    @NonNull
    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(@NonNull String recipientId) {
        this.recipientId = recipientId;
    }

    @NonNull
    public String getEncryptedContent() {
        return encryptedContent;
    }

    public void setEncryptedContent(@NonNull String encryptedContent) {
        this.encryptedContent = encryptedContent;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public boolean isSent() {
        return isSent;
    }

    public void setSent(boolean sent) {
        isSent = sent;
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }
    
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public boolean isSelf() {
        return isSelf;
    }

    public void setSelf(boolean self) {
        isSelf = self;
    }

    public boolean isEncrypted() {
        return isEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
    }
} 