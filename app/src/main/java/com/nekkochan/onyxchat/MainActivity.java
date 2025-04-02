package com.nekkochan.onyxchat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

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
 * Main activity for the OnyxChat application.
 */
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fab;
    private MainViewModel viewModel;
    private UserSessionManager sessionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        
        super.onCreate(savedInstanceState);
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_main);
        
        // Initialize ViewModel
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
        viewPager = findViewById(R.id.viewPager);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        fab = findViewById(R.id.fab);
        
        // Set up ViewPager and adapter
        setupViewPager();
        
        // Set up bottom navigation
        bottomNavigation.setOnNavigationItemSelectedListener(this);
        
        // Set up FAB
        fab.setOnClickListener(v -> {
            // Action depends on current tab
            int currentItem = viewPager.getCurrentItem();
            if (currentItem == 0) { // Messages tab
                // TODO: Start new conversation
                Toast.makeText(this, R.string.new_conversation, Toast.LENGTH_SHORT).show();
            } else if (currentItem == 1) { // Contacts tab
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
    
    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return new MessagesFragment();
                    case 1:
                        return new ContactsFragment();
                    case 2:
                        return new ProfileFragment();
                    default:
                        return new MessagesFragment();
                }
            }
            
            @Override
            public int getItemCount() {
                return 3;
            }
        });
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                bottomNavigation.setSelectedItemId(
                        position == 0 ? R.id.nav_messages :
                        position == 1 ? R.id.nav_contacts :
                        R.id.nav_profile);
                
                // Show/hide FAB based on page
                if (position == 0 || position == 1) {
                    fab.show();
                } else {
                    fab.hide();
                }
            }
        });
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        
        int itemId = item.getItemId();
        if (itemId == R.id.nav_messages) {
            selectedFragment = new MessagesFragment();
        } else if (itemId == R.id.nav_contacts) {
            selectedFragment = new ContactsFragment();
        } else if (itemId == R.id.nav_profile) {
            selectedFragment = new ProfileFragment();
        }
        
        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
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