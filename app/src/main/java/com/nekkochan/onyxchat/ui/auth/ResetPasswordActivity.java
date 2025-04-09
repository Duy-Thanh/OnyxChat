package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivityResetPasswordBinding;
import com.nekkochan.onyxchat.network.ApiClient;

/**
 * Reset password screen after OTP verification
 */
public class ResetPasswordActivity extends AppCompatActivity {
    
    private ActivityResetPasswordBinding binding;
    private ApiClient apiClient;
    private String resetToken;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        binding = ActivityResetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize API client
        apiClient = ApiClient.getInstance(this);
        
        // Get reset token from intent
        resetToken = getIntent().getStringExtra("resetToken");
        if (resetToken == null) {
            Toast.makeText(this, "Error: Reset token not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Apply window insets to handle system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navigationBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            
            // Apply padding to the main container to avoid overlapping with system bars
            binding.container.setPadding(
                    binding.container.getPaddingLeft(),
                    statusBarHeight + binding.container.getPaddingTop(),
                    binding.container.getPaddingRight(),
                    navigationBarHeight + binding.container.getPaddingBottom()
            );
            
            return WindowInsetsCompat.CONSUMED;
        });
        
        // Set up listeners
        binding.backButton.setOnClickListener(v -> onBackPressed());
        binding.resetButton.setOnClickListener(v -> resetPassword());
    }
    
    /**
     * Reset the password
     */
    private void resetPassword() {
        // Clear errors
        binding.passwordInputLayout.setError(null);
        binding.confirmPasswordInputLayout.setError(null);
        
        // Get values
        String password = binding.passwordInput.getText().toString();
        String confirmPassword = binding.confirmPasswordInput.getText().toString();
        
        // Validate fields
        boolean cancel = false;
        View focusView = null;
        
        if (TextUtils.isEmpty(password)) {
            binding.passwordInputLayout.setError(getString(R.string.error_field_required));
            focusView = binding.passwordInput;
            cancel = true;
        } else if (password.length() < 8) {
            binding.passwordInputLayout.setError(getString(R.string.error_password_too_short));
            focusView = binding.passwordInput;
            cancel = true;
        }
        
        if (TextUtils.isEmpty(confirmPassword)) {
            binding.confirmPasswordInputLayout.setError(getString(R.string.error_field_required));
            focusView = binding.confirmPasswordInput;
            cancel = true;
        } else if (!password.equals(confirmPassword)) {
            binding.confirmPasswordInputLayout.setError(getString(R.string.error_passwords_dont_match));
            focusView = binding.confirmPasswordInput;
            cancel = true;
        }
        
        if (cancel) {
            // Focus the field with an error
            focusView.requestFocus();
        } else {
            // Show progress
            showProgress(true);
            
            // Reset password
            apiClient.resetPassword(resetToken, password, new ApiClient.ApiCallback<ApiClient.ApiResponse>() {
                @Override
                public void onSuccess(ApiClient.ApiResponse response) {
                    showProgress(false);
                    
                    Toast.makeText(ResetPasswordActivity.this, 
                            "Password reset successful", Toast.LENGTH_LONG).show();
                    
                    // Return to login
                    goToLogin();
                }
                
                @Override
                public void onFailure(String errorMessage) {
                    showProgress(false);
                    Toast.makeText(ResetPasswordActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    
    /**
     * Show or hide progress UI
     */
    private void showProgress(boolean show) {
        binding.resetButton.setEnabled(!show);
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Go to login screen
     */
    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finishAffinity(); // Close all activities in the stack
    }
}
