package com.litterboxd.app.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.model.WeightLog;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.ui.onboarding.AddEditCatActivity;
import com.litterboxd.app.ui.splash.SplashActivity;
import com.litterboxd.app.util.AgeUtils;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;
import com.litterboxd.app.util.Validators;

import java.io.File;
import java.util.List;

public class CatProfileActivity extends AppCompatActivity {

    private TextView tvProfileName, tvProfileLifeStage, tvProfileDetails;
    private ImageView imgProfilePhoto;
    private LinearLayout containerWeightLogs, containerFeedingMeals;
    private MaterialButton btnAddWeight, btnEditCat, btnDeleteCat;

    private long catId = -1L;
    private CatDao catDao;
    private WeightLogDao weightLogDao;
    private FeedingScheduleDao feedingScheduleDao;
    private FileStorageHelper fileStorageHelper;
    private AppPrefs appPrefs;
    private Cat currentCat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cat_profile);

        catId = getIntent().getLongExtra("cat_id", -1L);
        LitterboxdDbHelper dbHelper = new LitterboxdDbHelper(this);
        catDao = new CatDao(dbHelper);
        weightLogDao = new WeightLogDao(dbHelper);
        feedingScheduleDao = new FeedingScheduleDao(dbHelper);
        fileStorageHelper = new FileStorageHelper(this);
        appPrefs = new AppPrefs(this);

        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileLifeStage = findViewById(R.id.tvProfileLifeStage);
        tvProfileDetails = findViewById(R.id.tvProfileDetails);
        imgProfilePhoto = findViewById(R.id.imgProfilePhoto);
        containerWeightLogs = findViewById(R.id.containerWeightLogs);
        containerFeedingMeals = findViewById(R.id.containerFeedingMeals);
        btnAddWeight = findViewById(R.id.btnAddWeight);
        btnEditCat = findViewById(R.id.btnEditCat);
        btnDeleteCat = findViewById(R.id.btnDeleteCat);

        btnAddWeight.setOnClickListener(v -> showAddWeightDialog());
        btnEditCat.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditCatActivity.class);
            intent.putExtra("cat_id", catId);
            startActivity(intent);
        });
        btnDeleteCat.setOnClickListener(v -> showDeleteConfirmationDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCatProfile();
    }

    private void loadCatProfile() {
        if (catId == -1L) {
            finish();
            return;
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            Cat loaded = catDao.getCatById(catId);
            List<WeightLog> weightLogs = weightLogDao.getWeightLogsForCat(catId);
            List<FeedingMeal> feedingMeals = feedingScheduleDao.getFeedingMealsForCat(catId);

            AppExecutors.getInstance().mainThread().execute(() -> {
                currentCat = loaded;
                if (currentCat != null) {
                    tvProfileName.setText(currentCat.getName());
                    String lifeStage = AgeUtils.calculateLifeStage(currentCat.getBirthday(), currentCat.getApproxAgeYears(), currentCat.getApproxAgeMonths());
                    tvProfileLifeStage.setText(getString(R.string.label_life_stage, lifeStage));

                    String ageStr = AgeUtils.getAgeDisplay(currentCat.getBirthday(), currentCat.getApproxAgeYears(), currentCat.getApproxAgeMonths());
                    String details = "Breed: " + currentCat.getBreed() + "\nAge: " + ageStr + " | Weight: " + currentCat.getWeight() + " kg\nSex: " + currentCat.getSex() + " | Neutered: " + (currentCat.isNeutered() ? "Yes" : "No") + "\nCoat: " + currentCat.getCoatColor() + " | Lifestyle: " + currentCat.getLifestyle();
                    tvProfileDetails.setText(details);

                    if (currentCat.getPhotoPath() != null && !currentCat.getPhotoPath().isEmpty()) {
                        File file = fileStorageHelper.getInternalImageFile(currentCat.getPhotoPath());
                        if (file.exists()) {
                            imgProfilePhoto.setImageURI(Uri.fromFile(file));
                        } else {
                            imgProfilePhoto.setImageResource(R.drawable.ic_cat_face);
                        }
                    } else {
                        imgProfilePhoto.setImageResource(R.drawable.ic_cat_face);
                    }

                    populateWeightLogs(weightLogs);
                    populateFeedingMeals(feedingMeals);
                } else {
                    finish();
                }
            });
        });
    }

    private void populateWeightLogs(List<WeightLog> logs) {
        containerWeightLogs.removeAllViews();
        if (logs.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No weight logs recorded yet.");
            tv.setTextColor(getResources().getColor(R.color.text_dark_gray));
            containerWeightLogs.addView(tv);
            return;
        }

        for (WeightLog log : logs) {
            TextView tv = new TextView(this);
            String dateStr = DateFormatUtils.formatDate(log.getDate());
            String text = "• " + log.getWeight() + " kg on " + dateStr;
            if (log.getNotes() != null && !log.getNotes().isEmpty()) {
                text += " (" + log.getNotes() + ")";
            }
            tv.setText(text);
            tv.setTextColor(getResources().getColor(R.color.black));
            tv.setPadding(0, 4, 0, 4);
            containerWeightLogs.addView(tv);
        }
    }

    private void populateFeedingMeals(List<FeedingMeal> meals) {
        containerFeedingMeals.removeAllViews();
        if (meals.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No feeding schedule configured.");
            tv.setTextColor(getResources().getColor(R.color.text_dark_gray));
            containerFeedingMeals.addView(tv);
            return;
        }

        for (FeedingMeal meal : meals) {
            TextView tv = new TextView(this);
            String text = "• " + meal.getMealLabel() + " at " + meal.getMealTime() + " — " + meal.getPortionGrams() + "g";
            tv.setText(text);
            tv.setTextColor(getResources().getColor(R.color.black));
            tv.setPadding(0, 4, 0, 4);
            containerFeedingMeals.addView(tv);
        }
    }

    private void showAddWeightDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        TextInputLayout tilWeight = new TextInputLayout(this);
        tilWeight.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextInputEditText etWeight = new TextInputEditText(this);
        etWeight.setHint(R.string.prompt_weight_grams);
        etWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tilWeight.addView(etWeight);
        layout.addView(tilWeight);

        TextInputLayout tilNotes = new TextInputLayout(this);
        tilNotes.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextInputEditText etNotes = new TextInputEditText(this);
        etNotes.setHint(R.string.prompt_weight_notes);
        tilNotes.addView(etNotes);
        layout.addView(tilNotes);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_add_weight_title)
                .setView(layout)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String wStr = etWeight.getText() != null ? etWeight.getText().toString() : "";
                    String notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";
                    Double weight = Validators.parseDecimal(wStr);
                    if (weight == null || !Validators.isValidWeight(weight)) {
                        Toast.makeText(this, R.string.error_invalid_weight, Toast.LENGTH_LONG).show();
                        return;
                    }
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        WeightLog log = new WeightLog();
                        log.setCatId(catId);
                        log.setWeight(weight);
                        log.setDate(System.currentTimeMillis());
                        log.setNotes(notes);
                        weightLogDao.insertWeightLog(log);

                        // Re-read the cat here instead of using currentCat, which belongs to the UI thread.
                        Cat cat = catDao.getCatById(catId);
                        if (cat != null) {
                            cat.setWeight(weight);
                            catDao.updateCat(cat);
                        }

                        AppExecutors.getInstance().mainThread().execute(this::loadCatProfile);
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showDeleteConfirmationDialog() {
        String catName = currentCat != null ? currentCat.getName() : "this cat";
        new AlertDialog.Builder(this)
                .setTitle(R.string.msg_delete_cat_title)
                .setMessage(getString(R.string.msg_delete_cat_message, catName))
                .setPositiveButton(R.string.btn_yes, (dialog, which) -> {
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        catDao.deleteCat(catId);
                        List<Cat> remaining = catDao.getAllCats();
                        if (!remaining.isEmpty()) {
                            if (appPrefs.getSelectedCatId() == catId) {
                                appPrefs.setSelectedCatId(remaining.get(0).getId());
                            }
                        } else {
                            appPrefs.setSelectedCatId(-1L);
                            appPrefs.setOnboardingComplete(false);
                        }

                        AppExecutors.getInstance().mainThread().execute(() -> {
                            // No cats left: let Splash route back to Welcome/onboarding.
                            Class<?> target = remaining.isEmpty() ? SplashActivity.class : HomeActivity.class;
                            Intent intent = new Intent(this, target);
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
