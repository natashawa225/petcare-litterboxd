package com.litterboxd.app.health;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Which of the last few days have a check-in, for the "all clear" week strip on Health alerts. */
public final class RecentLogs {

    public static final int WEEK = 7;
    /** Fewer logged days than this and "no alerts" can't mean much yet (the rules need 2-3 days). */
    public static final int ENOUGH_FOR_ALL_CLEAR = 3;

    private RecentLogs() {}

    /** One entry per day, oldest first; the last entry is {@code asOf}. */
    public static boolean[] loggedDays(Map<String, Map<String, List<String>>> answersByDate,
                                       LocalDate asOf, int days) {
        boolean[] logged = new boolean[days];
        if (answersByDate == null || asOf == null) return logged;
        for (int i = 0; i < days; i++) {
            Map<String, List<String>> answers =
                    answersByDate.get(asOf.minusDays(days - 1 - i).toString());
            logged[i] = answers != null && !answers.isEmpty();
        }
        return logged;
    }

    public static int count(boolean[] logged) {
        int n = 0;
        for (boolean day : logged) {
            if (day) n++;
        }
        return n;
    }
}
