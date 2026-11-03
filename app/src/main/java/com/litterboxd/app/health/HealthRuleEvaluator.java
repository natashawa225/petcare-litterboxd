package com.litterboxd.app.health;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns recent check-in answers (and optional weight points) into {@link HealthAlert}s.
 */
public final class HealthRuleEvaluator {

    private HealthRuleEvaluator() {}

    public static List<HealthAlert> evaluate(List<HealthRule> rules,
                                             Map<String, HealthArticle> articlesById,
                                             Map<String, Map<String, List<String>>> answersByDate,
                                             LocalDate asOf,
                                             Set<String> dismissedRuleIds) {
        return evaluate(rules, articlesById, answersByDate, asOf, dismissedRuleIds,
                Collections.<WeightTrend.Point>emptyList());
    }

    public static List<HealthAlert> evaluate(List<HealthRule> rules,
                                             Map<String, HealthArticle> articlesById,
                                             Map<String, Map<String, List<String>>> answersByDate,
                                             LocalDate asOf,
                                             Set<String> dismissedRuleIds,
                                             List<WeightTrend.Point> weightPoints) {
        List<HealthAlert> alerts = new ArrayList<>();
        if (rules == null || answersByDate == null || asOf == null) {
            return alerts;
        }
        List<WeightTrend.Point> weights = weightPoints == null
                ? Collections.<WeightTrend.Point>emptyList()
                : weightPoints;
        for (HealthRule rule : rules) {
            if (dismissedRuleIds != null && dismissedRuleIds.contains(rule.getId())) {
                continue;
            }
            if (matches(rule, answersByDate, asOf, weights)) {
                List<HealthArticle> articles = new ArrayList<>();
                for (String articleId : rule.getArticleIds()) {
                    HealthArticle article = articlesById.get(articleId);
                    if (article != null) articles.add(article);
                }
                alerts.add(new HealthAlert(rule, articles));
            }
        }
        alerts.sort((a, b) -> Integer.compare(
                b.getRule().severityRank(), a.getRule().severityRank()));
        return alerts;
    }

    private static boolean matches(HealthRule rule,
                                   Map<String, Map<String, List<String>>> answersByDate,
                                   LocalDate asOf,
                                   List<WeightTrend.Point> weightPoints) {
        boolean base;
        if (rule.isBaseline()) {
            base = matchesBaseline(rule, answersByDate, asOf);
        } else {
            base = matchesSignals(rule, answersByDate, asOf);
        }
        if (!base) return false;
        Double drop = rule.getRequiresWeightDropKg();
        if (drop == null) return true;
        return WeightTrend.droppedAtLeast(
                weightPoints, asOf, Math.max(1, rule.getWeightLookbackDays()), drop);
    }

    private static boolean matchesSignals(HealthRule rule,
                                          Map<String, Map<String, List<String>>> answersByDate,
                                          LocalDate asOf) {
        List<Boolean> results = new ArrayList<>();
        for (HealthSignal signal : rule.getSignals()) {
            results.add(countDays(signal, answersByDate, asOf, rule.getWindowDays())
                    >= signal.getMinDays());
        }
        if (results.isEmpty()) return false;
        if ("any".equals(rule.getMatch())) {
            for (boolean ok : results) {
                if (ok) return true;
            }
            return false;
        }
        for (boolean ok : results) {
            if (!ok) return false;
        }
        return true;
    }

    /**
     * Compares how often a chip appears in the recent window vs the prior baseline window.
     * Needs enough logged days in both windows (Notion: after ~1–2 weeks of history).
     */
    private static boolean matchesBaseline(HealthRule rule,
                                           Map<String, Map<String, List<String>>> answersByDate,
                                           LocalDate asOf) {
        String q = rule.getBaselineQuestionId();
        String value = rule.getBaselineValue();
        if (q == null || value == null) return false;

        int recentDays = Math.max(1, rule.getWindowDays());
        int priorDays = Math.max(recentDays, rule.getBaselinePriorDays());

        int recentLogged = 0;
        int recentHits = 0;
        for (int i = 0; i < recentDays; i++) {
            Map<String, List<String>> answers = answersByDate.get(asOf.minusDays(i).toString());
            if (answers == null) continue;
            recentLogged++;
            List<String> values = answers.get(q);
            if (values != null && values.contains(value)) recentHits++;
        }

        int priorLogged = 0;
        int priorHits = 0;
        for (int i = recentDays; i < recentDays + priorDays; i++) {
            Map<String, List<String>> answers = answersByDate.get(asOf.minusDays(i).toString());
            if (answers == null) continue;
            priorLogged++;
            List<String> values = answers.get(q);
            if (values != null && values.contains(value)) priorHits++;
        }

        if (recentLogged < rule.getBaselineMinRecentLogged()) return false;
        if (priorLogged < rule.getBaselineMinPriorLogged()) return false;

        double recentRate = (double) recentHits / (double) recentLogged;
        double priorRate = (double) priorHits / (double) priorLogged;
        if (priorRate <= 0) {
            // No prior "more" days: still flag a sudden cluster (e.g. 4+ of 7).
            return recentHits >= Math.max(4, (int) Math.ceil(rule.getBaselineMinRatio() * 2));
        }
        return recentRate >= priorRate * rule.getBaselineMinRatio();
    }

    private static int countDays(HealthSignal signal,
                                 Map<String, Map<String, List<String>>> answersByDate,
                                 LocalDate asOf,
                                 int windowDays) {
        int count = 0;
        for (int i = 0; i < windowDays; i++) {
            String day = asOf.minusDays(i).toString();
            Map<String, List<String>> answers = answersByDate.get(day);
            if (answers == null) continue;
            List<String> values = answers.get(signal.getQuestionId());
            if (values != null && values.contains(signal.getValue())) {
                count++;
            }
        }
        return count;
    }
}
