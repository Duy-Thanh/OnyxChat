package com.nekkochan.onyxchat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.model.FriendRequest;
import com.nekkochan.onyxchat.ui.adapter.FriendRequestAdapter;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for displaying friend requests
 */
public class FriendRequestsFragment extends Fragment {

    private MainViewModel viewModel;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Fetch friend requests when fragment is resumed
        viewModel.getFriendRequests();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.friend_requests_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_requests) {
            viewModel.getFriendRequests();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friend_requests, container, false);
        
        // Set up ViewPager2 and TabLayout
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        
        // Setup adapter with initially empty lists
        FriendRequestsPagerAdapter adapter = new FriendRequestsPagerAdapter(new ArrayList<>(), new ArrayList<>());
        viewPager.setAdapter(adapter);
        
        // Set up TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? R.string.received_requests : R.string.sent_requests);
        }).attach();
        
        // Set up observers for friend requests
        viewModel.getReceivedFriendRequests().observe(getViewLifecycleOwner(), receivedRequests -> {
            if (viewPager.getAdapter() != null) {
                ((FriendRequestsPagerAdapter) viewPager.getAdapter()).updateReceivedRequests(receivedRequests);
            }
        });
        
        viewModel.getSentFriendRequests().observe(getViewLifecycleOwner(), sentRequests -> {
            if (viewPager.getAdapter() != null) {
                ((FriendRequestsPagerAdapter) viewPager.getAdapter()).updateSentRequests(sentRequests);
            }
        });
        
        // Observe error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Snackbar.make(view, error, Snackbar.LENGTH_LONG).show();
                viewModel.clearErrorMessage();
            }
        });
        
        return view;
    }
    
    /**
     * Adapter for the ViewPager2 that shows received and sent requests
     */
    private class FriendRequestsPagerAdapter extends RecyclerView.Adapter<FriendRequestsPagerAdapter.PageViewHolder> {
        
        private List<FriendRequest> receivedRequests;
        private List<FriendRequest> sentRequests;
        
        public FriendRequestsPagerAdapter(List<FriendRequest> receivedRequests, List<FriendRequest> sentRequests) {
            this.receivedRequests = receivedRequests;
            this.sentRequests = sentRequests;
        }
        
        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_friend_requests_page, parent, false);
            return new PageViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            // Position 0 = Received, Position 1 = Sent
            if (position == 0) {
                // Received requests
                holder.bind(receivedRequests, true);
            } else {
                // Sent requests
                holder.bind(sentRequests, false);
            }
        }
        
        @Override
        public int getItemCount() {
            return 2; // Always 2 pages: Received and Sent
        }
        
        public void updateReceivedRequests(List<FriendRequest> requests) {
            this.receivedRequests = requests;
            notifyItemChanged(0);
        }
        
        public void updateSentRequests(List<FriendRequest> requests) {
            this.sentRequests = requests;
            notifyItemChanged(1);
        }
        
        /**
         * ViewHolder for each page in the ViewPager2
         */
        class PageViewHolder extends RecyclerView.ViewHolder {
            private final RecyclerView recyclerView;
            private final TextView emptyView;
            
            public PageViewHolder(@NonNull View itemView) {
                super(itemView);
                recyclerView = itemView.findViewById(R.id.requestsRecyclerView);
                emptyView = itemView.findViewById(R.id.emptyRequestsText);
                
                // Set up RecyclerView
                recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
                recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
            }
            
            public void bind(List<FriendRequest> requests, boolean isReceived) {
                // Update empty view
                if (requests == null || requests.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                    
                    // Create and set adapter
                    FriendRequestAdapter adapter = new FriendRequestAdapter(
                            requests,
                            isReceived,
                            // Accept click listener (only for received requests)
                            isReceived ? request -> {
                                viewModel.acceptFriendRequest(request.getId());
                                Toast.makeText(requireContext(), 
                                        R.string.friend_request_accepted, 
                                        Toast.LENGTH_SHORT).show();
                            } : null,
                            // Decline/Cancel click listener
                            request -> {
                                if (isReceived) {
                                    viewModel.rejectFriendRequest(request.getId());
                                    Toast.makeText(requireContext(), 
                                            R.string.friend_request_rejected, 
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    viewModel.cancelFriendRequest(request.getId());
                                    Toast.makeText(requireContext(), 
                                            R.string.friend_request_cancelled, 
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                    recyclerView.setAdapter(adapter);
                }
            }
        }
    }
} 