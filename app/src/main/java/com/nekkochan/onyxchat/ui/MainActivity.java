package com.nekkochan.onyxchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.util.ThemeUtils;
import com.nekkochan.onyxchat.util.UserSessionManager;
import com.nekkochan.onyxchat.ui.auth.LoginActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ThemeUtils themeUtils;
    private UserSessionManager userSessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        themeUtils = new ThemeUtils(this);
        userSessionManager = new UserSessionManager(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_toggle_theme) {
            themeUtils.toggleTheme();
            recreate();
            return true;
        } else if (item.getItemId() == R.id.action_logout) {
            logout();
            return true;
        } else if (item.getItemId() == R.id.action_test_token_refresh) {
            testTokenRefresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void logout() {
        Log.d(TAG, "Logging out user");
        userSessionManager.logout();
        
        // Return to login screen
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void testTokenRefresh() {
        Log.d(TAG, "Testing token refresh");
        Toast.makeText(this, "Testing token refresh...", Toast.LENGTH_SHORT).show();
        
        userSessionManager.testTokenRefresh()
            .thenAccept(success -> {
                runOnUiThread(() -> {
                    String message = success ? "Token refresh successful!" : "Token refresh failed!";
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Token refresh result: " + message);
                });
            })
            .exceptionally(e -> {
                runOnUiThread(() -> {
                    String errorMsg = "Error: " + e.getMessage();
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Token refresh error", e);
                });
                return null;
            });
    }
} 