package com.nekkochan.onyxchat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.model.ConversationDisplay;
import com.nekkochan.onyxchat.ui.adapters.ConversationAdapter;
import com.nekkochan.onyxchat.ui.chat.ChatActivity;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying the list of conversations
 */
public class ConversationListFragment extends Fragment implements ConversationAdapter.OnConversationClickListener {
    
    private static final String TAG = "ConversationListFragment";
    private MainViewModel viewModel;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView statusTextView;
    private ConversationAdapter adapter;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        setHasOptionsMenu(true); // Enable options menu
    }
    
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.conversation_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_conversations) {
            refreshConversations();
            return true;
        } else if (id == R.id.action_new_chat) {
            showContactsForNewChat();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conversation_list, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        recyclerView = view.findViewById(R.id.conversationsRecyclerView);
        emptyView = view.findViewById(R.id.emptyConversationsText);
        statusTextView = view.findViewById(R.id.chatStatusText);
        
        // Setup recycler view
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // Create adapter for conversations
        adapter = new ConversationAdapter(this);
        recyclerView.setAdapter(adapter);
        
        // Update connection status immediately
        updateConnectionStatus(viewModel.isChatConnected().getValue() == Boolean.TRUE);
        
        // Observe chat connection state
        viewModel.isChatConnected().observe(getViewLifecycleOwner(), this::updateConnectionStatus);
        
        // Observe conversations
        viewModel.getConversations().observe(getViewLifecycleOwner(), this::updateConversations);
        
        // Observe online users
        viewModel.getOnlineUsers().observe(getViewLifecycleOwner(), this::updateOnlineUsers);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Force UI update on resume
        updateConnectionStatus(viewModel.isChatConnected().getValue() == Boolean.TRUE);
        
        // Only try to reconnect if disconnected and visible
        if (isAdded() && isVisible() && viewModel.isChatConnected().getValue() != Boolean.TRUE) {
            viewModel.connectToChat();
        }
    }
    
    /**
     * Update the connection status UI
     */
    private void updateConnectionStatus(boolean isConnected) {
        if (!isAdded()) return;
        
        if (isConnected) {
            statusTextView.setText(R.string.status_connected);
            statusTextView.setTextColor(getResources().getColor(R.color.success_green, null));
        } else {
            statusTextView.setText(R.string.status_disconnected);
            statusTextView.setTextColor(getResources().getColor(R.color.error_red, null));
        }
    }
    
    /**
     * Update the conversations list
     */
    private void updateConversations(List<ConversationDisplay> conversations) {
        if (!isAdded()) return;
        
        if (conversations != null && !conversations.isEmpty()) {
            updateEmptyViewVisibility(false);
            adapter.submitList(conversations);
        } else {
            updateEmptyViewVisibility(true);
        }
    }
    
    /**
     * Update online users display
     */
    private void updateOnlineUsers(List<String> onlineUsers) {
        if (!isAdded()) return;
        
        if (onlineUsers != null && !onlineUsers.isEmpty()) {
            // Update UI with online users count
            String statusText = getString(R.string.status_connected) + 
                " (" + onlineUsers.size() + " " + 
                getString(R.string.online_users) + ")";
            statusTextView.setText(statusText);
        }
    }
    
    /**
     * Update the visibility of the empty view based on whether there are conversations
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
    
    @Override
    public void onConversationClick(ConversationDisplay conversation) {
        // Open chat activity for the selected conversation
        Intent intent = new Intent(getActivity(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONTACT_ID, conversation.getParticipantId());
        intent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, conversation.getDisplayName());
        startActivity(intent);
    }
    
    /**
     * Refresh conversations
     */
    private void refreshConversations() {
        // Request conversations update from server
        viewModel.refreshConversations();
        createSnackbar(requireView(), "Refreshing conversations...", Snackbar.LENGTH_SHORT).show();
    }
    
    /**
     * Show contacts to select for a new chat
     */
    private void showContactsForNewChat() {
        // Navigate to contacts fragment with selection mode
        ContactsFragment contactsFragment = new ContactsFragment();
        Bundle args = new Bundle();
        args.putBoolean(ContactsFragment.ARG_SELECTION_MODE, true);
        contactsFragment.setArguments(args);
        
        // Replace current fragment with contacts fragment
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, contactsFragment)
                .addToBackStack(null)
                .commit();
    }
    
    /**
     * Create a properly positioned Snackbar that won't overlap with bottom navigation
     */
    private Snackbar createSnackbar(View view, String message, int duration) {
        Snackbar snackbar = Snackbar.make(view, message, duration);
        
        // Apply special margins to position above navigation bar
        View snackbarView = snackbar.getView();
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) snackbarView.getLayoutParams();
        
        // Add extra margin at the bottom to ensure it's above the navigation bar
        params.bottomMargin = params.bottomMargin + 72;
        snackbarView.setLayoutParams(params);
        
        // Force Snackbar to appear higher up
        snackbar.setBehavior(new BaseTransientBottomBar.Behavior() {
            @Override
            public boolean canSwipeDismissView(View child) {
                return true; // Allow swipe dismiss
            }
        });
        
        return snackbar;
    }
} 