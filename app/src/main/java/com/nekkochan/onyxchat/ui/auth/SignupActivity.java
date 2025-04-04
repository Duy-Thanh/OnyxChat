package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivitySignupBinding;
import com.nekkochan.onyxchat.network.ApiClient;
import com.nekkochan.onyxchat.ui.legal.PrivacyPolicyActivity;
import com.nekkochan.onyxchat.ui.legal.TermsOfServiceActivity;
import com.nekkochan.onyxchat.util.UserSessionManager;

/**
 * Signup screen for the app
 */
public class SignupActivity extends AppCompatActivity {
    
    private static final String TAG = "SignupActivity";
    private ActivitySignupBinding binding;
    private UserSessionManager sessionManager;
    private ApiClient apiClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize view binding
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize session manager
        sessionManager = new UserSessionManager(this);
        
        // Initialize API client
        apiClient = ApiClient.getInstance(this);
        
        // Setup signup button
        binding.signupButton.setOnClickListener(v -> attemptSignup());
        
        // Setup login text
        binding.loginText.setOnClickListener(v -> openLoginScreen());
        
        // Set up clickable text for Terms of Service and Privacy Policy
        setupClickableTermsText();
    }
    
    /**
     * Set up clickable spans in the terms text
     */
    private void setupClickableTermsText() {
        String termsText = getString(R.string.accept_terms);
        SpannableString spannableString = new SpannableString(termsText);
        
        // Find the start of "Terms of Service"
        int termsStart = termsText.indexOf("Terms of Service");
        if (termsStart != -1) {
            spannableString.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View view) {
                    openTermsOfService();
                }
            }, termsStart, termsStart + "Terms of Service".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        // Find the start of "Privacy Policy"
        int privacyStart = termsText.indexOf("Privacy Policy");
        if (privacyStart != -1) {
            spannableString.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View view) {
                    openPrivacyPolicy();
                }
            }, privacyStart, privacyStart + "Privacy Policy".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        binding.termsCheckbox.setText(spannableString);
        binding.termsCheckbox.setMovementMethod(LinkMovementMethod.getInstance());
    }
    
    /**
     * Open the terms of service screen
     */
    private void openTermsOfService() {
        Intent intent = new Intent(this, TermsOfServiceActivity.class);
        startActivity(intent);
    }
    
    /**
     * Open the privacy policy screen
     */
    private void openPrivacyPolicy() {
        Intent intent = new Intent(this, PrivacyPolicyActivity.class);
        startActivity(intent);
    }
    
    /**
     * Attempt to sign up with the provided information
     */
    private void attemptSignup() {
        // Reset errors
        binding.usernameInput.setError(null);
        binding.emailInput.setError(null);
        binding.passwordInput.setError(null);
        binding.confirmPasswordInput.setError(null);
        
        // Get values
        String username = binding.usernameInput.getText().toString().trim();
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString();
        String confirmPassword = binding.confirmPasswordInput.getText().toString();
        String displayName = binding.displayNameInput.getText().toString().trim();
        
        // Validate inputs
        if (TextUtils.isEmpty(username)) {
            binding.usernameInput.setError(getString(R.string.error_field_required));
            binding.usernameInput.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(email)) {
            binding.emailInput.setError(getString(R.string.error_field_required));
            binding.emailInput.requestFocus();
            return;
        }
        
        if (!isValidEmail(email)) {
            binding.emailInput.setError(getString(R.string.error_invalid_email));
            binding.emailInput.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            binding.passwordInput.setError(getString(R.string.error_field_required));
            binding.passwordInput.requestFocus();
            return;
        }
        
        if (password.length() < 8) {
            binding.passwordInput.setError(getString(R.string.error_password_too_short));
            binding.passwordInput.requestFocus();
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            binding.confirmPasswordInput.setError(getString(R.string.error_passwords_dont_match));
            binding.confirmPasswordInput.requestFocus();
            return;
        }
        
        // Show progress
        binding.signupProgress.setVisibility(View.VISIBLE);
        binding.signupButton.setEnabled(false);
        
        // Register the user
        registerUser(username, email, password, displayName);
    }
    
    /**
     * Check if the email is valid
     */
    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
    
    /**
     * Register the user with the server
     */
    private void registerUser(String username, String email, String password, String displayName) {
        Log.d(TAG, "Registering user: " + username);
        
        apiClient.registerUser(username, email, password, displayName, new ApiClient.ApiCallback<ApiClient.AuthResponse>() {
            @Override
            public void onSuccess(ApiClient.AuthResponse response) {
                Log.d(TAG, "Registration successful: " + response.data.user.username);
                
                // Hide progress
                binding.signupProgress.setVisibility(View.GONE);
                binding.signupButton.setEnabled(true);
                
                // Proceed to main activity
                proceedToMainActivity();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Registration failed: " + errorMessage);
                
                // Hide progress
                binding.signupProgress.setVisibility(View.GONE);
                binding.signupButton.setEnabled(true);
                
                // Show error
                Toast.makeText(SignupActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * Open the login screen
     */
    private void openLoginScreen() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
    
    /**
     * Proceed to the main activity
     */
    private void proceedToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 