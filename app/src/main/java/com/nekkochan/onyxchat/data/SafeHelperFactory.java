package com.nekkochan.onyxchat.data;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;
import net.sqlcipher.database.SupportFactory;

/**
 * Factory for creating encrypted database connections using SQLCipher
 */
public class SafeHelperFactory implements SupportSQLiteOpenHelper.Factory {
    private final char[] passphrase;
    private final SQLiteDatabaseHook hook;
    private static boolean libsLoaded = false;
    private final Context context;

    /**
     * Initialize SQLCipher libraries
     * @param context Application context
     */
    public static synchronized void initSQLCipher(Context context) {
        if (!libsLoaded && context != null) {
            SQLiteDatabase.loadLibs(context);
            libsLoaded = true;
        }
    }

    /**
     * Creates a factory for SQLCipher-encrypted databases
     * @param passphrase Password used for encryption
     * @param context Application context
     */
    public SafeHelperFactory(char[] passphrase, Context context) {
        this(passphrase, null, context);
    }

    /**
     * Creates a factory for SQLCipher-encrypted databases with a custom hook
     * @param passphrase Password used for encryption
     * @param hook Custom database hook
     * @param context Application context
     */
    public SafeHelperFactory(char[] passphrase, SQLiteDatabaseHook hook, Context context) {
        this.passphrase = passphrase;
        this.hook = hook;
        this.context = context;
        
        // Initialize SQLCipher before any operation
        initSQLCipher(context);
    }

    @Override
    public SupportSQLiteOpenHelper create(SupportSQLiteOpenHelper.Configuration configuration) {
        // Convert char[] to byte[] for SupportFactory
        byte[] passphraseBytes = new byte[passphrase.length];
        for (int i = 0; i < passphrase.length; i++) {
            passphraseBytes[i] = (byte) passphrase[i];
        }
        return new SupportFactory(passphraseBytes, hook).create(configuration);
    }
} 