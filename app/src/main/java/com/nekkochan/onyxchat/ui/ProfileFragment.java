package com.nekkochan.onyxchat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

/**
 * Fragment displaying user profile and app settings
 */
public class ProfileFragment extends Fragment {

    private MainViewModel viewModel;
    private TextView onionAddressTextView;
    private TextView torStatusTextView;
    private Button connectTorButton;
    private Button generateKeyButton;
    private Button backupKeysButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views from layout
        onionAddressTextView = view.findViewById(R.id.onionAddressTextView);
        torStatusTextView = view.findViewById(R.id.torStatusTextView);
        connectTorButton = view.findViewById(R.id.connectTorButton);
        generateKeyButton = view.findViewById(R.id.generateKeyButton);
        backupKeysButton = view.findViewById(R.id.backupKeysButton);
        
        // Set up observers for ViewModel data
        viewModel.getOnionAddress().observe(getViewLifecycleOwner(), address -> {
            if (address != null && !address.isEmpty()) {
                onionAddressTextView.setText(address);
            } else {
                onionAddressTextView.setText("Not available");
            }
        });
        
        viewModel.isTorConnected().observe(getViewLifecycleOwner(), isConnected -> {
            String status = isConnected ? 
                    getString(R.string.tor_status_connected) : 
                    getString(R.string.tor_status_disconnected);
            torStatusTextView.setText(status);
            
            // Update connect button text based on connection state
            connectTorButton.setText(isConnected ? 
                    R.string.disconnect_from_tor : 
                    R.string.connect_to_tor);
        });
        
        // Set up button click listeners
        connectTorButton.setOnClickListener(v -> {
            Boolean isConnected = viewModel.isTorConnected().getValue();
            if (isConnected != null && isConnected) {
                // Disconnect from Tor
                Snackbar.make(view, "Tor disconnection not implemented yet", Snackbar.LENGTH_SHORT).show();
            } else {
                // Connect to Tor
                Snackbar.make(view, "Tor connection not implemented yet", Snackbar.LENGTH_SHORT).show();
            }
        });
        
        generateKeyButton.setOnClickListener(v -> {
            Snackbar.make(view, "Key generation not implemented yet", Snackbar.LENGTH_SHORT).show();
        });
        
        backupKeysButton.setOnClickListener(v -> {
            Snackbar.make(view, "Key backup not implemented yet", Snackbar.LENGTH_SHORT).show();
        });
    }
} 