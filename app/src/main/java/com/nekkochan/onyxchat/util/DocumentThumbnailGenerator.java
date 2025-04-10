package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nekkochan.onyxchat.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class for generating document thumbnails
 */
public class DocumentThumbnailGenerator {
    private static final String TAG = "DocThumbnailGenerator";
    private static final int THUMBNAIL_WIDTH = 300;
    private static final int THUMBNAIL_HEIGHT = 400;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Generate a thumbnail for a document
     * 
     * @param context Application context
     * @param uri Document URI
     * @param mimeType Document MIME type
     * @param extension Document extension
     * @param callback Callback to receive the thumbnail
     */
    public static void generateThumbnail(
            @NonNull Context context,
            @NonNull Uri uri,
            @Nullable String mimeType,
            @Nullable String extension,
            @NonNull ThumbnailCallback callback) {
        
        executor.execute(() -> {
            try {
                Bitmap thumbnail = null;
                
                // Try to generate thumbnail based on file type
                if (isPdfFile(mimeType, extension)) {
                    thumbnail = generatePdfThumbnail(context, uri);
                }
                
                // If PDF thumbnail generation failed or it's not a PDF, create a generic thumbnail
                if (thumbnail == null) {
                    thumbnail = createGenericThumbnail(context, extension);
                }
                
                // Return the thumbnail on the main thread
                final Bitmap finalThumbnail = thumbnail;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    callback.onThumbnailGenerated(finalThumbnail);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating thumbnail", e);
                // Return a generic thumbnail on error
                final Bitmap errorThumbnail = createGenericThumbnail(context, extension);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    callback.onThumbnailGenerated(errorThumbnail);
                });
            }
        });
    }
    
    /**
     * Generate a thumbnail from the first page of a PDF document
     */
    private static Bitmap generatePdfThumbnail(Context context, Uri uri) throws IOException {
        // Create a temporary file to store the PDF
        File tempFile = File.createTempFile("pdf_thumb_", ".pdf", context.getCacheDir());
        try {
            // Copy the PDF content to the temp file
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd == null) {
                    throw new IOException("Failed to open PDF file descriptor");
                }
                
                // Use PDF renderer to render the first page
                try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                    if (renderer.getPageCount() > 0) {
                        Bitmap bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        canvas.drawColor(Color.WHITE);
                        
                        // Render the first page
                        try (PdfRenderer.Page page = renderer.openPage(0)) {
                            Rect rect = new Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                            page.render(bitmap, rect, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            return bitmap;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating PDF thumbnail", e);
        } finally {
            // Clean up the temp file
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
        return null;
    }
    
    /**
     * Create a generic thumbnail with an icon based on the file type
     */
    private static Bitmap createGenericThumbnail(Context context, String extension) {
        Bitmap bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Fill background
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, bgPaint);
        
        // Draw border
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.LTGRAY);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        canvas.drawRect(1, 1, THUMBNAIL_WIDTH - 1, THUMBNAIL_HEIGHT - 1, borderPaint);
        
        // Get appropriate icon based on file type
        Drawable icon = getDocumentIcon(context, extension);
        if (icon != null) {
            // Draw icon in the center
            int iconSize = THUMBNAIL_WIDTH / 2;
            int left = (THUMBNAIL_WIDTH - iconSize) / 2;
            int top = (THUMBNAIL_HEIGHT - iconSize) / 2;
            
            icon.setBounds(left, top, left + iconSize, top + iconSize);
            icon.draw(canvas);
        }
        
        // Draw file extension at the bottom
        if (extension != null && !extension.isEmpty()) {
            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(24);
            textPaint.setTextAlign(Paint.Align.CENTER);
            
            String displayExt = extension.toUpperCase();
            canvas.drawText(displayExt, THUMBNAIL_WIDTH / 2, THUMBNAIL_HEIGHT - 30, textPaint);
        }
        
        return bitmap;
    }
    
    /**
     * Get the appropriate document icon based on file extension
     */
    private static Drawable getDocumentIcon(Context context, String extension) {
        if (extension == null || extension.isEmpty()) {
            return ContextCompat.getDrawable(context, R.drawable.ic_document_24dp);
        }
        
        switch (extension.toLowerCase()) {
            case "pdf":
                return ContextCompat.getDrawable(context, R.drawable.ic_pdf_24dp);
            case "doc":
            case "docx":
                return ContextCompat.getDrawable(context, R.drawable.ic_word_24dp);
            case "xls":
            case "xlsx":
                return ContextCompat.getDrawable(context, R.drawable.ic_excel_24dp);
            case "ppt":
            case "pptx":
                return ContextCompat.getDrawable(context, R.drawable.ic_powerpoint_24dp);
            case "txt":
                return ContextCompat.getDrawable(context, R.drawable.ic_text_24dp);
            default:
                return ContextCompat.getDrawable(context, R.drawable.ic_document_24dp);
        }
    }
    
    /**
     * Check if the file is a PDF based on MIME type or extension
     */
    private static boolean isPdfFile(String mimeType, String extension) {
        return (mimeType != null && mimeType.equals("application/pdf")) ||
               (extension != null && extension.equalsIgnoreCase("pdf"));
    }
    
    /**
     * Callback interface for thumbnail generation
     */
    public interface ThumbnailCallback {
        void onThumbnailGenerated(Bitmap thumbnail);
    }
}
