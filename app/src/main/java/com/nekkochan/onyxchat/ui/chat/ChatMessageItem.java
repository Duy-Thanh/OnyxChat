package com.nekkochan.onyxchat.ui.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Date;

/**
 * Data class for chat messages
 */
public class ChatMessageItem {

    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        DOCUMENT,
        SYSTEM
    }
    
    public enum MessageStatus {
        SENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED
    }
    
    private String id;
    private String senderId;
    private String recipientId;
    private String content;
    private MessageType messageType;
    private MessageStatus status;
    private Date timestamp;
    private boolean outgoing;
    
    // Media-specific fields
    private String mediaUrl;
    private String fileName;
    private String fileCaption;
    
    /**
     * Constructor for text messages
     */
    public ChatMessageItem(String id, String senderId, String recipientId, 
                         String content, MessageType messageType,
                         MessageStatus status, Date timestamp, boolean outgoing) {
        this.id = id;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.content = content;
        this.messageType = messageType;
        this.status = status;
        this.timestamp = timestamp;
        this.outgoing = outgoing;
    }
    
    /**
     * Constructor for media messages
     */
    public ChatMessageItem(String id, String senderId, String recipientId, 
                         String content, MessageType messageType,
                         MessageStatus status, Date timestamp, boolean outgoing,
                         String mediaUrl, String fileName, String fileCaption) {
        this(id, senderId, recipientId, content, messageType, status, timestamp, outgoing);
        this.mediaUrl = mediaUrl;
        this.fileName = fileName;
        this.fileCaption = fileCaption;
    }
    
    /**
     * Parse media content JSON
     */
    public static ChatMessageItem parseMediaMessage(String id, String senderId, String recipientId,
                                                  String contentJson, MessageStatus status, 
                                                  Date timestamp, boolean outgoing) {
        try {
            JsonObject jsonObject = JsonParser.parseString(contentJson).getAsJsonObject();
            
            String url = jsonObject.has("url") ? jsonObject.get("url").getAsString() : "";
            String filename = jsonObject.has("filename") ? jsonObject.get("filename").getAsString() : "";
            String caption = jsonObject.has("caption") ? jsonObject.get("caption").getAsString() : "";
            String typeStr = jsonObject.has("type") ? jsonObject.get("type").getAsString() : "TEXT";
            
            MessageType messageType = MessageType.TEXT;
            try {
                messageType = MessageType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                // Default to TEXT if type is invalid
            }
            
            return new ChatMessageItem(id, senderId, recipientId, caption, messageType, 
                                    status, timestamp, outgoing, url, filename, caption);
        } catch (Exception e) {
            // If parsing fails, create a text message
            return new ChatMessageItem(id, senderId, recipientId, contentJson, 
                                    MessageType.TEXT, status, timestamp, outgoing);
        }
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public String getRecipientId() {
        return recipientId;
    }
    
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public MessageType getMessageType() {
        return messageType;
    }
    
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }
    
    public MessageStatus getStatus() {
        return status;
    }
    
    public void setStatus(MessageStatus status) {
        this.status = status;
    }
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isOutgoing() {
        return outgoing;
    }
    
    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }
    
    public String getMediaUrl() {
        return mediaUrl;
    }
    
    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getFileCaption() {
        return fileCaption;
    }
    
    public void setFileCaption(String fileCaption) {
        this.fileCaption = fileCaption;
    }
} 