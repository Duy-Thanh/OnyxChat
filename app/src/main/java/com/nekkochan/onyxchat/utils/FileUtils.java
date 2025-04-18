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
import android.webkit.MimeTypeMap;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for file operations
 */
public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final int MAX_IMAGE_DIMENSION = 1280; // Maximum width/height for images
    private static final int VIDEO_BITRATE = 1500000; // 1.5 Mbps for videos
    private static final int MAX_FILE_SIZE_MB = 15; // 15 MB max file size

    // Static variables to track processing state
    public static boolean isMediaProcessing = false;
    public static String currentProcessingType = null;
    public static String currentMediaUri = null;

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
            
            try {
                // Try to get the data column
                String path = getDataColumn(context, uri, null, null);
                if (path != null) {
                    return path;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting path from content URI", e);
            }
            
            // If data column fails, copy the file to cache
            return copyFileToCache(context, uri);
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
        if (uri == null) {
            Log.e(TAG, "Cannot copy null URI to cache");
            return null;
        }

        String scheme = uri.getScheme();
        
        // If it's already a file path with no scheme or "file" scheme
        if (scheme == null || "file".equals(scheme)) {
            String filePath = uri.getPath();
            if (filePath != null) {
                File file = new File(filePath);
                if (file.exists() && file.canRead()) {
                    return filePath;
                } else {
                    Log.e(TAG, "File doesn't exist or can't be read: " + filePath);
                }
            }
        }
        
        try {
            String fileName = getFileName(context, uri);
            File cacheDir = new File(context.getCacheDir(), "media_files");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            File outputFile = new File(cacheDir, fileName);
            
            // If this is a content URI, use ContentResolver
            if ("content".equals(scheme)) {
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
            } 
            // For file URIs, use regular File IO
            else if (scheme == null || "file".equals(scheme)) {
                String filePath = uri.getPath();
                if (filePath != null) {
                    File inputFile = new File(filePath);
                    
                    try (FileInputStream inputStream = new FileInputStream(inputFile);
                         FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                        
                        byte[] buffer = new byte[4 * 1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        outputStream.flush();
                        return outputFile.getAbsolutePath();
                    }
                }
            }
            
            return null;
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

        try {
            // Check for null URI to prevent NullPointerException
            if (uri == null) {
                Log.e(TAG, "Cannot query null URI");
                return null;
            }
            
            try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    final int columnIndex = cursor.getColumnIndexOrThrow(column);
                    return cursor.getString(columnIndex);
                }
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

    /**
     * Create a temporary file for media attachments
     *
     * @param context The context
     * @param prefix File name prefix
     * @param extension File extension with dot
     * @return The temporary file
     * @throws IOException If file creation fails
     */
    public static File createTempFile(Context context, String prefix, String extension) throws IOException {
        File cacheDir = new File(context.getCacheDir(), "attachments");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        String fileName = prefix + "_" + UUID.randomUUID().toString() + extension;
        return new File(cacheDir, fileName);
    }
    
    /**
     * Execute an FFmpeg command asynchronously
     *
     * @param command The FFmpeg command
     * @param outputPath The output file path
     * @param future The CompletableFuture to complete when done
     */
    private static void executeFFmpegAsync(String command, String outputPath, CompletableFuture<String> future) {
        Log.d(TAG, "Executing FFmpeg command: " + command);
        
        // Set the processing state to true
        isMediaProcessing = true;
        
        FFmpegSession session = FFmpegKit.executeAsync(command, session1 -> {
            if (ReturnCode.isSuccess(session1.getReturnCode())) {
                Log.d(TAG, "FFmpeg process completed successfully");
                future.complete(outputPath);
                // Clear the processing state
                isMediaProcessing = false;
            } else if (ReturnCode.isCancel(session1.getReturnCode())) {
                Log.d(TAG, "FFmpeg process canceled");
                future.completeExceptionally(new IOException("Process canceled"));
                // Clear the processing state
                isMediaProcessing = false;
            } else {
                Log.e(TAG, "FFmpeg process failed with state: " + session1.getState() + 
                        " and return code: " + session1.getReturnCode());
                if (command.contains("-c:v mpeg4") || command.contains("-c:v libx264")) {
                    // Try fallback with copy codec (no transcoding)
                    // Remove all filters when using copy to avoid conflicts
                    String fallbackCommand = command
                        .replaceAll("-c:v \\w+", "-c:v copy")
                        .replaceAll("-vf [^ ]+", "") // Remove video filters
                        .replaceAll("-b:v [^ ]+", "") // Remove bitrate
                        .replaceAll("-maxrate [^ ]+", "") // Remove maxrate
                        .replaceAll("-bufsize [^ ]+", ""); // Remove bufsize
                    
                    Log.d(TAG, "Trying fallback command: " + fallbackCommand);
                    
                    FFmpegSession fallbackSession = FFmpegKit.executeAsync(fallbackCommand, session2 -> {
                        if (ReturnCode.isSuccess(session2.getReturnCode())) {
                            Log.d(TAG, "Fallback FFmpeg process completed successfully");
                            future.complete(outputPath);
                            // Clear the processing state
                            isMediaProcessing = false;
                        } else {
                            Log.e(TAG, "Fallback FFmpeg process also failed with state: " + 
                                    session2.getState() + " and return code: " + session2.getReturnCode());
                            
                            // Try one more fallback with just basic copy
                            String simpleFallbackCmd = String.format(
                                "-i %s -c copy -y %s",
                                command.substring(command.indexOf("-i ") + 3, command.indexOf(" -c:v")),
                                command.substring(command.lastIndexOf(" -y ") + 4));
                            
                            Log.d(TAG, "Trying simple fallback command: " + simpleFallbackCmd);
                            
                            FFmpegSession finalFallbackSession = FFmpegKit.executeAsync(simpleFallbackCmd, session3 -> {
                                if (ReturnCode.isSuccess(session3.getReturnCode())) {
                                    Log.d(TAG, "Simple fallback FFmpeg process completed successfully");
                                    future.complete(outputPath);
                                } else {
                                    Log.e(TAG, "All FFmpeg fallbacks failed");
                                    future.completeExceptionally(new IOException("Process failed with return code: " + 
                                            session1.getReturnCode()));
                                }
                                // Clear the processing state (whether it succeeded or failed)
                                isMediaProcessing = false;
                            }, log -> {
                                Log.d(TAG, "Simple fallback FFmpeg log: " + log.getMessage());
                            }, null);
                            
                            if (finalFallbackSession == null) {
                                future.completeExceptionally(new IOException("Failed to start final fallback FFmpeg process"));
                                // Clear the processing state
                                isMediaProcessing = false;
                            }
                        }
                    }, log -> {
                        Log.d(TAG, "Fallback FFmpeg log: " + log.getMessage());
                    }, null);
                    
                    if (fallbackSession == null) {
                        future.completeExceptionally(new IOException("Failed to start fallback FFmpeg process"));
                        // Clear the processing state
                        isMediaProcessing = false;
                    }
                } else {
                    future.completeExceptionally(new IOException("Process failed with return code: " + 
                            session1.getReturnCode()));
                    // Clear the processing state
                    isMediaProcessing = false;
                }
            }
        }, log -> {
            // FFmpeg logs
            Log.d(TAG, "FFmpeg log: " + log.getMessage());
        }, statistics -> {
            // Progress updates if needed
            // We can use statistics.getTime() to get the progress time in milliseconds
        });
        
        if (session == null) {
            future.completeExceptionally(new IOException("Failed to start FFmpeg process"));
            // Clear the processing state
            isMediaProcessing = false;
        }
    }
    
    /**
     * Compress an image file using FFmpeg
     *
     * @param context The context
     * @param sourceUri The source image Uri
     * @return CompletableFuture with the compressed file path
     */
    public static CompletableFuture<String> compressImage(Context context, Uri sourceUri) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String sourcePath = getPath(context, sourceUri);
        
        if (sourcePath == null) {
            future.completeExceptionally(new IOException("Could not find file path"));
            return future;
        }
        
        // Set the processing state
        isMediaProcessing = true;
        currentProcessingType = "image";
        currentMediaUri = sourceUri.toString();
        
        try {
            String fileName = getFileName(context, sourceUri);
            String extension = fileName.substring(fileName.lastIndexOf("."));
            File outputFile = createTempFile(context, "img", extension);
            
            // Fix the filter syntax by properly escaping quotes
            String quotedSourcePath = "\"" + sourcePath + "\"";
            String quotedOutputPath = "\"" + outputFile.getAbsolutePath() + "\"";
            
            String ffmpegCmd = String.format("-i %s -vf \"scale=min(%d\\,iw):-1\" -quality 85 -y %s",
                    quotedSourcePath, MAX_IMAGE_DIMENSION, quotedOutputPath);
            
            executeFFmpegAsync(ffmpegCmd, outputFile.getAbsolutePath(), future);
            
            return future;
        } catch (IOException e) {
            future.completeExceptionally(e);
            // Clear the processing state
            isMediaProcessing = false;
            return future;
        }
    }
    
    /**
     * Compress a video file using FFmpeg
     *
     * @param context The context
     * @param sourceUri The source video Uri
     * @return CompletableFuture with the compressed file path
     */
    public static CompletableFuture<String> compressVideo(Context context, Uri sourceUri) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String sourcePath = getPath(context, sourceUri);
        
        if (sourcePath == null) {
            future.completeExceptionally(new IOException("Could not find file path"));
            return future;
        }
        
        // Set the processing state
        isMediaProcessing = true;
        currentProcessingType = "video";
        currentMediaUri = sourceUri.toString();
        
        try {
            File outputFile = createTempFile(context, "video", ".mp4");
            
            // Properly quote file paths to handle spaces and special characters
            String quotedSourcePath = "\"" + sourcePath + "\"";
            String quotedOutputPath = "\"" + outputFile.getAbsolutePath() + "\"";
            
            // FFmpeg command to compress video using mpeg4 which is widely available
            // Fix the filter syntax by properly escaping quotes
            String ffmpegCmd = String.format(
                    "-i %s -c:v mpeg4 -b:v %d -maxrate %d " +
                    "-bufsize %d -vf \"scale=min(%d\\,iw):-2\" -c:a aac -b:a 128k " +
                    "-movflags +faststart -y %s",
                    quotedSourcePath, 
                    VIDEO_BITRATE, 
                    VIDEO_BITRATE * 2, 
                    VIDEO_BITRATE * 4,
                    MAX_IMAGE_DIMENSION,
                    quotedOutputPath);
            
            executeFFmpegAsync(ffmpegCmd, outputFile.getAbsolutePath(), future);
            
            return future;
        } catch (IOException e) {
            future.completeExceptionally(e);
            // Clear the processing state
            isMediaProcessing = false;
            return future;
        }
    }
    
    /**
     * Check if a file exceeds the maximum allowed size
     *
     * @param context The context
     * @param uri The file Uri
     * @return True if the file size is valid, false otherwise
     */
    public static boolean isFileSizeValid(Context context, Uri uri) {
        try {
            long fileSize = getFileSize(context, uri);
            return MimeTypeUtils.isFileSizeValid(fileSize, MAX_FILE_SIZE_MB);
        } catch (Exception e) {
            Log.e(TAG, "Error checking file size", e);
            return false;
        }
    }
    
    /**
     * Get the size of a file from Uri
     *
     * @param context The context
     * @param uri The file Uri
     * @return The file size in bytes
     */
    public static long getFileSize(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    return cursor.getLong(sizeIndex);
                }
            }
        }
        
        // If unable to query the size, get it from the file
        String path = getPath(context, uri);
        if (path != null) {
            return new File(path).length();
        }
        
        return 0;
    }

    /**
     * Get the MIME type of a file from its URI
     *
     * @param context The context
     * @param uri The URI of the file
     * @return The MIME type or null if not found
     */
    public static String getMimeType(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            String extension = getExtension(context, uri);
            if (extension != null) {
                mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            }
        }
        return mimeType;
    }
    
    /**
     * Get the file extension from a URI
     *
     * @param context The context
     * @param uri The URI
     * @return The file extension without dot
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
     * Check if media is currently being processed
     * @return true if media is being processed, false otherwise
     */
    public static boolean isMediaProcessing() {
        return isMediaProcessing;
    }

    /**
     * Get the current media processing type
     * @return The type of media being processed ("image", "video", etc.) or null if none
     */
    public static String getMediaProcessingType() {
        return currentProcessingType;
    }
} 