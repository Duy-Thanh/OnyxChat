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

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnErrorListener;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
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
public class DocumentViewerActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener, OnErrorListener {
    private static final String TAG = "DocumentViewerActivity";
    private static final String EXTRA_FILE_URI = "extra_file_uri";
    private static final String EXTRA_FILE_NAME = "extra_file_name";
    private static final String EXTRA_MIME_TYPE = "extra_mime_type";

    private DocumentViewModel viewModel;
    private PDFView pdfView;
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

        pdfView = findViewById(R.id.pdf_view);
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

        // Set up observers
        setupObservers();

        // Load document
        if (fileUri != null) {
            loadDocument();
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getErrorMessage().observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                showError(errorMessage);
            } else {
                errorText.setVisibility(View.GONE);
            }
        });

        viewModel.getCurrentPage().observe(this, currentPage -> {
            updatePageInfo();
        });

        viewModel.getTotalPages().observe(this, totalPages -> {
            updatePageInfo();
        });
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
                    // Load PDF directly
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
            pdfView.setVisibility(View.VISIBLE);
            textScrollView.setVisibility(View.GONE);

            pdfView.fromUri(uri)
                    .defaultPage(0)
                    .onPageChange(this)
                    .enableAnnotationRendering(true)
                    .onLoad(this)
                    .onError(this)
                    .load();
        });
    }

    private void loadTextDocument(Uri uri) {
        try {
            String text = FileUtils.readTextFromUri(this, uri);
            runOnUiThread(() -> {
                pdfView.setVisibility(View.GONE);
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
                    pdfView.setVisibility(View.VISIBLE);
                    textScrollView.setVisibility(View.GONE);

                    pdfView.fromFile(pdfFile)
                            .defaultPage(0)
                            .onPageChange(this)
                            .enableAnnotationRendering(true)
                            .onLoad(this)
                            .onError(this)
                            .load();
                });
            } else {
                showError("Failed to convert document to PDF");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting document", e);
            showError("Error converting document: " + e.getMessage());
        }
    }

    private void showError(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            viewModel.setIsLoading(false);
            viewModel.setErrorMessage(message);
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        viewModel.setCurrentPage(page);
        viewModel.setTotalPages(pageCount);
    }

    @Override
    public void loadComplete(int nbPages) {
        viewModel.setIsLoading(false);
        viewModel.setTotalPages(nbPages);
    }

    @Override
    public void onError(Throwable t) {
        Log.e(TAG, "Error loading PDF", t);
        showError("Error loading PDF: " + t.getMessage());
    }
}
