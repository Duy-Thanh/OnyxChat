package com.nekkochan.onyxchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.legal.PrivacyPolicyActivity;
import com.nekkochan.onyxchat.ui.legal.TermsOfServiceActivity;

/**
 * Settings activity for the application
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.settings);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    /**
     * Inner settings fragment
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            
            // Apply screen security if enabled
            SwitchPreferenceCompat screenSecurityPref = findPreference("screen_security");
            if (screenSecurityPref != null) {
                // Set listener to update window secure flag when changed
                screenSecurityPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean secureEnabled = (Boolean) newValue;
                    if (secureEnabled) {
                        requireActivity().getWindow().setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE);
                    } else {
                        requireActivity().getWindow().clearFlags(
                                WindowManager.LayoutParams.FLAG_SECURE);
                    }
                    return true;
                });
                
                // Apply current setting
                if (screenSecurityPref.isChecked()) {
                    requireActivity().getWindow().setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE);
                }
            }
            
            // Set up privacy policy preference
            setupLegalDocumentPreference(
                    "privacy_policy",
                    preference -> openPrivacyPolicy()
            );
            
            // Set up terms of service preference
            setupLegalDocumentPreference(
                    "terms_of_service",
                    preference -> openTermsOfService()
            );
            
            // Set up generate new key preference
            Preference generateKeyPref = findPreference("generate_new_key");
            if (generateKeyPref != null) {
                generateKeyPref.setOnPreferenceClickListener(preference -> {
                    // TODO: Implement key generation
                    return true;
                });
            }
        }
        
        /**
         * Set up a preference for viewing legal documents
         */
        private void setupLegalDocumentPreference(String key, Preference.OnPreferenceClickListener listener) {
            Preference preference = findPreference(key);
            if (preference != null) {
                preference.setOnPreferenceClickListener(listener);
            }
        }
        
        /**
         * Open the privacy policy screen
         */
        private boolean openPrivacyPolicy() {
            Intent intent = new Intent(getActivity(), PrivacyPolicyActivity.class);
            startActivity(intent);
            return true;
        }
        
        /**
         * Open the terms of service screen
         */
        private boolean openTermsOfService() {
            Intent intent = new Intent(getActivity(), TermsOfServiceActivity.class);
            startActivity(intent);
            return true;
        }
    }
} 