package com.nekkochan.onyxchat.ui.viewmodel;

import android.app.Application;
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
import com.nekkochan.onyxchat.network.ChatService;
import com.nekkochan.onyxchat.network.WebSocketClient;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for the MainActivity
 * Handles UI-related data in a lifecycle-conscious way
 */
public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MainViewModel";
    private final Repository repository;
    private final ChatService chatService;
    
    // Network connection state
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private final MutableLiveData<String> userAddress = new MutableLiveData<>();
    
    // Chat state
    private final LiveData<Boolean> isChatConnected;
    private final MutableLiveData<List<ChatMessage>> chatMessages = new MutableLiveData<>(new ArrayList<>());
    private final LiveData<Map<String, String>> onlineUsers;
    
    // Current UI state
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    // User data cache
    private User currentUser;
    private LiveData<List<Contact>> activeContacts;

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
                messages.add(new ChatMessage(message));
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
     * Connect to the chat server
     * @return true if connection attempt started, false otherwise
     */
    public boolean connectToChat() {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return false;
        }
        
        // Use username part of the address as the user ID
        String userId = currentUser.getAddress().split("@")[0];
        return chatService.connect(userId);
    }
    
    /**
     * Disconnect from the chat server
     */
    public void disconnectFromChat() {
        chatService.disconnect();
    }
    
    /**
     * Send a chat message
     * @param message The message to send
     * @return true if send was successful, false otherwise
     */
    public boolean sendChatMessage(String message) {
        return chatService.sendMessage(message);
    }
    
    /**
     * Send a direct message to a user
     * @param recipientId The recipient's user ID
     * @param message The message to send
     * @return true if send was successful, false otherwise
     */
    public boolean sendDirectMessage(String recipientId, String message) {
        return chatService.sendDirectMessage(recipientId, message);
    }
    
    /**
     * Get chat connection state
     * @return LiveData boolean of chat connection state
     */
    public LiveData<Boolean> isChatConnected() {
        return isChatConnected;
    }
    
    /**
     * Get list of all chat messages
     * @return LiveData list of chat messages
     */
    public LiveData<List<ChatMessage>> getChatMessages() {
        return chatMessages;
    }
    
    /**
     * Get online users
     * @return LiveData map of online users
     */
    public LiveData<Map<String, String>> getOnlineUsers() {
        return onlineUsers;
    }
    
    /**
     * Clear chat messages
     */
    public void clearChatMessages() {
        chatMessages.setValue(new ArrayList<>());
    }
    
    /**
     * A chat message in the UI
     */
    public static class ChatMessage {
        private final ChatService.ChatMessage message;
        private boolean isRead;
        
        public ChatMessage(ChatService.ChatMessage message) {
            this.message = message;
            this.isRead = false;
        }
        
        public ChatService.ChatMessage getMessage() {
            return message;
        }
        
        public boolean isRead() {
            return isRead;
        }
        
        public void setRead(boolean read) {
            isRead = read;
        }
        
        public String getSenderId() {
            return message.getSenderId();
        }
        
        public String getRecipientId() {
            return message.getRecipientId();
        }
        
        public String getContent() {
            return message.getContent();
        }
        
        public long getTimestamp() {
            return message.getTimestamp();
        }
        
        public ChatService.ChatMessage.MessageType getType() {
            return message.getType();
        }
    }
} 