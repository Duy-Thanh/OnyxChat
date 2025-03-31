package com.nekkochan.onyxchat.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nekkochan.onyxchat.model.Message;

import java.util.List;

/**
 * Data Access Object for the Message entity.
 */
@Dao
public interface MessageDao {

    /**
     * Insert a message into the database.
     * If the message already exists, replace it.
     *
     * @param message The message to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(Message message);

    /**
     * Insert multiple messages into the database.
     * If any message already exists, replace it.
     *
     * @param messages The messages to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessages(List<Message> messages);

    /**
     * Update a message in the database.
     *
     * @param message The message to update
     */
    @Update
    void updateMessage(Message message);

    /**
     * Delete a message from the database.
     *
     * @param message The message to delete
     */
    @Delete
    void deleteMessage(Message message);

    /**
     * Get all messages.
     *
     * @return List of all messages
     */
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    List<Message> getAllMessages();

    /**
     * Get all messages as LiveData.
     *
     * @return LiveData containing a list of all messages
     */
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    LiveData<List<Message>> getAllMessagesLive();

    /**
     * Get all messages between two users.
     *
     * @param address1 First user's onion address
     * @param address2 Second user's onion address
     * @return List of messages between the two users
     */
    @Query("SELECT * FROM messages WHERE (senderAddress = :address1 AND receiverAddress = :address2) OR (senderAddress = :address2 AND receiverAddress = :address1) ORDER BY timestamp ASC")
    List<Message> getMessagesBetweenUsers(String address1, String address2);

    /**
     * Get all messages between two users as LiveData.
     *
     * @param address1 First user's onion address
     * @param address2 Second user's onion address
     * @return LiveData containing a list of messages between the two users
     */
    @Query("SELECT * FROM messages WHERE (senderAddress = :address1 AND receiverAddress = :address2) OR (senderAddress = :address2 AND receiverAddress = :address1) ORDER BY timestamp ASC")
    LiveData<List<Message>> getMessagesBetweenUsersLive(String address1, String address2);

    /**
     * Get messages for a conversation.
     *
     * @param conversationId The conversation ID
     * @return LiveData containing a list of messages for the conversation
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    LiveData<List<Message>> getMessagesForConversation(String conversationId);

    /**
     * Get a message by ID.
     *
     * @param id The message ID
     * @return The message with the given ID, or null if not found
     */
    @Query("SELECT * FROM messages WHERE id = :id")
    Message getMessageById(String id);

    /**
     * Get a message by ID as LiveData.
     *
     * @param id The message ID
     * @return LiveData containing the message with the given ID
     */
    @Query("SELECT * FROM messages WHERE id = :id")
    LiveData<Message> getMessageByIdLive(String id);

    /**
     * Get all unread messages.
     *
     * @return List of all unread messages
     */
    @Query("SELECT * FROM messages WHERE isRead = 0 ORDER BY timestamp ASC")
    List<Message> getUnreadMessages();

    /**
     * Get all unread messages for a conversation.
     *
     * @param conversationId The conversation ID
     * @return List of unread messages for the conversation
     */
    @Query("SELECT * FROM messages WHERE isRead = 0 AND conversationId = :conversationId ORDER BY timestamp ASC")
    List<Message> getUnreadMessagesForConversation(String conversationId);

    /**
     * Get all unread messages as LiveData.
     *
     * @return LiveData containing a list of all unread messages
     */
    @Query("SELECT * FROM messages WHERE isRead = 0 ORDER BY timestamp ASC")
    LiveData<List<Message>> getUnreadMessagesLive();

    /**
     * Count the number of unread messages.
     *
     * @return The number of unread messages
     */
    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0")
    int countUnreadMessages();

    /**
     * Count the number of unread messages for a sender.
     *
     * @param senderAddress The sender's onion address
     * @return The number of unread messages from the sender
     */
    @Query("SELECT COUNT(*) FROM messages WHERE senderAddress = :senderAddress AND isRead = 0")
    int countUnreadMessagesFromSender(String senderAddress);

    /**
     * Delete all messages from the database.
     */
    @Query("DELETE FROM messages")
    void deleteAllMessages();

    /**
     * Delete all messages between two users.
     *
     * @param address1 First user's onion address
     * @param address2 Second user's onion address
     */
    @Query("DELETE FROM messages WHERE (senderAddress = :address1 AND receiverAddress = :address2) OR (senderAddress = :address2 AND receiverAddress = :address1)")
    void deleteMessagesBetweenUsers(String address1, String address2);

    /**
     * Delete all messages from a sender.
     *
     * @param senderAddress The sender's onion address
     */
    @Query("DELETE FROM messages WHERE senderAddress = :senderAddress")
    void deleteMessagesFromSender(String senderAddress);
} 