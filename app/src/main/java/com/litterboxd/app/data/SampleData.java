package com.litterboxd.app.data;

import android.content.Context;

import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.dao.FeedingScheduleDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DiaryEntry;
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
 *  - Biscuit (brown tabby Maine Coon, indoor/outdoor): two different meal times plus a full care
 *           routine (some done, some to do), weight drifting down, drinking more for 5 days -> a
 *           "Worth watching" alert; grumpy and tired today -> two "For you" reading cards; a
 *           profile photo and five photo diary pages.
 *  - Pip    (ginger kitten): three small meals and nothing else yet -> only feeding reminders,
 *           plus the Tasks tab's suggestions; no check-ins -> "No check-ins yet". She is the only
 *           cat with a profile photo and diary pages (four photo memories from her first weeks).
 *
 * Feeding reminders are not written here: they are generated from each cat's schedule by
 * CareTaskSetup, exactly as for a real cat. Cats already present (by name) are skipped.
 */
public final class SampleData {

    private static final long DAY = 86_400_000L;

    private SampleData() {}

    /**
     * Background thread. Returns how many things were added: sample cats, plus 1 when an existing
     * Pip (e.g. loaded by an older build) got her diary pages. 0 means everything was already there.
     */
    public static int load(Context context) {
        LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(context);
        CatDao catDao = new CatDao(db);
        Set<String> existing = new HashSet<>();
        Cat existingPip = null;
        Cat existingBiscuit = null;
        for (Cat c : catDao.getAllCats()) {
            existing.add(c.getName());
            if ("Pip".equals(c.getName()) && existingPip == null) existingPip = c;
            if ("Biscuit".equals(c.getName()) && existingBiscuit == null) existingBiscuit = c;
        }

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
            // Open the demo on Biscuit: his day has "For you" reading, an alert and a full diary.
            prefs.setSelectedCatId(id);
        } else if (new DiaryDao(db).getEntriesForCat(existingBiscuit.getId()).isEmpty()) {
            // Biscuit was added before his diary existed: top up his pages (never duplicates them).
            seedBiscuitPhotos(context, db, existingBiscuit.getId(), now);
            added++;
        }
        if (!existing.contains("Pip")) {
            long id = addPip(context, db, now);
            if (firstAdded < 0) firstAdded = id;
            added++;
        } else if (new DiaryDao(db).getEntriesForCat(existingPip.getId()).isEmpty()) {
            // Pip was added before her diary existed: top up her pages (never duplicates them).
            seedPipPhotos(context, db, existingPip.getId(), now);
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
                "Brown tabby", "Indoor / Outdoor", now - 300 * DAY);
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
        // Today he is also grumpy and low on energy -> two "For you" cards in the Reading Corner
        // (stress and lethargy), picked by the same check-in rules a real log uses.
        DailyCheckinDao checkins = new DailyCheckinDao(db);
        for (int i = 1; i < 5; i++) {
            checkins.saveCheckin(id, LocalDate.now().minusDays(i).toString(),
                    answers("appetite", "normal", "water", "more", "urination_more", "no", "litter", "normal"));
        }
        checkins.saveCheckin(id, LocalDate.now().toString(),
                answers("appetite", "normal", "water", "more", "urination_more", "no", "litter", "normal",
                        "energy", "low", "mood", "grumpy"));

        seedBiscuitPhotos(context, db, id, now);
        return id;
    }

    /**
     * Biscuit's photos (bundled in res/raw) become his profile picture and a diary spread over his
     * time at home. A profile picture the owner already chose is kept.
     */
    private static void seedBiscuitPhotos(Context context, LitterboxdDbHelper db, long id, long now) {
        setProfilePhotoIfMissing(context, db, id, R.raw.sample_biscuit_profile, "sample_biscuit.jpg");
        DiaryDao diary = new DiaryDao(db);
        diaryEntry(context, diary, id, DiaryEntry.Type.MILESTONE, "First bath (under protest)",
                "Biscuit's very first bath. He clung to the edge of the tub with both paws and gave "
                        + "me a look I will never forget. The rubber duck was his only friend.",
                R.raw.sample_biscuit_first_bath, "biscuit_first_bath", 210, now, "first_time", "funny");
        diaryEntry(context, diary, id, DiaryEntry.Type.MEMORY, "Bubble bath, round two",
                "Round two went much better. He sat in the bubbles like a tiny king, wearing the "
                        + "duck as a crown, and only complained a little.",
                R.raw.sample_biscuit_bubbles, "biscuit_bubbles", 95, now, "funny");
        diaryEntry(context, diary, id, DiaryEntry.Type.MEMORY, "Head chef, very grumpy",
                "Found him sitting in the frying pan on the (switched off!) cooker, looking deeply "
                        + "unimpressed that dinner was not ready yet.",
                R.raw.sample_biscuit_pan, "biscuit_pan", 40, now, "funny", "snack");
        diaryEntry(context, diary, id, DiaryEntry.Type.MEMORY, "Trying to help with the laundry",
                "Biscuit climbed into the washing machine to supervise. Very proud of himself. "
                        + "Laundry postponed until the inspector left.",
                R.raw.sample_biscuit_laundry, "biscuit_laundry", 12, now, "funny", "everyday");
        diaryEntry(context, diary, id, DiaryEntry.Type.MEMORY, "Sunbeam squint",
                "First sunny morning in weeks. He sat by the window with his eyes squeezed shut, "
                        + "soaking up every bit of warmth.",
                R.raw.sample_biscuit_sun, "biscuit_sun", 2, now, "everyday", "cuddles");
    }

    /** Copies a bundled photo as the cat's profile picture, unless the cat already has one. */
    private static void setProfilePhotoIfMissing(Context context, LitterboxdDbHelper db, long catId,
                                                 int rawRes, String name) {
        CatDao catDao = new CatDao(db);
        Cat cat = catDao.getCatById(catId);
        if (cat == null || (cat.getPhotoPath() != null && !cat.getPhotoPath().isEmpty())) return;
        String file = copyPhoto(context, rawRes, FileStorageHelper.CAT_PHOTO_PREFIX + name);
        if (file != null) {
            cat.setPhotoPath(file);
            catDao.updateCat(cat);
        }
    }

    private static long addPip(Context context, LitterboxdDbHelper db, long now) {
        long id = insertCat(db, "Pip", 0, 5, "Domestic Shorthair", "Female", false,
                "Ginger tabby", "Indoor only", now - 20 * DAY);
        weights(db, id, new double[]{2.1}, new int[]{6}, "First weigh-in");
        meals(db, id, new String[][]{{"Breakfast", "07:00", "25"}, {"Lunch", "12:30", "25"},
                {"Supper", "18:30", "25"}});
        finishTasks(context, id);
        seedPipPhotos(context, db, id, now);
        return id;
    }

    /**
     * Pip's photos (bundled in res/raw) become her profile picture and her first diary pages.
     * Only Pip gets these: she is the cat whose first weeks at home the demo walks through.
     * A profile picture the owner already chose (built-in avatar or own photo) is kept.
     */
    private static void seedPipPhotos(Context context, LitterboxdDbHelper db, long id, long now) {
        setProfilePhotoIfMissing(context, db, id, R.raw.sample_pip_profile, "sample_pip.jpg");
        DiaryDao diary = new DiaryDao(db);
        diaryEntry(context, diary, id, DiaryEntry.Type.MILESTONE, "First belly-up nap",
                "Pip fell asleep on her back in my lap for the first time, paws folded like a "
                        + "little thank-you. I think she has decided she lives here now.",
                R.raw.sample_pip_smile, "smile", 18, now, "cuddles", "first_time");
        diaryEntry(context, diary, id, DiaryEntry.Type.MEMORY, "Window seat inspector",
                "Spent the whole morning on the windowsill watching the street. In the low sun "
                        + "her shadow on the wall looked twice her size.",
                R.raw.sample_pip_window, "window", 12, now, "everyday");
        diaryEntry(context, diary, id, DiaryEntry.Type.MEMORY, "Staring contest with her shadow",
                "Pip spotted her own shadow on the yellow wall and stared it down for a full "
                        + "minute. The shadow did not back off. Neither did Pip.",
                R.raw.sample_pip_shadow, "shadow", 7, now, "funny", "play");
        diaryEntry(context, diary, id, DiaryEntry.Type.MEMORY, "Biggest yawn yet",
                "Woke up from her afternoon nap with the biggest yawn I have ever seen. "
                        + "All teeth, eyes squeezed shut, zero shame.",
                R.raw.sample_pip_yawn, "yawn", 2, now, "funny", "everyday");
    }

    /** One diary page with a bundled photo, dated {@code daysAgo} days back at 5 pm. */
    private static void diaryEntry(Context context, DiaryDao dao, long catId, DiaryEntry.Type type,
                                   String title, String text, int photoRes, String photoKey,
                                   int daysAgo, long now, String... tagKeys) {
        DiaryEntry entry = new DiaryEntry();
        entry.setCatId(catId);
        entry.setType(type);
        entry.setTitle(title);
        entry.setDescription(text);
        entry.setEntryAt(LocalDate.now().minusDays(daysAgo).atTime(17, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        entry.setPhotoPath(copyPhoto(context, photoRes,
                FileStorageHelper.DIARY_PHOTO_PREFIX + "sample_pip_" + photoKey + ".jpg"));
        entry.setTags(new java.util.ArrayList<>(java.util.Arrays.asList(tagKeys)));
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        dao.insertEntry(entry);
    }

    /**
     * Copies a bundled sample photo into the app's private storage, where every cat and diary
     * photo lives, and returns its file name (null if the copy failed: the entry keeps no photo).
     */
    private static String copyPhoto(Context context, int rawRes, String fileName) {
        java.io.File out = new FileStorageHelper(context).getInternalImageFile(fileName);
        try (java.io.InputStream in = context.getResources().openRawResource(rawRes);
             java.io.OutputStream os = new java.io.FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) os.write(buffer, 0, n);
            return fileName;
        } catch (java.io.IOException e) {
            android.util.Log.w("SampleData", "Could not copy sample photo " + fileName, e);
            return null;
        }
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
