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
    public boolean isCareTasksSeeded(long catId) {
        return prefs.getBoolean(KEY_CARE_TASKS_SEEDED_PREFIX + catId, false);
    }

    public void setCareTasksSeeded(long catId) {
        prefs.edit().putBoolean(KEY_CARE_TASKS_SEEDED_PREFIX + catId, true).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
