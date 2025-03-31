package com.nekkochan.onyxchat.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Main database for the SecureComm app
 */
@Database(entities = {User.class, Message.class, Contact.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    
    public abstract UserDao userDao();
    public abstract MessageDao messageDao();
    public abstract ContactDao contactDao();
    
    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Get Application context to avoid leaks
                    Context appContext = context.getApplicationContext();
                    
                    // Initialize SQLCipher first with context
                    SafeHelperFactory.initSQLCipher(appContext);
                    
                    INSTANCE = Room.databaseBuilder(
                            appContext,
                            AppDatabase.class,
                            "securecomm_db")
                            // Encrypt the database using SQLCipher with context
                            .openHelperFactory(new SafeHelperFactory("YOUR_ENCRYPTION_KEY".toCharArray(), appContext))
                            .fallbackToDestructiveMigration() // For simplicity in development
                            .build();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Callback for database creation
     */
    private static RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            // You can pre-populate the database here if needed
        }
    };
} 