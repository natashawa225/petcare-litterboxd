package com.litterboxd.app.checkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A history-based follow-up rule stored with the decision tree.
 * Example: water answered "more" for 5 days in a row before today → also ask about urination.
 */
public final class HistoryTrigger {
    private final String id;
    private final String questionId;
    private final String value;
    private final int consecutiveDays;
    private final List<String> addFollowUps;

    public HistoryTrigger(String id, String questionId, String value, int consecutiveDays,
                          List<String> addFollowUps) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("History trigger needs an id");
        }
        if (questionId == null || questionId.isEmpty()) {
            throw new IllegalArgumentException("History trigger '" + id + "' needs a questionId");
        }
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("History trigger '" + id + "' needs a value");
        }
        if (consecutiveDays < 1) {
            throw new IllegalArgumentException("History trigger '" + id + "' needs consecutiveDays >= 1");
        }
        if (addFollowUps == null || addFollowUps.isEmpty()) {
            throw new IllegalArgumentException("History trigger '" + id + "' needs addFollowUps");
        }
        this.id = id;
        this.questionId = questionId;
        this.value = value;
        this.consecutiveDays = consecutiveDays;
        this.addFollowUps = Collections.unmodifiableList(new ArrayList<>(addFollowUps));
    }

    public String getId() {
        return id;
    }

    public String getQuestionId() {
        return questionId;
    }

    public String getValue() {
        return value;
    }

    public int getConsecutiveDays() {
        return consecutiveDays;
    }

    public List<String> getAddFollowUps() {
        return addFollowUps;
    }
}
