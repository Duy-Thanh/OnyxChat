package com.nekkochan.onyxchat.ui.legal;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivityLegalDocumentBinding;

/**
 * Activity that displays the Privacy Policy
 */
public class PrivacyPolicyActivity extends AppCompatActivity {
    
    private ActivityLegalDocumentBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        binding = ActivityLegalDocumentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setTitle(R.string.privacy_policy);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        
        // Apply window insets to fix status bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (view, windowInsets) -> {
            int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            view.setPadding(view.getPaddingLeft(), statusBarHeight, view.getPaddingRight(), view.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });
        
        // Set up WebView with privacy policy content
        binding.webView.loadDataWithBaseURL(null, getPrivacyPolicyContent(), "text/html", "UTF-8", null);
        
        // Hide progress bar once content is loaded
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }
    
    /**
     * Get privacy policy HTML content
     */
    private String getPrivacyPolicyContent() {
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
                "    <h1>OnyxChat Privacy Policy</h1>\n" +
                "    <p>Last updated: " + java.time.LocalDate.now() + "</p>\n" +
                "    \n" +
                "    <p>OnyxChat is committed to protecting your privacy. This Privacy Policy explains how we collect, use, and safeguard your information when you use our mobile application.</p>\n" +
                "    \n" +
                "    <h2>1. Information We Collect</h2>\n" +
                "    <p>We collect minimal personal information to provide secure messaging services:</p>\n" +
                "    <ul>\n" +
                "        <li><strong>Account Information:</strong> When you create an account, we may collect your username and email address.</li>\n" +
                "        <li><strong>Message Content:</strong> All messages are end-to-end encrypted using post-quantum cryptography. We cannot access the content of your communications.</li>\n" +
                "        <li><strong>Cryptographic Keys:</strong> Public keys are stored to facilitate secure message exchange. Private keys never leave your device.</li>\n" +
                "    </ul>\n" +
                "    \n" +
                "    <h2>2. How We Use Your Information</h2>\n" +
                "    <p>The information we collect is used solely for:</p>\n" +
                "    <ul>\n" +
                "        <li>Providing and maintaining our service</li>\n" +
                "        <li>Facilitating secure communications between users</li>\n" +
                "        <li>Account verification and management</li>\n" +
                "        <li>Improving the user experience</li>\n" +
                "    </ul>\n" +
                "    \n" +
                "    <h2>3. Data Security</h2>\n" +
                "    <p>We implement robust security measures to protect your data:</p>\n" +
                "    <ul>\n" +
                "        <li>Post-quantum encryption for all messages</li>\n" +
                "        <li>Encrypted local storage for message history</li>\n" +
                "        <li>No server-side message storage</li>\n" +
                "        <li>Option to enable additional security features like screen security</li>\n" +
                "    </ul>\n" +
                "    \n" +
                "    <h2>4. Third-Party Services</h2>\n" +
                "    <p>We do not share your personal information with third parties except as required to provide our service or comply with legal obligations.</p>\n" +
                "    \n" +
                "    <h2>5. Your Rights</h2>\n" +
                "    <p>You have the right to:</p>\n" +
                "    <ul>\n" +
                "        <li>Access the personal information we hold about you</li>\n" +
                "        <li>Request correction of inaccurate information</li>\n" +
                "        <li>Request deletion of your account and associated data</li>\n" +
                "        <li>Opt-out of non-essential data collection</li>\n" +
                "    </ul>\n" +
                "    \n" +
                "    <h2>6. Changes to This Policy</h2>\n" +
                "    <p>We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page and updating the \"Last updated\" date.</p>\n" +
                "    \n" +
                "    <h2>7. Contact Us</h2>\n" +
                "    <p>If you have any questions about this Privacy Policy, please contact us at:</p>\n" +
                "    <p>support@onyxchat.example.com</p>\n" +
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