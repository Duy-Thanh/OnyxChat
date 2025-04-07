package com.nekkochan.onyxchat.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.SessionState;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * Utility class for media processing operations using FFmpeg
 */
public class MediaUtils {
    private static final String TAG = "MediaUtils";

    /**
     * Compress video file for chat sharing
     *
     * @param context       App context
     * @param inputUri      Source video URI
     * @param callback      Callback to handle the result
     */
    public static void compressVideo(Context context, Uri inputUri, MediaProcessCallback callback) {
        try {
            // Get real file path
            String inputPath = FileUtils.getPath(context, inputUri);
            if (inputPath == null) {
                callback.onError("Could not resolve file path from URI");
                return;
            }

            // Create output file
            File outputDir = context.getCacheDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File outputFile = new File(outputDir, "VID_" + timeStamp + ".mp4");
            String outputPath = outputFile.getAbsolutePath();

            // FFmpeg command for video compression
            String[] command = {
                "-i", inputPath,
                "-c:v", "libx264",
                "-crf", "28",
                "-preset", "medium",
                "-c:a", "aac",
                "-b:a", "128k",
                "-vf", "scale=720:-2",
                "-movflags", "+faststart",
                outputPath
            };

            executeFFmpegCommand(command, session -> {
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    callback.onSuccess(Uri.fromFile(outputFile));
                } else {
                    callback.onError("Video compression failed: " + session.getReturnCode());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error compressing video", e);
            callback.onError("Error compressing video: " + e.getMessage());
        }
    }

    /**
     * Convert audio file to a compatible format for chat
     *
     * @param context       App context
     * @param inputUri      Source audio URI
     * @param callback      Callback to handle the result
     */
    public static void convertAudio(Context context, Uri inputUri, MediaProcessCallback callback) {
        try {
            // Get real file path
            String inputPath = FileUtils.getPath(context, inputUri);
            if (inputPath == null) {
                callback.onError("Could not resolve file path from URI");
                return;
            }

            // Create output file
            File outputDir = context.getCacheDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File outputFile = new File(outputDir, "AUD_" + timeStamp + ".m4a");
            String outputPath = outputFile.getAbsolutePath();

            // FFmpeg command for audio conversion
            String[] command = {
                "-i", inputPath,
                "-c:a", "aac",
                "-b:a", "128k",
                outputPath
            };

            executeFFmpegCommand(command, session -> {
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    callback.onSuccess(Uri.fromFile(outputFile));
                } else {
                    callback.onError("Audio conversion failed: " + session.getReturnCode());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error converting audio", e);
            callback.onError("Error converting audio: " + e.getMessage());
        }
    }

    /**
     * Resize and compress image for chat sharing
     *
     * @param context       App context
     * @param inputUri      Source image URI
     * @param callback      Callback to handle the result
     */
    public static void compressImage(Context context, Uri inputUri, MediaProcessCallback callback) {
        try {
            // Get real file path
            String inputPath = FileUtils.getPath(context, inputUri);
            if (inputPath == null) {
                callback.onError("Could not resolve file path from URI");
                return;
            }

            // Create output file
            File outputDir = context.getCacheDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File outputFile = new File(outputDir, "IMG_" + timeStamp + ".jpg");
            String outputPath = outputFile.getAbsolutePath();

            // FFmpeg command for image compression
            String[] command = {
                "-i", inputPath,
                "-vf", "scale='min(1280,iw):-1'",
                "-quality", "85",
                outputPath
            };

            executeFFmpegCommand(command, session -> {
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    callback.onSuccess(Uri.fromFile(outputFile));
                } else {
                    callback.onError("Image compression failed: " + session.getReturnCode());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error compressing image", e);
            callback.onError("Error compressing image: " + e.getMessage());
        }
    }

    /**
     * Create a video thumbnail from a video file
     *
     * @param context       App context
     * @param videoUri      Source video URI
     * @param callback      Callback to handle the result
     */
    public static void createVideoThumbnail(Context context, Uri videoUri, MediaProcessCallback callback) {
        try {
            // Get real file path
            String videoPath = FileUtils.getPath(context, videoUri);
            if (videoPath == null) {
                callback.onError("Could not resolve file path from URI");
                return;
            }

            // Create output file
            File outputDir = context.getCacheDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File outputFile = new File(outputDir, "THUMB_" + timeStamp + ".jpg");
            String outputPath = outputFile.getAbsolutePath();

            // FFmpeg command to extract a frame at 1 second mark
            String[] command = {
                "-i", videoPath,
                "-ss", "00:00:01.000",
                "-vframes", "1",
                "-vf", "scale=320:-1",
                outputPath
            };

            executeFFmpegCommand(command, session -> {
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    callback.onSuccess(Uri.fromFile(outputFile));
                } else {
                    callback.onError("Thumbnail creation failed: " + session.getReturnCode());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error creating thumbnail", e);
            callback.onError("Error creating thumbnail: " + e.getMessage());
        }
    }

    /**
     * Extract audio from a video file
     * 
     * @param context       App context
     * @param videoUri      Source video URI
     * @param callback      Callback to handle the result
     */
    public static void extractAudioFromVideo(Context context, Uri videoUri, MediaProcessCallback callback) {
        try {
            // Get real file path
            String videoPath = FileUtils.getPath(context, videoUri);
            if (videoPath == null) {
                callback.onError("Could not resolve file path from URI");
                return;
            }

            // Create output file
            File outputDir = context.getCacheDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File outputFile = new File(outputDir, "AUDIO_" + timeStamp + ".m4a");
            String outputPath = outputFile.getAbsolutePath();

            // FFmpeg command to extract audio
            String[] command = {
                "-i", videoPath,
                "-vn",
                "-c:a", "aac",
                "-b:a", "128k",
                outputPath
            };

            executeFFmpegCommand(command, session -> {
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    callback.onSuccess(Uri.fromFile(outputFile));
                } else {
                    callback.onError("Audio extraction failed: " + session.getReturnCode());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error extracting audio", e);
            callback.onError("Error extracting audio: " + e.getMessage());
        }
    }

    /**
     * Get media file info using FFprobe
     *
     * @param context     App context
     * @param mediaUri    Media file URI
     * @return String containing media info or null if there's an error
     */
    public static String getMediaInfo(Context context, Uri mediaUri) {
        try {
            String mediaPath = FileUtils.getPath(context, mediaUri);
            if (mediaPath == null) {
                return null;
            }

            return FFprobeKit.execute("-v quiet -print_format json -show_format -show_streams " + mediaPath).getOutput();
        } catch (Exception e) {
            Log.e(TAG, "Error getting media info", e);
            return null;
        }
    }

    /**
     * Execute an FFmpeg command with the given arguments
     *
     * @param command    FFmpeg command arguments
     * @param callback   Callback for when the command completes
     */
    private static void executeFFmpegCommand(String[] command, FFmpegSessionCallback callback) {
        // Convert the string array to a single command string
        StringBuilder commandBuilder = new StringBuilder();
        for (String part : command) {
            commandBuilder.append(part).append(" ");
        }
        String commandString = commandBuilder.toString().trim();
        
        FFmpegSession session = FFmpegKit.executeAsync(commandString, 
            session1 -> {
                if (callback != null) {
                    callback.onComplete(session1);
                }
            },
            log -> Log.d(TAG, log.getMessage()),
            statistics -> {
                // Process statistics if needed
                // Log.d(TAG, "Progress: " + statistics.getTime());
            });
    }

    /**
     * Callback for FFmpeg session completion
     */
    private interface FFmpegSessionCallback {
        void onComplete(FFmpegSession session);
    }

    /**
     * Callback for media processing operations
     */
    public interface MediaProcessCallback {
        void onSuccess(Uri outputUri);
        void onError(String errorMessage);
    }

    /**
     * Check if a file's size is within the specified limit
     * @param context Application context
     * @param uri File URI to check
     * @param maxSizeMB Maximum file size in megabytes
     * @return true if file size is within limits, false otherwise
     */
    public static boolean isFileSizeValid(Context context, Uri uri, int maxSizeMB) {
        try {
            long fileSize = getFileSize(context, uri);
            long maxSizeBytes = maxSizeMB * 1024 * 1024L; // Convert MB to bytes
            return fileSize <= maxSizeBytes;
        } catch (Exception e) {
            Log.e(TAG, "Error checking file size", e);
            return false;
        }
    }
    
    /**
     * Get the size of a file from its URI
     * @param context Application context
     * @param uri File URI
     * @return File size in bytes
     */
    public static long getFileSize(Context context, Uri uri) {
        long fileSize = 0;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            if (cursor != null) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                cursor.moveToFirst();
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex);
                }
                cursor.close();
            } else {
                // If cursor is null, try to get file size using other method
                try (InputStream inputStream = contentResolver.openInputStream(uri)) {
                    if (inputStream != null) {
                        fileSize = inputStream.available();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file size", e);
        }
        return fileSize;
    }
    
    /**
     * Get MIME type of a file from its URI
     * @param context Application context
     * @param uri File URI
     * @return MIME type of the file
     */
    public static String getMimeType(Context context, Uri uri) {
        String mimeType = null;
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = context.getContentResolver();
            mimeType = contentResolver.getType(uri);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return mimeType;
    }
    
    /**
     * Copy file from URI to app's cache directory
     * @param context Application context
     * @param uri Source file URI
     * @return File object pointing to the copied file
     * @throws IOException If file copying fails
     */
    public static File copyFileToCache(Context context, Uri uri) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        String fileName = getFileName(context, uri);
        
        // Create a unique file name if original is not available
        if (fileName == null) {
            String mimeType = getMimeType(context, uri);
            String extension = getExtensionFromMimeType(mimeType);
            fileName = UUID.randomUUID().toString() + "." + extension;
        }
        
        File destinationFile = new File(context.getCacheDir(), fileName);
        
        try (InputStream inputStream = contentResolver.openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            
            if (inputStream == null) {
                throw new IOException("Failed to open input stream");
            }
            
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            return destinationFile;
        }
    }
    
    /**
     * Get the original file name from URI
     * @param context Application context
     * @param uri File URI
     * @return Original file name or null if not available
     */
    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get filename", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    
    /**
     * Get file extension from MIME type
     * @param mimeType MIME type string
     * @return File extension corresponding to the MIME type
     */
    private static String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return "bin";
        
        String[] parts = mimeType.split("/");
        if (parts.length < 2) return "bin";
        
        String subType = parts[1];
        switch (subType) {
            case "jpeg":
                return "jpg";
            case "png":
                return "png";
            case "gif":
                return "gif";
            case "mp4":
                return "mp4";
            case "3gpp":
                return "3gp";
            case "quicktime":
                return "mov";
            case "pdf":
                return "pdf";
            case "msword":
                return "doc";
            case "vnd.openxmlformats-officedocument.wordprocessingml.document":
                return "docx";
            default:
                // Try to use the subtype as extension
                if (subType.contains("+")) {
                    return subType.split("\\+")[0];
                }
                return subType;
        }
    }
    
    /**
     * Check if a MIME type represents an image
     * @param mimeType MIME type to check
     * @return true if the MIME type is for an image, false otherwise
     */
    public static boolean isImageMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }
    
    /**
     * Check if a MIME type represents a video
     * @param mimeType MIME type to check
     * @return true if the MIME type is for a video, false otherwise
     */
    public static boolean isVideoMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }
    
    /**
     * Get a File object from a URI
     * @param context Application context
     * @param uri File URI to convert
     * @return File object, or null if conversion fails
     */
    public static File getFileFromUri(Context context, Uri uri) {
        if (uri.getScheme().equals("file")) {
            return new File(uri.getPath());
        } else if (uri.getScheme().equals("content")) {
            try {
                return copyFileToCache(context, uri);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy file to cache", e);
                return null;
            }
        }
        return null;
    }
} 