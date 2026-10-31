package com.litterboxd.app.ui.nav;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.litterboxd.app.R;
import com.litterboxd.app.ui.diary.DiaryActivity;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.ui.profile.OwnerProfileActivity;
import com.litterboxd.app.ui.tasks.CareTasksActivity;

/**
 * Shared behaviour of the bottom navigation bar on the four tab Activities (Daily, Tasks, Diary,
 * Profile).
 *
 * Each tab is its own Activity. Switching uses FLAG_ACTIVITY_REORDER_TO_FRONT, so every tab exists
 * at most once in the back stack: an already-open tab is brought forward with its scroll position
 * and loaded data intact instead of a duplicate being created. Back on Tasks/Diary/Profile returns to
 * Daily; Back on Daily closes all tabs (see HomeActivity), so it never returns to onboarding.
 */
public final class BottomNavHelper {

    private BottomNavHelper() {}

    /** Marks {@code selectedItemId} as the current tab and handles taps on the others. */
    public static void setup(Activity activity, BottomNavigationView nav, int selectedItemId) {
        nav.setSelectedItemId(selectedItemId);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) {
                return true;
            }
            openTab(activity, id);
            // Keep this tab's own item highlighted; the target tab highlights itself.
            return false;
        });
        // Tapping the current tab again does nothing (no reload, no duplicate).
        nav.setOnItemReselectedListener(item -> { });
    }

    /** Brings the tab for {@code itemId} to the front, creating it only if it isn't open yet. */
    public static void openTab(Activity activity, int itemId) {
        openTab(activity, intentFor(activity, itemId));
    }

    /** Same as {@link #openTab(Activity, int)} for an Intent that may carry extras. */
    public static void openTab(Activity activity, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);
        // Tabs swap in place, like a single screen, instead of sliding like a new page.
        activity.overridePendingTransition(0, 0);
    }

    public static Intent intentFor(Activity activity, int itemId) {
        Class<?> target;
        if (itemId == R.id.nav_tasks) {
            target = CareTasksActivity.class;
        } else if (itemId == R.id.nav_diary) {
            target = DiaryActivity.class;
        } else if (itemId == R.id.nav_profile) {
            target = OwnerProfileActivity.class;
        } else {
            target = HomeActivity.class;
        }
        return new Intent(activity, target);
    }
}
