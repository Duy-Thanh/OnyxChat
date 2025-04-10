package com.nekkochan.onyxchat.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.Toast;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import androidx.core.content.FileProvider;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.rajat.pdfviewer.PdfViewerActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.viewmodel.DocumentViewModel;
import com.nekkochan.onyxchat.util.DocumentConverter;
import com.nekkochan.onyxchat.util.DocumentThumbnailGenerator;
import com.nekkochan.onyxchat.util.FileUtils;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for viewing various document formats
 */
public class DocumentViewerActivity extends AppCompatActivity {
    private static final String TAG = "DocumentViewerActivity";
    private static final String EXTRA_FILE_URI = "extra_file_uri";
    private static final String EXTRA_FILE_NAME = "extra_file_name";
    private static final String EXTRA_MIME_TYPE = "extra_mime_type";

    private DocumentViewModel viewModel;
    private TextView textContent;
    private ScrollView textScrollView;
    private TextView pageNumber;
    private TextView errorText;
    private ProgressBar progressBar;
    private ExecutorService executorService;

    private Uri fileUri;
    private String fileName;
    private String mimeType;
    private String fileExtension;
    private ImageView thumbnailView;

    /**
     * Create an intent to start the DocumentViewerActivity
     *
     * @param context  The context
     * @param fileUri  The URI of the document to open
     * @param fileName The name of the document
     * @param mimeType The MIME type of the document
     * @return The intent
     */
    public static Intent createIntent(Context context, Uri fileUri, String fileName, String mimeType) {
        Intent intent = new Intent(context, DocumentViewerActivity.class);
        intent.putExtra(EXTRA_FILE_URI, fileUri);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        intent.putExtra(EXTRA_MIME_TYPE, mimeType);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set status bar color and flags
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(getResources().getColor(R.color.primary, getTheme()));
        
        // Set content view after configuring window
        setContentView(R.layout.activity_document_viewer);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(DocumentViewModel.class);

        // Initialize UI components
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setTitle(fileName != null ? fileName : "Document Viewer");
        }
        
        // Handle window insets to avoid status bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), insets.top, view.getPaddingRight(), view.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });
        
        // Adjust toolbar height to account for status bar
        toolbar.post(() -> {
            int statusBarHeight = getStatusBarHeight();
            ViewGroup.LayoutParams params = toolbar.getLayoutParams();
            params.height = getResources().getDimensionPixelSize(R.dimen.action_bar_size) + statusBarHeight;
            toolbar.setLayoutParams(params);
            toolbar.setPadding(0, statusBarHeight, 0, 0);
        });
        
        Log.d(TAG, "Document viewer created with URI: " + fileUri + ", filename: " + fileName + ", MIME type: " + mimeType);

        textContent = findViewById(R.id.text_content);
        textScrollView = findViewById(R.id.text_scroll_view);
        pageNumber = findViewById(R.id.page_number);
        errorText = findViewById(R.id.error_text);
        progressBar = findViewById(R.id.progress_bar);
        thumbnailView = findViewById(R.id.document_thumbnail);
        
        // Set up thumbnail container
        View thumbnailContainer = findViewById(R.id.thumbnail_container);
        TextView thumbnailCaption = findViewById(R.id.thumbnail_caption);
        Button openDocumentButton = findViewById(R.id.open_document_button);

        // Get intent data
        Intent intent = getIntent();
        if (intent != null) {
            fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
            fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            mimeType = intent.getStringExtra(EXTRA_MIME_TYPE);

            // Extract file extension from fileName
            if (fileName != null && fileName.contains(".")) {
                fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            }

            if (fileName != null) {
                setTitle(fileName);
                // Set caption text for thumbnail
                thumbnailCaption.setText(fileName);
            }
            
            // Set up open document button
            openDocumentButton.setOnClickListener(v -> {
                // Hide thumbnail container and show progress
                thumbnailContainer.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                
                // Load the document
                loadDocument();
            });
        }

        // Initialize executor service for background tasks
        executorService = Executors.newSingleThreadExecutor();

        // Set up observers for ViewModel
        setupObservers();

        // Generate thumbnail instead of loading document directly
        if (fileUri != null) {
            generateDocumentThumbnail();
        } else {
            showError("Invalid document URI");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.document_viewer_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_share) {
            shareDocument();
            return true;
        } else if (itemId == R.id.action_annotate) {
            annotateDocument();
            return true;
        } else if (itemId == R.id.action_open_with) {
            openDocumentWithExternalApp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Share the current document with other apps
     */
    private void shareDocument() {
        if (fileUri == null) {
            Toast.makeText(this, "No document to share", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            
            // Create a content URI that can be shared with other apps
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".provider",
                    new File(fileUri.getPath()));
            
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Add document name as subject if available
            if (fileName != null && !fileName.isEmpty()) {
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share Document"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing document", e);
            Toast.makeText(this, "Error sharing document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Open the document with an external app
     */
    private void openDocumentWithExternalApp() {
        if (fileUri == null) {
            Toast.makeText(this, "No document to open", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(fileUri, mimeType);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            if (openIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(Intent.createChooser(openIntent, "Open With"));
            } else {
                Toast.makeText(this, "No app found to open this document type", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening document with external app", e);
            Toast.makeText(this, "Error opening document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Open the document for annotation
     */
    private void annotateDocument() {
        if (fileUri == null) {
            Toast.makeText(this, "No document to annotate", Toast.LENGTH_SHORT).show();
            return;
        }

        // Currently only PDF documents can be annotated
        if (mimeType.startsWith("application/pdf") || FileUtils.getExtension(this, fileUri).equalsIgnoreCase("pdf")) {
            // Launch the annotation activity
            Intent intent = DocumentAnnotationActivity.createIntent(this, fileUri, fileName);
            startActivity(intent);
        } else {
            // For non-PDF documents, convert to PDF first
            Toast.makeText(this, "Converting document for annotation...", Toast.LENGTH_SHORT).show();
            
            executorService.execute(() -> {
                try {
                    // Convert the document to PDF
                    String extension = FileUtils.getExtension(this, fileUri);
                    File pdfFile = DocumentConverter.convertToPdf(this, fileUri, extension);
                    if (pdfFile != null) {
                        // Create a content URI for the PDF file
                        Uri pdfUri = FileProvider.getUriForFile(
                                this,
                                getApplicationContext().getPackageName() + ".provider",
                                pdfFile);
                        
                        // Launch the annotation activity with the PDF
                        Intent intent = DocumentAnnotationActivity.createIntent(this, pdfUri, fileName);
                        runOnUiThread(() -> startActivity(intent));
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, 
                                "Could not convert document for annotation", 
                                Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error converting document for annotation", e);
                    runOnUiThread(() -> Toast.makeText(this, 
                            "Error preparing document for annotation: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                progressBar.setVisibility(View.VISIBLE);
                errorText.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.GONE);
            }
        });

        viewModel.getErrorMessage().observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                errorText.setText(errorMessage);
                errorText.setVisibility(View.VISIBLE);
            } else {
                errorText.setVisibility(View.GONE);
            }
        });

        viewModel.getCurrentPage().observe(this, page -> updatePageInfo());
        viewModel.getTotalPages().observe(this, total -> updatePageInfo());
    }

    private void updatePageInfo() {
        Integer currentPage = viewModel.getCurrentPage().getValue();
        Integer totalPages = viewModel.getTotalPages().getValue();

        if (currentPage != null && totalPages != null && totalPages > 0) {
            pageNumber.setText(String.format("%d / %d", currentPage + 1, totalPages));
            pageNumber.setVisibility(View.VISIBLE);
        } else {
            pageNumber.setVisibility(View.GONE);
        }
    }

    private void loadDocument() {
        viewModel.setIsLoading(true);
        viewModel.setErrorMessage(null);

        executorService.execute(() -> {
            try {
                String extension = FileUtils.getExtension(this, fileUri);
                if (extension == null) {
                    extension = "";
                }
                
                Log.d(TAG, "Loading document with mime type: " + mimeType + " and extension: " + extension);

                // Try to handle based on mime type and extension
                if (mimeType.startsWith("application/pdf") || extension.equalsIgnoreCase("pdf")) {
                    // Load PDF using external viewer for best compatibility
                    loadPdfDocument(fileUri);
                } else if (mimeType.startsWith("text/") || 
                           extension.equalsIgnoreCase("txt") ||
                           extension.equalsIgnoreCase("log") ||
                           extension.equalsIgnoreCase("csv") ||
                           extension.equalsIgnoreCase("json") ||
                           extension.equalsIgnoreCase("xml")) {
                    // Load text document directly
                    loadTextDocument(fileUri);
                } else {
                    // If text extraction fails or produces unreadable content, try to convert to PDF
                    String fileExtension = FileUtils.getExtension(this, fileUri);
                    File pdfFile = DocumentConverter.convertToPdf(this, fileUri, fileExtension);
                    if (pdfFile != null && pdfFile.exists()) {
                        // Create a content URI that can be shared with other apps
                        Uri contentUri = FileProvider.getUriForFile(
                                this,
                                getApplicationContext().getPackageName() + ".provider",
                                pdfFile);
                        
                        Log.d(TAG, "Created content URI for PDF: " + contentUri);
                        
                        // Try system PDF viewer first for better compatibility
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(contentUri, "application/pdf");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            
                            if (intent.resolveActivity(getPackageManager()) != null) {
                                startActivity(intent);
                                // Don't finish this activity, let the user come back if needed
                                viewModel.setIsLoading(false);
                                return;
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error launching system PDF viewer, trying built-in viewer", ex);
                        }
                        
                        // Fallback to built-in PDF viewer
                        try {
                            // Launch the PDF viewer activity from the library
                            Intent intent = new Intent(this, PdfViewerActivity.class);
                            intent.putExtra("PDF_FILE_URI", contentUri.toString());
                            intent.putExtra("PDF_TITLE", fileName != null ? fileName : "Document");
                            intent.putExtra("ENABLE_SHARE", false);
                            intent.putExtra("ENABLE_SWIPE", true);
                            intent.putExtra("NIGHT_MODE_ENABLED", false);
                            
                            // Add flags to ensure proper display
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                            
                            // Don't finish this activity, let the user come back if needed
                            viewModel.setIsLoading(false);
                        } catch (Exception e) {
                            Log.e(TAG, "Error using PDF viewer library", e);
                            // No PDF viewer available, show message
                            runOnUiThread(() -> {
                                String message = "No PDF viewer app installed. Please install a PDF viewer from the Play Store.";
                                textContent.setText(message);
                                textScrollView.setVisibility(View.VISIBLE);
                                viewModel.setIsLoading(false);
                            });
                        }
                    } else {
                        // If conversion fails, show a helpful message based on the document type
                        String docType = getDocumentTypeDescription(extension);
                        String message = "Could not preview this " + docType + ". Please try opening it with an external app.";
                        runOnUiThread(() -> {
                            textContent.setText(message);
                            textScrollView.setVisibility(View.VISIBLE);
                            viewModel.setIsLoading(false);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading document", e);
                showError("Error loading document: " + e.getMessage());
            }
        });
    }
    
    private String extractTextFromPdf(Uri pdfUri) {
        try {
            // Since we can't reliably extract text from PDF on Android without PDFBox,
            // we'll just return a message indicating the PDF needs to be viewed externally
            return "This PDF document needs to be viewed with an external PDF viewer app.\n\n" +
                   "If you don't have a PDF viewer installed, please install one from the Play Store.";
        } catch (Exception e) {
            Log.e(TAG, "Error handling PDF", e);
            return null;
        }
    }

    private void loadTextDocument(Uri uri) {
        try {
            // Try to detect encoding and read text properly
            String text;
            try {
                // First try UTF-8 encoding
                text = readTextWithEncoding(uri, "UTF-8");
            } catch (Exception e) {
                // Fallback to ISO-8859-1 for binary files
                Log.w(TAG, "Failed to read with UTF-8, trying ISO-8859-1", e);
                text = readTextWithEncoding(uri, "ISO-8859-1");
            }
            
            // Clean up text if needed
            if (text != null && text.contains("�")) {
                // Try to clean up text with encoding issues
                text = text.replaceAll("[^\\p{Print}\\p{Space}]", "?");
            }
            
            final String finalText = text;
            runOnUiThread(() -> {
                textScrollView.setVisibility(View.VISIBLE);
                textContent.setText(finalText);
                viewModel.setIsLoading(false);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading text document", e);
            showError("Error loading text document: " + e.getMessage());
        }
    }
    
    private String readTextWithEncoding(Uri uri, String encoding) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Could not open input stream for URI: " + uri);
        }
        
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, encoding))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        }
        return stringBuilder.toString();
    }

    private void loadPdfDocument(Uri uri) {
        try {
            Log.d(TAG, "Loading PDF from URI: " + uri);
            
            // Make sure we have a valid URI
            if (uri == null) {
                showError("Invalid PDF document URI");
                return;
            }
            
            // Try system PDF viewer first for better compatibility
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                if (intent.resolveActivity(getPackageManager()) != null) {
                    Log.d(TAG, "Opening PDF with system viewer");
                    startActivity(intent);
                    // Don't finish this activity, let the user come back if needed
                    viewModel.setIsLoading(false);
                    return;
                } else {
                    Log.d(TAG, "No system PDF viewer found, trying built-in viewer");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error launching system PDF viewer, trying built-in viewer", e);
            }
            
            // Fallback to the built-in PDF viewer from the PDF Viewer library
            try {
                // Launch the PDF viewer activity from the library
                Intent intent = new Intent(this, PdfViewerActivity.class);
                intent.putExtra("PDF_FILE_URI", uri.toString());
                intent.putExtra("PDF_TITLE", fileName != null ? fileName : "Document");
                intent.putExtra("ENABLE_SHARE", false);
                intent.putExtra("ENABLE_SWIPE", true);
                intent.putExtra("NIGHT_MODE_ENABLED", false);
                
                // Add flags to ensure proper display
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Log.d(TAG, "Opening PDF with built-in viewer");
                startActivity(intent);
                
                // Don't finish this activity, let the user come back if needed
                viewModel.setIsLoading(false);
            } catch (Exception e) {
                Log.e(TAG, "Error using built-in PDF viewer, showing error message", e);
                
                // If both viewers fail, show a helpful message
                runOnUiThread(() -> {
                    String message = "Could not open PDF document. Please install a PDF viewer app from the Play Store.";
                    textContent.setText(message);
                    textScrollView.setVisibility(View.VISIBLE);
                    viewModel.setIsLoading(false);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading PDF document", e);
            showError("Error loading PDF document: " + e.getMessage());
        }
    }
    
    /**
     * Check if the text is human-readable (not binary content)
     */
    private boolean isReadableText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // Check if the text contains too many non-printable characters
        int nonPrintableCount = 0;
        int totalChars = Math.min(text.length(), 1000); // Check first 1000 chars
        
        for (int i = 0; i < totalChars; i++) {
            char c = text.charAt(i);
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                nonPrintableCount++;
            }
        }
        
        // If more than 10% are non-printable, consider it binary
        return (nonPrintableCount / (double) totalChars) < 0.1;
    }
    
    /**
     * Get a user-friendly description of the document type
     */
    private String getDocumentTypeDescription(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "document";
        }
        
        switch (extension.toLowerCase()) {
            case "doc":
            case "docx":
                return "Word document";
            case "xls":
            case "xlsx":
                return "Excel spreadsheet";
            case "ppt":
            case "pptx":
                return "PowerPoint presentation";
            case "pdf":
                return "PDF document";
            case "odt":
                return "OpenDocument text";
            case "ods":
                return "OpenDocument spreadsheet";
            case "odp":
                return "OpenDocument presentation";
            default:
                return extension.toUpperCase() + " file";
        }
    }
    
    /**
     * Get the height of the status bar
     * 
     * @return The height of the status bar in pixels
     */
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void showError(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            viewModel.setIsLoading(false);
            viewModel.setErrorMessage(message);
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
        });
    }
    
    /**
     * Generate a thumbnail for the document and show it in the thumbnail view
     */
    private void generateDocumentThumbnail() {
        if (fileUri == null) {
            showError("Invalid document URI");
            return;
        }
        
        // Show progress while generating thumbnail
        progressBar.setVisibility(View.VISIBLE);
        
        // Generate thumbnail using DocumentThumbnailGenerator
        DocumentThumbnailGenerator.generateThumbnail(
                this,
                fileUri,
                mimeType,
                fileExtension,
                thumbnail -> {
                    // Update UI on main thread
                    runOnUiThread(() -> {
                        // Hide progress
                        progressBar.setVisibility(View.GONE);
                        
                        if (thumbnail != null) {
                            // Show thumbnail
                            thumbnailView.setImageBitmap(thumbnail);
                            findViewById(R.id.thumbnail_container).setVisibility(View.VISIBLE);
                        } else {
                            // If thumbnail generation fails, load document directly
                            loadDocument();
                        }
                    });
                });
    }
}
