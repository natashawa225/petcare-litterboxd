package com.litterboxd.app.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.WeightLog;

import java.util.ArrayList;
import java.util.List;

public class WeightLogDao {
    private final LitterboxdDbHelper dbHelper;

    public WeightLogDao(LitterboxdDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertWeightLog(WeightLog weightLog) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LitterboxdContract.WeightLogEntry.COLUMN_CAT_ID, weightLog.getCatId());
        values.put(LitterboxdContract.WeightLogEntry.COLUMN_WEIGHT, weightLog.getWeight());
        values.put(LitterboxdContract.WeightLogEntry.COLUMN_DATE, weightLog.getDate() > 0 ? weightLog.getDate() : System.currentTimeMillis());
        values.put(LitterboxdContract.WeightLogEntry.COLUMN_NOTES, weightLog.getNotes());
        return db.insert(LitterboxdContract.WeightLogEntry.TABLE_NAME, null, values);
    }

    public List<WeightLog> getWeightLogsForCat(long catId) {
        List<WeightLog> logs = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(LitterboxdContract.WeightLogEntry.TABLE_NAME,
                null,
                LitterboxdContract.WeightLogEntry.COLUMN_CAT_ID + " = ?",
                new String[]{String.valueOf(catId)},
                null, null,
                LitterboxdContract.WeightLogEntry.COLUMN_DATE + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    WeightLog log = new WeightLog();
                    log.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.WeightLogEntry.COLUMN_ID)));
                    log.setCatId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.WeightLogEntry.COLUMN_CAT_ID)));
                    log.setWeight(cursor.getDouble(cursor.getColumnIndexOrThrow(LitterboxdContract.WeightLogEntry.COLUMN_WEIGHT)));
                    log.setDate(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.WeightLogEntry.COLUMN_DATE)));
                    log.setNotes(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.WeightLogEntry.COLUMN_NOTES)));
                    logs.add(log);
                } while (cursor.moveToNext());
            }
        }
        return logs;
    }
}
