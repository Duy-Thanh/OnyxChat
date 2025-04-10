package com.nekkochan.onyxchat.ai;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manager class for handling language translation using ML Kit
 */
public class TranslationManager {
    private static final String TAG = "TranslationManager";
    private static TranslationManager instance;
    
    private final LanguageIdentifier languageIdentifier;
    private final Map<String, Translator> translators = new HashMap<>();
    private String preferredLanguage = TranslateLanguage.ENGLISH; // Default language
    
    /**
     * Get the singleton instance of TranslationManager
     * 
     * @param context The application context
     * @return The TranslationManager instance
     */
    public static synchronized TranslationManager getInstance(Context context) {
        if (instance == null) {
            instance = new TranslationManager();
        }
        return instance;
    }
    
    private TranslationManager() {
        languageIdentifier = LanguageIdentification.getClient();
    }
    
    /**
     * Set the user's preferred language for translations
     * 
     * @param languageCode The language code (e.g., "en", "es", "fr")
     */
    public void setPreferredLanguage(String languageCode) {
        if (new java.util.HashSet<>(TranslateLanguage.getAllLanguages()).contains(languageCode)) {
            this.preferredLanguage = languageCode;
        } else {
            Log.w(TAG, "Unsupported language code: " + languageCode);
        }
    }
    
    /**
     * Get the list of supported languages for translation
     * 
     * @return Set of supported language codes
     */
    public Set<String> getSupportedLanguages() {
        // Convert List to Set
        return new java.util.HashSet<>(TranslateLanguage.getAllLanguages());
    }
    
    /**
     * Identify the language of a text
     * 
     * @param text The text to identify
     * @param callback Callback to receive the identified language code
     */
    public void identifyLanguage(String text, final LanguageIdentificationCallback callback) {
        if (text == null || text.isEmpty()) {
            callback.onLanguageIdentified("und"); // Undefined
            return;
        }
        
        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener(languageCode -> {
                    if (languageCode.equals("und")) {
                        Log.d(TAG, "Language not identified or text too short");
                    } else {
                        Log.d(TAG, "Language identified: " + languageCode);
                    }
                    callback.onLanguageIdentified(languageCode);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Language identification failed", e);
                    callback.onLanguageIdentified("und");
                });
    }
    
    /**
     * Translate text to the user's preferred language
     * 
     * @param text The text to translate
     * @param callback Callback to receive the translated text
     */
    public void translateToPreferredLanguage(String text, final TranslationCallback callback) {
        if (text == null || text.isEmpty()) {
            callback.onTranslationComplete(text);
            return;
        }
        
        // First identify the source language
        identifyLanguage(text, sourceLanguage -> {
            if (sourceLanguage.equals("und") || sourceLanguage.equals(preferredLanguage)) {
                // No translation needed
                callback.onTranslationComplete(text);
                return;
            }
            
            // Translate from identified language to preferred language
            translateText(text, sourceLanguage, preferredLanguage, callback);
        });
    }
    
    /**
     * Translate text from one language to another
     * 
     * @param text The text to translate
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @param callback Callback to receive the translated text
     */
    public void translateText(String text, String sourceLanguage, String targetLanguage, 
                             final TranslationCallback callback) {
        if (text == null || text.isEmpty()) {
            callback.onTranslationComplete(text);
            return;
        }
        
        if (sourceLanguage.equals(targetLanguage)) {
            callback.onTranslationComplete(text);
            return;
        }
        
        // Create a translation model key
        String modelKey = sourceLanguage + "-" + targetLanguage;
        
        // Get or create translator
        Translator translator = getTranslator(sourceLanguage, targetLanguage);
        
        // Check if model is downloaded
        translator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> {
                    // Model downloaded, perform translation
                    translator.translate(text)
                            .addOnSuccessListener(translatedText -> {
                                Log.d(TAG, "Translation successful");
                                callback.onTranslationComplete(translatedText);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Translation failed", e);
                                callback.onTranslationComplete(text); // Return original text on failure
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Model download failed", e);
                    callback.onTranslationComplete(text); // Return original text on failure
                });
    }
    
    /**
     * Get or create a translator for the specified language pair
     * 
     * @param sourceLanguage The source language code
     * @param targetLanguage The target language code
     * @return The Translator instance
     */
    private Translator getTranslator(String sourceLanguage, String targetLanguage) {
        String modelKey = sourceLanguage + "-" + targetLanguage;
        
        if (!translators.containsKey(modelKey)) {
            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLanguage)
                    .setTargetLanguage(targetLanguage)
                    .build();
            
            translators.put(modelKey, Translation.getClient(options));
        }
        
        return translators.get(modelKey);
    }
    
    /**
     * Release resources when no longer needed
     */
    public void shutdown() {
        languageIdentifier.close();
        
        for (Translator translator : translators.values()) {
            translator.close();
        }
        translators.clear();
    }
    
    /**
     * Callback interface for language identification
     */
    public interface LanguageIdentificationCallback {
        void onLanguageIdentified(String languageCode);
    }
    
    /**
     * Callback interface for translation
     */
    public interface TranslationCallback {
        void onTranslationComplete(String translatedText);
    }
}
