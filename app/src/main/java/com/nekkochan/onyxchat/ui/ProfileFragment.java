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
    private Button generateKeyButton;
    private Button backupKeysButton;
    private TextView versionTextView;

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
        generateKeyButton = view.findViewById(R.id.generateKeyButton);
        backupKeysButton = view.findViewById(R.id.backupKeysButton);
        versionTextView = view.findViewById(R.id.versionTextView);
        
        // Set up button click listeners
        generateKeyButton.setOnClickListener(v -> {
            Snackbar.make(view, "Key generation not implemented yet", Snackbar.LENGTH_SHORT).show();
        });
        
        backupKeysButton.setOnClickListener(v -> {
            Snackbar.make(view, "Key backup not implemented yet", Snackbar.LENGTH_SHORT).show();
        });
        
        // Set version info
        versionTextView.setText("OnyxChat v1.0");
    }
} 