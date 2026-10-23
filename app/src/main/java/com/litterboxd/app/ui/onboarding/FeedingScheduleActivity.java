package com.litterboxd.app.ui.onboarding;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.FeedingScheduleDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.Validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Onboarding mode (default): step 3; Save/Skip/Back complete onboarding and open Home.
 * Edit mode (EXTRA_EDIT_MODE): loads the cat's meals; Save replaces them and Back just closes.
 */
public class FeedingScheduleActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    public static final String EXTRA_EDIT_MODE = "edit_mode";

    private Spinner spinnerMealsPerDay;
    private LinearLayout containerMealRows;
    private ProgressBar progressBar;
    private MaterialButton btnSaveSchedule, btnSkipSchedule;

    private long catId = -1L;
    private boolean editMode;
    private AppPrefs appPrefs;
    private FeedingScheduleDao feedingScheduleDao;
    private final List<MealRowView> mealRowViews = new ArrayList<>();

    private static class MealRowView {
        TextInputEditText etLabel;
        MaterialButton btnTime;
        TextInputEditText etPortion;
        TextInputLayout tilPortion;
        int hour = 8;
        int minute = 0;
    }

    private static final int DEFAULT_PORTION_GRAMS = 50;
    private static final String STATE_LABELS = "meal_labels";
    private static final String STATE_HOURS = "meal_hours";
    private static final String STATE_MINUTES = "meal_minutes";
    private static final String STATE_PORTIONS = "meal_portions";

    // Row values to re-apply the next time the rows are rebuilt (after rotation or a meal-count change).
    private String[] pendingLabels, pendingPortions;
    private int[] pendingHours, pendingMinutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feeding_schedule);

        catId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);
        appPrefs = new AppPrefs(this);
        feedingScheduleDao = new FeedingScheduleDao(LitterboxdDbHelper.getInstance(this));

        spinnerMealsPerDay = findViewById(R.id.spinnerMealsPerDay);
        containerMealRows = findViewById(R.id.containerMealRows);
        progressBar = findViewById(R.id.progressBar);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
        btnSkipSchedule = findViewById(R.id.btnSkipSchedule);

        if (savedInstanceState != null) {
            pendingLabels = savedInstanceState.getStringArray(STATE_LABELS);
            pendingHours = savedInstanceState.getIntArray(STATE_HOURS);
            pendingMinutes = savedInstanceState.getIntArray(STATE_MINUTES);
            pendingPortions = savedInstanceState.getStringArray(STATE_PORTIONS);
        }

        Integer[] mealCounts = {1, 2, 3, 4};
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mealCounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMealsPerDay.setAdapter(adapter);
        spinnerMealsPerDay.setSelection(1); // default 2 meals

        spinnerMealsPerDay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int count = (Integer) spinnerMealsPerDay.getSelectedItem();
                buildMealRows(count);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSaveSchedule.setOnClickListener(v -> saveScheduleAndFinish());
        btnSkipSchedule.setOnClickListener(v -> finishOnboarding());

        if (editMode) {
            findViewById(R.id.tvStepIndicator).setVisibility(View.GONE);
            btnSkipSchedule.setVisibility(View.GONE);
            if (savedInstanceState == null) loadExistingMeals();
        }
    }

    /** Edit mode: shows the cat's saved meals instead of the defaults. */
    private void loadExistingMeals() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<FeedingMeal> meals = feedingScheduleDao.getFeedingMealsForCat(catId);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (meals.isEmpty()) return; // keep the default rows
                int count = Math.min(meals.size(), spinnerMealsPerDay.getCount());
                pendingLabels = new String[count];
                pendingPortions = new String[count];
                pendingHours = new int[count];
                pendingMinutes = new int[count];
                for (int i = 0; i < count; i++) {
                    FeedingMeal meal = meals.get(i);
                    pendingLabels[i] = meal.getMealLabel();
                    pendingPortions[i] = String.valueOf(meal.getPortionGrams());
                    int[] time = parseTime(meal.getMealTime());
                    pendingHours[i] = time[0];
                    pendingMinutes[i] = time[1];
                }
                if (spinnerMealsPerDay.getSelectedItemPosition() == count - 1) {
                    buildMealRows(count); // selection unchanged, so the listener will not fire
                } else {
                    spinnerMealsPerDay.setSelection(count - 1);
                }
            });
        });
    }

    /** Parses "HH:mm" (also tolerates older "hh:mm AM/PM" values); falls back to 08:00. */
    private static int[] parseTime(String time) {
        try {
            String[] parts = time.trim().split("[: ]");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (parts.length > 2) {
                boolean pm = parts[2].equalsIgnoreCase("PM");
                if (hour == 12) hour = 0;
                if (pm) hour += 12;
            }
            return new int[]{hour, minute};
        } catch (RuntimeException e) {
            return new int[]{8, 0};
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingLabels == null) captureRows(); // rows not built yet: keep what is pending
        outState.putStringArray(STATE_LABELS, pendingLabels);
        outState.putIntArray(STATE_HOURS, pendingHours);
        outState.putIntArray(STATE_MINUTES, pendingMinutes);
        outState.putStringArray(STATE_PORTIONS, pendingPortions);
    }

    /** Copies what the user typed in the current rows into the pending arrays. */
    private void captureRows() {
        int n = mealRowViews.size();
        pendingLabels = new String[n];
        pendingPortions = new String[n];
        pendingHours = new int[n];
        pendingMinutes = new int[n];
        for (int i = 0; i < n; i++) {
            MealRowView row = mealRowViews.get(i);
            pendingLabels[i] = row.etLabel.getText() != null ? row.etLabel.getText().toString() : "";
            pendingPortions[i] = row.etPortion.getText() != null ? row.etPortion.getText().toString() : "";
            pendingHours[i] = row.hour;
            pendingMinutes[i] = row.minute;
        }
    }

    private void buildMealRows(int count) {
        if (pendingLabels == null && !mealRowViews.isEmpty()) {
            captureRows();
        }
        containerMealRows.removeAllViews();
        mealRowViews.clear();

        String[] defaultLabels = getResources().getStringArray(R.array.default_meal_labels);
        int paddingSmall = getResources().getDimensionPixelSize(R.dimen.spacing_small);
        int paddingMedium = getResources().getDimensionPixelSize(R.dimen.spacing_medium);

        for (int i = 0; i < count; i++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.VERTICAL);
            rowLayout.setPadding(0, paddingSmall, 0, paddingMedium);

            TextView tvHeader = new TextView(this);
            tvHeader.setText(getString(R.string.meal_header, i + 1));
            tvHeader.setTextColor(ContextCompat.getColor(this, R.color.sage_primary));
            tvHeader.setTextSize(16f);
            rowLayout.addView(tvHeader);

            TextInputLayout tilLabel = new TextInputLayout(this);
            tilLabel.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextInputEditText etLabel = new TextInputEditText(this);
            etLabel.setHint(R.string.hint_meal_label);
            etLabel.setText(i < defaultLabels.length ? defaultLabels[i] : getString(R.string.meal_header, i + 1));
            tilLabel.addView(etLabel);
            rowLayout.addView(tilLabel);

            MaterialButton btnTime = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnTime.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            int defaultHour = 8 + i * 4;
            if (defaultHour > 22) defaultHour = 20;
            final MealRowView mealRow = new MealRowView();
            mealRow.etLabel = etLabel;
            mealRow.btnTime = btnTime;
            mealRow.hour = defaultHour;
            mealRow.minute = 0;
            btnTime.setText(String.format(Locale.US, "%02d:00", defaultHour));
            btnTime.setOnClickListener(v -> {
                new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                    mealRow.hour = hourOfDay;
                    mealRow.minute = minute;
                    btnTime.setText(String.format(Locale.US, "%02d:%02d", hourOfDay, minute));
                }, mealRow.hour, mealRow.minute, false).show();
            });
            rowLayout.addView(btnTime);

            TextInputLayout tilPortion = new TextInputLayout(this);
            tilPortion.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextInputEditText etPortion = new TextInputEditText(this);
            etPortion.setHint(R.string.meal_portion_prefix);
            etPortion.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            etPortion.setText(String.valueOf(DEFAULT_PORTION_GRAMS));
            mealRow.etPortion = etPortion;
            mealRow.tilPortion = tilPortion;
            tilPortion.addView(etPortion);
            rowLayout.addView(tilPortion);

            if (pendingLabels != null && i < pendingLabels.length) {
                etLabel.setText(pendingLabels[i]);
                etPortion.setText(pendingPortions[i]);
                mealRow.hour = pendingHours[i];
                mealRow.minute = pendingMinutes[i];
                btnTime.setText(String.format(Locale.US, "%02d:%02d", mealRow.hour, mealRow.minute));
            }

            containerMealRows.addView(rowLayout);
            mealRowViews.add(mealRow);
        }
        pendingLabels = null;
        pendingPortions = null;
        pendingHours = null;
        pendingMinutes = null;
    }

    private void saveScheduleAndFinish() {
        // Read the rows on the UI thread; the worker thread only writes to the database.
        List<FeedingMeal> meals = new ArrayList<>();
        for (MealRowView row : mealRowViews) {
            Double portion = Validators.parseDecimal(row.etPortion.getText() != null ? row.etPortion.getText().toString() : "");
            if (portion == null || !Validators.isValidPortion(portion)) {
                row.tilPortion.setError(getString(R.string.error_invalid_portion));
                return;
            }
            row.tilPortion.setError(null);

            FeedingMeal meal = new FeedingMeal();
            meal.setCatId(catId);
            String label = row.etLabel.getText() != null ? row.etLabel.getText().toString().trim() : "";
            meal.setMealLabel(label.isEmpty() ? getString(R.string.meal_default_label) : label);
            meal.setMealTime(String.format(Locale.US, "%02d:%02d", row.hour, row.minute));
            meal.setPortionGrams(portion);
            meals.add(meal);
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSaveSchedule.setEnabled(false);

        AppExecutors.getInstance().diskIO().execute(() -> {
            if (catId != -1L) {
                feedingScheduleDao.deleteFeedingMealsForCat(catId);
                for (FeedingMeal meal : meals) {
                    feedingScheduleDao.insertFeedingMeal(meal);
                }
            }

            if (!editMode) appPrefs.setOnboardingComplete(true);

            AppExecutors.getInstance().mainThread().execute(() -> {
                progressBar.setVisibility(View.GONE);
                if (editMode) {
                    finish();
                } else {
                    navigateToHome();
                }
            });
        });
    }

    /**
     * Onboarding: the cat was already saved in step 2, so going back to step 1 would let the
     * user add the same cat twice. Back here behaves like Skip instead.
     * Edit mode: Back just closes without saving.
     */
    @Override
    public void onBackPressed() {
        if (editMode) {
            super.onBackPressed();
        } else {
            finishOnboarding();
        }
    }

    private void finishOnboarding() {
        appPrefs.setOnboardingComplete(true);
        navigateToHome();
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
