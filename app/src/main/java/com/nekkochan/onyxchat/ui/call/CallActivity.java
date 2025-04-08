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
import com.nekkochan.onyxchat.utils.EmojiUtils;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
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

    private boolean isVideoCall;
    private String contactId;
    private String contactName;
    private boolean isCaller;
    private boolean isMuted = false;
    private boolean isSpeakerOn = true;
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

    // UI components
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private ImageButton endCallButton;
    private ImageButton muteButton;
    private ImageButton speakerButton;
    private ImageButton videoButton;
    private TextView contactNameText;
    private TextView callStatusText;

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
        // Initialize EGL context
        eglBaseContext = EglBase.create().getEglBaseContext();

        // Initialize PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        // Create PeerConnectionFactory
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();

        // Initialize video views
        localVideoView.init(eglBaseContext, null);
        remoteVideoView.init(eglBaseContext, null);

        // Create local media stream
        createLocalMediaStream();

        // Create peer connection
        createPeerConnection();

        // Start call
        if (isCaller) {
            startCall();
        } else {
            // Handle incoming call
            handleIncomingCall();
        }
    }

    private void createLocalMediaStream() {
        MediaStream localStream = peerConnectionFactory.createLocalMediaStream("local_stream");

        // Create audio track
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource);
        localStream.addTrack(localAudioTrack);

        if (isVideoCall) {
            // Create video track
            VideoCapturer videoCapturer = createVideoCapturer();
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
            videoCapturer.startCapture(1280, 720, 30);

            localVideoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource);
            localStream.addTrack(localVideoTrack);
            localVideoTrack.addSink(localVideoView);
        }

        // Add stream to peer connection
        peerConnection.addStream(localStream);
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

    private void createPeerConnection() {
        // Add STUN/TURN servers
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                // Send ICE candidate to remote peer
                sendIceCandidate(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                // Handle remote stream
                runOnUiThread(() -> {
                    if (mediaStream.videoTracks.size() > 0) {
                        mediaStream.videoTracks.get(0).addSink(remoteVideoView);
                    }
                });
            }

            // Implement other required methods...
        });
    }

    private void startCall() {
        // Create offer
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        // Send offer to remote peer
                        sendOffer(sessionDescription);
                    }
                    // Implement other required methods...
                }, sessionDescription);
            }
            // Implement other required methods...
        }, constraints);
    }

    private void handleIncomingCall() {
        // Handle incoming call setup
        // This will be implemented when we add signaling
    }

    private void endCall() {
        if (peerConnection != null) {
            peerConnection.close();
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
} 