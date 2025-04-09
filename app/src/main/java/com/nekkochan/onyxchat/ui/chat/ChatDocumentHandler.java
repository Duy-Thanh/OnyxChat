package com.nekkochan.onyxchat.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.nekkochan.onyxchat.ui.DocumentViewerActivity;
import com.nekkochan.onyxchat.util.FileUtils;

/**
 * Helper class for handling document operations in chat
 */
public class ChatDocumentHandler {
    private static final String TAG = "ChatDocumentHandler";

    /**
     * Open a document in the document viewer
     *
     * @param context  The context
     * @param fileUri  The URI of the document to open
     * @param fileName The name of the document
     */
    public static void openDocument(Context context, Uri fileUri, String fileName) {
        try {
            String mimeType = FileUtils.getMimeType(context, fileUri);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            // Start document viewer activity
            Intent intent = DocumentViewerActivity.createIntent(
                    context, fileUri, fileName, mimeType);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening document", e);
            Toast.makeText(context, "Error opening document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check if a file is a document based on its MIME type
     *
     * @param mimeType The MIME type of the file
     * @return True if the file is a document, false otherwise
     */
    public static boolean isDocument(String mimeType) {
        if (mimeType == null) {
            return false;
        }

        return mimeType.startsWith("application/pdf") ||
                mimeType.startsWith("application/msword") ||
                mimeType.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") ||
                mimeType.startsWith("application/vnd.ms-excel") ||
                mimeType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") ||
                mimeType.startsWith("application/vnd.ms-powerpoint") ||
                mimeType.startsWith("application/vnd.openxmlformats-officedocument.presentationml") ||
                mimeType.startsWith("application/vnd.oasis.opendocument.text") ||
                mimeType.startsWith("application/vnd.oasis.opendocument.spreadsheet") ||
                mimeType.startsWith("application/vnd.oasis.opendocument.presentation") ||
                mimeType.startsWith("text/plain") ||
                mimeType.startsWith("text/rtf") ||
                mimeType.startsWith("text/csv");
    }
}
