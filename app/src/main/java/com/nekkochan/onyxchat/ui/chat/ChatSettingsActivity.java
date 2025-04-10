package com.nekkochan.onyxchat.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.AISettingsActivity;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatSettingsActivity extends AppCompatActivity {

    private static final String TAG = "ChatSettingsActivity";
    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";

    private String contactId;
    private String contactName;
    private CircleImageView contactAvatar;
    private TextView contactNameText;
    private TextView contactIdText;
    private SwitchCompat notificationsSwitch;
    private SwitchCompat mediaAutoDownloadSwitch;
    private TextView disappearingMessagesDuration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_settings);

        // Get contact ID and name from intent
        contactId = getIntent().getStringExtra(EXTRA_CONTACT_ID);
        contactName = getIntent().getStringExtra(EXTRA_CONTACT_NAME);

        if (contactId == null) {
            finish();
            return;
        }

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Initialize views
        initializeViews();
        
        // Set contact info
        contactNameText.setText(contactName != null ? contactName : contactId);
        contactIdText.setText(contactId);

        // Set up click listeners
        setupClickListeners();
    }

    private void initializeViews() {
        contactAvatar = findViewById(R.id.contactAvatar);
        contactNameText = findViewById(R.id.contactName);
        contactIdText = findViewById(R.id.contactId);
        notificationsSwitch = findViewById(R.id.notificationsSwitch);
        mediaAutoDownloadSwitch = findViewById(R.id.mediaAutoDownloadSwitch);
        disappearingMessagesDuration = findViewById(R.id.disappearingMessagesDuration);
    }

    private void setupClickListeners() {
        // Info section buttons
        findViewById(R.id.mediaGalleryButton).setOnClickListener(v -> 
                showFeatureComingSoon("Media gallery"));
        
        findViewById(R.id.voiceCallButton).setOnClickListener(v -> 
                showFeatureComingSoon("Voice call"));
        
        findViewById(R.id.videoCallButton).setOnClickListener(v -> 
                showFeatureComingSoon("Video call"));
        
        findViewById(R.id.searchButton).setOnClickListener(v -> 
                showFeatureComingSoon("Chat search"));

        // Notification section
        findViewById(R.id.notificationsOption).setOnClickListener(v -> {
            notificationsSwitch.toggle();
            showFeatureComingSoon("Notification settings");
        });

        // Privacy section
        findViewById(R.id.encryptionOption).setOnClickListener(v -> 
                showFeatureComingSoon("Encryption info"));
        
        findViewById(R.id.disappearingMessagesOption).setOnClickListener(v -> 
                showFeatureComingSoon("Disappearing messages"));

        // Media & Storage section
        findViewById(R.id.mediaAutoDownloadOption).setOnClickListener(v -> {
            mediaAutoDownloadSwitch.toggle();
            showFeatureComingSoon("Media auto-download settings");
        });
        
        findViewById(R.id.storageUsageOption).setOnClickListener(v -> 
                showFeatureComingSoon("Storage usage info"));
                
        // AI Features section
        findViewById(R.id.aiSettingsOption).setOnClickListener(v -> {
            Intent intent = new Intent(this, AISettingsActivity.class);
            startActivity(intent);
        });

        // Danger zone
        findViewById(R.id.clearChatOption).setOnClickListener(v -> 
                showFeatureComingSoon("Clear chat history"));
        
        findViewById(R.id.blockContactOption).setOnClickListener(v -> 
                showFeatureComingSoon("Block contact"));
    }

    private void showFeatureComingSoon(String feature) {
        Toast.makeText(this, feature + " feature coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 