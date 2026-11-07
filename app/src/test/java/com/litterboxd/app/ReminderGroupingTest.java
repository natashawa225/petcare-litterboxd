package com.litterboxd.app;

import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.TaskFrequency;
import com.litterboxd.app.reminders.ReminderGrouping;
import com.litterboxd.app.reminders.TodayListReminderScheduler;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReminderGroupingTest {

    private static long millis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    private static CareTask daily(long catId, String name, int hour, int minute) {
        CareTask task = new CareTask();
        task.setCatId(catId);
        task.setName(name);
        task.setFrequency(TaskFrequency.DAILY);
        task.setReminderHour(hour);
        task.setReminderMinute(minute);
        return task;
    }

    @Test
    public void sameNameAndTimeShareOneSlot() {
        long now = millis(2026, Calendar.NOVEMBER, 5, 8, 0);
        CareTask mochi = daily(1, "Meals", 8, 0);
        CareTask biscuit = daily(2, "Meals", 8, 0);
        CareTask scoop = daily(2, "Poop scoop", 8, 0);
        List<CareTask> group = ReminderGrouping.incompleteSameSlot(
                mochi, Arrays.asList(mochi, biscuit, scoop), now);
        assertEquals(2, group.size());
    }

    @Test
    public void finishedTaskIsLeftOutOfTheSharedReminder() {
        long now = millis(2026, Calendar.NOVEMBER, 5, 8, 0);
        CareTask mochi = daily(1, "Meals", 8, 0);
        CareTask biscuit = daily(2, "Meals", 8, 0);
        biscuit.setLastCompletedAt(now);
        List<CareTask> group = ReminderGrouping.incompleteSameSlot(
                mochi, Arrays.asList(mochi, biscuit), now);
        assertEquals(1, group.size());
        assertEquals(1L, group.get(0).getCatId());
    }

    @Test
    public void unfinishedTodayNamesEveryCatStillOpen() {
        long now = millis(2026, Calendar.NOVEMBER, 5, 20, 0);
        CareTask mochiMeals = daily(1, "Meals", 8, 0);
        CareTask biscuitMeals = daily(2, "Meals", 8, 0);
        biscuitMeals.setLastCompletedAt(now);
        CareTask lunaScoop = daily(3, "Poop scoop", 9, 0);
        Map<Long, String> names = new HashMap<>();
        names.put(1L, "Mochi");
        names.put(2L, "Biscuit");
        names.put(3L, "Luna");
        List<String> open = ReminderGrouping.unfinishedTodayCatNames(
                Arrays.asList(mochiMeals, biscuitMeals, lunaScoop), names, now);
        assertEquals(Arrays.asList("Luna", "Mochi"), open);
        assertEquals("Luna and Mochi", ReminderGrouping.joinNames(open));
        assertEquals("Mochi, Biscuit, and Luna",
                ReminderGrouping.joinNames(Arrays.asList("Mochi", "Biscuit", "Luna")));
    }

    @Test
    public void doneCatsProduceNoUnfinishedNames() {
        long now = millis(2026, Calendar.NOVEMBER, 5, 20, 0);
        CareTask meals = daily(1, "Meals", 8, 0);
        meals.setLastCompletedAt(now);
        assertTrue(ReminderGrouping.unfinishedTodayCatNames(
                Collections.singletonList(meals), Collections.singletonMap(1L, "Mochi"), now).isEmpty());
    }

    @Test
    public void todayReminderMovesToTomorrowOnceTheTimeHasPassed() {
        long atEight = millis(2026, Calendar.NOVEMBER, 5, 20, 0);
        long next = TodayListReminderScheduler.nextMillis(20, 0, atEight);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(next);
        assertEquals(6, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(20, calendar.get(Calendar.HOUR_OF_DAY));
        assertFalse(next <= atEight);
    }
}
