package com.litterboxd.app.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.litterboxd.app.R;
import com.litterboxd.app.data.CareTaskSeeder;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.FeedingScheduleDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.model.WeightLog;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.reminders.CareReminderScheduler;
import com.litterboxd.app.ui.nav.BottomNavHelper;
import com.litterboxd.app.ui.onboarding.AddEditCatActivity;
import com.litterboxd.app.ui.splash.SplashActivity;
import com.litterboxd.app.util.AppExecutors;

/**
 * Profile tab: the person using Litterboxd (not a cat). Shows the owner's avatar and name
 * (edited in EditProfileActivity) and the app's settings: cats, notifications, about, sample data
 * and reset. Grew out of the former SettingsActivity; sample data and reset are unchanged.
 */
public class OwnerProfileActivity extends AppCompatActivity {

    private ImageView imgOwnerAvatar;
    private TextView tvOwnerName;
    private View rowManageCats;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private WeightLogDao weightLogDao;
    private FeedingScheduleDao feedingScheduleDao;
    private CareTaskDao careTaskDao;
    private LitterboxdDbHelper dbHelper;
    private Cat selectedCat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_profile);

        appPrefs = new AppPrefs(this);
        dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        weightLogDao = new WeightLogDao(dbHelper);
        feedingScheduleDao = new FeedingScheduleDao(dbHelper);
        careTaskDao = new CareTaskDao(dbHelper);

        imgOwnerAvatar = findViewById(R.id.imgOwnerAvatar);
        tvOwnerName = findViewById(R.id.tvOwnerName);
        BottomNavHelper.setup(this, (BottomNavigationView) findViewById(R.id.bottomNav), R.id.nav_profile);

        findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));
        imgOwnerAvatar.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));

        rowManageCats = bindRow(R.id.rowManageCats, R.string.profile_manage_cats, v -> {
            if (selectedCat != null) {
                Intent intent = new Intent(this, CatProfileActivity.class);
                intent.putExtra("cat_id", selectedCat.getId());
                startActivity(intent);
            }
        });
        bindRow(R.id.rowAddCat, R.string.profile_add_cat, v -> {
            Intent intent = new Intent(this, AddEditCatActivity.class);
            intent.putExtra(AddEditCatActivity.EXTRA_ADD_ANOTHER, true);
            startActivity(intent);
        });
        bindRow(R.id.rowNotifications, R.string.profile_notifications, v -> openNotificationSettings());
        bindRow(R.id.rowAbout, R.string.profile_about, v -> showAbout());
        bindRow(R.id.rowSampleData, R.string.btn_load_sample_data, v -> loadSampleData());
        View reset = bindRow(R.id.rowResetApp, R.string.btn_reset_app, v -> showResetConfirmationDialog());
        ((TextView) reset.findViewById(R.id.tvRowTitle)).setTextColor(ContextCompat.getColor(this, R.color.error_red));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Name, avatar, selected cat and notification permission can all change on other screens.
        String name = appPrefs.getOwnerName();
        tvOwnerName.setText(name == null || name.isEmpty() ? getString(R.string.default_owner_name) : name);
        OwnerAvatars.show(this, imgOwnerAvatar, appPrefs.getOwnerAvatar());
        setRowSummary(findViewById(R.id.rowNotifications), getString(
                NotificationManagerCompat.from(this).areNotificationsEnabled()
                        ? R.string.profile_notifications_on : R.string.profile_notifications_off));
        loadSelectedCat();
    }

    /** Back on a secondary tab returns to the Daily tab rather than leaving the app. */
    @Override
    public void onBackPressed() {
        BottomNavHelper.openTab(this, R.id.nav_daily);
    }

    private View bindRow(int rowId, int titleRes, View.OnClickListener listener) {
        View row = findViewById(rowId);
        ((TextView) row.findViewById(R.id.tvRowTitle)).setText(titleRes);
        row.setOnClickListener(listener);
        return row;
    }

    private static void setRowSummary(View row, CharSequence summary) {
        TextView tv = row.findViewById(R.id.tvRowSummary);
        tv.setText(summary);
        tv.setVisibility(summary == null ? View.GONE : View.VISIBLE);
    }

    private void loadSelectedCat() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            Cat cat = catDao.getCatById(appPrefs.getSelectedCatId());
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                selectedCat = cat;
                rowManageCats.setEnabled(cat != null);
                setRowSummary(rowManageCats, cat != null
                        ? getString(R.string.profile_manage_cats_summary, cat.getName())
                        : getString(R.string.daily_no_cat));
            });
        });
    }

    /** Opens the system screen for this app's notifications (implicit system Intent). */
    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        // Started directly: resolveActivity() can return null on Android 11+ without a <queries>
        // entry even though the system Settings screen exists.
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.msg_notifications_denied, Toast.LENGTH_LONG).show();
        }
    }

    private void showAbout() {
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            version = "";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.section_about)
                .setMessage(getString(R.string.about_version, version) + "\n\n" + getString(R.string.about_disclaimer))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void loadSampleData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            // Loading twice would duplicate Mochi and Biscuit.
            boolean hasMochi = false, hasBiscuit = false;
            for (Cat c : catDao.getAllCats()) {
                if ("Mochi".equals(c.getName())) hasMochi = true;
                if ("Biscuit".equals(c.getName())) hasBiscuit = true;
            }
            if (hasMochi && hasBiscuit) {
                AppExecutors.getInstance().mainThread().execute(() ->
                        Toast.makeText(this, R.string.msg_sample_data_already_loaded, Toast.LENGTH_SHORT).show());
                return;
            }

            // Mochi
            Cat mochi = new Cat();
            mochi.setName("Mochi");
            mochi.setApproxAgeYears(2);
            mochi.setApproxAgeMonths(4);
            mochi.setBreed("Domestic Shorthair");
            mochi.setSex("Female");
            mochi.setNeutered(true);
            mochi.setWeight(4.1);
            mochi.setCoatColor("Calico");
            mochi.setLifestyle("Indoor only");
            mochi.setCreatedAt(System.currentTimeMillis() - 86400000L * 30);
            long mochiId = catDao.insertCat(mochi);

            WeightLog w1 = new WeightLog();
            w1.setCatId(mochiId);
            w1.setWeight(4.0);
            w1.setDate(System.currentTimeMillis() - 86400000L * 15);
            w1.setNotes("Initial check");
            weightLogDao.insertWeightLog(w1);

            FeedingMeal m1 = new FeedingMeal();
            m1.setCatId(mochiId);
            m1.setMealLabel("Breakfast");
            m1.setMealTime("08:00"); // same 24-hour HH:mm format as the feeding schedule screen
            m1.setPortionGrams(45.0);
            feedingScheduleDao.insertFeedingMeal(m1);

            FeedingMeal m2 = new FeedingMeal();
            m2.setCatId(mochiId);
            m2.setMealLabel("Dinner");
            m2.setMealTime("18:00");
            m2.setPortionGrams(45.0);
            feedingScheduleDao.insertFeedingMeal(m2);

            // Biscuit
            Cat biscuit = new Cat();
            biscuit.setName("Biscuit");
            biscuit.setApproxAgeYears(5);
            biscuit.setApproxAgeMonths(0);
            biscuit.setBreed("Maine Coon");
            biscuit.setSex("Male");
            biscuit.setNeutered(true);
            biscuit.setWeight(6.8);
            biscuit.setCoatColor("Ginger");
            biscuit.setLifestyle("Indoor / Outdoor");
            biscuit.setCreatedAt(System.currentTimeMillis() - 86400000L * 60);
            long biscuitId = catDao.insertCat(biscuit);

            WeightLog w2 = new WeightLog();
            w2.setCatId(biscuitId);
            w2.setWeight(6.7);
            w2.setDate(System.currentTimeMillis() - 86400000L * 20);
            w2.setNotes("Healthy");
            weightLogDao.insertWeightLog(w2);

            FeedingMeal bm1 = new FeedingMeal();
            bm1.setCatId(biscuitId);
            bm1.setMealLabel("Morning Feed");
            bm1.setMealTime("07:30");
            bm1.setPortionGrams(70.0);
            feedingScheduleDao.insertFeedingMeal(bm1);

            CareTaskSeeder.seedIfEmpty(this, careTaskDao, mochiId);
            CareTaskSeeder.seedIfEmpty(this, careTaskDao, biscuitId);

            if (appPrefs.getSelectedCatId() == -1L) {
                appPrefs.setSelectedCatId(mochiId);
            }
            appPrefs.setOnboardingComplete(true);

            AppExecutors.getInstance().mainThread().execute(() -> {
                Toast.makeText(this, R.string.msg_sample_data_loaded, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showResetConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.msg_reset_app_title)
                .setMessage(R.string.msg_reset_app_message)
                .setPositiveButton(R.string.btn_yes, (dialog, which) -> {
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        // Children first (they reference cats), then cats, then photos and preferences.
                        for (CareTask task : careTaskDao.getAllTasks()) {
                            CareReminderScheduler.cancel(this, task.getId());
                        }
                        SQLiteDatabase db = dbHelper.getWritableDatabase();
                        db.delete(LitterboxdContract.CareTaskLogEntry.TABLE_NAME, null, null);
                        db.delete(LitterboxdContract.CareTaskEntry.TABLE_NAME, null, null);
                        db.delete(LitterboxdContract.FeedingScheduleEntry.TABLE_NAME, null, null);
                        db.delete(LitterboxdContract.WeightLogEntry.TABLE_NAME, null, null);
                        db.delete(LitterboxdContract.CatEntry.TABLE_NAME, null, null);
                        new FileStorageHelper(this).deleteAllCatPhotos();
                        appPrefs.clear();

                        AppExecutors.getInstance().mainThread().execute(() -> {
                            Intent intent = new Intent(this, SplashActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        });
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
