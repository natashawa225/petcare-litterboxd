package com.litterboxd.app.health;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a health recap calculation for a cat over 7 or 30 days.
 */
public final class RecapResult {

    public static final class Highlight {
        private final String text;
        private final String iconType; // "alert", "trend_up", "trend_down", "check", "star", "info"
        private final double changeMagnitude;
        private final String alertRuleId; // nullable

        public Highlight(String text, String iconType, double changeMagnitude, String alertRuleId) {
            this.text = text;
            this.iconType = iconType;
            this.changeMagnitude = changeMagnitude;
            this.alertRuleId = alertRuleId;
        }

        public String getText() {
            return text;
        }

        public String getIconType() {
            return iconType;
        }

        public double getChangeMagnitude() {
            return changeMagnitude;
        }

        public String getAlertRuleId() {
            return alertRuleId;
        }
    }

    private final int daysLogged;
    private final int daysInPeriod;
    private final int currentStreak;
    private final int longestStreak;
    private final List<SignalSummary> signals;
    private final Double weightLatest;
    private final Double weightChangeKg;
    private final Double weightChangePct;
    private final Double monthlyRateKg;
    private final String weightInsightText;
    private final String weightInsightNote;
    private final String weightCitationTitle;
    private final String weightCitationSource;
    private final String weightCitationUrl;
    private final List<Highlight> highlights;
    private final boolean notEnoughData;
    private final String catName;

    public RecapResult(int daysLogged,
                       int daysInPeriod,
                       int currentStreak,
                       int longestStreak,
                       List<SignalSummary> signals,
                       Double weightLatest,
                       Double weightChangeKg,
                       Double weightChangePct,
                       Double monthlyRateKg,
                       String weightInsightText,
                       String weightInsightNote,
                       String weightCitationTitle,
                       String weightCitationSource,
                       String weightCitationUrl,
                       List<Highlight> highlights,
                       boolean notEnoughData,
                       String catName) {
        this.daysLogged = daysLogged;
        this.daysInPeriod = daysInPeriod;
        this.currentStreak = currentStreak;
        this.longestStreak = longestStreak;
        this.signals = signals != null ? Collections.unmodifiableList(signals) : Collections.emptyList();
        this.weightLatest = weightLatest;
        this.weightChangeKg = weightChangeKg;
        this.weightChangePct = weightChangePct;
        this.monthlyRateKg = monthlyRateKg;
        this.weightInsightText = weightInsightText;
        this.weightInsightNote = weightInsightNote;
        this.weightCitationTitle = weightCitationTitle;
        this.weightCitationSource = weightCitationSource;
        this.weightCitationUrl = weightCitationUrl;
        this.highlights = highlights != null ? Collections.unmodifiableList(highlights) : Collections.emptyList();
        this.notEnoughData = notEnoughData;
        this.catName = catName;
    }

    public int getDaysLogged() {
        return daysLogged;
    }

    public int getDaysInPeriod() {
        return daysInPeriod;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public List<SignalSummary> getSignals() {
        return signals;
    }

    public Double getWeightLatest() {
        return weightLatest;
    }

    public Double getWeightChangeKg() {
        return weightChangeKg;
    }

    public Double getWeightChangePct() {
        return weightChangePct;
    }

    public Double getMonthlyRateKg() {
        return monthlyRateKg;
    }

    public String getWeightInsightText() {
        return weightInsightText;
    }

    /** What the cited source says about this change; null when there is nothing to add. */
    public String getWeightInsightNote() {
        return weightInsightNote;
    }

    public String getWeightCitationTitle() {
        return weightCitationTitle;
    }

    public String getWeightCitationSource() {
        return weightCitationSource;
    }

    public String getWeightCitationUrl() {
        return weightCitationUrl;
    }

    public List<Highlight> getHighlights() {
        return highlights;
    }

    public boolean isNotEnoughData() {
        return notEnoughData;
    }

    public String getCatName() {
        return catName;
    }
}
