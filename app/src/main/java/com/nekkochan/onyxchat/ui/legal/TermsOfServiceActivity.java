package com.nekkochan.onyxchat.ui.legal;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivityLegalDocumentBinding;

/**
 * Activity that displays the Terms of Service
 */
public class TermsOfServiceActivity extends AppCompatActivity {
    
    private ActivityLegalDocumentBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLegalDocumentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setTitle(R.string.terms_of_service);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        
        // Set up WebView with terms of service content
        binding.webView.loadDataWithBaseURL(null, getTermsOfServiceContent(), "text/html", "UTF-8", null);
        
        // Hide progress bar once content is loaded
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }
    
    /**
     * Get terms of service HTML content
     */
    private String getTermsOfServiceContent() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; padding: 16px; color: #e0e0e0; background-color: #121212; }\n" +
                "        h1 { color: #bb86fc; }\n" +
                "        h2 { color: #bb86fc; }\n" +
                "        p { line-height: 1.6; }\n" +
                "        ul { padding-left: 20px; }\n" +
                "        li { margin-bottom: 8px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>OnyxChat Terms of Service</h1>\n" +
                "    <p>Last updated: " + java.time.LocalDate.now() + "</p>\n" +
                "    \n" +
                "    <p>These Terms of Service (\"Terms\") govern your use of the OnyxChat mobile application. By using OnyxChat, you agree to these Terms in full. If you disagree with any part of these Terms, you must not use our service.</p>\n" +
                "    \n" +
                "    <h2>1. Service Description</h2>\n" +
                "    <p>OnyxChat provides an encrypted messaging platform that uses post-quantum cryptography to secure communications between users. Our service is provided \"as is\" and we make no guarantees about its availability or functionality.</p>\n" +
                "    \n" +
                "    <h2>2. User Accounts</h2>\n" +
                "    <p>When creating an account, you must provide accurate information and keep it updated. You are responsible for maintaining the confidentiality of your account credentials and for all activities that occur under your account.</p>\n" +
                "    <p>You must:</p>\n" +
                "    <ul>\n" +
                "        <li>Be at least 13 years old to use our service</li>\n" +
                "        <li>Provide a valid email address for account verification</li>\n" +
                "        <li>Create a strong password and keep it secure</li>\n" +
                "        <li>Notify us immediately of any unauthorized access to your account</li>\n" +
                "    </ul>\n" +
                "    \n" +
                "    <h2>3. Acceptable Use</h2>\n" +
                "    <p>When using OnyxChat, you agree not to:</p>\n" +
                "    <ul>\n" +
                "        <li>Violate any applicable laws or regulations</li>\n" +
                "        <li>Distribute malware or engage in harmful activities</li>\n" +
                "        <li>Harass, abuse, or harm other users</li>\n" +
                "        <li>Impersonate others or provide false information</li>\n" +
                "        <li>Attempt to access other users' accounts or personal data</li>\n" +
                "        <li>Use our service for illegal activities</li>\n" +
                "        <li>Interfere with the proper functioning of the service</li>\n" +
                "    </ul>\n" +
                "    \n" +
                "    <h2>4. Intellectual Property</h2>\n" +
                "    <p>OnyxChat and its original content, features, and functionality are owned by OnyxChat and are protected by international copyright, trademark, and other intellectual property laws.</p>\n" +
                "    \n" +
                "    <h2>5. Limitation of Liability</h2>\n" +
                "    <p>To the maximum extent permitted by law, OnyxChat shall not be liable for any indirect, incidental, special, consequential, or punitive damages resulting from your use or inability to use the service.</p>\n" +
                "    \n" +
                "    <h2>6. Termination</h2>\n" +
                "    <p>We reserve the right to terminate or suspend your account and access to the service immediately, without prior notice or liability, for any reason, including if you breach these Terms.</p>\n" +
                "    \n" +
                "    <h2>7. Changes to Terms</h2>\n" +
                "    <p>We reserve the right to modify these Terms at any time. We will provide notice of significant changes by posting an update on our website or through the application. Your continued use of the service after such modifications constitutes your acceptance of the updated Terms.</p>\n" +
                "    \n" +
                "    <h2>8. Governing Law</h2>\n" +
                "    <p>These Terms shall be governed by and construed in accordance with the laws of the jurisdiction in which OnyxChat operates, without regard to its conflict of law provisions.</p>\n" +
                "    \n" +
                "    <h2>9. Contact Us</h2>\n" +
                "    <p>If you have any questions about these Terms, please contact us at:</p>\n" +
                "    <p>legal@onyxchat.example.com</p>\n" +
                "</body>\n" +
                "</html>";
    }
    
    /**
     * Simple WebViewClient to handle page loading
     */
    private static class WebViewClient extends android.webkit.WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
    }
} 