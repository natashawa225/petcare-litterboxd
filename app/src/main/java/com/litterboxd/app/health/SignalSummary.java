package com.litterboxd.app.health;

import java.util.Arrays;

/**
 * Summary of a single signal (Appetite, Water, Litter, Energy, Mood) over the recap period,
 * including daily states and comparison with the prior period of the same length.
 */
public final class SignalSummary {

    public enum Comparison {
        STEADIER,
        MORE_OFF,
        ABOUT_SAME,
        NO_PRIOR_DATA
    }

    private final String signalId;
    private final DayState[] days;
    private final int normalCount;
    private final int offCount;
    private final int concernCount;
    private final int loggedCount;
    private final int priorOffOrConcernCount;
    private final int priorLoggedCount;
    private final Comparison comparison;

    public SignalSummary(String signalId,
                         DayState[] days,
                         int normalCount,
                         int offCount,
                         int concernCount,
                         int loggedCount,
                         int priorOffOrConcernCount,
                         int priorLoggedCount,
                         Comparison comparison) {
        this.signalId = signalId;
        this.days = days != null ? Arrays.copyOf(days, days.length) : new DayState[0];
        this.normalCount = normalCount;
        this.offCount = offCount;
        this.concernCount = concernCount;
        this.loggedCount = loggedCount;
        this.priorOffOrConcernCount = priorOffOrConcernCount;
        this.priorLoggedCount = priorLoggedCount;
        this.comparison = comparison;
    }

    public String getSignalId() {
        return signalId;
    }

    public DayState[] getDays() {
        return Arrays.copyOf(days, days.length);
    }

    public int getNormalCount() {
        return normalCount;
    }

    public int getOffCount() {
        return offCount;
    }

    public int getConcernCount() {
        return concernCount;
    }

    public int getLoggedCount() {
        return loggedCount;
    }

    public int getPriorOffOrConcernCount() {
        return priorOffOrConcernCount;
    }

    public int getPriorLoggedCount() {
        return priorLoggedCount;
    }

    public Comparison getComparison() {
        return comparison;
    }

    public int getOffOrConcernCount() {
        return offCount + concernCount;
    }
}
