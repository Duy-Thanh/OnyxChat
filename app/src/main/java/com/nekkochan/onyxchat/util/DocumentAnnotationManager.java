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
     * Draw annotations on a canvas
     * 
     * @param canvas Canvas to draw annotations on
     * @param annotations List of annotations to draw
     * @param scale Scale factor for the canvas
     */
    public static void drawAnnotations(Canvas canvas, List<Annotation> annotations, float scale) {
        if (canvas == null || annotations == null || annotations.isEmpty()) {
            return;
        }
        
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
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * scale);
        
        RectF scaledBounds = scaleRect(annotation.bounds, scale);
        canvas.drawLine(
                scaledBounds.left,
                scaledBounds.bottom,
                scaledBounds.right,
                scaledBounds.bottom,
                paint);
    }
    
    /**
     * Draw a path annotation (drawing)
     */
    private static void drawPath(Canvas canvas, Annotation annotation, float scale) {
        if (annotation.data == null || !(annotation.data instanceof List)) {
            return;
        }
        
        @SuppressWarnings("unchecked")
        List<PointF> points = (List<PointF>) annotation.data;
        
        if (points.size() < 2) {
            return;
        }
        
        Paint paint = new Paint();
        paint.setColor(annotation.color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(6 * scale);
        
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
        if (annotation.data == null || !(annotation.data instanceof String)) {
            return;
        }
        
        String text = (String) annotation.data;
        
        Paint paint = new Paint();
        paint.setColor(annotation.color);
        paint.setTextSize(14 * scale);
        paint.setAntiAlias(true);
        
        RectF scaledBounds = scaleRect(annotation.bounds, scale);
        canvas.drawText(text, scaledBounds.left, scaledBounds.top + 14 * scale, paint);
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
     * Save an annotated PDF document
     * 
     * @param context Context
     * @param fileUri URI of the original PDF document
     * @param annotations List of annotations to add to the document
     * @return File object for the annotated PDF, or null if an error occurred
     */
    public static File saveAnnotatedPdf(Context context, Uri fileUri, List<Annotation> annotations) {
        if (context == null || fileUri == null || annotations == null || annotations.isEmpty()) {
            Log.e(TAG, "Invalid parameters for saveAnnotatedPdf");
            return null;
        }
        
        try {
            // Create a temporary file for the annotated PDF
            File outputDir = context.getCacheDir();
            File outputFile = new File(outputDir, "annotated_" + System.currentTimeMillis() + ".pdf");
            
            // Open the input PDF
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                Log.e(TAG, "Could not open input stream for PDF");
                return null;
            }
            
            // Read the PDF
            byte[] pdfBytes = readBytes(inputStream);
            inputStream.close();
            
            PdfReader reader = new PdfReader(pdfBytes);
            
            // Create a new PDF document
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
            document.open();
            
            // Get the direct content for drawing
            PdfContentByte cb = writer.getDirectContent();
            
            // Group annotations by page
            Map<Integer, List<Annotation>> annotationsByPage = new HashMap<>();
            for (Annotation annotation : annotations) {
                List<Annotation> pageAnnotations = annotationsByPage.get(annotation.pageNumber);
                if (pageAnnotations == null) {
                    pageAnnotations = new ArrayList<>();
                    annotationsByPage.put(annotation.pageNumber, pageAnnotations);
                }
                pageAnnotations.add(annotation);
            }
            
            // Process each page
            int numPages = reader.getNumberOfPages();
            for (int i = 1; i <= numPages; i++) {
                // Import the page
                PdfImportedPage page = writer.getImportedPage(reader, i);
                document.newPage();
                
                // Draw the original page
                cb.addTemplate(page, 0, 0);
                
                // Get annotations for this page (0-based index)
                List<Annotation> pageAnnotations = annotationsByPage.get(i - 1);
                
                if (pageAnnotations != null && !pageAnnotations.isEmpty()) {
                    // Create a bitmap for annotations
                    float pageWidth = page.getWidth();
                    float pageHeight = page.getHeight();
                    
                    Bitmap annotationBitmap = Bitmap.createBitmap(
                            (int) pageWidth,
                            (int) pageHeight,
                            Bitmap.Config.ARGB_8888);
                    
                    Canvas canvas = new Canvas(annotationBitmap);
                    
                    // Draw annotations on the bitmap
                    drawAnnotations(canvas, pageAnnotations, 1.0f);
                    
                    // Convert the bitmap to an iText Image
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    annotationBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                    Image annotationImage = Image.getInstance(baos.toByteArray());
                    
                    // Position the annotation image
                    annotationImage.setAbsolutePosition(0, 0);
                    annotationImage.scaleToFit(pageWidth, pageHeight);
                    
                    // Add the annotation image to the page
                    cb.addImage(annotationImage);
                    
                    // Clean up
                    annotationBitmap.recycle();
                    baos.close();
                }
            }
            
            // Close the document
            document.close();
            writer.close();
            reader.close();
            
            return outputFile;
        } catch (Exception e) {
            Log.e(TAG, "Error saving annotated PDF", e);
            return null;
        }
    }
    
    /**
     * Read bytes from an input stream
     */
    private static byte[] readBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }
    
    /**
     * Annotation class
     */
    public static class Annotation {
        public String id;
        public int type;
        public int pageNumber;
        public RectF bounds;
        public int color;
        public Object data; // Text for text annotations, List<PointF> for drawing
    }
}
