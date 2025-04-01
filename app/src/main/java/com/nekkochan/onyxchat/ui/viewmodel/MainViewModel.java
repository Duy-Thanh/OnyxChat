package com.nekkochan.onyxchat.ui.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nekkochan.onyxchat.crypto.PQCProvider;
import com.nekkochan.onyxchat.data.Contact;
import com.nekkochan.onyxchat.data.Repository;
import com.nekkochan.onyxchat.data.SafeHelperFactory;
import com.nekkochan.onyxchat.data.User;

import java.security.KeyPair;
import java.util.List;

/**
 * ViewModel for the MainActivity
 * Handles UI-related data in a lifecycle-conscious way
 */
public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MainViewModel";
    private final Repository repository;
    
    // Network connection state
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private final MutableLiveData<String> userAddress = new MutableLiveData<>();
    
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
     * Get the repository instance
     * @return The repository
     */
    public Repository getRepository() {
        return repository;
    }
} 