package com.litterboxd.app.ui.checkin;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.checkin.AnswerOption;
import com.litterboxd.app.checkin.CheckInDayMark;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.Question;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DailyCheckin;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Flo-style calendar for Feature A logging: pick a day, see whether it was logged, then open
 * {@link CheckInActivity} for that date. Does not change the check-in question tree.
 */
public class HealthLogActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";

    private TextView tvSubtitle, tvMonthTitle, tvSelectedDayTitle, tvSelectedDayStatus;
    private LinearLayout layoutWeekdayHeaders, layoutSelectedAnswers;
    private GridLayout gridCalendar;
    private MaterialButton btnLogSelectedDay, btnPrevMonth, btnNextMonth;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private DailyCheckinDao dailyCheckinDao;
    private DecisionTree checkInTree;

    private long catId = -1L;
    private String catName = "";
    private YearMonth visibleMonth;
    private LocalDate selectedDate;
    private Set<String> loggedDates = new HashSet<>();
    /** Soft marks per ISO date for logged days (NORMAL / NOTED). */
    private Map<String, CheckInDayMark> dayMarks = new HashMap<>();
    private DailyCheckin selectedCheckin;

    private static final DateTimeFormatter MONTH_TITLE =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_log);

        appPrefs = new AppPrefs(this);
        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        dailyCheckinDao = new DailyCheckinDao(dbHelper);

        tvSubtitle = findViewById(R.id.tvHealthLogSubtitle);
        tvMonthTitle = findViewById(R.id.tvMonthTitle);
        tvSelectedDayTitle = findViewById(R.id.tvSelectedDayTitle);
        tvSelectedDayStatus = findViewById(R.id.tvSelectedDayStatus);
        layoutWeekdayHeaders = findViewById(R.id.layoutWeekdayHeaders);
        layoutSelectedAnswers = findViewById(R.id.layoutSelectedAnswers);
        gridCalendar = findViewById(R.id.gridCalendar);
        btnLogSelectedDay = findViewById(R.id.btnLogSelectedDay);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);

        visibleMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        buildWeekdayHeaders();

        btnPrevMonth.setOnClickListener(v -> {
            visibleMonth = visibleMonth.minusMonths(1);
            renderCalendar();
        });
        btnNextMonth.setOnClickListener(v -> {
            YearMonth next = visibleMonth.plusMonths(1);
            if (next.isAfter(YearMonth.now())) {
                Toast.makeText(this, R.string.health_log_no_future_month, Toast.LENGTH_SHORT).show();
                return;
            }
            visibleMonth = next;
            renderCalendar();
        });
        btnLogSelectedDay.setOnClickListener(v -> openCheckInForSelectedDay());

        catId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        if (catId == -1L) {
            catId = appPrefs.getSelectedCatId();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCatAndLogs();
    }

    private void loadCatAndLogs() {
        final long id = catId;
        final DecisionTree cachedTree = checkInTree;
        AppExecutors.getInstance().diskIO().execute(() -> {
            Cat cat = catDao.getCatById(id);
            if (cat == null) {
                List<Cat> cats = catDao.getAllCats();
                if (!cats.isEmpty()) {
                    cat = cats.get(0);
                }
            }
            DecisionTree tree = cachedTree != null ? cachedTree : CheckInContent.loadTree(this);
            Set<String> dates = new HashSet<>();
            Map<String, CheckInDayMark> marks = new HashMap<>();
            DailyCheckin checkin = null;
            if (cat != null) {
                dates = dailyCheckinDao.getCheckinDatesForCat(cat.getId());
                Map<String, Map<String, List<String>>> answersByDate =
                        dailyCheckinDao.getAnswersByDateForCat(cat.getId());
                for (Map.Entry<String, Map<String, List<String>>> entry : answersByDate.entrySet()) {
                    marks.put(entry.getKey(), CheckInDayMark.classify(tree, entry.getValue()));
                }
                checkin = dailyCheckinDao.getCheckin(cat.getId(), selectedDate.toString());
            }
            final Cat finalCat = cat;
            final DailyCheckin finalCheckin = checkin;
            final Set<String> finalDates = dates;
            final Map<String, CheckInDayMark> finalMarks = marks;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                checkInTree = tree;
                loggedDates = finalDates;
                dayMarks = finalMarks;
                selectedCheckin = finalCheckin;
                if (finalCat == null) {
                    Toast.makeText(this, R.string.checkin_error_no_cat, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                catId = finalCat.getId();
                catName = finalCat.getName();
                tvSubtitle.setText(getString(R.string.health_log_subtitle, catName));
                renderCalendar();
                renderSelectedDay();
            });
        });
    }

    private void buildWeekdayHeaders() {
        layoutWeekdayHeaders.removeAllViews();
        DayOfWeek first = DayOfWeek.MONDAY;
        for (int i = 0; i < 7; i++) {
            DayOfWeek day = first.plus(i);
            TextView label = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(lp);
            label.setGravity(Gravity.CENTER);
            label.setText(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()));
            label.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_size_caption));
            layoutWeekdayHeaders.addView(label);
        }
    }

    private void renderCalendar() {
        tvMonthTitle.setText(visibleMonth.format(MONTH_TITLE));
        btnNextMonth.setEnabled(!visibleMonth.equals(YearMonth.now()));
        gridCalendar.removeAllViews();

        LocalDate firstOfMonth = visibleMonth.atDay(1);
        int leadEmpty = (firstOfMonth.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue() + 7) % 7;
        int daysInMonth = visibleMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < leadEmpty; i++) {
            View blank = new View(this);
            GridLayout.LayoutParams lp = dayCellParams();
            blank.setLayoutParams(lp);
            gridCalendar.addView(blank);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = visibleMonth.atDay(day);
            View cell = inflater.inflate(R.layout.item_calendar_day, gridCalendar, false);
            cell.setLayoutParams(dayCellParams());
            TextView number = cell.findViewById(R.id.tvDayNumber);
            View dot = cell.findViewById(R.id.viewLoggedDot);
            number.setText(String.valueOf(day));

            boolean future = date.isAfter(today);
            String dateKey = date.toString();
            CheckInDayMark mark = dayMarks.containsKey(dateKey)
                    ? dayMarks.get(dateKey)
                    : (loggedDates.contains(dateKey) ? CheckInDayMark.NOTED : CheckInDayMark.NONE);
            boolean logged = mark != CheckInDayMark.NONE;
            boolean selected = date.equals(selectedDate);
            boolean isToday = date.equals(today);

            if (future) {
                number.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
                number.setAlpha(0.35f);
                cell.setEnabled(false);
                cell.setClickable(false);
            } else {
                number.setAlpha(1f);
                cell.setEnabled(true);
                cell.setOnClickListener(v -> selectDate(date));
            }

            if (selected) {
                number.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_calendar_day_selected));
                number.setTextColor(ContextCompat.getColor(this, R.color.dark_olive_green));
            } else if (isToday && !future) {
                number.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_calendar_day_today));
                number.setTextColor(ContextCompat.getColor(this, R.color.white));
            } else {
                number.setBackground(null);
                if (!future) {
                    number.setTextColor(ContextCompat.getColor(this, R.color.black));
                }
            }

            if (!logged) {
                dot.setVisibility(View.GONE);
            } else {
                dot.setVisibility(View.VISIBLE);
                dot.setBackgroundResource(mark == CheckInDayMark.NORMAL
                        ? R.drawable.bg_calendar_dot_normal
                        : R.drawable.bg_calendar_dot_noted);
            }
            cell.setContentDescription(getString(
                    logged ? R.string.cd_health_log_day_logged : R.string.cd_health_log_day,
                    dateKey));
            gridCalendar.addView(cell);
        }
    }

    private GridLayout.LayoutParams dayCellParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = getResources().getDimensionPixelSize(R.dimen.health_log_day_cell);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
        return lp;
    }

    private void selectDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            Toast.makeText(this, R.string.health_log_no_future_day, Toast.LENGTH_SHORT).show();
            return;
        }
        selectedDate = date;
        if (!YearMonth.from(date).equals(visibleMonth)) {
            visibleMonth = YearMonth.from(date);
        }
        renderCalendar();
        loadSelectedCheckin();
    }

    private void loadSelectedCheckin() {
        final long id = catId;
        final String dateKey = selectedDate.toString();
        AppExecutors.getInstance().diskIO().execute(() -> {
            DailyCheckin checkin = dailyCheckinDao.getCheckin(id, dateKey);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                selectedCheckin = checkin;
                renderSelectedDay();
            });
        });
    }

    private void renderSelectedDay() {
        layoutSelectedAnswers.removeAllViews();
        boolean isToday = selectedDate.equals(LocalDate.now());
        long dayMillis = selectedDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        String pretty = DateFormatUtils.formatDate(dayMillis);
        if (isToday) {
            tvSelectedDayTitle.setText(R.string.health_log_selected_today);
        } else {
            tvSelectedDayTitle.setText(getString(R.string.health_log_selected_day, pretty));
        }

        boolean future = selectedDate.isAfter(LocalDate.now());
        if (future) {
            tvSelectedDayStatus.setText(R.string.health_log_no_future_day);
            btnLogSelectedDay.setEnabled(false);
            return;
        }

        btnLogSelectedDay.setEnabled(true);
        if (selectedCheckin == null || selectedCheckin.getAnswers().isEmpty()) {
            tvSelectedDayStatus.setText(R.string.health_log_day_empty);
            btnLogSelectedDay.setText(isToday ? R.string.btn_log_today : R.string.health_log_log_day);
            return;
        }

        tvSelectedDayStatus.setText(getString(R.string.health_log_day_logged_at,
                DateFormatUtils.formatTime(selectedCheckin.getUpdatedAt())));
        btnLogSelectedDay.setText(isToday ? R.string.btn_update_log : R.string.health_log_update_day);

        if (checkInTree == null) return;
        int pad = getResources().getDimensionPixelSize(R.dimen.spacing_tiny);
        for (Question group : checkInTree.getGroups()) {
            List<String> values = selectedCheckin.getAnswers().get(group.getId());
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
            layoutSelectedAnswers.addView(row);
        }
    }

    private void openCheckInForSelectedDay() {
        if (catId <= 0 || selectedDate.isAfter(LocalDate.now())) return;
        Intent intent = new Intent(this, CheckInActivity.class);
        intent.putExtra(CheckInActivity.EXTRA_CAT_ID, catId);
        intent.putExtra(CheckInActivity.EXTRA_CHECKIN_DATE, selectedDate.toString());
        startActivity(intent);
    }
}
