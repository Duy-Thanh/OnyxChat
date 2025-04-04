package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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
import com.nekkochan.onyxchat.network.ApiClient;
import com.nekkochan.onyxchat.service.ChatNotificationService;
import com.nekkochan.onyxchat.util.UserSessionManager;

/**
 * Login screen for the app
 */
public class LoginActivity extends AppCompatActivity {
    
    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private UserSessionManager sessionManager;
    private ApiClient apiClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize session manager
        sessionManager = new UserSessionManager(this);
        
        // Initialize API client
        apiClient = ApiClient.getInstance(this);
        
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
     * Attempt to login with the provided credentials
     */
    private void attemptLogin() {
        // Reset errors
        binding.usernameInput.setError(null);
        binding.passwordInput.setError(null);
        
        // Get values
        String username = binding.usernameInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString();
        
        // Validate inputs
        if (TextUtils.isEmpty(username)) {
            binding.usernameInput.setError(getString(R.string.error_field_required));
            binding.usernameInput.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            binding.passwordInput.setError(getString(R.string.error_field_required));
            binding.passwordInput.requestFocus();
            return;
        }
        
        // Show progress
        binding.loginProgress.setVisibility(View.VISIBLE);
        binding.loginButton.setEnabled(false);
        
        // Authenticate with the server
        authenticateUser(username, password);
    }
    
    /**
     * Authenticate the user with the server
     */
    private void authenticateUser(String username, String password) {
        Log.d(TAG, "Authenticating user: " + username);
        
        apiClient.login(username, password, new ApiClient.ApiCallback<ApiClient.AuthResponse>() {
            @Override
            public void onSuccess(ApiClient.AuthResponse response) {
                Log.d(TAG, "Login successful: " + response.data.user.username);
                
                // Start the notification service
                startChatNotificationService();
                
                // Hide progress
                binding.loginProgress.setVisibility(View.GONE);
                binding.loginButton.setEnabled(true);
                
                // Proceed to main activity
                proceedToMainActivity();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Login failed: " + errorMessage);
                
                // Hide progress
                binding.loginProgress.setVisibility(View.GONE);
                binding.loginButton.setEnabled(true);
                
                // Show error
                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * Start the chat notification service
     */
    private void startChatNotificationService() {
        Log.d(TAG, "Starting chat notification service");
        
        Intent serviceIntent = new Intent(this, ChatNotificationService.class);
        serviceIntent.setAction(ChatNotificationService.ACTION_START_SERVICE);
        
        // For Android O and above, we need to start as a foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
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
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 