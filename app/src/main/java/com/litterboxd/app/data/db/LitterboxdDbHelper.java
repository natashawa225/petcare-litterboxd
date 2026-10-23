package com.litterboxd.app.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LitterboxdDbHelper extends SQLiteOpenHelper {

    private static final String SQL_CREATE_CATS =
            "CREATE TABLE " + LitterboxdContract.CatEntry.TABLE_NAME + " (" +
                    LitterboxdContract.CatEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.CatEntry.COLUMN_NAME + " TEXT NOT NULL, " +
                    LitterboxdContract.CatEntry.COLUMN_PHOTO_PATH + " TEXT, " +
                    LitterboxdContract.CatEntry.COLUMN_BIRTHDAY + " INTEGER, " +
                    LitterboxdContract.CatEntry.COLUMN_APPROX_AGE_YEARS + " INTEGER, " +
                    LitterboxdContract.CatEntry.COLUMN_APPROX_AGE_MONTHS + " INTEGER, " +
                    LitterboxdContract.CatEntry.COLUMN_BREED + " TEXT, " +
                    LitterboxdContract.CatEntry.COLUMN_SEX + " TEXT, " +
                    LitterboxdContract.CatEntry.COLUMN_NEUTERED + " INTEGER, " +
                    LitterboxdContract.CatEntry.COLUMN_WEIGHT + " REAL, " +
                    LitterboxdContract.CatEntry.COLUMN_COAT_COLOR + " TEXT, " +
                    LitterboxdContract.CatEntry.COLUMN_LIFESTYLE + " TEXT, " +
                    LitterboxdContract.CatEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL)";

    private static final String SQL_CREATE_WEIGHT_LOGS =
            "CREATE TABLE " + LitterboxdContract.WeightLogEntry.TABLE_NAME + " (" +
                    LitterboxdContract.WeightLogEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.WeightLogEntry.COLUMN_CAT_ID + " INTEGER NOT NULL, " +
                    LitterboxdContract.WeightLogEntry.COLUMN_WEIGHT + " REAL NOT NULL, " +
                    LitterboxdContract.WeightLogEntry.COLUMN_DATE + " INTEGER NOT NULL, " +
                    LitterboxdContract.WeightLogEntry.COLUMN_NOTES + " TEXT, " +
                    "FOREIGN KEY(" + LitterboxdContract.WeightLogEntry.COLUMN_CAT_ID + ") REFERENCES " +
                    LitterboxdContract.CatEntry.TABLE_NAME + "(" + LitterboxdContract.CatEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_FEEDING_SCHEDULE =
            "CREATE TABLE " + LitterboxdContract.FeedingScheduleEntry.TABLE_NAME + " (" +
                    LitterboxdContract.FeedingScheduleEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.FeedingScheduleEntry.COLUMN_CAT_ID + " INTEGER NOT NULL, " +
                    LitterboxdContract.FeedingScheduleEntry.COLUMN_MEAL_LABEL + " TEXT, " +
                    LitterboxdContract.FeedingScheduleEntry.COLUMN_MEAL_TIME + " TEXT, " +
                    LitterboxdContract.FeedingScheduleEntry.COLUMN_PORTION_GRAMS + " REAL, " +
                    "FOREIGN KEY(" + LitterboxdContract.FeedingScheduleEntry.COLUMN_CAT_ID + ") REFERENCES " +
                    LitterboxdContract.CatEntry.TABLE_NAME + "(" + LitterboxdContract.CatEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_INDEX_WEIGHT_CAT =
            "CREATE INDEX idx_weight_logs_cat_id ON " + LitterboxdContract.WeightLogEntry.TABLE_NAME +
                    "(" + LitterboxdContract.WeightLogEntry.COLUMN_CAT_ID + ")";

    private static final String SQL_CREATE_INDEX_FEEDING_CAT =
            "CREATE INDEX idx_feeding_schedule_cat_id ON " + LitterboxdContract.FeedingScheduleEntry.TABLE_NAME +
                    "(" + LitterboxdContract.FeedingScheduleEntry.COLUMN_CAT_ID + ")";

    public LitterboxdDbHelper(Context context) {
        super(context, LitterboxdContract.DATABASE_NAME, null, LitterboxdContract.DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CATS);
        db.execSQL(SQL_CREATE_WEIGHT_LOGS);
        db.execSQL(SQL_CREATE_FEEDING_SCHEDULE);
        db.execSQL(SQL_CREATE_INDEX_WEIGHT_CAT);
        db.execSQL(SQL_CREATE_INDEX_FEEDING_CAT);

        // TODO: Create future tables in subsequent updates:
        // - daily_checkins
        // - checkin_answers
        // - care_tasks
        // - care_task_logs
        // - alerts
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: Implement proper migration strategy for production instead of dropping tables.
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.FeedingScheduleEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.WeightLogEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.CatEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS users");
        onCreate(db);
    }
}
