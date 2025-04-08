package com.nekkochan.onyxchat.ui.call;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
    private static final int VIDEO_CAPTURE_PERMISSION_REQUEST_CODE = 1;
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 2;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 3;
    
    // Video constants
    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_FPS = 30;
    
    private static final String VIDEO_TRACK_ID = "ARDAMSv0";
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final String LOCAL_VIDEO_STREAM_ID = "ARDAMSv0";
    private static final String LOCAL_AUDIO_STREAM_ID = "ARDAMSa0";
    
    private String currentRecipientId;
    private boolean isVideoCall;
    private boolean isCaller;
    private boolean isCallActive = false;
    private boolean isMuted = false;
    private boolean isSpeakerOn = true;
    private boolean isCameraOn = true;
    
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private ImageButton toggleAudioButton;
    private ImageButton toggleSpeakerButton;
    private ImageButton toggleCameraButton;
    private ImageButton endCallButton;
    private TextView callStatusText;
    private TextView callDurationText;
    private TextView recipientNameText;
    private ImageView recipientAvatarImage;
    
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private MediaStream localStream;
    private VideoCapturer videoCapturer;
    private EglBase rootEglBase;
    private SurfaceTextureHelper surfaceTextureHelper;
    
    private final List<IceCandidate> queuedRemoteCandidates = new ArrayList<>();
    private SessionDescription localSdp;
    private SessionDescription remoteSdp;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private long callStartTime;
    private Runnable updateCallDurationRunnable;
    
    private BroadcastReceiver callStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            
            switch (action) {
                case "CALL_ACCEPTED":
                    handleCallAccepted();
                    break;
                case "CALL_REJECTED":
                    handleCallRejected();
                    break;
                case "CALL_ENDED":
                    handleCallEnded();
                    break;
                case "CALL_BUSY":
                    handleCallBusy();
                    break;
                case "CALL_TIMEOUT":
                    handleCallTimeout();
                    break;
                case "ICE_CANDIDATE":
                    handleIceCandidate(intent.getStringExtra("candidate"),
                                     intent.getStringExtra("sdpMid"),
                                     intent.getIntExtra("sdpMLineIndex", 0));
                    break;
                case "OFFER":
                    handleOffer(intent.getStringExtra("sdp"));
                    break;
                case "ANSWER":
                    handleAnswer(intent.getStringExtra("sdp"));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        
        // Initialize views
        initializeViews();
        
        // Get call parameters
        Intent intent = getIntent();
        currentRecipientId = intent.getStringExtra("recipientId");
        isVideoCall = intent.getBooleanExtra("isVideoCall", false);
        isCaller = intent.getBooleanExtra("isCaller", false);
        
        // Set up UI based on call type
        setupCallUI();
        
        // Request necessary permissions
        requestPermissions();
        
        // Initialize WebRTC
        initializeWebRTC();
        
        // Register broadcast receiver
        registerCallStateReceiver();
        
        // Start call if caller
        if (isCaller) {
            startCall();
        }
    }
    
    private void initializeViews() {
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        toggleAudioButton = findViewById(R.id.toggle_audio_button);
        toggleSpeakerButton = findViewById(R.id.toggle_speaker_button);
        toggleCameraButton = findViewById(R.id.toggle_camera_button);
        endCallButton = findViewById(R.id.end_call_button);
        callStatusText = findViewById(R.id.call_status_text);
        callDurationText = findViewById(R.id.call_duration_text);
        recipientNameText = findViewById(R.id.recipient_name_text);
        recipientAvatarImage = findViewById(R.id.recipient_avatar_image);
        
        // Set up click listeners
        toggleAudioButton.setOnClickListener(v -> toggleAudio());
        toggleSpeakerButton.setOnClickListener(v -> toggleSpeaker());
        toggleCameraButton.setOnClickListener(v -> toggleCamera());
        endCallButton.setOnClickListener(v -> endCall());
    }
    
    private void setupCallUI() {
        // Set up UI based on call type
        if (!isVideoCall) {
            localVideoView.setVisibility(View.GONE);
            remoteVideoView.setVisibility(View.GONE);
            toggleCameraButton.setVisibility(View.GONE);
        }
        
        // Set initial call status
        updateCallStatus(isCaller ? "Calling..." : "Incoming call...");
        
        // Load recipient info
        loadRecipientInfo();
    }
    
    private void loadRecipientInfo() {
        // TODO: Load recipient name and avatar from database
        recipientNameText.setText(currentRecipientId);
    }
    
    private void requestPermissions() {
        if (isVideoCall) {
            requestCameraPermission();
        }
        requestAudioPermission();
    }
    
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        }
    }
    
    private void requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_REQUEST_CODE);
        }
    }
    
    private void initializeWebRTC() {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);
        
        rootEglBase = EglBase.create();
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();
        
        // Create video capturer
        if (isVideoCall) {
            videoCapturer = createVideoCapturer();
            if (videoCapturer == null) {
                Log.e(TAG, "Failed to create video capturer");
                return;
            }
        }
        
        // Create local stream
        localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_VIDEO_STREAM_ID);
        
        // Create audio track
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localStream.addTrack(localAudioTrack);
        
        // Create video track if video call
        if (isVideoCall && videoCapturer != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
            videoCapturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS);
            
            localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
            localStream.addTrack(localVideoTrack);
            
            localVideoView.init(rootEglBase.getEglBaseContext(), null);
            localVideoView.setMirror(true);
            localVideoTrack.addSink(localVideoView);
        }
        
        // Initialize peer connection
        initializePeerConnection();
    }
    
    private void initializePeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }
            
            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
                runOnUiThread(() -> {
                    switch (iceConnectionState) {
                        case CONNECTED:
                            updateCallStatus("Connected");
                            break;
                        case DISCONNECTED:
                            updateCallStatus("Disconnected");
                            break;
                        case FAILED:
                            updateCallStatus("Connection failed");
                            endCall();
                            break;
                    }
                });
            }
            
            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
                Log.d(TAG, "onIceConnectionReceivingChange: " + receiving);
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
                        VideoTrack videoTrack = mediaStream.videoTracks.get(0);
                        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
                        videoTrack.addSink(remoteVideoView);
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
        
        peerConnection.addStream(localStream);
    }
    
    private void startCall() {
        if (peerConnection == null) return;
        
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (isVideoCall) {
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        }
        
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        Log.d(TAG, "Local description set");
                    }
                    
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully");
                        sendOffer(sessionDescription);
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
        }, sdpConstraints);
    }
    
    private void sendOffer(SessionDescription sdp) {
        Intent intent = new Intent("SEND_OFFER");
        intent.putExtra("recipientId", currentRecipientId);
        intent.putExtra("sdp", sdp.description);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void sendAnswer(SessionDescription sdp) {
        Intent intent = new Intent("SEND_ANSWER");
        intent.putExtra("recipientId", currentRecipientId);
        intent.putExtra("sdp", sdp.description);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void sendIceCandidate(IceCandidate candidate) {
        Intent intent = new Intent("SEND_ICE_CANDIDATE");
        intent.putExtra("recipientId", currentRecipientId);
        intent.putExtra("candidate", candidate.sdp);
        intent.putExtra("sdpMid", candidate.sdpMid);
        intent.putExtra("sdpMLineIndex", candidate.sdpMLineIndex);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void handleOffer(String sdp) {
        if (peerConnection == null) return;
        
        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.OFFER, sdp);
        
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Remote description set");
            }
            
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote description set successfully");
                createAnswer();
            }
            
            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create remote description: " + s);
            }
            
            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Failed to set remote description: " + s);
            }
        }, sessionDescription);
    }
    
    private void createAnswer() {
        if (peerConnection == null) return;
        
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (isVideoCall) {
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        }
        
        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        Log.d(TAG, "Local description set");
                    }
                    
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully");
                        sendAnswer(sessionDescription);
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
            }
            
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Answer set successfully");
            }
            
            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create answer: " + s);
            }
            
            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Failed to set answer: " + s);
            }
        }, sdpConstraints);
    }
    
    private void handleAnswer(String sdp) {
        if (peerConnection == null) return;
        
        SessionDescription sessionDescription = new SessionDescription(
                SessionDescription.Type.ANSWER, sdp);
        
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Remote description set");
            }
            
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote description set successfully");
            }
            
            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create remote description: " + s);
            }
            
            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "Failed to set remote description: " + s);
            }
        }, sessionDescription);
    }
    
    private void handleIceCandidate(String candidate, String sdpMid, int sdpMLineIndex) {
        if (peerConnection == null) return;
        
        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
        if (localSdp != null) {
            peerConnection.addIceCandidate(iceCandidate);
        } else {
            queuedRemoteCandidates.add(iceCandidate);
        }
    }
    
    private void handleCallAccepted() {
        runOnUiThread(() -> {
            updateCallStatus("Call accepted");
            isCallActive = true;
            startCallDurationTimer();
        });
    }
    
    private void handleCallRejected() {
        runOnUiThread(() -> {
            updateCallStatus("Call rejected");
            endCall();
        });
    }
    
    private void handleCallEnded() {
        runOnUiThread(() -> {
            updateCallStatus("Call ended");
            endCall();
        });
    }
    
    private void handleCallBusy() {
        runOnUiThread(() -> {
            updateCallStatus("User is busy");
            endCall();
        });
    }
    
    private void handleCallTimeout() {
        runOnUiThread(() -> {
            updateCallStatus("Call timed out");
            endCall();
        });
    }
    
    private void toggleAudio() {
        if (localAudioTrack != null) {
            isMuted = !isMuted;
            localAudioTrack.setEnabled(!isMuted);
            toggleAudioButton.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        }
    }
    
    private void toggleSpeaker() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        isSpeakerOn = !isSpeakerOn;
        audioManager.setSpeakerphoneOn(isSpeakerOn);
        toggleSpeakerButton.setImageResource(isSpeakerOn ? R.drawable.ic_speaker : R.drawable.ic_speaker_off);
    }
    
    private void toggleCamera() {
        if (videoCapturer != null) {
            isCameraOn = !isCameraOn;
            videoCapturer.changeCaptureFormat(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS);
            toggleCameraButton.setImageResource(isCameraOn ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);
        }
    }
    
    private void endCall() {
        if (updateCallDurationRunnable != null) {
            handler.removeCallbacks(updateCallDurationRunnable);
        }
        
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping video capture", e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        
        if (localStream != null) {
            localStream.dispose();
            localStream = null;
        }
        
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
        
        // Send end call signal
        Intent intent = new Intent("END_CALL");
        intent.putExtra("recipientId", currentRecipientId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        finish();
    }
    
    private void updateCallStatus(String status) {
        callStatusText.setText(status);
    }
    
    private void startCallDurationTimer() {
        callStartTime = System.currentTimeMillis();
        updateCallDurationRunnable = new Runnable() {
            @Override
            public void run() {
                long duration = System.currentTimeMillis() - callStartTime;
                long seconds = (duration / 1000) % 60;
                long minutes = (duration / (1000 * 60)) % 60;
                long hours = (duration / (1000 * 60 * 60)) % 24;
                
                String durationText;
                if (hours > 0) {
                    durationText = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                } else {
                    durationText = String.format("%02d:%02d", minutes, seconds);
                }
                
                callDurationText.setText(durationText);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateCallDurationRunnable);
    }
    
    private void registerCallStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("CALL_ACCEPTED");
        filter.addAction("CALL_REJECTED");
        filter.addAction("CALL_ENDED");
        filter.addAction("CALL_BUSY");
        filter.addAction("CALL_TIMEOUT");
        filter.addAction("ICE_CANDIDATE");
        filter.addAction("OFFER");
        filter.addAction("ANSWER");
        LocalBroadcastManager.getInstance(this).registerReceiver(callStateReceiver, filter);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callStateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(callStateReceiver);
        }
        endCall();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeWebRTC();
            } else {
                Toast.makeText(this, "Camera permission required for video calls", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeWebRTC();
            } else {
                Toast.makeText(this, "Audio permission required for calls", Toast.LENGTH_SHORT).show();
                finish();
            }
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