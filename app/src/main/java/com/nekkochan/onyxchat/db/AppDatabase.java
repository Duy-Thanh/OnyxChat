package com.nekkochan.onyxchat.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.nekkochan.onyxchat.db.dao.ContactDao;
import com.nekkochan.onyxchat.db.dao.ConversationDao;
import com.nekkochan.onyxchat.db.dao.MessageDao;
import com.nekkochan.onyxchat.db.dao.UserDao;
import com.nekkochan.onyxchat.model.Contact;
import com.nekkochan.onyxchat.model.Conversation;
import com.nekkochan.onyxchat.model.Message;
import com.nekkochan.onyxchat.model.User;
import com.nekkochan.onyxchat.utils.Converters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Room database for the OnyxChat application.
 */
@Database(
    entities = {User.class, Contact.class, Conversation.class, Message.class},
    version = 1,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "onyxchat_db";
    private static volatile AppDatabase instance;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * Get the ContactDao interface
     */
    public abstract ContactDao contactDao();

    /**
     * Get the ConversationDao interface
     */
    public abstract ConversationDao conversationDao();

    /**
     * Get the MessageDao interface
     */
    public abstract MessageDao messageDao();

    /**
     * Get the UserDao interface
     */
    public abstract UserDao userDao();

    /**
     * Get the singleton instance of the AppDatabase
     *
     * @param context Application context
     * @return The AppDatabase instance
     */
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    DATABASE_NAME)
                    .addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            // Initialize database here if needed
                        }
                    })
                    .build();
        }
        return instance;
    }
} 