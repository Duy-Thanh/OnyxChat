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
 * Data Access Object for Message entities
 */
@Dao
public interface MessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Message message);
    
    @Update
    void update(Message message);
    
    @Delete
    void delete(Message message);
    
    @Query("SELECT * FROM messages WHERE id = :id")
    Message getMessageById(String id);
    
    @Query("SELECT * FROM messages WHERE isDeleted = 0 AND " +
           "((senderId = :userAddress AND recipientId = :contactAddress) OR " +
           "(senderId = :contactAddress AND recipientId = :userAddress)) " +
           "ORDER BY timestamp ASC")
    LiveData<List<Message>> getMessagesForContact(String userAddress, String contactAddress);
    
    @Query("SELECT * FROM messages WHERE isDeleted = 0 AND " +
           "((senderId = :userAddress AND recipientId = :contactAddress) OR " +
           "(senderId = :contactAddress AND recipientId = :userAddress)) " +
           "ORDER BY timestamp DESC LIMIT 1")
    Message getLastMessageForContact(String userAddress, String contactAddress);
    
    @Query("SELECT DISTINCT recipientId FROM messages WHERE senderId = :userAddress " +
           "UNION SELECT DISTINCT senderId FROM messages WHERE recipientId = :userAddress")
    LiveData<List<String>> getAllContactAddresses(String userAddress);
    
    @Query("UPDATE messages SET isRead = 1 WHERE senderId = :contactAddress AND recipientId = :userAddress")
    void markAsRead(String userAddress, String contactAddress);
    
    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :messageId")
    void markAsDeleted(String messageId);
    
    @Query("DELETE FROM messages WHERE isDeleted = 1")
    void permanentlyDeleteMarkedMessages();
    
    @Query("DELETE FROM messages WHERE expirationTime IS NOT NULL AND expirationTime < :currentTime AND isDeleted = 0")
    void deleteExpiredMessages(long currentTime);
    
    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND recipientId = :userAddress AND senderId = :contactAddress")
    LiveData<Integer> getUnreadMessageCount(String userAddress, String contactAddress);
} 