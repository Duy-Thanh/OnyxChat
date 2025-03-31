package com.nekkochan.onyxchat.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nekkochan.onyxchat.model.User;

/**
 * Data Access Object for the User entity.
 */
@Dao
public interface UserDao {

    /**
     * Insert a user into the database.
     * If the user already exists, replace it.
     *
     * @param user The user to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    /**
     * Update a user in the database.
     *
     * @param user The user to update
     */
    @Update
    void updateUser(User user);

    /**
     * Delete a user from the database.
     *
     * @param user The user to delete
     */
    @Delete
    void deleteUser(User user);

    /**
     * Get the current user.
     * This assumes there is only one user in the database.
     *
     * @return The user, or null if no user exists
     */
    @Query("SELECT * FROM users LIMIT 1")
    User getUser();

    /**
     * Get the current user as LiveData.
     *
     * @return LiveData containing the user
     */
    @Query("SELECT * FROM users LIMIT 1")
    LiveData<User> getUserLive();

    /**
     * Get a user by ID.
     *
     * @param id The user ID
     * @return The user with the given ID, or null if not found
     */
    @Query("SELECT * FROM users WHERE id = :id")
    User getUserById(String id);

    /**
     * Get a user by ID as LiveData.
     *
     * @param id The user ID
     * @return LiveData containing the user with the given ID
     */
    @Query("SELECT * FROM users WHERE id = :id")
    LiveData<User> getUserByIdLive(String id);

    /**
     * Check if any user exists in the database.
     *
     * @return True if at least one user exists, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM users LIMIT 1)")
    boolean hasUser();

    /**
     * Delete all users from the database.
     */
    @Query("DELETE FROM users")
    void deleteAllUsers();

    /**
     * Count the number of users in the database.
     *
     * @return The number of users
     */
    @Query("SELECT COUNT(*) FROM users")
    int countUsers();
} 