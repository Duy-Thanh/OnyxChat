package com.nekkochan.onyxchat.ui.viewmodel;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
import com.nekkochan.onyxchat.network.ApiClient;
import com.nekkochan.onyxchat.model.UserProfile;
import com.nekkochan.onyxchat.model.FriendRequest;
import com.nekkochan.onyxchat.model.UserStatus;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

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
    
    // Users and friend requests
    private final MutableLiveData<List<UserProfile>> users = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<FriendRequest>> receivedFriendRequests = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<FriendRequest>> sentFriendRequests = new MutableLiveData<>(new ArrayList<>());
    
    // Connection debouncing
    private long lastConnectionAttempt = 0;
    private static final long CONNECTION_DEBOUNCE_MS = 3000; // 3 second cooldown between attempts
    
    // Chat state
    private final LiveData<Boolean> isChatConnected;
    private final LiveData<Map<String, UserStatus>> onlineUsers;

    // Conversations list
    private final MutableLiveData<List<ConversationDisplay>> conversations = new MutableLiveData<>(new ArrayList<>());

    /**
     * Constructor for the ViewModel
     */
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
        
        // Listen for WebSocket events
        chatService.getWebSocketClient().getEvents().observeForever(event -> {
            if (event != null && event.getType() == WebSocketClient.WebSocketEventType.USER_STATUS_CHANGE) {
                try {
                    // Parse the event data
                    JSONObject data = new JSONObject(event.getData());
                    String userId = data.getString("userId");
                    boolean isActive = data.getBoolean("isActive");
                    
                    // Update user in the list if it exists
                    updateUserStatus(userId, isActive);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing user status event", e);
                }
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
     * @return LiveData<Boolean> that will be true if successful, false otherwise
     */
    public LiveData<Boolean> generateNewKeyPair() {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        
        try {
            if (currentUser == null) {
                errorMessage.setValue("No current user");
                result.setValue(false);
                return result;
            }
            
            isLoading.setValue(true);
            
            // Run on a background thread
            new Thread(() -> {
                try {
                    // Generate new post-quantum key pair
                    KeyPair keyPair = PQCProvider.generateKyberKeyPair();
                    if (keyPair == null) {
                        errorMessage.postValue("Failed to generate key pair");
                        isLoading.postValue(false);
                        result.postValue(false);
                        return;
                    }
                    
                    String encodedPublicKey = PQCProvider.encodePublicKey(keyPair.getPublic());
                    String encodedPrivateKey = PQCProvider.encodePrivateKey(keyPair.getPrivate());
                    
                    // Update user
                    currentUser.setPublicKey(encodedPublicKey);
                    repository.updateUser(currentUser);
                    
                    isLoading.postValue(false);
                    result.postValue(true);
                } catch (Exception e) {
                    Log.e(TAG, "Error generating key pair: " + e.getMessage(), e);
                    errorMessage.postValue("Failed to generate new key: " + e.getMessage());
                    isLoading.postValue(false);
                    result.postValue(false);
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating key pair: " + e.getMessage(), e);
            errorMessage.setValue("Failed to generate new key: " + e.getMessage());
            isLoading.setValue(false);
            result.setValue(false);
        }
        
        return result;
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
        
        // Don't allow adding self as contact
        if (contactAddress.equals(currentUser.getAddress())) {
            errorMessage.setValue("Cannot add yourself as a contact");
            return;
        }
        
        try {
            // Use an async transaction to prevent main thread blocking
            repository.executeTransactionAsync(() -> {
                // Check if User already exists with this address
                User existingUser = repository.getUserByAddress(contactAddress);
                
                // If not, create a User entry for the contact address
                if (existingUser == null) {
                    User contactUser = new User(contactAddress, nickname != null ? nickname : contactAddress, null);
                    contactUser.setCurrentUser(false);
                    repository.insertUser(contactUser);
                }
                
                // Check if contact already exists to avoid duplicates
                if (repository.contactExists(currentUser.getAddress(), contactAddress)) {
                    throw new IllegalStateException("Contact already exists");
                }
                
                // Now create the contact
                Contact contact = new Contact(
                        currentUser.getAddress(),
                        contactAddress,
                        nickname
                );
                
                // Save to database
                repository.insertContact(contact);
                return null;
            });
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
        if (isChatConnected.getValue() == Boolean.TRUE) {
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
        return isChatConnected;
    }
    
    /**
     * Get the online users
     * @return LiveData containing the list of online user IDs
     */
    public LiveData<List<String>> getOnlineUsers() {
        // Update to extract the list of online users from the map
        return Transformations.map(chatService.getOnlineUsers(), userMap -> {
            if (userMap == null) return new ArrayList<>();
            
            List<String> onlineUserIds = new ArrayList<>();
            for (Map.Entry<String, UserStatus> entry : userMap.entrySet()) {
                if (entry.getValue().isOnline()) {
                    onlineUserIds.add(entry.getKey());
                }
            }
            return onlineUserIds;
        });
    }
    
    /**
     * Get detailed user statuses
     * @return LiveData containing map of user IDs to their status info
     */
    public LiveData<Map<String, UserStatus>> getUserStatuses() {
        return onlineUsers;
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

    /**
     * Refresh the conversations list from the server
     */
    public void refreshConversations() {
        // Show loading indicator
        isLoading.setValue(true);
        
        // Get user ID
        String userId = getUserId();
        if (userId == null || userId.isEmpty()) {
            errorMessage.setValue("User ID not available");
            isLoading.setValue(false);
            return;
        }
        
        // Get conversations from API
        ApiClient apiClient = ApiClient.getInstance(getApplication());
        apiClient.getConversations(new ApiClient.ApiCallback<List<ApiClient.ConversationResponse>>() {
            @Override
            public void onSuccess(List<ApiClient.ConversationResponse> result) {
                Log.d(TAG, "Got " + (result != null ? result.size() : 0) + " conversations");
                
                if (result != null) {
                    List<ConversationDisplay> conversationsList = new ArrayList<>();
                    
                    SimpleDateFormat formatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    formatter.setTimeZone(TimeZone.getDefault());
                    
                    for (ApiClient.ConversationResponse conversation : result) {
                        // Log the timestamp for debugging
                        if (conversation.getCreatedAt() != null) {
                            Log.d(TAG, "Conversation timestamp for " + conversation.getEmail() + 
                                  ": " + conversation.getCreatedAt() + 
                                  " -> " + formatter.format(conversation.getCreatedAt()));
                        } else {
                            Log.d(TAG, "No timestamp for conversation with " + conversation.getEmail());
                        }
                        
                        // Create display model for this conversation
                        String displayName = conversation.getDisplayName();
                        if (displayName == null || displayName.isEmpty()) {
                            displayName = conversation.getUsername();
                        }
                        
                        conversationsList.add(new ConversationDisplay(
                                conversation.getUserId(),
                                conversation.getEmail(),
                                displayName,
                                conversation.getContent(),
                                conversation.getCreatedAt(),
                                conversation.getUnreadCount(),
                                false // Default to offline since we don't have online status yet
                        ));
                    }
                    
                    // Sort conversations by timestamp (newest first)
                    Collections.sort(conversationsList, (a, b) -> {
                        if (a.getLastMessageTime() == null && b.getLastMessageTime() == null) {
                            return 0;
                        } else if (a.getLastMessageTime() == null) {
                            return 1;
                        } else if (b.getLastMessageTime() == null) {
                            return -1;
                        } else {
                            // Sort in descending order (newest first)
                            return b.getLastMessageTime().compareTo(a.getLastMessageTime());
                        }
                    });
                    
                    conversations.setValue(conversationsList);
                } else {
                    conversations.setValue(new ArrayList<>());
                }
                
                isLoading.setValue(false);
                clearErrorMessage();
            }
            
            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "Error getting conversations: " + errorMsg);
                errorMessage.setValue("Error loading conversations: " + errorMsg);
                isLoading.setValue(false);
            }
        });
    }

    /**
     * Get the current user's ID/address
     * @return The user ID or null if not available
     */
    public String getUserId() {
        return userAddress.getValue();
    }

    /**
     * Fetch all users from the server
     */
    public void fetchUsers() {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return;
        }
        
        isLoading.setValue(true);
        
        // Get API client
        ApiClient apiClient = ApiClient.getInstance(getApplication());
        
        // Fetch users
        apiClient.getUsers(new ApiClient.ApiCallback<ApiClient.UsersResponse>() {
            @Override
            public void onSuccess(ApiClient.UsersResponse response) {
                List<UserProfile> usersList = new ArrayList<>();
                
                if (response.data != null && response.data.users != null) {
                    for (ApiClient.UserProfile apiUser : response.data.users) {
                        // Convert ApiClient.UserProfile to model.UserProfile
                        UserProfile userProfile = new UserProfile(
                            apiUser.id,
                            apiUser.username,
                            apiUser.displayName,
                            apiUser.isActive,
                            new Date() // We don't have lastActiveAt in the API response
                        );
                        
                        // Check if this is already set in the response
                        if (apiUser.friendStatus != null) {
                            switch (apiUser.friendStatus) {
                                case "contact":
                                    userProfile.setFriendStatus(UserProfile.FriendStatus.CONTACT);
                                    break;
                                case "sent":
                                    userProfile.setFriendStatus(UserProfile.FriendStatus.SENT);
                                    break;
                                case "received":
                                    userProfile.setFriendStatus(UserProfile.FriendStatus.RECEIVED);
                                    break;
                                default:
                                    userProfile.setFriendStatus(UserProfile.FriendStatus.NONE);
                                    break;
                            }
                        }
                        
                        usersList.add(userProfile);
                    }
                }
                
                users.setValue(usersList);
                setLoading(false);
                clearErrorMessage();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                isLoading.postValue(false);
                MainViewModel.this.errorMessage.postValue("Failed to fetch users: " + errorMessage);
            }
        });
    }
    
    /**
     * Get current friends request lists
     */
    public void getFriendRequests() {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return;
        }
        
        isLoading.setValue(true);
        
        // Get API client
        ApiClient apiClient = ApiClient.getInstance(getApplication());
        
        // Fetch friend requests
        apiClient.getFriendRequests(new ApiClient.ApiCallback<ApiClient.FriendRequestsResponse>() {
            @Override
            public void onSuccess(ApiClient.FriendRequestsResponse response) {
                List<FriendRequest> receivedList = new ArrayList<>();
                List<FriendRequest> sentList = new ArrayList<>();
                
                if (response.data != null) {
                    // Process received requests
                    if (response.data.received != null) {
                        for (ApiClient.FriendRequestsResponse.ReceivedRequest request : response.data.received) {
                            // Convert ApiClient.UserProfile to model.UserProfile
                            UserProfile senderProfile = new UserProfile(
                                request.sender.id,
                                request.sender.username,
                                request.sender.displayName,
                                request.sender.isActive,
                                new Date() // Use current date since we don't have lastActiveAt
                            );
                            
                            FriendRequest friendRequest = new FriendRequest(
                                request.id,
                                senderProfile,
                                request.message,
                                request.status,
                                request.createdAt
                            );
                            receivedList.add(friendRequest);
                        }
                    }
                    
                    // Process sent requests
                    if (response.data.sent != null) {
                        for (ApiClient.FriendRequestsResponse.SentRequest request : response.data.sent) {
                            // Convert ApiClient.UserProfile to model.UserProfile
                            UserProfile receiverProfile = new UserProfile(
                                request.receiver.id,
                                request.receiver.username,
                                request.receiver.displayName,
                                request.receiver.isActive,
                                new Date() // Use current date since we don't have lastActiveAt
                            );
                            
                            FriendRequest friendRequest = new FriendRequest(
                                request.id,
                                receiverProfile,
                                request.message,
                                request.status,
                                request.createdAt,
                                true // Indicate this is a sent request
                            );
                            sentList.add(friendRequest);
                        }
                    }
                }
                
                receivedFriendRequests.setValue(receivedList);
                sentFriendRequests.setValue(sentList);
                setLoading(false);
                clearErrorMessage();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                isLoading.postValue(false);
                MainViewModel.this.errorMessage.postValue("Failed to fetch friend requests: " + errorMessage);
            }
        });
    }
    
    /**
     * Send a friend request
     */
    public void sendFriendRequest(String receiverId, String message) {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return;
        }
        
        isLoading.setValue(true);
        
        // Get API client
        ApiClient apiClient = ApiClient.getInstance(getApplication());
        
        // Send friend request
        apiClient.sendFriendRequest(receiverId, message, new ApiClient.ApiCallback<ApiClient.FriendRequestResponse>() {
            @Override
            public void onSuccess(ApiClient.FriendRequestResponse response) {
                isLoading.postValue(false);
                
                // If auto-accepted (both parties sent requests to each other)
                if (response.message.contains("was accepted")) {
                    // Refresh contacts
                    syncContacts();
                }
                
                // Show success message
                MainViewModel.this.errorMessage.postValue(response.message);
                
                // Refresh users and friend requests
                fetchUsers();
                getFriendRequests();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                isLoading.postValue(false);
                MainViewModel.this.errorMessage.postValue("Failed to send friend request: " + errorMessage);
            }
        });
    }
    
    /**
     * Accept a friend request
     */
    public void acceptFriendRequest(String requestId) {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return;
        }
        
        isLoading.setValue(true);
        
        // Get API client
        ApiClient apiClient = ApiClient.getInstance(getApplication());
        
        // Accept friend request
        apiClient.acceptFriendRequest(requestId, new ApiClient.ApiCallback<ApiClient.AcceptFriendResponse>() {
            @Override
            public void onSuccess(ApiClient.AcceptFriendResponse response) {
                isLoading.postValue(false);
                
                // Show success message
                MainViewModel.this.errorMessage.postValue("Friend request accepted");
                
                // Refresh contacts
                syncContacts();
                
                // Refresh users and friend requests
                fetchUsers();
                getFriendRequests();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                isLoading.postValue(false);
                MainViewModel.this.errorMessage.postValue("Failed to accept friend request: " + errorMessage);
            }
        });
    }
    
    /**
     * Reject a friend request
     */
    public void rejectFriendRequest(String requestId) {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return;
        }
        
        isLoading.setValue(true);
        
        // Get API client
        ApiClient apiClient = ApiClient.getInstance(getApplication());
        
        // Reject friend request
        apiClient.rejectFriendRequest(requestId, new ApiClient.ApiCallback<ApiClient.BaseResponse>() {
            @Override
            public void onSuccess(ApiClient.BaseResponse response) {
                isLoading.postValue(false);
                
                // Show success message
                MainViewModel.this.errorMessage.postValue("Friend request rejected");
                
                // Refresh users and friend requests
                fetchUsers();
                getFriendRequests();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                isLoading.postValue(false);
                MainViewModel.this.errorMessage.postValue("Failed to reject friend request: " + errorMessage);
            }
        });
    }
    
    /**
     * Cancel a sent friend request
     */
    public void cancelFriendRequest(String requestId) {
        if (currentUser == null) {
            errorMessage.setValue("No current user");
            return;
        }
        
        isLoading.setValue(true);
        
        // Get API client
        ApiClient apiClient = ApiClient.getInstance(getApplication());
        
        // Cancel friend request
        apiClient.cancelFriendRequest(requestId, new ApiClient.ApiCallback<ApiClient.BaseResponse>() {
            @Override
            public void onSuccess(ApiClient.BaseResponse response) {
                isLoading.postValue(false);
                
                // Show success message
                MainViewModel.this.errorMessage.postValue("Friend request cancelled");
                
                // Refresh users and friend requests
                fetchUsers();
                getFriendRequests();
            }
            
            @Override
            public void onFailure(String errorMessage) {
                isLoading.postValue(false);
                MainViewModel.this.errorMessage.postValue("Failed to cancel friend request: " + errorMessage);
            }
        });
    }
    
    /**
     * Get user list
     */
    public LiveData<List<UserProfile>> getUsers() {
        return users;
    }
    
    /**
     * Get received friend requests
     */
    public LiveData<List<FriendRequest>> getReceivedFriendRequests() {
        return receivedFriendRequests;
    }
    
    /**
     * Get sent friend requests
     */
    public LiveData<List<FriendRequest>> getSentFriendRequests() {
        return sentFriendRequests;
    }

    /**
     * Update the online status of a user in the users list
     */
    private void updateUserStatus(String userId, boolean isActive) {
        List<UserProfile> currentUsers = users.getValue();
        if (currentUsers != null) {
            boolean updated = false;
            
            // Create a new list to update the LiveData
            List<UserProfile> updatedUsers = new ArrayList<>();
            
            for (UserProfile user : currentUsers) {
                if (user.getId().equals(userId)) {
                    // Update the user's active status
                    user.setActive(isActive);
                    updated = true;
                }
                updatedUsers.add(user);
            }
            
            // Only update the LiveData if a user was actually changed
            if (updated) {
                users.postValue(updatedUsers);
            }
        }
    }
} 