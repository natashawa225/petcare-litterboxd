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

    private static final String SQL_CREATE_CARE_TASKS =
            "CREATE TABLE " + LitterboxdContract.CareTaskEntry.TABLE_NAME + " (" +
                    LitterboxdContract.CareTaskEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_CAT_ID + " INTEGER NOT NULL, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_NAME + " TEXT NOT NULL, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_FREQUENCY + " TEXT NOT NULL, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_REMINDER_HOUR + " INTEGER, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_REMINDER_MINUTE + " INTEGER, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_LAST_COMPLETED_AT + " INTEGER, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_IS_DEFAULT + " INTEGER NOT NULL DEFAULT 0, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_SORT_ORDER + " INTEGER NOT NULL DEFAULT 0, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + LitterboxdContract.CareTaskEntry.COLUMN_CAT_ID + ") REFERENCES " +
                    LitterboxdContract.CatEntry.TABLE_NAME + "(" + LitterboxdContract.CatEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_CARE_TASK_LOGS =
            "CREATE TABLE " + LitterboxdContract.CareTaskLogEntry.TABLE_NAME + " (" +
                    LitterboxdContract.CareTaskLogEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.CareTaskLogEntry.COLUMN_TASK_ID + " INTEGER NOT NULL, " +
                    LitterboxdContract.CareTaskLogEntry.COLUMN_COMPLETED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + LitterboxdContract.CareTaskLogEntry.COLUMN_TASK_ID + ") REFERENCES " +
                    LitterboxdContract.CareTaskEntry.TABLE_NAME + "(" + LitterboxdContract.CareTaskEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_INDEX_CARE_TASKS_CAT =
            "CREATE INDEX idx_care_tasks_cat_id ON " + LitterboxdContract.CareTaskEntry.TABLE_NAME +
                    "(" + LitterboxdContract.CareTaskEntry.COLUMN_CAT_ID + ")";

    private static final String SQL_CREATE_INDEX_CARE_TASK_LOGS_TASK =
            "CREATE INDEX idx_care_task_logs_task_id ON " + LitterboxdContract.CareTaskLogEntry.TABLE_NAME +
                    "(" + LitterboxdContract.CareTaskLogEntry.COLUMN_TASK_ID + ")";

    private static LitterboxdDbHelper instance;

    /**
     * One shared helper for the whole app, so every Activity and worker thread uses the same
     * database connection (separate helpers can hit "database is locked").
     */
    public static synchronized LitterboxdDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new LitterboxdDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    /** In-memory database (nothing written to disk), for instrumented tests only. */
    public static LitterboxdDbHelper createInMemoryForTests(Context context) {
        return new LitterboxdDbHelper(context.getApplicationContext(), null);
    }

    private LitterboxdDbHelper(Context context) {
        this(context, LitterboxdContract.DATABASE_NAME);
    }

    private LitterboxdDbHelper(Context context, String databaseName) {
        super(context, databaseName, null, LitterboxdContract.DATABASE_VERSION);
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
        createCareTaskTables(db);

        // TODO: Create future tables in subsequent updates:
        // - daily_checkins
        // - checkin_answers
        // - alerts
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            createCareTaskTables(db);
        }
    }

    private void createCareTaskTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CARE_TASKS);
        db.execSQL(SQL_CREATE_CARE_TASK_LOGS);
        db.execSQL(SQL_CREATE_INDEX_CARE_TASKS_CAT);
        db.execSQL(SQL_CREATE_INDEX_CARE_TASK_LOGS_TASK);
    }
}
