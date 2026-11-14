package com.litterboxd.app;

import com.litterboxd.app.health.RecentLogs;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RecentLogsTest {

    private static Map<String, List<String>> answer() {
        return Collections.singletonMap("appetite", Collections.singletonList("normal"));
    }

    @Test
    public void weekIsOldestFirstWithTodayLast() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        byDate.put("2026-11-14", answer());
        byDate.put("2026-11-12", answer());
        byDate.put("2026-11-08", answer());
        byDate.put("2026-11-07", answer()); // 8 days ago: outside the week
        byDate.put("2026-11-13", Collections.emptyMap()); // saved with no answers

        boolean[] week = RecentLogs.loggedDays(byDate, today, RecentLogs.WEEK);
        assertArrayEquals(new boolean[]{true, false, false, false, true, false, true}, week);
        assertEquals(3, RecentLogs.count(week));
    }

    @Test
    public void noHistoryMeansNothingLogged() {
        boolean[] week = RecentLogs.loggedDays(null, LocalDate.of(2026, 11, 14), RecentLogs.WEEK);
        assertEquals(0, RecentLogs.count(week));
        assertEquals(RecentLogs.WEEK, week.length);
    }
}
