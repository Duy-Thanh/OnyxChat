package com.nekkochan.onyxchat.utils;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Utility class for file operations
 */
public class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * Get a file path from a Uri
     * 
     * @param context The context
     * @param uri The Uri to query
     * @return The file path or null if not found
     */
    @SuppressLint("NewApi")
    public static String getPath(final Context context, final Uri uri) {
        // DocumentProvider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                try {
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    return getDataColumn(context, contentUri, null, null);
                } catch (NumberFormatException e) {
                    // If id is not a number, try the "raw" format
                    if (id.startsWith("raw:")) {
                        return id.substring(4);
                    }
                    // If not raw format, copy the file to cache
                    return copyFileToCache(context, uri);
                }
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }
            // Try to get the data column
            String path = getDataColumn(context, uri, null, null);
            if (path != null) {
                return path;
            } else {
                // If data column fails, copy the file to cache
                return copyFileToCache(context, uri);
            }
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        // If all else fails, copy the file to cache
        return copyFileToCache(context, uri);
    }

    /**
     * Copy a file from a Uri to the cache directory
     * 
     * @param context The context
     * @param uri The Uri to copy
     * @return The file path or null if failed
     */
    private static String copyFileToCache(Context context, Uri uri) {
        try {
            String fileName = getFileName(context, uri);
            File cacheDir = new File(context.getCacheDir(), "media_files");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            File outputFile = new File(cacheDir, fileName);
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                
                if (inputStream == null) {
                    return null;
                }
                
                byte[] buffer = new byte[4 * 1024]; // 4k buffer
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
                return outputFile.getAbsolutePath();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying file to cache", e);
            return null;
        }
    }

    /**
     * Get file name from Uri
     * 
     * @param context The context
     * @param uri The Uri
     * @return The file name or a generated name if not found
     */
    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        // If still null, generate a unique name
        if (result == null) {
            result = "file_" + System.currentTimeMillis() + getFileExtension(uri);
        }
        return result;
    }

    /**
     * Get file extension from Uri based on MIME type
     * 
     * @param uri The Uri to check
     * @return The file extension with dot or empty string if unknown
     */
    private static String getFileExtension(Uri uri) {
        String mimeType = null;
        if ("content".equals(uri.getScheme())) {
            mimeType = uri.getScheme();
        }
        if (mimeType == null) {
            return "";
        }
        
        if (mimeType.startsWith("image/")) {
            if (mimeType.contains("jpg") || mimeType.contains("jpeg")) {
                return ".jpg";
            } else if (mimeType.contains("png")) {
                return ".png";
            } else {
                return ".img";
            }
        } else if (mimeType.startsWith("video/")) {
            return ".mp4";
        } else if (mimeType.startsWith("audio/")) {
            return ".mp3";
        }
        return "";
    }

    /**
     * Get the value of the data column for this Uri
     * 
     * @param context The context
     * @param uri The Uri to query
     * @param selection (Optional) Filter used in the query
     * @param selectionArgs (Optional) Selection arguments used in the query
     * @return The value of the _data column, which is typically a file path
     */
    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        final String column = "_data";
        final String[] projection = {column};

        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting data column", e);
        }
        return null;
    }

    /**
     * @param uri The Uri to check
     * @return Whether the Uri authority is ExternalStorageProvider
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check
     * @return Whether the Uri authority is DownloadsProvider
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check
     * @return Whether the Uri authority is MediaProvider
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check
     * @return Whether the Uri authority is Google Photos
     */
    private static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
} 