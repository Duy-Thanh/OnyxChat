package com.nekkochan.onyxchat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nekkochan.onyxchat.R;
import com.nekkochan.onyxchat.ui.viewmodel.MainViewModel;

/**
 * Fragment displaying recent message conversations
 */
public class MessagesFragment extends Fragment {
    
    private MainViewModel viewModel;
    private RecyclerView recyclerView;
    private TextView emptyView;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        recyclerView = view.findViewById(R.id.messagesRecyclerView);
        emptyView = view.findViewById(R.id.emptyMessagesText);
        
        // Setup recycler view
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // TODO: Create and set adapter for recent conversations
        // ConversationAdapter adapter = new ConversationAdapter();
        // recyclerView.setAdapter(adapter);
        
        // For now, just show the empty view
        updateEmptyViewVisibility(true);
        
        // TODO: Observe recent conversations from the ViewModel and update adapter
        // viewModel.getRecentConversations().observe(getViewLifecycleOwner(), conversations -> {
        //     adapter.submitList(conversations);
        //     updateEmptyViewVisibility(conversations.isEmpty());
        // });
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
} 