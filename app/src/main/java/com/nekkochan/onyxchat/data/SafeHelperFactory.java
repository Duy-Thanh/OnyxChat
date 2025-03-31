package com.nekkochan.onyxchat.data;

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

    static {
        SQLiteDatabase.loadLibs(null);
    }

    /**
     * Creates a factory for SQLCipher-encrypted databases
     * @param passphrase Password used for encryption
     */
    public SafeHelperFactory(char[] passphrase) {
        this(passphrase, null);
    }

    /**
     * Creates a factory for SQLCipher-encrypted databases with a custom hook
     * @param passphrase Password used for encryption
     * @param hook Custom database hook
     */
    public SafeHelperFactory(char[] passphrase, SQLiteDatabaseHook hook) {
        this.passphrase = passphrase;
        this.hook = hook;
    }

    @Override
    public SupportSQLiteOpenHelper create(SupportSQLiteOpenHelper.Configuration configuration) {
        return new SupportFactory(passphrase, hook).create(configuration);
    }
} 