package com.litterboxd.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.FeedingScheduleDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.model.WeightLog;
import com.litterboxd.app.data.session.AppPrefs;
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
    private LitterboxdDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        appPrefs = new AppPrefs(this);
        dbHelper = new LitterboxdDbHelper(this);
        catDao = new CatDao(dbHelper);
        weightLogDao = new WeightLogDao(dbHelper);
        feedingScheduleDao = new FeedingScheduleDao(dbHelper);

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
            Toast.makeText(this, "Owner name updated", Toast.LENGTH_SHORT).show();
        });

        btnLoadSampleData.setOnClickListener(v -> loadSampleData());
        btnResetApp.setOnClickListener(v -> showResetConfirmationDialog());
    }

    private void loadSampleData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
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
            m1.setMealTime("08:00 AM");
            m1.setPortionGrams(45.0);
            feedingScheduleDao.insertFeedingMeal(m1);

            FeedingMeal m2 = new FeedingMeal();
            m2.setCatId(mochiId);
            m2.setMealLabel("Dinner");
            m2.setMealTime("06:00 PM");
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
            bm1.setMealTime("07:30 AM");
            bm1.setPortionGrams(70.0);
            feedingScheduleDao.insertFeedingMeal(bm1);

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
                        appPrefs.clear();
                        dbHelper.onUpgrade(dbHelper.getWritableDatabase(), 2, 2);

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
