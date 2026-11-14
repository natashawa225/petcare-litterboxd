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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
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
}
