package com.litterboxd.app.data;

import android.content.Context;

import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.TaskFrequency;
import com.litterboxd.app.reminders.CareReminderScheduler;

/**
 * Inserts the built-in daily/monthly care tasks the first time a cat is used.
 */
public final class CareTaskSeeder {
    private CareTaskSeeder() {}

    public static void seedIfEmpty(Context context, CareTaskDao dao, long catId) {
        if (catId <= 0 || dao.countForCat(catId) > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        insert(dao, catId, context.getString(R.string.default_task_meals), TaskFrequency.DAILY, 8, 0, 0, now);
        insert(dao, catId, context.getString(R.string.default_task_brushing), TaskFrequency.DAILY, 19, 0, 1, now);
        insert(dao, catId, context.getString(R.string.default_task_poop_scoop), TaskFrequency.DAILY, 9, 0, 2, now);
        insert(dao, catId, context.getString(R.string.default_task_play), TaskFrequency.DAILY, 18, 0, 3, now);
        insert(dao, catId, context.getString(R.string.default_task_vet), TaskFrequency.MONTHLY, 10, 0, 0, now);
        insert(dao, catId, context.getString(R.string.default_task_grooming), TaskFrequency.MONTHLY, 10, 0, 1, now);

        if (context != null) {
            for (CareTask task : dao.getTasksForCat(catId)) {
                CareReminderScheduler.schedule(context, task);
            }
        }
    }

    private static void insert(CareTaskDao dao, long catId, String name, TaskFrequency frequency,
                               int hour, int minute, int sortOrder, long createdAt) {
        CareTask task = new CareTask();
        task.setCatId(catId);
        task.setName(name);
        task.setFrequency(frequency);
        task.setReminderHour(hour);
        task.setReminderMinute(minute);
        task.setDefault(true);
        task.setSortOrder(sortOrder);
        task.setCreatedAt(createdAt);
        dao.insertTask(task);
    }
}
