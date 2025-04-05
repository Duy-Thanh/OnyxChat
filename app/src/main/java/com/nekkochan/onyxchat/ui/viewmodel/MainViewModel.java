package com.nekkochan.onyxchat.ui.viewmodel;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.nekkochan.onyxchat.crypto.PQCProvider;
import com.nekkochan.onyxchat.data.Contact;
import com.nekkochan.onyxchat.data.Repository;
import com.nekkochan.onyxchat.data.SafeHelperFactory;
import com.nekkochan.onyxchat.data.User;
import com.nekkochan.onyxchat.model.ConversationDisplay;
import com.nekkochan.onyxchat.network.ChatService;
import com.nekkochan.onyxchat.network.WebSocketClient;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for the MainActivity
 * Handles UI-related data in a lifecycle-conscious way
 */
public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MainViewModel";
    
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private final MutableLiveData<String> userAddress = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<List<ChatMessage>> chatMessages = new MutableLiveData<>(new ArrayList<>());
    
    private final Repository repository;
    private final ChatService chatService;
    private User currentUser;
    private LiveData<List<Contact>> activeContacts;
    
    // Connection debouncing
    private long lastConnectionAttempt = 0;
    private static final long CONNECTION_DEBOUNCE_MS = 3000; // 3 second cooldown between attempts
    
    // Chat state
    private final LiveData<Boolean> isChatConnected;
    private final LiveData<Map<String, String>> onlineUsers;

    // Conversations list
    private final MutableLiveData<List<ConversationDisplay>> conversations = new MutableLiveData<>(new ArrayList<>());

    public MainViewModel(@NonNull Application application) {
        super(application);
        
        // Initialize SQLCipher before creating repository
        SafeHelperFactory.initSQLCipher(application.getApplicationContext());
        
        repository = new Repository(application);
        
        // Initialize chat service
        chatService = ChatService.getInstance(application);
        
        // Map WebSocket connection state to boolean
        isChatConnected = Transformations.map(
                chatService.getConnectionState(),
                state -> state == WebSocketClient.WebSocketState.CONNECTED
        );
        
        // Get online users from chat service
        onlineUsers = chatService.getOnlineUsers();
        
        // Listen for new messages
        chatService.getLatestMessage().observeForever(message -> {
            if (message != null) {
                // Add message to the list
                List<ChatMessage> messages = chatMessages.getValue();
                if (messages == null) {
                    messages = new ArrayList<>();
                }
                
                // Create a new chat message from the service message
                ChatMessage chatMessage = new ChatMessage(
                    message.getType().name(),
                    message.getSenderId(),
                    message.getRecipientId(),
                    message.getContent(),
                    new Date(message.getTimestamp())
                );
                
                messages.add(chatMessage);
                chatMessages.postValue(messages);
            }
        });
        
        // Initialize current user
        initCurrentUser();
    }
    
    /**
     * Initialize current user data
     */
    private void initCurrentUser() {
        repository.getCurrentUser().observeForever(user -> {
            if (user != null) {
                currentUser = user;
                userAddress.setValue(user.getAddress());
            } else {
                // No user exists yet, we'll need to create one
                createNewUser();
            }
        });
    }
    
    /**
     * Create a new user with a fresh key pair
     */
    private void createNewUser() {
        try {
            // Generate new post-quantum key pair
            KeyPair keyPair = PQCProvider.generateKyberKeyPair();
            if (keyPair == null) {
                errorMessage.setValue("Failed to generate key pair");
                return;
            }
            
            String encodedPublicKey = PQCProvider.encodePublicKey(keyPair.getPublic());
            String encodedPrivateKey = PQCProvider.encodePrivateKey(keyPair.getPrivate());
            
            // Create new user with default name and address
            User newUser = new User(
                    "user" + System.currentTimeMillis() + "@onyxchat.com",
                    "Me",
                    encodedPublicKey
            );
            newUser.setCurrentUser(true);
            
            // Save user to database
            repository.insertUser(newUser);
            
            currentUser = newUser;
            userAddress.setValue(newUser.getAddress());
            
            Log.d(TAG, "Created new user with address: " + newUser.getAddress());
        } catch (Exception e) {
            Log.e(TAG, "Error creating user: " + e.getMessage(), e);
            errorMessage.setValue("Failed to create user: " + e.getMessage());
        }
    }
    
    /**
     * Generate a new key pair for the current user
     */
    public void generateNewKeyPair() {
        try {
            if (currentUser == null) {
                errorMessage.setValue("No current user");
                return;
            }
            
            isLoading.setValue(true);
            
            // Generate new post-quantum key pair
            KeyPair keyPair = PQCProvider.generateKyberKeyPair();
            if (keyPair == null) {
                errorMessage.setValue("Failed to generate key pair");
                isLoading.setValue(false);
                return;
            }
            
            String encodedPublicKey = PQCProvider.encodePublicKey(keyPair.getPublic());
            
            // Update user
            currentUser.setPublicKey(encodedPublicKey);
            repository.updateUser(currentUser);
            
            isLoading.setValue(false);
        } catch (Exception e) {
            Log.e(TAG, "Error generating key pair: " + e.getMessage(), e);
            errorMessage.setValue("Failed to generate new key: " + e.getMessage());
            isLoading.setValue(false);
        }
    }
    
    /**
     * Get current user's contacts
     * @return LiveData list of contacts
     */
    public LiveData<List<Contact>> getContacts() {
        if (currentUser == null) {
            return new MutableLiveData<>();
        }
        
        if (activeContacts == null) {
            activeContacts = repository.getActiveContacts(currentUser.getAddress());
        }
        
        return activeContacts;
    }
    
    /**
     * Add a new contact
     * @param contactAddress The address of the contact
     * @param nickname Optional nickname for the contact
     */
    public void addContact(String contactAddress, String nickname) {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return;
        }
        
        try {
            // Create new contact
            Contact contact = new Contact(
                    currentUser.getAddress(),
                    contactAddress,
                    nickname
            );
            
            // Save to database
            repository.insertContact(contact);
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding contact: " + e.getMessage(), e);
            errorMessage.setValue("Failed to add contact: " + e.getMessage());
        }
    }
    
    /**
     * Delete a contact
     * @param contact The contact to delete
     */
    public void deleteContact(Contact contact) {
        repository.deleteContact(contact);
    }
    
    /**
     * Block/unblock a contact
     * @param contact The contact to update
     * @param blocked Whether to block or unblock
     */
    public void setContactBlocked(Contact contact, boolean blocked) {
        repository.setContactBlocked(contact.getOwnerAddress(), contact.getContactAddress(), blocked);
    }
    
    /**
     * Mark a contact as verified/unverified
     * @param contact The contact to update
     * @param verified Whether the contact is verified
     */
    public void setContactVerified(Contact contact, boolean verified) {
        repository.setContactVerified(contact.getOwnerAddress(), contact.getContactAddress(), verified);
    }
    
    /**
     * Sync contacts with server to find out which contacts are also app users
     */
    public void syncContacts() {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return;
        }
        
        isLoading.setValue(true);
        repository.syncContactsWithServer(currentUser.getAddress(), success -> {
            isLoading.postValue(false);
            if (!success) {
                errorMessage.postValue("Failed to sync contacts with server");
            } else {
                Log.d(TAG, "Contacts synced successfully");
                
                // Re-fetch the contacts list to update the UI with new app user statuses
                if (activeContacts == null) {
                    activeContacts = repository.getActiveContacts(currentUser.getAddress());
                }
            }
        });
    }

    /**
     * Get the current network connection state
     * @return LiveData boolean of connection state
     */
    public LiveData<Boolean> isConnected() {
        return isConnected;
    }

    /**
     * Set the network connection state
     * @param connected Whether network is connected
     */
    public void setConnected(boolean connected) {
        isConnected.setValue(connected);
    }

    /**
     * Get the current user address
     * @return LiveData String of user address
     */
    public LiveData<String> getUserAddress() {
        return userAddress;
    }

    /**
     * Set the user address
     * @param address The user address
     */
    public void setUserAddress(String address) {
        userAddress.setValue(address);
    }

    /**
     * Get the loading state
     * @return LiveData boolean of loading state
     */
    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    /**
     * Set the loading state
     * @param loading Whether the app is loading
     */
    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    /**
     * Get the error message
     * @return LiveData String of error message
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Set the error message
     * @param message The error message
     */
    public void setErrorMessage(String message) {
        errorMessage.setValue(message);
    }

    /**
     * Clear the error message
     */
    public void clearErrorMessage() {
        errorMessage.setValue(null);
    }

    /**
     * Get the repository
     * @return Repository instance
     */
    public Repository getRepository() {
        return repository;
    }
    
    /**
     * Get the chat service instance
     * @return ChatService instance
     */
    public ChatService getChatService() {
        return chatService;
    }
    
    /**
     * Connect to the chat server
     * @return true if connection was successful or already connected
     */
    public boolean connectToChat() {
        // Check if we're already connected
        if (isChatConnected().getValue() == Boolean.TRUE) {
            return true;
        }
        
        // Only attempt to connect if we have a user ID
        String userId = userAddress.getValue();
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        
        // Connect to chat service
        return chatService.connect(userId);
    }
    
    /**
     * Disconnect from the chat server
     */
    public void disconnectFromChat() {
        chatService.disconnect();
    }

    /**
     * Send a direct message to the specified user
     * @param recipientId the ID of the recipient
     * @param message the message to send
     * @return true if the message was sent successfully
     */
    public boolean sendDirectMessage(String recipientId, String message) {
        return chatService.sendDirectMessage(recipientId, message);
    }
    
    /**
     * Send a message to the public chat
     * @param message the message to send
     * @return true if the message was sent successfully
     */
    public boolean sendChatMessage(String message) {
        return chatService.sendMessage(message);
    }
    
    /**
     * Get the list of conversations
     * @return LiveData containing the list of conversations
     */
    public LiveData<List<ConversationDisplay>> getConversations() {
        return conversations;
    }
    
    /**
     * Get the list of chat messages
     * @return LiveData containing the list of chat messages
     */
    public LiveData<List<ChatMessage>> getChatMessages() {
        return chatMessages;
    }
    
    /**
     * Get the chat connection status
     * @return LiveData containing the connection status
     */
    public LiveData<Boolean> isChatConnected() {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        
        // Using observeForever is safe for threading
        chatService.getConnectionState().observeForever(state -> {
            boolean isConnected = state == WebSocketClient.WebSocketState.CONNECTED;
            // Use postValue which is safe to call from any thread
            result.postValue(isConnected);
        });
        
        return result;
    }
    
    /**
     * Get the online users
     * @return LiveData containing the list of online users
     */
    public LiveData<List<String>> getOnlineUsers() {
        return chatService.getOnlineUsersList();
    }
    
    /**
     * Clear chat messages
     */
    public void clearChatMessages() {
        chatMessages.setValue(new ArrayList<>());
    }
    
    /**
     * A chat message class that represents a message in the chat
     */
    public static class ChatMessage {
        private final String type;
        private final String senderId;
        private final String recipientId;
        private final String content;
        private final Date timestamp;
        
        public ChatMessage(String type, String senderId, String recipientId, String content, Date timestamp) {
            this.type = type;
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.content = content;
            this.timestamp = timestamp;
        }
        
        public String getType() {
            return type;
        }
        
        public String getSenderId() {
            return senderId;
        }
        
        public String getRecipientId() {
            return recipientId;
        }
        
        public String getContent() {
            return content;
        }
        
        public Date getTimestamp() {
            return timestamp;
        }
    }
} 