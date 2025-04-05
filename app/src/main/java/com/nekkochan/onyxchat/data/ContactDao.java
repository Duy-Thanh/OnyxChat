package com.nekkochan.onyxchat.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object for Contact entities
 */
@Dao
public interface ContactDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Contact contact);
    
    @Update
    void update(Contact contact);
    
    @Delete
    void delete(Contact contact);
    
    @Query("SELECT * FROM contacts WHERE ownerAddress = :ownerAddress")
    LiveData<List<Contact>> getAllContacts(String ownerAddress);
    
    @Query("SELECT * FROM contacts WHERE ownerAddress = :ownerAddress AND contactAddress = :contactAddress")
    Contact getContact(String ownerAddress, String contactAddress);
    
    @Query("SELECT * FROM contacts WHERE ownerAddress = :ownerAddress AND isBlocked = 0 " +
           "ORDER BY lastInteractionTime DESC")
    LiveData<List<Contact>> getActiveContacts(String ownerAddress);
    
    @Query("SELECT * FROM contacts WHERE ownerAddress = :ownerAddress AND isBlocked = 1")
    LiveData<List<Contact>> getBlockedContacts(String ownerAddress);
    
    @Query("UPDATE contacts SET isBlocked = :blocked WHERE ownerAddress = :ownerAddress AND contactAddress = :contactAddress")
    void setContactBlocked(String ownerAddress, String contactAddress, boolean blocked);
    
    @Query("UPDATE contacts SET isVerified = :verified WHERE ownerAddress = :ownerAddress AND contactAddress = :contactAddress")
    void setContactVerified(String ownerAddress, String contactAddress, boolean verified);
    
    @Query("UPDATE contacts SET lastInteractionTime = :timestamp WHERE ownerAddress = :ownerAddress AND contactAddress = :contactAddress")
    void updateLastInteractionTime(String ownerAddress, String contactAddress, long timestamp);
    
    @Query("SELECT EXISTS(SELECT 1 FROM contacts WHERE ownerAddress = :ownerAddress AND contactAddress = :contactAddress LIMIT 1)")
    boolean contactExists(String ownerAddress, String contactAddress);
    
    @Query("SELECT * FROM contacts WHERE ownerAddress = :ownerAddress AND contactAddress = :contactAddress LIMIT 1")
    Contact getContactByAddresses(String ownerAddress, String contactAddress);
    
    @Query("SELECT * FROM contacts WHERE ownerAddress = :ownerAddress")
    List<Contact> getAllContactsForOwner(String ownerAddress);
} 