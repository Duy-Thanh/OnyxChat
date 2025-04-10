package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for managing document annotations
 */
public class DocumentAnnotationManager {
    private static final String TAG = "DocAnnotationManager";
    
    // Annotation types
    public static final int ANNOTATION_HIGHLIGHT = 1;
    public static final int ANNOTATION_UNDERLINE = 2;
    public static final int ANNOTATION_DRAWING = 3;
    public static final int ANNOTATION_TEXT = 4;
    
    // Default colors
    public static final int COLOR_HIGHLIGHT = Color.parseColor("#FFFF00"); // Yellow
    public static final int COLOR_UNDERLINE = Color.parseColor("#FF0000"); // Red
    public static final int COLOR_DRAWING = Color.parseColor("#0000FF"); // Blue
    public static final int COLOR_TEXT = Color.parseColor("#000000"); // Black
    
    // Store annotations for each document
    private static final Map<String, List<Annotation>> documentAnnotations = new HashMap<>();
    
    /**
     * Add an annotation to a document
     * 
     * @param documentUri URI of the document
     * @param type Annotation type (highlight, underline, drawing, text)
     * @param pageNumber Page number (0-based)
     * @param bounds Bounds of the annotation
     * @param color Color of the annotation
     * @param data Additional data (text for text annotations, path points for drawing)
     * @return Unique ID of the created annotation
     */
    public static String addAnnotation(
            String documentUri,
            int type,
            int pageNumber,
            RectF bounds,
            int color,
            @Nullable Object data) {
        
        // Create a new annotation
        Annotation annotation = new Annotation();
        annotation.id = UUID.randomUUID().toString();
        annotation.type = type;
        annotation.pageNumber = pageNumber;
        annotation.bounds = bounds;
        annotation.color = color;
        annotation.data = data;
        
        // Get or create the list of annotations for this document
        List<Annotation> annotations = documentAnnotations.get(documentUri);
        if (annotations == null) {
            annotations = new ArrayList<>();
            documentAnnotations.put(documentUri, annotations);
        }
        
        // Add the annotation to the list
        annotations.add(annotation);
        
        return annotation.id;
    }
    
    /**
     * Remove an annotation from a document
     * 
     * @param documentUri URI of the document
     * @param annotationId ID of the annotation to remove
     * @return true if the annotation was removed, false otherwise
     */
    public static boolean removeAnnotation(String documentUri, String annotationId) {
        List<Annotation> annotations = documentAnnotations.get(documentUri);
        if (annotations == null) {
            return false;
        }
        
        for (int i = 0; i < annotations.size(); i++) {
            if (annotations.get(i).id.equals(annotationId)) {
                annotations.remove(i);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get all annotations for a document
     * 
     * @param documentUri URI of the document
     * @return List of annotations for the document
     */
    public static List<Annotation> getAnnotations(String documentUri) {
        List<Annotation> annotations = documentAnnotations.get(documentUri);
        return annotations != null ? annotations : new ArrayList<>();
    }
    
    /**
     * Get annotations for a specific page of a document
     * 
     * @param documentUri URI of the document
     * @param pageNumber Page number (0-based)
     * @return List of annotations for the specified page
     */
    public static List<Annotation> getAnnotationsForPage(String documentUri, int pageNumber) {
        List<Annotation> allAnnotations = getAnnotations(documentUri);
        List<Annotation> pageAnnotations = new ArrayList<>();
        
        for (Annotation annotation : allAnnotations) {
            if (annotation.pageNumber == pageNumber) {
                pageAnnotations.add(annotation);
            }
        }
        
        return pageAnnotations;
    }
    
    /**
     * Draw annotations on a bitmap
     * 
     * @param bitmap Bitmap to draw annotations on
     * @param annotations List of annotations to draw
     * @param scale Scale factor for the bitmap
     */
    public static void drawAnnotations(Bitmap bitmap, List<Annotation> annotations, float scale) {
        Canvas canvas = new Canvas(bitmap);
        
        for (Annotation annotation : annotations) {
            switch (annotation.type) {
                case ANNOTATION_HIGHLIGHT:
                    drawHighlight(canvas, annotation, scale);
                    break;
                case ANNOTATION_UNDERLINE:
                    drawUnderline(canvas, annotation, scale);
                    break;
                case ANNOTATION_DRAWING:
                    drawPath(canvas, annotation, scale);
                    break;
                case ANNOTATION_TEXT:
                    drawText(canvas, annotation, scale);
                    break;
            }
        }
    }
    
    /**
     * Draw a highlight annotation
     */
    private static void drawHighlight(Canvas canvas, Annotation annotation, float scale) {
        Paint paint = new Paint();
        paint.setColor(annotation.color);
        paint.setAlpha(80); // Semi-transparent
        paint.setStyle(Paint.Style.FILL);
        
        RectF scaledBounds = scaleRect(annotation.bounds, scale);
        canvas.drawRect(scaledBounds, paint);
    }
    
    /**
     * Draw an underline annotation
     */
    private static void drawUnderline(Canvas canvas, Annotation annotation, float scale) {
        Paint paint = new Paint();
        paint.setColor(annotation.color);
        paint.setStrokeWidth(2 * scale);
        paint.setStyle(Paint.Style.STROKE);
        
        RectF scaledBounds = scaleRect(annotation.bounds, scale);
        canvas.drawLine(
                scaledBounds.left,
                scaledBounds.bottom,
                scaledBounds.right,
                scaledBounds.bottom,
                paint);
    }
    
    /**
     * Draw a path (drawing) annotation
     */
    @SuppressWarnings("unchecked")
    private static void drawPath(Canvas canvas, Annotation annotation, float scale) {
        if (!(annotation.data instanceof List)) {
            return;
        }
        
        List<PointF> points = (List<PointF>) annotation.data;
        if (points.size() < 2) {
            return;
        }
        
        Paint paint = new Paint();
        paint.setColor(annotation.color);
        paint.setStrokeWidth(3 * scale);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        
        Path path = new Path();
        PointF firstPoint = points.get(0);
        path.moveTo(firstPoint.x * scale, firstPoint.y * scale);
        
        for (int i = 1; i < points.size(); i++) {
            PointF point = points.get(i);
            path.lineTo(point.x * scale, point.y * scale);
        }
        
        canvas.drawPath(path, paint);
    }
    
    /**
     * Draw a text annotation
     */
    private static void drawText(Canvas canvas, Annotation annotation, float scale) {
        if (!(annotation.data instanceof String)) {
            return;
        }
        
        String text = (String) annotation.data;
        
        Paint paint = new Paint();
        paint.setColor(annotation.color);
        paint.setTextSize(14 * scale);
        paint.setAntiAlias(true);
        
        RectF scaledBounds = scaleRect(annotation.bounds, scale);
        canvas.drawText(text, scaledBounds.left, scaledBounds.top + paint.getTextSize(), paint);
    }
    
    /**
     * Scale a rectangle by a factor
     */
    private static RectF scaleRect(RectF rect, float scale) {
        return new RectF(
                rect.left * scale,
                rect.top * scale,
                rect.right * scale,
                rect.bottom * scale);
    }
    
    /**
     * Save annotations to a PDF file
     * 
     * @param context Application context
     * @param sourceUri URI of the source PDF
     * @param annotations List of annotations to add
     * @return File with annotations added, or null if an error occurred
     */
    public static File saveAnnotatedPdf(Context context, Uri sourceUri, List<Annotation> annotations) {
        try {
            // Create a temporary file for the output
            File outputFile = File.createTempFile("annotated_", ".pdf", context.getCacheDir());
            
            // Open the source PDF
            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
            if (inputStream == null) {
                throw new IOException("Could not open source PDF");
            }
            
            // Read the source PDF
            PdfReader reader = new PdfReader(inputStream);
            
            // Create a new PDF document
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            document.open();
            
            // Copy pages from source to destination
            PdfContentByte cb = writer.getDirectContent();
            int numPages = reader.getNumberOfPages();
            
            for (int i = 1; i <= numPages; i++) {
                document.newPage();
                
                // Import the page
                PdfImportedPage page = writer.getImportedPage(reader, i);
                cb.addTemplate(page, 0, 0);
                
                // Get annotations for this page (0-based page number)
                List<Annotation> pageAnnotations = new ArrayList<>();
                for (Annotation annotation : annotations) {
                    if (annotation.pageNumber == i - 1) {
                        pageAnnotations.add(annotation);
                    }
                }
                
                // Draw annotations if there are any
                if (!pageAnnotations.isEmpty()) {
                    // Create a bitmap with the same dimensions as the page
                    float pageWidth = document.getPageSize().getWidth();
                    float pageHeight = document.getPageSize().getHeight();
                    
                    Bitmap bitmap = Bitmap.createBitmap(
                            (int) pageWidth,
                            (int) pageHeight,
                            Bitmap.Config.ARGB_8888);
                    
                    // Draw annotations on the bitmap
                    drawAnnotations(bitmap, pageAnnotations, 1.0f);
                    
                    // Convert bitmap to iText Image
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                    Image annotationImage = Image.getInstance(baos.toByteArray());
                    annotationImage.setAbsolutePosition(0, 0);
                    annotationImage.scaleToFit(pageWidth, pageHeight);
                    
                    // Add the annotation image to the page
                    cb.addImage(annotationImage);
                    
                    // Clean up
                    bitmap.recycle();
                    baos.close();
                }
            }
            
            // Close the document
            document.close();
            writer.close();
            reader.close();
            inputStream.close();
            
            return outputFile;
            
        } catch (IOException | DocumentException e) {
            Log.e(TAG, "Error saving annotated PDF", e);
            return null;
        }
    }
    
    /**
     * Class representing an annotation
     */
    public static class Annotation {
        public String id;
        public int type;
        public int pageNumber;
        public RectF bounds;
        public int color;
        public Object data;
    }
}
