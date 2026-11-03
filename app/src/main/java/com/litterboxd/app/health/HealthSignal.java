package com.litterboxd.app.health;

/** One logged answer to count inside a rule window (e.g. water=more on ≥5 days). */
public final class HealthSignal {
    private final String questionId;
    private final String value;
    private final int minDays;

    public HealthSignal(String questionId, String value, int minDays) {
        this.questionId = questionId;
        this.value = value;
        this.minDays = minDays;
    }

    public String getQuestionId() { return questionId; }
    public String getValue() { return value; }
    public int getMinDays() { return minDays; }
}
