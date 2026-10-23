package com.litterboxd.app;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.dao.FeedingScheduleDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.WeightLog;
import com.litterboxd.app.data.model.FeedingMeal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CatDaoTest {
    private LitterboxdDbHelper dbHelper;
    private CatDao catDao;
    private WeightLogDao weightLogDao;
    private FeedingScheduleDao feedingScheduleDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        dbHelper = new LitterboxdDbHelper(context);
        catDao = new CatDao(dbHelper);
        weightLogDao = new WeightLogDao(dbHelper);
        feedingScheduleDao = new FeedingScheduleDao(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    @Test
    public void testInsertAndGetCat() {
        Cat cat = new Cat();
        cat.setName("Mochi");
        cat.setBreed("Domestic Shorthair");
        cat.setSex("Female");
        cat.setNeutered(true);
        cat.setWeight(4.2);
        cat.setCoatColor("Calico");
        cat.setLifestyle("Indoor only");
        cat.setCreatedAt(System.currentTimeMillis());

        long catId = catDao.insertCat(cat);
        assertTrue(catId > 0);

        Cat fetched = catDao.getCatById(catId);
        assertNotNull(fetched);
        assertEquals("Mochi", fetched.getName());
        assertEquals(4.2, fetched.getWeight(), 0.01);

        List<Cat> cats = catDao.getAllCats();
        assertFalse(cats.isEmpty());

        catDao.deleteCat(catId);
        assertNull(catDao.getCatById(catId));
    }
}
