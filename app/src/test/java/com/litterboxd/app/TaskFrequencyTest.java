package com.litterboxd.app;

import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.TaskFrequency;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.*;

public class TaskFrequencyTest {

    @Test
    public void dailyCompletesOnSameCalendarDayOnly() {
        long completed = millis(2026, Calendar.OCTOBER, 24, 22, 15);
        long sameDay = millis(2026, Calendar.OCTOBER, 24, 23, 59);
        long nextDay = millis(2026, Calendar.OCTOBER, 25, 0, 1);
        assertTrue(TaskFrequency.DAILY.isCompleteForCurrentPeriod(completed, sameDay));
        assertFalse(TaskFrequency.DAILY.isCompleteForCurrentPeriod(completed, nextDay));
        assertFalse(TaskFrequency.DAILY.isCompleteForCurrentPeriod(null, sameDay));
    }

    @Test
    public void monthlyCompletesInSameCalendarMonthOnly() {
        long completed = millis(2026, Calendar.OCTOBER, 3, 10, 0);
        long laterInMonth = millis(2026, Calendar.OCTOBER, 31, 18, 0);
        long nextMonth = millis(2026, Calendar.NOVEMBER, 1, 0, 1);
        assertTrue(TaskFrequency.MONTHLY.isCompleteForCurrentPeriod(completed, laterInMonth));
        assertFalse(TaskFrequency.MONTHLY.isCompleteForCurrentPeriod(completed, nextMonth));
    }

    @Test
    public void dailyReminderUsesNextClockTimeUntilDone() {
        long afternoon = millis(2026, Calendar.OCTOBER, 24, 15, 0);
        long beforeReminder = millis(2026, Calendar.OCTOBER, 24, 8, 0);
        assertEquals(millis(2026, Calendar.OCTOBER, 25, 9, 0),
                TaskFrequency.DAILY.nextReminderMillis(9, 0, afternoon, false));
        assertEquals(millis(2026, Calendar.OCTOBER, 24, 9, 0),
                TaskFrequency.DAILY.nextReminderMillis(9, 0, beforeReminder, false));
        assertEquals(millis(2026, Calendar.OCTOBER, 25, 9, 0),
                TaskFrequency.DAILY.nextReminderMillis(9, 0, beforeReminder, true));
    }

    @Test
    public void monthlyReminderKeepsFiringUntilDoneThenWaitsForNextMonth() {
        long afternoon = millis(2026, Calendar.OCTOBER, 24, 15, 0);
        assertEquals(millis(2026, Calendar.OCTOBER, 25, 10, 0),
                TaskFrequency.MONTHLY.nextReminderMillis(10, 0, afternoon, false));
        assertEquals(millis(2026, Calendar.NOVEMBER, 1, 10, 0),
                TaskFrequency.MONTHLY.nextReminderMillis(10, 0, afternoon, true));
    }

    @Test
    public void oneOffStaysDoneOnceCompleted() {
        long completed = millis(2026, Calendar.OCTOBER, 3, 10, 0);
        long monthsLater = millis(2027, Calendar.MARCH, 1, 9, 0);
        assertTrue(TaskFrequency.ONCE.isCompleteForCurrentPeriod(completed, monthsLater));
        assertFalse(TaskFrequency.ONCE.isCompleteForCurrentPeriod(null, monthsLater));
        assertEquals(TaskFrequency.ONCE, TaskFrequency.fromStorage("ONCE"));
    }

    @Test
    public void oneOffRemindsAtDueTimeOnlyWhileFutureAndNotDone() {
        long now = millis(2026, Calendar.OCTOBER, 24, 15, 0);
        long due = millis(2026, Calendar.OCTOBER, 25, 9, 30);
        CareTask task = new CareTask();
        task.setFrequency(TaskFrequency.ONCE);
        task.setDueAt(due);
        task.setReminderHour(9);
        task.setReminderMinute(30);
        assertEquals(Long.valueOf(due), task.nextReminderMillis(now));
        assertFalse(task.isOverdue(now));

        long afterDue = millis(2026, Calendar.OCTOBER, 25, 10, 0);
        assertNull(task.nextReminderMillis(afterDue));
        assertTrue(task.isOverdue(afterDue));

        task.setLastCompletedAt(now);
        assertNull(task.nextReminderMillis(now));
        assertFalse(task.isOverdue(afterDue));
    }

    @Test
    public void oneOffWithoutTimeHasNoReminder() {
        CareTask task = new CareTask();
        task.setFrequency(TaskFrequency.ONCE);
        task.setDueAt(millis(2026, Calendar.OCTOBER, 25, 23, 59));
        assertNull(task.nextReminderMillis(millis(2026, Calendar.OCTOBER, 24, 15, 0)));
    }

    @Test
    public void dailyTaskReminderGoesThroughFrequency() {
        CareTask task = new CareTask();
        task.setFrequency(TaskFrequency.DAILY);
        task.setReminderHour(9);
        task.setReminderMinute(0);
        long afternoon = millis(2026, Calendar.OCTOBER, 24, 15, 0);
        assertEquals(Long.valueOf(millis(2026, Calendar.OCTOBER, 25, 9, 0)), task.nextReminderMillis(afternoon));
        task.setReminderHour(null);
        assertNull(task.nextReminderMillis(afternoon));
    }

    @Test
    public void todaysTasksAreDailyPlusOneOffsDueToday() {
        long now = millis(2026, Calendar.OCTOBER, 30, 10, 0);
        CareTask daily = new CareTask();
        daily.setFrequency(TaskFrequency.DAILY);
        assertTrue(daily.isDueToday(now));

        CareTask monthly = new CareTask();
        monthly.setFrequency(TaskFrequency.MONTHLY);
        assertFalse(monthly.isDueToday(now));

        CareTask dueToday = new CareTask();
        dueToday.setFrequency(TaskFrequency.ONCE);
        dueToday.setDueAt(millis(2026, Calendar.OCTOBER, 30, 23, 59));
        assertTrue(dueToday.isDueToday(now));

        CareTask dueTomorrow = new CareTask();
        dueTomorrow.setFrequency(TaskFrequency.ONCE);
        dueTomorrow.setDueAt(millis(2026, Calendar.OCTOBER, 31, 9, 0));
        assertFalse(dueTomorrow.isDueToday(now));

        CareTask noDueDate = new CareTask();
        noDueDate.setFrequency(TaskFrequency.ONCE);
        assertFalse(noDueDate.isDueToday(now));
    }

    @Test
    public void fromStorageFallsBackToDaily() {
        assertEquals(TaskFrequency.MONTHLY, TaskFrequency.fromStorage("MONTHLY"));
        assertEquals(TaskFrequency.DAILY, TaskFrequency.fromStorage("WEEKLY"));
        assertEquals(TaskFrequency.DAILY, TaskFrequency.fromStorage(null));
    }

    private static long millis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}
