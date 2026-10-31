package com.litterboxd.app.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.DailyCheckin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DailyCheckinDao {
    private final LitterboxdDbHelper dbHelper;

    public DailyCheckinDao(LitterboxdDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    /**
     * Saves the check-in for this cat and day. If one already exists it is updated and its
     * answers are replaced, so there is never more than one check-in per cat per day.
     * The check-in row and its answers are written in one transaction.
     *
     * @return the id of the check-in row
     */
    public long saveCheckin(long catId, String checkinDate, Map<String, List<String>> answers) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            long checkinId = findCheckinId(db, catId, checkinDate);
            if (checkinId == -1) {
                ContentValues values = new ContentValues();
                values.put(LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID, catId);
                values.put(LitterboxdContract.DailyCheckinEntry.COLUMN_CHECKIN_DATE, checkinDate);
                values.put(LitterboxdContract.DailyCheckinEntry.COLUMN_CREATED_AT, now);
                values.put(LitterboxdContract.DailyCheckinEntry.COLUMN_UPDATED_AT, now);
                checkinId = db.insertOrThrow(LitterboxdContract.DailyCheckinEntry.TABLE_NAME, null, values);
            } else {
                ContentValues values = new ContentValues();
                values.put(LitterboxdContract.DailyCheckinEntry.COLUMN_UPDATED_AT, now);
                db.update(LitterboxdContract.DailyCheckinEntry.TABLE_NAME, values,
                        LitterboxdContract.DailyCheckinEntry.COLUMN_ID + " = ?",
                        new String[]{String.valueOf(checkinId)});
                db.delete(LitterboxdContract.CheckinAnswerEntry.TABLE_NAME,
                        LitterboxdContract.CheckinAnswerEntry.COLUMN_CHECKIN_ID + " = ?",
                        new String[]{String.valueOf(checkinId)});
            }

            for (Map.Entry<String, List<String>> entry : answers.entrySet()) {
                for (String value : entry.getValue()) {
                    ContentValues row = new ContentValues();
                    row.put(LitterboxdContract.CheckinAnswerEntry.COLUMN_CHECKIN_ID, checkinId);
                    row.put(LitterboxdContract.CheckinAnswerEntry.COLUMN_QUESTION_ID, entry.getKey());
                    row.put(LitterboxdContract.CheckinAnswerEntry.COLUMN_ANSWER_VALUE, value);
                    db.insertOrThrow(LitterboxdContract.CheckinAnswerEntry.TABLE_NAME, null, row);
                }
            }
            db.setTransactionSuccessful();
            return checkinId;
        } finally {
            db.endTransaction();
        }
    }

    /** Returns the check-in for this cat and day with its answers, or null if none was saved. */
    public DailyCheckin getCheckin(long catId, String checkinDate) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        DailyCheckin checkin = null;
        try (Cursor cursor = db.query(LitterboxdContract.DailyCheckinEntry.TABLE_NAME,
                null,
                LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID + " = ? AND " +
                        LitterboxdContract.DailyCheckinEntry.COLUMN_CHECKIN_DATE + " = ?",
                new String[]{String.valueOf(catId), checkinDate},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                checkin = new DailyCheckin();
                checkin.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_ID)));
                checkin.setCatId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID)));
                checkin.setCheckinDate(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_CHECKIN_DATE)));
                checkin.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_CREATED_AT)));
                checkin.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_UPDATED_AT)));
            }
        }
        if (checkin != null) {
            checkin.setAnswers(readAnswers(db, checkin.getId()));
        }
        return checkin;
    }

    /** All of one cat's check-ins with their answers, newest day first (for the diary's care history). */
    public List<DailyCheckin> getCheckinsForCat(long catId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<DailyCheckin> checkins = new ArrayList<>();
        try (Cursor cursor = db.query(LitterboxdContract.DailyCheckinEntry.TABLE_NAME,
                null,
                LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID + " = ?",
                new String[]{String.valueOf(catId)},
                null, null,
                LitterboxdContract.DailyCheckinEntry.COLUMN_CHECKIN_DATE + " DESC")) {
            while (cursor != null && cursor.moveToNext()) {
                DailyCheckin checkin = new DailyCheckin();
                checkin.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_ID)));
                checkin.setCatId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID)));
                checkin.setCheckinDate(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_CHECKIN_DATE)));
                checkin.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_CREATED_AT)));
                checkin.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.DailyCheckinEntry.COLUMN_UPDATED_AT)));
                checkins.add(checkin);
            }
        }
        for (DailyCheckin checkin : checkins) {
            checkin.setAnswers(readAnswers(db, checkin.getId()));
        }
        return checkins;
    }

    private long findCheckinId(SQLiteDatabase db, long catId, String checkinDate) {
        try (Cursor cursor = db.query(LitterboxdContract.DailyCheckinEntry.TABLE_NAME,
                new String[]{LitterboxdContract.DailyCheckinEntry.COLUMN_ID},
                LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID + " = ? AND " +
                        LitterboxdContract.DailyCheckinEntry.COLUMN_CHECKIN_DATE + " = ?",
                new String[]{String.valueOf(catId), checkinDate},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }
        return -1;
    }

    private Map<String, List<String>> readAnswers(SQLiteDatabase db, long checkinId) {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        try (Cursor cursor = db.query(LitterboxdContract.CheckinAnswerEntry.TABLE_NAME,
                null,
                LitterboxdContract.CheckinAnswerEntry.COLUMN_CHECKIN_ID + " = ?",
                new String[]{String.valueOf(checkinId)},
                null, null,
                LitterboxdContract.CheckinAnswerEntry.COLUMN_ID + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String questionId = cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CheckinAnswerEntry.COLUMN_QUESTION_ID));
                    String value = cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CheckinAnswerEntry.COLUMN_ANSWER_VALUE));
                    List<String> values = answers.get(questionId);
                    if (values == null) {
                        values = new ArrayList<>();
                        answers.put(questionId, values);
                    }
                    values.add(value);
                } while (cursor.moveToNext());
            }
        }
        return answers;
    }
}
