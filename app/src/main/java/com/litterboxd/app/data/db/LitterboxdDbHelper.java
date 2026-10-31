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
                    LitterboxdContract.CareTaskEntry.COLUMN_DESCRIPTION + " TEXT, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_FREQUENCY + " TEXT NOT NULL, " +
                    LitterboxdContract.CareTaskEntry.COLUMN_DUE_AT + " INTEGER, " +
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

    private static final String SQL_CREATE_DAILY_CHECKINS =
            "CREATE TABLE " + LitterboxdContract.DailyCheckinEntry.TABLE_NAME + " (" +
                    LitterboxdContract.DailyCheckinEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID + " INTEGER NOT NULL, " +
                    LitterboxdContract.DailyCheckinEntry.COLUMN_CHECKIN_DATE + " TEXT NOT NULL, " +
                    LitterboxdContract.DailyCheckinEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    LitterboxdContract.DailyCheckinEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "UNIQUE(" + LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID + ", " +
                    LitterboxdContract.DailyCheckinEntry.COLUMN_CHECKIN_DATE + "), " +
                    "FOREIGN KEY(" + LitterboxdContract.DailyCheckinEntry.COLUMN_CAT_ID + ") REFERENCES " +
                    LitterboxdContract.CatEntry.TABLE_NAME + "(" + LitterboxdContract.CatEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_CHECKIN_ANSWERS =
            "CREATE TABLE " + LitterboxdContract.CheckinAnswerEntry.TABLE_NAME + " (" +
                    LitterboxdContract.CheckinAnswerEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.CheckinAnswerEntry.COLUMN_CHECKIN_ID + " INTEGER NOT NULL, " +
                    LitterboxdContract.CheckinAnswerEntry.COLUMN_QUESTION_ID + " TEXT NOT NULL, " +
                    LitterboxdContract.CheckinAnswerEntry.COLUMN_ANSWER_VALUE + " TEXT NOT NULL, " +
                    "UNIQUE(" + LitterboxdContract.CheckinAnswerEntry.COLUMN_CHECKIN_ID + ", " +
                    LitterboxdContract.CheckinAnswerEntry.COLUMN_QUESTION_ID + ", " +
                    LitterboxdContract.CheckinAnswerEntry.COLUMN_ANSWER_VALUE + "), " +
                    "FOREIGN KEY(" + LitterboxdContract.CheckinAnswerEntry.COLUMN_CHECKIN_ID + ") REFERENCES " +
                    LitterboxdContract.DailyCheckinEntry.TABLE_NAME + "(" + LitterboxdContract.DailyCheckinEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    private static final String SQL_CREATE_INDEX_CHECKIN_ANSWERS_CHECKIN =
            "CREATE INDEX idx_checkin_answers_checkin_id ON " + LitterboxdContract.CheckinAnswerEntry.TABLE_NAME +
                    "(" + LitterboxdContract.CheckinAnswerEntry.COLUMN_CHECKIN_ID + ")";

    private static final String SQL_CREATE_DIARY_ENTRIES =
            "CREATE TABLE " + LitterboxdContract.DiaryEntryEntry.TABLE_NAME + " (" +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_CAT_ID + " INTEGER NOT NULL, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_TYPE + " TEXT NOT NULL, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_TITLE + " TEXT NOT NULL, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_DESCRIPTION + " TEXT, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_ENTRY_AT + " INTEGER NOT NULL, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_PHOTO_PATH + " TEXT, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_TAGS + " TEXT, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
                    "FOREIGN KEY(" + LitterboxdContract.DiaryEntryEntry.COLUMN_CAT_ID + ") REFERENCES " +
                    LitterboxdContract.CatEntry.TABLE_NAME + "(" + LitterboxdContract.CatEntry.COLUMN_ID + ") ON DELETE CASCADE)";

    /** The diary is always read per cat, newest first. */
    private static final String SQL_CREATE_INDEX_DIARY_CAT_DATE =
            "CREATE INDEX idx_diary_entries_cat_date ON " + LitterboxdContract.DiaryEntryEntry.TABLE_NAME +
                    "(" + LitterboxdContract.DiaryEntryEntry.COLUMN_CAT_ID + ", " +
                    LitterboxdContract.DiaryEntryEntry.COLUMN_ENTRY_AT + ")";

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

    /** A helper on a separately named database file, for upgrade (migration) tests only. */
    public static LitterboxdDbHelper openNamedForTests(Context context, String databaseName) {
        return new LitterboxdDbHelper(context.getApplicationContext(), databaseName);
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
        createCheckinTables(db);
        createDiaryTables(db);

        // TODO: Create future tables in subsequent updates:
        // - alerts
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // Created with the current columns, so the v5 step below must not add them again.
            createCareTaskTables(db);
        } else if (oldVersion < 5) {
            // v5: one-off tasks. ALTER keeps every existing task, log and reminder.
            db.execSQL("ALTER TABLE " + LitterboxdContract.CareTaskEntry.TABLE_NAME +
                    " ADD COLUMN " + LitterboxdContract.CareTaskEntry.COLUMN_DESCRIPTION + " TEXT");
            db.execSQL("ALTER TABLE " + LitterboxdContract.CareTaskEntry.TABLE_NAME +
                    " ADD COLUMN " + LitterboxdContract.CareTaskEntry.COLUMN_DUE_AT + " INTEGER");
        }
        if (oldVersion < 4) {
            createCheckinTables(db);
        }
        if (oldVersion < 6) {
            // v6: Cat Diary. A new table only, so all existing data is untouched.
            createDiaryTables(db);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.DiaryEntryEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.CheckinAnswerEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.DailyCheckinEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.CareTaskLogEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.CareTaskEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.FeedingScheduleEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.WeightLogEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + LitterboxdContract.CatEntry.TABLE_NAME);
        onCreate(db);
    }

    private void createCareTaskTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CARE_TASKS);
        db.execSQL(SQL_CREATE_CARE_TASK_LOGS);
        db.execSQL(SQL_CREATE_INDEX_CARE_TASKS_CAT);
        db.execSQL(SQL_CREATE_INDEX_CARE_TASK_LOGS_TASK);
    }

    private void createDiaryTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_DIARY_ENTRIES);
        db.execSQL(SQL_CREATE_INDEX_DIARY_CAT_DATE);
    }

    private void createCheckinTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_DAILY_CHECKINS);
        db.execSQL(SQL_CREATE_CHECKIN_ANSWERS);
        db.execSQL(SQL_CREATE_INDEX_CHECKIN_ANSWERS_CHECKIN);
    }
}
