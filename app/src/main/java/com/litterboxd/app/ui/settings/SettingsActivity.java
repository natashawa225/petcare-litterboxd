package com.litterboxd.app.ui.settings;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
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
import com.litterboxd.app.ui.splash.SplashActivity;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.Validators;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etSettingsOwnerName;
    private TextInputLayout tilSettingsOwnerName;
    private MaterialButton btnUpdateOwnerName, btnLoadSampleData, btnResetApp;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private WeightLogDao weightLogDao;
    private FeedingScheduleDao feedingScheduleDao;
    private CareTaskDao careTaskDao;
    private LitterboxdDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        appPrefs = new AppPrefs(this);
        dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        weightLogDao = new WeightLogDao(dbHelper);
        feedingScheduleDao = new FeedingScheduleDao(dbHelper);
        careTaskDao = new CareTaskDao(dbHelper);

        etSettingsOwnerName = findViewById(R.id.etSettingsOwnerName);
        tilSettingsOwnerName = findViewById(R.id.tilSettingsOwnerName);
        btnUpdateOwnerName = findViewById(R.id.btnUpdateOwnerName);
        btnLoadSampleData = findViewById(R.id.btnLoadSampleData);
        btnResetApp = findViewById(R.id.btnResetApp);

        String ownerName = appPrefs.getOwnerName();
        if (ownerName != null) {
            etSettingsOwnerName.setText(ownerName);
        }

        btnUpdateOwnerName.setOnClickListener(v -> {
            String name = etSettingsOwnerName.getText() != null ? etSettingsOwnerName.getText().toString().trim() : "";
            if (!Validators.isValidOwnerName(name)) {
                tilSettingsOwnerName.setError(getString(R.string.error_field_required));
                return;
            }
            tilSettingsOwnerName.setError(null);
            appPrefs.setOwnerName(name);
            Toast.makeText(this, R.string.msg_owner_name_updated, Toast.LENGTH_SHORT).show();
        });

        btnLoadSampleData.setOnClickListener(v -> loadSampleData());
        btnResetApp.setOnClickListener(v -> showResetConfirmationDialog());
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
