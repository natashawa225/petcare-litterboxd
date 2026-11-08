package com.litterboxd.app.data.session;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREF_NAME = "litterboxd_prefs";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_OWNER_NAME = "owner_name";
    private static final String KEY_SELECTED_CAT_ID = "selected_cat_id";
    private static final String KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked";
    private static final String KEY_CARE_TASKS_SEEDED_PREFIX = "care_tasks_seeded_";
    private static final String KEY_CARE_TASKS_PREPARED_PREFIX = "care_tasks_prepared_v2_";
    private static final String KEY_OWNER_AVATAR = "owner_avatar";
    private static final String KEY_DISMISSED_ALERTS_PREFIX = "dismissed_health_alerts_";
    private static final String KEY_TODAY_LIST_REMINDER_ON = "today_list_reminder_on";
    private static final String KEY_TODAY_LIST_REMINDER_HOUR = "today_list_reminder_hour";
    private static final String KEY_TODAY_LIST_REMINDER_MINUTE = "today_list_reminder_minute";
    private static final String KEY_REMINDER_SLOT_PREFIX = "reminder_slot_posted_";

    /** Owner avatar values: "bundled:<drawable name>", "file:<private file name>", or none. */
    public static final String AVATAR_BUNDLED_PREFIX = "bundled:";
    public static final String AVATAR_FILE_PREFIX = "file:";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOnboardingComplete() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public void setOnboardingComplete(boolean complete) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply();
    }

    public String getOwnerName() {
        return prefs.getString(KEY_OWNER_NAME, "");
    }

    public void setOwnerName(String name) {
        prefs.edit().putString(KEY_OWNER_NAME, name).apply();
    }

    public long getSelectedCatId() {
        return prefs.getLong(KEY_SELECTED_CAT_ID, -1L);
    }

    public void setSelectedCatId(long catId) {
        prefs.edit().putLong(KEY_SELECTED_CAT_ID, catId).apply();
    }

    public boolean isNotificationPermissionAsked() {
        return prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false);
    }

    public void setNotificationPermissionAsked(boolean asked) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, asked).apply();
    }

    /** True once the default care tasks were created for this cat, so deleted defaults are not re-added. */
    /** Set once CareTaskSetup has prepared this cat's tasks (placeholder clean-up + feeding sync). */
    public boolean isCareTasksPrepared(long catId) {
        return prefs.getBoolean(KEY_CARE_TASKS_PREPARED_PREFIX + catId, false);
    }

    public void setCareTasksPrepared(long catId) {
        prefs.edit().putBoolean(KEY_CARE_TASKS_PREPARED_PREFIX + catId, true).apply();
    }

    public boolean isCareTasksSeeded(long catId) {
        return prefs.getBoolean(KEY_CARE_TASKS_SEEDED_PREFIX + catId, false);
    }

    public void setCareTasksSeeded(long catId) {
        prefs.edit().putBoolean(KEY_CARE_TASKS_SEEDED_PREFIX + catId, true).apply();
    }

    /** The owner's avatar choice (see AVATAR_*_PREFIX), or null for the default avatar. */
    public String getOwnerAvatar() {
        return prefs.getString(KEY_OWNER_AVATAR, null);
    }

    public void setOwnerAvatar(String value) {
        if (value == null) {
            prefs.edit().remove(KEY_OWNER_AVATAR).apply();
        } else {
            prefs.edit().putString(KEY_OWNER_AVATAR, value).apply();
        }
    }

    /** Rule ids the owner dismissed for this cat (comma-separated). Cleared when the pattern stops matching. */
    public java.util.Set<String> getDismissedHealthAlertIds(long catId) {
        String raw = prefs.getString(KEY_DISMISSED_ALERTS_PREFIX + catId, "");
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (raw == null || raw.trim().isEmpty()) return ids;
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    public void dismissHealthAlert(long catId, String ruleId) {
        java.util.Set<String> ids = getDismissedHealthAlertIds(catId);
        ids.add(ruleId);
        prefs.edit().putString(KEY_DISMISSED_ALERTS_PREFIX + catId, joinIds(ids)).apply();
    }

    public void setDismissedHealthAlertIds(long catId, java.util.Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            prefs.edit().remove(KEY_DISMISSED_ALERTS_PREFIX + catId).apply();
        } else {
            prefs.edit().putString(KEY_DISMISSED_ALERTS_PREFIX + catId, joinIds(ids)).apply();
        }
    }

    private static String joinIds(java.util.Set<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    public boolean isTodayListReminderEnabled() {
        return prefs.getBoolean(KEY_TODAY_LIST_REMINDER_ON, false);
    }

    public void setTodayListReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TODAY_LIST_REMINDER_ON, enabled).apply();
    }

    /** Hour for the unfinished-today reminder. 8:00 pm until the owner picks one. */
    public int getTodayListReminderHour() {
        return prefs.getInt(KEY_TODAY_LIST_REMINDER_HOUR, 20);
    }

    public int getTodayListReminderMinute() {
        return prefs.getInt(KEY_TODAY_LIST_REMINDER_MINUTE, 0);
    }

    public void setTodayListReminderTime(int hour, int minute) {
        prefs.edit()
                .putInt(KEY_TODAY_LIST_REMINDER_HOUR, hour)
                .putInt(KEY_TODAY_LIST_REMINDER_MINUTE, minute)
                .apply();
    }

    /**
     * First caller for this reminder slot on this day returns true and may post.
     * A second alarm for the same task and time the same day returns false.
     */
    public boolean claimReminderSlot(String slotKey, String day) {
        synchronized (AppPrefs.class) {
            String key = KEY_REMINDER_SLOT_PREFIX + slotKey;
            String existing = prefs.getString(key, "");
            if (day.equals(existing)) return false;
            return prefs.edit().putString(key, day).commit();
        }
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
