package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for file operations
 */
public class FileUtils {
    private static final String TAG = "FileUtils";
    
    /**
     * Get the file name from a URI
     * 
     * @param context The context
     * @param uri The URI
     * @return The file name
     */
    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name from URI", e);
            }
        }
        
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        
        return result;
    }
    
    /**
     * Get the MIME type from a URI
     * 
     * @param context The context
     * @param uri The URI
     * @return The MIME type
     */
    public static String getMimeType(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            String extension = getExtension(context, uri);
            if (extension != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
        }
        return mimeType;
    }
    
    /**
     * Get the file extension from a URI
     * 
     * @param context The context
     * @param uri The URI
     * @return The file extension
     */
    public static String getExtension(Context context, Uri uri) {
        String fileName = getFileName(context, uri);
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex >= 0) {
                return fileName.substring(dotIndex + 1).toLowerCase();
            }
        }
        return null;
    }
    
    /**
     * Copy a file from a URI to a destination file
     * 
     * @param context The context
     * @param uri The source URI
     * @param destFile The destination file
     * @throws IOException If an I/O error occurs
     */
    public static void copyFile(Context context, Uri uri, File destFile) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(destFile)) {
            if (inputStream == null) {
                throw new IOException("Failed to open input stream");
            }
            IOUtils.copy(inputStream, outputStream);
        }
    }
    
    /**
     * Read text from a URI
     * 
     * @param context The context
     * @param uri The URI
     * @return The text content
     * @throws IOException If an I/O error occurs
     */
    public static String readTextFromUri(Context context, Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            if (inputStream == null) {
                throw new IOException("Failed to open input stream");
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
        }
        return stringBuilder.toString();
    }
    
    /**
     * Create a temporary file from a URI
     * 
     * @param context The context
     * @param uri The URI
     * @param prefix The file prefix
     * @param suffix The file suffix
     * @return The temporary file
     * @throws IOException If an I/O error occurs
     */
    public static File createTempFileFromUri(Context context, Uri uri, String prefix, String suffix) throws IOException {
        File tempFile = File.createTempFile(prefix, suffix, context.getCacheDir());
        copyFile(context, uri, tempFile);
        return tempFile;
    }
}
