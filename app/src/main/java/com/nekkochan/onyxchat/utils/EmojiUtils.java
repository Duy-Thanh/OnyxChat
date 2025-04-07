package com.nekkochan.onyxchat.utils;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.view.inputmethod.InputMethodManager;
import android.app.Activity;

import androidx.emoji.text.EmojiCompat;
import androidx.emoji.bundled.BundledEmojiCompatConfig;

import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.google.GoogleEmojiProvider;
import com.nekkochan.onyxchat.R;

/**
 * Utility class for emoji support in the application
 */
public class EmojiUtils {

    private static EmojiPopup emojiPopup; // Store the active emoji popup

    /**
     * Initialize emoji support for the application
     * @param context Application context
     */
    public static void init(Context context) {
        // Initialize EmojiCompat
        EmojiCompat.Config config = new BundledEmojiCompatConfig(context);
        EmojiCompat.init(config);
        
        // Initialize EmojiPopup
        EmojiManager.install(new GoogleEmojiProvider());
    }
    
    /**
     * Set up emoji popup for an EditText
     * @param context Context
     * @param rootView Root view for the emoji popup
     * @param editText EditText that will receive emojis
     * @param emojiButton Button to toggle emoji picker
     * @return EmojiPopup instance
     */
    public static EmojiPopup setupEmojiPopup(Context context, View rootView, EditText editText, ImageView emojiButton) {
        Log.d("EmojiUtils", "Setting up emoji popup - NEW IMPLEMENTATION");
        
        // Clean up any existing popup completely
        if (emojiPopup != null) {
            try {
                emojiPopup.dismiss();
            } catch (Exception e) {
                Log.e("EmojiUtils", "Error dismissing existing popup", e);
            }
            emojiPopup = null;
        }
        
        // Prevent keyboard from showing when emoji popup is active
        editText.setShowSoftInputOnFocus(false);
        
        // Get a reference to the Done button
        View doneButton = ((Activity)context).findViewById(android.R.id.content)
                .getRootView().findViewById(R.id.emojiDoneButton);
        
        // Create a new popup with minimal options to avoid conflicts
        final EmojiPopup popup = EmojiPopup.Builder.fromRootView(rootView)
                .setOnEmojiPopupShownListener(() -> {
                    emojiButton.setSelected(true);
                    Log.d("EmojiUtils", "Emoji popup shown");
                    
                    // Show the Done button when emoji popup is shown
                    if (doneButton != null) {
                        doneButton.setVisibility(View.VISIBLE);
                    }
                    
                    // Force hide keyboard when emoji popup is shown
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                    }
                })
                .setOnEmojiPopupDismissListener(() -> {
                    emojiButton.setSelected(false);
                    Log.d("EmojiUtils", "Emoji popup dismissed");
                    
                    // Hide the Done button when emoji popup is dismissed
                    if (doneButton != null) {
                        doneButton.setVisibility(View.GONE);
                    }
                })
                .build(editText);
        
        // Save reference to the popup
        emojiPopup = popup;
        
        // THIS IS THE KEY CHANGE: We'll manually control the popup visibility rather than using toggle()
        // Remove any existing click listeners
        emojiButton.setOnClickListener(null);
        
        // Set a custom click listener with direct control and no delay
        emojiButton.setOnClickListener(v -> {
            Log.d("EmojiUtils", "Emoji button clicked - ONE CLICK IMPLEMENTATION");
            
            // Prevent focus which would normally trigger keyboard
            editText.clearFocus();
            
            // Make sure keyboard doesn't show
            editText.setShowSoftInputOnFocus(false);
            
            // Get the IMM reference
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            
            // Always hide keyboard first
            if (imm != null) {
                imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
            }
            
            // Toggle popup display directly
            if (popup.isShowing()) {
                Log.d("EmojiUtils", "Hiding emoji popup");
                popup.dismiss();
            } else {
                Log.d("EmojiUtils", "Directly showing emoji popup (ONE CLICK)");
                // Force show without delay
                popup.show();
            }
        });
        
        return popup;
    }
    
    /**
     * Dismiss the active emoji popup if it's showing
     */
    public static void dismissEmojiPopup() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            emojiPopup.dismiss();
        }
    }
    
    /**
     * Check if the emoji popup is currently showing
     * @return true if the popup is showing, false otherwise
     */
    public static boolean isEmojiPopupShowing() {
        return emojiPopup != null && emojiPopup.isShowing();
    }
} 