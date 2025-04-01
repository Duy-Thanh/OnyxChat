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
 * Data Access Object for User entities
 */
@Dao
public interface UserDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(User user);
    
    @Update
    void update(User user);
    
    @Delete
    void delete(User user);
    
    @Query("SELECT * FROM users WHERE address = :address")
    User getUserByAddress(String address);
    
    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    User getCurrentUser();
    
    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    LiveData<User> observeCurrentUser();
    
    @Query("SELECT * FROM users WHERE address IN " +
           "(SELECT contactAddress FROM contacts WHERE ownerAddress = :userAddress)")
    LiveData<List<User>> getContactUsers(String userAddress);
    
    @Query("UPDATE users SET lastSeen = :timestamp WHERE address = :address")
    void updateLastSeen(String address, long timestamp);
    
    @Query("SELECT * FROM users WHERE address != :currentUserAddress " +
           "ORDER BY lastSeen DESC")
    LiveData<List<User>> getAllOtherUsers(String currentUserAddress);
} 