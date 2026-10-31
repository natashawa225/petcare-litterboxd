package com.litterboxd.app.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.litterboxd.app.data.db.LitterboxdContract.DiaryEntryEntry;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.DiaryEntry;
import com.litterboxd.app.util.DiaryTags;

import java.util.ArrayList;
import java.util.List;

public class DiaryDao {
    private final LitterboxdDbHelper dbHelper;

    public DiaryDao(LitterboxdDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertEntry(DiaryEntry entry) {
        long now = System.currentTimeMillis();
        if (entry.getCreatedAt() <= 0) entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long id = db.insert(DiaryEntryEntry.TABLE_NAME, null, toValues(entry));
        entry.setId(id);
        return id;
    }

    public int updateEntry(DiaryEntry entry) {
        entry.setUpdatedAt(System.currentTimeMillis());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.update(DiaryEntryEntry.TABLE_NAME, toValues(entry),
                DiaryEntryEntry.COLUMN_ID + " = ?", new String[]{String.valueOf(entry.getId())});
    }

    public int deleteEntry(long entryId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DiaryEntryEntry.TABLE_NAME,
                DiaryEntryEntry.COLUMN_ID + " = ?", new String[]{String.valueOf(entryId)});
    }

    public DiaryEntry getEntryById(long entryId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(DiaryEntryEntry.TABLE_NAME, null,
                DiaryEntryEntry.COLUMN_ID + " = ?", new String[]{String.valueOf(entryId)},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToEntry(cursor);
            }
        }
        return null;
    }

    /** All of one cat's entries, newest first (ties broken by id so the order is stable). */
    public List<DiaryEntry> getEntriesForCat(long catId) {
        List<DiaryEntry> entries = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(DiaryEntryEntry.TABLE_NAME, null,
                DiaryEntryEntry.COLUMN_CAT_ID + " = ?", new String[]{String.valueOf(catId)},
                null, null,
                DiaryEntryEntry.COLUMN_ENTRY_AT + " DESC, " + DiaryEntryEntry.COLUMN_ID + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    entries.add(cursorToEntry(cursor));
                } while (cursor.moveToNext());
            }
        }
        return entries;
    }

    /** Photo file names used by one cat's entries (to clean up files when the cat is deleted). */
    public List<String> getPhotoPathsForCat(long catId) {
        List<String> paths = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(DiaryEntryEntry.TABLE_NAME,
                new String[]{DiaryEntryEntry.COLUMN_PHOTO_PATH},
                DiaryEntryEntry.COLUMN_CAT_ID + " = ? AND " + DiaryEntryEntry.COLUMN_PHOTO_PATH + " IS NOT NULL",
                new String[]{String.valueOf(catId)}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                paths.add(cursor.getString(0));
            }
        }
        return paths;
    }

    /** How many entries (any cat) still point at this photo file; a file is only deleted at 0. */
    public int countPhotoReferences(String photoPath) {
        if (photoPath == null) return 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DiaryEntryEntry.TABLE_NAME +
                " WHERE " + DiaryEntryEntry.COLUMN_PHOTO_PATH + " = ?", new String[]{photoPath})) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    private ContentValues toValues(DiaryEntry entry) {
        ContentValues values = new ContentValues();
        values.put(DiaryEntryEntry.COLUMN_CAT_ID, entry.getCatId());
        values.put(DiaryEntryEntry.COLUMN_TYPE, entry.getType().name());
        values.put(DiaryEntryEntry.COLUMN_TITLE, entry.getTitle());
        values.put(DiaryEntryEntry.COLUMN_DESCRIPTION, entry.getDescription());
        values.put(DiaryEntryEntry.COLUMN_ENTRY_AT, entry.getEntryAt());
        values.put(DiaryEntryEntry.COLUMN_PHOTO_PATH, entry.getPhotoPath());
        values.put(DiaryEntryEntry.COLUMN_TAGS, DiaryTags.encode(entry.getTags()));
        values.put(DiaryEntryEntry.COLUMN_CREATED_AT, entry.getCreatedAt());
        values.put(DiaryEntryEntry.COLUMN_UPDATED_AT, entry.getUpdatedAt());
        return values;
    }

    private DiaryEntry cursorToEntry(Cursor cursor) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_ID)));
        entry.setCatId(cursor.getLong(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_CAT_ID)));
        entry.setType(DiaryEntry.Type.fromStorage(cursor.getString(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_TYPE))));
        entry.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_TITLE)));
        entry.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_DESCRIPTION)));
        entry.setEntryAt(cursor.getLong(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_ENTRY_AT)));
        entry.setPhotoPath(cursor.getString(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_PHOTO_PATH)));
        entry.setTags(DiaryTags.decode(cursor.getString(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_TAGS))));
        entry.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_CREATED_AT)));
        entry.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(DiaryEntryEntry.COLUMN_UPDATED_AT)));
        return entry;
    }
}
