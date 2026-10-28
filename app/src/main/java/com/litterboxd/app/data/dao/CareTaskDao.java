package com.litterboxd.app.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.CareTaskLog;
import com.litterboxd.app.data.model.TaskFrequency;

import java.util.ArrayList;
import java.util.List;

public class CareTaskDao {
    private final LitterboxdDbHelper dbHelper;

    public CareTaskDao(LitterboxdDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertTask(CareTask task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long id = db.insert(LitterboxdContract.CareTaskEntry.TABLE_NAME, null, toValues(task));
        task.setId(id);
        return id;
    }

    public int updateTask(CareTask task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.update(LitterboxdContract.CareTaskEntry.TABLE_NAME, toValues(task),
                LitterboxdContract.CareTaskEntry.COLUMN_ID + " = ?",
                new String[]{String.valueOf(task.getId())});
    }

    public int deleteTask(long taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(LitterboxdContract.CareTaskEntry.TABLE_NAME,
                LitterboxdContract.CareTaskEntry.COLUMN_ID + " = ?",
                new String[]{String.valueOf(taskId)});
    }

    public CareTask getTaskById(long taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(LitterboxdContract.CareTaskEntry.TABLE_NAME,
                null,
                LitterboxdContract.CareTaskEntry.COLUMN_ID + " = ?",
                new String[]{String.valueOf(taskId)},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToTask(cursor);
            }
        }
        return null;
    }

    public List<CareTask> getTasksForCat(long catId) {
        List<CareTask> tasks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(LitterboxdContract.CareTaskEntry.TABLE_NAME,
                null,
                LitterboxdContract.CareTaskEntry.COLUMN_CAT_ID + " = ?",
                new String[]{String.valueOf(catId)},
                null, null,
                LitterboxdContract.CareTaskEntry.COLUMN_FREQUENCY + " ASC, " +
                        LitterboxdContract.CareTaskEntry.COLUMN_SORT_ORDER + " ASC, " +
                        LitterboxdContract.CareTaskEntry.COLUMN_CREATED_AT + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    tasks.add(cursorToTask(cursor));
                } while (cursor.moveToNext());
            }
        }
        return tasks;
    }

    public List<CareTask> getAllTasks() {
        List<CareTask> tasks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(LitterboxdContract.CareTaskEntry.TABLE_NAME,
                null, null, null, null, null,
                LitterboxdContract.CareTaskEntry.COLUMN_ID + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    tasks.add(cursorToTask(cursor));
                } while (cursor.moveToNext());
            }
        }
        return tasks;
    }

    public int countForCat(long catId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + LitterboxdContract.CareTaskEntry.TABLE_NAME +
                        " WHERE " + LitterboxdContract.CareTaskEntry.COLUMN_CAT_ID + " = ?",
                new String[]{String.valueOf(catId)})) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    public long insertLog(long taskId, long completedAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LitterboxdContract.CareTaskLogEntry.COLUMN_TASK_ID, taskId);
        values.put(LitterboxdContract.CareTaskLogEntry.COLUMN_COMPLETED_AT, completedAt);
        return db.insert(LitterboxdContract.CareTaskLogEntry.TABLE_NAME, null, values);
    }

    /** Removes the log row written when the task was completed at {@code completedAt} (used when a tick is undone). */
    public int deleteLog(long taskId, long completedAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(LitterboxdContract.CareTaskLogEntry.TABLE_NAME,
                LitterboxdContract.CareTaskLogEntry.COLUMN_TASK_ID + " = ? AND " +
                        LitterboxdContract.CareTaskLogEntry.COLUMN_COMPLETED_AT + " = ?",
                new String[]{String.valueOf(taskId), String.valueOf(completedAt)});
    }

    public List<CareTaskLog> getLogsForTask(long taskId) {
        List<CareTaskLog> logs = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(LitterboxdContract.CareTaskLogEntry.TABLE_NAME,
                null,
                LitterboxdContract.CareTaskLogEntry.COLUMN_TASK_ID + " = ?",
                new String[]{String.valueOf(taskId)},
                null, null,
                LitterboxdContract.CareTaskLogEntry.COLUMN_COMPLETED_AT + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    CareTaskLog log = new CareTaskLog();
                    log.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskLogEntry.COLUMN_ID)));
                    log.setTaskId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskLogEntry.COLUMN_TASK_ID)));
                    log.setCompletedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskLogEntry.COLUMN_COMPLETED_AT)));
                    logs.add(log);
                } while (cursor.moveToNext());
            }
        }
        return logs;
    }

    private ContentValues toValues(CareTask task) {
        ContentValues values = new ContentValues();
        values.put(LitterboxdContract.CareTaskEntry.COLUMN_CAT_ID, task.getCatId());
        values.put(LitterboxdContract.CareTaskEntry.COLUMN_NAME, task.getName());
        values.put(LitterboxdContract.CareTaskEntry.COLUMN_FREQUENCY,
                task.getFrequency() != null ? task.getFrequency().toStorage() : TaskFrequency.DAILY.toStorage());
        if (task.getReminderHour() != null) {
            values.put(LitterboxdContract.CareTaskEntry.COLUMN_REMINDER_HOUR, task.getReminderHour());
        } else {
            values.putNull(LitterboxdContract.CareTaskEntry.COLUMN_REMINDER_HOUR);
        }
        if (task.getReminderMinute() != null) {
            values.put(LitterboxdContract.CareTaskEntry.COLUMN_REMINDER_MINUTE, task.getReminderMinute());
        } else {
            values.putNull(LitterboxdContract.CareTaskEntry.COLUMN_REMINDER_MINUTE);
        }
        if (task.getLastCompletedAt() != null) {
            values.put(LitterboxdContract.CareTaskEntry.COLUMN_LAST_COMPLETED_AT, task.getLastCompletedAt());
        } else {
            values.putNull(LitterboxdContract.CareTaskEntry.COLUMN_LAST_COMPLETED_AT);
        }
        values.put(LitterboxdContract.CareTaskEntry.COLUMN_IS_DEFAULT, task.isDefault() ? 1 : 0);
        values.put(LitterboxdContract.CareTaskEntry.COLUMN_SORT_ORDER, task.getSortOrder());
        values.put(LitterboxdContract.CareTaskEntry.COLUMN_CREATED_AT,
                task.getCreatedAt() > 0 ? task.getCreatedAt() : System.currentTimeMillis());
        return values;
    }

    private CareTask cursorToTask(Cursor cursor) {
        CareTask task = new CareTask();
        task.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_ID)));
        task.setCatId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_CAT_ID)));
        task.setName(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_NAME)));
        task.setFrequency(TaskFrequency.fromStorage(
                cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_FREQUENCY))));
        int hourCol = cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_REMINDER_HOUR);
        if (!cursor.isNull(hourCol)) {
            task.setReminderHour(cursor.getInt(hourCol));
        }
        int minuteCol = cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_REMINDER_MINUTE);
        if (!cursor.isNull(minuteCol)) {
            task.setReminderMinute(cursor.getInt(minuteCol));
        }
        int completedCol = cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_LAST_COMPLETED_AT);
        if (!cursor.isNull(completedCol)) {
            task.setLastCompletedAt(cursor.getLong(completedCol));
        }
        task.setDefault(cursor.getInt(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_IS_DEFAULT)) == 1);
        task.setSortOrder(cursor.getInt(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_SORT_ORDER)));
        task.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.CareTaskEntry.COLUMN_CREATED_AT)));
        return task;
    }
}
