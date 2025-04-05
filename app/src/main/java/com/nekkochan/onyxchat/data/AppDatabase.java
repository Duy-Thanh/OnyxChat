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
@Database(entities = {User.class, Message.class, Contact.class}, version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    
    public abstract UserDao userDao();
    public abstract MessageDao messageDao();
    public abstract ContactDao contactDao();
    
    /**
     * Migration from version 1 to 2 - adding isAppUser field to Contact
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add the isAppUser column to the contacts table with default value of 0 (false)
            database.execSQL("ALTER TABLE contacts ADD COLUMN isAppUser INTEGER NOT NULL DEFAULT 0");
        }
    };
    
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
                            // Add the migration
                            .addMigrations(MIGRATION_1_2)
                            // Fallback only as last resort
                            .fallbackToDestructiveMigration()
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