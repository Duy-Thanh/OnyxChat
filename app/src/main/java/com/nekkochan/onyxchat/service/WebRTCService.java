package com.nekkochan.onyxchat.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.nekkochan.onyxchat.network.WebSocketClient;
import com.nekkochan.onyxchat.network.WebSocketClient.WebSocketEventType;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Service to manage WebRTC signaling
 */
public class WebRTCService extends Service implements WebSocketClient.MessageListener {
    private static final String TAG = "WebRTCService";
    
    private WebSocketClient webSocketClient;
    private LocalBroadcastManager broadcastManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebRTC service created");
        
        webSocketClient = new WebSocketClient(getApplicationContext());
        webSocketClient.addListener(this);
        
        broadcastManager = LocalBroadcastManager.getInstance(this);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WebRTC service started");
        
        if (intent != null && intent.getAction() != null) {
            handleIntent(intent);
        }
        
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WebRTC service destroyed");
        
        if (webSocketClient != null) {
            webSocketClient.removeListener(this);
        }
    }
    
    @Override
    public void onStateChanged(WebSocketClient.WebSocketState state) {
        // Not used
    }
    
    @Override
    public void onMessageReceived(String message) {
        Log.d(TAG, "WebSocket message received: " + message);
        try {
            JSONObject jsonObject = new JSONObject(message);
            String type = jsonObject.getString("type");
            
            switch (type) {
                case "incoming_call":
                    handleIncomingCall(jsonObject);
                    break;
                case "call_answered":
                    handleCallAnswered(jsonObject);
                    break;
                case "call_busy":
                    handleCallBusy(jsonObject);
                    break;
                case "call_timeout":
                    handleCallTimeout(jsonObject);
                    break;
                case "call_ended":
                    handleCallEnded(jsonObject);
                    break;
                case "ice_candidate":
                    handleIceCandidate(jsonObject);
                    break;
                case "offer":
                    handleOffer(jsonObject);
                    break;
                case "answer":
                    handleAnswer(jsonObject);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing message: " + e.getMessage());
        }
    }
    
    @Override
    public void onError(String error) {
        Log.e(TAG, "WebSocket error: " + error);
    }
    
    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        
        Log.d(TAG, "Handling intent with action: " + action);
        
        switch (action) {
            case "SEND_CALL_REQUEST":
                sendCallRequest(
                        intent.getStringExtra("recipientId"),
                        intent.getBooleanExtra("isVideoCall", false)
                );
                break;
            case "SEND_CALL_RESPONSE":
                sendCallResponse(
                        intent.getStringExtra("callerId"),
                        intent.getBooleanExtra("accepted", false)
                );
                break;
            case "SEND_OFFER":
                sendOffer(
                        intent.getStringExtra("recipientId"),
                        intent.getStringExtra("sdp")
                );
                break;
            case "SEND_ANSWER":
                sendAnswer(
                        intent.getStringExtra("recipientId"),
                        intent.getStringExtra("sdp")
                );
                break;
            case "SEND_ICE_CANDIDATE":
                sendIceCandidate(
                        intent.getStringExtra("recipientId"),
                        intent.getStringExtra("candidate"),
                        intent.getStringExtra("sdpMid"),
                        intent.getIntExtra("sdpMLineIndex", 0)
                );
                break;
            case "END_CALL":
                sendEndCall(intent.getStringExtra("recipientId"));
                break;
        }
    }
    
    private void handleIncomingCall(JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        String callerId = data.getString("callerId");
        boolean isVideoCall = data.getBoolean("isVideoCall");
        
        Intent intent = new Intent("INCOMING_CALL");
        intent.putExtra("callerId", callerId);
        intent.putExtra("isVideoCall", isVideoCall);
        broadcastManager.sendBroadcast(intent);
    }
    
    private void handleCallAnswered(JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        boolean accepted = data.getBoolean("accepted");
        String recipientId = data.getString("recipientId");
        
        Intent intent = new Intent(accepted ? "CALL_ACCEPTED" : "CALL_REJECTED");
        intent.putExtra("recipientId", recipientId);
        broadcastManager.sendBroadcast(intent);
    }
    
    private void handleCallBusy(JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        String recipientId = data.getString("recipientId");
        
        Intent intent = new Intent("CALL_BUSY");
        intent.putExtra("recipientId", recipientId);
        broadcastManager.sendBroadcast(intent);
    }
    
    private void handleCallTimeout(JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        String userId = data.has("callerId") ? data.getString("callerId") : data.getString("recipientId");
        
        Intent intent = new Intent("CALL_TIMEOUT");
        intent.putExtra("userId", userId);
        broadcastManager.sendBroadcast(intent);
    }
    
    private void handleCallEnded(JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        String fromUserId = data.getString("from");
        
        Intent intent = new Intent("CALL_ENDED");
        intent.putExtra("fromUserId", fromUserId);
        broadcastManager.sendBroadcast(intent);
    }
    
    private void handleIceCandidate(JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        String candidate = data.getString("candidate");
        String sdpMid = data.getString("sdpMid");
        int sdpMLineIndex = data.getInt("sdpMLineIndex");
        
        Intent intent = new Intent("ICE_CANDIDATE");
        intent.putExtra("candidate", candidate);
        intent.putExtra("sdpMid", sdpMid);
        intent.putExtra("sdpMLineIndex", sdpMLineIndex);
        broadcastManager.sendBroadcast(intent);
    }
    
    private void handleOffer(JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        String sdp = data.getString("sdp");
        
        Intent intent = new Intent("OFFER");
        intent.putExtra("sdp", sdp);
        broadcastManager.sendBroadcast(intent);
    }
    
    private void handleAnswer(JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        String sdp = data.getString("sdp");
        
        Intent intent = new Intent("ANSWER");
        intent.putExtra("sdp", sdp);
        broadcastManager.sendBroadcast(intent);
    }
    
    private void sendCallRequest(String recipientId, boolean isVideoCall) {
        Log.d(TAG, "Sending call request to: " + recipientId + ", video: " + isVideoCall);
        webSocketClient.sendCallRequest(recipientId, isVideoCall);
    }
    
    private void sendCallResponse(String callerId, boolean accepted) {
        Log.d(TAG, "Sending call response to: " + callerId + ", accepted: " + accepted);
        webSocketClient.sendCallResponse(callerId, accepted);
    }
    
    private void sendOffer(String recipientId, String sdp) {
        Log.d(TAG, "Sending offer to: " + recipientId);
        webSocketClient.sendOffer(recipientId, sdp);
    }
    
    private void sendAnswer(String recipientId, String sdp) {
        Log.d(TAG, "Sending answer to: " + recipientId);
        webSocketClient.sendAnswer(recipientId, sdp);
    }
    
    private void sendIceCandidate(String recipientId, String candidate, String sdpMid, int sdpMLineIndex) {
        Log.d(TAG, "Sending ICE candidate to: " + recipientId);
        webSocketClient.sendIceCandidate(recipientId, candidate, sdpMid, sdpMLineIndex);
    }
    
    private void sendEndCall(String recipientId) {
        Log.d(TAG, "Sending end call to: " + recipientId);
        webSocketClient.sendEndCall(recipientId);
    }
} 