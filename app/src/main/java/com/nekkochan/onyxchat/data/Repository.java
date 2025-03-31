package com.nekkochan.onyxchat.data;

import android.app.Application;
import android.os.AsyncTask;
import android.content.Context;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository to manage data operations and provide a clean API for data access
 */
public class Repository {
    private final UserDao userDao;
    private final MessageDao messageDao;
    private final ContactDao contactDao;
    private final ExecutorService executorService;

    /**
     * Constructor to initialize the repository with DAOs
     */
    public Repository(Context context) {
        // Initialize SQLCipher before database access
        SafeHelperFactory.initSQLCipher(context.getApplicationContext());
        
        AppDatabase db = AppDatabase.getInstance(context);
        userDao = db.userDao();
        messageDao = db.messageDao();
        contactDao = db.contactDao();
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
        executorService.execute(() -> 
            contactDao.updateLastInteractionTime(ownerAddress, contactAddress, System.currentTimeMillis()));
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