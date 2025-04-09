package com.nekkochan.onyxchat.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.databinding.ActivityVerifyOtpBinding;
import com.nekkochan.onyxchat.network.ApiClient;

/**
 * OTP verification screen for password reset
 */
public class VerifyOtpActivity extends AppCompatActivity {
    
    private ActivityVerifyOtpBinding binding;
    private ApiClient apiClient;
    private String email;
    private CountDownTimer resendTimer;
    private static final long RESEND_TIMEOUT = 60000; // 60 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        binding = ActivityVerifyOtpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize API client
        apiClient = ApiClient.getInstance(this);
        
        // Get email from intent
        email = getIntent().getStringExtra("email");
        if (email == null) {
            Toast.makeText(this, "Error: Email not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Check if OTP was passed directly
        String directOtp = getIntent().getStringExtra("otp");
        if (directOtp != null && !directOtp.isEmpty()) {
            binding.otpInput.setText(directOtp);
        }
        
        // Set email text
        binding.emailText.setText(email);
        
        // For development: auto-fill OTP if provided
        String devOtp = getIntent().getStringExtra("otp");
        if (devOtp != null && !devOtp.isEmpty()) {
            binding.otpInput.setText(devOtp);
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
        binding.verifyButton.setOnClickListener(v -> verifyOtp());
        binding.resendButton.setOnClickListener(v -> resendOtp());
        
        // Start resend timer
        startResendTimer();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
        }
    }
    
    /**
     * Start the timer for resend button
     */
    private void startResendTimer() {
        binding.resendButton.setEnabled(false);
        
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        
        resendTimer = new CountDownTimer(RESEND_TIMEOUT, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.resendButton.setText(getString(R.string.resend_code_in, millisUntilFinished / 1000));
            }
            
            @Override
            public void onFinish() {
                binding.resendButton.setText(R.string.resend_code);
                binding.resendButton.setEnabled(true);
            }
        }.start();
    }
    
    /**
     * Verify the OTP entered by the user
     */
    private void verifyOtp() {
        // Clear errors
        binding.otpInputLayout.setError(null);
        
        // Get values
        String otp = binding.otpInput.getText().toString().trim();
        
        // Validate fields
        boolean cancel = false;
        View focusView = null;
        
        if (TextUtils.isEmpty(otp)) {
            binding.otpInputLayout.setError(getString(R.string.error_field_required));
            focusView = binding.otpInput;
            cancel = true;
        } else if (otp.length() != 6) {
            binding.otpInputLayout.setError(getString(R.string.error_invalid_otp));
            focusView = binding.otpInput;
            cancel = true;
        }
        
        if (cancel) {
            // Focus the field with an error
            focusView.requestFocus();
        } else {
            // Show progress
            showProgress(true);
            
            // Verify OTP
            apiClient.verifyResetOtp(email, otp, new ApiClient.ApiCallback<ApiClient.VerifyOtpResponse>() {
                @Override
                public void onSuccess(ApiClient.VerifyOtpResponse response) {
                    showProgress(false);
                    
                    if (response.data != null && response.data.token != null) {
                        // Start reset password activity
                        Intent intent = new Intent(VerifyOtpActivity.this, ResetPasswordActivity.class);
                        intent.putExtra("resetToken", response.data.token);
                        startActivity(intent);
                        finish(); // Close this activity
                    } else {
                        Toast.makeText(VerifyOtpActivity.this, 
                                "Error: Reset token not received", Toast.LENGTH_LONG).show();
                    }
                }
                
                @Override
                public void onFailure(String errorMessage) {
                    showProgress(false);
                    Toast.makeText(VerifyOtpActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    
    /**
     * Resend OTP to the user's email
     */
    private void resendOtp() {
        // Show progress
        showProgress(true);
        
        // Request password reset OTP again
        apiClient.requestPasswordReset(email, new ApiClient.ApiCallback<ApiClient.ApiResponse>() {
            @Override
            public void onSuccess(ApiClient.ApiResponse response) {
                showProgress(false);
                
                // Start resend timer
                startResendTimer();
                
                // In development mode, the OTP might be returned in the response
                if (response.data != null && response.data.has("otp")) {
                    String otp = response.data.get("otp").getAsString();
                    binding.otpInput.setText(otp);
                }
                
                Toast.makeText(VerifyOtpActivity.this, 
                        "A new verification code has been sent to your email", 
                        Toast.LENGTH_LONG).show();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                showProgress(false);
                Toast.makeText(VerifyOtpActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * Show or hide progress UI
     */
    private void showProgress(boolean show) {
        binding.verifyButton.setEnabled(!show);
        binding.resendButton.setEnabled(!show && !binding.resendButton.getText().toString().contains(":"));
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
