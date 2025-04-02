package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;
import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivityLoginBinding;
import com.nekkochan.onyxchat.util.UserSessionManager;

/**
 * Login screen for the app
 */
public class LoginActivity extends AppCompatActivity {
    
    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private UserSessionManager sessionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize session manager
        sessionManager = new UserSessionManager(this);
        
        // Check if user is already logged in
        if (sessionManager.isLoggedIn()) {
            proceedToMainActivity();
            return;
        }
        
        // Apply window insets to handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navigationBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            binding.container.setPadding(
                    binding.container.getPaddingLeft(),
                    statusBarHeight,
                    binding.container.getPaddingRight(),
                    navigationBarHeight
            );
            return WindowInsetsCompat.CONSUMED;
        });
        
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
            binding.loginProgress.setVisibility(View.VISIBLE);
            
            // Authenticate user
            authenticateUser(username, password);
        }
    }
    
    /**
     * Authenticate user credentials
     * In a real app, this would validate with a server
     */
    private void authenticateUser(String username, String password) {
        // In a real app, this would validate with server
        // For now, simulate a successful login with any credentials
        Log.d(TAG, "Authenticating user: " + username);
        
        // Simulate network delay
        binding.loginButton.postDelayed(() -> {
            // Create user session
            sessionManager.createLoginSession(username, generateUserId(username));
            
            // Hide progress
            binding.loginProgress.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            
            // Proceed to main activity
            proceedToMainActivity();
        }, 1000); // Simulated delay
    }
    
    /**
     * Generate a consistent user ID from username
     * For a real app, the server should provide the user ID
     */
    private String generateUserId(String username) {
        // Remove special characters and spaces, convert to lowercase
        String baseId = username.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        
        // Ensure ID is not empty
        if (baseId.isEmpty()) {
            baseId = "user";
        }
        
        // Add a timestamp for uniqueness
        return baseId + System.currentTimeMillis();
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