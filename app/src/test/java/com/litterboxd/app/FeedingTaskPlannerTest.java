package com.litterboxd.app;

import com.litterboxd.app.data.FeedingTaskPlanner;
import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.model.TaskFrequency;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Feeding reminders follow each cat's own feeding schedule, without duplicates. */
public class FeedingTaskPlannerTest {

    private static final FeedingTaskPlanner.Describer GRAMS =
            meal -> (long) meal.getPortionGrams() + " g";
    private static final long NOW = 1_700_000_000_000L;

    private static FeedingMeal meal(long catId, String label, String time, double grams) {
        FeedingMeal meal = new FeedingMeal();
        meal.setCatId(catId);
        meal.setMealLabel(label);
        meal.setMealTime(time);
        meal.setPortionGrams(grams);
        return meal;
    }

    /** Runs a plan and turns its inserts into stored tasks (fake ids), as the DAO would. */
    private static List<CareTask> applyInserts(FeedingTaskPlanner.Plan plan, long firstId) {
        List<CareTask> stored = new ArrayList<>();
        long id = firstId;
        for (CareTask task : plan.toInsert) {
            task.setId(id++);
            stored.add(task);
        }
        return stored;
    }

    private static CareTask ownerTask(long id, long catId, String name, int hour, int minute) {
        CareTask task = new CareTask();
        task.setId(id);
        task.setCatId(catId);
        task.setName(name);
        task.setFrequency(TaskFrequency.DAILY);
        task.setReminderHour(hour);
        task.setReminderMinute(minute);
        return task;
    }

    @Test
    public void newCatGetsOneDailyReminderPerMealAtItsOwnTimes() {
        FeedingTaskPlanner.Plan plan = FeedingTaskPlanner.plan(7L,
                Arrays.asList(meal(7, "Breakfast", "08:00", 45), meal(7, "Dinner", "18:30", 40)),
                Collections.emptyList(), GRAMS, NOW);

        assertEquals(2, plan.toInsert.size());
        assertTrue(plan.toUpdate.isEmpty());
        assertTrue(plan.toDelete.isEmpty());
        CareTask breakfast = plan.toInsert.get(0);
        assertEquals(7L, breakfast.getCatId());
        assertEquals("Breakfast", breakfast.getName());
        assertEquals("45 g", breakfast.getDescription());
        assertEquals(TaskFrequency.DAILY, breakfast.getFrequency());
        assertEquals(Integer.valueOf(8), breakfast.getReminderHour());
        assertEquals(Integer.valueOf(0), breakfast.getReminderMinute());
        assertEquals(LitterboxdContract.CareTaskEntry.SOURCE_FEEDING, breakfast.getSource());
        assertEquals(Integer.valueOf(18), plan.toInsert.get(1).getReminderHour());
        assertEquals(Integer.valueOf(30), plan.toInsert.get(1).getReminderMinute());
    }

    @Test
    public void catWithNoScheduleGetsNoInventedTasks() {
        FeedingTaskPlanner.Plan plan = FeedingTaskPlanner.plan(3L, Collections.emptyList(),
                Collections.emptyList(), GRAMS, NOW);
        assertTrue(plan.isEmpty());
    }

    @Test
    public void syncingTheSameScheduleAgainCreatesNoDuplicates() {
        List<FeedingMeal> meals = Arrays.asList(meal(1, "Breakfast", "08:00", 45),
                meal(1, "Dinner", "18:00", 45));
        List<CareTask> stored = applyInserts(
                FeedingTaskPlanner.plan(1L, meals, Collections.emptyList(), GRAMS, NOW), 100);

        FeedingTaskPlanner.Plan again = FeedingTaskPlanner.plan(1L, meals, stored, GRAMS, NOW);
        assertTrue(again.isEmpty());
    }

    @Test
    public void changingAPortionUpdatesTheSameReminder() {
        List<CareTask> stored = applyInserts(FeedingTaskPlanner.plan(1L,
                Collections.singletonList(meal(1, "Breakfast", "08:00", 45)),
                Collections.emptyList(), GRAMS, NOW), 100);

        FeedingTaskPlanner.Plan plan = FeedingTaskPlanner.plan(1L,
                Collections.singletonList(meal(1, "Breakfast", "08:00", 60)), stored, GRAMS, NOW);
        assertTrue(plan.toInsert.isEmpty());
        assertTrue(plan.toDelete.isEmpty());
        assertEquals(1, plan.toUpdate.size());
        assertEquals(100L, plan.toUpdate.get(0).getId()); // same row: keeps its done-today state
        assertEquals("60 g", plan.toUpdate.get(0).getDescription());
    }

    @Test
    public void movingAMealReplacesItsReminderAndRemovedMealsLoseTheirs() {
        List<CareTask> stored = applyInserts(FeedingTaskPlanner.plan(1L,
                Arrays.asList(meal(1, "Breakfast", "08:00", 45), meal(1, "Dinner", "18:00", 45)),
                Collections.emptyList(), GRAMS, NOW), 100);

        // Breakfast moves to 07:30, Dinner is removed.
        FeedingTaskPlanner.Plan plan = FeedingTaskPlanner.plan(1L,
                Collections.singletonList(meal(1, "Breakfast", "07:30", 45)), stored, GRAMS, NOW);
        assertEquals(1, plan.toInsert.size());
        assertEquals(Integer.valueOf(7), plan.toInsert.get(0).getReminderHour());
        assertEquals(Arrays.asList(100L, 101L), plan.toDelete);
    }

    @Test
    public void ownerTasksAreNeverTouchedEvenAtTheSameTime() {
        List<CareTask> existing = new ArrayList<>();
        existing.add(ownerTask(50, 1, "Litter scoop", 8, 0));

        FeedingTaskPlanner.Plan plan = FeedingTaskPlanner.plan(1L,
                Collections.singletonList(meal(1, "Breakfast", "08:00", 45)), existing, GRAMS, NOW);
        assertEquals(1, plan.toInsert.size());
        assertTrue(plan.toUpdate.isEmpty());
        assertTrue(plan.toDelete.isEmpty());

        FeedingTaskPlanner.Plan cleared = FeedingTaskPlanner.plan(1L,
                Collections.emptyList(), existing, GRAMS, NOW);
        assertTrue(cleared.isEmpty());
    }

    @Test
    public void differentCatsGetTheirOwnSchedules() {
        FeedingTaskPlanner.Plan mochi = FeedingTaskPlanner.plan(1L,
                Arrays.asList(meal(1, "Breakfast", "08:00", 45), meal(1, "Dinner", "18:00", 45)),
                Collections.emptyList(), GRAMS, NOW);
        FeedingTaskPlanner.Plan pip = FeedingTaskPlanner.plan(2L,
                Arrays.asList(meal(2, "Breakfast", "07:00", 25), meal(2, "Lunch", "12:30", 25),
                        meal(2, "Supper", "18:30", 25)),
                Collections.emptyList(), GRAMS, NOW);

        assertEquals(2, mochi.toInsert.size());
        assertEquals(3, pip.toInsert.size());
        for (CareTask t : mochi.toInsert) assertEquals(1L, t.getCatId());
        for (CareTask t : pip.toInsert) assertEquals(2L, t.getCatId());
        assertEquals("Lunch", pip.toInsert.get(1).getName());
    }

    @Test
    public void invalidMealTimesAreSkipped() {
        FeedingTaskPlanner.Plan plan = FeedingTaskPlanner.plan(1L,
                Arrays.asList(meal(1, "Odd", "25:00", 10), meal(1, "Blank", null, 10),
                        meal(1, "Ok", "09:15", 10)),
                Collections.emptyList(), GRAMS, NOW);
        assertEquals(1, plan.toInsert.size());
        assertEquals("Ok", plan.toInsert.get(0).getName());
    }
}
