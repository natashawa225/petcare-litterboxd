package com.litterboxd.app.ui.tasks;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.data.CareTaskSeeder;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.TaskFrequency;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.reminders.CareReminderScheduler;
import com.litterboxd.app.ui.home.CatSwitcher;
import com.litterboxd.app.ui.nav.BottomNavHelper;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Tasks tab: the selected cat's care tasks (daily, monthly and one-off), split into To do and
 * Done. Tasks are added and edited in a dialog; ticking a task logs a completion (care_task_logs)
 * and reschedules its reminder.
 */
public class CareTasksActivity extends AppCompatActivity {

    /** Set by the Daily tab's "Add a task" quick action: open the add form once tasks are loaded. */
    public static final String EXTRA_OPEN_ADD_TASK = "open_add_task";

    private TextView tvTasksForCat, tvEmptyCareTasks, tvTodoSection, tvDoneSection;
    private LinearLayout layoutTodoTasks, layoutDoneTasks;
    private MaterialButton btnAddCareTask, btnSwitchCat;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private CareTaskDao careTaskDao;
    private Cat currentCat;
    private List<Cat> allCats = new ArrayList<>();
    private List<CareTask> careTasks = new ArrayList<>();
    private boolean openAddWhenLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_care_tasks);

        appPrefs = new AppPrefs(this);
        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        careTaskDao = new CareTaskDao(dbHelper);

        tvTasksForCat = findViewById(R.id.tvTasksForCat);
        tvEmptyCareTasks = findViewById(R.id.tvEmptyCareTasks);
        tvTodoSection = findViewById(R.id.tvTodoSection);
        tvDoneSection = findViewById(R.id.tvDoneSection);
        layoutTodoTasks = findViewById(R.id.layoutTodoTasks);
        layoutDoneTasks = findViewById(R.id.layoutDoneTasks);
        btnAddCareTask = findViewById(R.id.btnAddCareTask);
        btnSwitchCat = findViewById(R.id.btnTasksSwitchCat);

        BottomNavHelper.setup(this, (BottomNavigationView) findViewById(R.id.bottomNav), R.id.nav_tasks);
        btnAddCareTask.setOnClickListener(v -> {
            if (currentCat != null) showTaskEditor(null);
        });
        btnSwitchCat.setOnClickListener(v -> CatSwitcher.show(this, allCats, cat -> loadData()));

        // Only on first creation: after a rotation the extra is still in the Intent but was handled.
        openAddWhenLoaded = savedInstanceState == null
                && getIntent().getBooleanExtra(EXTRA_OPEN_ADD_TASK, false);
    }

    /** The tab already exists and was brought to the front (FLAG_ACTIVITY_REORDER_TO_FRONT). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_ADD_TASK, false)) {
            openAddWhenLoaded = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    /** Back on a secondary tab returns to the Daily tab rather than leaving the app. */
    @Override
    public void onBackPressed() {
        BottomNavHelper.openTab(this, R.id.nav_daily);
    }

    private void loadData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<Cat> cats = catDao.getAllCats();
            long selectedId = appPrefs.getSelectedCatId();
            Cat selected = null;
            for (Cat c : cats) {
                if (c.getId() == selectedId) selected = c;
            }
            if (selected == null && !cats.isEmpty()) {
                selected = cats.get(0);
                appPrefs.setSelectedCatId(selected.getId());
            }
            List<CareTask> tasks = new ArrayList<>();
            if (selected != null) {
                CareTaskSeeder.seedIfEmpty(this, careTaskDao, selected.getId());
                tasks = careTaskDao.getTasksForCat(selected.getId());
            }
            final Cat finalSelected = selected;
            final List<CareTask> finalTasks = tasks;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                allCats = cats;
                currentCat = finalSelected;
                careTasks = finalTasks;
                tvTasksForCat.setText(currentCat != null
                        ? getString(R.string.tasks_for_cat, currentCat.getName())
                        : getString(R.string.daily_no_cat));
                btnAddCareTask.setEnabled(currentCat != null);
                btnSwitchCat.setVisibility(currentCat != null ? View.VISIBLE : View.GONE);
                renderCareTasks();
                if (openAddWhenLoaded && currentCat != null) {
                    openAddWhenLoaded = false;
                    showTaskEditor(null);
                }
            });
        });
    }

    // ---- List ----

    private void renderCareTasks() {
        layoutTodoTasks.removeAllViews();
        layoutDoneTasks.removeAllViews();
        long now = System.currentTimeMillis();
        LayoutInflater inflater = LayoutInflater.from(this);
        int todo = 0, done = 0;
        for (CareTask task : careTasks) {
            boolean complete = task.isCompleteNow(now);
            LinearLayout parent = complete ? layoutDoneTasks : layoutTodoTasks;
            parent.addView(createTaskRow(inflater, parent, task, complete, now));
            if (complete) done++; else todo++;
        }
        tvEmptyCareTasks.setVisibility(careTasks.isEmpty() && currentCat != null ? View.VISIBLE : View.GONE);
        tvTodoSection.setVisibility(todo == 0 ? View.GONE : View.VISIBLE);
        tvDoneSection.setVisibility(done == 0 ? View.GONE : View.VISIBLE);
    }

    private View createTaskRow(LayoutInflater inflater, LinearLayout parent, CareTask task,
                               boolean complete, long now) {
        View row = inflater.inflate(R.layout.item_care_task, parent, false);
        CheckBox checkbox = row.findViewById(R.id.cbTaskDone);
        TextView name = row.findViewById(R.id.tvTaskName);
        TextView description = row.findViewById(R.id.tvTaskDescription);
        TextView schedule = row.findViewById(R.id.tvTaskReminder);
        MaterialButton edit = row.findViewById(R.id.btnEditTask);

        name.setText(task.getName());
        // Done = struck through + grey + in the "Done" section, so it's not shown by colour alone.
        if (complete) {
            name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            name.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
        }
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            description.setVisibility(View.VISIBLE);
            description.setText(task.getDescription());
        }
        schedule.setText(describeSchedule(task, now));
        if (task.isOverdue(now)) {
            schedule.setTextColor(ContextCompat.getColor(this, R.color.error_red));
        }

        checkbox.setChecked(complete);
        checkbox.setContentDescription(getString(R.string.cd_task_done, task.getName()));
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> setTaskComplete(task, isChecked));
        View.OnClickListener toggle = v -> checkbox.toggle();
        name.setOnClickListener(toggle);
        schedule.setOnClickListener(toggle);
        edit.setContentDescription(getString(R.string.cd_task_edit, task.getName()));
        edit.setOnClickListener(v -> showTaskEditor(task));
        return row;
    }

    private String describeSchedule(CareTask task, long now) {
        TaskFrequency frequency = task.getFrequency();
        if (frequency == TaskFrequency.ONCE) {
            if (task.getDueAt() == null) return getString(R.string.task_once_no_due);
            String when = task.hasReminder()
                    ? getString(R.string.date_time, DateFormatUtils.formatDate(task.getDueAt()),
                    DateFormatUtils.formatTime(task.getDueAt()))
                    : DateFormatUtils.formatDate(task.getDueAt());
            return task.isOverdue(now) ? getString(R.string.task_overdue, when) : getString(R.string.task_once_due, when);
        }
        boolean monthly = frequency == TaskFrequency.MONTHLY;
        if (task.hasReminder()) {
            String clock = formatClock(task.getReminderHour(), task.getReminderMinute());
            return getString(monthly ? R.string.task_monthly_reminder : R.string.task_daily_reminder, clock);
        }
        return getString(monthly ? R.string.task_monthly_no_reminder : R.string.task_daily_no_reminder);
    }

    // ---- Writes (each reads a fresh copy from the database on the worker thread) ----

    private void setTaskComplete(CareTask shown, boolean complete) {
        final long taskId = shown.getId();
        final long catId = shown.getCatId();
        AppExecutors.getInstance().diskIO().execute(() -> {
            CareTask task = careTaskDao.getTaskById(taskId);
            if (task != null) {
                long now = System.currentTimeMillis();
                if (complete) {
                    if (!task.isCompleteNow(now)) { // one log per completion, even on a double tap
                        task.setLastCompletedAt(now);
                        careTaskDao.insertLog(taskId, now);
                    }
                } else {
                    // Undoing a tick also removes the log it wrote, so the history stays accurate.
                    if (task.getLastCompletedAt() != null) {
                        careTaskDao.deleteLog(taskId, task.getLastCompletedAt());
                    }
                    task.setLastCompletedAt(null);
                }
                careTaskDao.updateTask(task);
                CareReminderScheduler.schedule(this, task);
            }
            List<CareTask> tasks = careTaskDao.getTasksForCat(catId);
            AppExecutors.getInstance().mainThread().execute(() -> showTasksIfCurrent(catId, tasks));
        });
    }

    /** Values from the editor form, read on the UI thread. */
    private static final class TaskForm {
        long catId;
        String name;
        String description;
        TaskFrequency frequency;
        Integer reminderHour;
        Integer reminderMinute;
        Long dueAt;
    }

    private void saveTask(CareTask existing, TaskForm form) {
        final long existingId = existing != null ? existing.getId() : -1L;
        final String catName = catNameFor(form.catId);
        final boolean otherCat = currentCat != null && form.catId != currentCat.getId();
        AppExecutors.getInstance().diskIO().execute(() -> {
            CareTask task = existingId != -1L ? careTaskDao.getTaskById(existingId) : new CareTask();
            if (task == null) return; // deleted meanwhile
            task.setCatId(form.catId);
            task.setName(form.name);
            task.setDescription(form.description);
            task.setFrequency(form.frequency);
            task.setReminderHour(form.reminderHour);
            task.setReminderMinute(form.reminderMinute);
            task.setDueAt(form.dueAt);
            if (existingId == -1L) {
                task.setDefault(false);
                task.setSortOrder(careTaskDao.countForCat(form.catId));
                task.setCreatedAt(System.currentTimeMillis());
                careTaskDao.insertTask(task);
            } else {
                careTaskDao.updateTask(task);
            }
            CareReminderScheduler.schedule(this, task);
            long shownCatId = currentCat != null ? currentCat.getId() : form.catId;
            List<CareTask> tasks = careTaskDao.getTasksForCat(shownCatId);
            AppExecutors.getInstance().mainThread().execute(() -> {
                showTasksIfCurrent(shownCatId, tasks);
                if (otherCat && catName != null) {
                    Toast.makeText(this, getString(R.string.msg_task_saved_for_cat, catName), Toast.LENGTH_SHORT).show();
                }
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
        final long taskId = task.getId();
        final long catId = task.getCatId();
        AppExecutors.getInstance().diskIO().execute(() -> {
            CareReminderScheduler.cancel(this, taskId);
            careTaskDao.deleteTask(taskId); // its completion logs go too (ON DELETE CASCADE)
            List<CareTask> tasks = careTaskDao.getTasksForCat(catId);
            AppExecutors.getInstance().mainThread().execute(() -> showTasksIfCurrent(catId, tasks));
        });
    }

    /** Shows reloaded tasks only if the user is still looking at that cat. */
    private void showTasksIfCurrent(long catId, List<CareTask> tasks) {
        if (isFinishing() || isDestroyed()) return;
        if (currentCat != null && currentCat.getId() == catId) {
            careTasks = tasks;
            renderCareTasks();
        }
    }

    // ---- Add / edit dialog ----

    private void showTaskEditor(CareTask existing) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_care_task, null, false);
        TextInputLayout tilName = dialogView.findViewById(R.id.tilTaskName);
        TextInputEditText etName = dialogView.findViewById(R.id.etTaskName);
        TextInputEditText etDescription = dialogView.findViewById(R.id.etTaskDescription);
        Spinner spinnerCat = dialogView.findViewById(R.id.spinnerTaskCat);
        Spinner spinnerFrequency = dialogView.findViewById(R.id.spinnerTaskFrequency);
        View layoutRecurring = dialogView.findViewById(R.id.layoutRecurring);
        View layoutOnce = dialogView.findViewById(R.id.layoutOnce);
        CheckBox cbReminder = dialogView.findViewById(R.id.cbEnableReminder);
        MaterialButton btnTime = dialogView.findViewById(R.id.btnPickReminderTime);
        MaterialButton btnDueDate = dialogView.findViewById(R.id.btnPickDueDate);
        MaterialButton btnDueTime = dialogView.findViewById(R.id.btnPickDueTime);
        MaterialButton btnClearDue = dialogView.findViewById(R.id.btnClearDue);
        TextView tvDueError = dialogView.findViewById(R.id.tvDueError);

        // Cat: defaults to the cat being shown. An existing task stays with its cat.
        String[] catNames = new String[allCats.size()];
        int catIndex = 0;
        long defaultCatId = existing != null ? existing.getCatId() : currentCat.getId();
        for (int i = 0; i < allCats.size(); i++) {
            catNames[i] = allCats.get(i).getName();
            if (allCats.get(i).getId() == defaultCatId) catIndex = i;
        }
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, catNames);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCat.setAdapter(catAdapter);
        spinnerCat.setSelection(catIndex);
        spinnerCat.setEnabled(existing == null);
        int catVisibility = allCats.size() > 1 ? View.VISIBLE : View.GONE;
        spinnerCat.setVisibility(catVisibility);
        dialogView.findViewById(R.id.tvTaskCatLabel).setVisibility(catVisibility);

        TaskFrequency[] frequencies = TaskFrequency.values();
        String[] labels = new String[frequencies.length];
        for (int i = 0; i < frequencies.length; i++) {
            labels[i] = getString(frequencyLabel(frequencies[i]));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequency.setAdapter(adapter);

        // Mutable picker state captured by the listeners below.
        final int[] time = {9, 0};                 // reminder clock time (daily/monthly)
        final int[] dueDate = {-1, -1, -1};        // year, month, day; -1 = no due date
        final int[] dueTime = {-1, -1};            // hour, minute; -1 = no due time
        final Long originalDueAt = existing != null ? existing.getDueAt() : null;

        if (existing != null) {
            etName.setText(existing.getName());
            etDescription.setText(existing.getDescription());
            spinnerFrequency.setSelection(existing.getFrequency().ordinal());
            if (existing.getFrequency() == TaskFrequency.ONCE) {
                if (existing.getDueAt() != null) {
                    Calendar due = Calendar.getInstance();
                    due.setTimeInMillis(existing.getDueAt());
                    dueDate[0] = due.get(Calendar.YEAR);
                    dueDate[1] = due.get(Calendar.MONTH);
                    dueDate[2] = due.get(Calendar.DAY_OF_MONTH);
                    if (existing.hasReminder()) {
                        dueTime[0] = existing.getReminderHour();
                        dueTime[1] = existing.getReminderMinute();
                    }
                }
                cbReminder.setChecked(true);
            } else {
                if (existing.hasReminder()) {
                    time[0] = existing.getReminderHour();
                    time[1] = existing.getReminderMinute();
                }
                cbReminder.setChecked(existing.hasReminder());
            }
        } else {
            cbReminder.setChecked(true);
        }

        Runnable refreshDueButtons = () -> {
            btnDueDate.setText(dueDate[0] < 0 ? getString(R.string.btn_due_date_none)
                    : getString(R.string.btn_due_date, DateFormatUtils.formatDate(dueMillis(dueDate, -1, -1))));
            btnDueTime.setText(dueTime[0] < 0 ? getString(R.string.btn_due_time_none)
                    : getString(R.string.btn_due_time, formatClock(dueTime[0], dueTime[1])));
            btnClearDue.setVisibility(dueDate[0] < 0 && dueTime[0] < 0 ? View.GONE : View.VISIBLE);
            tvDueError.setVisibility(View.GONE);
        };
        refreshDueButtons.run();
        updateReminderTimeButton(btnTime, cbReminder.isChecked(), time[0], time[1]);

        spinnerFrequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean once = frequencies[position] == TaskFrequency.ONCE;
                layoutOnce.setVisibility(once ? View.VISIBLE : View.GONE);
                layoutRecurring.setVisibility(once ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        cbReminder.setOnCheckedChangeListener((buttonView, isChecked) ->
                updateReminderTimeButton(btnTime, isChecked, time[0], time[1]));
        btnTime.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            time[0] = h;
            time[1] = m;
            cbReminder.setChecked(true);
            updateReminderTimeButton(btnTime, true, h, m);
        }, time[0], time[1], DateFormat.is24HourFormat(this)).show());
        btnDueDate.setOnClickListener(v -> {
            Calendar initial = Calendar.getInstance();
            if (dueDate[0] >= 0) initial.set(dueDate[0], dueDate[1], dueDate[2]);
            DatePickerDialog picker = new DatePickerDialog(this, (view, y, m, d) -> {
                dueDate[0] = y;
                dueDate[1] = m;
                dueDate[2] = d;
                refreshDueButtons.run();
            }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
            // New due dates can't be in the past (an existing overdue date is kept unless changed).
            picker.getDatePicker().setMinDate(startOfToday());
            picker.show();
        });
        btnDueTime.setOnClickListener(v -> {
            int h = dueTime[0] >= 0 ? dueTime[0] : 9;
            int m = dueTime[0] >= 0 ? dueTime[1] : 0;
            new TimePickerDialog(this, (view, hh, mm) -> {
                dueTime[0] = hh;
                dueTime[1] = mm;
                refreshDueButtons.run();
            }, h, m, DateFormat.is24HourFormat(this)).show();
        });
        btnClearDue.setOnClickListener(v -> {
            dueDate[0] = dueDate[1] = dueDate[2] = -1;
            dueTime[0] = dueTime[1] = -1;
            refreshDueButtons.run();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.dialog_add_task_title : R.string.dialog_edit_task_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, null) // overridden below so errors keep it open
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

            TaskForm form = new TaskForm();
            form.catId = allCats.isEmpty() ? defaultCatId : allCats.get(spinnerCat.getSelectedItemPosition()).getId();
            form.name = name;
            String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
            form.description = description.isEmpty() ? null : description;
            form.frequency = frequencies[spinnerFrequency.getSelectedItemPosition()];

            if (form.frequency == TaskFrequency.ONCE) {
                if (dueTime[0] >= 0 && dueDate[0] < 0) {
                    showDueError(tvDueError, R.string.error_due_time_needs_date);
                    return;
                }
                form.dueAt = dueDate[0] < 0 ? null : dueMillis(dueDate, dueTime[0], dueTime[1]);
                boolean changed = form.dueAt != null && !form.dueAt.equals(originalDueAt);
                if (changed && form.dueAt < System.currentTimeMillis()) {
                    showDueError(tvDueError, R.string.error_due_in_past);
                    return;
                }
                // For one-off tasks the reminder fires at the due time, if one was picked.
                form.reminderHour = dueTime[0] >= 0 && dueDate[0] >= 0 ? dueTime[0] : null;
                form.reminderMinute = dueTime[0] >= 0 && dueDate[0] >= 0 ? dueTime[1] : null;
            } else {
                form.reminderHour = cbReminder.isChecked() ? time[0] : null;
                form.reminderMinute = cbReminder.isChecked() ? time[1] : null;
            }
            saveTask(existing, form);
            dialog.dismiss();
        });
    }

    private static void showDueError(TextView view, int messageRes) {
        view.setText(messageRes);
        view.setVisibility(View.VISIBLE);
    }

    /**
     * Due timestamp: the picked date at the picked time, or, with no time, the END of that day so
     * a date-only task only counts as overdue once the whole day has passed.
     */
    private static long dueMillis(int[] date, int hour, int minute) {
        Calendar due = Calendar.getInstance();
        due.clear();
        if (hour >= 0) {
            due.set(date[0], date[1], date[2], hour, minute, 0);
        } else {
            due.set(date[0], date[1], date[2], 23, 59, 59);
            due.set(Calendar.MILLISECOND, 999);
        }
        return due.getTimeInMillis();
    }

    private static long startOfToday() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return today.getTimeInMillis();
    }

    private String catNameFor(long catId) {
        for (Cat cat : allCats) {
            if (cat.getId() == catId) return cat.getName();
        }
        return null;
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
        if (frequency == TaskFrequency.MONTHLY) return R.string.frequency_monthly;
        if (frequency == TaskFrequency.ONCE) return R.string.frequency_once;
        return R.string.frequency_daily;
    }
}
