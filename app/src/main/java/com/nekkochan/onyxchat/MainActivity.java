package com.nekkochan.onyxchat;

import android.content.Intent;
import android.os.Bundle;
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
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

/**
 * Main activity for the OnyxChat application.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fab;
    private MainViewModel viewModel;
    
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
        setupBottomNavigation();
        
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
        
        // Connect to chat server
        connectToChat();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Reconnect to chat if disconnected
        if (viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            connectToChat();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Disconnect from chat when app is closed
        viewModel.disconnectFromChat();
    }
    
    /**
     * Connect to the chat server
     */
    private void connectToChat() {
        if (viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            boolean connectStarted = viewModel.connectToChat();
            if (!connectStarted) {
                Toast.makeText(this, R.string.error_connection, Toast.LENGTH_SHORT).show();
            }
        }
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
    
    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_messages) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (id == R.id.nav_contacts) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (id == R.id.nav_profile) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });
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
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}