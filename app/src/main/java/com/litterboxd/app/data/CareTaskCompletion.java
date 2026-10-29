package com.litterboxd.app.data;

import android.content.Context;

import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.reminders.CareReminderScheduler;

/**
 * Ticking or unticking a care task, shared by the Daily and Tasks tabs so both write completions
 * the same way. Call on a worker thread.
 */
public final class CareTaskCompletion {

    private CareTaskCompletion() {}

    public static void setComplete(Context context, CareTaskDao dao, long taskId, boolean complete) {
        // Work on a fresh copy from the database, not the object a screen is drawing.
        CareTask task = dao.getTaskById(taskId);
        if (task == null) return; // deleted meanwhile
        long now = System.currentTimeMillis();
        if (complete) {
            if (!task.isCompleteNow(now)) { // one log per completion, even on a double tap
                task.setLastCompletedAt(now);
                dao.insertLog(taskId, now);
            }
        } else {
            // Undoing a tick also removes the log it wrote, so the history stays accurate.
            if (task.getLastCompletedAt() != null) {
                dao.deleteLog(taskId, task.getLastCompletedAt());
            }
            task.setLastCompletedAt(null);
        }
        dao.updateTask(task);
        CareReminderScheduler.schedule(context, task);
    }
}
