package com.litterboxd.app.ui.home;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.data.CareTaskSeeder;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.TaskFrequency;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.reminders.CareReminderScheduler;
import com.litterboxd.app.ui.checkin.CheckInActivity;
import com.litterboxd.app.ui.onboarding.AddEditCatActivity;
import com.litterboxd.app.ui.profile.CatProfileActivity;
import com.litterboxd.app.ui.settings.SettingsActivity;
import com.litterboxd.app.util.AgeUtils;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private TextView tvWelcomeUser, tvHomeCatName, tvHomeCatDetails;
    private ImageView imgHomeCatPhoto;
    private MaterialButton btnSwitchCat, btnViewCatProfile, btnSettings, btnAddCareTask;
    private TextView tvDailyProgress, tvMonthlyProgress, tvEmptyCareTasks, tvDailySection, tvMonthlySection;
    private ProgressBar progressDailyTasks, progressMonthlyTasks;
    private LinearLayout layoutDailyTasks, layoutMonthlyTasks;
    private View cardCareTasks;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private CareTaskDao careTaskDao;
    private FileStorageHelper fileStorageHelper;
    private Cat currentCat;
    private List<Cat> allCats;
    private List<CareTask> careTasks = new ArrayList<>();

    private static final int PERMISSION_REQUEST_CODE_NOTIFICATION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        appPrefs = new AppPrefs(this);
        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        careTaskDao = new CareTaskDao(dbHelper);
        fileStorageHelper = new FileStorageHelper(this);

        tvWelcomeUser = findViewById(R.id.tvWelcomeUser);
        tvHomeCatName = findViewById(R.id.tvHomeCatName);
        tvHomeCatDetails = findViewById(R.id.tvHomeCatDetails);
        imgHomeCatPhoto = findViewById(R.id.imgHomeCatPhoto);
        btnSwitchCat = findViewById(R.id.btnSwitchCat);
        btnViewCatProfile = findViewById(R.id.btnViewCatProfile);
        btnSettings = findViewById(R.id.btnSettings);
        btnAddCareTask = findViewById(R.id.btnAddCareTask);
        cardCareTasks = findViewById(R.id.cardCareTasks);
        tvDailyProgress = findViewById(R.id.tvDailyProgress);
        tvMonthlyProgress = findViewById(R.id.tvMonthlyProgress);
        tvEmptyCareTasks = findViewById(R.id.tvEmptyCareTasks);
        tvDailySection = findViewById(R.id.tvDailySection);
        tvMonthlySection = findViewById(R.id.tvMonthlySection);
        progressDailyTasks = findViewById(R.id.progressDailyTasks);
        progressMonthlyTasks = findViewById(R.id.progressMonthlyTasks);
        layoutDailyTasks = findViewById(R.id.layoutDailyTasks);
        layoutMonthlyTasks = findViewById(R.id.layoutMonthlyTasks);

        btnSwitchCat.setOnClickListener(v -> showCatSwitcherDialog());
        btnViewCatProfile.setOnClickListener(v -> {
            if (currentCat != null) {
                Intent intent = new Intent(this, CatProfileActivity.class);
                intent.putExtra("cat_id", currentCat.getId());
                startActivity(intent);
            }
        });
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.cardDailyCheckin).setOnClickListener(v -> {
            if (currentCat != null) {
                Intent intent = new Intent(this, CheckInActivity.class);
                intent.putExtra(CheckInActivity.EXTRA_CAT_ID, currentCat.getId());
                startActivity(intent);
            }
        });
        btnAddCareTask.setOnClickListener(v -> {
            if (currentCat != null) {
                showTaskEditor(null);
            }
        });

        checkNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String ownerName = appPrefs.getOwnerName();
            final String greeting = (ownerName == null || ownerName.isEmpty())
                    ? getString(R.string.home_greeting_guest)
                    : getString(R.string.home_greeting, ownerName);

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
            final Cat finalSelected = selected;
            List<CareTask> tasks = new ArrayList<>();
            if (finalSelected != null) {
                CareTaskSeeder.seedIfEmpty(this, careTaskDao, finalSelected.getId());
                tasks = careTaskDao.getTasksForCat(finalSelected.getId());
            }
            final List<CareTask> loadedTasks = tasks;

            AppExecutors.getInstance().mainThread().execute(() -> {
                allCats = cats;
                currentCat = finalSelected;
                careTasks = loadedTasks;
                tvWelcomeUser.setText(greeting);
                updateCatCard();
                renderCareTasks();
            });
        });
    }

    private void updateCatCard() {
        if (currentCat != null) {
            tvHomeCatName.setText(currentCat.getName());
            String ageStr = AgeUtils.getAgeDisplay(currentCat.getBirthday(), currentCat.getApproxAgeYears(), currentCat.getApproxAgeMonths());
            String details = getString(R.string.home_cat_summary, currentCat.getBreed(), ageStr, String.valueOf(currentCat.getWeight()));
            tvHomeCatDetails.setText(details);

            fileStorageHelper.showCatPhoto(imgHomeCatPhoto, currentCat.getPhotoPath());
            btnSwitchCat.setVisibility(View.VISIBLE);
            cardCareTasks.setVisibility(View.VISIBLE);
        } else {
            tvHomeCatName.setText(R.string.no_cat_selected);
            tvHomeCatDetails.setText(null);
            imgHomeCatPhoto.setImageResource(R.drawable.ic_cat_face);
            btnSwitchCat.setVisibility(View.GONE);
            cardCareTasks.setVisibility(View.GONE);
        }
    }

    private void renderCareTasks() {
        layoutDailyTasks.removeAllViews();
        layoutMonthlyTasks.removeAllViews();
        if (currentCat == null) {
            return;
        }

        long now = System.currentTimeMillis();
        int dailyDone = 0, dailyTotal = 0, monthlyDone = 0, monthlyTotal = 0;
        LayoutInflater inflater = LayoutInflater.from(this);

        for (CareTask task : careTasks) {
            boolean complete = task.isCompleteNow(now);
            if (task.getFrequency() == TaskFrequency.MONTHLY) {
                monthlyTotal++;
                if (complete) monthlyDone++;
                layoutMonthlyTasks.addView(createTaskRow(inflater, layoutMonthlyTasks, task, complete));
            } else {
                dailyTotal++;
                if (complete) dailyDone++;
                layoutDailyTasks.addView(createTaskRow(inflater, layoutDailyTasks, task, complete));
            }
        }

        boolean empty = careTasks.isEmpty();
        tvEmptyCareTasks.setVisibility(empty ? View.VISIBLE : View.GONE);
        tvDailyProgress.setVisibility(empty ? View.GONE : View.VISIBLE);
        progressDailyTasks.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvMonthlyProgress.setVisibility(empty ? View.GONE : View.VISIBLE);
        progressMonthlyTasks.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvDailySection.setVisibility(dailyTotal == 0 ? View.GONE : View.VISIBLE);
        layoutDailyTasks.setVisibility(dailyTotal == 0 ? View.GONE : View.VISIBLE);
        tvMonthlySection.setVisibility(monthlyTotal == 0 ? View.GONE : View.VISIBLE);
        layoutMonthlyTasks.setVisibility(monthlyTotal == 0 ? View.GONE : View.VISIBLE);

        tvDailyProgress.setText(getString(R.string.progress_daily_tasks, dailyDone, dailyTotal));
        tvMonthlyProgress.setText(getString(R.string.progress_monthly_tasks, monthlyDone, monthlyTotal));
        progressDailyTasks.setProgress(dailyTotal == 0 ? 0 : (int) Math.round(100.0 * dailyDone / dailyTotal));
        progressMonthlyTasks.setProgress(monthlyTotal == 0 ? 0 : (int) Math.round(100.0 * monthlyDone / monthlyTotal));
    }

    private View createTaskRow(LayoutInflater inflater, LinearLayout parent, CareTask task, boolean complete) {
        View row = inflater.inflate(R.layout.item_care_task, parent, false);
        CheckBox checkbox = row.findViewById(R.id.cbTaskDone);
        TextView name = row.findViewById(R.id.tvTaskName);
        TextView reminder = row.findViewById(R.id.tvTaskReminder);
        MaterialButton edit = row.findViewById(R.id.btnEditTask);

        name.setText(task.getName());
        applyCompletedStyle(name, complete);
        if (task.hasReminder()) {
            reminder.setVisibility(View.VISIBLE);
            reminder.setText(getString(R.string.reminder_at, formatClock(task.getReminderHour(), task.getReminderMinute())));
        } else {
            reminder.setText(R.string.reminder_none);
        }

        checkbox.setOnCheckedChangeListener(null);
        checkbox.setChecked(complete);
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> setTaskComplete(task, isChecked));
        View.OnClickListener toggle = v -> checkbox.toggle();
        name.setOnClickListener(toggle);
        reminder.setOnClickListener(toggle);
        edit.setOnClickListener(v -> showTaskEditor(task));
        return row;
    }

    private void applyCompletedStyle(TextView name, boolean complete) {
        if (complete) {
            name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            name.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
        } else {
            name.setPaintFlags(name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            name.setTextColor(ContextCompat.getColor(this, R.color.black));
        }
    }

    private void setTaskComplete(CareTask task, boolean complete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            long now = System.currentTimeMillis();
            if (complete) {
                task.setLastCompletedAt(now);
                careTaskDao.insertLog(task.getId(), now);
            } else {
                task.setLastCompletedAt(null);
            }
            careTaskDao.updateTask(task);
            CareReminderScheduler.schedule(this, task);
            List<CareTask> tasks = careTaskDao.getTasksForCat(task.getCatId());
            AppExecutors.getInstance().mainThread().execute(() -> {
                careTasks = tasks;
                renderCareTasks();
            });
        });
    }

    private void showTaskEditor(CareTask existing) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_care_task, null, false);
        TextInputLayout tilName = dialogView.findViewById(R.id.tilTaskName);
        TextInputEditText etName = dialogView.findViewById(R.id.etTaskName);
        Spinner spinnerFrequency = dialogView.findViewById(R.id.spinnerTaskFrequency);
        CheckBox cbReminder = dialogView.findViewById(R.id.cbEnableReminder);
        MaterialButton btnTime = dialogView.findViewById(R.id.btnPickReminderTime);

        TaskFrequency[] frequencies = TaskFrequency.values();
        String[] labels = new String[frequencies.length];
        for (int i = 0; i < frequencies.length; i++) {
            labels[i] = getString(frequencyLabel(frequencies[i]));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequency.setAdapter(adapter);

        final int[] hour = {9};
        final int[] minute = {0};
        if (existing != null) {
            etName.setText(existing.getName());
            spinnerFrequency.setSelection(indexOfFrequency(existing.getFrequency()));
            if (existing.hasReminder()) {
                hour[0] = existing.getReminderHour();
                minute[0] = existing.getReminderMinute();
                cbReminder.setChecked(true);
            } else {
                cbReminder.setChecked(false);
            }
        } else {
            cbReminder.setChecked(true);
        }
        updateReminderTimeButton(btnTime, cbReminder.isChecked(), hour[0], minute[0]);
        cbReminder.setOnCheckedChangeListener((buttonView, isChecked) ->
                updateReminderTimeButton(btnTime, isChecked, hour[0], minute[0]));
        btnTime.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            hour[0] = h;
            minute[0] = m;
            cbReminder.setChecked(true);
            updateReminderTimeButton(btnTime, true, h, m);
        }, hour[0], minute[0], DateFormat.is24HourFormat(this)).show());

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.dialog_add_task_title : R.string.dialog_edit_task_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.btn_delete, (dialog, which) -> confirmDeleteTask(existing));
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                tilName.setError(getString(R.string.error_task_name_required));
                return;
            }
            tilName.setError(null);
            TaskFrequency frequency = frequencies[spinnerFrequency.getSelectedItemPosition()];
            Integer reminderHour = cbReminder.isChecked() ? hour[0] : null;
            Integer reminderMinute = cbReminder.isChecked() ? minute[0] : null;
            saveTask(existing, name, frequency, reminderHour, reminderMinute);
            dialog.dismiss();
        });
    }

    private void saveTask(CareTask existing, String name, TaskFrequency frequency,
                          Integer reminderHour, Integer reminderMinute) {
        if (currentCat == null) return;
        AppExecutors.getInstance().diskIO().execute(() -> {
            CareTask task = existing != null ? existing : new CareTask();
            task.setCatId(currentCat.getId());
            task.setName(name);
            task.setFrequency(frequency);
            task.setReminderHour(reminderHour);
            task.setReminderMinute(reminderMinute);
            if (existing == null) {
                task.setDefault(false);
                task.setSortOrder(careTaskDao.countForCat(currentCat.getId()));
                task.setCreatedAt(System.currentTimeMillis());
                careTaskDao.insertTask(task);
            } else {
                careTaskDao.updateTask(task);
            }
            CareReminderScheduler.schedule(this, task);
            List<CareTask> tasks = careTaskDao.getTasksForCat(currentCat.getId());
            AppExecutors.getInstance().mainThread().execute(() -> {
                careTasks = tasks;
                renderCareTasks();
            });
        });
    }

    private void confirmDeleteTask(CareTask task) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.msg_delete_task_title)
                .setMessage(getString(R.string.msg_delete_task_message, task.getName()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> deleteTask(task))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void deleteTask(CareTask task) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            CareReminderScheduler.cancel(this, task.getId());
            careTaskDao.deleteTask(task.getId());
            List<CareTask> tasks = currentCat != null
                    ? careTaskDao.getTasksForCat(currentCat.getId())
                    : new ArrayList<>();
            AppExecutors.getInstance().mainThread().execute(() -> {
                careTasks = tasks;
                renderCareTasks();
            });
        });
    }

    private void updateReminderTimeButton(MaterialButton button, boolean enabled, int hour, int minute) {
        button.setEnabled(enabled);
        button.setText(getString(R.string.btn_pick_reminder_time, formatClock(hour, minute)));
    }

    private String formatClock(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        return DateFormatUtils.formatTime(calendar.getTimeInMillis());
    }

    private static int frequencyLabel(TaskFrequency frequency) {
        if (frequency == TaskFrequency.MONTHLY) {
            return R.string.frequency_monthly;
        }
        return R.string.frequency_daily;
    }

    private static int indexOfFrequency(TaskFrequency frequency) {
        TaskFrequency[] values = TaskFrequency.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == frequency) return i;
        }
        return 0;
    }

    private void showCatSwitcherDialog() {
        if (allCats == null || allCats.isEmpty()) return;
        String[] items = new String[allCats.size() + 1];
        for (int i = 0; i < allCats.size(); i++) {
            items[i] = allCats.get(i).getName();
        }
        items[allCats.size()] = getString(R.string.add_another_cat);

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_cat_switcher)
                .setItems(items, (dialog, which) -> {
                    if (which == allCats.size()) {
                        Intent intent = new Intent(this, AddEditCatActivity.class);
                        intent.putExtra(AddEditCatActivity.EXTRA_ADD_ANOTHER, true);
                        startActivity(intent);
                        return;
                    }
                    Cat chosen = allCats.get(which);
                    appPrefs.setSelectedCatId(chosen.getId());
                    currentCat = chosen;
                    updateCatCard();
                    loadCareTasksForCurrentCat();
                })
                .show();
    }

    private void loadCareTasksForCurrentCat() {
        if (currentCat == null) {
            careTasks = new ArrayList<>();
            renderCareTasks();
            return;
        }
        final long catId = currentCat.getId();
        AppExecutors.getInstance().diskIO().execute(() -> {
            CareTaskSeeder.seedIfEmpty(this, careTaskDao, catId);
            List<CareTask> tasks = careTaskDao.getTasksForCat(catId);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (currentCat != null && currentCat.getId() == catId) {
                    careTasks = tasks;
                    renderCareTasks();
                }
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
