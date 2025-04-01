package com.nekkochan.onyxchat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.nekkochan.onyxchat.R;

public class SettingsActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private AppBarLayout appBarLayout;
    private MaterialButton btnProfile;
    private MaterialButton btnEncryption;
    private MaterialButton btnTheme;
    private MaterialButton btnAbout;
    private SwitchMaterial switchScreenSecurity;
    
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_settings);
        
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Initialize views
        toolbar = findViewById(R.id.toolbar);
        appBarLayout = findViewById(R.id.appBarLayout);
        btnProfile = findViewById(R.id.btnProfile);
        btnEncryption = findViewById(R.id.btnEncryption);
        btnTheme = findViewById(R.id.btnTheme);
        btnAbout = findViewById(R.id.btnAbout);
        switchScreenSecurity = findViewById(R.id.switchScreenSecurity);
        
        // Apply window insets to fix status bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (view, windowInsets) -> {
            int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            view.setPadding(view.getPaddingLeft(), statusBarHeight, view.getPaddingRight(), view.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });
        
        // Set up toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        // Load saved preferences
        switchScreenSecurity.setChecked(sharedPreferences.getBoolean("screen_security", true));
        
        // Set up click listeners
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        
        btnProfile.setOnClickListener(v -> {
            // Navigate to profile settings
            Toast.makeText(this, "Profile settings coming soon", Toast.LENGTH_SHORT).show();
        });
        
        btnEncryption.setOnClickListener(v -> {
            // Navigate to encryption settings
            Toast.makeText(this, "Encryption settings coming soon", Toast.LENGTH_SHORT).show();
        });
        
        btnTheme.setOnClickListener(v -> {
            showThemeDialog();
        });
        
        btnAbout.setOnClickListener(v -> {
            // Navigate to about screen
            Toast.makeText(this, "About screen coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Set up switch listener
        switchScreenSecurity.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("screen_security", isChecked).apply();
            if (isChecked) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        });
        
        // Apply screen security setting
        if (switchScreenSecurity.isChecked()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
    
    private void showThemeDialog() {
        String[] themes = {"System Default", "Light", "Dark"};
        int currentTheme = sharedPreferences.getInt("theme_mode", 0);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose Theme")
                .setSingleChoiceItems(themes, currentTheme, (dialog, which) -> {
                    sharedPreferences.edit().putInt("theme_mode", which).apply();
                    dialog.dismiss();
                    Toast.makeText(this, "Restart app to apply theme", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
} 