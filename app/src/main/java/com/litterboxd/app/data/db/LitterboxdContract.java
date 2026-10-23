package com.litterboxd.app.data.db;

public final class LitterboxdContract {
    public static final String DATABASE_NAME = "litterboxd.db";
    public static final int DATABASE_VERSION = 2;

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
}
