package com.nekkochan.onyxchat;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nekkochan.onyxchat.service.ChatNotificationService;
import com.nekkochan.onyxchat.ui.ContactsFragment;
import com.nekkochan.onyxchat.ui.ConversationListFragment;
import com.nekkochan.onyxchat.ui.ProfileFragment;
import com.nekkochan.onyxchat.ui.SettingsActivity;
import com.nekkochan.onyxchat.ui.auth.LoginActivity;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;
import com.nekkochan.onyxchat.util.NotificationPermissionHelper;
import com.nekkochan.onyxchat.util.UserSessionManager;
import com.nekkochan.onyxchat.network.ChatService;

/**
 * Main activity for the application
 */
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {
    
    private static final String TAG = "MainActivity";
    private MainViewModel viewModel;
    private UserSessionManager sessionManager;
    
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fab;
    
    // Permission request launcher
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_main);
        
        // Initialize view model
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        
        // Initialize session manager
        sessionManager = new UserSessionManager(this);
        
        // Register for notification permission results
        notificationPermissionLauncher = NotificationPermissionHelper.registerForPermissionResult(
                this, isGranted -> {
                    if (isGranted) {
                        // Permission granted, start service
                        startChatNotificationService();
                    } else {
                        // Permission denied, show toast
                        Toast.makeText(this, 
                                "Notification permission denied. You won't receive chat notifications.", 
                                Toast.LENGTH_LONG).show();
                    }
                });
        
        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            // Redirect to login
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        // Check if refresh token is missing for an existing user
        checkForMissingRefreshToken();
        
        // Get the user ID from session
        String userId = sessionManager.getUserId();
        if (userId != null) {
            Log.d(TAG, "User ID from session: " + userId);
            // Set the user ID in the view model
            viewModel.setUserAddress(userId);
            
            // Request notification permission if needed
            if (NotificationPermissionHelper.requestNotificationPermissionIfNeeded(
                    this, notificationPermissionLauncher)) {
                // Permission already granted or not needed
                startChatNotificationService();
            }
        }
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
        setSupportActionBar(toolbar);
        
        // Initialize views
        bottomNavigation = findViewById(R.id.bottomNavigation);
        fab = findViewById(R.id.fab);
        
        // Set up bottom navigation
        bottomNavigation.setOnNavigationItemSelectedListener(this);
        
        // Set up FAB
        fab.setOnClickListener(view -> {
            int selectedItemId = bottomNavigation.getSelectedItemId();
            if (selectedItemId == R.id.nav_messages) {
                // Action for messages tab - start new chat
                startNewChat();
            } else if (selectedItemId == R.id.nav_contacts) {
                // Action for contacts tab - add new contact
                addNewContact();
            }
        });
        
        // Set initial fragment
        if (savedInstanceState == null) {
            bottomNavigation.setSelectedItemId(R.id.nav_messages);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        String userId = sessionManager.getUserId();
        if (userId != null && !userId.isEmpty()) {
            Log.d(TAG, "User ID from session: " + userId);
            
            // Make sure the notification service is running
            startChatNotificationService();
            
            // Check if we need to connect to chat
            boolean isAlreadyConnected = viewModel.getChatService().checkConnectionStatus();
            
            if (isAlreadyConnected) {
                Log.d(TAG, "WebSocket is already connected in background service");
                // We're done - the connection status has been updated in the UI
                return;
            }
            
            // If we're not connected, try to connect
            Log.d(TAG, "WebSocket not connected or status mismatch, attempting to connect");
            boolean connected = viewModel.connectToChat();
            if (!connected) {
                Log.w(TAG, "Failed to connect to chat server, scheduling retry");
                // Schedule a retry after a delay using a Handler (safe for threading)
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    // This will run on the main thread
                    viewModel.connectToChat();
                }, 2000); // Wait 2 seconds
            }
        } else {
            Log.e(TAG, "No user ID available from session manager");
            Toast.makeText(this, "Login error: No user ID", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    }
    
    /**
     * Start a new chat
     */
    private void startNewChat() {
        Toast.makeText(this, "Start new chat", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Add a new contact
     */
    private void addNewContact() {
        Toast.makeText(this, "Add new contact", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        boolean showFab = false;
        
        int itemId = item.getItemId();
        if (itemId == R.id.nav_messages) {
            selectedFragment = new ConversationListFragment();
            showFab = true;
        } else if (itemId == R.id.nav_contacts) {
            selectedFragment = new ContactsFragment();
            showFab = true;
        } else if (itemId == R.id.nav_profile) {
            selectedFragment = new ProfileFragment();
            showFab = false;
        }
        
        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
            
            // Show/hide FAB based on the selected item
            if (showFab) {
                fab.show();
            } else {
                fab.hide();
            }
            
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            // Open settings activity
            openSettings();
            return true;
        } else if (id == R.id.action_logout) {
            // Log out
            logOut();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Open the settings activity
     */
    public void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
    /**
     * Start the chat notification service
     */
    private void startChatNotificationService() {
        Log.d(TAG, "Ensuring chat notification service is running");
        
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
     * Stop the chat notification service
     */
    private void stopChatNotificationService() {
        Log.d(TAG, "Stopping chat notification service");
        
        Intent serviceIntent = new Intent(this, ChatNotificationService.class);
        serviceIntent.setAction(ChatNotificationService.ACTION_STOP_SERVICE);
        startService(serviceIntent);
    }
    
    /**
     * Log out the user and return to login screen
     */
    public void logOut() {
        // Disconnect from chat
        viewModel.disconnectFromChat();
        
        // Stop notification service
        stopChatNotificationService();
        
        // Clear session
        sessionManager.logout();
        
        // Return to login screen
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
    
    /**
     * Check if refresh token is missing for an existing user
     */
    private void checkForMissingRefreshToken() {
        if (!sessionManager.hasRefreshToken()) {
            Log.w(TAG, "User is missing a refresh token - token refresh will fail");
            
            // Show a dialog to inform the user
            new AlertDialog.Builder(this)
                    .setTitle("Authentication Update Required")
                    .setMessage("To improve security and ensure uninterrupted service, please log out and log back in again.")
                    .setPositiveButton("Log Out Now", (dialog, which) -> {
                        // Log out the user
                        logOut();
                    })
                    .setNegativeButton("Later", (dialog, which) -> {
                        // User chooses to stay logged in, warn about possible disconnections
                        Toast.makeText(this, 
                                "You may experience disconnections until you log out and log back in", 
                                Toast.LENGTH_LONG).show();
                    })
                    .setCancelable(false)
                    .show();
        }
    }
}