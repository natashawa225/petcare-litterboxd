package com.litterboxd.app;

import com.litterboxd.app.health.HealthAlert;
import com.litterboxd.app.health.HealthArticle;
import com.litterboxd.app.health.HealthContentParser;
import com.litterboxd.app.health.HealthReading;
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
        String rulesJson = new String(Files.readAllBytes(
                Paths.get("src/main/res/raw/health_rules.json")), StandardCharsets.UTF_8);
        articles = HealthContentParser.parseArticles(rulesJson);
        rules = HealthContentParser.parseRules(rulesJson, articles);
    }

    @Test
    public void everyRuleGivesReasonsAndCitesARealPage() {
        for (HealthRule rule : rules) {
            assertFalse(rule.getId(), rule.getReasonsKey().isEmpty());
            for (HealthArticle article : HealthRuleEvaluator.articlesFor(rule, articles)) {
                assertTrue(rule.getId(), article.getUrl().startsWith("https://"));
            }
            assertFalse(rule.getId(), HealthRuleEvaluator.articlesFor(rule, articles).isEmpty());
        }
    }

    @Test
    public void disclaimerAppearsOnceAndNoAlertTitleNamesACondition() throws IOException {
        String strings = new String(Files.readAllBytes(
                Paths.get("src/main/res/values/strings.xml")), StandardCharsets.UTF_8);
        assertEquals(2, strings.split("does not diagnose", -1).length);
        assertFalse(strings.contains("Possible urinary blockage"));
    }

    private static Map<String, Map<String, List<String>>> days(LocalDate asOf, String question,
                                                               String value, int... daysAgo) {
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        for (int ago : daysAgo) {
            byDate.put(asOf.minusDays(ago).toString(), ans(question, value));
        }
        return byDate;
    }

    private static List<HealthAlert> alertsFor(LocalDate asOf,
                                               Map<String, Map<String, List<String>>> byDate) {
        return HealthRuleEvaluator.evaluate(rules, articles, byDate, asOf, Collections.emptySet());
    }

    @Test
    public void oneDayOfEachSignGivesNoAlert() {
        LocalDate asOf = LocalDate.of(2026, 11, 9);
        Map<String, List<String>> today = merge(merge(ans("appetite", "less"), ans("water", "less")),
                merge(merge(ans("litter", "less"), ans("energy", "low")), ans("mood", "grumpy")));
        Map<String, Map<String, List<String>>> byDate = new HashMap<>();
        byDate.put(asOf.toString(), today);
        assertTrue(alertsFor(asOf, byDate).isEmpty());
    }

    @Test
    public void repeatedSignsFireTheirAlerts() {
        LocalDate asOf = LocalDate.of(2026, 11, 9);
        assertTrue(hasRule(alertsFor(asOf, days(asOf, "appetite", "less", 0, 1, 2)), "appetite_less_repeat"));
        assertFalse(hasRule(alertsFor(asOf, days(asOf, "appetite", "less", 0, 1)), "appetite_less_repeat"));
        assertTrue(hasRule(alertsFor(asOf, days(asOf, "water", "less", 0, 1, 2)), "water_less_repeat"));
        assertTrue(hasRule(alertsFor(asOf, days(asOf, "litter", "less", 0, 2)), "litter_less_repeat"));
        assertTrue(hasRule(alertsFor(asOf, days(asOf, "energy", "low", 0, 1)), "energy_low_repeat"));
    }

    @Test
    public void grumpyStaysReadingOnlyEvenWhenRepeated() {
        LocalDate asOf = LocalDate.of(2026, 11, 9);
        assertTrue(alertsFor(asOf, days(asOf, "mood", "grumpy", 0, 1, 2, 3, 4, 5, 6)).isEmpty());
    }

    @Test
    public void vomitingAlertReplacesThePlainEatingLessAlert() {
        LocalDate asOf = LocalDate.of(2026, 11, 9);
        Map<String, Map<String, List<String>>> byDate = days(asOf, "appetite", "less", 0, 1, 2);
        byDate.put(asOf.toString(), merge(ans("appetite", "less"), ans("vomiting", "repeated")));
        List<HealthAlert> alerts = alertsFor(asOf, byDate);
        assertTrue(hasRule(alerts, "low_appetite_vomiting"));
        assertFalse(hasRule(alerts, "appetite_less_repeat"));
    }

    @Test
    public void readingsFollowTodaysAnswersAndStayShort() throws IOException, JSONException {
        String rulesJson = new String(Files.readAllBytes(
                Paths.get("src/main/res/raw/health_rules.json")), StandardCharsets.UTF_8);
        List<HealthReading> readings = HealthContentParser.parseReadings(rulesJson, articles);

        List<HealthArticle> one = HealthRuleEvaluator.readingsFor(readings, articles, ans("mood", "grumpy"));
        assertEquals(1, one.size());
        assertEquals("source_stress", one.get(0).getId());

        assertTrue(HealthRuleEvaluator.readingsFor(readings, articles, ans("mood", "normal")).isEmpty());

        Map<String, List<String>> all = merge(merge(ans("appetite", "less"), ans("water", "less")),
                merge(merge(ans("litter", "less"), ans("energy", "low")), ans("mood", "grumpy")));
        assertEquals(3, HealthRuleEvaluator.readingsFor(readings, articles, all).size());
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
    public void everyRuleSearchesWithTheSignsThatFireIt() {
        for (HealthRule rule : rules) {
            assertFalse(rule.getSearchQuery().isEmpty());
            assertFalse(rule.getSearchTerms().isEmpty());
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
    public void rulesFileParses() {
        assertEquals(12, rules.size());
    }

    private static Map<String, List<String>> merge(Map<String, List<String>> a,
                                                   Map<String, List<String>> b) {
        Map<String, List<String>> out = new LinkedHashMap<>(a);
        out.putAll(b);
        return out;
    }
}
