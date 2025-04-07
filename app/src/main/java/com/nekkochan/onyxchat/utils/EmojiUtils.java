package com.nekkochan.onyxchat.utils;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.emoji.text.EmojiCompat;
import androidx.emoji.bundled.BundledEmojiCompatConfig;

import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.google.GoogleEmojiProvider;

/**
 * Utility class for emoji support in the application
 */
public class EmojiUtils {

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
        EmojiPopup popup = EmojiPopup.Builder.fromRootView(rootView)
                .setOnEmojiPopupShownListener(() -> emojiButton.setSelected(true))
                .setOnEmojiPopupDismissListener(() -> emojiButton.setSelected(false))
                .build(editText);
                
        emojiButton.setOnClickListener(v -> popup.toggle());
        
        return popup;
    }
} 