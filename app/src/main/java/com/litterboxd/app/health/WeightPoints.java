package com.litterboxd.app.health;

import com.litterboxd.app.data.model.WeightLog;

import java.util.ArrayList;
import java.util.List;

/** Maps DB weight logs into evaluator points. */
public final class WeightPoints {
    private WeightPoints() {}

    public static List<WeightTrend.Point> fromLogs(List<WeightLog> logs) {
        List<WeightTrend.Point> points = new ArrayList<>();
        if (logs == null) return points;
        for (WeightLog log : logs) {
            if (log == null) continue;
            points.add(new WeightTrend.Point(log.getDate(), log.getWeight()));
        }
        return points;
    }
}
