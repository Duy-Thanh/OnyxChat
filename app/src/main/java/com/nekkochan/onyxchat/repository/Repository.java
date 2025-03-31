package com.nekkochan.onyxchat.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nekkochan.onyxchat.crypto.PQCProvider;
import com.nekkochan.onyxchat.db.AppDatabase;
import com.nekkochan.onyxchat.model.Contact;
import com.nekkochan.onyxchat.model.Conversation;
import com.nekkochan.onyxchat.model.Message;
import com.nekkochan.onyxchat.model.User;

import java.security.KeyPair;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository class that handles data operations between the ViewModel and data sources
 * (Room database and network).
 */
public class Repository {
    private static final String TAG = "Repository";
    
    private static Repository instance;
    private final AppDatabase database;
    private final ExecutorService executor;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    /**
     * Private constructor for the singleton pattern
     */
    private Repository(Context context) {
        database = AppDatabase.getInstance(context);
        executor = Executors.newFixedThreadPool(4);
    }

    /**
     * Get the singleton instance of the Repository
     *
     * @param context Application context
     * @return The Repository instance
     */
    public static synchronized Repository getInstance(Context context) {
        if (instance == null) {
            instance = new Repository(context);
        }
        return instance;
    }

    /**
     * Get the error message LiveData
     *
     * @return LiveData containing error messages
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Set an error message to be observed
     *
     * @param message The error message
     */
    public void setErrorMessage(String message) {
        Log.e(TAG, "Error: " + message);
        errorMessage.postValue(message);
    }

    /**
     * Clear the error message
     */
    public void clearErrorMessage() {
        errorMessage.postValue(null);
    }

    // User operations

    /**
     * Get the current user
     *
     * @return LiveData containing the user
     */
    public LiveData<User> getUser() {
        return database.userDao().getUserLive();
    }

    /**
     * Get the current user synchronously
     *
     * @return The user
     */
    public User getUserSync() {
        return database.userDao().getUser();
    }

    /**
     * Save a user to the database
     *
     * @param user The user to save
     */
    public void saveUser(User user) {
        executor.execute(() -> {
            try {
                database.userDao().insertUser(user);
            } catch (Exception e) {
                Log.e(TAG, "Error saving user", e);
                setErrorMessage("Failed to save user: " + e.getMessage());
            }
        });
    }

    /**
     * Create a new user with a generated key pair
     *
     * @param displayName The user's display name
     * @param onionAddress The user's onion address
     * @return The created user
     */
    public User createUser(String displayName, String onionAddress) {
        try {
            // Generate new key pair using Java security
            KeyPair keyPair = PQCProvider.generateKyberKeyPair();
            if (keyPair == null) {
                setErrorMessage("Failed to generate key pair");
                return null;
            }
            
            String publicKey = PQCProvider.encodePublicKey(keyPair.getPublic());
            String privateKey = PQCProvider.encodePrivateKey(keyPair.getPrivate());
            
            String userId = UUID.randomUUID().toString();
            User user = new User(
                userId,
                displayName,
                onionAddress,
                privateKey,
                publicKey,
                "KYBER"
            );
            
            saveUser(user);
            return user;
        } catch (Exception e) {
            Log.e(TAG, "Error creating user", e);
            setErrorMessage("Failed to create user: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate a new key pair for the user
     *
     * @param user The user
     */
    public void regenerateUserKeyPair(User user) {
        try {
            KeyPair keyPair = PQCProvider.generateKyberKeyPair();
            if (keyPair == null) {
                setErrorMessage("Failed to generate key pair");
                return;
            }
            
            String publicKey = PQCProvider.encodePublicKey(keyPair.getPublic());
            String privateKey = PQCProvider.encodePrivateKey(keyPair.getPrivate());
            
            user.setPrivateKey(privateKey);
            user.setPublicKey(publicKey);
            user.setKeyAlgorithm("KYBER");
            
            saveUser(user);
        } catch (Exception e) {
            Log.e(TAG, "Error regenerating user key pair", e);
            setErrorMessage("Failed to regenerate key pair: " + e.getMessage());
        }
    }

    // Contact operations

    /**
     * Get all contacts
     *
     * @return LiveData containing a list of contacts
     */
    public LiveData<List<Contact>> getContacts() {
        return database.contactDao().getAllContacts();
    }

    /**
     * Add a new contact
     *
     * @param onionAddress The contact's onion address
     * @param nickname The contact's nickname
     * @param publicKey The contact's public key
     */
    public void addContact(String onionAddress, String nickname, String publicKey) {
        executor.execute(() -> {
            try {
                Contact contact = new Contact(onionAddress, nickname, publicKey);
                database.contactDao().insertContact(contact);
            } catch (Exception e) {
                Log.e(TAG, "Error adding contact", e);
                setErrorMessage("Failed to add contact: " + e.getMessage());
            }
        });
    }

    /**
     * Update a contact
     *
     * @param contact The contact to update
     */
    public void updateContact(Contact contact) {
        executor.execute(() -> {
            try {
                database.contactDao().updateContact(contact);
            } catch (Exception e) {
                Log.e(TAG, "Error updating contact", e);
                setErrorMessage("Failed to update contact: " + e.getMessage());
            }
        });
    }

    /**
     * Delete a contact
     *
     * @param contact The contact to delete
     */
    public void deleteContact(Contact contact) {
        executor.execute(() -> {
            try {
                database.contactDao().deleteContact(contact);
            } catch (Exception e) {
                Log.e(TAG, "Error deleting contact", e);
                setErrorMessage("Failed to delete contact: " + e.getMessage());
            }
        });
    }

    /**
     * Get a contact by onion address
     *
     * @param onionAddress The contact's onion address
     * @return LiveData containing the contact
     */
    public LiveData<Contact> getContactByAddress(String onionAddress) {
        return database.contactDao().getContactByAddressLive(onionAddress);
    }

    // Conversation operations

    /**
     * Get all conversations
     *
     * @return LiveData containing a list of conversations
     */
    public LiveData<List<Conversation>> getConversations() {
        return database.conversationDao().getAllConversations();
    }

    /**
     * Create a new conversation
     *
     * @param contactAddress The contact's onion address
     * @param isEncrypted Whether the conversation is encrypted
     * @return The created conversation
     */
    public Conversation createConversation(String contactAddress, boolean isEncrypted) {
        String conversationId = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(conversationId, contactAddress, isEncrypted);
        
        executor.execute(() -> {
            try {
                database.conversationDao().insertConversation(conversation);
            } catch (Exception e) {
                Log.e(TAG, "Error creating conversation", e);
                setErrorMessage("Failed to create conversation: " + e.getMessage());
            }
        });
        
        return conversation;
    }

    /**
     * Get a conversation by contact address
     *
     * @param contactAddress The contact's onion address
     * @return LiveData containing the conversation
     */
    public LiveData<Conversation> getConversationByContactAddress(String contactAddress) {
        return database.conversationDao().getConversationByContactAddressLive(contactAddress);
    }

    /**
     * Update a conversation
     *
     * @param conversation The conversation to update
     */
    public void updateConversation(Conversation conversation) {
        executor.execute(() -> {
            try {
                database.conversationDao().updateConversation(conversation);
            } catch (Exception e) {
                Log.e(TAG, "Error updating conversation", e);
                setErrorMessage("Failed to update conversation: " + e.getMessage());
            }
        });
    }

    /**
     * Delete a conversation
     *
     * @param conversation The conversation to delete
     */
    public void deleteConversation(Conversation conversation) {
        executor.execute(() -> {
            try {
                database.conversationDao().deleteConversation(conversation);
            } catch (Exception e) {
                Log.e(TAG, "Error deleting conversation", e);
                setErrorMessage("Failed to delete conversation: " + e.getMessage());
            }
        });
    }

    // Message operations

    /**
     * Get messages for a conversation
     *
     * @param conversationId The conversation ID
     * @return LiveData containing a list of messages
     */
    public LiveData<List<Message>> getMessagesForConversation(String conversationId) {
        return database.messageDao().getMessagesForConversation(conversationId);
    }

    /**
     * Send a message
     *
     * @param content The message content
     * @param senderAddress The sender's onion address
     * @param receiverAddress The receiver's onion address
     * @param isEncrypted Whether the message is encrypted
     * @return The created message
     */
    public Message sendMessage(String content, String senderAddress, String receiverAddress, boolean isEncrypted) {
        String messageId = UUID.randomUUID().toString();
        String conversationId = senderAddress + "_" + receiverAddress; // Generate a conversation ID
        Message message = new Message(messageId, content, senderAddress, receiverAddress, conversationId, true, isEncrypted);
        
        executor.execute(() -> {
            try {
                database.messageDao().insertMessage(message);
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                setErrorMessage("Failed to send message: " + e.getMessage());
            }
        });
        
        return message;
    }

    /**
     * Mark a message as read
     *
     * @param messageId The message ID
     */
    public void markMessageAsRead(String messageId) {
        executor.execute(() -> {
            try {
                Message message = database.messageDao().getMessageById(messageId);
                if (message != null) {
                    message.setRead(true);
                    database.messageDao().updateMessage(message);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error marking message as read", e);
                setErrorMessage("Failed to mark message as read: " + e.getMessage());
            }
        });
    }

    /**
     * Mark all messages in a conversation as read
     *
     * @param conversationId The conversation ID
     */
    public void markAllMessagesAsRead(String conversationId) {
        executor.execute(() -> {
            try {
                List<Message> messages = database.messageDao().getUnreadMessagesForConversation(conversationId);
                for (Message message : messages) {
                    message.setRead(true);
                    database.messageDao().updateMessage(message);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error marking all messages as read", e);
                setErrorMessage("Failed to mark all messages as read: " + e.getMessage());
            }
        });
    }

    /**
     * Delete a message
     *
     * @param message The message to delete
     */
    public void deleteMessage(Message message) {
        executor.execute(() -> {
            try {
                database.messageDao().deleteMessage(message);
            } catch (Exception e) {
                Log.e(TAG, "Error deleting message", e);
                setErrorMessage("Failed to delete message: " + e.getMessage());
            }
        });
    }
} 