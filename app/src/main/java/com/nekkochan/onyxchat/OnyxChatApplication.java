package com.nekkochan.onyxchat;

import android.app.Application;
import android.util.Log;

import com.nekkochan.onyxchat.utils.EmojiUtils;

/**
 * Main application class for OnyxChat
 * Responsible for initialization of various components
 */
public class OnyxChatApplication extends Application {
    private static final String TAG = "OnyxChatApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize emoji support
        Log.d(TAG, "Initializing emoji support");
        EmojiUtils.init(this);
        
        // Initialize other components as needed
    }
} 