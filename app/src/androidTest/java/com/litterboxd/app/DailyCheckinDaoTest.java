package com.litterboxd.app;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DailyCheckin;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DailyCheckinDaoTest {
    private static final String DAY_1 = "2026-10-27";
    private static final String DAY_2 = "2026-10-28";

    private LitterboxdDbHelper dbHelper;
    private CatDao catDao;
    private DailyCheckinDao checkinDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        dbHelper = LitterboxdDbHelper.createInMemoryForTests(context);
        catDao = new CatDao(dbHelper);
        checkinDao = new DailyCheckinDao(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    @Test
    public void saveThenReadBackKeepsEveryAnswerInOrder() {
        long catId = catDao.insertCat(newCat("Mochi"));
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("appetite", Collections.singletonList("less"));
        answers.put("symptoms", Arrays.asList("vomiting", "hairball")); // multi-select: two rows
        answers.put("vomiting", Collections.singletonList("once"));

        long id = checkinDao.saveCheckin(catId, DAY_1, answers);
        assertTrue(id > 0);

        DailyCheckin fetched = checkinDao.getCheckin(catId, DAY_1);
        assertNotNull(fetched);
        assertEquals(id, fetched.getId());
        assertEquals(catId, fetched.getCatId());
        assertEquals(DAY_1, fetched.getCheckinDate());
        assertEquals(answers, fetched.getAnswers());
        assertEquals(Arrays.asList("appetite", "symptoms", "vomiting"),
                Arrays.asList(fetched.getAnswers().keySet().toArray()));
    }

    @Test
    public void savingTheSameDayAgainReplacesAnswersInsteadOfDuplicating() {
        long catId = catDao.insertCat(newCat("Mochi"));
        Map<String, List<String>> first = new LinkedHashMap<>();
        first.put("appetite", Collections.singletonList("less"));
        first.put("hiding", Collections.singletonList("yes"));
        long firstId = checkinDao.saveCheckin(catId, DAY_1, first);

        Map<String, List<String>> second = new LinkedHashMap<>();
        second.put("appetite", Collections.singletonList("normal"));
        long secondId = checkinDao.saveCheckin(catId, DAY_1, second);

        assertEquals("same row is reused", firstId, secondId);
        DailyCheckin fetched = checkinDao.getCheckin(catId, DAY_1);
        assertEquals(second, fetched.getAnswers());
        assertFalse(fetched.getAnswers().containsKey("hiding"));
        assertTrue(fetched.getUpdatedAt() >= fetched.getCreatedAt());
    }

    @Test
    public void differentDaysAndDifferentCatsAreKeptSeparate() {
        long mochi = catDao.insertCat(newCat("Mochi"));
        long biscuit = catDao.insertCat(newCat("Biscuit"));
        checkinDao.saveCheckin(mochi, DAY_1, single("appetite", "less"));
        checkinDao.saveCheckin(mochi, DAY_2, single("appetite", "more"));
        checkinDao.saveCheckin(biscuit, DAY_1, single("appetite", "normal"));

        assertEquals("less", checkinDao.getCheckin(mochi, DAY_1).getAnswers().get("appetite").get(0));
        assertEquals("more", checkinDao.getCheckin(mochi, DAY_2).getAnswers().get("appetite").get(0));
        assertEquals("normal", checkinDao.getCheckin(biscuit, DAY_1).getAnswers().get("appetite").get(0));
        assertNull(checkinDao.getCheckin(biscuit, DAY_2));
    }

    @Test
    public void readingAMissingCheckinReturnsNull() {
        long catId = catDao.insertCat(newCat("Mochi"));
        assertNull(checkinDao.getCheckin(catId, DAY_1));
    }

    @Test
    public void deletingCatCascadesToItsCheckinsAndAnswers() {
        long catId = catDao.insertCat(newCat("Biscuit"));
        checkinDao.saveCheckin(catId, DAY_1, single("appetite", "less"));
        assertNotNull(checkinDao.getCheckin(catId, DAY_1));

        catDao.deleteCat(catId);
        assertNull(checkinDao.getCheckin(catId, DAY_1));
    }

    private Map<String, List<String>> single(String questionId, String value) {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put(questionId, Collections.singletonList(value));
        return answers;
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
}
