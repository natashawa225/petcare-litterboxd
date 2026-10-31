package com.litterboxd.app.data.db;

public final class LitterboxdContract {
    public static final String DATABASE_NAME = "litterboxd.db";
    public static final int DATABASE_VERSION = 6;

    private LitterboxdContract() {}

    public static class CatEntry {
        public static final String TABLE_NAME = "cats";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_PHOTO_PATH = "photo_path";
        public static final String COLUMN_BIRTHDAY = "birthday";
        public static final String COLUMN_APPROX_AGE_YEARS = "approx_age_years";
        public static final String COLUMN_APPROX_AGE_MONTHS = "approx_age_months";
        public static final String COLUMN_BREED = "breed";
        public static final String COLUMN_SEX = "sex";
        public static final String COLUMN_NEUTERED = "neutered";
        public static final String COLUMN_WEIGHT = "weight";
        public static final String COLUMN_COAT_COLOR = "coat_color";
        public static final String COLUMN_LIFESTYLE = "lifestyle";
        public static final String COLUMN_CREATED_AT = "created_at";
    }

    public static class WeightLogEntry {
        public static final String TABLE_NAME = "weight_logs";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_CAT_ID = "cat_id";
        public static final String COLUMN_WEIGHT = "weight";
        public static final String COLUMN_DATE = "date";
        public static final String COLUMN_NOTES = "notes";
    }

    public static class FeedingScheduleEntry {
        public static final String TABLE_NAME = "feeding_schedule";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_CAT_ID = "cat_id";
        public static final String COLUMN_MEAL_LABEL = "meal_label";
        public static final String COLUMN_MEAL_TIME = "meal_time";
        public static final String COLUMN_PORTION_GRAMS = "portion_grams";
    }

    public static class CareTaskEntry {
        public static final String TABLE_NAME = "care_tasks";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_CAT_ID = "cat_id";
        public static final String COLUMN_NAME = "name";
        /** Optional short note shown under the task title (added in DB v5). */
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_FREQUENCY = "frequency";
        /** One-off tasks only: due date/time in millis, null if no due date (added in DB v5). */
        public static final String COLUMN_DUE_AT = "due_at";
        public static final String COLUMN_REMINDER_HOUR = "reminder_hour";
        public static final String COLUMN_REMINDER_MINUTE = "reminder_minute";
        public static final String COLUMN_LAST_COMPLETED_AT = "last_completed_at";
        public static final String COLUMN_IS_DEFAULT = "is_default";
        public static final String COLUMN_SORT_ORDER = "sort_order";
        public static final String COLUMN_CREATED_AT = "created_at";
    }

    public static class CareTaskLogEntry {
        public static final String TABLE_NAME = "care_task_logs";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_TASK_ID = "task_id";
        public static final String COLUMN_COMPLETED_AT = "completed_at";
    }

    /** One daily check-in per cat per day. COLUMN_CHECKIN_DATE is an ISO day such as 2026-10-27. */
    public static class DailyCheckinEntry {
        public static final String TABLE_NAME = "daily_checkins";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_CAT_ID = "cat_id";
        public static final String COLUMN_CHECKIN_DATE = "checkin_date";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
    }

    /**
     * Cat Diary (added in DB v6): memories and milestones the owner writes. Care history shown in
     * the diary (weights, check-ins) is read from its own tables, never copied here.
     */
    public static class DiaryEntryEntry {
        public static final String TABLE_NAME = "diary_entries";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_CAT_ID = "cat_id";
        /** "MEMORY" or "MILESTONE" (DiaryEntry.Type). */
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_DESCRIPTION = "description";
        /** When the moment happened (millis); a milestone may be long before the app was installed. */
        public static final String COLUMN_ENTRY_AT = "entry_at";
        /** Private file name in internal storage (diary_photo_*), or null. */
        public static final String COLUMN_PHOTO_PATH = "photo_path";
        /** Comma-separated stable tag keys, e.g. "play,first_time", or null. */
        public static final String COLUMN_TAGS = "tags";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
    }

    /** One row per selected answer: a multi-select question has several rows for the same question. */
    public static class CheckinAnswerEntry {
        public static final String TABLE_NAME = "checkin_answers";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_CHECKIN_ID = "checkin_id";
        public static final String COLUMN_QUESTION_ID = "question_id";
        public static final String COLUMN_ANSWER_VALUE = "answer_value";
    }
}
