package com.nekkochan.onyxchat.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.model.UserProfile;
import com.nekkochan.onyxchat.ui.adapter.UserAdapter;
import com.nekkochan.onyxchat.ui.chat.ChatActivity;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

/**
 * Fragment for displaying all app users and friend discovery
 */
public class UsersFragment extends Fragment {

    private MainViewModel viewModel;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private UserAdapter adapter;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        setupViewModel();
        
        // Set up SwipeRefreshLayout
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(this::refreshUsers);
        
        // Set up loading indicator
        viewModel.isLoading().observe(getViewLifecycleOwner(), isLoading -> {
            swipeRefreshLayout.setRefreshing(isLoading);
        });
        
        // Set up error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Snackbar.make(view, error, Snackbar.LENGTH_LONG).show();
                viewModel.clearErrorMessage();
            }
        });
        
        // Initial users load
        refreshUsers();
        
        // Set up websocket connection for real-time status updates
        connectToWebSocket();
    }
    
    /**
     * Connect to the WebSocket for real-time updates
     */
    private void connectToWebSocket() {
        // Make sure we have a WebSocket connection for getting real-time user status updates
        if (viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            viewModel.connectToChat();
        }
    }

    /**
     * Set up the RecyclerView
     */
    private void setupRecyclerView() {
        recyclerView = requireView().findViewById(R.id.usersRecyclerView);
        emptyView = requireView().findViewById(R.id.emptyUsersText);
        
        // Create adapter
        adapter = new UserAdapter(
                user -> showChatDialog(user),  // Chat click handler
                user -> showAddFriendDialog(user)   // Add friend click handler
        );
        
        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), 
                DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
    }
    
    /**
     * Set up the ViewModel observers
     */
    private void setupViewModel() {
        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        
        // Observe users list
        viewModel.getUsers().observe(getViewLifecycleOwner(), users -> {
            adapter.submitList(users);
            updateEmptyViewVisibility(users == null || users.isEmpty());
        });
    }
    
    /**
     * Refresh the users list
     */
    private void refreshUsers() {
        viewModel.fetchUsers();
    }
    
    /**
     * Show or hide the empty view
     */
    private void updateEmptyViewVisibility(boolean isEmpty) {
        if (isEmpty) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Show dialog for starting a chat
     */
    private void showChatDialog(UserProfile user) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.chat_with) + " " + user.getDisplayName())
                .setMessage("Start a new conversation with " + user.getDisplayName() + "?")
                .setPositiveButton(R.string.message, (dialog, which) -> {
                    startChatWithUser(user);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    /**
     * Show dialog for adding a friend
     */
    private void showAddFriendDialog(UserProfile user) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_friend, null);
        EditText messageInput = dialogView.findViewById(R.id.friendRequestMessage);
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.send_friend_request)
                .setView(dialogView)
                .setPositiveButton(R.string.send, (dialog, which) -> {
                    String message = messageInput.getText().toString().trim();
                    sendFriendRequest(user, message);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    /**
     * Send a friend request
     */
    private void sendFriendRequest(UserProfile user, String message) {
        viewModel.sendFriendRequest(user.getId(), message);
    }
    
    /**
     * Start chat with a user
     */
    private void startChatWithUser(UserProfile user) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra("contactId", user.getId());
        intent.putExtra("contactName", user.getDisplayName());
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.users_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_friend_requests) {
            showFriendRequests();
            return true;
        } else if (id == R.id.action_refresh) {
            refreshUsers();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Show friend requests screen
     */
    private void showFriendRequests() {
        FriendRequestsFragment friendRequestsFragment = new FriendRequestsFragment();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, friendRequestsFragment)
                .addToBackStack(null)
                .commit();
    }
} 