package com.litterboxd.app.data.model;

public class CareTaskLog {
    private long id;
    private long taskId;
    private long completedAt;

    public CareTaskLog() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }

    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
}
