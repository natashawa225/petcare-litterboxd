package com.litterboxd.app.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.User;

/**
 * Data Access Object for User entity using SQLiteOpenHelper, ContentValues, query, and Cursor.
 */
public class UserDao {
    private final LitterboxdDbHelper dbHelper;

    public UserDao(LitterboxdDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    /**
     * Inserts a new user into the database.
     * @return the row ID of the newly inserted row, or -iling if an error occurred (e.g. duplicate email).
     */
    public long insertUser(String email, String passwordHash, String salt, long createdAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LitterboxdContract.UserEntry.COLUMN_EMAIL, email);
        values.put(LitterboxdContract.UserEntry.COLUMN_PASSWORD_HASH, passwordHash);
        values.put(LitterboxdContract.UserEntry.COLUMN_SALT, salt);
        values.put(LitterboxdContract.UserEntry.COLUMN_CREATED_AT, createdAt);
        return db.insert(LitterboxdContract.UserEntry.TABLE_NAME, null, values);
    }

    /**
     * Finds a user by their email address.
     */
    public User getUserByEmail(String email) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
                LitterboxdContract.UserEntry.COLUMN_ID,
                LitterboxdContract.UserEntry.COLUMN_EMAIL,
                LitterboxdContract.UserEntry.COLUMN_PASSWORD_HASH,
                LitterboxdContract.UserEntry.COLUMN_SALT,
                LitterboxdContract.UserEntry.COLUMN_CREATED_AT
        };
        String selection = LitterboxdContract.UserEntry.COLUMN_EMAIL + " = ?";
        String[] selectionArgs = { email };

        try (Cursor cursor = db.query(
                LitterboxdContract.UserEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_ID));
                String userEmail = cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_EMAIL));
                String hash = cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_PASSWORD_HASH));
                String salt = cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_SALT));
                long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_CREATED_AT));
                return new User(id, userEmail, hash, salt, createdAt);
            }
        }
        return null;
    }

    /**
     * Finds a user by their ID.
     */
    public User getUserById(long userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
                LitterboxdContract.UserEntry.COLUMN_ID,
                LitterboxdContract.UserEntry.COLUMN_EMAIL,
                LitterboxdContract.UserEntry.COLUMN_PASSWORD_HASH,
                LitterboxdContract.UserEntry.COLUMN_SALT,
                LitterboxdContract.UserEntry.COLUMN_CREATED_AT
        };
        String selection = LitterboxdContract.UserEntry.COLUMN_ID + " = ?";
        String[] selectionArgs = { String.valueOf(userId) };

        try (Cursor cursor = db.query(
                LitterboxdContract.UserEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_ID));
                String userEmail = cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_EMAIL));
                String hash = cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_PASSWORD_HASH));
                String salt = cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_SALT));
                long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.UserEntry.COLUMN_CREATED_AT));
                return new User(id, userEmail, hash, salt, createdAt);
            }
        }
        return null;
    }
}
