package com.litterboxd.app;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.TaskFrequency;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CareTaskDaoTest {
    private LitterboxdDbHelper dbHelper;
    private CatDao catDao;
    private CareTaskDao careTaskDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        dbHelper = LitterboxdDbHelper.createInMemoryForTests(context);
        catDao = new CatDao(dbHelper);
        careTaskDao = new CareTaskDao(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    @Test
    public void insertUpdateCompleteAndDeleteTask() {
        long catId = catDao.insertCat(newCat("Mochi"));
        CareTask task = newTask(catId, "Meals", TaskFrequency.DAILY);
        long taskId = careTaskDao.insertTask(task);
        assertTrue(taskId > 0);

        CareTask fetched = careTaskDao.getTaskById(taskId);
        assertNotNull(fetched);
        assertEquals("Meals", fetched.getName());
        assertEquals(Integer.valueOf(8), fetched.getReminderHour());
        assertFalse(fetched.isCompleteNow(System.currentTimeMillis()));

        long doneAt = System.currentTimeMillis();
        fetched.setLastCompletedAt(doneAt);
        careTaskDao.updateTask(fetched);
        careTaskDao.insertLog(taskId, doneAt);
        CareTask completed = careTaskDao.getTaskById(taskId);
        assertTrue(completed.isCompleteNow(doneAt));
        assertEquals(1, careTaskDao.getLogsForTask(taskId).size());

        careTaskDao.deleteTask(taskId);
        assertNull(careTaskDao.getTaskById(taskId));
        assertTrue(careTaskDao.getLogsForTask(taskId).isEmpty());
    }

    @Test
    public void oneOffTaskKeepsDescriptionAndDueDate() {
        long catId = catDao.insertCat(newCat("Mochi"));
        CareTask task = newTask(catId, "Book the vet", TaskFrequency.ONCE);
        long due = System.currentTimeMillis() + 86_400_000L;
        task.setDescription("Annual check-up");
        task.setDueAt(due);
        long taskId = careTaskDao.insertTask(task);

        CareTask fetched = careTaskDao.getTaskById(taskId);
        assertEquals(TaskFrequency.ONCE, fetched.getFrequency());
        assertEquals("Annual check-up", fetched.getDescription());
        assertEquals(Long.valueOf(due), fetched.getDueAt());

        fetched.setDescription(null);
        fetched.setDueAt(null);
        careTaskDao.updateTask(fetched);
        CareTask cleared = careTaskDao.getTaskById(taskId);
        assertNull(cleared.getDescription());
        assertNull(cleared.getDueAt());
    }

    @Test
    public void undoingACompletionRemovesOnlyItsLog() {
        long catId = catDao.insertCat(newCat("Pudding"));
        long taskId = careTaskDao.insertTask(newTask(catId, "Meals", TaskFrequency.DAILY));
        long yesterday = System.currentTimeMillis() - 86_400_000L;
        long today = System.currentTimeMillis();
        careTaskDao.insertLog(taskId, yesterday);
        careTaskDao.insertLog(taskId, today);

        assertEquals(1, careTaskDao.deleteLog(taskId, today));
        assertEquals(1, careTaskDao.getLogsForTask(taskId).size());
        assertEquals(yesterday, careTaskDao.getLogsForTask(taskId).get(0).getCompletedAt());
    }

    @Test
    public void deletingCatCascadesCareTasks() {
        long catId = catDao.insertCat(newCat("Biscuit"));
        careTaskDao.insertTask(newTask(catId, "Poop scoop", TaskFrequency.DAILY));
        assertEquals(1, careTaskDao.countForCat(catId));

        catDao.deleteCat(catId);
        assertTrue(careTaskDao.getTasksForCat(catId).isEmpty());
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

    private CareTask newTask(long catId, String name, TaskFrequency frequency) {
        CareTask task = new CareTask();
        task.setCatId(catId);
        task.setName(name);
        task.setFrequency(frequency);
        task.setReminderHour(8);
        task.setReminderMinute(0);
        task.setDefault(true);
        task.setSortOrder(0);
        task.setCreatedAt(System.currentTimeMillis());
        return task;
    }
}
