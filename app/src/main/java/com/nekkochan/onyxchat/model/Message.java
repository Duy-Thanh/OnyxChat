package com.nekkochan.onyxchat.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

/**
 * Entity representing a message in the OnyxChat application.
 */
@Entity(
    tableName = "messages",
    indices = {
        @Index("senderAddress"),
        @Index("receiverAddress"),
        @Index("conversationId")
    },
    foreignKeys = {
        @ForeignKey(
            entity = Contact.class,
            parentColumns = "onionAddress",
            childColumns = "senderAddress",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = Contact.class,
            parentColumns = "onionAddress",
            childColumns = "receiverAddress",
            onDelete = ForeignKey.CASCADE
        )
    }
)
public class Message {
    @PrimaryKey
    @NonNull
    private String id;
    
    private String content;
    private String senderAddress;
    private String receiverAddress;
    private String conversationId;
    private long timestamp;
    private boolean isRead;
    private boolean isDelivered;
    private boolean isSent;
    private boolean isSelf;
    private String encryptionInfo;
    private boolean isEncrypted;
    private long selfDestructTime; // Time in milliseconds after which the message will be deleted (0 = never)

    /**
     * Default constructor required by Room
     */
    public Message() {
    }

    /**
     * Constructor for creating a new message
     * 
     * @param id The message's unique identifier
     * @param content The message content
     * @param senderAddress The sender's onion address
     * @param receiverAddress The receiver's onion address
     * @param conversationId The conversation ID
     * @param isSelf Whether this message was sent by the user
     * @param isEncrypted Whether this message is encrypted
     */
    @Ignore
    public Message(@NonNull String id, String content, String senderAddress, String receiverAddress, 
                   String conversationId, boolean isSelf, boolean isEncrypted) {
        this.id = id;
        this.content = content;
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
        this.conversationId = conversationId;
        this.timestamp = System.currentTimeMillis();
        this.isRead = isSelf; // Messages sent by self are automatically marked as read
        this.isDelivered = false;
        this.isSent = isSelf; // Messages sent by self are automatically marked as sent
        this.isSelf = isSelf;
        this.isEncrypted = isEncrypted;
        this.selfDestructTime = 0; // Default: never self-destruct
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public boolean isDelivered() {
        return isDelivered;
    }

    public void setDelivered(boolean delivered) {
        isDelivered = delivered;
    }

    public boolean isSent() {
        return isSent;
    }

    public void setSent(boolean sent) {
        isSent = sent;
    }

    public boolean isSelf() {
        return isSelf;
    }

    public void setSelf(boolean self) {
        isSelf = self;
    }

    public String getEncryptionInfo() {
        return encryptionInfo;
    }

    public void setEncryptionInfo(String encryptionInfo) {
        this.encryptionInfo = encryptionInfo;
    }

    public boolean isEncrypted() {
        return isEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
    }

    public long getSelfDestructTime() {
        return selfDestructTime;
    }

    public void setSelfDestructTime(long selfDestructTime) {
        this.selfDestructTime = selfDestructTime;
    }

    /**
     * Check if the message should be self-destructed
     * 
     * @return true if the message should be deleted based on self-destruct time
     */
    public boolean shouldSelfDestruct() {
        if (selfDestructTime == 0) {
            return false; // Never self-destruct
        }
        return System.currentTimeMillis() >= timestamp + selfDestructTime;
    }

    /**
     * Set the message to self-destruct after a specified time
     * 
     * @param timeInMillis Time in milliseconds after which the message will be deleted
     */
    public void setSelfDestructAfter(long timeInMillis) {
        this.selfDestructTime = timeInMillis;
    }
} 