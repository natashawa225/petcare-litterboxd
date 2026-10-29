package com.litterboxd.app.data.model;

public class CareTask {
    private long id;
    private long catId;
    private String name;
    private String description;
    private TaskFrequency frequency;
    private Long dueAt;
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

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** One-off tasks: due date/time in millis, or null. Unused for daily/monthly tasks. */
    public Long getDueAt() { return dueAt; }
    public void setDueAt(Long dueAt) { this.dueAt = dueAt; }

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

    /** A one-off task whose due date/time has passed without being completed. */
    public boolean isOverdue(long nowMillis) {
        return frequency == TaskFrequency.ONCE && dueAt != null && dueAt < nowMillis
                && !isCompleteNow(nowMillis);
    }

    /**
     * When the reminder for this task should next fire, or null for no reminder.
     * Daily/monthly: the reminder clock time (see TaskFrequency). One-off: the due time, if a
     * time was set (reminderHour/Minute), it's still in the future and the task isn't done.
     */
    public Long nextReminderMillis(long nowMillis) {
        if (frequency == null || !hasReminder()) {
            return null;
        }
        boolean complete = isCompleteNow(nowMillis);
        if (frequency == TaskFrequency.ONCE) {
            return (dueAt == null || complete || dueAt <= nowMillis) ? null : dueAt;
        }
        return frequency.nextReminderMillis(reminderHour, reminderMinute, nowMillis, complete);
    }
}
