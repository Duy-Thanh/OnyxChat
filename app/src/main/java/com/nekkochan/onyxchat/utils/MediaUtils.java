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
            String inputPath = FileUtils.getPathFromUri(context, inputUri);
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
            String inputPath = FileUtils.getPathFromUri(context, inputUri);
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
            String inputPath = FileUtils.getPathFromUri(context, inputUri);
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
            String videoPath = FileUtils.getPathFromUri(context, videoUri);
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
            String videoPath = FileUtils.getPathFromUri(context, videoUri);
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
            String mediaPath = FileUtils.getPathFromUri(context, mediaUri);
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
} 