package com.nekkochan.onyxchat.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.data.Contact;
import com.nekkochan.onyxchat.ui.adapter.ContactAdapter;
import com.nekkochan.onyxchat.ui.chat.ChatActivity;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

/**
 * Fragment displaying the user's contacts
 */
public class ContactsFragment extends Fragment implements ContactAdapter.OnContactClickListener {
    
    public static final String ARG_SELECTION_MODE = "selection_mode";
    
    private MainViewModel viewModel;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private ContactAdapter adapter;
    private boolean selectionMode = false;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        adapter = new ContactAdapter(this);
        setHasOptionsMenu(true); // Enable options menu
        
        // Check if we're in selection mode
        Bundle args = getArguments();
        if (args != null) {
            selectionMode = args.getBoolean(ARG_SELECTION_MODE, false);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Sync contacts with server when fragment is resumed
        syncContacts();
    }
    
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (selectionMode) {
            // Custom menu for selection mode
            inflater.inflate(R.menu.contacts_selection_menu, menu);
        } else {
            // Normal contacts menu
            inflater.inflate(R.menu.contacts_menu, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_contacts) {
            syncContacts();
            return true;
        } else if (id == R.id.action_add_contact) {
            showAddContactDialog();
            return true;
        } else if (id == R.id.action_cancel_selection) {
            // Go back to conversation list
            requireActivity().getSupportFragmentManager().popBackStack();
            return true;
        } else if (id == R.id.action_discover_users) {
            showDiscoverUsers();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        recyclerView = view.findViewById(R.id.contactsRecyclerView);
        emptyView = view.findViewById(R.id.emptyContactsText);
        
        // Set appropriate text for empty view based on mode
        if (selectionMode) {
            emptyView.setText(R.string.select_contact_for_chat);
        }
        
        // Setup SwipeRefreshLayout
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout = 
                view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            syncContacts();
            swipeRefreshLayout.setRefreshing(false);
        });
        
        // Setup recycler view
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
        
        // Observe contacts from the ViewModel and update adapter
        viewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            adapter.submitList(contacts);
            updateEmptyViewVisibility(contacts == null || contacts.isEmpty());
        });
        
        // Observe error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                createSnackbar(view, error, Snackbar.LENGTH_LONG).show();
                viewModel.clearErrorMessage();
            }
        });
    }
    
    /**
     * Update the visibility of the empty view based on whether there are contacts
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
     * Show dialog to add a new contact
     */
    public void showAddContactDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_contact, null);
        
        EditText addressInput = dialogView.findViewById(R.id.contactAddressInput);
        
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_contact)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String address = addressInput.getText().toString().trim();
                    
                    if (address.isEmpty()) {
                        Toast.makeText(requireContext(), "Address is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Ensure onion address format
                    if (!address.endsWith(".onion")) {
                        address = address + ".onion";
                    }
                    
                    // Always use null for nickname - we'll display the real name from the server
                    viewModel.addContact(address, null);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    /**
     * Create a properly positioned Snackbar that won't overlap with bottom navigation
     * @param view The parent view
     * @param message The message to show
     * @param duration The duration (LENGTH_SHORT or LENGTH_LONG)
     * @return The created Snackbar
     */
    private Snackbar createSnackbar(View view, String message, int duration) {
        Snackbar snackbar = Snackbar.make(view, message, duration);
        
        // Get the root CoordinatorLayout for proper Snackbar behavior
        View coordinator = requireActivity().findViewById(android.R.id.content);
        
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
    
    @Override
    public void onContactClick(Contact contact) {
        if (selectionMode) {
            // Start a chat with this contact
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CONTACT_ID, contact.getContactAddress());
            intent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, contact.getContactAddress());
            startActivity(intent);
            
            // Go back to conversation list
            requireActivity().getSupportFragmentManager().popBackStack();
        } else {
            // Normal behavior - Open chat with this contact
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CONTACT_ID, contact.getContactAddress());
            intent.putExtra(ChatActivity.EXTRA_CONTACT_NAME, contact.getContactAddress());
            startActivity(intent);
        }
    }
    
    @Override
    public void onContactLongClick(Contact contact, View view) {
        // Show popup menu for contact actions
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(R.menu.contact_menu, popup.getMenu());
        
        // Update menu items based on contact state
        popup.getMenu().findItem(R.id.action_block).setVisible(!contact.isBlocked());
        popup.getMenu().findItem(R.id.action_unblock).setVisible(contact.isBlocked());
        popup.getMenu().findItem(R.id.action_verify).setVisible(!contact.isVerified());
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.action_block) {
                viewModel.setContactBlocked(contact, true);
                return true;
            } else if (itemId == R.id.action_unblock) {
                viewModel.setContactBlocked(contact, false);
                return true;
            } else if (itemId == R.id.action_verify) {
                viewModel.setContactVerified(contact, true);
                return true;
            } else if (itemId == R.id.action_delete) {
                confirmDeleteContact(contact);
                return true;
            } else if (itemId == R.id.action_encryption_info) {
                showEncryptionInfo(contact);
                return true;
            }
            
            return false;
        });
        
        popup.show();
    }
    
    /**
     * Show confirmation dialog for deleting a contact
     */
    private void confirmDeleteContact(Contact contact) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete this contact? This action cannot be undone.")
                .setPositiveButton(R.string.delete, (dialog, which) -> viewModel.deleteContact(contact))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    /**
     * Show encryption info for a contact
     */
    private void showEncryptionInfo(Contact contact) {
        // TODO: Implement showing encryption details
        createSnackbar(requireView(), "Encryption info not implemented yet", Snackbar.LENGTH_SHORT).show();
    }
    
    /**
     * Sync contacts with server
     */
    private void syncContacts() {
        viewModel.syncContacts();
        createSnackbar(requireView(), "Syncing contacts...", Snackbar.LENGTH_SHORT).show();
    }
    
    /**
     * Show discover users screen
     */
    private void showDiscoverUsers() {
        UsersFragment usersFragment = new UsersFragment();
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, usersFragment)
                .addToBackStack(null)
                .commit();
    }
} 