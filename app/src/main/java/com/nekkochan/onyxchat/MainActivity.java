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
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nekkochan.onyxchat.ui.ProfileFragment;
import com.nekkochan.onyxchat.ui.SettingsActivity;

/**
 * Main activity for the OnyxChat application.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fab;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        
        super.onCreate(savedInstanceState);
        // Set up edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_main);
        
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
            Toast.makeText(this, R.string.add_contact, Toast.LENGTH_SHORT).show();
        });
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
    
    // Simple placeholder fragments
    public static class MessagesFragment extends Fragment {
        public MessagesFragment() {
            super(R.layout.fragment_messages);
        }
    }
    
    public static class ContactsFragment extends Fragment {
        public ContactsFragment() {
            super(R.layout.fragment_contacts);
        }
    }
}