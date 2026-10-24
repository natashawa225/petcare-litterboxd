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
        // In-memory database: the tests never touch the app's real data.
        dbHelper = LitterboxdDbHelper.createInMemoryForTests(context);
        catDao = new CatDao(dbHelper);
        weightLogDao = new WeightLogDao(dbHelper);
        feedingScheduleDao = new FeedingScheduleDao(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    private Cat newCat(String name) {
        Cat cat = new Cat();
        cat.setName(name);
        cat.setBreed("Domestic Shorthair");
        cat.setSex("Female");
        cat.setNeutered(true);
        cat.setWeight(4.2);
        cat.setCoatColor("Calico");
        cat.setLifestyle("Indoor only");
        cat.setCreatedAt(System.currentTimeMillis());
        return cat;
    }

    @Test
    public void testInsertAndGetCat() {
        long catId = catDao.insertCat(newCat("Mochi"));
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

    @Test
    public void testDeletingCatCascadesToWeightLogsAndMeals() {
        long catId = catDao.insertCat(newCat("Biscuit"));
        long otherId = catDao.insertCat(newCat("Mochi"));

        WeightLog log = new WeightLog();
        log.setCatId(catId);
        log.setWeight(6.7);
        log.setDate(System.currentTimeMillis());
        weightLogDao.insertWeightLog(log);

        FeedingMeal meal = new FeedingMeal();
        meal.setCatId(catId);
        meal.setMealLabel("Breakfast");
        meal.setMealTime("08:00");
        meal.setPortionGrams(50);
        feedingScheduleDao.insertFeedingMeal(meal);

        FeedingMeal otherMeal = new FeedingMeal();
        otherMeal.setCatId(otherId);
        otherMeal.setMealLabel("Dinner");
        otherMeal.setMealTime("18:00");
        otherMeal.setPortionGrams(45);
        feedingScheduleDao.insertFeedingMeal(otherMeal);

        assertEquals(1, weightLogDao.getWeightLogsForCat(catId).size());
        assertEquals(1, feedingScheduleDao.getFeedingMealsForCat(catId).size());

        catDao.deleteCat(catId);

        // ON DELETE CASCADE only works because onConfigure enables foreign keys.
        assertTrue(weightLogDao.getWeightLogsForCat(catId).isEmpty());
        assertTrue(feedingScheduleDao.getFeedingMealsForCat(catId).isEmpty());
        assertEquals(1, feedingScheduleDao.getFeedingMealsForCat(otherId).size());
    }
}
