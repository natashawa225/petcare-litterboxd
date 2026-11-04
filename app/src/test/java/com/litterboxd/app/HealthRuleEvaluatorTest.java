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
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HealthRuleEvaluatorTest {

    private static List<HealthRule> rules;
    private static Map<String, HealthArticle> articles;

    @BeforeClass
    public static void loadFiles() throws IOException, JSONException {
        String articlesJson = new String(Files.readAllBytes(
                Paths.get("src/main/res/raw/health_articles.json")), StandardCharsets.UTF_8);
        String rulesJson = new String(Files.readAllBytes(
                Paths.get("src/main/res/raw/health_rules.json")), StandardCharsets.UTF_8);
        articles = HealthContentParser.parseArticles(articlesJson);
        rules = HealthContentParser.parseRules(rulesJson, articles);
    }

    private static Map<String, List<String>> ans(String questionId, String value) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put(questionId, Collections.singletonList(value));
        return map;
    }

    private static Map<String, Map<String, List<String>>> waterMoreDays(LocalDate asOf, int days) {
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        for (int i = 0; i < days; i++) {
            byDate.put(asOf.minusDays(i).toString(), ans("water", "more"));
        }
        return byDate;
    }

    private static long millis(LocalDate day) {
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static boolean hasRule(List<HealthAlert> alerts, String id) {
        for (HealthAlert alert : alerts) {
            if (id.equals(alert.getRule().getId())) return true;
        }
        return false;
    }

    @Test
    public void fiveWaterMoreDaysInAWeekFiresWatchAlert() {
        LocalDate asOf = LocalDate.of(2026, 11, 3);
        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                rules, articles, waterMoreDays(asOf, 5), asOf, Collections.emptySet());
        assertTrue(hasRule(alerts, "water_rising_week"));
    }

    @Test
    public void drinkAndPeeComboIsVetVisit() {
        LocalDate asOf = LocalDate.of(2026, 11, 3);
        Map<String, Map<String, List<String>>> byDate = waterMoreDays(asOf, 3);
        byDate.put(asOf.toString(), merge(ans("water", "more"), ans("urination_more", "yes")));
        byDate.put(asOf.minusDays(1).toString(),
                merge(ans("water", "more"), ans("urination_more", "yes")));
        byDate.put(asOf.minusDays(2).toString(), ans("water", "more"));

        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                rules, articles, byDate, asOf, Collections.emptySet());
        assertTrue(hasRule(alerts, "drink_and_pee"));
    }

    @Test
    public void drinkPeeWithWeightDropFiresWeightRule() {
        LocalDate asOf = LocalDate.of(2026, 11, 3);
        Map<String, Map<String, List<String>>> byDate = waterMoreDays(asOf, 3);
        byDate.put(asOf.toString(), merge(ans("water", "more"), ans("urination_more", "yes")));
        byDate.put(asOf.minusDays(1).toString(),
                merge(ans("water", "more"), ans("urination_more", "yes")));

        List<WeightTrend.Point> weights = Arrays.asList(
                new WeightTrend.Point(millis(asOf.minusDays(10)), 5.6),
                new WeightTrend.Point(millis(asOf), 5.2));

        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                rules, articles, byDate, asOf, Collections.emptySet(), weights);
        assertTrue(hasRule(alerts, "drink_pee_weight"));
    }

    @Test
    public void weightRuleHidesGeneralDrinkPeeRule() {
        LocalDate asOf = LocalDate.of(2026, 11, 3);
        Map<String, Map<String, List<String>>> byDate = waterMoreDays(asOf, 3);
        byDate.put(asOf.toString(), merge(ans("water", "more"), ans("urination_more", "yes")));
        byDate.put(asOf.minusDays(1).toString(),
                merge(ans("water", "more"), ans("urination_more", "yes")));
        List<WeightTrend.Point> weights = Arrays.asList(
                new WeightTrend.Point(millis(asOf.minusDays(10)), 5.6),
                new WeightTrend.Point(millis(asOf), 5.2));

        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                rules, articles, byDate, asOf, Collections.emptySet(), weights);
        assertTrue(hasRule(alerts, "drink_pee_weight"));
        assertFalse(hasRule(alerts, "drink_and_pee"));

        // Dismissing the specific alert must not bring the general duplicate back.
        Set<String> dismissed = new HashSet<>(Collections.singleton("drink_pee_weight"));
        List<HealthAlert> afterDismiss = HealthRuleEvaluator.evaluate(
                rules, articles, byDate, asOf, dismissed, weights);
        assertFalse(hasRule(afterDismiss, "drink_pee_weight"));
        assertFalse(hasRule(afterDismiss, "drink_and_pee"));
    }

    @Test
    public void articlesForReturnsRulesListInOrder() {
        for (HealthRule rule : rules) {
            List<HealthArticle> list = HealthRuleEvaluator.articlesFor(rule, articles);
            assertEquals(rule.getArticleIds().size(), list.size());
            for (int i = 0; i < list.size(); i++) {
                assertEquals(rule.getArticleIds().get(i), list.get(i).getId());
            }
        }
    }

    @Test
    public void litterStrainingIsUrgentSameDay() {
        LocalDate asOf = LocalDate.of(2026, 11, 3);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        byDate.put(asOf.toString(), ans("litter", "straining"));
        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                rules, articles, byDate, asOf, Collections.emptySet());
        assertTrue(hasRule(alerts, "urine_blockage_urgent"));
        for (HealthAlert alert : alerts) {
            if ("urine_blockage_urgent".equals(alert.getRule().getId())) {
                assertTrue(alert.getRule().isUrgent());
            }
        }
    }

    @Test
    public void littleUrineOutputIsUrgent() {
        LocalDate asOf = LocalDate.of(2026, 11, 3);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        byDate.put(asOf.toString(), ans("urine_output", "little_or_none"));
        assertTrue(hasRule(HealthRuleEvaluator.evaluate(
                rules, articles, byDate, asOf, Collections.emptySet()), "urine_blockage_urgent"));
    }

    @Test
    public void litterBaselineDoublingFiresWatch() {
        LocalDate asOf = LocalDate.of(2026, 11, 21);
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        // Prior 14 days (offset 7..20): 7 logged days, 1 litter=more → rate ~0.14
        for (int i = 7; i <= 13; i++) {
            byDate.put(asOf.minusDays(i).toString(),
                    i == 10 ? ans("litter", "more") : ans("litter", "normal"));
        }
        // Recent 7 days: 5 logged, 4 litter=more → rate 0.8 (>= 2x prior)
        for (int i = 0; i < 5; i++) {
            byDate.put(asOf.minusDays(i).toString(),
                    i < 4 ? ans("litter", "more") : ans("litter", "normal"));
        }
        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                rules, articles, byDate, asOf, Collections.emptySet());
        assertTrue(hasRule(alerts, "litter_baseline_double"));
    }

    @Test
    public void dismissedRuleIsSkippedUntilCleared() {
        LocalDate asOf = LocalDate.of(2026, 11, 3);
        Set<String> dismissed = new HashSet<>();
        dismissed.add("water_rising_week");
        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                rules, articles, waterMoreDays(asOf, 5), asOf, dismissed);
        assertFalse(hasRule(alerts, "water_rising_week"));
    }

    @Test
    public void fourWaterDaysDoesNotFireRisingRule() {
        LocalDate asOf = LocalDate.of(2026, 11, 3);
        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                rules, articles, waterMoreDays(asOf, 4), asOf, Collections.emptySet());
        assertFalse(hasRule(alerts, "water_rising_week"));
    }

    @Test
    public void presetFilesParseAndCrossLink() {
        assertEquals(8, articles.size());
        assertEquals(8, rules.size());
        for (HealthRule rule : rules) {
            assertFalse(rule.getArticleIds().isEmpty());
            for (String id : rule.getArticleIds()) {
                assertTrue(articles.containsKey(id));
            }
        }
    }

    private static Map<String, List<String>> merge(Map<String, List<String>> a,
                                                   Map<String, List<String>> b) {
        Map<String, List<String>> out = new LinkedHashMap<>(a);
        out.putAll(b);
        return out;
    }
}
