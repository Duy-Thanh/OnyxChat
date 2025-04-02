package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivitySignupBinding;
import com.nekkochan.onyxchat.ui.legal.PrivacyPolicyActivity;
import com.nekkochan.onyxchat.ui.legal.TermsOfServiceActivity;

/**
 * Signup screen for the app
 */
public class SignupActivity extends AppCompatActivity {
    
    private ActivitySignupBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set up listeners
        binding.signupButton.setOnClickListener(v -> attemptSignup());
        binding.loginText.setOnClickListener(v -> openLogin());
        
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
     * Attempt to sign up with the provided credentials
     */
    private void attemptSignup() {
        // Clear errors
        binding.usernameInputLayout.setError(null);
        binding.emailInputLayout.setError(null);
        binding.passwordInputLayout.setError(null);
        binding.confirmPasswordInputLayout.setError(null);
        
        // Get values
        String username = binding.usernameInput.getText().toString().trim();
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString().trim();
        String confirmPassword = binding.confirmPasswordInput.getText().toString().trim();
        boolean termsAccepted = binding.termsCheckbox.isChecked();
        
        // Validate fields
        boolean cancel = false;
        View focusView = null;
        
        if (!termsAccepted) {
            Toast.makeText(this, R.string.error_terms_required, Toast.LENGTH_SHORT).show();
            focusView = binding.termsCheckbox;
            cancel = true;
        }
        
        if (!TextUtils.equals(password, confirmPassword)) {
            binding.confirmPasswordInputLayout.setError(getString(R.string.error_passwords_dont_match));
            focusView = binding.confirmPasswordInput;
            cancel = true;
        }
        
        if (TextUtils.isEmpty(password) || password.length() < 8) {
            binding.passwordInputLayout.setError(getString(R.string.error_invalid_password));
            focusView = binding.passwordInput;
            cancel = true;
        }
        
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError(getString(R.string.error_invalid_email));
            focusView = binding.emailInput;
            cancel = true;
        }
        
        if (TextUtils.isEmpty(username)) {
            binding.usernameInputLayout.setError(getString(R.string.error_username_required));
            focusView = binding.usernameInput;
            cancel = true;
        }
        
        if (cancel) {
            // Focus the first field with an error
            focusView.requestFocus();
        } else {
            // Show progress
            binding.signupButton.setEnabled(false);
            
            // TODO: Implement actual sign up logic
            // For now, just proceed to main activity
            proceedToMainActivity();
        }
    }
    
    /**
     * Open the login screen
     */
    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
    
    /**
     * Proceed to the main activity
     */
    private void proceedToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
} 