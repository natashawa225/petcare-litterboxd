package com.litterboxd.app.data;

import android.content.Context;

import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.FeedingScheduleDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.reminders.CareReminderScheduler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps each cat's care tasks based on what the owner actually told us about that cat.
 *
 * - Feeding reminders are generated from the cat's own feeding schedule (one per meal) and
 *   re-synced whenever the schedule is saved. See {@link FeedingTaskPlanner}.
 * - Nothing else is created automatically: no vet, grooming or medical tasks are invented.
 *   The Tasks tab suggests routines the owner can add with their own times.
 *
 * Replaces the old seeder that gave every cat the same six generic tasks.
 */
public final class CareTaskSetup {

    private CareTaskSetup() {}

    /**
     * Once per cat: removes the old generic placeholder tasks the owner never used, then
     * generates feeding reminders from the cat's schedule. Safe to call on every screen load.
     */
    public static synchronized void prepareCat(Context context, CareTaskDao dao, long catId) {
        if (catId <= 0) return;
        AppPrefs prefs = new AppPrefs(context);
        if (prefs.isCareTasksPrepared(catId)) return;
        removeUntouchedPlaceholders(context, dao, catId);
        syncFeedingTasks(context, catId);
        prefs.setCareTasksPrepared(catId);
    }

    /**
     * Makes the cat's feeding reminders match its feeding schedule: inserts, updates in place,
     * or removes reminders, and reschedules their alarms. Call after the schedule is saved.
     */
    public static synchronized void syncFeedingTasks(Context context, long catId) {
        if (catId <= 0) return;
        LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(context);
        CareTaskDao taskDao = new CareTaskDao(db);
        List<FeedingMeal> meals = new FeedingScheduleDao(db).getFeedingMealsForCat(catId);
        FeedingTaskPlanner.Plan plan = FeedingTaskPlanner.plan(catId, meals,
                taskDao.getTasksForCat(catId), meal -> describe(context, meal),
                System.currentTimeMillis());

        for (Long id : plan.toDelete) {
            CareReminderScheduler.cancel(context, id);
            taskDao.deleteTask(id);
        }
        for (CareTask task : plan.toUpdate) {
            taskDao.updateTask(task);
            CareReminderScheduler.schedule(context, task);
        }
        for (CareTask task : plan.toInsert) {
            task.setId(taskDao.insertTask(task));
            CareReminderScheduler.schedule(context, task);
        }
    }

    private static String describe(Context context, FeedingMeal meal) {
        double grams = meal.getPortionGrams();
        if (grams <= 0) return context.getString(R.string.feeding_task_description_plain);
        String amount = grams == Math.rint(grams)
                ? String.valueOf((long) grams)
                : String.format(Locale.getDefault(), "%.1f", grams);
        return context.getString(R.string.feeding_task_description, amount);
    }

    /**
     * The old seeder added the same six tasks to every cat. Removes only those the owner never
     * touched: still marked default, never ticked off, and still named exactly as seeded.
     * Anything the owner used, renamed or added themselves is kept.
     */
    private static void removeUntouchedPlaceholders(Context context, CareTaskDao dao, long catId) {
        Set<String> seededNames = new HashSet<>(Arrays.asList(
                context.getString(R.string.default_task_meals),
                context.getString(R.string.default_task_brushing),
                context.getString(R.string.default_task_poop_scoop),
                context.getString(R.string.default_task_play),
                context.getString(R.string.default_task_vet),
                context.getString(R.string.default_task_grooming)));
        for (CareTask task : dao.getTasksForCat(catId)) {
            boolean untouched = task.isDefault()
                    && task.getSource() == null
                    && task.getLastCompletedAt() == null
                    && seededNames.contains(task.getName())
                    && !dao.hasLogs(task.getId());
            if (untouched) {
                CareReminderScheduler.cancel(context, task.getId());
                dao.deleteTask(task.getId());
            }
        }
    }
}
