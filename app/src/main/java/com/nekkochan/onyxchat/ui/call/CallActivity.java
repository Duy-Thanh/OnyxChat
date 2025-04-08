package com.nekkochan.onyxchat.ui.call;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.model.Contact;
import com.nekkochan.onyxchat.network.WebSocketClient;
import com.nekkochan.onyxchat.utils.EmojiUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SdpObserver;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class CallActivity extends AppCompatActivity {
    private static final String TAG = "CallActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    // Video constants
    private static final int VIDEO_RESOLUTION_WIDTH = 1280;
    private static final int VIDEO_RESOLUTION_HEIGHT = 720;
    private static final int VIDEO_FPS = 30;

    private boolean isVideoCall;
    private String contactId;
    private String contactName;
    private boolean isCaller;
    private boolean isMuted = false;
    private boolean isSpeakerOn = false;
    private boolean isVideoEnabled = true;

    // WebRTC components
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase.Context eglBaseContext;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private PeerConnection peerConnection;
    private List<PeerConnection.IceServer> iceServers = new ArrayList<>();
    private VideoCapturer videoCapturer;
    private VideoTrack remoteVideoTrack;

    // UI components
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private ImageButton endCallButton;
    private ImageButton muteButton;
    private ImageButton speakerButton;
    private ImageButton videoButton;
    private TextView contactNameText;
    private TextView callStatusText;

    // State
    private String recipientId;
    private WebSocketClient webSocketClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Get call parameters from intent
        isVideoCall = getIntent().getBooleanExtra("is_video_call", false);
        contactId = getIntent().getStringExtra("contact_id");
        contactName = getIntent().getStringExtra("contact_name");
        isCaller = getIntent().getBooleanExtra("is_caller", false);

        // Initialize UI components
        initializeViews();

        // Check and request permissions
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            initializeWebRTC();
        }
    }

    private void initializeViews() {
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        endCallButton = findViewById(R.id.end_call_button);
        muteButton = findViewById(R.id.mute_button);
        speakerButton = findViewById(R.id.speaker_button);
        videoButton = findViewById(R.id.video_button);
        contactNameText = findViewById(R.id.contact_name_text);
        callStatusText = findViewById(R.id.call_status_text);

        // Set contact name
        contactNameText.setText(contactName);

        // Set up button listeners
        endCallButton.setOnClickListener(v -> endCall());
        muteButton.setOnClickListener(v -> toggleMute());
        speakerButton.setOnClickListener(v -> toggleSpeaker());
        videoButton.setOnClickListener(v -> toggleVideo());

        // Hide video views if it's a voice call
        if (!isVideoCall) {
            localVideoView.setVisibility(View.GONE);
            remoteVideoView.setVisibility(View.GONE);
            videoButton.setVisibility(View.GONE);
        }
    }

    private void initializeWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        );

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory();

        // Create video capturer
        videoCapturer = createVideoCapturer();
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to create video capturer");
            return;
        }

        // Create video source
        videoSource = peerConnectionFactory.createVideoSource(false);
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", EglBase.create().getEglBaseContext());
        videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, VIDEO_FPS);

        // Create video track
        localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource);

        // Create audio source and track
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource);

        // Initialize peer connection
        initializePeerConnection();
    }

    private void initializePeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    runOnUiThread(() -> {
                        Toast.makeText(CallActivity.this, "Call disconnected", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {
                Log.d(TAG, "onIceConnectionReceivingChange: " + b);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "onIceCandidate");
                sendIceCandidate(iceCandidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved");
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "onAddStream");
                runOnUiThread(() -> {
                    if (mediaStream.videoTracks.size() > 0) {
                        remoteVideoTrack = mediaStream.videoTracks.get(0);
                        remoteVideoTrack.addSink(remoteVideoView);
                    }
                });
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onRemoveStream");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel");
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded");
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "onAddTrack");
            }
        });

        // Add local tracks
        peerConnection.addTrack(localAudioTrack);
        peerConnection.addTrack(localVideoTrack);

        // Create and send offer
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new org.webrtc.SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new org.webrtc.SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        Log.d(TAG, "Local description set successfully");
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully");
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        Log.e(TAG, "Failed to create local description: " + s);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set local description: " + s);
                    }
                }, sessionDescription);
                sendOffer(sessionDescription);
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Offer set successfully");
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create offer: " + s);
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Failed to set offer: " + s);
            }
        }, constraints);
    }

    private void sendIceCandidate(IceCandidate iceCandidate) {
        // Implement WebSocket message sending for ICE candidates
        try {
            JSONObject message = new JSONObject();
            message.put("type", "ice_candidate");
            message.put("candidate", iceCandidate.sdp);
            message.put("sdpMid", iceCandidate.sdpMid);
            message.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            message.put("to", recipientId);
            webSocketClient.sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending ICE candidate: " + e.getMessage());
        }
    }

    private void sendOffer(SessionDescription sessionDescription) {
        // Implement WebSocket message sending for offer
        try {
            JSONObject message = new JSONObject();
            message.put("type", "offer");
            message.put("sdp", sessionDescription.description);
            message.put("to", recipientId);
            webSocketClient.sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending offer: " + e.getMessage());
        }
    }

    private void handleWebSocketMessage(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);
            String type = jsonMessage.getString("type");
            JSONObject data = jsonMessage.getJSONObject("data");

            switch (type) {
                case "call_busy":
                    runOnUiThread(() -> {
                        Toast.makeText(this, "User is busy in another call", Toast.LENGTH_LONG).show();
                        endCall();
                    });
                    break;

                case "call_timeout":
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Call timed out", Toast.LENGTH_LONG).show();
                        endCall();
                    });
                    break;

                case "call_ended":
                    runOnUiThread(this::endCall);
                    break;

                case "offer":
                    handleOffer(data);
                    break;

                case "answer":
                    handleAnswer(data);
                    break;

                case "ice_candidate":
                    handleIceCandidate(data);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing WebSocket message: " + e.getMessage());
        }
    }

    private void handleOffer(JSONObject data) throws JSONException {
        String sdp = data.getString("sdp");
        String from = data.getString("from");
        
        // Create remote description from offer
        SessionDescription offer = new SessionDescription(
            SessionDescription.Type.OFFER,
            sdp
        );
        
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Remote description set successfully");
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote description set successfully");
                // Create and send answer
                peerConnection.createAnswer(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        peerConnection.setLocalDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {
                                Log.d(TAG, "Local description set successfully");
                            }

                            @Override
                            public void onSetSuccess() {
                                Log.d(TAG, "Local description set successfully");
                                // Send answer to peer
                                try {
                                    JSONObject message = new JSONObject();
                                    message.put("type", "answer");
                                    JSONObject data = new JSONObject();
                                    data.put("sdp", sessionDescription.description);
                                    data.put("to", from);
                                    message.put("data", data);
                                    webSocketClient.sendMessage(message.toString());
                                } catch (JSONException e) {
                                    Log.e(TAG, "Error sending answer: " + e.getMessage());
                                }
                            }

                            @Override
                            public void onCreateFailure(String s) {
                                Log.e(TAG, "Error creating local description: " + s);
                            }

                            @Override
                            public void onSetFailure(String s) {
                                Log.e(TAG, "Error setting local description: " + s);
                            }
                        }, sessionDescription);
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Answer created successfully");
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        Log.e(TAG, "Error creating answer: " + s);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Error setting answer: " + s);
                    }
                }, new MediaConstraints());
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Error creating remote description: " + s);
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Error setting remote description: " + s);
            }
        }, offer);
    }

    private void handleAnswer(JSONObject data) throws JSONException {
        String sdp = data.getString("sdp");
        
        // Create remote description from answer
        SessionDescription answer = new SessionDescription(
            SessionDescription.Type.ANSWER,
            sdp
        );
        
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Remote description set successfully");
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote description set successfully");
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Error creating remote description: " + s);
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Error setting remote description: " + s);
            }
        }, answer);
    }

    private void handleIceCandidate(JSONObject data) throws JSONException {
        String candidate = data.getString("candidate");
        String sdpMid = data.getString("sdpMid");
        int sdpMLineIndex = data.getInt("sdpMLineIndex");
        
        IceCandidate iceCandidate = new IceCandidate(
            sdpMid,
            sdpMLineIndex,
            candidate
        );
        
        peerConnection.addIceCandidate(iceCandidate);
    }

    private void endCall() {
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        
        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }
        
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping video capture: " + e.getMessage());
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        
        // Notify peer that call has ended
        try {
            JSONObject message = new JSONObject();
            message.put("type", "end_call");
            JSONObject data = new JSONObject();
            data.put("to", recipientId);
            message.put("data", data);
            webSocketClient.sendMessage(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending end call message: " + e.getMessage());
        }
        
        finish();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!isMuted);
        }
        muteButton.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        // Implement speaker toggle logic
        speakerButton.setImageResource(isSpeakerOn ? R.drawable.ic_speaker : R.drawable.ic_speaker_off);
    }

    private void toggleVideo() {
        isVideoEnabled = !isVideoEnabled;
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(isVideoEnabled);
        }
        videoButton.setImageResource(isVideoEnabled ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);
    }

    private boolean hasPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasPermissions()) {
                initializeWebRTC();
            } else {
                Toast.makeText(this, "Permissions required for call", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (peerConnection != null) {
            peerConnection.close();
        }
        if (localVideoView != null) {
            localVideoView.release();
        }
        if (remoteVideoView != null) {
            remoteVideoView.release();
        }
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer;
        if (Camera2Enumerator.isSupported(this)) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }
} 