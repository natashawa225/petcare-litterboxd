package com.litterboxd.app.health;

/** One check-in answer that earns a calm "Worth a read" link on the Daily tab (not an alert). */
public final class HealthReading {
    private final String questionId;
    private final String value;
    private final String articleId;

    public HealthReading(String questionId, String value, String articleId) {
        this.questionId = questionId;
        this.value = value;
        this.articleId = articleId;
    }

    public String getQuestionId() { return questionId; }
    public String getValue() { return value; }
    public String getArticleId() { return articleId; }
}
