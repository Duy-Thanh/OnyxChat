package com.nekkochan.onyxchat.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ai.AIFeatureManager;
import com.nekkochan.onyxchat.ai.ContentModerationManager;
import com.nekkochan.onyxchat.ai.TranslationManager;
import com.nekkochan.onyxchat.util.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.View;

/**
 * Activity for managing AI feature settings
 */
public class AISettingsActivity extends AppCompatActivity {

    private AIFeatureManager aiFeatureManager;
    private PreferenceManager preferenceManager;
    
    private SwitchMaterial smartRepliesSwitch;
    private SwitchMaterial translationSwitch;
    private SwitchMaterial moderationSwitch;
    private Spinner moderationLevelSpinner;
    private Spinner languageSpinner;
    private Button resetButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_settings);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.ai_settings_title);
        }
        
        // Initialize managers
        aiFeatureManager = AIFeatureManager.getInstance(this);
        preferenceManager = new PreferenceManager(this);
        
        // Initialize UI components
        initializeViews();
        
        // Load current settings
        loadSettings();
        
        // Set up listeners
        setupListeners();
    }
    
    private void initializeViews() {
        smartRepliesSwitch = findViewById(R.id.switch_smart_replies);
        translationSwitch = findViewById(R.id.switch_translation);
        moderationSwitch = findViewById(R.id.switch_moderation);
        moderationLevelSpinner = findViewById(R.id.spinner_moderation_level);
        languageSpinner = findViewById(R.id.spinner_language);
        resetButton = findViewById(R.id.button_reset_settings);
        
        // Set up moderation level spinner
        List<String> moderationLevels = new ArrayList<>();
        moderationLevels.add(getString(R.string.moderation_level_low));
        moderationLevels.add(getString(R.string.moderation_level_medium));
        moderationLevels.add(getString(R.string.moderation_level_high));
        
        ArrayAdapter<String> moderationAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, moderationLevels);
        moderationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        moderationLevelSpinner.setAdapter(moderationAdapter);
        
        // Set up language spinner
        List<String> languages = new ArrayList<>();
        languages.add("English");
        languages.add("Spanish");
        languages.add("French");
        languages.add("German");
        languages.add("Chinese");
        languages.add("Japanese");
        languages.add("Korean");
        languages.add("Russian");
        languages.add("Arabic");
        languages.add("Hindi");
        
        ArrayAdapter<String> languageAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, languages);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);
    }
    
    private void loadSettings() {
        // Load feature states
        smartRepliesSwitch.setChecked(aiFeatureManager.isSmartRepliesEnabled());
        translationSwitch.setChecked(aiFeatureManager.isTranslationEnabled());
        moderationSwitch.setChecked(aiFeatureManager.isContentModerationEnabled());
        
        // Load moderation level
        ContentModerationManager.ModerationLevel level = 
                ContentModerationManager.getInstance(this).getModerationLevel();
        switch (level) {
            case LOW:
                moderationLevelSpinner.setSelection(0);
                break;
            case MEDIUM:
                moderationLevelSpinner.setSelection(1);
                break;
            case HIGH:
                moderationLevelSpinner.setSelection(2);
                break;
        }
        
        // Load preferred language
        String preferredLanguage = preferenceManager.getString("pref_preferred_language", "en");
        int languagePosition = 0; // Default to English
        
        if (preferredLanguage.equals("es")) {
            languagePosition = 1; // Spanish
        } else if (preferredLanguage.equals("fr")) {
            languagePosition = 2; // French
        } else if (preferredLanguage.equals("de")) {
            languagePosition = 3; // German
        } else if (preferredLanguage.equals("zh")) {
            languagePosition = 4; // Chinese
        } else if (preferredLanguage.equals("ja")) {
            languagePosition = 5; // Japanese
        } else if (preferredLanguage.equals("ko")) {
            languagePosition = 6; // Korean
        } else if (preferredLanguage.equals("ru")) {
            languagePosition = 7; // Russian
        } else if (preferredLanguage.equals("ar")) {
            languagePosition = 8; // Arabic
        } else if (preferredLanguage.equals("hi")) {
            languagePosition = 9; // Hindi
        }
        
        languageSpinner.setSelection(languagePosition);
    }
    
    private void setupListeners() {
        // Smart replies switch
        smartRepliesSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            aiFeatureManager.setSmartRepliesEnabled(isChecked);
            Toast.makeText(this, 
                    isChecked ? R.string.smart_replies_enabled : R.string.smart_replies_disabled, 
                    Toast.LENGTH_SHORT).show();
        });
        
        // Translation switch
        translationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            aiFeatureManager.setTranslationEnabled(isChecked);
            languageSpinner.setEnabled(isChecked);
            Toast.makeText(this, 
                    isChecked ? R.string.translation_enabled : R.string.translation_disabled, 
                    Toast.LENGTH_SHORT).show();
        });
        
        // Moderation switch
        moderationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            aiFeatureManager.setContentModerationEnabled(isChecked);
            moderationLevelSpinner.setEnabled(isChecked);
            Toast.makeText(this, 
                    isChecked ? R.string.moderation_enabled : R.string.moderation_disabled, 
                    Toast.LENGTH_SHORT).show();
        });
        
        // Moderation level spinner
        moderationLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ContentModerationManager.ModerationLevel level;
                switch (position) {
                    case 0:
                        level = ContentModerationManager.ModerationLevel.LOW;
                        break;
                    case 1:
                        level = ContentModerationManager.ModerationLevel.MEDIUM;
                        break;
                    case 2:
                        level = ContentModerationManager.ModerationLevel.HIGH;
                        break;
                    default:
                        level = ContentModerationManager.ModerationLevel.MEDIUM;
                }
                
                aiFeatureManager.setModerationLevel(level);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        
        // Language spinner
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String languageCode;
                switch (position) {
                    case 0:
                        languageCode = "en"; // English
                        break;
                    case 1:
                        languageCode = "es"; // Spanish
                        break;
                    case 2:
                        languageCode = "fr"; // French
                        break;
                    case 3:
                        languageCode = "de"; // German
                        break;
                    case 4:
                        languageCode = "zh"; // Chinese
                        break;
                    case 5:
                        languageCode = "ja"; // Japanese
                        break;
                    case 6:
                        languageCode = "ko"; // Korean
                        break;
                    case 7:
                        languageCode = "ru"; // Russian
                        break;
                    case 8:
                        languageCode = "ar"; // Arabic
                        break;
                    case 9:
                        languageCode = "hi"; // Hindi
                        break;
                    default:
                        languageCode = "en"; // Default to English
                }
                
                aiFeatureManager.setPreferredLanguage(languageCode);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        
        // Reset button
        resetButton.setOnClickListener(v -> {
            // Reset to default settings
            aiFeatureManager.setSmartRepliesEnabled(true);
            aiFeatureManager.setTranslationEnabled(true);
            aiFeatureManager.setContentModerationEnabled(true);
            aiFeatureManager.setModerationLevel(ContentModerationManager.ModerationLevel.MEDIUM);
            aiFeatureManager.setPreferredLanguage("en");
            
            // Reload UI
            loadSettings();
            
            Toast.makeText(this, R.string.settings_reset, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
