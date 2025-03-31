package com.nekkochan.onyxchat.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nekkochan.onyxchat.model.Contact;

import java.util.List;

/**
 * Data Access Object for the Contact entity.
 */
@Dao
public interface ContactDao {

    /**
     * Insert a contact into the database.
     * If the contact already exists, replace it.
     *
     * @param contact The contact to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertContact(Contact contact);

    /**
     * Insert multiple contacts into the database.
     * If any contact already exists, replace it.
     *
     * @param contacts The contacts to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertContacts(List<Contact> contacts);

    /**
     * Update a contact in the database.
     *
     * @param contact The contact to update
     */
    @Update
    void updateContact(Contact contact);

    /**
     * Delete a contact from the database.
     *
     * @param contact The contact to delete
     */
    @Delete
    void deleteContact(Contact contact);

    /**
     * Get all contacts.
     *
     * @return List of all contacts
     */
    @Query("SELECT * FROM contacts")
    List<Contact> getAllContactsSync();

    /**
     * Get all contacts as LiveData.
     *
     * @return LiveData containing a list of all contacts
     */
    @Query("SELECT * FROM contacts")
    LiveData<List<Contact>> getAllContacts();

    /**
     * Get all unblocked contacts.
     *
     * @return List of all unblocked contacts
     */
    @Query("SELECT * FROM contacts WHERE isBlocked = 0")
    List<Contact> getUnblockedContacts();

    /**
     * Get all unblocked contacts as LiveData.
     *
     * @return LiveData containing a list of all unblocked contacts
     */
    @Query("SELECT * FROM contacts WHERE isBlocked = 0")
    LiveData<List<Contact>> getUnblockedContactsLive();

    /**
     * Get all blocked contacts.
     *
     * @return List of all blocked contacts
     */
    @Query("SELECT * FROM contacts WHERE isBlocked = 1")
    List<Contact> getBlockedContacts();

    /**
     * Get all blocked contacts as LiveData.
     *
     * @return LiveData containing a list of all blocked contacts
     */
    @Query("SELECT * FROM contacts WHERE isBlocked = 1")
    LiveData<List<Contact>> getBlockedContactsLive();

    /**
     * Get all verified contacts.
     *
     * @return List of all verified contacts
     */
    @Query("SELECT * FROM contacts WHERE isVerified = 1")
    List<Contact> getVerifiedContacts();

    /**
     * Get all verified contacts as LiveData.
     *
     * @return LiveData containing a list of all verified contacts
     */
    @Query("SELECT * FROM contacts WHERE isVerified = 1")
    LiveData<List<Contact>> getVerifiedContactsLive();

    /**
     * Get a contact by onion address.
     *
     * @param onionAddress The contact's onion address
     * @return The contact with the given onion address, or null if not found
     */
    @Query("SELECT * FROM contacts WHERE onionAddress = :onionAddress")
    Contact getContactByAddress(String onionAddress);

    /**
     * Get a contact by onion address as LiveData.
     *
     * @param onionAddress The contact's onion address
     * @return LiveData containing the contact with the given onion address
     */
    @Query("SELECT * FROM contacts WHERE onionAddress = :onionAddress")
    LiveData<Contact> getContactByAddressLive(String onionAddress);

    /**
     * Search contacts by nickname or onion address.
     *
     * @param query The search query
     * @return List of contacts matching the query
     */
    @Query("SELECT * FROM contacts WHERE nickname LIKE '%' || :query || '%' OR onionAddress LIKE '%' || :query || '%'")
    List<Contact> searchContacts(String query);

    /**
     * Search contacts by nickname or onion address as LiveData.
     *
     * @param query The search query
     * @return LiveData containing a list of contacts matching the query
     */
    @Query("SELECT * FROM contacts WHERE nickname LIKE '%' || :query || '%' OR onionAddress LIKE '%' || :query || '%'")
    LiveData<List<Contact>> searchContactsLive(String query);

    /**
     * Delete all contacts from the database.
     */
    @Query("DELETE FROM contacts")
    void deleteAllContacts();

    /**
     * Count the number of contacts in the database.
     *
     * @return The number of contacts
     */
    @Query("SELECT COUNT(*) FROM contacts")
    int countContacts();
}