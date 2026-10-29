package com.litterboxd.app.data.model;

import java.util.Calendar;

/**
 * How often a care task resets. Add new constants (WEEKLY, YEARLY, …) here and handle
 * them in {@link #isCompleteForCurrentPeriod} and {@link #nextReminderMillis}.
 */
public enum TaskFrequency {
    DAILY,
    MONTHLY,
    /** A one-off task (e.g. "Book the vet"): stays done once completed; may have a due date/time. */
    ONCE;

    public static TaskFrequency fromStorage(String value) {
        if (value == null) {
            return DAILY;
        }
        try {
            return TaskFrequency.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return DAILY;
        }
    }

    public String toStorage() {
        return name();
    }

    /**
     * True when {@code lastCompletedAt} falls in the same period as {@code nowMillis}
     * (same local calendar day for DAILY, same month for MONTHLY).
     */
    public boolean isCompleteForCurrentPeriod(Long lastCompletedAt, long nowMillis) {
        if (lastCompletedAt == null || lastCompletedAt <= 0L) {
            return false;
        }
        Calendar completed = Calendar.getInstance();
        completed.setTimeInMillis(lastCompletedAt);
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        switch (this) {
            case DAILY:
                return completed.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                        && completed.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
            case MONTHLY:
                return completed.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                        && completed.get(Calendar.MONTH) == now.get(Calendar.MONTH);
            case ONCE:
                return true; // completed at any time means done for good
            default:
                return false;
        }
    }

    /**
     * Next alarm time at {@code hour}:{@code minute}.
     * Incomplete tasks fire at the next upcoming clock time (today or tomorrow) so monthly
     * tasks keep reminding until they are done this month. Completed tasks wait until the
     * next period (tomorrow for daily, 1st of next month for monthly).
     */
    public long nextReminderMillis(int hour, int minute, long nowMillis, boolean completeThisPeriod) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);

        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        if (completeThisPeriod) {
            switch (this) {
                case DAILY:
                    next.add(Calendar.DAY_OF_MONTH, 1);
                    return next.getTimeInMillis();
                case MONTHLY:
                    next.set(Calendar.DAY_OF_MONTH, 1);
                    next.add(Calendar.MONTH, 1);
                    return next.getTimeInMillis();
                default:
                    next.add(Calendar.DAY_OF_MONTH, 1);
                    return next.getTimeInMillis();
            }
        }

        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        return next.getTimeInMillis();
    }
}
