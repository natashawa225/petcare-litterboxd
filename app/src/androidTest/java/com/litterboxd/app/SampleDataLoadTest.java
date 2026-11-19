package com.litterboxd.app;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.litterboxd.app.data.SampleData;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.WeightLog;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SampleDataLoadTest {

    @Test
    public void loadSampleDataIntoApp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(context);

        // Load sample data
        int added = SampleData.load(context);

        CatDao catDao = new CatDao(db);
        List<Cat> cats = catDao.getAllCats();
        assertTrue("Cats should be seeded", cats.size() >= 3);

        Cat biscuit = cats.stream().filter(c -> "Biscuit".equals(c.getName())).findFirst().orElse(null);
        Cat pip = cats.stream().filter(c -> "Pip".equals(c.getName())).findFirst().orElse(null);
        Cat mochi = cats.stream().filter(c -> "Mochi".equals(c.getName())).findFirst().orElse(null);

        assertNotNull("Biscuit should be present", biscuit);
        assertNotNull("Pip should be present", pip);
        assertNotNull("Mochi should be present", mochi);

        // Check Biscuit
        DailyCheckinDao checkinDao = new DailyCheckinDao(db);
        WeightLogDao weightLogDao = new WeightLogDao(db);

        Map<String, Map<String, List<String>>> biscuitAnswers = checkinDao.getAnswersByDateForCat(biscuit.getId());
        assertTrue("Biscuit should have ~36 logged days", biscuitAnswers.size() >= 30);
        List<WeightLog> biscuitWeights = weightLogDao.getWeightLogsForCat(biscuit.getId());
        assertEquals("Biscuit should have 7 weight points", 7, biscuitWeights.size());

        // Check Pip
        Map<String, Map<String, List<String>>> pipAnswers = checkinDao.getAnswersByDateForCat(pip.getId());
        assertTrue("Pip should have ~29 logged days", pipAnswers.size() >= 25);
        List<WeightLog> pipWeights = weightLogDao.getWeightLogsForCat(pip.getId());
        assertEquals("Pip should have 6 weight points", 6, pipWeights.size());

        // Check Mochi
        Map<String, Map<String, List<String>>> mochiAnswers = checkinDao.getAnswersByDateForCat(mochi.getId());
        assertEquals("Mochi should have 7 logged days", 7, mochiAnswers.size());
        List<WeightLog> mochiWeights = weightLogDao.getWeightLogsForCat(mochi.getId());
        assertEquals("Mochi should have 4 weight points", 4, mochiWeights.size());
    }
}
