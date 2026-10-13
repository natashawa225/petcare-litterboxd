package com.litterboxd.app;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.litterboxd.app.data.dao.UserDao;
import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.User;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UserDaoTest {
    private Context context;
    private LitterboxdDbHelper dbHelper;
    private UserDao userDao;
    private static final String TEST_DB_NAME = "test_litterboxd.db";

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DB_NAME);
        dbHelper = new LitterboxdDbHelper(context);
        userDao = new UserDao(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
        context.deleteDatabase(TEST_DB_NAME);
    }

    @Test
    public void testInsertAndGetUser() {
        String email = "catlover@example.com";
        String hash = "dummyHash123";
        String salt = "dummySalt123";
        long now = System.currentTimeMillis();

        long rowId = userDao.insertUser(email, hash, salt, now);
        assertNotEquals(-1, rowId);

        User user = userDao.getUserByEmail(email);
        assertNotNull(user);
        assertEquals(email, user.getEmail());
        assertEquals(hash, user.getPasswordHash());
        assertEquals(salt, user.getSalt());

        User userById = userDao.getUserById(user.getId());
        assertNotNull(userById);
        assertEquals(user.getId(), userById.getId());
    }

    @Test
    public void testDuplicateEmailConstraint() {
        String email = "duplicate@example.com";
        long rowId1 = userDao.insertUser(email, "hash1", "salt1", System.currentTimeMillis());
        assertNotEquals(-1, rowId1);

        // Inserting same email should fail due to UNIQUE constraint
        long rowId2 = userDao.insertUser(email, "hash2", "salt2", System.currentTimeMillis());
        assertEquals(-1, rowId2);
    }
}
