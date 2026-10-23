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

import androidx.appcompat.app.AppCompatActivity;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeedingScheduleActivity extends AppCompatActivity {

    private Spinner spinnerMealsPerDay;
    private LinearLayout containerMealRows;
    private ProgressBar progressBar;
    private MaterialButton btnSaveSchedule, btnSkipSchedule;

    private long catId = -1L;
    private AppPrefs appPrefs;
    private FeedingScheduleDao feedingScheduleDao;
    private final List<MealRowView> mealRowViews = new ArrayList<>();

    private static class MealRowView {
        TextInputEditText etLabel;
        MaterialButton btnTime;
        TextInputEditText etPortion;
        int hour = 8;
        int minute = 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feeding_schedule);

        catId = getIntent().getLongExtra("cat_id", -1L);
        appPrefs = new AppPrefs(this);
        feedingScheduleDao = new FeedingScheduleDao(new LitterboxdDbHelper(this));

        spinnerMealsPerDay = findViewById(R.id.spinnerMealsPerDay);
        containerMealRows = findViewById(R.id.containerMealRows);
        progressBar = findViewById(R.id.progressBar);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
        btnSkipSchedule = findViewById(R.id.btnSkipSchedule);

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
    }

    private void buildMealRows(int count) {
        containerMealRows.removeAllViews();
        mealRowViews.clear();

        String[] defaultLabels = {"Breakfast", "Lunch", "Dinner", "Snack"};

        for (int i = 0; i < count; i++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.VERTICAL);
            rowLayout.setPadding(0, 8, 0, 16);

            TextView tvHeader = new TextView(this);
            tvHeader.setText("Meal " + (i + 1));
            tvHeader.setTextColor(getResources().getColor(R.color.sage_primary));
            tvHeader.setTextSize(16f);
            rowLayout.addView(tvHeader);

            TextInputLayout tilLabel = new TextInputLayout(this);
            tilLabel.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextInputEditText etLabel = new TextInputEditText(this);
            etLabel.setHint("Meal Label (e.g. Breakfast)");
            etLabel.setText(i < defaultLabels.length ? defaultLabels[i] : "Meal " + (i + 1));
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
            etPortion.setHint("Portion (grams)");
            etPortion.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            etPortion.setText("50");
            mealRow.etPortion = etPortion;
            tilPortion.addView(etPortion);
            rowLayout.addView(tilPortion);

            containerMealRows.addView(rowLayout);
            mealRowViews.add(mealRow);
        }
    }

    private void saveScheduleAndFinish() {
        progressBar.setVisibility(View.VISIBLE);
        btnSaveSchedule.setEnabled(false);

        AppExecutors.getInstance().diskIO().execute(() -> {
            if (catId != -1L) {
                feedingScheduleDao.deleteFeedingMealsForCat(catId);
                for (MealRowView row : mealRowViews) {
                    FeedingMeal meal = new FeedingMeal();
                    meal.setCatId(catId);
                    String label = row.etLabel.getText() != null ? row.etLabel.getText().toString().trim() : "Meal";
                    meal.setMealLabel(label.isEmpty() ? "Meal" : label);
                    meal.setMealTime(row.btnTime.getText().toString());
                    double portion = 50.0;
                    try {
                        String pStr = row.etPortion.getText() != null ? row.etPortion.getText().toString().trim() : "";
                        portion = Double.parseDouble(pStr);
                    } catch (NumberFormatException ignored) {}
                    meal.setPortionGrams(portion);
                    feedingScheduleDao.insertFeedingMeal(meal);
                }
            }

            appPrefs.setOnboardingComplete(true);

            AppExecutors.getInstance().mainThread().execute(() -> {
                progressBar.setVisibility(View.GONE);
                navigateToHome();
            });
        });
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
