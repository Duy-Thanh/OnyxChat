package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Utility class to manage application themes
 */
public class ThemeUtils {
    private static final String TAG = "ThemeUtils";
    
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    
    private final Context context;
    private final SharedPreferences preferences;
    
    public ThemeUtils(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Apply saved theme on initialization
        applyTheme();
    }
    
    /**
     * Toggle between light and dark theme
     */
    public void toggleTheme() {
        int currentMode = preferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int newMode;
        
        // Toggle between light and dark
        if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            newMode = AppCompatDelegate.MODE_NIGHT_NO;
            Log.d(TAG, "Switching to light theme");
        } else {
            newMode = AppCompatDelegate.MODE_NIGHT_YES;
            Log.d(TAG, "Switching to dark theme");
        }
        
        // Save the new theme
        preferences.edit().putInt(KEY_THEME_MODE, newMode).apply();
        
        // Apply the theme
        AppCompatDelegate.setDefaultNightMode(newMode);
    }
    
    /**
     * Apply saved theme
     */
    public void applyTheme() {
        int themeMode = preferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        Log.d(TAG, "Applying theme mode: " + themeMode);
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }
    
    /**
     * Check if dark mode is currently active
     */
    public boolean isDarkModeActive() {
        int currentNightMode = context.getResources().getConfiguration().uiMode 
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }
    
    /**
     * Set specific theme mode
     * @param mode AppCompatDelegate.MODE_NIGHT_YES, MODE_NIGHT_NO, or MODE_NIGHT_FOLLOW_SYSTEM
     */
    public void setThemeMode(int mode) {
        preferences.edit().putInt(KEY_THEME_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }
} 