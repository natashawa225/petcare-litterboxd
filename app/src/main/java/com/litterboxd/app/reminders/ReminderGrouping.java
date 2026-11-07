package com.litterboxd.app.reminders;

import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.TaskFrequency;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Decides which care reminders belong in one notification, and which cats still have today's list open. */
public final class ReminderGrouping {

    private ReminderGrouping() {}

    public static String nameKey(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    public static String slotKey(CareTask task) {
        return nameKey(task.getName()) + "|" + task.getReminderHour() + "|" + task.getReminderMinute();
    }

    public static String dayKey(long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        return String.format(Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Incomplete tasks that share {@code firing}'s name and reminder clock time.
     * One-off tasks count only when they are due today. Daily and monthly count whenever
     * they are still open, because their alarm is that clock time.
     */
    public static List<CareTask> incompleteSameSlot(CareTask firing, List<CareTask> all, long nowMillis) {
        List<CareTask> group = new ArrayList<>();
        if (firing == null || !countsNow(firing, nowMillis)) return group;
        String key = nameKey(firing.getName());
        if (all == null) return group;
        for (CareTask task : all) {
            if (!countsNow(task, nowMillis)) continue;
            if (!key.equals(nameKey(task.getName()))) continue;
            if (!firing.getReminderHour().equals(task.getReminderHour())) continue;
            if (!firing.getReminderMinute().equals(task.getReminderMinute())) continue;
            group.add(task);
        }
        return group;
    }

    /** Cat names that still have a task on today's checklist. Monthly tasks are not part of that list. */
    public static List<String> unfinishedTodayCatNames(List<CareTask> tasks, Map<Long, String> namesByCatId, long nowMillis) {
        Set<Long> catIds = new LinkedHashSet<>();
        if (tasks != null) {
            for (CareTask task : tasks) {
                if (task != null && task.isDueToday(nowMillis) && !task.isCompleteNow(nowMillis)) {
                    catIds.add(task.getCatId());
                }
            }
        }
        List<String> names = new ArrayList<>();
        if (namesByCatId != null) {
            for (Long catId : catIds) {
                String name = namesByCatId.get(catId);
                if (name != null && !name.trim().isEmpty()) names.add(name.trim());
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** "Mochi", "Mochi and Biscuit", or "Mochi, Biscuit, and Luna". */
    public static String joinNames(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) text.append(i == names.size() - 1 ? ", and " : ", ");
            text.append(names.get(i));
        }
        return text.toString();
    }

    private static boolean countsNow(CareTask task, long nowMillis) {
        if (task == null || !task.hasReminder() || task.isCompleteNow(nowMillis)) return false;
        if (task.getFrequency() == TaskFrequency.ONCE) return task.isDueToday(nowMillis);
        return task.getFrequency() == TaskFrequency.DAILY || task.getFrequency() == TaskFrequency.MONTHLY;
    }
}
