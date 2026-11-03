package com.litterboxd.app;

import com.litterboxd.app.checkin.HistoryTrigger;
import com.litterboxd.app.checkin.HistoryTriggerEvaluator;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HistoryTriggerEvaluatorTest {

    private static final HistoryTrigger WATER_RISING = new HistoryTrigger(
            "water_rising_urination", "water", "more", 5,
            Collections.singletonList("urination_more"));

    private static Map<String, List<String>> waterMore() {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("water", Collections.singletonList("more"));
        return answers;
    }

    private static Map<String, Map<String, List<String>>> streakEndingBefore(LocalDate checkin, int days) {
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        for (int i = 1; i <= days; i++) {
            byDate.put(checkin.minusDays(i).toString(), waterMore());
        }
        return byDate;
    }

    @Test
    public void fiveDaysOfMoreWaterAddsUrinationFollowUp() {
        LocalDate day = LocalDate.of(2026, 11, 2);
        List<String> ids = HistoryTriggerEvaluator.evaluate(
                Collections.singletonList(WATER_RISING), day, streakEndingBefore(day, 5));
        assertEquals(Collections.singletonList("urination_more"), ids);
    }

    @Test
    public void fourDaysIsNotEnough() {
        LocalDate day = LocalDate.of(2026, 11, 2);
        List<String> ids = HistoryTriggerEvaluator.evaluate(
                Collections.singletonList(WATER_RISING), day, streakEndingBefore(day, 4));
        assertTrue(ids.isEmpty());
    }

    @Test
    public void aGapInTheStreakCancelsTheTrigger() {
        LocalDate day = LocalDate.of(2026, 11, 2);
        Map<String, Map<String, List<String>>> byDate = streakEndingBefore(day, 5);
        byDate.remove(day.minusDays(3).toString()); // missing day breaks the streak
        List<String> ids = HistoryTriggerEvaluator.evaluate(
                Collections.singletonList(WATER_RISING), day, byDate);
        assertTrue(ids.isEmpty());
    }

    @Test
    public void normalWaterOnOneDayCancelsTheTrigger() {
        LocalDate day = LocalDate.of(2026, 11, 2);
        Map<String, Map<String, List<String>>> byDate = streakEndingBefore(day, 5);
        Map<String, List<String>> normal = new LinkedHashMap<>();
        normal.put("water", Collections.singletonList("normal"));
        byDate.put(day.minusDays(2).toString(), normal);
        List<String> ids = HistoryTriggerEvaluator.evaluate(
                Collections.singletonList(WATER_RISING), day, byDate);
        assertTrue(ids.isEmpty());
    }

    @Test
    public void todaysOwnAnswersDoNotCountTowardTheStreak() {
        LocalDate day = LocalDate.of(2026, 11, 2);
        Map<String, Map<String, List<String>>> byDate = streakEndingBefore(day, 4);
        byDate.put(day.toString(), waterMore()); // today does not fill the 5th past day
        List<String> ids = HistoryTriggerEvaluator.evaluate(
                Collections.singletonList(WATER_RISING), day, byDate);
        assertTrue(ids.isEmpty());
    }

    @Test
    public void matchingTriggersDeduplicateFollowUpIds() {
        HistoryTrigger again = new HistoryTrigger(
                "water_rising_again", "water", "more", 5,
                Arrays.asList("urination_more", "urination_more"));
        LocalDate day = LocalDate.of(2026, 11, 2);
        List<String> ids = HistoryTriggerEvaluator.evaluate(
                Arrays.asList(WATER_RISING, again), day, streakEndingBefore(day, 5));
        assertEquals(Collections.singletonList("urination_more"), ids);
    }
}
