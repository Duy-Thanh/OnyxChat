package com.nekkochan.onyxchat.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.rajat.pdfviewer.PdfViewerActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.viewmodel.DocumentViewModel;
import com.nekkochan.onyxchat.util.DocumentConverter;
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
        setContentView(R.layout.activity_document_viewer);
        
        // Set window to draw behind status bar
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(DocumentViewModel.class);

        // Initialize UI components
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        
        // Add padding to toolbar to avoid status bar overlap
        int statusBarHeight = getStatusBarHeight();
        toolbar.setPadding(toolbar.getPaddingLeft(), statusBarHeight,
                toolbar.getPaddingRight(), toolbar.getPaddingBottom());

        textContent = findViewById(R.id.text_content);
        textScrollView = findViewById(R.id.text_scroll_view);
        pageNumber = findViewById(R.id.page_number);
        errorText = findViewById(R.id.error_text);
        progressBar = findViewById(R.id.progress_bar);

        // Get intent data
        Intent intent = getIntent();
        if (intent != null) {
            fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
            fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            mimeType = intent.getStringExtra(EXTRA_MIME_TYPE);

            if (fileName != null) {
                setTitle(fileName);
            }
        }

        // Initialize executor service for background tasks
        executorService = Executors.newSingleThreadExecutor();

        // Set up observers for ViewModel
        setupObservers();

        // Load the document
        loadDocument();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

                if (mimeType.startsWith("application/pdf")) {
                    // Load PDF using the new library
                    loadPdfDocument(fileUri);
                } else if (mimeType.startsWith("text/")) {
                    // Load text document
                    loadTextDocument(fileUri);
                } else {
                    // Convert document to PDF
                    convertAndLoadDocument(fileUri, extension);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading document", e);
                showError("Error loading document: " + e.getMessage());
            }
        });
    }

    private void loadPdfDocument(Uri uri) {
        runOnUiThread(() -> {
            viewModel.setIsLoading(false);
            textScrollView.setVisibility(View.GONE);
            
            // Launch the PDF viewer activity from the new library
            Intent intent = new Intent(this, PdfViewerActivity.class);
            intent.putExtra("PDF_FILE_URI", uri.toString());
            intent.putExtra("PDF_TITLE", fileName != null ? fileName : "Document");
            intent.putExtra("ENABLE_SHARE", false);
            intent.putExtra("ENABLE_SWIPE", true);
            intent.putExtra("NIGHT_MODE_ENABLED", false);
            
            // Add flags to ensure proper display
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            
            // Finish this activity since we're launching another activity
            finish();
        });
    }

    private void loadTextDocument(Uri uri) {
        try {
            String text = FileUtils.readTextFromUri(this, uri);
            runOnUiThread(() -> {
                textScrollView.setVisibility(View.VISIBLE);
                textContent.setText(text);
                viewModel.setIsLoading(false);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading text document", e);
            showError("Error loading text document: " + e.getMessage());
        }
    }

    private void convertAndLoadDocument(Uri uri, String extension) {
        try {
            File pdfFile = DocumentConverter.convertToPdf(this, uri, extension);
            if (pdfFile != null && pdfFile.exists()) {
                runOnUiThread(() -> {
                    viewModel.setIsLoading(false);
                    textScrollView.setVisibility(View.GONE);
                    
                    // Launch the PDF viewer activity from the new library
                    Intent intent = new Intent(this, PdfViewerActivity.class);
                    intent.putExtra("PDF_FILE_URI", Uri.fromFile(pdfFile).toString());
                    intent.putExtra("PDF_TITLE", fileName != null ? fileName : "Document");
                    intent.putExtra("ENABLE_SHARE", false);
                    intent.putExtra("ENABLE_SWIPE", true);
                    intent.putExtra("NIGHT_MODE_ENABLED", false);
                    startActivity(intent);
                    
                    // Finish this activity since we're launching another activity
                    finish();
                });
            } else {
                showError("Failed to convert document to PDF");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting document", e);
            showError("Error converting document: " + e.getMessage());
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
}
