package com.nekkochan.onyxchat.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nekkochan.onyxchat.model.Conversation;

import java.util.List;

/**
 * Data Access Object for the Conversation entity.
 */
@Dao
public interface ConversationDao {

    /**
     * Insert a conversation into the database.
     * If the conversation already exists, replace it.
     *
     * @param conversation The conversation to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertConversation(Conversation conversation);

    /**
     * Update a conversation in the database.
     *
     * @param conversation The conversation to update
     */
    @Update
    void updateConversation(Conversation conversation);

    /**
     * Delete a conversation from the database.
     *
     * @param conversation The conversation to delete
     */
    @Delete
    void deleteConversation(Conversation conversation);

    /**
     * Get all conversations.
     *
     * @return List of all conversations
     */
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    List<Conversation> getAllConversationsSync();

    /**
     * Get all conversations as LiveData.
     *
     * @return LiveData containing a list of all conversations
     */
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    LiveData<List<Conversation>> getAllConversations();

    /**
     * Get all unarchived conversations.
     *
     * @return List of all unarchived conversations
     */
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    List<Conversation> getUnarchivedConversations();

    /**
     * Get all unarchived conversations as LiveData.
     *
     * @return LiveData containing a list of all unarchived conversations
     */
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    LiveData<List<Conversation>> getUnarchivedConversationsLive();

    /**
     * Get all archived conversations.
     *
     * @return List of all archived conversations
     */
    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY lastMessageTimestamp DESC")
    List<Conversation> getArchivedConversations();

    /**
     * Get all archived conversations as LiveData.
     *
     * @return LiveData containing a list of all archived conversations
     */
    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY lastMessageTimestamp DESC")
    LiveData<List<Conversation>> getArchivedConversationsLive();

    /**
     * Get a conversation by ID.
     *
     * @param id The conversation ID
     * @return The conversation with the given ID, or null if not found
     */
    @Query("SELECT * FROM conversations WHERE id = :id")
    Conversation getConversationById(String id);

    /**
     * Get a conversation by ID as LiveData.
     *
     * @param id The conversation ID
     * @return LiveData containing the conversation with the given ID
     */
    @Query("SELECT * FROM conversations WHERE id = :id")
    LiveData<Conversation> getConversationByIdLive(String id);

    /**
     * Get a conversation by contact address.
     *
     * @param contactAddress The contact's onion address
     * @return The conversation with the given contact address, or null if not found
     */
    @Query("SELECT * FROM conversations WHERE contactAddress = :contactAddress")
    Conversation getConversationByContactAddress(String contactAddress);

    /**
     * Get a conversation by contact address as LiveData.
     *
     * @param contactAddress The contact's onion address
     * @return LiveData containing the conversation with the given contact address
     */
    @Query("SELECT * FROM conversations WHERE contactAddress = :contactAddress")
    LiveData<Conversation> getConversationByContactAddressLive(String contactAddress);

    /**
     * Delete all conversations from the database.
     */
    @Query("DELETE FROM conversations")
    void deleteAllConversations();

    /**
     * Count the number of conversations in the database.
     *
     * @return The number of conversations
     */
    @Query("SELECT COUNT(*) FROM conversations")
    int countConversations();

    /**
     * Get all conversations with unread messages.
     *
     * @return List of conversations with unread messages
     */
    @Query("SELECT * FROM conversations WHERE unreadCount > 0 ORDER BY lastMessageTimestamp DESC")
    List<Conversation> getConversationsWithUnreadMessages();

    /**
     * Get all conversations with unread messages as LiveData.
     *
     * @return LiveData containing a list of conversations with unread messages
     */
    @Query("SELECT * FROM conversations WHERE unreadCount > 0 ORDER BY lastMessageTimestamp DESC")
    LiveData<List<Conversation>> getConversationsWithUnreadMessagesLive();

    /**
     * Get the total number of unread messages across all conversations.
     *
     * @return The total number of unread messages
     */
    @Query("SELECT SUM(unreadCount) FROM conversations")
    int getTotalUnreadMessageCount();

    /**
     * Get the total number of unread messages across all conversations as LiveData.
     *
     * @return LiveData containing the total number of unread messages
     */
    @Query("SELECT SUM(unreadCount) FROM conversations")
    LiveData<Integer> getTotalUnreadMessageCountLive();
}