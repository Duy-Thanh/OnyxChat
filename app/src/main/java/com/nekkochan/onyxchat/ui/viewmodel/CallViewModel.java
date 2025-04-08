package com.nekkochan.onyxchat.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.nekkochan.onyxchat.network.WebSocketClient;

public class CallViewModel extends ViewModel {
    private final MutableLiveData<CallState> callState = new MutableLiveData<>(CallState.IDLE);
    private final MutableLiveData<Boolean> isMuted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSpeakerOn = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isVideoEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private final WebSocketClient webSocketClient;

    public CallViewModel(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }

    public enum CallState {
        IDLE,
        DIALING,
        RINGING,
        CONNECTED,
        ENDED,
        BUSY,
        TIMEOUT
    }

    public void startCall(String recipientId, boolean isVideoCall) {
        callState.postValue(CallState.DIALING);
        webSocketClient.sendCallRequest(recipientId, isVideoCall);
    }

    public void acceptCall(String callerId) {
        callState.postValue(CallState.CONNECTED);
        webSocketClient.sendCallResponse(callerId, true);
    }

    public void rejectCall(String callerId) {
        callState.postValue(CallState.ENDED);
        webSocketClient.sendCallResponse(callerId, false);
    }

    public void endCall(String recipientId) {
        callState.postValue(CallState.ENDED);
        webSocketClient.sendEndCall(recipientId);
    }

    public void toggleMute() {
        boolean currentMuteState = isMuted.getValue() != null ? isMuted.getValue() : false;
        isMuted.postValue(!currentMuteState);
    }

    public void toggleSpeaker() {
        boolean currentSpeakerState = isSpeakerOn.getValue() != null ? isSpeakerOn.getValue() : false;
        isSpeakerOn.postValue(!currentSpeakerState);
    }

    public void toggleVideo() {
        boolean currentVideoState = isVideoEnabled.getValue() != null ? isVideoEnabled.getValue() : false;
        isVideoEnabled.postValue(!currentVideoState);
    }

    public LiveData<CallState> getCallState() {
        return callState;
    }

    public LiveData<Boolean> getIsMuted() {
        return isMuted;
    }

    public LiveData<Boolean> getIsSpeakerOn() {
        return isSpeakerOn;
    }

    public LiveData<Boolean> getIsVideoEnabled() {
        return isVideoEnabled;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Clean up resources if needed
    }
} 