package com.litterboxd.app.health;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HealthRule {
    public static final String SEVERITY_WATCH = "watch";
    public static final String SEVERITY_VET_VISIT = "vet_visit";
    public static final String SEVERITY_URGENT = "urgent";

    public static final String TYPE_SIGNAL = "signal";
    public static final String TYPE_BASELINE = "baseline";

    private final String id;
    private final String type;
    private final String severity;
    private final int windowDays;
    private final String match;
    private final List<HealthSignal> signals;
    private final String titleKey;
    private final String messageKey;
    private final List<String> articleIds;
    /** Ids of less specific rules hidden when this rule matches (avoids duplicate cards). */
    private final List<String> supersedes;
    /** If set, newest weight must be at least this many kg below an older reading in the lookback. */
    private final Double requiresWeightDropKg;
    private final int weightLookbackDays;
    /** Baseline rules only: which chip to compare (e.g. litter / more). */
    private final String baselineQuestionId;
    private final String baselineValue;
    private final int baselinePriorDays;
    private final int baselineMinPriorLogged;
    private final int baselineMinRecentLogged;
    private final double baselineMinRatio;
    /** Words typed into the live article search for this alert. */
    private final String searchQuery;
    /** Substrings that must appear in a page before it is shown (the signs that fire this alert). */
    private final List<String> searchTerms;

    public HealthRule(String id, String type, String severity, int windowDays, String match,
                      List<HealthSignal> signals, String titleKey, String messageKey,
                      List<String> articleIds, List<String> supersedes,
                      Double requiresWeightDropKg, int weightLookbackDays,
                      String baselineQuestionId, String baselineValue,
                      int baselinePriorDays, int baselineMinPriorLogged,
                      int baselineMinRecentLogged, double baselineMinRatio,
                      String searchQuery, List<String> searchTerms) {
        this.id = id;
        this.type = type == null ? TYPE_SIGNAL : type;
        this.severity = severity;
        this.windowDays = windowDays;
        this.match = match == null ? "all" : match;
        this.signals = Collections.unmodifiableList(new ArrayList<>(
                signals == null ? Collections.<HealthSignal>emptyList() : signals));
        this.titleKey = titleKey;
        this.messageKey = messageKey;
        this.articleIds = Collections.unmodifiableList(new ArrayList<>(
                articleIds == null ? Collections.<String>emptyList() : articleIds));
        this.supersedes = Collections.unmodifiableList(new ArrayList<>(
                supersedes == null ? Collections.<String>emptyList() : supersedes));
        this.requiresWeightDropKg = requiresWeightDropKg;
        this.weightLookbackDays = weightLookbackDays;
        this.baselineQuestionId = baselineQuestionId;
        this.baselineValue = baselineValue;
        this.baselinePriorDays = baselinePriorDays;
        this.baselineMinPriorLogged = baselineMinPriorLogged;
        this.baselineMinRecentLogged = baselineMinRecentLogged;
        this.baselineMinRatio = baselineMinRatio;
        this.searchQuery = searchQuery == null ? "" : searchQuery;
        this.searchTerms = Collections.unmodifiableList(new ArrayList<>(
                searchTerms == null ? Collections.<String>emptyList() : searchTerms));
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public int getWindowDays() { return windowDays; }
    public String getMatch() { return match; }
    public List<HealthSignal> getSignals() { return signals; }
    public String getTitleKey() { return titleKey; }
    public String getMessageKey() { return messageKey; }
    public List<String> getArticleIds() { return articleIds; }
    public List<String> getSupersedes() { return supersedes; }
    public Double getRequiresWeightDropKg() { return requiresWeightDropKg; }
    public int getWeightLookbackDays() { return weightLookbackDays; }
    public String getBaselineQuestionId() { return baselineQuestionId; }
    public String getBaselineValue() { return baselineValue; }
    public int getBaselinePriorDays() { return baselinePriorDays; }
    public int getBaselineMinPriorLogged() { return baselineMinPriorLogged; }
    public int getBaselineMinRecentLogged() { return baselineMinRecentLogged; }
    public double getBaselineMinRatio() { return baselineMinRatio; }
    public String getSearchQuery() { return searchQuery; }
    public List<String> getSearchTerms() { return searchTerms; }

    public boolean isUrgent() {
        return SEVERITY_URGENT.equals(severity);
    }

    public boolean isVetVisit() {
        return SEVERITY_VET_VISIT.equals(severity);
    }

    public boolean isBaseline() {
        return TYPE_BASELINE.equals(type);
    }

    /** Higher = more severe (urgent 2, vet 1, watch 0). */
    public int severityRank() {
        if (isUrgent()) return 2;
        if (isVetVisit()) return 1;
        return 0;
    }
}
