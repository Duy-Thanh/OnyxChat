package com.nekkochan.onyxchat;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.nekkochan.onyxchat.tor.OrbotInstaller;
import com.nekkochan.onyxchat.tor.TorHttpClient;
import com.nekkochan.onyxchat.tor.TorManager;

/**
 * Main activity for the OnyxChat application.
 */
public class MainActivity extends AppCompatActivity implements TorManager.TorConnectionListener {
    private TorManager torManager;
    private TorHttpClient torHttpClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize Tor components
        torManager = TorManager.getInstance(this);
        torManager.startTor(this);
        
        // Initialize the HTTP client for Tor
        torHttpClient = new TorHttpClient(this);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // Let TorManager handle Orbot activity results
        torManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_tor_status) {
            checkTorStatus();
            return true;
        } else if (id == R.id.action_tor_connect) {
            torManager.startTor(this);
            return true;
        } else if (id == R.id.action_tor_disconnect) {
            torManager.stopTor();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void checkTorStatus() {
        if (torManager.isRunning()) {
            Toast.makeText(this, getString(R.string.tor_status_connected) + " - " + torManager.getOnionAddress(), 
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.tor_status_disconnected), Toast.LENGTH_SHORT).show();
            torManager.startTor(this);
        }
    }
    
    @Override
    public void onTorConnected(String onionAddress) {
        Toast.makeText(this, getString(R.string.tor_status_connected) + " - " + onionAddress, 
                Toast.LENGTH_SHORT).show();
        // You can now use torHttpClient to make requests
    }
    
    @Override
    public void onTorDisconnected() {
        Toast.makeText(this, getString(R.string.tor_status_disconnected), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onTorError(String errorMessage) {
        Toast.makeText(this, getString(R.string.tor_status_error) + ": " + errorMessage, Toast.LENGTH_LONG).show();
        
        // If error is related to Orbot not being installed, prompt to install
        if (errorMessage.contains("not installed")) {
            OrbotInstaller.promptToInstallOrbot(this, true);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources
        if (torHttpClient != null) {
            torHttpClient.shutdown();
        }
        if (torManager != null) {
            torManager.stopTor();
        }
    }
}