package com.nekkochan.onyxchat.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for MIME type operations with media files
 */
public class MimeTypeUtils {
    
    // Categories of supported media types
    public static final Set<String> IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    ));
    
    public static final Set<String> VIDEO_TYPES = new HashSet<>(Arrays.asList(
            "video/mp4", "video/3gpp", "video/webm", "video/quicktime", "video/x-matroska"
    ));
    
    public static final Set<String> AUDIO_TYPES = new HashSet<>(Arrays.asList(
            "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/x-wav", 
            "audio/aac", "audio/flac", "audio/x-matroska"
    ));
    
    public static final Set<String> DOCUMENT_TYPES = new HashSet<>(Arrays.asList(
            "application/pdf", "application/msword", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
    ));
    
    /**
     * Get MIME type from URI
     * 
     * @param context App context
     * @param uri File URI
     * @return MIME type string or null if unknown
     */
    public static String getMimeType(Context context, Uri uri) {
        String mimeType = null;
        
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = context.getContentResolver();
            mimeType = contentResolver.getType(uri);
        } else if (uri.getScheme().equals("file")) {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        
        return mimeType;
    }
    
    /**
     * Get MIME type from file path
     * 
     * @param filePath Path to the file
     * @return MIME type string or null if unknown
     */
    public static String getMimeTypeFromPath(String filePath) {
        String extension = getFileExtension(filePath);
        if (extension != null) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        return null;
    }
    
    /**
     * Get file extension from path
     * 
     * @param filePath Path to the file
     * @return File extension without the dot, or null if none exists
     */
    public static String getFileExtension(String filePath) {
        if (filePath == null) {
            return null;
        }
        
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1);
        }
        return null;
    }
    
    /**
     * Check if a file is an image based on its MIME type
     * 
     * @param mimeType The MIME type to check
     * @return true if the file is an image
     */
    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/") && IMAGE_TYPES.contains(mimeType);
    }
    
    /**
     * Check if a file is a video based on its MIME type
     * 
     * @param mimeType The MIME type to check
     * @return true if the file is a video
     */
    public static boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/") && VIDEO_TYPES.contains(mimeType);
    }
    
    /**
     * Check if a file is audio based on its MIME type
     * 
     * @param mimeType The MIME type to check
     * @return true if the file is audio
     */
    public static boolean isAudio(String mimeType) {
        return mimeType != null && mimeType.startsWith("audio/") && AUDIO_TYPES.contains(mimeType);
    }
    
    /**
     * Check if a file is a document based on its MIME type
     * 
     * @param mimeType The MIME type to check
     * @return true if the file is a document
     */
    public static boolean isDocument(String mimeType) {
        return mimeType != null && DOCUMENT_TYPES.contains(mimeType);
    }
    
    /**
     * Convert file size to human-readable format
     * 
     * @param size File size in bytes
     * @return Human-readable file size
     */
    public static String getReadableFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
    
    /**
     * Check if file size is within the max allowed size
     * 
     * @param fileSize File size in bytes
     * @param maxSizeMB Maximum allowed size in MB
     * @return true if the file is within the limit
     */
    public static boolean isFileSizeValid(long fileSize, int maxSizeMB) {
        long maxSizeBytes = maxSizeMB * 1024 * 1024;
        return fileSize <= maxSizeBytes;
    }
} 