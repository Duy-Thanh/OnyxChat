package com.nekkochan.onyxchat.ui.call;

import org.webrtc.SessionDescription;

public interface SdpObserver {
    void onCreateSuccess(SessionDescription sessionDescription);
    void onSetSuccess();
    void onCreateFailure(String error);
    void onSetFailure(String error);
}