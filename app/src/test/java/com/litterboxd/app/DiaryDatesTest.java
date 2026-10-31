package com.litterboxd.app;

import com.litterboxd.app.util.DiaryDates;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DiaryDatesTest {

    private static final long NOW = millis(2026, Calendar.OCTOBER, 30, 10, 0);

    @Test
    public void todayAndYesterday() {
        assertEquals(DiaryDates.Kind.TODAY, DiaryDates.headingFor(millis(2026, Calendar.OCTOBER, 30, 0, 5), NOW).kind);
        assertEquals(DiaryDates.Kind.YESTERDAY, DiaryDates.headingFor(millis(2026, Calendar.OCTOBER, 29, 23, 55), NOW).kind);
    }

    @Test
    public void recentDaysGetTheirOwnHeading() {
        DiaryDates.Heading a = DiaryDates.headingFor(millis(2026, Calendar.OCTOBER, 24, 9, 0), NOW);
        DiaryDates.Heading b = DiaryDates.headingFor(millis(2026, Calendar.OCTOBER, 24, 21, 0), NOW);
        DiaryDates.Heading c = DiaryDates.headingFor(millis(2026, Calendar.OCTOBER, 23, 21, 0), NOW);
        assertEquals(DiaryDates.Kind.DAY, a.kind);
        assertEquals(a.key(), b.key());
        assertNotEquals(a.key(), c.key());
    }

    @Test
    public void olderEntriesAreGroupedByMonth() {
        DiaryDates.Heading a = DiaryDates.headingFor(millis(2026, Calendar.SEPTEMBER, 3, 9, 0), NOW);
        DiaryDates.Heading b = DiaryDates.headingFor(millis(2026, Calendar.SEPTEMBER, 20, 9, 0), NOW);
        DiaryDates.Heading c = DiaryDates.headingFor(millis(2025, Calendar.SEPTEMBER, 20, 9, 0), NOW);
        assertEquals(DiaryDates.Kind.MONTH, a.kind);
        assertEquals(a.key(), b.key());
        assertNotEquals(a.key(), c.key()); // same month, different year
    }

    @Test
    public void futureTimesCountAsToday() {
        assertEquals(DiaryDates.Kind.TODAY, DiaryDates.headingFor(NOW + 3_600_000L, NOW).kind);
    }

    private static long millis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}
