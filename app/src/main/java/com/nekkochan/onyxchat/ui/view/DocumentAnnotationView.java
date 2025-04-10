package com.nekkochan.onyxchat.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nekkochan.onyxchat.util.DocumentAnnotationManager;
import com.nekkochan.onyxchat.util.DocumentAnnotationManager.Annotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for document annotation
 */
public class DocumentAnnotationView extends View {
    private static final String TAG = "DocAnnotationView";
    
    // Current annotation mode
    private int currentMode = DocumentAnnotationManager.ANNOTATION_DRAWING;
    
    // Current annotation color
    private int currentColor = DocumentAnnotationManager.COLOR_DRAWING;
    
    // Current page number
    private int currentPage = 0;
    
    // Document URI
    private String documentUri;
    
    // Bitmap for the current page
    private Bitmap pageBitmap;
    
    // Scale factor for the bitmap
    private float scale = 1.0f;
    
    // Paint for drawing
    private Paint paint;
    
    // Current path being drawn
    private Path currentPath;
    
    // Points for the current drawing
    private List<PointF> currentPoints;
    
    // Start point for highlight/underline
    private PointF startPoint;
    
    // Current bounds for highlight/underline
    private RectF currentBounds;
    
    // Callback for annotation changes
    private AnnotationCallback callback;
    
    // List of annotations for the current page
    private List<Annotation> pageAnnotations;
    
    public DocumentAnnotationView(Context context) {
        super(context);
        init();
    }
    
    public DocumentAnnotationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public DocumentAnnotationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(6);
        
        currentPath = new Path();
        currentPoints = new ArrayList<>();
        pageAnnotations = new ArrayList<>();
    }
    
    /**
     * Set the document URI
     */
    public void setDocumentUri(String uri) {
        documentUri = uri;
        loadAnnotations();
    }
    
    /**
     * Set the current page bitmap
     */
    public void setPageBitmap(Bitmap bitmap, float scale) {
        pageBitmap = bitmap;
        this.scale = scale;
        loadAnnotations();
        invalidate();
    }
    
    /**
     * Set the current page number
     */
    public void setCurrentPage(int page) {
        currentPage = page;
        loadAnnotations();
        invalidate();
    }
    
    /**
     * Set the annotation callback
     */
    public void setAnnotationCallback(AnnotationCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Set the current annotation mode
     */
    public void setAnnotationMode(int mode) {
        currentMode = mode;
        
        // Set the appropriate color for the mode
        switch (mode) {
            case DocumentAnnotationManager.ANNOTATION_HIGHLIGHT:
                currentColor = DocumentAnnotationManager.COLOR_HIGHLIGHT;
                break;
            case DocumentAnnotationManager.ANNOTATION_UNDERLINE:
                currentColor = DocumentAnnotationManager.COLOR_UNDERLINE;
                break;
            case DocumentAnnotationManager.ANNOTATION_DRAWING:
                currentColor = DocumentAnnotationManager.COLOR_DRAWING;
                break;
            case DocumentAnnotationManager.ANNOTATION_TEXT:
                currentColor = DocumentAnnotationManager.COLOR_TEXT;
                break;
        }
        
        // Update paint properties
        updatePaint();
    }
    
    /**
     * Set the current annotation color
     */
    public void setAnnotationColor(int color) {
        currentColor = color;
        updatePaint();
    }
    
    /**
     * Update paint properties based on current mode and color
     */
    private void updatePaint() {
        paint.setColor(currentColor);
        
        switch (currentMode) {
            case DocumentAnnotationManager.ANNOTATION_HIGHLIGHT:
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(80); // Semi-transparent
                break;
            case DocumentAnnotationManager.ANNOTATION_UNDERLINE:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * scale);
                paint.setAlpha(255); // Fully opaque
                break;
            case DocumentAnnotationManager.ANNOTATION_DRAWING:
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(6 * scale);
                paint.setAlpha(255); // Fully opaque
                break;
            case DocumentAnnotationManager.ANNOTATION_TEXT:
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(14 * scale);
                paint.setAlpha(255); // Fully opaque
                break;
        }
    }
    
    /**
     * Load annotations for the current page
     */
    private void loadAnnotations() {
        if (documentUri != null) {
            pageAnnotations = DocumentAnnotationManager.getAnnotationsForPage(documentUri, currentPage);
        } else {
            pageAnnotations = new ArrayList<>();
        }
    }
    
    /**
     * Add text annotation at the specified position
     */
    public void addTextAnnotation(float x, float y, String text) {
        if (documentUri == null) {
            return;
        }
        
        // Create bounds for the text
        RectF bounds = new RectF(x, y, x + 100, y + 20); // Approximate size
        
        // Add the annotation
        String id = DocumentAnnotationManager.addAnnotation(
                documentUri,
                DocumentAnnotationManager.ANNOTATION_TEXT,
                currentPage,
                bounds,
                currentColor,
                text);
        
        // Reload annotations
        loadAnnotations();
        invalidate();
        
        // Notify callback
        if (callback != null) {
            callback.onAnnotationAdded(id);
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw the page bitmap if available
        if (pageBitmap != null && !pageBitmap.isRecycled()) {
            canvas.drawBitmap(pageBitmap, 0, 0, null);
        }
        
        // Draw existing annotations
        if (pageAnnotations != null && !pageAnnotations.isEmpty()) {
            DocumentAnnotationManager.drawAnnotations(canvas, pageAnnotations, scale);
        }
        
        // Draw current annotation being created
        switch (currentMode) {
            case DocumentAnnotationManager.ANNOTATION_HIGHLIGHT:
            case DocumentAnnotationManager.ANNOTATION_UNDERLINE:
                if (currentBounds != null) {
                    if (currentMode == DocumentAnnotationManager.ANNOTATION_HIGHLIGHT) {
                        canvas.drawRect(currentBounds, paint);
                    } else {
                        canvas.drawLine(
                                currentBounds.left,
                                currentBounds.bottom,
                                currentBounds.right,
                                currentBounds.bottom,
                                paint);
                    }
                }
                break;
            case DocumentAnnotationManager.ANNOTATION_DRAWING:
                if (!currentPath.isEmpty()) {
                    canvas.drawPath(currentPath, paint);
                }
                break;
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStart(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touchMove(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touchUp();
                invalidate();
                break;
        }
        
        return true;
    }
    
    private void touchStart(float x, float y) {
        switch (currentMode) {
            case DocumentAnnotationManager.ANNOTATION_HIGHLIGHT:
            case DocumentAnnotationManager.ANNOTATION_UNDERLINE:
                startPoint = new PointF(x, y);
                currentBounds = new RectF(x, y, x, y);
                break;
            case DocumentAnnotationManager.ANNOTATION_DRAWING:
                currentPath.reset();
                currentPath.moveTo(x, y);
                currentPoints = new ArrayList<>();
                currentPoints.add(new PointF(x, y));
                break;
            case DocumentAnnotationManager.ANNOTATION_TEXT:
                // Text annotations are handled separately via addTextAnnotation
                break;
        }
    }
    
    private void touchMove(float x, float y) {
        switch (currentMode) {
            case DocumentAnnotationManager.ANNOTATION_HIGHLIGHT:
            case DocumentAnnotationManager.ANNOTATION_UNDERLINE:
                currentBounds.right = x;
                currentBounds.bottom = y;
                break;
            case DocumentAnnotationManager.ANNOTATION_DRAWING:
                currentPath.lineTo(x, y);
                currentPoints.add(new PointF(x, y));
                break;
        }
    }
    
    private void touchUp() {
        if (documentUri == null) {
            return;
        }
        
        String id = null;
        
        switch (currentMode) {
            case DocumentAnnotationManager.ANNOTATION_HIGHLIGHT:
            case DocumentAnnotationManager.ANNOTATION_UNDERLINE:
                if (currentBounds != null && Math.abs(currentBounds.width()) > 10 && Math.abs(currentBounds.height()) > 10) {
                    // Normalize the bounds (ensure left < right and top < bottom)
                    float left = Math.min(currentBounds.left, currentBounds.right);
                    float right = Math.max(currentBounds.left, currentBounds.right);
                    float top = Math.min(currentBounds.top, currentBounds.bottom);
                    float bottom = Math.max(currentBounds.top, currentBounds.bottom);
                    
                    RectF normalizedBounds = new RectF(left, top, right, bottom);
                    
                    // Add the annotation
                    id = DocumentAnnotationManager.addAnnotation(
                            documentUri,
                            currentMode,
                            currentPage,
                            normalizedBounds,
                            currentColor,
                            null);
                }
                
                // Reset current bounds
                currentBounds = null;
                break;
                
            case DocumentAnnotationManager.ANNOTATION_DRAWING:
                if (currentPoints.size() > 1) {
                    // Create bounds for the drawing
                    float minX = Float.MAX_VALUE;
                    float minY = Float.MAX_VALUE;
                    float maxX = 0;
                    float maxY = 0;
                    
                    for (PointF point : currentPoints) {
                        minX = Math.min(minX, point.x);
                        minY = Math.min(minY, point.y);
                        maxX = Math.max(maxX, point.x);
                        maxY = Math.max(maxY, point.y);
                    }
                    
                    RectF bounds = new RectF(minX, minY, maxX, maxY);
                    
                    // Add the annotation
                    id = DocumentAnnotationManager.addAnnotation(
                            documentUri,
                            currentMode,
                            currentPage,
                            bounds,
                            currentColor,
                            new ArrayList<>(currentPoints));
                }
                
                // Reset current path
                currentPath.reset();
                currentPoints.clear();
                break;
        }
        
        // Reload annotations
        loadAnnotations();
        
        // Notify callback
        if (callback != null && id != null) {
            callback.onAnnotationAdded(id);
        }
    }
    
    /**
     * Remove the last annotation
     */
    public void undoLastAnnotation() {
        if (documentUri == null || pageAnnotations.isEmpty()) {
            return;
        }
        
        // Get the last annotation
        Annotation lastAnnotation = pageAnnotations.get(pageAnnotations.size() - 1);
        
        // Remove it
        boolean removed = DocumentAnnotationManager.removeAnnotation(documentUri, lastAnnotation.id);
        
        if (removed) {
            // Reload annotations
            loadAnnotations();
            invalidate();
            
            // Notify callback
            if (callback != null) {
                callback.onAnnotationRemoved(lastAnnotation.id);
            }
        }
    }
    
    /**
     * Clear all annotations for the current page
     */
    public void clearAnnotations() {
        if (documentUri == null || pageAnnotations.isEmpty()) {
            return;
        }
        
        // Remove all annotations for the current page
        for (Annotation annotation : new ArrayList<>(pageAnnotations)) {
            DocumentAnnotationManager.removeAnnotation(documentUri, annotation.id);
            
            // Notify callback
            if (callback != null) {
                callback.onAnnotationRemoved(annotation.id);
            }
        }
        
        // Reload annotations
        loadAnnotations();
        invalidate();
    }
    
    /**
     * Interface for annotation callbacks
     */
    public interface AnnotationCallback {
        void onAnnotationAdded(String id);
        void onAnnotationRemoved(String id);
    }
}
