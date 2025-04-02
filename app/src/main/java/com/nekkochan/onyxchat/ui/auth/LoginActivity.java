package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivityLoginBinding;

/**
 * Login screen for the app
 */
public class LoginActivity extends AppCompatActivity {
    
    private ActivityLoginBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set up listeners
        binding.loginButton.setOnClickListener(v -> attemptLogin());
        binding.forgotPasswordText.setOnClickListener(v -> openForgotPassword());
        binding.signUpText.setOnClickListener(v -> openSignUp());
    }
    
    /**
     * Attempt to log in with the provided credentials
     */
    private void attemptLogin() {
        // Clear errors
        binding.usernameInputLayout.setError(null);
        binding.passwordInputLayout.setError(null);
        
        // Get values
        String username = binding.usernameInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString().trim();
        
        // Validate fields
        boolean cancel = false;
        View focusView = null;
        
        if (TextUtils.isEmpty(password)) {
            binding.passwordInputLayout.setError(getString(R.string.error_invalid_password));
            focusView = binding.passwordInput;
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
            binding.loginButton.setEnabled(false);
            
            // TODO: Implement actual login logic
            // For now, just proceed to main activity
            proceedToMainActivity();
        }
    }
    
    /**
     * Open the forgot password screen
     */
    private void openForgotPassword() {
        Intent intent = new Intent(this, ForgotPasswordActivity.class);
        startActivity(intent);
    }
    
    /**
     * Open the sign up screen
     */
    private void openSignUp() {
        Intent intent = new Intent(this, SignupActivity.class);
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