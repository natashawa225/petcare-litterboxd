package com.litterboxd.app.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.checkin.AnswerOption;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.Question;
import com.litterboxd.app.data.CareTaskCompletion;
import com.litterboxd.app.data.CareTaskSeeder;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DailyCheckin;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.checkin.CheckInActivity;
import com.litterboxd.app.ui.checkin.CheckInContent;
import com.litterboxd.app.ui.nav.BottomNavHelper;
import com.litterboxd.app.ui.profile.CatProfileActivity;
import com.litterboxd.app.ui.tasks.CareTasksActivity;
import com.litterboxd.app.util.AgeUtils;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Daily tab, the app's landing screen after onboarding: greeting, the selected cat, today's
 * check-in (filled in on CheckInActivity), quick actions and a care-task summary.
 */
public class HomeActivity extends AppCompatActivity {

    private TextView tvWelcomeUser, tvToday, tvHomeCatName, tvHomeCatDetails;
    private TextView tvDailyLogStatus, tvTaskSummary;
    private ImageView imgHomeCatPhoto;
    private MaterialButton btnSwitchCat, btnViewCatProfile, btnLogToday;
    private LinearLayout layoutDailyLogAnswers, layoutTodayTasks;
    private ProgressBar progressTasks;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private CareTaskDao careTaskDao;
    private DailyCheckinDao dailyCheckinDao;
    private FileStorageHelper fileStorageHelper;
    private Cat currentCat;
    private List<Cat> allCats = new ArrayList<>();
    /** Question tree, loaded once (it only changes with an app update). */
    private DecisionTree checkInTree;

    private static final int PERMISSION_REQUEST_CODE_NOTIFICATION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        appPrefs = new AppPrefs(this);
        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        careTaskDao = new CareTaskDao(dbHelper);
        dailyCheckinDao = new DailyCheckinDao(dbHelper);
        fileStorageHelper = new FileStorageHelper(this);

        tvWelcomeUser = findViewById(R.id.tvWelcomeUser);
        tvToday = findViewById(R.id.tvToday);
        tvHomeCatName = findViewById(R.id.tvHomeCatName);
        tvHomeCatDetails = findViewById(R.id.tvHomeCatDetails);
        imgHomeCatPhoto = findViewById(R.id.imgHomeCatPhoto);
        btnSwitchCat = findViewById(R.id.btnSwitchCat);
        btnViewCatProfile = findViewById(R.id.btnViewCatProfile);
        tvDailyLogStatus = findViewById(R.id.tvDailyLogStatus);
        layoutDailyLogAnswers = findViewById(R.id.layoutDailyLogAnswers);
        btnLogToday = findViewById(R.id.btnLogToday);
        tvTaskSummary = findViewById(R.id.tvTaskSummary);
        progressTasks = findViewById(R.id.progressTasks);
        layoutTodayTasks = findViewById(R.id.layoutTodayTasks);

        BottomNavHelper.setup(this, (BottomNavigationView) findViewById(R.id.bottomNav), R.id.nav_daily);

        btnSwitchCat.setOnClickListener(v -> CatSwitcher.show(this, allCats, cat -> loadData()));
        btnViewCatProfile.setOnClickListener(v -> {
            if (currentCat != null) {
                Intent intent = new Intent(this, CatProfileActivity.class);
                intent.putExtra("cat_id", currentCat.getId());
                startActivity(intent);
            }
        });
        btnLogToday.setOnClickListener(v -> openCheckIn());
        findViewById(R.id.btnQuickLog).setOnClickListener(v -> openCheckIn());
        findViewById(R.id.btnQuickAddTask).setOnClickListener(v -> {
            Intent intent = BottomNavHelper.intentFor(this, R.id.nav_tasks);
            intent.putExtra(CareTasksActivity.EXTRA_OPEN_ADD_TASK, true);
            BottomNavHelper.openTab(this, intent);
        });
        findViewById(R.id.btnSeeTasks).setOnClickListener(v -> BottomNavHelper.openTab(this, R.id.nav_tasks));

        checkNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload whenever the tab is shown: another tab or screen may have changed the selected
        // cat, today's log or the tasks.
        loadData();
    }

    /**
     * Daily is the root tab: Back leaves the app (closing the other tabs too) instead of
     * stepping back through tabs or returning to onboarding.
     */
    @Override
    public void onBackPressed() {
        finishAffinity();
    }

    private void openCheckIn() {
        if (currentCat == null) return;
        Intent intent = new Intent(this, CheckInActivity.class);
        intent.putExtra(CheckInActivity.EXTRA_CAT_ID, currentCat.getId());
        startActivity(intent);
    }

    private void loadData() {
        final String today = LocalDate.now().toString(); // same day key as CheckInActivity
        final DecisionTree cachedTree = checkInTree;
        AppExecutors.getInstance().diskIO().execute(() -> {
            String ownerName = appPrefs.getOwnerName();
            final String hello = getString(R.string.daily_hello,
                    (ownerName == null || ownerName.isEmpty()) ? getString(R.string.default_owner_name) : ownerName);

            List<Cat> cats = catDao.getAllCats();
            long selectedId = appPrefs.getSelectedCatId();
            Cat selected = null;
            for (Cat c : cats) {
                if (c.getId() == selectedId) {
                    selected = c;
                    break;
                }
            }
            if (selected == null && !cats.isEmpty()) {
                selected = cats.get(0);
                appPrefs.setSelectedCatId(selected.getId());
            }
            DecisionTree tree = cachedTree != null ? cachedTree : CheckInContent.loadTree(this);
            List<CareTask> tasks = new ArrayList<>();
            DailyCheckin checkin = null;
            if (selected != null) {
                CareTaskSeeder.seedIfEmpty(this, careTaskDao, selected.getId());
                tasks = careTaskDao.getTasksForCat(selected.getId());
                checkin = dailyCheckinDao.getCheckin(selected.getId(), today);
            }
            final Cat finalSelected = selected;
            final List<CareTask> finalTasks = tasks;
            final DailyCheckin finalCheckin = checkin;

            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                allCats = cats;
                currentCat = finalSelected;
                checkInTree = tree;
                tvWelcomeUser.setText(hello);
                tvToday.setText(DateFormat.format(
                        DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), new Date()));
                updateCatCard();
                renderDailyLog(finalCheckin);
                renderTodayTasks(finalTasks);
            });
        });
    }

    private void updateCatCard() {
        if (currentCat != null) {
            tvHomeCatName.setText(currentCat.getName());
            String ageStr = AgeUtils.getAgeDisplay(currentCat.getBirthday(), currentCat.getApproxAgeYears(), currentCat.getApproxAgeMonths());
            tvHomeCatDetails.setText(getString(R.string.home_cat_summary, currentCat.getBreed(), ageStr, String.valueOf(currentCat.getWeight())));
            imgHomeCatPhoto.setContentDescription(getString(R.string.cd_cat_photo, currentCat.getName()));
            fileStorageHelper.showCatPhoto(imgHomeCatPhoto, currentCat.getPhotoPath());
            btnSwitchCat.setVisibility(View.VISIBLE);
            btnViewCatProfile.setEnabled(true);
        } else {
            tvHomeCatName.setText(R.string.no_cat_selected);
            tvHomeCatDetails.setText(R.string.daily_no_cat);
            imgHomeCatPhoto.setImageResource(R.drawable.ic_cat_face);
            btnSwitchCat.setVisibility(View.GONE);
            btnViewCatProfile.setEnabled(false);
        }
    }

    /** Today's saved answers as "Appetite: Ate normally" rows, or the friendly empty state. */
    private void renderDailyLog(DailyCheckin checkin) {
        layoutDailyLogAnswers.removeAllViews();
        btnLogToday.setEnabled(currentCat != null);
        if (currentCat == null) {
            tvDailyLogStatus.setText(R.string.daily_no_cat);
            return;
        }
        if (checkInTree == null) {
            tvDailyLogStatus.setText(R.string.daily_log_error);
            return;
        }
        if (checkin == null || checkin.getAnswers().isEmpty()) {
            tvDailyLogStatus.setText(R.string.daily_log_empty);
            btnLogToday.setText(R.string.btn_log_today);
            return;
        }
        tvDailyLogStatus.setText(getString(R.string.daily_log_saved_at,
                DateFormatUtils.formatTime(checkin.getUpdatedAt())));
        btnLogToday.setText(R.string.btn_update_log);

        int pad = getResources().getDimensionPixelSize(R.dimen.spacing_tiny);
        for (Question group : checkInTree.getGroups()) {
            List<String> values = checkin.getAnswers().get(group.getId());
            if (values == null || values.isEmpty()) continue;
            List<String> labels = new ArrayList<>();
            for (String value : values) {
                AnswerOption option = group.getOption(value);
                if (option != null) labels.add(CheckInContent.text(this, option.getLabelKey()));
            }
            if (labels.isEmpty()) continue;
            TextView row = new TextView(this);
            row.setText(getString(R.string.daily_log_answer_row,
                    CheckInContent.text(this, group.getTextKey()), String.join(", ", labels)));
            row.setTextColor(ContextCompat.getColor(this, R.color.black));
            row.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_size_small));
            row.setPadding(0, pad, 0, pad);
            layoutDailyLogAnswers.addView(row);
        }
    }

    /**
     * Today's care tasks for the selected cat: "2 of 4 tasks done", a progress bar and a tickable
     * row per task. Only tasks due today count (see CareTask.isDueToday): daily tasks and one-off
     * tasks due today; monthly tasks and other days' one-offs are left out.
     */
    private void renderTodayTasks(List<CareTask> tasks) {
        layoutTodayTasks.removeAllViews();
        long now = System.currentTimeMillis();
        LayoutInflater inflater = LayoutInflater.from(this);
        int done = 0, total = 0;
        for (CareTask task : tasks) {
            if (!task.isDueToday(now)) continue;
            boolean complete = task.isCompleteNow(now);
            total++;
            if (complete) done++;
            layoutTodayTasks.addView(createTodayTaskRow(inflater, task, complete));
        }
        if (total == 0) {
            // No "0 of 0": a friendly empty state and no bar.
            tvTaskSummary.setText(R.string.tasks_empty);
            progressTasks.setVisibility(View.GONE);
            return;
        }
        progressTasks.setVisibility(View.VISIBLE);
        progressTasks.setProgress((int) Math.round(100.0 * done / total));
        tvTaskSummary.setText(getString(done == total ? R.string.daily_tasks_all_done : R.string.daily_tasks_summary,
                done, total));
    }

    private View createTodayTaskRow(LayoutInflater inflater, CareTask task, boolean complete) {
        View row = inflater.inflate(R.layout.item_care_task, layoutTodayTasks, false);
        CheckBox checkbox = row.findViewById(R.id.cbTaskDone);
        TextView name = row.findViewById(R.id.tvTaskName);
        name.setText(task.getName());
        if (complete) { // struck through as well as ticked, not colour alone
            name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            name.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
        }
        // Compact rows here; schedule details and editing are on the Tasks tab.
        row.findViewById(R.id.tvTaskReminder).setVisibility(View.GONE);
        row.findViewById(R.id.btnEditTask).setVisibility(View.GONE);
        checkbox.setChecked(complete);
        checkbox.setContentDescription(getString(R.string.cd_task_done, task.getName()));
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> setTaskComplete(task, isChecked));
        name.setOnClickListener(v -> checkbox.toggle());
        return row;
    }

    /** Same completion logic as the Tasks tab; re-reads the cat's tasks so the count updates at once. */
    private void setTaskComplete(CareTask shown, boolean complete) {
        final long taskId = shown.getId();
        final long catId = shown.getCatId();
        AppExecutors.getInstance().diskIO().execute(() -> {
            CareTaskCompletion.setComplete(this, careTaskDao, taskId, complete);
            List<CareTask> tasks = careTaskDao.getTasksForCat(catId);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (currentCat != null && currentCat.getId() == catId) renderTodayTasks(tasks);
            });
        });
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!appPrefs.isNotificationPermissionAsked()) {
                appPrefs.setNotificationPermissionAsked(true);
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE_NOTIFICATION);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_NOTIFICATION
                && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, R.string.msg_notifications_denied, Toast.LENGTH_LONG).show();
        }
    }
}
