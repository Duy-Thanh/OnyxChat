package com.nekkochan.onyxchat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nekkochan.onyxchat.ui.ContactsFragment;
import com.nekkochan.onyxchat.ui.MessagesFragment;
import com.nekkochan.onyxchat.ui.ProfileFragment;
import com.nekkochan.onyxchat.ui.SettingsActivity;
import com.nekkochan.onyxchat.ui.auth.LoginActivity;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;
import com.nekkochan.onyxchat.util.UserSessionManager;

/**
 * Main activity for the application
 */
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {
    
    private static final String TAG = "MainActivity";
    private MainViewModel viewModel;
    private UserSessionManager sessionManager;
    
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fab;
    
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
        
        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            // Redirect to login
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        // Get the user ID from session
        String userId = sessionManager.getUserId();
        if (userId != null) {
            Log.d(TAG, "User ID from session: " + userId);
            // Set the user ID in the view model
            viewModel.setUserAddress(userId);
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
        fab.setOnClickListener(v -> {
            // Action depends on current tab
            int selectedItemId = bottomNavigation.getSelectedItemId();
            if (selectedItemId == R.id.nav_messages) {
                // Start new conversation
                Toast.makeText(this, R.string.new_conversation, Toast.LENGTH_SHORT).show();
            } else if (selectedItemId == R.id.nav_contacts) {
                // Add contact
                Toast.makeText(this, R.string.add_contact, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Default to messages fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MessagesFragment())
                    .commit();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Reconnect to chat if needed
        if (!viewModel.isChatConnected().getValue() && sessionManager.isLoggedIn()) {
            viewModel.connectToChat();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Disconnect from chat on app close
        viewModel.disconnectFromChat();
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        boolean showFab = false;
        
        int itemId = item.getItemId();
        if (itemId == R.id.nav_messages) {
            selectedFragment = new MessagesFragment();
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
     * Log out the user and return to login screen
     */
    public void logOut() {
        // Disconnect from chat
        viewModel.disconnectFromChat();
        
        // Clear session
        sessionManager.logout();
        
        // Redirect to login
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}