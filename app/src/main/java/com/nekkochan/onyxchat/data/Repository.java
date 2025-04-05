package com.nekkochan.onyxchat.data;

import android.app.Application;
import android.os.AsyncTask;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.function.Consumer;

import com.nekkochan.onyxchat.network.ApiClient;

/**
 * Repository to manage data operations and provide a clean API for data access
 */
public class Repository {
    private final UserDao userDao;
    private final MessageDao messageDao;
    private final ContactDao contactDao;
    private final ExecutorService executorService;
    private final AppDatabase appDatabase;
    private final Context appContext;

    /**
     * Constructor to initialize the repository with DAOs
     */
    public Repository(Context context) {
        // Initialize SQLCipher for database encryption
        SafeHelperFactory.initSQLCipher(context.getApplicationContext());
        
        appContext = context.getApplicationContext();
        appDatabase = AppDatabase.getInstance(context);
        userDao = appDatabase.userDao();
        messageDao = appDatabase.messageDao();
        contactDao = appDatabase.contactDao();
        executorService = Executors.newFixedThreadPool(4);
    }

    // User operations
    public void insertUser(User user) {
        executorService.execute(() -> userDao.insert(user));
    }

    public void updateUser(User user) {
        executorService.execute(() -> userDao.update(user));
    }

    public void deleteUser(User user) {
        executorService.execute(() -> userDao.delete(user));
    }

    public LiveData<User> getCurrentUser() {
        return userDao.observeCurrentUser();
    }

    public LiveData<List<User>> getContactUsers(String userAddress) {
        return userDao.getContactUsers(userAddress);
    }

    // Message operations
    public void insertMessage(Message message) {
        executorService.execute(() -> messageDao.insert(message));
    }

    public void updateMessage(Message message) {
        executorService.execute(() -> messageDao.update(message));
    }

    public void deleteMessage(Message message) {
        executorService.execute(() -> messageDao.delete(message));
    }

    public LiveData<List<Message>> getMessagesForContact(String userAddress, String contactAddress) {
        return messageDao.getMessagesForContact(userAddress, contactAddress);
    }

    public void markMessagesAsRead(String userAddress, String contactAddress) {
        executorService.execute(() -> messageDao.markAsRead(userAddress, contactAddress));
    }

    public void markMessageAsDeleted(String messageId) {
        executorService.execute(() -> messageDao.markAsDeleted(messageId));
    }

    public void deleteExpiredMessages() {
        executorService.execute(() -> messageDao.deleteExpiredMessages(System.currentTimeMillis()));
    }

    public LiveData<Integer> getUnreadMessageCount(String userAddress, String contactAddress) {
        return messageDao.getUnreadMessageCount(userAddress, contactAddress);
    }

    // Contact operations
    public void insertContact(Contact contact) {
        executorService.execute(() -> contactDao.insert(contact));
    }

    public void updateContact(Contact contact) {
        executorService.execute(() -> contactDao.update(contact));
    }

    public void deleteContact(Contact contact) {
        executorService.execute(() -> contactDao.delete(contact));
    }

    public LiveData<List<Contact>> getActiveContacts(String ownerAddress) {
        return contactDao.getActiveContacts(ownerAddress);
    }

    public LiveData<List<Contact>> getBlockedContacts(String ownerAddress) {
        return contactDao.getBlockedContacts(ownerAddress);
    }

    public void setContactBlocked(String ownerAddress, String contactAddress, boolean blocked) {
        executorService.execute(() -> contactDao.setContactBlocked(ownerAddress, contactAddress, blocked));
    }
    
    public void setContactVerified(String ownerAddress, String contactAddress, boolean verified) {
        executorService.execute(() -> contactDao.setContactVerified(ownerAddress, contactAddress, verified));
    }

    public void updateContactInteractionTime(String ownerAddress, String contactAddress) {
        executorService.execute(() -> {
            Contact contact = contactDao.getContactByAddresses(ownerAddress, contactAddress);
            if (contact != null) {
                contact.setLastInteractionTime(System.currentTimeMillis());
                contactDao.update(contact);
            }
        });
    }

    /**
     * Update the app user status of a contact
     */
    public void updateContactAppUserStatus(String ownerAddress, String contactAddress, boolean isAppUser) {
        executorService.execute(() -> {
            Contact contact = contactDao.getContactByAddresses(ownerAddress, contactAddress);
            if (contact != null) {
                contact.setAppUser(isAppUser);
                contactDao.update(contact);
            }
        });
    }
    
    /**
     * Get a user by their address
     * @param address The user's address
     * @return The user, or null if not found
     */
    public User getUserByAddress(String address) {
        return userDao.getUserByAddress(address);
    }
    
    /**
     * Execute a transaction with proper error handling
     * @param transaction The transaction to execute
     * @param <T> The return type of the transaction
     * @return The result of the transaction
     */
    public <T> T executeTransaction(RoomTransactionCallback<T> transaction) {
        try {
            // Execute transaction on a background thread
            return appDatabase.runInTransaction(transaction::execute);
        } catch (Exception e) {
            Log.e("Repository", "Transaction failed", e);
            throw e;
        }
    }
    
    /**
     * Execute a transaction with proper error handling on a background thread
     * @param transaction The transaction to execute
     */
    public void executeTransactionAsync(RoomTransactionCallback<Void> transaction) {
        executorService.execute(() -> {
            try {
                appDatabase.runInTransaction(transaction::execute);
            } catch (Exception e) {
                Log.e("Repository", "Transaction failed", e);
            }
        });
    }
    
    /**
     * Functional interface for Room transactions
     */
    public interface RoomTransactionCallback<T> {
        T execute() throws Exception;
    }
    
    /**
     * Check if a contact exists
     * @param ownerAddress The owner's address
     * @param contactAddress The contact's address
     * @return True if the contact exists, false otherwise
     */
    public boolean contactExists(String ownerAddress, String contactAddress) {
        return contactDao.contactExists(ownerAddress, contactAddress);
    }
    
    /**
     * Sync contacts with server to find which ones are app users
     */
    public void syncContactsWithServer(String ownerAddress, Consumer<Boolean> completionCallback) {
        // Create a ApiClient instance
        ApiClient apiClient = ApiClient.getInstance(appContext);
        
        // Get all contacts for the owner
        executorService.execute(() -> {
            List<Contact> contacts = contactDao.getAllContactsForOwner(ownerAddress);
            if (contacts == null || contacts.isEmpty()) {
                // No contacts to sync
                if (completionCallback != null) {
                    completionCallback.accept(true);
                }
                return;
            }
            
            // Extract contact addresses
            List<String> contactAddresses = new ArrayList<>();
            for (Contact contact : contacts) {
                contactAddresses.add(contact.getContactAddress());
            }
            
            // Call API to sync contacts
            apiClient.syncContacts(contactAddresses, new ApiClient.ApiCallback<ApiClient.ContactSyncResponse>() {
                @Override
                public void onSuccess(ApiClient.ContactSyncResponse response) {
                    if (response.data != null && response.data.appUsers != null) {
                        // Update contacts in database
                        executorService.execute(() -> {
                            for (Contact contact : contacts) {
                                boolean isAppUser = response.data.appUsers.contains(contact.getContactAddress());
                                contact.setAppUser(isAppUser);
                                contactDao.update(contact);
                            }
                            
                            if (completionCallback != null) {
                                completionCallback.accept(true);
                            }
                        });
                    } else {
                        if (completionCallback != null) {
                            completionCallback.accept(false);
                        }
                    }
                }
                
                @Override
                public void onFailure(String errorMessage) {
                    Log.e("Repository", "Failed to sync contacts: " + errorMessage);
                    if (completionCallback != null) {
                        completionCallback.accept(false);
                    }
                }
            });
        });
    }

    // Helper methods
    public String generateUniqueId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Send a message to a contact
     * @param senderAddress Sender's onion address
     * @param recipientAddress Recipient's onion address
     * @param encryptedContent Encrypted message content
     * @param expirationTime Time when message should expire (0 for never)
     * @return The created message
     */
    public Message sendMessage(String senderAddress, String recipientAddress, 
                              String encryptedContent, long expirationTime) {
        String messageId = generateUniqueId();
        String conversationId = senderAddress + "_" + recipientAddress;
        Message message = new Message(
            messageId, 
            encryptedContent,
            senderAddress, 
            recipientAddress,
            conversationId,
            true,    // sent by self
            true     // is encrypted
        );
        
        if (expirationTime > 0) {
            message.setExpirationTime(expirationTime);
        }
        
        message.setSent(true);
        insertMessage(message);
        updateContactInteractionTime(senderAddress, recipientAddress);
        return message;
    }
} 