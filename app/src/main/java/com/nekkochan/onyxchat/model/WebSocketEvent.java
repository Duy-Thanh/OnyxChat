package com.nekkochan.onyxchat.model;

public class WebSocketEvent {
    private final WebSocketEventType type;
    private final String data;

    public WebSocketEvent(WebSocketEventType type, String data) {
        this.type = type;
        this.data = data;
    }

    public WebSocketEventType getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public enum WebSocketEventType {
        USER_JOINED,
        USER_LEFT,
        MESSAGE_RECEIVED,
        DIRECT_MESSAGE,
        CONNECT,
        DISCONNECT,
        ERROR,
        USER_STATUS_CHANGE,
        CALL_REQUEST,
        CALL_RESPONSE,
        CALL_ENDED,
        CALL_BUSY,
        CALL_TIMEOUT
    }
}