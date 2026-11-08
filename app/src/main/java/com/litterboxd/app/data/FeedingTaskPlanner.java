package com.litterboxd.app.data;

import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.model.TaskFrequency;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out how a cat's feeding reminders must change to match its feeding schedule.
 * Plain Java (no Android), so the rules are unit-tested in FeedingTaskPlannerTest.
 *
 * One daily reminder per meal, at the meal's time. Existing feeding reminders are matched by
 * time, so editing a portion or a label updates the same task (keeping its "done today" state and
 * history) instead of creating a duplicate; meals that were removed lose their reminder.
 */
public final class FeedingTaskPlanner {

    /** Builds the line shown under a feeding reminder, e.g. "45 g · from feeding schedule". */
    public interface Describer {
        String describe(FeedingMeal meal);
    }

    /** The changes to apply: tasks to insert, tasks to update in place, task ids to delete. */
    public static final class Plan {
        public final List<CareTask> toInsert = new ArrayList<>();
        public final List<CareTask> toUpdate = new ArrayList<>();
        public final List<Long> toDelete = new ArrayList<>();

        public boolean isEmpty() {
            return toInsert.isEmpty() && toUpdate.isEmpty() && toDelete.isEmpty();
        }
    }

    /** Meals sort before the owner's own tasks; within meals, earliest first. */
    static final int SORT_BASE = -2000;

    private FeedingTaskPlanner() {}

    /**
     * @param catId          the cat being synced
     * @param meals          the cat's feeding schedule (times as "HH:mm"; bad times are skipped)
     * @param existingTasks  all of the cat's care tasks; only feeding-generated ones are touched
     */
    public static Plan plan(long catId, List<FeedingMeal> meals, List<CareTask> existingTasks,
                            Describer describer, long now) {
        Plan plan = new Plan();
        List<CareTask> unmatched = new ArrayList<>();
        for (CareTask task : existingTasks) {
            if (task.isFromFeedingSchedule()) unmatched.add(task);
        }

        for (FeedingMeal meal : meals) {
            int[] time = parseTime(meal.getMealTime());
            if (time == null) continue;
            String name = mealName(meal);
            String description = describer.describe(meal);
            int sortOrder = SORT_BASE + time[0] * 60 + time[1];

            CareTask match = takeMatch(unmatched, time[0], time[1]);
            if (match != null) {
                boolean changed = !name.equals(match.getName())
                        || !equalsNullable(description, match.getDescription())
                        || match.getSortOrder() != sortOrder;
                if (changed) {
                    match.setName(name);
                    match.setDescription(description);
                    match.setSortOrder(sortOrder);
                    plan.toUpdate.add(match);
                }
                continue;
            }
            CareTask task = new CareTask();
            task.setCatId(catId);
            task.setName(name);
            task.setDescription(description);
            task.setFrequency(TaskFrequency.DAILY);
            task.setReminderHour(time[0]);
            task.setReminderMinute(time[1]);
            task.setSortOrder(sortOrder);
            task.setDefault(false);
            task.setSource(LitterboxdContract.CareTaskEntry.SOURCE_FEEDING);
            task.setCreatedAt(now);
            plan.toInsert.add(task);
        }

        for (CareTask leftover : unmatched) {
            plan.toDelete.add(leftover.getId());
        }
        return plan;
    }

    /** "08:00" -> {8, 0}; null when the text isn't a valid 24-hour time. */
    static int[] parseTime(String hhmm) {
        if (hhmm == null) return null;
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) return null;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
            return new int[]{hour, minute};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String mealName(FeedingMeal meal) {
        String label = meal.getMealLabel();
        return label == null || label.trim().isEmpty() ? "Meal" : label.trim();
    }

    /** Removes and returns the first unmatched feeding task at this time, or null. */
    private static CareTask takeMatch(List<CareTask> unmatched, int hour, int minute) {
        for (int i = 0; i < unmatched.size(); i++) {
            CareTask task = unmatched.get(i);
            if (task.getReminderHour() != null && task.getReminderMinute() != null
                    && task.getReminderHour() == hour && task.getReminderMinute() == minute) {
                return unmatched.remove(i);
            }
        }
        return null;
    }

    private static boolean equalsNullable(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
