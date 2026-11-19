package com.litterboxd.app;

import com.litterboxd.app.health.HealthAlert;
import com.litterboxd.app.health.HealthArticle;
import com.litterboxd.app.health.HealthContentParser;
import com.litterboxd.app.health.HealthRule;
import com.litterboxd.app.health.HealthRuleEvaluator;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The sample cats' check-in patterns (SampleData) produce the alerts and "For you" readings the
 * demo relies on. If a rule changes, this says which sample cat no longer shows its state.
 */
public class SampleScenarioRulesTest {

    private static List<HealthRule> rules;
    private static Map<String, HealthArticle> articles;
    private static String json;

    @BeforeClass
    public static void load() throws IOException, JSONException {
        json = new String(Files.readAllBytes(Paths.get("src/main/res/raw/health_rules.json")),
                StandardCharsets.UTF_8);
        articles = HealthContentParser.parseArticles(json);
        rules = HealthContentParser.parseRules(json, articles);
    }

    private static Map<String, List<String>> answers(String... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], Collections.singletonList(pairs[i + 1]));
        return map;
    }

    private static Set<String> ruleIds(Map<String, Map<String, List<String>>> byDate, LocalDate today) {
        Set<String> ids = new HashSet<>();
        for (HealthAlert alert : HealthRuleEvaluator.evaluate(rules, articles, byDate, today,
                Collections.emptySet(), Collections.<WeightTrend.Point>emptyList())) {
            ids.add(alert.getRule().getId());
        }
        return ids;
    }

    @Test
    public void mochiGetsOneVetVisitAlertAndAnInappetenceReading() throws JSONException {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        for (int i = 6; i >= 3; i--) {
            byDate.put(today.minusDays(i).toString(), answers("appetite", "normal", "water", "normal",
                    "litter", "normal", "energy", "normal", "mood", "normal"));
        }
        // Same answers as SampleData.addMochi().
        byDate.put(today.minusDays(2).toString(), answers("appetite", "less", "water", "normal",
                "litter", "normal", "vomiting", "once", "energy", "normal"));
        byDate.put(today.minusDays(1).toString(), answers("appetite", "less", "water", "normal",
                "litter", "normal", "vomiting", "repeated", "energy", "normal"));
        byDate.put(today.toString(), answers("appetite", "less", "water", "normal",
                "litter", "normal", "vomiting", "none", "energy", "normal"));

        Set<String> ids = ruleIds(byDate, today);
        assertTrue(ids.contains("low_appetite_vomiting"));
        for (HealthRule rule : rules) {
            if ("low_appetite_vomiting".equals(rule.getId())) assertTrue(rule.isVetVisit());
        }
        // The more specific vomiting rule supersedes "eating less 3 days": one card, not two.
        assertTrue(!ids.contains("appetite_less_repeat"));
        for (HealthAlert alert : HealthRuleEvaluator.evaluate(rules, articles, byDate, today,
                Collections.emptySet())) {
            assertTrue("Mochi's alerts are vet-visit, never urgent", !alert.getRule().isUrgent());
        }

        List<HealthArticle> reading = HealthRuleEvaluator.readingsFor(
                HealthContentParser.parseReadings(json, articles), articles, byDate.get(today.toString()));
        assertEquals("source_inappetence", reading.get(0).getId());
    }

    @Test
    public void biscuitGetsTheWaterWatchAlertAndTwoReadings() throws JSONException {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        for (int i = 1; i < 5; i++) {
            byDate.put(today.minusDays(i).toString(), answers("appetite", "normal", "water", "more",
                    "urination_more", "no", "litter", "normal"));
        }
        Map<String, List<String>> todays = answers("appetite", "normal", "water", "more",
                "urination_more", "no", "litter", "normal", "energy", "low", "mood", "grumpy");
        byDate.put(today.toString(), todays);

        assertTrue(ruleIds(byDate, today).contains("water_rising_week"));
        List<HealthArticle> reading = HealthRuleEvaluator.readingsFor(
                HealthContentParser.parseReadings(json, articles), articles, todays);
        assertEquals(2, reading.size());
    }

    @Test
    public void biscuitFullHistoryRecapHasWaterMoreOffAndAlertUnchanged() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        Set<Integer> skippedDays = new HashSet<>(java.util.Arrays.asList(12, 19, 26, 33, 38, 41));

        for (int i = 42; i >= 10; i--) {
            if (skippedDays.contains(i)) continue;
            String energy = (i == 15 || i == 28) ? "low" : "normal";
            String mood = (i == 18 || i == 25) ? "grumpy" : "normal";
            byDate.put(today.minusDays(i).toString(), i == 21
                    ? answers("appetite", "normal", "water", "normal", "urination_more", "no",
                            "litter", "straining", "urine_output", "some", "energy", energy, "mood", mood)
                    : answers("appetite", "normal", "water", "normal", "urination_more", "no",
                            "litter", "normal", "energy", energy, "mood", mood));
        }
        for (int i = 9; i >= 5; i--) {
            byDate.put(today.minusDays(i).toString(),
                    answers("appetite", "normal", "water", "more", "urination_more", "no",
                            "litter", "normal", "energy", "normal", "mood", "normal"));
        }
        for (int i = 4; i >= 1; i--) {
            byDate.put(today.minusDays(i).toString(),
                    answers("appetite", "normal", "water", "more", "urination_more", "no",
                            "litter", "normal", "energy", "normal", "mood", "normal"));
        }
        byDate.put(today.toString(),
                answers("appetite", "normal", "water", "more", "urination_more", "no",
                        "litter", "normal", "energy", "low", "mood", "grumpy"));

        // Assert existing alerts: Biscuit only gets water_rising_week, no extra alerts from older history
        Set<String> activeIds = ruleIds(byDate, today);
        assertEquals(1, activeIds.size());
        assertTrue(activeIds.contains("water_rising_week"));

        // Weights: 7 weekly points: 6.9 -> 6.5 kg
        List<com.litterboxd.app.health.WeightTrend.Point> weightPoints = new ArrayList<>();
        double[] weights = {6.9, 6.85, 6.8, 6.75, 6.7, 6.65, 6.5};
        int[] daysAgo = {40, 34, 27, 20, 13, 7, 2};
        for (int i = 0; i < weights.length; i++) {
            long millis = today.minusDays(daysAgo[i]).atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
            weightPoints.add(new com.litterboxd.app.health.WeightTrend.Point(millis, weights[i]));
        }

        Map<String, String> normalValues = com.litterboxd.app.health.HealthRecap.extractNormalValues(null);
        Set<String> concernPairs = com.litterboxd.app.health.HealthRecap.extractConcernPairs(rules);
        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(rules, articles, byDate, today,
                Collections.emptySet(), weightPoints);

        com.litterboxd.app.health.RecapResult recap30d = com.litterboxd.app.health.HealthRecap.compute(
                byDate, weightPoints, 30, today, normalValues, concernPairs, alerts, false, "Biscuit");

        assertFalse(recap30d.isNotEnoughData());
        assertTrue(recap30d.getDaysLogged() >= 24);

        com.litterboxd.app.health.SignalSummary waterSummary = recap30d.getSignals().stream()
                .filter(s -> "water".equals(s.getSignalId())).findFirst().orElse(null);
        assertNotNull(waterSummary);
        assertEquals(com.litterboxd.app.health.SignalSummary.Comparison.MORE_OFF, waterSummary.getComparison());

        // The straining day three weeks ago shows as a concern on the litter strip and leads the
        // highlights; the water rise comes next in plain words. The alert itself is not repeated.
        com.litterboxd.app.health.SignalSummary litterSummary = recap30d.getSignals().stream()
                .filter(s -> "litter".equals(s.getSignalId())).findFirst().orElse(null);
        assertNotNull(litterSummary);
        assertEquals(1, litterSummary.getConcernCount());
        List<com.litterboxd.app.health.RecapResult.Highlight> highlights = recap30d.getHighlights();
        assertEquals("Straining in the litter box on 1 day", highlights.get(0).getText());
        assertTrue(highlights.get(1).getText().startsWith("Drinking more on "));
        for (com.litterboxd.app.health.RecapResult.Highlight h : highlights) {
            assertEquals(null, h.getAlertRuleId());
        }
        // 6.9 -> 6.5 kg is under VCA's 10% line: a plain note with the source.
        assertTrue(recap30d.getWeightInsightText().startsWith("Down "));
        assertTrue(recap30d.getWeightInsightNote().contains("keep weighing"));
        assertEquals(com.litterboxd.app.health.HealthRecap.CITATION_VCA_TITLE, recap30d.getWeightCitationTitle());
    }

    @Test
    public void pipRecapShowsKittenGrowthCurve() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        Set<Integer> skippedDays = new HashSet<>(java.util.Arrays.asList(5, 11, 17, 23, 29, 34));

        for (int i = 35; i >= 1; i--) {
            if (skippedDays.contains(i)) continue;
            byDate.put(today.minusDays(i).toString(),
                    answers("appetite", "normal", "water", "normal", "litter", "normal",
                            "energy", "high", "mood", "normal"));
        }

        // Pip weights: 1.8 to 2.3 kg, about 100 g a week
        List<com.litterboxd.app.health.WeightTrend.Point> weightPoints = new ArrayList<>();
        double[] weights = {1.8, 1.9, 2.0, 2.1, 2.2, 2.3};
        int[] daysAgo = {35, 28, 21, 14, 7, 1};
        for (int i = 0; i < weights.length; i++) {
            long millis = today.minusDays(daysAgo[i]).atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
            weightPoints.add(new com.litterboxd.app.health.WeightTrend.Point(millis, weights[i]));
        }

        Map<String, String> normalValues = com.litterboxd.app.health.HealthRecap.extractNormalValues(null);
        Set<String> concernPairs = com.litterboxd.app.health.HealthRecap.extractConcernPairs(rules);

        com.litterboxd.app.health.RecapResult recap = com.litterboxd.app.health.HealthRecap.compute(
                byDate, weightPoints, 30, today, normalValues, concernPairs, Collections.emptyList(), true, "Pip");

        assertFalse(recap.isNotEnoughData());
        assertTrue(recap.getWeightChangeKg() > 0);
        assertTrue(recap.getWeightInsightText(), recap.getWeightInsightText().contains("about 10"));
        assertEquals(com.litterboxd.app.health.HealthRecap.CITATION_KITTEN_TITLE, recap.getWeightCitationTitle());
    }
}
