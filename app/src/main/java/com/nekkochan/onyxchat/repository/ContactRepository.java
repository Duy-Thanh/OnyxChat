package com.nekkochan.onyxchat.repository;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nekkochan.onyxchat.db.AppDatabase;
import com.nekkochan.onyxchat.model.Contact;
import com.nekkochan.onyxchat.network.ApiClient;
import com.nekkochan.onyxchat.util.PermissionHelper;
import com.nekkochan.onyxchat.util.UserSessionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for managing contacts, including synchronization with device address book
 */
public class ContactRepository {
    private static final String TAG = "ContactRepository";
    
    private static ContactRepository instance;
    private final Context context;
    private final AppDatabase database;
    private final ExecutorService executor;
    private final UserSessionManager sessionManager;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> syncStatus = new MutableLiveData<>(false);
    
    /**
     * Private constructor for the singleton pattern
     */
    private ContactRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
        this.executor = Executors.newFixedThreadPool(2);
        this.sessionManager = new UserSessionManager(context);
    }
    
    /**
     * Get the singleton instance of the ContactRepository
     *
     * @param context Application context
     * @return The ContactRepository instance
     */
    public static synchronized ContactRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ContactRepository(context);
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
     * Get the sync status LiveData
     *
     * @return LiveData indicating whether a sync is in progress
     */
    public LiveData<Boolean> getSyncStatus() {
        return syncStatus;
    }
    
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
                errorMessage.postValue("Failed to add contact: " + e.getMessage());
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
                errorMessage.postValue("Failed to delete contact: " + e.getMessage());
            }
        });
    }
    
    /**
     * Sync contacts with the device address book.
     * This will check if any contacts in the address book exist in the Onyx network
     * and add them to the app's contacts.
     */
    public void syncWithAddressBook() {
        // Check if we have permission to read contacts
        if (!PermissionHelper.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            errorMessage.postValue("Contact permission is required for synchronization.");
            return;
        }
        
        syncStatus.postValue(true);
        
        executor.execute(() -> {
            try {
                // Get device contacts
                List<DeviceContact> deviceContacts = getDeviceContacts();
                if (deviceContacts.isEmpty()) {
                    Log.w(TAG, "No contacts found in device address book");
                    syncStatus.postValue(false);
                    return;
                }
                
                Log.d(TAG, "Found " + deviceContacts.size() + " contacts in address book");
                
                // Get existing contacts from the database
                List<Contact> existingContacts = database.contactDao().getAllContactsSync();
                Map<String, Contact> existingContactMap = new HashMap<>();
                for (Contact contact : existingContacts) {
                    existingContactMap.put(contact.getOnionAddress(), contact);
                }
                
                // Check which device contacts are in the Onyx network
                checkDeviceContactsOnNetwork(deviceContacts, existingContactMap);
                
            } catch (Exception e) {
                Log.e(TAG, "Error syncing contacts", e);
                errorMessage.postValue("Failed to sync contacts: " + e.getMessage());
            } finally {
                syncStatus.postValue(false);
            }
        });
    }
    
    /**
     * Get contacts from the device address book
     *
     * @return List of device contacts
     */
    private List<DeviceContact> getDeviceContacts() {
        List<DeviceContact> contacts = new ArrayList<>();
        
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );
        
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                
                // Get contact phone numbers
                List<String> phoneNumbers = new ArrayList<>();
                if (Integer.parseInt(cursor.getString(cursor.getColumnIndex(
                        ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
                    
                    Cursor phoneCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id},
                            null
                    );
                    
                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            String phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER));
                            // Normalize phone number
                            phoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
                            phoneNumbers.add(phoneNumber);
                        }
                        phoneCursor.close();
                    }
                }
                
                // Get contact email addresses
                List<String> emails = new ArrayList<>();
                Cursor emailCursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                        new String[]{id},
                        null
                );
                
                if (emailCursor != null) {
                    while (emailCursor.moveToNext()) {
                        String email = emailCursor.getString(emailCursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Email.ADDRESS));
                        emails.add(email);
                    }
                    emailCursor.close();
                }
                
                if (!phoneNumbers.isEmpty() || !emails.isEmpty()) {
                    DeviceContact contact = new DeviceContact();
                    contact.name = name;
                    contact.phoneNumbers = phoneNumbers;
                    contact.emails = emails;
                    contacts.add(contact);
                }
            }
            cursor.close();
        }
        
        return contacts;
    }
    
    /**
     * Check which device contacts are on the Onyx network and add them to app contacts
     *
     * @param deviceContacts List of device contacts
     * @param existingContacts Map of existing contacts by onion address
     */
    private void checkDeviceContactsOnNetwork(List<DeviceContact> deviceContacts, 
                                              Map<String, Contact> existingContacts) {
        // Since this is a mock implementation without an actual API endpoint,
        // we'll just log the intent to check contacts on the network
        Log.d(TAG, "Would check " + deviceContacts.size() + " contacts against Onyx network");
        
        // In a real implementation, we would:
        // 1. Send the phone numbers and emails to the server to check for matches
        // 2. For each match, create a Contact and add it to the database
        
        // For now, let's just mock the response by creating fake contacts for demonstration
        List<Contact> newContacts = new ArrayList<>();
        
        // For testing, create a contact for every 5th device contact
        for (int i = 0; i < deviceContacts.size(); i += 5) {
            DeviceContact deviceContact = deviceContacts.get(i);
            String onionAddress = generateMockOnionAddress(deviceContact);
            
            // Skip if already in contacts
            if (existingContacts.containsKey(onionAddress)) {
                Log.d(TAG, "Contact already exists: " + onionAddress);
                continue;
            }
            
            // Create new contact
            Contact contact = new Contact(onionAddress, deviceContact.name, "mock_public_key");
            newContacts.add(contact);
            Log.d(TAG, "Found new contact on network: " + deviceContact.name);
        }
        
        // Insert new contacts into database
        if (!newContacts.isEmpty()) {
            try {
                database.contactDao().insertContacts(newContacts);
                Log.d(TAG, "Added " + newContacts.size() + " new contacts from address book");
            } catch (Exception e) {
                Log.e(TAG, "Error adding contacts from address book", e);
                errorMessage.postValue("Failed to add contacts: " + e.getMessage());
            }
        }
    }
    
    /**
     * Generate a mock onion address for a device contact
     * In a real implementation, this would be the actual onion address from the server
     */
    private String generateMockOnionAddress(DeviceContact contact) {
        // Generate a deterministic mock onion address from contact details
        String seed = contact.name;
        if (!contact.phoneNumbers.isEmpty()) {
            seed += contact.phoneNumbers.get(0);
        } else if (!contact.emails.isEmpty()) {
            seed += contact.emails.get(0);
        }
        
        // Simple hash function to generate a mock onion address
        int hash = Math.abs(seed.hashCode());
        String onionBase = Integer.toHexString(hash);
        
        // Pad to ensure it's at least 16 characters
        while (onionBase.length() < 16) {
            onionBase = "0" + onionBase;
        }
        
        return onionBase.substring(0, 16) + ".onion";
    }
    
    /**
     * Helper class to represent a contact from the device address book
     */
    private static class DeviceContact {
        String name;
        List<String> phoneNumbers = new ArrayList<>();
        List<String> emails = new ArrayList<>();
    }
} 