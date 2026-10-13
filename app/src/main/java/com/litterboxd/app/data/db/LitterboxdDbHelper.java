package com.litterboxd.app.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLiteOpenHelper for managing the Litterboxd SQLite database.
 * Reused and adapted from DataStorageDemo patterns.
 */
public class LitterboxdDbHelper extends SQLiteOpenHelper {

    private static final String SQL_CREATE_USERS =
            "CREATE TABLE " + LitterboxdContract.UserEntry.TABLE_NAME + " (" +
                    LitterboxdContract.UserEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.UserEntry.COLUMN_EMAIL + " TEXT UNIQUE NOT NULL, " +
                    LitterboxdContract.UserEntry.COLUMN_PASSWORD_HASH + " TEXT NOT NULL, " +
                    LitterboxdContract.UserEntry.COLUMN_SALT + " TEXT NOT NULL, " +
                    LitterboxdContract.UserEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL)";

    public LitterboxdDbHelper(Context context) {
        super(context, LitterboxdContract.DATABASE_NAME, null, LitterboxdContract.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_USERS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: Handle future schema upgrades for cats, health logs, and care schedules.
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.UserEntry.TABLE_NAME);
        onCreate(db);
    }
}
