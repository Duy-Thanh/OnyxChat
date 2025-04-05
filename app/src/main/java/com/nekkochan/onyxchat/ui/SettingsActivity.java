package com.nekkochan.onyxchat.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.legal.PrivacyPolicyActivity;
import com.nekkochan.onyxchat.ui.legal.TermsOfServiceActivity;

import android.app.AlertDialog;
import android.app.ProgressDialog;

/**
 * Settings activity for the application
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Set up edge-to-edge display
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        
        // Apply transparent status bar for edge-to-edge design
        getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.transparent));
        
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
            actionBar.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.background_dark)));
            actionBar.setElevation(4f); // Add slight elevation for modern look
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
            
            // Apply styling to preference categories
            applyPreferenceStyles();
            
            // Set app version
            setupAppVersionPreference();
            
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
                    showKeyGenerationDialog();
                    return true;
                });
            }
            
            // Setup server test preference
            setupServerTestPreference();
        }
        
        /**
         * Apply custom styles to preferences
         */
        private void applyPreferenceStyles() {
            // Modern styling will be applied through the theme
            setDivider(new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.transparent)));
            setDividerHeight(16);
        }
        
        /**
         * Setup app version preference
         */
        private void setupAppVersionPreference() {
            Preference versionPref = findPreference("app_version");
            if (versionPref != null) {
                try {
                    PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(
                            requireContext().getPackageName(), 0);
                    String version = pInfo.versionName;
                    versionPref.setSummary(version);
                } catch (PackageManager.NameNotFoundException e) {
                    versionPref.setSummary("1.0.0");
                }
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
        
        /**
         * Show dialog to confirm key generation
         */
        private void showKeyGenerationDialog() {
            new AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
                    .setTitle(R.string.generate_new_key)
                    .setMessage("This will create a new encryption key pair for your account. Continue?")
                    .setPositiveButton(R.string.generate_new_key, (dialog, which) -> {
                        simulateKeyGeneration();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
        
        /**
         * Simulate key generation process
         */
        private void simulateKeyGeneration() {
            // Show progress dialog
            ProgressDialog progressDialog = new ProgressDialog(requireContext(), R.style.AlertDialogStyle);
            progressDialog.setMessage(getString(R.string.key_generation_progress));
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // Simulate generation with a delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), 
                        R.string.key_generation_success, 
                        Toast.LENGTH_SHORT).show();
            }, 2000);
        }
        
        /**
         * Setup server test preference
         */
        private void setupServerTestPreference() {
            Preference testServerPref = findPreference("test_server_connection");
            if (testServerPref != null) {
                testServerPref.setOnPreferenceClickListener(preference -> {
                    testServerConnection();
                    return true;
                });
            }
        }
        
        /**
         * Test server connection
         */
        private void testServerConnection() {
            // Show progress dialog
            ProgressDialog progressDialog = new ProgressDialog(requireContext(), R.style.AlertDialogStyle);
            progressDialog.setMessage("Testing server connection...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // Simulate connection test with a delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), 
                        R.string.server_connection_success, 
                        Toast.LENGTH_SHORT).show();
            }, 1500);
        }
    }
} 