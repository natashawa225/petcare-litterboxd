package com.litterboxd.app.util;

import java.util.Calendar;

/**
 * Groups diary timeline items under date headings: one heading per day for the last 30 days
 * ("Today", "Yesterday", "October 24"), and one per month for anything older ("September 2026"),
 * so years of memories stay easy to scan. Plain Java so it can be unit-tested.
 */
public final class DiaryDates {

    /** Days that get their own heading before entries are grouped by month. */
    public static final int DAY_HEADING_WINDOW = 30;

    public enum Kind { TODAY, YESTERDAY, DAY, MONTH }

    /** A heading: its kind plus the calendar fields needed to format it. */
    public static final class Heading {
        public final Kind kind;
        public final int year, month, day;

        Heading(Kind kind, int year, int month, int day) {
            this.kind = kind;
            this.year = year;
            this.month = month;
            this.day = day;
        }

        /** Same heading = same group in the timeline. */
        public String key() {
            return kind == Kind.MONTH ? year + "-" + month : year + "-" + month + "-" + day;
        }
    }

    private DiaryDates() {}

    public static Heading headingFor(long entryMillis, long nowMillis) {
        Calendar entry = Calendar.getInstance();
        entry.setTimeInMillis(entryMillis);
        long daysAgo = startOfDay(nowMillis) - startOfDay(entryMillis);
        daysAgo = Math.round(daysAgo / 86_400_000.0); // round: a DST change makes a day 23 or 25 hours
        int y = entry.get(Calendar.YEAR), m = entry.get(Calendar.MONTH), d = entry.get(Calendar.DAY_OF_MONTH);
        if (daysAgo <= 0) return new Heading(Kind.TODAY, y, m, d);
        if (daysAgo == 1) return new Heading(Kind.YESTERDAY, y, m, d);
        if (daysAgo <= DAY_HEADING_WINDOW) return new Heading(Kind.DAY, y, m, d);
        return new Heading(Kind.MONTH, y, m, 1);
    }

    public static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
