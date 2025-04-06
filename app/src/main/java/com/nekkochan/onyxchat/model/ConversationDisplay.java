package com.nekkochan.onyxchat.model;

import java.util.Date;

/**
 * Model class representing a conversation for display purposes only.
 * This is not a Room entity, just a UI model.
 */
public class ConversationDisplay {
    private final String participantId;
    private final String email;
    private final String displayName;
    private final String lastMessage;
    private final Date lastMessageTime;
    private final int unreadCount;
    private final boolean isOnline;

    public ConversationDisplay(String participantId, String email, String displayName, String lastMessage, 
                        Date lastMessageTime, int unreadCount, boolean isOnline) {
        this.participantId = participantId;
        this.email = email;
        this.displayName = displayName;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.unreadCount = unreadCount;
        this.isOnline = isOnline;
    }

    public String getParticipantId() {
        return participantId;
    }
    
    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName != null && !displayName.isEmpty() ? displayName : participantId;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public Date getLastMessageTime() {
        return lastMessageTime;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public boolean isOnline() {
        return isOnline;
    }
} 