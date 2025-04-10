package com.nekkochan.onyxchat.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.view.DocumentAnnotationView;
import com.nekkochan.onyxchat.util.DocumentAnnotationManager;
import com.nekkochan.onyxchat.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for annotating PDF documents
 */
public class DocumentAnnotationActivity extends AppCompatActivity implements DocumentAnnotationView.AnnotationCallback {
    private static final String TAG = "DocAnnotationActivity";
    private static final String EXTRA_FILE_URI = "extra_file_uri";
    private static final String EXTRA_FILE_NAME = "extra_file_name";
    
    private Uri fileUri;
    private String fileName;
    private String documentUriString;
    
    private DocumentAnnotationView annotationView;
    private TextView pageNumberText;
    private Button prevPageButton;
    private Button nextPageButton;
    
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private int currentPageIndex = 0;
    private int pageCount = 0;
    
    private ExecutorService executorService;
    
    /**
     * Create an intent to start the DocumentAnnotationActivity
     *
     * @param context  The context
     * @param fileUri  The URI of the document to annotate
     * @param fileName The name of the document
     * @return The intent
     */
    public static Intent createIntent(Context context, Uri fileUri, String fileName) {
        Intent intent = new Intent(context, DocumentAnnotationActivity.class);
        intent.putExtra(EXTRA_FILE_URI, fileUri);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        return intent;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_annotation);
        
        // Initialize UI components
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        
        // Initialize views
        annotationView = findViewById(R.id.annotation_view);
        pageNumberText = findViewById(R.id.page_number_text);
        prevPageButton = findViewById(R.id.prev_page_button);
        nextPageButton = findViewById(R.id.next_page_button);
        
        // Set up annotation tools
        ImageButton drawButton = findViewById(R.id.draw_button);
        ImageButton highlightButton = findViewById(R.id.highlight_button);
        ImageButton underlineButton = findViewById(R.id.underline_button);
        ImageButton textButton = findViewById(R.id.text_button);
        ImageButton undoButton = findViewById(R.id.undo_button);
        ImageButton colorButton = findViewById(R.id.color_button);
        
        // Set up button click listeners
        drawButton.setOnClickListener(v -> setAnnotationMode(DocumentAnnotationManager.ANNOTATION_DRAWING));
        highlightButton.setOnClickListener(v -> setAnnotationMode(DocumentAnnotationManager.ANNOTATION_HIGHLIGHT));
        underlineButton.setOnClickListener(v -> setAnnotationMode(DocumentAnnotationManager.ANNOTATION_UNDERLINE));
        textButton.setOnClickListener(v -> showTextAnnotationDialog());
        undoButton.setOnClickListener(v -> annotationView.undoLastAnnotation());
        colorButton.setOnClickListener(v -> showColorPickerDialog());
        
        prevPageButton.setOnClickListener(v -> showPreviousPage());
        nextPageButton.setOnClickListener(v -> showNextPage());
        
        // Set annotation callback
        annotationView.setAnnotationCallback(this);
        
        // Initialize executor service for background tasks
        executorService = Executors.newSingleThreadExecutor();
        
        // Get intent data
        Intent intent = getIntent();
        if (intent != null) {
            fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
            fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            
            if (actionBar != null && fileName != null) {
                actionBar.setTitle(fileName);
            }
            
            if (fileUri != null) {
                documentUriString = fileUri.toString();
                annotationView.setDocumentUri(documentUriString);
                
                // Load the document
                loadDocument();
            } else {
                showError("Invalid document URI");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Close the PDF renderer
        closeRenderer();
        
        // Shutdown the executor service
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.document_annotation_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_save) {
            saveAnnotatedDocument();
            return true;
        } else if (itemId == R.id.action_clear) {
            showClearAnnotationsDialog();
            return true;
        } else if (itemId == R.id.action_share) {
            shareAnnotatedDocument();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Load the document
     */
    private void loadDocument() {
        if (fileUri == null) {
            showError("Invalid document URI");
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Open the PDF document
                ParcelFileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(fileUri, "r");
                if (fileDescriptor != null) {
                    // Initialize the PDF renderer
                    pdfRenderer = new PdfRenderer(fileDescriptor);
                    pageCount = pdfRenderer.getPageCount();
                    
                    // Update UI on the main thread
                    runOnUiThread(() -> {
                        updatePageControls();
                        
                        // Show the first page
                        showPage(0);
                    });
                } else {
                    showError("Could not open document");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error loading document", e);
                showError("Error loading document: " + e.getMessage());
            }
        });
    }
    
    /**
     * Show a specific page
     */
    private void showPage(int pageIndex) {
        if (pdfRenderer == null) {
            return;
        }
        
        // Close the current page
        if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }
        
        // Validate page index
        if (pageIndex < 0) {
            pageIndex = 0;
        } else if (pageIndex >= pageCount) {
            pageIndex = pageCount - 1;
        }
        
        currentPageIndex = pageIndex;
        
        try {
            // Open the page
            currentPage = pdfRenderer.openPage(pageIndex);
            
            // Create a bitmap for the page
            Bitmap bitmap = Bitmap.createBitmap(
                    currentPage.getWidth(),
                    currentPage.getHeight(),
                    Bitmap.Config.ARGB_8888);
            
            // Render the page to the bitmap
            currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            
            // Calculate scale factor
            float scale = (float) annotationView.getWidth() / bitmap.getWidth();
            
            // Create a scaled bitmap
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    annotationView.getWidth(),
                    (int) (bitmap.getHeight() * scale),
                    true);
            
            // Set the bitmap and page number
            annotationView.setPageBitmap(scaledBitmap, scale);
            annotationView.setCurrentPage(pageIndex);
            
            // Update page number text
            pageNumberText.setText(String.format("%d / %d", pageIndex + 1, pageCount));
            
            // Update page controls
            updatePageControls();
            
            // Clean up the original bitmap
            if (bitmap != scaledBitmap) {
                bitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing page", e);
            showError("Error showing page: " + e.getMessage());
        }
    }
    
    /**
     * Show the previous page
     */
    private void showPreviousPage() {
        if (currentPageIndex > 0) {
            showPage(currentPageIndex - 1);
        }
    }
    
    /**
     * Show the next page
     */
    private void showNextPage() {
        if (currentPageIndex < pageCount - 1) {
            showPage(currentPageIndex + 1);
        }
    }
    
    /**
     * Update page controls (enable/disable buttons)
     */
    private void updatePageControls() {
        prevPageButton.setEnabled(currentPageIndex > 0);
        nextPageButton.setEnabled(currentPageIndex < pageCount - 1);
    }
    
    /**
     * Close the PDF renderer
     */
    private void closeRenderer() {
        if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }
        
        if (pdfRenderer != null) {
            pdfRenderer.close();
            pdfRenderer = null;
        }
    }
    
    /**
     * Set the current annotation mode
     */
    private void setAnnotationMode(int mode) {
        annotationView.setAnnotationMode(mode);
        
        // Update UI to reflect the selected mode
        ImageButton drawButton = findViewById(R.id.draw_button);
        ImageButton highlightButton = findViewById(R.id.highlight_button);
        ImageButton underlineButton = findViewById(R.id.underline_button);
        ImageButton textButton = findViewById(R.id.text_button);
        
        drawButton.setSelected(mode == DocumentAnnotationManager.ANNOTATION_DRAWING);
        highlightButton.setSelected(mode == DocumentAnnotationManager.ANNOTATION_HIGHLIGHT);
        underlineButton.setSelected(mode == DocumentAnnotationManager.ANNOTATION_UNDERLINE);
        textButton.setSelected(mode == DocumentAnnotationManager.ANNOTATION_TEXT);
    }
    
    /**
     * Show dialog for adding text annotation
     */
    private void showTextAnnotationDialog() {
        // Set annotation mode to text
        setAnnotationMode(DocumentAnnotationManager.ANNOTATION_TEXT);
        
        // Show dialog to enter text
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Text Annotation");
        
        // Set up the input
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter text");
        builder.setView(input);
        
        // Set up the buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                // Show another dialog to tap where to place the text
                Toast.makeText(this, "Tap where you want to add the text", Toast.LENGTH_SHORT).show();
                
                // Set a click listener on the annotation view
                annotationView.setOnClickListener(v -> {
                    // Get the click coordinates
                    float x = v.getX();
                    float y = v.getY();
                    
                    // Add the text annotation
                    annotationView.addTextAnnotation(x, y, text);
                    
                    // Remove the click listener
                    annotationView.setOnClickListener(null);
                });
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    /**
     * Show color picker dialog
     */
    private void showColorPickerDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_color_picker);
        
        // Set up color buttons
        View redButton = dialog.findViewById(R.id.color_red);
        View blueButton = dialog.findViewById(R.id.color_blue);
        View greenButton = dialog.findViewById(R.id.color_green);
        View yellowButton = dialog.findViewById(R.id.color_yellow);
        View blackButton = dialog.findViewById(R.id.color_black);
        View purpleButton = dialog.findViewById(R.id.color_purple);
        
        if (redButton != null) redButton.setOnClickListener(v -> {
            annotationView.setAnnotationColor(Color.RED);
            dialog.dismiss();
        });
        
        if (blueButton != null) blueButton.setOnClickListener(v -> {
            annotationView.setAnnotationColor(Color.BLUE);
            dialog.dismiss();
        });
        
        if (greenButton != null) greenButton.setOnClickListener(v -> {
            annotationView.setAnnotationColor(Color.GREEN);
            dialog.dismiss();
        });
        
        if (yellowButton != null) yellowButton.setOnClickListener(v -> {
            annotationView.setAnnotationColor(Color.YELLOW);
            dialog.dismiss();
        });
        
        if (blackButton != null) blackButton.setOnClickListener(v -> {
            annotationView.setAnnotationColor(Color.BLACK);
            dialog.dismiss();
        });
        
        if (purpleButton != null) purpleButton.setOnClickListener(v -> {
            annotationView.setAnnotationColor(Color.parseColor("#9C27B0"));
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    /**
     * Show dialog to confirm clearing all annotations
     */
    private void showClearAnnotationsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Clear Annotations");
        builder.setMessage("Are you sure you want to clear all annotations on this page?");
        
        builder.setPositiveButton("Clear", (dialog, which) -> annotationView.clearAnnotations());
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    /**
     * Save the annotated document
     */
    private void saveAnnotatedDocument() {
        if (fileUri == null) {
            showError("Invalid document URI");
            return;
        }
        
        Toast.makeText(this, "Saving annotated document...", Toast.LENGTH_SHORT).show();
        
        executorService.execute(() -> {
            try {
                // Save the annotated document
                File annotatedFile = DocumentAnnotationManager.saveAnnotatedPdf(
                        this,
                        fileUri,
                        DocumentAnnotationManager.getAnnotations(documentUriString));
                
                if (annotatedFile != null) {
                    // Create a content URI for the annotated file
                    Uri contentUri = FileProvider.getUriForFile(
                            this,
                            getApplicationContext().getPackageName() + ".provider",
                            annotatedFile);
                    
                    // Update the file URI
                    fileUri = contentUri;
                    documentUriString = fileUri.toString();
                    annotationView.setDocumentUri(documentUriString);
                    
                    // Show success message
                    runOnUiThread(() -> Toast.makeText(this, "Document saved successfully", Toast.LENGTH_SHORT).show());
                } else {
                    showError("Failed to save document");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving document", e);
                showError("Error saving document: " + e.getMessage());
            }
        });
    }
    
    /**
     * Share the annotated document
     */
    private void shareAnnotatedDocument() {
        if (fileUri == null) {
            showError("Invalid document URI");
            return;
        }
        
        Toast.makeText(this, "Preparing document for sharing...", Toast.LENGTH_SHORT).show();
        
        executorService.execute(() -> {
            try {
                // Save the annotated document
                File annotatedFile = DocumentAnnotationManager.saveAnnotatedPdf(
                        this,
                        fileUri,
                        DocumentAnnotationManager.getAnnotations(documentUriString));
                
                if (annotatedFile != null) {
                    // Create a content URI for the annotated file
                    Uri contentUri = FileProvider.getUriForFile(
                            this,
                            getApplicationContext().getPackageName() + ".provider",
                            annotatedFile);
                    
                    // Create a share intent
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/pdf");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    // Add document name as subject if available
                    if (fileName != null && !fileName.isEmpty()) {
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Annotated: " + fileName);
                    }
                    
                    // Start the sharing activity
                    runOnUiThread(() -> startActivity(Intent.createChooser(shareIntent, "Share Annotated Document")));
                } else {
                    showError("Failed to prepare document for sharing");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sharing document", e);
                showError("Error sharing document: " + e.getMessage());
            }
        });
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        Log.e(TAG, "Error: " + message);
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
    
    @Override
    public void onAnnotationAdded(String id) {
        // Annotation added, nothing to do here
    }
    
    @Override
    public void onAnnotationRemoved(String id) {
        // Annotation removed, nothing to do here
    }
}
