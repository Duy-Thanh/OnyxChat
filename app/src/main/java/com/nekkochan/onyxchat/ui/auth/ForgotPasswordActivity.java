package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivityForgotPasswordBinding;
import com.nekkochan.onyxchat.network.ApiClient;

/**
 * Password reset screen
 */
public class ForgotPasswordActivity extends AppCompatActivity {
    
    private ActivityForgotPasswordBinding binding;
    private ApiClient apiClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize API client
        apiClient = ApiClient.getInstance(this);
        
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
        binding.resetButton.setOnClickListener(v -> attemptPasswordReset());
        binding.backToLoginText.setOnClickListener(v -> onBackPressed());
    }
    
    /**
     * Attempt to reset the password
     */
    private void attemptPasswordReset() {
        // Clear errors
        binding.emailInputLayout.setError(null);
        
        // Get values
        String email = binding.emailInput.getText().toString().trim();
        
        // Validate fields
        boolean cancel = false;
        View focusView = null;
        
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError(getString(R.string.error_invalid_email));
            focusView = binding.emailInput;
            cancel = true;
        }
        
        if (cancel) {
            // Focus the field with an error
            focusView.requestFocus();
        } else {
            // Show progress
            showProgress(true);
            
            // Request password reset OTP
            apiClient.requestPasswordReset(email, new ApiClient.ApiCallback<ApiClient.ApiResponse>() {
                @Override
                public void onSuccess(ApiClient.ApiResponse response) {
                    showProgress(false);
                    
                    // In development mode, the OTP might be returned in the response
                    String otp = null;
                    if (response.data != null && response.data.has("otp")) {
                        otp = response.data.get("otp").getAsString();
                    }
                    
                    // Start OTP verification activity
                    Intent intent = new Intent(ForgotPasswordActivity.this, VerifyOtpActivity.class);
                    intent.putExtra("email", email);
                    if (otp != null) {
                        intent.putExtra("otp", otp); // Only in development
                    }
                    startActivity(intent);
                }
                
                @Override
                public void onFailure(String errorMessage) {
                    showProgress(false);
                    Toast.makeText(ForgotPasswordActivity.this, errorMessage, Toast.LENGTH_LONG).show();
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
     * Go back to login screen
     */
    private void goBackToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}