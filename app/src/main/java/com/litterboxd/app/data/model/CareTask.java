package com.litterboxd.app.data.model;

public class CareTask {
    private long id;
    private long catId;
    private String name;
    private TaskFrequency frequency;
    private Integer reminderHour;
    private Integer reminderMinute;
    private Long lastCompletedAt;
    private boolean isDefault;
    private int sortOrder;
    private long createdAt;

    public CareTask() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCatId() { return catId; }
    public void setCatId(long catId) { this.catId = catId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public TaskFrequency getFrequency() { return frequency; }
    public void setFrequency(TaskFrequency frequency) { this.frequency = frequency; }

    public Integer getReminderHour() { return reminderHour; }
    public void setReminderHour(Integer reminderHour) { this.reminderHour = reminderHour; }

    public Integer getReminderMinute() { return reminderMinute; }
    public void setReminderMinute(Integer reminderMinute) { this.reminderMinute = reminderMinute; }

    public Long getLastCompletedAt() { return lastCompletedAt; }
    public void setLastCompletedAt(Long lastCompletedAt) { this.lastCompletedAt = lastCompletedAt; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean hasReminder() {
        return reminderHour != null && reminderMinute != null;
    }

    public boolean isCompleteNow(long nowMillis) {
        return frequency != null && frequency.isCompleteForCurrentPeriod(lastCompletedAt, nowMillis);
    }
}
