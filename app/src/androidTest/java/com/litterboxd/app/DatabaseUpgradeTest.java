package com.litterboxd.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.DiaryEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Builds a version-5 database file (the schema before the Cat Diary) containing user data, then
 * opens it with the current helper so onUpgrade runs. Uses its own file, never litterboxd.db.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseUpgradeTest {
    private static final String DB_NAME = "upgrade_test.db";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(DB_NAME);
    }

    @Test
    public void upgradeFromV5KeepsDataAndAddsDiary() {
        File file = context.getDatabasePath(DB_NAME);
        file.getParentFile().mkdirs();
        try (SQLiteDatabase v5 = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            v5.execSQL("CREATE TABLE cats (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, photo_path TEXT, "
                    + "birthday INTEGER, approx_age_years INTEGER, approx_age_months INTEGER, breed TEXT, sex TEXT, "
                    + "neutered INTEGER, weight REAL, coat_color TEXT, lifestyle TEXT, created_at INTEGER NOT NULL)");
            v5.execSQL("CREATE TABLE care_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, cat_id INTEGER NOT NULL, "
                    + "name TEXT NOT NULL, description TEXT, frequency TEXT NOT NULL, due_at INTEGER, reminder_hour INTEGER, "
                    + "reminder_minute INTEGER, last_completed_at INTEGER, is_default INTEGER NOT NULL DEFAULT 0, "
                    + "sort_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, "
                    + "FOREIGN KEY(cat_id) REFERENCES cats(id) ON DELETE CASCADE)");
            v5.execSQL("INSERT INTO cats(name, created_at) VALUES('Mochi', 1)");
            v5.execSQL("INSERT INTO care_tasks(cat_id, name, frequency, created_at) VALUES(1, 'Meals', 'DAILY', 1)");
            v5.setVersion(5);
        }

        LitterboxdDbHelper helper = LitterboxdDbHelper.openNamedForTests(context, DB_NAME);
        try {
            SQLiteDatabase db = helper.getWritableDatabase(); // runs onUpgrade(5 -> 6)
            assertEquals(6, db.getVersion());
            assertEquals("Mochi", new CatDao(helper).getCatById(1).getName());
            assertEquals(1, new CareTaskDao(helper).countForCat(1));
            try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='diary_entries'", null)) {
                assertTrue("diary_entries should exist after the upgrade", c.moveToFirst());
            }
            DiaryEntry entry = new DiaryEntry();
            entry.setCatId(1);
            entry.setTitle("First day home");
            entry.setType(DiaryEntry.Type.MILESTONE);
            entry.setEntryAt(1);
            assertTrue(new DiaryDao(helper).insertEntry(entry) > 0);
        } finally {
            helper.close();
        }
    }
}
