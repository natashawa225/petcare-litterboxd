package com.litterboxd.app.health;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java engine that computes the health recap summary, signal states, consistency,
 * highlights, and weight insights from raw check-in answers and weight points.
 *
 * Contains no Android framework imports.
 */
public final class HealthRecap {

    public static final List<String> SIGNALS = Collections.unmodifiableList(
            Arrays.asList("appetite", "water", "litter", "energy", "mood"));

    // Citations. Each insight quotes only the figure its source states.
    /** VCA: loss of more than 10% of normal body weight is clinically significant. */
    public static final String CITATION_VCA_TITLE = "Abnormal Weight Loss in Cats";
    public static final String CITATION_VCA_SOURCE = "VCA Animal Hospitals";
    public static final String CITATION_VCA_URL =
            "https://vcahospitals.com/know-your-pet/abnormal-weight-loss-in-cats";
    static final double SIGNIFICANT_LOSS_PCT = 10.0;
    /** A rise in "off" days only leads the recap once it covers a quarter of logged days. */
    static final double MIN_OFF_SHARE_FOR_HIGHLIGHT = 0.25;

    /** Royal Canin: during rapid growth kittens gain around 100 grams each week. */
    public static final String CITATION_KITTEN_TITLE = "What is a healthy kitten weight?";
    public static final String CITATION_KITTEN_SOURCE = "Royal Canin";
    public static final String CITATION_KITTEN_URL =
            "https://www.royalcanin.com/au/cats/kitten/how-to-help-your-kitten-stay-in-shape";

    private HealthRecap() {}

    /**
     * Extracts normal chip values per question group from checkin_tree.json.
     * Looks for "normal": true on each chip.
     */
    public static Map<String, String> extractNormalValues(String checkinTreeJson) {
        Map<String, String> map = new LinkedHashMap<>();
        // Sensible defaults
        for (String sig : SIGNALS) {
            map.put(sig, "normal");
        }
        if (checkinTreeJson == null || checkinTreeJson.trim().isEmpty()) {
            return map;
        }
        try {
            JSONObject root = new JSONObject(checkinTreeJson);
            JSONArray categories = root.optJSONArray("categories");
            if (categories != null) {
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject cat = categories.getJSONObject(i);
                    JSONArray groups = cat.optJSONArray("groups");
                    if (groups != null) {
                        for (int j = 0; j < groups.length(); j++) {
                            JSONObject group = groups.getJSONObject(j);
                            String groupId = group.optString("id");
                            JSONArray chips = group.optJSONArray("chips");
                            if (chips != null) {
                                for (int k = 0; k < chips.length(); k++) {
                                    JSONObject chip = chips.getJSONObject(k);
                                    if (chip.optBoolean("normal", false)) {
                                        map.put(groupId, chip.optString("value"));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep defaults
        }
        return map;
    }

    /**
     * Extracts concern pairs ("questionId:value") from rules with urgent or vet_visit severity
     * where minDays <= 1.
     */
    public static Set<String> extractConcernPairs(List<HealthRule> rules) {
        Set<String> concerns = new HashSet<>();
        if (rules != null) {
            for (HealthRule rule : rules) {
                if (rule.isUrgent() || rule.isVetVisit()) {
                    for (HealthSignal sig : rule.getSignals()) {
                        if (sig.getMinDays() <= 1) {
                            concerns.add(sig.getQuestionId() + ":" + sig.getValue());
                        }
                    }
                }
            }
        }
        return concerns;
    }

    /**
     * Computes the complete health recap for the given period.
     */
    public static RecapResult compute(Map<String, Map<String, List<String>>> answersByDate,
                                       List<WeightTrend.Point> weightPoints,
                                       int periodDays,
                                       LocalDate today,
                                       Map<String, String> normalValues,
                                       Set<String> concernPairs,
                                       List<HealthAlert> activeAlerts,
                                       boolean isKitten,
                                       String catName) {
        if (answersByDate == null) {
            answersByDate = Collections.emptyMap();
        }
        if (normalValues == null) {
            normalValues = extractNormalValues(null);
        }
        if (concernPairs == null) {
            concernPairs = Collections.emptySet();
        }
        if (today == null) {
            today = LocalDate.now();
        }
        if (periodDays <= 0) {
            periodDays = 7;
        }

        int totalDaysLogged = answersByDate.size();

        // 1. Current period and prior period consistency
        int daysLogged = 0;
        for (int i = 0; i < periodDays; i++) {
            LocalDate d = today.minusDays(i);
            if (answersByDate.containsKey(d.toString())) {
                daysLogged++;
            }
        }

        int priorDaysLogged = 0;
        for (int i = periodDays; i < 2 * periodDays; i++) {
            LocalDate d = today.minusDays(i);
            if (answersByDate.containsKey(d.toString())) {
                priorDaysLogged++;
            }
        }

        // Streak calculation
        int currentStreak = 0;
        LocalDate cursor = today;
        if (!answersByDate.containsKey(cursor.toString())) {
            cursor = today.minusDays(1);
        }
        while (answersByDate.containsKey(cursor.toString())) {
            currentStreak++;
            cursor = cursor.minusDays(1);
        }

        int longestStreak = 0;
        int run = 0;
        for (int i = periodDays - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            if (answersByDate.containsKey(d.toString())) {
                run++;
                if (run > longestStreak) {
                    longestStreak = run;
                }
            } else {
                run = 0;
            }
        }

        // Empty state / not enough data check
        boolean notEnoughData = totalDaysLogged < 7
                || (periodDays == 30 && daysLogged < 14)
                || (periodDays == 7 && totalDaysLogged <= 7 && priorDaysLogged == 0);

        // 2. Compute signals
        List<SignalSummary> summaries = new ArrayList<>();
        Map<String, Integer> offCountBySignal = new HashMap<>();
        Map<String, Integer> priorOffCountBySignal = new HashMap<>();
        Map<String, String> mostCommonOffValBySignal = new HashMap<>();

        for (String signalId : SIGNALS) {
            DayState[] days = new DayState[periodDays];
            int normalCount = 0;
            int offCount = 0;
            int concernCount = 0;
            int loggedCount = 0;
            Map<String, Integer> offValFreq = new HashMap<>();

            for (int i = 0; i < periodDays; i++) {
                // days[0] is oldest, days[periodDays-1] is today
                int daysAgo = periodDays - 1 - i;
                LocalDate d = today.minusDays(daysAgo);
                Map<String, List<String>> ans = answersByDate.get(d.toString());
                DayState state = classifyDay(signalId, ans, normalValues, concernPairs);
                days[i] = state;
                if (state != DayState.NOT_LOGGED) {
                    loggedCount++;
                }
                if (state == DayState.NORMAL) {
                    normalCount++;
                } else if (state == DayState.OFF) {
                    offCount++;
                    if (ans != null && ans.containsKey(signalId)) {
                        for (String val : ans.get(signalId)) {
                            offValFreq.put(val, offValFreq.getOrDefault(val, 0) + 1);
                        }
                    }
                } else if (state == DayState.CONCERN) {
                    concernCount++;
                    if (ans != null && ans.containsKey(signalId)) {
                        for (String val : ans.get(signalId)) {
                            offValFreq.put(val, offValFreq.getOrDefault(val, 0) + 1);
                        }
                    }
                }
            }

            // Find most common off value
            String mostCommonOff = null;
            int maxFreq = 0;
            for (Map.Entry<String, Integer> e : offValFreq.entrySet()) {
                if (e.getValue() > maxFreq) {
                    maxFreq = e.getValue();
                    mostCommonOff = e.getKey();
                }
            }
            mostCommonOffValBySignal.put(signalId, mostCommonOff);
            offCountBySignal.put(signalId, offCount + concernCount);

            // Prior period count
            int priorNormal = 0;
            int priorOff = 0;
            int priorConcern = 0;
            int priorLogged = 0;
            for (int i = periodDays; i < 2 * periodDays; i++) {
                LocalDate d = today.minusDays(i);
                Map<String, List<String>> ans = answersByDate.get(d.toString());
                DayState state = classifyDay(signalId, ans, normalValues, concernPairs);
                if (state != DayState.NOT_LOGGED) {
                    priorLogged++;
                }
                if (state == DayState.NORMAL) {
                    priorNormal++;
                } else if (state == DayState.OFF) {
                    priorOff++;
                } else if (state == DayState.CONCERN) {
                    priorConcern++;
                }
            }
            priorOffCountBySignal.put(signalId, priorOff + priorConcern);

            SignalSummary.Comparison comp;
            if (priorLogged == 0) {
                comp = SignalSummary.Comparison.NO_PRIOR_DATA;
            } else {
                int currBad = offCount + concernCount;
                int priorBad = priorOff + priorConcern;
                double currRate = loggedCount > 0 ? (double) currBad / loggedCount : 0.0;
                double priorRate = (double) priorBad / priorLogged;
                if (currBad > priorBad && (currRate - priorRate >= 0.10 || currBad >= priorBad + 2)) {
                    comp = SignalSummary.Comparison.MORE_OFF;
                } else if (currBad < priorBad && (priorRate - currRate >= 0.10 || priorBad >= currBad + 2)) {
                    comp = SignalSummary.Comparison.STEADIER;
                } else {
                    comp = SignalSummary.Comparison.ABOUT_SAME;
                }
            }

            summaries.add(new SignalSummary(signalId, days, normalCount, offCount, concernCount,
                    loggedCount, priorOff + priorConcern, priorLogged, comp));
        }

        // 3. Highlights generation
        List<RecapResult.Highlight> highlights = generateHighlights(
                summaries, offCountBySignal, priorOffCountBySignal, mostCommonOffValBySignal,
                periodDays, activeAlerts, catName);

        // 4. Weight computations and insight
        WeightComputation weightComp = computeWeight(weightPoints, today, periodDays, isKitten);

        return new RecapResult(daysLogged, periodDays, currentStreak, longestStreak,
                summaries, weightComp.latestWeight, weightComp.changeKg, weightComp.changePct,
                weightComp.monthlyRateKg, weightComp.insightText, weightComp.insightNote, weightComp.citationTitle,
                weightComp.citationSource, weightComp.citationUrl, highlights,
                notEnoughData, catName);
    }

    private static DayState classifyDay(String signalId,
                                        Map<String, List<String>> answers,
                                        Map<String, String> normalValues,
                                        Set<String> concernPairs) {
        if (answers == null || answers.isEmpty()) {
            return DayState.NOT_LOGGED;
        }

        // Check concern follow-ups associated with this signal
        if ("litter".equals(signalId)) {
            List<String> straining = answers.get("litter");
            if (straining != null && straining.contains("straining")) {
                return DayState.CONCERN;
            }
            List<String> urineOutput = answers.get("urine_output");
            if (urineOutput != null && urineOutput.contains("little_or_none")) {
                return DayState.CONCERN;
            }
        } else if ("appetite".equals(signalId)) {
            List<String> vomit = answers.get("vomiting");
            if (vomit != null && vomit.contains("repeated")) {
                return DayState.CONCERN;
            }
        }

        List<String> vals = answers.get(signalId);
        if (vals == null || vals.isEmpty()) {
            return DayState.NOT_LOGGED;
        }

        for (String val : vals) {
            if (concernPairs.contains(signalId + ":" + val)) {
                return DayState.CONCERN;
            }
        }

        String normalVal = normalValues.getOrDefault(signalId, "normal");
        if (vals.contains(normalVal)) {
            return DayState.NORMAL;
        }

        return DayState.OFF;
    }

    private static List<RecapResult.Highlight> generateHighlights(
            List<SignalSummary> summaries,
            Map<String, Integer> offCounts,
            Map<String, Integer> priorOffCounts,
            Map<String, String> mostCommonOff,
            int periodDays,
            List<HealthAlert> activeAlerts,
            String catName) {

        List<RecapResult.Highlight> candidates = new ArrayList<>();

        // Active alerts are not repeated here: the profile's health status line right above
        // the recap already shows them. Highlights summarise the logs themselves.
        for (SignalSummary s : summaries) {
            String sig = s.getSignalId();
            int logged = s.getLoggedCount();
            if (logged == 0) continue;

            int normal = s.getNormalCount();
            int off = s.getOffOrConcernCount();
            int priorOff = priorOffCounts.getOrDefault(sig, 0);
            int priorLogged = s.getPriorLoggedCount();

            double currRate = (double) off / logged;
            double priorRate = priorLogged > 0 ? (double) priorOff / priorLogged : 0.0;
            double diff = Math.abs(currRate - priorRate);
            String offPhrase = offPhrase(sig, mostCommonOff.get(sig));
            String label = capitalize(sig);

            if (s.getConcernCount() > 0) {
                String text = String.format(Locale.US, "%s on %d %s",
                        offPhrase, s.getConcernCount(), s.getConcernCount() == 1 ? "day" : "days");
                candidates.add(new RecapResult.Highlight(text, "alert", 500.0 + s.getConcernCount(), null));
            } else if (s.getComparison() == SignalSummary.Comparison.MORE_OFF && off > 0
                    && currRate >= MIN_OFF_SHARE_FOR_HIGHLIGHT) {
                String text = String.format(Locale.US, "%s on %d of %d days", offPhrase, off, logged);
                candidates.add(new RecapResult.Highlight(text, "trend_up", 100.0 + diff * 100.0, null));
            } else if (s.getComparison() == SignalSummary.Comparison.STEADIER && priorOff > off) {
                String text = String.format(Locale.US, "%s steadier than before", label);
                candidates.add(new RecapResult.Highlight(text, "trend_down", 50.0 + diff * 50.0, null));
            } else if (normal == logged) {
                String text = String.format(Locale.US, "%s normal every day", label);
                candidates.add(new RecapResult.Highlight(text, "check", 10.0 + (logged / 10.0), null));
            } else if (normal >= (int) (logged * 0.8)) {
                String text = String.format(Locale.US, "%s normal on %d of %d days", label, normal, logged);
                candidates.add(new RecapResult.Highlight(text, "check", 5.0 + (normal / 10.0), null));
            }
        }

        // Sort descending by magnitude
        candidates.sort((a, b) -> Double.compare(b.getChangeMagnitude(), a.getChangeMagnitude()));

        List<RecapResult.Highlight> results = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            results.add(candidates.get(i));
        }
        return results;
    }

    /** Plain words for a signal's off-normal answer, e.g. water + more -> "Drinking more". */
    public static String offPhrase(String signalId, String value) {
        String key = signalId + ":" + (value == null ? "" : value);
        switch (key) {
            case "appetite:less": return "Eating less";
            case "appetite:more": return "Eating more";
            case "water:less": return "Drinking less";
            case "water:more": return "Drinking more";
            case "litter:less": return "Fewer litter visits";
            case "litter:more": return "More litter visits";
            case "litter:straining": return "Straining in the litter box";
            case "energy:low": return "Low energy";
            case "energy:high": return "Extra playful";
            case "mood:grumpy": return "Grumpy";
            case "mood:clingy": return "Extra cuddly";
            default: return capitalize(signalId) + " off";
        }
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private static final class WeightComputation {
        Double latestWeight;
        Double changeKg;
        Double changePct;
        Double monthlyRateKg;
        String insightText;   // short headline for the card
        String insightNote;   // what the source says, shown in "More info"
        String citationTitle;
        String citationSource;
        String citationUrl;
    }

    private static WeightComputation computeWeight(List<WeightTrend.Point> weightPoints,
                                                   LocalDate today,
                                                   int periodDays,
                                                   boolean isKitten) {
        WeightComputation comp = new WeightComputation();
        if (weightPoints == null || weightPoints.isEmpty()) {
            return comp;
        }

        // Sort oldest to newest
        List<WeightTrend.Point> sorted = new ArrayList<>(weightPoints);
        sorted.sort(Comparator.comparingLong(p -> p.epochMillis));

        WeightTrend.Point latestPoint = sorted.get(sorted.size() - 1);
        comp.latestWeight = latestPoint.kg;

        if (sorted.size() < 2) {
            if (isKitten) {
                comp.insightText = "First weigh-in recorded";
                comp.insightNote = "Growing kittens gain around 100 g a week.";
                comp.citationTitle = CITATION_KITTEN_TITLE;
                comp.citationSource = CITATION_KITTEN_SOURCE;
                comp.citationUrl = CITATION_KITTEN_URL;
            }
            return comp;
        }

        // Filter points within the lookback window or use earliest available
        LocalDate windowStart = today.minusDays(Math.max(periodDays, 30));
        WeightTrend.Point basePoint = null;
        for (WeightTrend.Point pt : sorted) {
            LocalDate ptDate = java.time.Instant.ofEpochMilli(pt.epochMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (!ptDate.isBefore(windowStart)) {
                basePoint = pt;
                break;
            }
        }
        if (basePoint == null || basePoint == latestPoint) {
            basePoint = sorted.get(0);
        }

        double change = latestPoint.kg - basePoint.kg;
        LocalDate baseDate = java.time.Instant.ofEpochMilli(basePoint.epochMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        LocalDate latestDate = java.time.Instant.ofEpochMilli(latestPoint.epochMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        long daysBetween = ChronoUnit.DAYS.between(baseDate, latestDate);
        if (daysBetween <= 0) daysBetween = 1;
        double weeks = Math.max(1.0, daysBetween / 7.0);

        comp.changeKg = Math.round(change * 100.0) / 100.0;
        comp.changePct = Math.round(((change / basePoint.kg) * 100.0) * 10.0) / 10.0;
        comp.monthlyRateKg = Math.round(((change / weeks) * 4.33) * 100.0) / 100.0;

        int roundedWeeks = Math.max(1, (int) Math.round(weeks));

        if (isKitten) {
            long weeklyGainGrams = Math.round((change * 1000.0) / weeks);
            if (change > 0.05) {
                comp.insightText = String.format(Locale.US,
                        "Up %.1f kg in %d weeks · about %d g a week", change, roundedWeeks, weeklyGainGrams);
                comp.insightNote = "Growing kittens gain around 100 g a week.";
            } else {
                comp.insightText = String.format(Locale.US, "No gain in %d weeks", roundedWeeks);
                comp.insightNote = "Growing kittens gain around 100 g a week, so mention this to your vet.";
            }
            comp.citationTitle = CITATION_KITTEN_TITLE;
            comp.citationSource = CITATION_KITTEN_SOURCE;
            comp.citationUrl = CITATION_KITTEN_URL;
        } else if (change < -0.05) {
            double dropPct = Math.abs(comp.changePct);
            comp.insightText = String.format(Locale.US, "Down %.1f kg (%.1f%%) in %d weeks",
                    Math.abs(change), dropPct, roundedWeeks);
            comp.insightNote = dropPct > SIGNIFICANT_LOSS_PCT
                    ? "Vets treat losing more than 10% of body weight as significant. Book a check-up."
                    : "Vets treat losing more than 10% of body weight as significant, so keep weighing regularly.";
            comp.citationTitle = CITATION_VCA_TITLE;
            comp.citationSource = CITATION_VCA_SOURCE;
            comp.citationUrl = CITATION_VCA_URL;
        } else if (change > 0.05) {
            comp.insightText = String.format(Locale.US,
                    "Up %.1f kg (%.1f%%) in %d weeks", change, comp.changePct, roundedWeeks);
        } else {
            comp.insightText = String.format(Locale.US, "Steady over %d weeks", roundedWeeks);
        }

        return comp;
    }
}
