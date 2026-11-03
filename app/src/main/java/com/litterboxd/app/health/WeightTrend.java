package com.litterboxd.app.health;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Helpers for “weight dropping” combo rules from weight log timestamps. */
public final class WeightTrend {

    public static final class Point {
        public final long epochMillis;
        public final double kg;

        public Point(long epochMillis, double kg) {
            this.epochMillis = epochMillis;
            this.kg = kg;
        }
    }

    private WeightTrend() {}

    /**
     * True when the newest weight in the lookback window is at least {@code minDropKg} below
     * an older weight also inside that window.
     */
    public static boolean droppedAtLeast(List<Point> points, LocalDate asOf, int lookbackDays,
                                         double minDropKg) {
        if (points == null || points.isEmpty() || asOf == null || lookbackDays < 1 || minDropKg <= 0) {
            return false;
        }
        LocalDate start = asOf.minusDays(lookbackDays);
        List<Point> inWindow = new ArrayList<>();
        for (Point p : points) {
            LocalDate day = Instant.ofEpochMilli(p.epochMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            if (!day.isBefore(start) && !day.isAfter(asOf)) {
                inWindow.add(p);
            }
        }
        if (inWindow.size() < 2) return false;
        Collections.sort(inWindow, Comparator.comparingLong(p -> p.epochMillis));
        Point oldest = inWindow.get(0);
        Point newest = inWindow.get(inWindow.size() - 1);
        return newest.kg <= oldest.kg - minDropKg;
    }
}
