package com.litterboxd.app.data.db;

/**
 * Contract class defining database name, version, table names, and column constants.
 */
public final class LitterboxdContract {
    public static final String DATABASE_NAME = "litterboxd.db";
    public static final int DATABASE_VERSION = 1;

    private LitterboxdContract() {}

    public static class UserEntry {
        public static final String TABLE_NAME = "users";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_EMAIL = "email";
        public static final String COLUMN_PASSWORD_HASH = "password_hash";
        public static final String COLUMN_SALT = "salt";
        public static final String COLUMN_CREATED_AT = "created_at";
    }
}
