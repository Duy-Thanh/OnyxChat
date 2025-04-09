package com.nekkochan.onyxchat.ui.auth;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.network.ApiClient;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText emailInput;
    private TextInputEditText usernameInput;
    private Button sendCodeButton;
    private ProgressBar progressBar;
    private TextView backToLoginText;
    
    // Notification channel ID
    private static final String CHANNEL_ID = "otp_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize views
        emailInput = findViewById(R.id.email_input);
        usernameInput = findViewById(R.id.username_input);
        sendCodeButton = findViewById(R.id.resetButton);
        progressBar = findViewById(R.id.progressBar);
        backToLoginText = findViewById(R.id.backToLoginText);

        // Create notification channel for Android O and above
        createNotificationChannel();

        // Set up click listeners
        sendCodeButton.setOnClickListener(v -> requestPasswordReset());
        backToLoginText.setOnClickListener(v -> finish());
    }
    
    /**
     * Create notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "OTP Notifications";
            String description = "Notifications for OTP verification codes";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void requestPasswordReset() {
        String email = emailInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();

        if (email.isEmpty()) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }

        showProgress(true);

        ApiClient apiClient = ApiClient.getInstance(this);
        apiClient.requestPasswordReset(email, username, new ApiClient.ApiCallback<ApiClient.ApiResponse>() {
            @Override
            public void onSuccess(ApiClient.ApiResponse response) {
                showProgress(false);
                
                // Check if OTP was returned directly (when username matches)
                if (response.data != null && response.data.has("otp")) {
                    try {
                        String otp = response.data.get("otp").getAsString();
                        
                        // Show notification with OTP
                        showOtpNotification(otp, email);
                        
                        // Also show dialog
                        showOtpDialog(otp, email);
                    } catch (Exception e) {
                        Toast.makeText(ForgotPasswordActivity.this, 
                                "Error reading OTP from response", Toast.LENGTH_LONG).show();
                    }
                } else {
                    // OTP was sent to email
                    Toast.makeText(ForgotPasswordActivity.this, 
                            response.message, Toast.LENGTH_LONG).show();
                    
                    // Navigate to OTP verification screen
                    Intent intent = new Intent(ForgotPasswordActivity.this, VerifyOtpActivity.class);
                    intent.putExtra("email", email);
                    startActivity(intent);
                    finish();
                }
            }
            
            @Override
            public void onFailure(String errorMessage) {
                showProgress(false);
                Toast.makeText(ForgotPasswordActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * Show a notification with the OTP code
     * @param otp The OTP code
     * @param email The user's email
     */
    private void showOtpNotification(String otp, String email) {
        // Create an intent to open the VerifyOtpActivity when notification is tapped
        Intent intent = new Intent(this, VerifyOtpActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("otp", otp);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        // Create a PendingIntent
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("OnyxChat Password Reset")
                .setContentText("Your verification code is: " + otp)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Your verification code is: " + otp + "\n\nThis code will expire in 15 minutes."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        
        // Show the notification
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
    
    private void showOtpDialog(String otp, String email) {
        new AlertDialog.Builder(this)
                .setTitle("Verification Code")
                .setMessage("Your verification code is: " + otp + "\n\nPlease use this code to reset your password.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    // Navigate to OTP verification screen with the OTP
                    Intent intent = new Intent(ForgotPasswordActivity.this, VerifyOtpActivity.class);
                    intent.putExtra("email", email);
                    intent.putExtra("otp", otp);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        sendCodeButton.setEnabled(!show);
    }
}