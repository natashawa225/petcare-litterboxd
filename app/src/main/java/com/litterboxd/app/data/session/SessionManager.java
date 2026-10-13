package com.litterboxd.app.data.session;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SessionManager wrapping SharedPreferences to manage user login state and guest flag.
 * Uses SharedPreferences for session persistence.
 */
public class SessionManager {
    private static final String PREF_NAME = "litterboxd_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_IS_GUEST = "is_guest";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void createLoginSession(long userId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_USER_ID, userId);
        editor.putBoolean(KEY_IS_GUEST, false);
        // Use apply() for asynchronous non-blocking write
        editor.apply();
    }

    public void createGuestSession() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_USER_ID, -1);
        editor.putBoolean(KEY_IS_GUEST, true);
        // commit() is used here because the splash/routing decision immediately follows
        // and needs synchronous persistence assurance before starting the next activity.
        editor.commit();
    }

    public void logout() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_IS_GUEST);
        editor.commit(); // immediate commit to ensure session is cleared before redirect
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public boolean isGuest() {
        return prefs.getBoolean(KEY_IS_GUEST, false);
    }

    public boolean isLoggedIn() {
        return getUserId() != -1 || isGuest();
    }
}
