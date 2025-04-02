package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivitySignupBinding;

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