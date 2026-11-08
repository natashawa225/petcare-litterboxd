package com.litterboxd.app.data;

import android.content.Context;

import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.dao.FeedingScheduleDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.model.TaskFrequency;
import com.litterboxd.app.data.model.WeightLog;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.reminders.CareReminderScheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fictional demo cats that show the data really is per cat. Each cat differs in every table:
 *
 *  - Mochi  (adult, indoor): two meals, litter scoop done today, a booked booster vaccination
 *           coming up, a calm week of check-ins -> "All looks normal".
 *  - Biscuit (Maine Coon, indoor/outdoor): two different meal times plus a full care routine
 *           (litter, play, brushing, monthly vet and grooming; some done, some to do), weight
 *           drifting down, drinking more for 5 days -> a "Worth watching" alert.
 *  - Pip    (kitten): three small meals and nothing else yet -> only feeding reminders, plus the
 *           Tasks tab's suggestions; no check-ins -> "No check-ins yet".
 *
 * Feeding reminders are not written here: they are generated from each cat's schedule by
 * CareTaskSetup, exactly as for a real cat. Cats already present (by name) are skipped.
 */
public final class SampleData {

    private static final long DAY = 86_400_000L;

    private SampleData() {}

    /** Background thread. Returns how many sample cats were added (0 if all were already there). */
    public static int load(Context context) {
        LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(context);
        CatDao catDao = new CatDao(db);
        Set<String> existing = new HashSet<>();
        for (Cat c : catDao.getAllCats()) existing.add(c.getName());

        AppPrefs prefs = new AppPrefs(context);
        long now = System.currentTimeMillis();
        long firstAdded = -1L;
        int added = 0;

        if (!existing.contains("Mochi")) {
            long id = addMochi(context, db, now);
            if (firstAdded < 0) firstAdded = id;
            added++;
        }
        if (!existing.contains("Biscuit")) {
            long id = addBiscuit(context, db, now);
            if (firstAdded < 0) firstAdded = id;
            added++;
        }
        if (!existing.contains("Pip")) {
            long id = addPip(context, db, now);
            if (firstAdded < 0) firstAdded = id;
            added++;
        }

        if (firstAdded > 0 && prefs.getSelectedCatId() == -1L) prefs.setSelectedCatId(firstAdded);
        if (added > 0) prefs.setOnboardingComplete(true);
        return added;
    }

    // ---- The cats ----

    private static long addMochi(Context context, LitterboxdDbHelper db, long now) {
        long id = insertCat(db, "Mochi", 2, 4, "Domestic Shorthair", "Female", true,
                "Calico", "Indoor only", now - 120 * DAY);
        weights(db, id, new double[]{4.0, 4.1}, new int[]{45, 15}, "Vet weigh-in", "Home scales");
        meals(db, id, new String[][]{{"Breakfast", "08:00", "45"}, {"Dinner", "18:00", "45"}});
        finishTasks(context, id);

        CareTaskDao tasks = new CareTaskDao(db);
        long litter = ownerTask(context, tasks, id, "Litter scoop", null, TaskFrequency.DAILY, 9, 0, null, now);
        complete(tasks, litter, now - 2 * 3_600_000L); // done earlier today
        long due = LocalDate.now().plusDays(12).atTime(10, 30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        ownerTask(context, tasks, id, "Booster vaccination", "Booked at Willow Lane Vets (sample)",
                TaskFrequency.ONCE, 10, 30, due, now);

        // A calm week: everything normal on 6 of the last 7 days.
        DailyCheckinDao checkins = new DailyCheckinDao(db);
        for (int i = 0; i < 6; i++) {
            checkins.saveCheckin(id, LocalDate.now().minusDays(i + 1).toString(),
                    answers("appetite", "normal", "water", "normal", "litter", "normal"));
        }
        return id;
    }

    private static long addBiscuit(Context context, LitterboxdDbHelper db, long now) {
        long id = insertCat(db, "Biscuit", 5, 0, "Maine Coon", "Male", true,
                "Ginger", "Indoor / Outdoor", now - 300 * DAY);
        weights(db, id, new double[]{6.9, 6.7, 6.5}, new int[]{40, 20, 2},
                "Vet weigh-in", "Home scales", "Home scales");
        meals(db, id, new String[][]{{"Morning feed", "07:30", "70"}, {"Evening feed", "19:00", "60"}});
        finishTasks(context, id);

        // Biscuit is the established cat with a full care routine (the classic set of routines,
        // minus a generic "Meals" task: his feeding reminders above already cover meals).
        // Saved as the owner's own tasks, so the placeholder tidy-up never removes them.
        // Statuses are mixed so the demo shows both "To do" and "Done".
        CareTaskDao tasks = new CareTaskDao(db);
        long scoop = ownerTask(context, tasks, id, context.getString(R.string.default_task_poop_scoop),
                null, TaskFrequency.DAILY, 9, 0, null, now);
        complete(tasks, scoop, now - 2 * 3_600_000L); // done earlier today
        ownerTask(context, tasks, id, context.getString(R.string.default_task_play),
                null, TaskFrequency.DAILY, 18, 0, null, now);
        ownerTask(context, tasks, id, context.getString(R.string.default_task_brushing),
                null, TaskFrequency.DAILY, 19, 0, null, now);
        long vet = ownerTask(context, tasks, id, context.getString(R.string.default_task_vet),
                "Annual check-up booked (sample)", TaskFrequency.MONTHLY, 10, 0, null, now);
        int dayOfMonth = LocalDate.now().getDayOfMonth();
        complete(tasks, vet, now - Math.min(3, dayOfMonth - 1) * DAY); // done this month
        ownerTask(context, tasks, id, context.getString(R.string.default_task_grooming),
                null, TaskFrequency.MONTHLY, 10, 0, null, now);

        // Drinking more on each of the last 5 days -> the "water rising" watch alert.
        DailyCheckinDao checkins = new DailyCheckinDao(db);
        for (int i = 0; i < 5; i++) {
            checkins.saveCheckin(id, LocalDate.now().minusDays(i).toString(),
                    answers("appetite", "normal", "water", "more", "urination_more", "no", "litter", "normal"));
        }
        return id;
    }

    private static long addPip(Context context, LitterboxdDbHelper db, long now) {
        long id = insertCat(db, "Pip", 0, 5, "Domestic Shorthair", "Female", false,
                "Brown tabby", "Indoor only", now - 20 * DAY);
        weights(db, id, new double[]{2.1}, new int[]{6}, "First weigh-in");
        meals(db, id, new String[][]{{"Breakfast", "07:00", "25"}, {"Lunch", "12:30", "25"},
                {"Supper", "18:30", "25"}});
        finishTasks(context, id);
        return id;
    }

    // ---- Helpers ----

    private static long insertCat(LitterboxdDbHelper db, String name, int years, int months,
                                  String breed, String sex, boolean neutered, String coat,
                                  String lifestyle, long createdAt) {
        Cat cat = new Cat();
        cat.setName(name);
        cat.setApproxAgeYears(years);
        cat.setApproxAgeMonths(months);
        cat.setBreed(breed);
        cat.setSex(sex);
        cat.setNeutered(neutered);
        cat.setCoatColor(coat);
        cat.setLifestyle(lifestyle);
        cat.setCreatedAt(createdAt);
        return new CatDao(db).insertCat(cat);
    }

    /** Weight logs (kg) taken {@code daysAgo} days back; the profile weight is the newest one. */
    private static void weights(LitterboxdDbHelper db, long catId, double[] kg, int[] daysAgo,
                                String... notes) {
        WeightLogDao dao = new WeightLogDao(db);
        long now = System.currentTimeMillis();
        int newest = 0;
        for (int i = 0; i < kg.length; i++) {
            WeightLog log = new WeightLog();
            log.setCatId(catId);
            log.setWeight(kg[i]);
            log.setDate(now - daysAgo[i] * DAY);
            log.setNotes(i < notes.length ? notes[i] : "");
            dao.insertWeightLog(log);
            if (daysAgo[i] < daysAgo[newest]) newest = i;
        }
        CatDao catDao = new CatDao(db);
        Cat cat = catDao.getCatById(catId);
        if (cat != null) {
            cat.setWeight(kg[newest]);
            catDao.updateCat(cat);
        }
    }

    /** Rows of {label, "HH:mm", grams}. */
    private static void meals(LitterboxdDbHelper db, long catId, String[][] rows) {
        FeedingScheduleDao dao = new FeedingScheduleDao(db);
        for (String[] row : rows) {
            FeedingMeal meal = new FeedingMeal();
            meal.setCatId(catId);
            meal.setMealLabel(row[0]);
            meal.setMealTime(row[1]);
            meal.setPortionGrams(Double.parseDouble(row[2]));
            dao.insertFeedingMeal(meal);
        }
    }

    /** Feeding reminders from the schedule (same path as a real cat), then mark the cat prepared. */
    private static void finishTasks(Context context, long catId) {
        CareTaskSetup.syncFeedingTasks(context, catId);
        new AppPrefs(context).setCareTasksPrepared(catId);
    }

    private static long ownerTask(Context context, CareTaskDao dao, long catId, String name,
                                  String description, TaskFrequency frequency, int hour, int minute,
                                  Long dueAt, long now) {
        CareTask task = new CareTask();
        task.setCatId(catId);
        task.setName(name);
        task.setDescription(description);
        task.setFrequency(frequency);
        task.setReminderHour(hour);
        task.setReminderMinute(minute);
        task.setDueAt(dueAt);
        task.setCreatedAt(now);
        task.setId(dao.insertTask(task));
        CareReminderScheduler.schedule(context, task);
        return task.getId();
    }

    private static void complete(CareTaskDao dao, long taskId, long at) {
        dao.insertLog(taskId, at);
        CareTask task = dao.getTaskById(taskId);
        if (task != null) {
            task.setLastCompletedAt(at);
            dao.updateTask(task);
        }
    }

    /** answers("water", "more", "litter", "normal") -> {water=[more], litter=[normal]} */
    private static Map<String, List<String>> answers(String... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], Collections.singletonList(pairs[i + 1]));
        }
        return map;
    }
}
