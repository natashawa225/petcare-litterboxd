package com.litterboxd.app.data.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One day's check-in for one cat. Answers map a question id to the values selected for it:
 * a single-choice question has one value, a multi-select question can have several.
 */
public class DailyCheckin {
    private long id;
    private long catId;
    private String checkinDate;
    private long createdAt;
    private long updatedAt;
    private Map<String, List<String>> answers = new LinkedHashMap<>();

    public DailyCheckin() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCatId() { return catId; }
    public void setCatId(long catId) { this.catId = catId; }

    /** ISO day such as 2026-10-27. */
    public String getCheckinDate() { return checkinDate; }
    public void setCheckinDate(String checkinDate) { this.checkinDate = checkinDate; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, List<String>> getAnswers() { return answers; }
    public void setAnswers(Map<String, List<String>> answers) { this.answers = answers; }
}
