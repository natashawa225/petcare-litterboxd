package com.litterboxd.app;

import com.litterboxd.app.health.DayState;
import com.litterboxd.app.health.HealthAlert;
import com.litterboxd.app.health.HealthArticle;
import com.litterboxd.app.health.HealthContentParser;
import com.litterboxd.app.health.HealthRecap;
import com.litterboxd.app.health.HealthRule;
import com.litterboxd.app.health.RecapResult;
import com.litterboxd.app.health.SignalSummary;
import com.litterboxd.app.health.WeightTrend;

import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HealthRecapTest {

    private static List<HealthRule> rules;
    private static Map<String, HealthArticle> articles;
    private static String checkinTreeJson;
    private static Map<String, String> normalValues;
    private static Set<String> concernPairs;

    @BeforeClass
    public static void load() throws IOException, JSONException {
        String rulesJson = new String(Files.readAllBytes(Paths.get("src/main/res/raw/health_rules.json")),
                StandardCharsets.UTF_8);
        articles = HealthContentParser.parseArticles(rulesJson);
        rules = HealthContentParser.parseRules(rulesJson, articles);
        checkinTreeJson = new String(Files.readAllBytes(Paths.get("src/main/res/raw/checkin_tree.json")),
                StandardCharsets.UTF_8);
        normalValues = HealthRecap.extractNormalValues(checkinTreeJson);
        concernPairs = HealthRecap.extractConcernPairs(rules);
    }

    private static Map<String, List<String>> answers(String... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], Collections.singletonList(pairs[i + 1]));
        }
        return map;
    }

    @Test
    public void testExtractNormalValues() {
        assertEquals("normal", normalValues.get("appetite"));
        assertEquals("normal", normalValues.get("water"));
        assertEquals("normal", normalValues.get("litter"));
        assertEquals("normal", normalValues.get("energy"));
        assertEquals("normal", normalValues.get("mood"));
    }

    @Test
    public void testExtractConcernPairs() {
        assertTrue(concernPairs.contains("litter:straining"));
        assertTrue(concernPairs.contains("urine_output:little_or_none"));
        assertTrue(concernPairs.contains("vomiting:repeated"));
    }

    @Test
    public void testStreaksWithGaps() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();

        // Logged: days 0, 1, 2, skip 3, logged: 4, 5, 6
        byDate.put(today.toString(), answers("appetite", "normal"));
        byDate.put(today.minusDays(1).toString(), answers("appetite", "normal"));
        byDate.put(today.minusDays(2).toString(), answers("appetite", "normal"));
        // day 3 missing
        byDate.put(today.minusDays(4).toString(), answers("appetite", "normal"));
        byDate.put(today.minusDays(5).toString(), answers("appetite", "normal"));
        byDate.put(today.minusDays(6).toString(), answers("appetite", "normal"));
        // prior days logged so notEnoughData is false
        for (int i = 7; i <= 14; i++) {
            byDate.put(today.minusDays(i).toString(), answers("appetite", "normal"));
        }

        RecapResult result = HealthRecap.compute(byDate, null, 7, today,
                normalValues, concernPairs, Collections.emptyList(), false, "TestCat");

        assertEquals(6, result.getDaysLogged());
        assertEquals(7, result.getDaysInPeriod());
        assertEquals(3, result.getCurrentStreak());
        assertEquals(3, result.getLongestStreak());
        assertFalse(result.isNotEnoughData());
    }

    @Test
    public void testEmptyCaseProducesNotEnoughData() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        RecapResult result = HealthRecap.compute(Collections.emptyMap(), null, 7, today,
                normalValues, concernPairs, Collections.emptyList(), false, "EmptyCat");

        assertTrue(result.isNotEnoughData());
        assertEquals(0, result.getDaysLogged());
        assertEquals(0, result.getCurrentStreak());
    }

    @Test
    public void testLessThan7DaysProducesNotEnoughData() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            byDate.put(today.minusDays(i).toString(), answers("appetite", "normal"));
        }

        RecapResult result = HealthRecap.compute(byDate, null, 7, today,
                normalValues, concernPairs, Collections.emptyList(), false, "NewCat");

        assertTrue(result.isNotEnoughData());
        assertEquals(5, result.getDaysLogged());
    }

    @Test
    public void testPreviousPeriodComparisonMoreOff() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();

        // Current 7 days: water is "more" on 5 of 7 days
        for (int i = 0; i < 5; i++) {
            byDate.put(today.minusDays(i).toString(),
                    answers("appetite", "normal", "water", "more", "litter", "normal",
                            "energy", "normal", "mood", "normal"));
        }
        for (int i = 5; i < 7; i++) {
            byDate.put(today.minusDays(i).toString(),
                    answers("appetite", "normal", "water", "normal", "litter", "normal",
                            "energy", "normal", "mood", "normal"));
        }

        // Prior 7 days: water was normal all 7 days
        for (int i = 7; i < 14; i++) {
            byDate.put(today.minusDays(i).toString(),
                    answers("appetite", "normal", "water", "normal", "litter", "normal",
                            "energy", "normal", "mood", "normal"));
        }

        RecapResult result = HealthRecap.compute(byDate, null, 7, today,
                normalValues, concernPairs, Collections.emptyList(), false, "Biscuit");

        assertFalse(result.isNotEnoughData());
        SignalSummary waterSummary = null;
        for (SignalSummary s : result.getSignals()) {
            if ("water".equals(s.getSignalId())) {
                waterSummary = s;
                break;
            }
        }
        assertNotNull(waterSummary);
        assertEquals(SignalSummary.Comparison.MORE_OFF, waterSummary.getComparison());
        assertEquals(2, waterSummary.getNormalCount());
        assertEquals(5, waterSummary.getOffCount());
    }

    @Test
    public void testHighlightRankingSkipsActiveAlert() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();

        for (int i = 0; i < 14; i++) {
            byDate.put(today.minusDays(i).toString(),
                    answers("appetite", "normal", "water", i < 5 ? "more" : "normal",
                            "litter", "normal", "energy", "normal", "mood", "normal"));
        }

        HealthRule rule = rules.stream().filter(r -> "water_rising_week".equals(r.getId()))
                .findFirst().orElse(null);
        assertNotNull(rule);
        List<HealthAlert> activeAlerts = Collections.singletonList(
                new HealthAlert(rule, Collections.emptyList()));

        RecapResult result = HealthRecap.compute(byDate, null, 7, today,
                normalValues, concernPairs, activeAlerts, false, "Biscuit");

        // The alert is shown by the profile's status line, not repeated as a highlight;
        // the top highlight describes the logs in plain words.
        assertFalse(result.getHighlights().isEmpty());
        RecapResult.Highlight topHighlight = result.getHighlights().get(0);
        assertEquals(null, topHighlight.getAlertRuleId());
        assertTrue(topHighlight.getText(), topHighlight.getText().startsWith("Drinking more on "));
    }

    @Test
    public void testMochiProducesNotEnoughDataState() {
        // Mochi has 7 days of logs (days 0 through 6)
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        for (int i = 6; i >= 3; i--) {
            byDate.put(today.minusDays(i).toString(), answers("appetite", "normal", "water", "normal",
                    "litter", "normal", "energy", "normal", "mood", "normal"));
        }
        byDate.put(today.minusDays(2).toString(), answers("appetite", "less", "water", "normal",
                "litter", "normal", "vomiting", "once", "energy", "normal"));
        byDate.put(today.minusDays(1).toString(), answers("appetite", "less", "water", "normal",
                "litter", "normal", "vomiting", "repeated", "energy", "normal"));
        byDate.put(today.toString(), answers("appetite", "less", "water", "normal",
                "litter", "normal", "vomiting", "none", "energy", "normal"));

        // For 30d period, Mochi has only 7 days -> not enough data
        RecapResult result30d = HealthRecap.compute(byDate, null, 30, today,
                normalValues, concernPairs, Collections.emptyList(), false, "Mochi");
        assertTrue("Mochi should produce not enough data for 30d period", result30d.isNotEnoughData());

        // For 7d period, Mochi has 0 prior days logged (first week) -> not enough data for comparison
        RecapResult result7d = HealthRecap.compute(byDate, null, 7, today,
                normalValues, concernPairs, Collections.emptyList(), false, "Mochi");
        assertTrue("Mochi should produce not enough data for 7d period", result7d.isNotEnoughData());
    }

    @Test
    public void adultLosingMoreThanTenPercentGetsVetNoteWithoutCrashing() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        List<WeightTrend.Point> points = new ArrayList<>();
        double[] kg = {5.0, 4.7, 4.4};
        int[] daysAgo = {28, 14, 1};
        for (int i = 0; i < kg.length; i++) {
            points.add(new WeightTrend.Point(today.minusDays(daysAgo[i])
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), kg[i]));
        }
        RecapResult result = HealthRecap.compute(new HashMap<>(), points, 30, today,
                normalValues, concernPairs, Collections.emptyList(), false, "Mochi");
        String insight = result.getWeightInsightText();
        assertTrue(insight, insight.startsWith("Down 0.6 kg (12.0%)"));
        assertTrue(result.getWeightInsightNote().contains("Book a check-up"));
        assertEquals(HealthRecap.CITATION_VCA_TITLE, result.getWeightCitationTitle());
    }

    @Test
    public void offPhrasesUsePlainWords() {
        assertEquals("Drinking more", HealthRecap.offPhrase("water", "more"));
        assertEquals("Straining in the litter box", HealthRecap.offPhrase("litter", "straining"));
        assertEquals("Grumpy", HealthRecap.offPhrase("mood", "grumpy"));
    }
}
