package com.litterboxd.app.ui.profile;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.dao.FeedingScheduleDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.FeedingMeal;
import com.litterboxd.app.data.model.WeightLog;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.health.HealthAlert;
import com.litterboxd.app.health.HealthContent;
import com.litterboxd.app.health.HealthRule;
import com.litterboxd.app.health.HealthRuleEvaluator;
import com.litterboxd.app.health.WeightPoints;
import com.litterboxd.app.ui.checkin.CheckInContent;
import com.litterboxd.app.ui.health.HealthAlertsActivity;
import com.litterboxd.app.reminders.CareReminderScheduler;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.ui.onboarding.AddEditCatActivity;
import com.litterboxd.app.ui.onboarding.FeedingScheduleActivity;
import com.litterboxd.app.ui.splash.SplashActivity;
import com.litterboxd.app.util.AgeUtils;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;
import com.litterboxd.app.util.Validators;

import java.time.LocalDate;
import java.util.Calendar;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CatProfileActivity extends AppCompatActivity {

    private static final int WEIGHT_PREVIEW_LIMIT = 3;

    private TextView tvProfileName, tvProfileDetails, tvProfileAgePill, tvWeightChartHint;
    private TextView tvHealthStatusIcon, tvHealthStatusTitle, tvHealthStatusSub;
    private TextView tvStatWeight, tvStatWeightPill, tvStatLogged, tvStatLoggedPill;
    private TextView tvStatLifestyle, tvStatLifestylePill;
    private View cardHealthStatus;
    private ImageView imgProfilePhoto;
    private LinearLayout containerWeightLogs, containerFeedingMeals;
    private MaterialButton btnAddWeight, btnWeightHistory;
    private WeightChartView weightChart;
    private AlertDialog weightHistoryDialog;

    private long catId = -1L;
    private CatDao catDao;
    private WeightLogDao weightLogDao;
    private FeedingScheduleDao feedingScheduleDao;
    private CareTaskDao careTaskDao;
    private DiaryDao diaryDao;
    private FileStorageHelper fileStorageHelper;
    private AppPrefs appPrefs;
    private Cat currentCat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cat_profile);

        catId = getIntent().getLongExtra("cat_id", -1L);
        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        weightLogDao = new WeightLogDao(dbHelper);
        feedingScheduleDao = new FeedingScheduleDao(dbHelper);
        careTaskDao = new CareTaskDao(dbHelper);
        diaryDao = new DiaryDao(dbHelper);
        fileStorageHelper = new FileStorageHelper(this);
        appPrefs = new AppPrefs(this);

        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileDetails = findViewById(R.id.tvProfileDetails);
        tvProfileAgePill = findViewById(R.id.tvProfileAgePill);
        cardHealthStatus = findViewById(R.id.cardHealthStatus);
        tvHealthStatusIcon = findViewById(R.id.tvHealthStatusIcon);
        tvHealthStatusTitle = findViewById(R.id.tvHealthStatusTitle);
        tvHealthStatusSub = findViewById(R.id.tvHealthStatusSub);
        tvStatWeight = findViewById(R.id.tvStatWeight);
        tvStatWeightPill = findViewById(R.id.tvStatWeightPill);
        tvStatLogged = findViewById(R.id.tvStatLogged);
        tvStatLoggedPill = findViewById(R.id.tvStatLoggedPill);
        tvStatLifestyle = findViewById(R.id.tvStatLifestyle);
        tvStatLifestylePill = findViewById(R.id.tvStatLifestylePill);
        imgProfilePhoto = findViewById(R.id.imgProfilePhoto);
        containerWeightLogs = findViewById(R.id.containerWeightLogs);
        containerFeedingMeals = findViewById(R.id.containerFeedingMeals);
        weightChart = findViewById(R.id.weightChart);
        tvWeightChartHint = findViewById(R.id.tvWeightChartHint);
        btnAddWeight = findViewById(R.id.btnAddWeight);
        btnWeightHistory = findViewById(R.id.btnWeightHistory);

        btnAddWeight.setOnClickListener(v -> showAddWeightDialog());
        btnWeightHistory.setOnClickListener(v -> showWeightHistoryDialog());
        View.OnClickListener editCat = v -> {
            Intent intent = new Intent(this, AddEditCatActivity.class);
            intent.putExtra("cat_id", catId);
            startActivity(intent);
        };
        // Tapping the cat's picture is a shortcut to change it (same edit screen and photo picker).
        imgProfilePhoto.setOnClickListener(editCat);
        // ⋮ menu: Edit profile / Delete cat.
        findViewById(R.id.btnProfileMenu).setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, v);
            menu.getMenu().add(0, 1, 0, R.string.profile_menu_edit);
            menu.getMenu().add(0, 2, 1, R.string.profile_menu_delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) editCat.onClick(v);
                else showDeleteConfirmationDialog();
                return true;
            });
            menu.show();
        });
        cardHealthStatus.setOnClickListener(v -> {
            Intent intent = new Intent(this, HealthAlertsActivity.class);
            intent.putExtra(HealthAlertsActivity.EXTRA_CAT_ID, catId);
            startActivity(intent);
        });
        findViewById(R.id.btnEditSchedule).setOnClickListener(v -> {
            Intent intent = new Intent(this, FeedingScheduleActivity.class);
            intent.putExtra(FeedingScheduleActivity.EXTRA_CAT_ID, catId);
            intent.putExtra(FeedingScheduleActivity.EXTRA_EDIT_MODE, true);
            startActivity(intent);
        });
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
            final ProfileHealth health = loadHealth(weightLogs);

            AppExecutors.getInstance().mainThread().execute(() -> {
                currentCat = loaded;
                if (currentCat != null) {
                    tvProfileName.setText(currentCat.getName());
                    tvProfileDetails.setText(joinDetails(currentCat.getSex(),
                            currentCat.getBreed(), currentCat.getCoatColor()));
                    String lifeStage = AgeUtils.calculateLifeStage(currentCat.getBirthday(), currentCat.getApproxAgeYears(), currentCat.getApproxAgeMonths());
                    String ageStr = AgeUtils.getAgeDisplay(currentCat.getBirthday(), currentCat.getApproxAgeYears(), currentCat.getApproxAgeMonths());
                    tvProfileAgePill.setText(getString(R.string.profile_age_pill, ageStr, lifeStage));
                    tvProfileAgePill.setBackground(pill(R.color.blush_pink, false));
                    bindStats(weightLogs, health);
                    bindHealthStatus(health);

                    fileStorageHelper.showCatPhoto(imgProfilePhoto, currentCat.getPhotoPath());
                    imgProfilePhoto.setContentDescription(getString(R.string.cd_change_cat_photo, currentCat.getName()));

                    populateWeightLogs(weightLogs);
                    populateFeedingMeals(feedingMeals);
                } else {
                    finish();
                }
            });
        });
    }

    // ---- Header: health status + stats ----

    /** What the header needs from the health rules and check-ins (built off the UI thread). */
    private static final class ProfileHealth {
        HealthRule topRule;      // most severe current alert, or null
        String lastCheckin;      // yyyy-MM-dd, or null if never logged
        int loggedThisWeek;      // days with a check-in in the last 7 days (incl. today)
    }

    /** Background thread. Same rules and dismissals as the Health alerts screen. */
    private ProfileHealth loadHealth(List<WeightLog> weightLogs) {
        ProfileHealth health = new ProfileHealth();
        LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(this);
        Map<String, Map<String, List<String>>> answers =
                new DailyCheckinDao(db).getAnswersByDateForCat(catId);
        LocalDate today = LocalDate.now();
        for (String day : answers.keySet()) {
            if (health.lastCheckin == null || day.compareTo(health.lastCheckin) > 0) {
                health.lastCheckin = day;
            }
            try {
                LocalDate date = LocalDate.parse(day);
                if (!date.isAfter(today) && date.isAfter(today.minusDays(7))) health.loggedThisWeek++;
            } catch (RuntimeException ignored) {
                // skip a malformed date key
            }
        }
        HealthContent content = HealthContent.load(this);
        if (content != null) {
            List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(content.getRules(),
                    content.getArticlesById(), answers, today,
                    appPrefs.getDismissedHealthAlertIds(catId), WeightPoints.fromLogs(weightLogs));
            if (!alerts.isEmpty()) health.topRule = alerts.get(0).getRule(); // sorted by severity
        }
        return health;
    }

    private void bindHealthStatus(ProfileHealth health) {
        int fill;
        int titleColour;
        String icon;
        String title;
        String sub;
        HealthRule rule = health.topRule;
        if (rule != null) {
            String alertTitle = CheckInContent.text(this, rule.getTitleKey(), currentCat.getName());
            sub = getString(R.string.profile_status_sub_alerts, alertTitle);
            if (rule.isUrgent()) {
                fill = R.color.pastel_coral; titleColour = R.color.error_red; icon = "🚨";
                title = getString(R.string.profile_status_urgent);
            } else if (rule.isVetVisit()) {
                fill = R.color.card_clay; titleColour = R.color.muted_pinkish_brown; icon = "🩺";
                title = getString(R.string.profile_status_vet);
            } else {
                fill = R.color.card_oat; titleColour = R.color.muted_pinkish_brown; icon = "👀";
                title = getString(R.string.profile_status_watch);
            }
        } else if (health.lastCheckin != null) {
            fill = R.color.card_sage; titleColour = R.color.sage_primary; icon = "🌿";
            title = getString(R.string.profile_status_normal);
            sub = getString(R.string.profile_status_sub_last, formatIsoDate(health.lastCheckin));
        } else {
            fill = R.color.card_oat; titleColour = R.color.sage_primary; icon = "📝";
            title = getString(R.string.profile_status_none);
            sub = getString(R.string.profile_status_sub_none);
        }
        cardHealthStatus.setBackground(pill(fill, true));
        tvHealthStatusIcon.setText(icon);
        tvHealthStatusTitle.setText(title);
        tvHealthStatusTitle.setTextColor(ContextCompat.getColor(this, titleColour));
        tvHealthStatusSub.setText(sub);
        cardHealthStatus.setContentDescription(title + ". " + sub);
    }

    private void bindStats(List<WeightLog> logs, ProfileHealth health) {
        // Weight: newest log (falls back to the profile weight) and how it moved since the last log.
        double latest = logs.isEmpty() ? currentCat.getWeight() : logs.get(0).getWeight();
        tvStatWeight.setText(latest > 0
                ? getString(R.string.profile_weight_value, formatKg(latest))
                : getString(R.string.profile_unknown));
        if (logs.size() >= 2) {
            double diff = logs.get(0).getWeight() - logs.get(1).getWeight(); // newest first
            if (Math.abs(diff) < 0.05) {
                setPill(tvStatWeightPill, getString(R.string.profile_weight_steady), R.color.card_sage);
            } else if (diff < 0) {
                setPill(tvStatWeightPill, getString(R.string.profile_weight_down, formatKg(-diff)), R.color.card_clay);
            } else {
                setPill(tvStatWeightPill, getString(R.string.profile_weight_up, formatKg(diff)), R.color.card_oat);
            }
        } else {
            setPill(tvStatWeightPill, getString(logs.isEmpty()
                    ? R.string.profile_weight_none : R.string.profile_weight_first), R.color.card_oat);
        }

        tvStatLogged.setText(getString(R.string.profile_logged_value, health.loggedThisWeek));
        setPill(tvStatLoggedPill, getString(health.loggedThisWeek >= 5
                ? R.string.profile_logged_great : R.string.profile_logged_more),
                health.loggedThisWeek >= 5 ? R.color.card_sage : R.color.card_oat);

        String lifestyle = currentCat.getLifestyle();
        tvStatLifestyle.setText(lifestyle == null || lifestyle.trim().isEmpty()
                ? getString(R.string.profile_unknown) : lifestyle);
        setPill(tvStatLifestylePill, getString(currentCat.isNeutered()
                ? R.string.profile_neutered_yes : R.string.profile_neutered_no), R.color.blush_pink);
    }

    private void setPill(TextView view, String text, int fillRes) {
        view.setText(text);
        view.setBackground(pill(fillRes, false));
    }

    /** Rounded fill with a thin ink outline: status card (outlined=true, softer corners) or pill. */
    private GradientDrawable pill(int fillRes, boolean card) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setColor(ContextCompat.getColor(this, fillRes));
        d.setCornerRadius((card ? 16 : 999) * density);
        d.setStroke(Math.round((card ? 2f : 1.5f) * density), ContextCompat.getColor(this, R.color.ink));
        return d;
    }

    /** "Female • British Shorthair • Tabby", skipping blanks. */
    private static String joinDetails(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(" • ");
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private static String formatKg(double kg) {
        return String.format(Locale.getDefault(), "%.1f", kg);
    }

    private static String formatIsoDate(String isoDay) {
        try {
            LocalDate day = LocalDate.parse(isoDay);
            return DateFormatUtils.formatDate(day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } catch (RuntimeException e) {
            return isoDay;
        }
    }

    private void populateWeightLogs(List<WeightLog> logs) {
        int rowPadding = getResources().getDimensionPixelSize(R.dimen.spacing_tiny);
        containerWeightLogs.removeAllViews();
        weightChart.setWeightLogs(logs);
        tvWeightChartHint.setVisibility(logs.size() >= 2 ? View.VISIBLE : View.GONE);
        btnWeightHistory.setEnabled(!logs.isEmpty());
        if (logs.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.empty_weight_logs);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
            containerWeightLogs.addView(tv);
            return;
        }

        int previewCount = Math.min(WEIGHT_PREVIEW_LIMIT, logs.size());
        for (int i = 0; i < previewCount; i++) {
            TextView tv = new TextView(this);
            tv.setText(formatWeightLogLine(logs.get(i)));
            tv.setTextColor(ContextCompat.getColor(this, R.color.black));
            tv.setPadding(0, rowPadding, 0, rowPadding);
            containerWeightLogs.addView(tv);
        }
        if (logs.size() > WEIGHT_PREVIEW_LIMIT) {
            TextView more = new TextView(this);
            more.setText(getString(R.string.weight_history_more, logs.size() - WEIGHT_PREVIEW_LIMIT));
            more.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
            more.setPadding(0, rowPadding, 0, rowPadding);
            containerWeightLogs.addView(more);
        }
    }

    private String formatWeightLogLine(WeightLog log) {
        String dateStr = DateFormatUtils.formatDate(log.getDate());
        if (log.getNotes() != null && !log.getNotes().isEmpty()) {
            return getString(R.string.weight_log_row_with_notes,
                    String.valueOf(log.getWeight()), dateStr, log.getNotes());
        }
        return getString(R.string.weight_log_row, String.valueOf(log.getWeight()), dateStr);
    }

    private void showWeightHistoryDialog() {
        View layout = LayoutInflater.from(this).inflate(R.layout.dialog_weight_history, null, false);
        LinearLayout rows = layout.findViewById(R.id.containerWeightHistoryRows);

        weightHistoryDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_weight_history_title)
                .setView(layout)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        AppExecutors.getInstance().diskIO().execute(() -> {
            List<WeightLog> logs = weightLogDao.getWeightLogsForCat(catId);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || weightHistoryDialog == null) return;
                populateWeightHistoryRows(rows, logs);
                if (!weightHistoryDialog.isShowing()) {
                    weightHistoryDialog.show();
                }
            });
        });
    }

    private void populateWeightHistoryRows(LinearLayout rows, List<WeightLog> logs) {
        rows.removeAllViews();
        if (logs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.empty_weight_logs);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
            rows.addView(empty);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (WeightLog log : logs) {
            View row = inflater.inflate(R.layout.item_weight_log_row, rows, false);
            TextView tv = row.findViewById(R.id.tvWeightLogText);
            MaterialButton btnDelete = row.findViewById(R.id.btnDeleteWeightLog);
            tv.setText(formatWeightLogLine(log));
            btnDelete.setContentDescription(getString(R.string.cd_delete_weight));
            btnDelete.setOnClickListener(v -> confirmDeleteWeightLog(log));
            rows.addView(row);
        }
    }

    private void confirmDeleteWeightLog(WeightLog log) {
        String dateStr = DateFormatUtils.formatDate(log.getDate());
        new AlertDialog.Builder(this)
                .setTitle(R.string.msg_delete_weight_title)
                .setMessage(getString(R.string.msg_delete_weight_message,
                        String.valueOf(log.getWeight()), dateStr))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> deleteWeightLog(log.getId()))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void deleteWeightLog(long logId) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            weightLogDao.deleteWeightLog(logId);
            List<WeightLog> remaining = weightLogDao.getWeightLogsForCat(catId);
            Cat cat = catDao.getCatById(catId);
            if (cat != null && !remaining.isEmpty()) {
                // Profile "current weight" tracks the newest remaining weigh-in.
                cat.setWeight(remaining.get(0).getWeight());
                catDao.updateCat(cat);
            }
            AppExecutors.getInstance().mainThread().execute(() -> {
                Toast.makeText(this, R.string.msg_weight_deleted, Toast.LENGTH_SHORT).show();
                if (weightHistoryDialog != null && weightHistoryDialog.isShowing()) {
                    LinearLayout rows = weightHistoryDialog.findViewById(R.id.containerWeightHistoryRows);
                    if (rows != null) {
                        populateWeightHistoryRows(rows, remaining);
                    }
                    if (remaining.isEmpty()) {
                        weightHistoryDialog.dismiss();
                    }
                }
                loadCatProfile();
            });
        });
    }

    private void populateFeedingMeals(List<FeedingMeal> meals) {
        int rowPadding = getResources().getDimensionPixelSize(R.dimen.spacing_tiny);
        containerFeedingMeals.removeAllViews();
        if (meals.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.empty_feeding_schedule);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
            containerFeedingMeals.addView(tv);
            return;
        }

        for (FeedingMeal meal : meals) {
            TextView tv = new TextView(this);
            String text = getString(R.string.feeding_meal_row, meal.getMealLabel(), meal.getMealTime(), String.valueOf(meal.getPortionGrams()));
            tv.setText(text);
            tv.setTextColor(ContextCompat.getColor(this, R.color.black));
            tv.setPadding(0, rowPadding, 0, rowPadding);
            containerFeedingMeals.addView(tv);
        }
    }

    private void showAddWeightDialog() {
        View layout = LayoutInflater.from(this).inflate(R.layout.dialog_add_weight, null, false);
        TextInputEditText etWeight = layout.findViewById(R.id.etDialogWeight);
        TextInputEditText etNotes = layout.findViewById(R.id.etDialogWeightNotes);
        MaterialButtonToggleGroup toggleDate = layout.findViewById(R.id.toggleWeightDate);
        MaterialButton btnPickDate = layout.findViewById(R.id.btnWeightPickDate);

        // null = today (saved with the current time); otherwise the picked past day, at noon.
        final Long[] pickedDay = {null};
        btnPickDate.setOnClickListener(v -> {
            Calendar initial = Calendar.getInstance();
            if (pickedDay[0] != null) initial.setTimeInMillis(pickedDay[0]);
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, dayOfMonth, 12, 0, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                if (DateUtils.isToday(chosen.getTimeInMillis())) {
                    pickedDay[0] = null;
                    btnPickDate.setText(R.string.weight_date_pick);
                    toggleDate.check(R.id.btnWeightToday);
                } else {
                    pickedDay[0] = chosen.getTimeInMillis();
                    btnPickDate.setText(DateFormatUtils.formatDate(pickedDay[0]));
                    toggleDate.check(R.id.btnWeightPickDate);
                }
            }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis()); // no future weights
            picker.setOnCancelListener(d -> {
                if (pickedDay[0] == null) toggleDate.check(R.id.btnWeightToday);
            });
            picker.show();
        });
        layout.findViewById(R.id.btnWeightToday).setOnClickListener(v -> {
            pickedDay[0] = null;
            btnPickDate.setText(R.string.weight_date_pick);
        });

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
                    final long date = pickedDay[0] != null ? pickedDay[0] : System.currentTimeMillis();
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        WeightLog log = new WeightLog();
                        log.setCatId(catId);
                        log.setWeight(weight);
                        log.setDate(date);
                        log.setNotes(notes);
                        weightLogDao.insertWeightLog(log);

                        // The profile weight follows the newest log, so backfilling an old
                        // weight doesn't overwrite a more recent one. (Logs come newest first.)
                        List<WeightLog> logs = weightLogDao.getWeightLogsForCat(catId);
                        Cat cat = catDao.getCatById(catId);
                        if (cat != null && !logs.isEmpty()) {
                            cat.setWeight(logs.get(0).getWeight());
                            catDao.updateCat(cat);
                        }

                        AppExecutors.getInstance().mainThread().execute(this::loadCatProfile);
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showDeleteConfirmationDialog() {
        String catName = currentCat != null ? currentCat.getName() : getString(R.string.this_cat);
        new AlertDialog.Builder(this)
                .setTitle(R.string.msg_delete_cat_title)
                .setMessage(getString(R.string.msg_delete_cat_message, catName))
                .setPositiveButton(R.string.btn_yes, (dialog, which) -> {
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        Cat toDelete = catDao.getCatById(catId);
                        CareReminderScheduler.cancelForCat(this, careTaskDao, catId);
                        List<String> diaryPhotos = diaryDao.getPhotoPathsForCat(catId);
                        catDao.deleteCat(catId); // weight logs, meals, care tasks and diary entries go too (ON DELETE CASCADE)
                        // The cascade removes the diary rows, not their photo files.
                        for (String photo : diaryPhotos) {
                            if (diaryDao.countPhotoReferences(photo) == 0) fileStorageHelper.deleteImage(photo);
                        }
                        if (toDelete != null && toDelete.getPhotoPath() != null
                                && toDelete.getPhotoPath().startsWith(FileStorageHelper.CAT_PHOTO_PREFIX)) {
                            fileStorageHelper.deleteImage(toDelete.getPhotoPath());
                        }
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
