package com.litterboxd.app.health;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses res/raw health_rules.json. Plain Java for unit tests. */
public final class HealthContentParser {

    private HealthContentParser() {}

    public static Map<String, HealthArticle> parseArticles(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray array = root.getJSONArray("articles");
        Map<String, HealthArticle> map = new LinkedHashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            HealthArticle article = new HealthArticle(
                    o.getString("id"),
                    o.getString("titleKey"),
                    o.getString("summaryKey"),
                    o.getString("sourceKey"),
                    o.getString("url"));
            if (map.put(article.getId(), article) != null) {
                throw new IllegalArgumentException("Duplicate article id: " + article.getId());
            }
        }
        return map;
    }

    public static List<HealthRule> parseRules(String json, Map<String, HealthArticle> articles)
            throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray array = root.getJSONArray("rules");
        List<HealthRule> rules = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            String type = o.optString("type", HealthRule.TYPE_SIGNAL);
            List<HealthSignal> signals = new ArrayList<>();
            JSONArray signalArray = o.optJSONArray("signals");
            if (signalArray != null) {
                for (int s = 0; s < signalArray.length(); s++) {
                    JSONObject sig = signalArray.getJSONObject(s);
                    signals.add(new HealthSignal(
                            sig.getString("questionId"),
                            sig.getString("value"),
                            sig.getInt("minDays")));
                }
            }
            if (HealthRule.TYPE_SIGNAL.equals(type) && signals.isEmpty()) {
                throw new IllegalArgumentException("Signal rule '" + o.getString("id")
                        + "' needs signals");
            }

            JSONArray articleArray = o.optJSONArray("articleIds");
            List<String> articleIds = articleArray == null
                    ? new ArrayList<String>()
                    : readStrings(articleArray);
            if (articles != null) {
                for (String articleId : articleIds) {
                    if (!articles.containsKey(articleId)) {
                        throw new IllegalArgumentException("Rule '" + o.getString("id")
                                + "' references missing article '" + articleId + "'");
                    }
                }
            }
            String searchQuery = o.optString("searchQuery", "").trim();
            JSONArray termArray = o.optJSONArray("searchTerms");
            List<String> searchTerms = termArray == null
                    ? new ArrayList<String>()
                    : readStrings(termArray);
            if (searchQuery.isEmpty() || searchTerms.isEmpty()) {
                throw new IllegalArgumentException("Rule '" + o.getString("id")
                        + "' needs searchQuery and searchTerms");
            }
            JSONArray supersedesArray = o.optJSONArray("supersedes");
            List<String> supersedes = supersedesArray == null
                    ? new ArrayList<String>()
                    : readStrings(supersedesArray);

            String severity = o.getString("severity");
            if (!HealthRule.SEVERITY_WATCH.equals(severity)
                    && !HealthRule.SEVERITY_VET_VISIT.equals(severity)
                    && !HealthRule.SEVERITY_URGENT.equals(severity)) {
                throw new IllegalArgumentException("Unknown severity: " + severity);
            }

            Double weightDrop = o.has("requiresWeightDropKg")
                    ? Double.valueOf(o.getDouble("requiresWeightDropKg"))
                    : null;
            int weightLookback = o.optInt("weightLookbackDays", 14);

            String baselineQ = o.has("baselineQuestionId") ? o.getString("baselineQuestionId") : null;
            String baselineV = o.has("baselineValue") ? o.getString("baselineValue") : null;
            if (baselineQ != null && baselineQ.isEmpty()) baselineQ = null;
            if (baselineV != null && baselineV.isEmpty()) baselineV = null;
            if (HealthRule.TYPE_BASELINE.equals(type) && (baselineQ == null || baselineV == null)) {
                throw new IllegalArgumentException("Baseline rule '" + o.getString("id")
                        + "' needs baselineQuestionId and baselineValue");
            }

            rules.add(new HealthRule(
                    o.getString("id"),
                    type,
                    severity,
                    o.getInt("windowDays"),
                    o.optString("match", "all"),
                    signals,
                    o.getString("titleKey"),
                    o.getString("messageKey"),
                    articleIds,
                    supersedes,
                    weightDrop,
                    weightLookback,
                    baselineQ,
                    baselineV,
                    o.optInt("baselinePriorDays", 14),
                    o.optInt("baselineMinPriorLogged", 7),
                    o.optInt("baselineMinRecentLogged", 5),
                    o.optDouble("baselineMinRatio", 2.0),
                    searchQuery,
                    searchTerms));
        }
        Set<String> ruleIds = new HashSet<>();
        for (HealthRule rule : rules) ruleIds.add(rule.getId());
        for (HealthRule rule : rules) {
            for (String hidden : rule.getSupersedes()) {
                if (!ruleIds.contains(hidden)) {
                    throw new IllegalArgumentException("Rule '" + rule.getId()
                            + "' supersedes missing rule '" + hidden + "'");
                }
            }
        }
        return rules;
    }

    private static List<String> readStrings(JSONArray array) throws JSONException {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(array.getString(i));
        }
        return list;
    }
}
