package com.nekkochan.onyxchat.util;

import android.app.Application;
import android.view.View;
import android.widget.EditText;

import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.google.GoogleEmojiProvider;
import com.vanniktech.emoji.material.MaterialEmojiLayoutFactory;
import com.vanniktech.emoji.material.MaterialEmojiTextView;

/**
 * Utility class for emoji support in the application
 */
public class EmojiUtils {
    
    private static EmojiPopup emojiPopup;
    private static boolean initialized = false;
    
    /**
     * Initialize emoji support for the application
     * Must be called once in Application.onCreate()
     * 
     * @param application The application instance
     */
    public static void init(Application application) {
        if (!initialized) {
            EmojiManager.install(new GoogleEmojiProvider());
            initialized = true;
        }
    }
    
    /**
     * Show/hide emoji popup for an EditText
     * 
     * @param rootView The root view of the activity/fragment
     * @param editText The EditText to attach the emoji popup to
     * @param emojiButton The emoji toggle button
     * @return The EmojiPopup instance
     */
    public static EmojiPopup setupEmojiPopup(View rootView, EditText editText, View emojiButton) {
        if (!initialized) {
            throw new IllegalStateException("EmojiUtils not initialized. Call init() first.");
        }
        
        // Create emoji popup
        EmojiPopup.Builder builder = EmojiPopup.Builder.fromRootView(rootView);
        emojiPopup = builder.build(editText);
        
        // Toggle emoji popup when button is clicked
        emojiButton.setOnClickListener(v -> {
            if (emojiPopup.isShowing()) {
                emojiPopup.dismiss();
            } else {
                emojiPopup.show();
            }
        });
        
        return emojiPopup;
    }
    
    /**
     * Dismisses the emoji popup if it's showing
     */
    public static void dismissEmojiPopup() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            emojiPopup.dismiss();
        }
    }
    
    /**
     * Check if emoji popup is showing
     * 
     * @return true if emoji popup is showing
     */
    public static boolean isEmojiPopupShowing() {
        return emojiPopup != null && emojiPopup.isShowing();
    }
    
    /**
     * Get the current EmojiPopup instance
     * 
     * @return the EmojiPopup instance
     */
    public static EmojiPopup getEmojiPopup() {
        return emojiPopup;
    }
} 