package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Utility class for managing user preferences
 */
public class PreferenceManager {
    private static final String PREF_NAME = "onyxchat_preferences";
    private final SharedPreferences sharedPreferences;
    
    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Store a string value in preferences
     * 
     * @param key The preference key
     * @param value The string value to store
     */
    public void putString(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }
    
    /**
     * Retrieve a string value from preferences
     * 
     * @param key The preference key
     * @param defaultValue The default value to return if the key doesn't exist
     * @return The stored string value or the default value
     */
    public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }
    
    /**
     * Store a boolean value in preferences
     * 
     * @param key The preference key
     * @param value The boolean value to store
     */
    public void putBoolean(String key, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }
    
    /**
     * Retrieve a boolean value from preferences
     * 
     * @param key The preference key
     * @param defaultValue The default value to return if the key doesn't exist
     * @return The stored boolean value or the default value
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(key, defaultValue);
    }
    
    /**
     * Store an integer value in preferences
     * 
     * @param key The preference key
     * @param value The integer value to store
     */
    public void putInt(String key, int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }
    
    /**
     * Retrieve an integer value from preferences
     * 
     * @param key The preference key
     * @param defaultValue The default value to return if the key doesn't exist
     * @return The stored integer value or the default value
     */
    public int getInt(String key, int defaultValue) {
        return sharedPreferences.getInt(key, defaultValue);
    }
    
    /**
     * Store a long value in preferences
     * 
     * @param key The preference key
     * @param value The long value to store
     */
    public void putLong(String key, long value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(key, value);
        editor.apply();
    }
    
    /**
     * Retrieve a long value from preferences
     * 
     * @param key The preference key
     * @param defaultValue The default value to return if the key doesn't exist
     * @return The stored long value or the default value
     */
    public long getLong(String key, long defaultValue) {
        return sharedPreferences.getLong(key, defaultValue);
    }
    
    /**
     * Remove a preference
     * 
     * @param key The preference key to remove
     */
    public void remove(String key) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(key);
        editor.apply();
    }
    
    /**
     * Clear all preferences
     */
    public void clear() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
    
    /**
     * Check if a preference exists
     * 
     * @param key The preference key
     * @return True if the preference exists, false otherwise
     */
    public boolean contains(String key) {
        return sharedPreferences.contains(key);
    }
}
