package com.litterboxd.app.ui.health;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.health.DayState;
import com.litterboxd.app.health.HealthArticle;
import com.litterboxd.app.health.HealthContent;
import com.litterboxd.app.health.HealthReading;
import com.litterboxd.app.health.HealthRecap;
import com.litterboxd.app.health.HealthRule;
import com.litterboxd.app.ui.checkin.CheckInContent;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shows 6 weeks (42 days) of daily check-ins for a single health signal,
 * allowing the owner to tap any day to see its logged answer,
 * plus related reading if available.
 */
public class SignalDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    public static final String EXTRA_SIGNAL_ID = "signal_id";
    public static final String EXTRA_SIGNAL_LABEL = "signal_label";

    private long catId = -1L;
    private String signalId = "";
    private String signalLabel = "";
    private Cat cat;

    private MaterialToolbar toolbar;
    private TextView tvSubtitle;
    private LinearLayout rowDayOfWeek;
    private LinearLayout gridCalendar;
    private View cardSelectedDay;
    private View todayCell;
    private TextView tvSelectedDayTitle;
    private TextView tvSelectedDayDetail;
    private View cardReading;
    private TextView tvReadingTitle;
    private TextView tvReadingSource;
    private MaterialButton btnOpenArticle;

    private View selectedCellView = null;

    private static final DateTimeFormatter DISPLAY_DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signal_detail);

        catId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        signalId = getIntent().getStringExtra(EXTRA_SIGNAL_ID);
        signalLabel = getIntent().getStringExtra(EXTRA_SIGNAL_LABEL);
        if (signalId == null) signalId = "water";
        if (signalLabel == null) signalLabel = "Water";

        toolbar = findViewById(R.id.toolbar);
        tvSubtitle = findViewById(R.id.tvSignalDetailSubtitle);
        rowDayOfWeek = findViewById(R.id.rowDayOfWeek);
        gridCalendar = findViewById(R.id.gridCalendar);
        cardSelectedDay = findViewById(R.id.cardSelectedDay);
        tvSelectedDayTitle = findViewById(R.id.tvSelectedDayTitle);
        tvSelectedDayDetail = findViewById(R.id.tvSelectedDayDetail);
        cardReading = findViewById(R.id.cardReading);
        tvReadingTitle = findViewById(R.id.tvReadingTitle);
        tvReadingSource = findViewById(R.id.tvReadingSource);
        btnOpenArticle = findViewById(R.id.btnOpenArticle);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(getString(R.string.signal_detail_title, signalLabel));

        loadData();
    }

    private void loadData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(this);
            CatDao catDao = new CatDao(db);
            cat = catDao.getCatById(catId);
            String catName = cat != null ? cat.getName() : "Cat";

            DailyCheckinDao checkinDao = new DailyCheckinDao(db);
            Map<String, Map<String, List<String>>> answers = checkinDao.getAnswersByDateForCat(catId);

            String checkinTreeJson = loadRawString(R.raw.checkin_tree);
            Map<String, String> normalValues = HealthRecap.extractNormalValues(checkinTreeJson);

            HealthContent content = HealthContent.load(this);
            List<HealthRule> rules = content != null ? content.getRules() : Collections.emptyList();
            Set<String> concernPairs = HealthRecap.extractConcernPairs(rules);

            // Related reading for this signal
            HealthArticle relatedArticle = null;
            if (content != null && content.getReadings() != null) {
                for (HealthReading r : content.getReadings()) {
                    if (signalId.equals(r.getQuestionId())) {
                        relatedArticle = content.getArticlesById().get(r.getArticleId());
                        break;
                    }
                }
            }
            final HealthArticle articleToOpen = relatedArticle;

            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                tvSubtitle.setText(getString(R.string.signal_detail_subtitle, catName));
                setupCalendarGrid(answers, normalValues, concernPairs);
                bindReading(articleToOpen);
            });
        });
    }

    private void bindReading(HealthArticle article) {
        if (article == null) {
            cardReading.setVisibility(View.GONE);
            return;
        }
        cardReading.setVisibility(View.VISIBLE);
        String title = CheckInContent.text(this, article.getTitleKey());
        String source = CheckInContent.text(this, article.getSourceKey());
        tvReadingTitle.setText(title);
        tvReadingSource.setText(getString(R.string.health_alert_source, title, source));
        btnOpenArticle.setOnClickListener(v ->
                HealthArticleReaderActivity.start(this, title, article.getUrl()));
    }

    private void setupCalendarGrid(Map<String, Map<String, List<String>>> answers,
                                   Map<String, String> normalValues,
                                   Set<String> concernPairs) {
        rowDayOfWeek.removeAllViews();
        gridCalendar.removeAllViews();

        float density = getResources().getDisplayMetrics().density;

        // Day of week header (Mon - Sun)
        String[] daysOfWeek = {"M", "T", "W", "T", "F", "S", "S"};
        for (String dow : daysOfWeek) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setGravity(Gravity.CENTER);
            tv.setText(dow);
            tv.setTextSize(12);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
            rowDayOfWeek.addView(tv);
        }

        // Build 42 days grid ending on today (or today aligned to end of week)
        LocalDate today = LocalDate.now();
        // Determine end date: end of current week (Sunday) so the grid looks like a regular calendar
        LocalDate endGrid = today.plusDays(7 - today.getDayOfWeek().getValue());
        LocalDate startGrid = endGrid.minusDays(41); // 42 days total = 6 weeks

        for (int week = 0; week < 6; week++) {
            LinearLayout weekRow = new LinearLayout(this);
            weekRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            weekRow.setOrientation(LinearLayout.HORIZONTAL);
            weekRow.setPadding(0, (int) (3f * density), 0, (int) (3f * density));

            for (int day = 0; day < 7; day++) {
                LocalDate date = startGrid.plusDays(week * 7 + day);
                View cell = createDayCell(date, today, answers, normalValues, concernPairs);
                weekRow.addView(cell);
            }
            gridCalendar.addView(weekRow);
        }
        // Start with today selected, so the card below shows something useful straight away.
        if (todayCell != null) todayCell.performClick();
    }

    private View createDayCell(LocalDate date,
                               LocalDate today,
                               Map<String, Map<String, List<String>>> answers,
                               Map<String, String> normalValues,
                               Set<String> concernPairs) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout cell = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (44f * density), 1f);
        lp.setMargins((int) (2f * density), (int) (2f * density), (int) (2f * density), (int) (2f * density));
        cell.setLayoutParams(lp);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);

        boolean isFuture = date.isAfter(today);
        Map<String, List<String>> dayAnswers = isFuture ? null : answers.get(date.toString());

        DayState state = isFuture ? DayState.NOT_LOGGED
                : classifyDayForSignal(signalId, dayAnswers, normalValues, concernPairs);

        // Day number
        TextView tvDayNum = new TextView(this);
        tvDayNum.setText(String.valueOf(date.getDayOfMonth()));
        tvDayNum.setTextSize(12);
        tvDayNum.setTextColor(ContextCompat.getColor(this, isFuture ? R.color.text_dark_gray : R.color.ink));

        // State indicator dot
        View dot = new View(this);
        int dotSize = (int) (7f * density);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotLp.topMargin = (int) (2f * density);
        dot.setLayoutParams(dotLp);
        dot.setBackground(makeDotDrawable(state, isFuture));

        cell.addView(tvDayNum);
        cell.addView(dot);

        // Cell background
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8f * density);
        if (date.equals(today)) {
            bg.setColor(ContextCompat.getColor(this, R.color.card_oat));
            bg.setStroke((int) (1.5f * density), ContextCompat.getColor(this, R.color.ink));
        } else {
            bg.setColor(ContextCompat.getColor(this, R.color.white));
            bg.setStroke((int) (1f * density), ContextCompat.getColor(this, R.color.surface_card));
        }
        cell.setBackground(bg);

        if (date.equals(today)) {
            cell.setTag(Boolean.TRUE); // today keeps its oat fill and ink border when deselected
            todayCell = cell;
        }
        if (!isFuture) {
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setOnClickListener(v -> onCellClicked(cell, date, state, dayAnswers));
        }

        return cell;
    }

    private void onCellClicked(View cell, LocalDate date, DayState state, Map<String, List<String>> dayAnswers) {
        float density = getResources().getDisplayMetrics().density;

        if (selectedCellView != null) {
            // Restore previous cell border
            GradientDrawable prevBg = (GradientDrawable) selectedCellView.getBackground();
            boolean wasToday = Boolean.TRUE.equals(selectedCellView.getTag());
            prevBg.setStroke((int) ((wasToday ? 1.5f : 1f) * density),
                    ContextCompat.getColor(this, wasToday ? R.color.ink : R.color.surface_card));
        }

        selectedCellView = cell;
        GradientDrawable curBg = (GradientDrawable) cell.getBackground();
        curBg.setStroke((int) (2.5f * density), ContextCompat.getColor(this, R.color.ink));

        String dateTitle = date.format(DISPLAY_DATE_FMT);
        tvSelectedDayTitle.setText(dateTitle);

        if (dayAnswers == null || dayAnswers.isEmpty()) {
            tvSelectedDayDetail.setText(getString(R.string.signal_detail_not_logged_short));
            return;
        }

        tvSelectedDayDetail.setText(describeDay(dayAnswers));
    }

    /** Plain words for one day, e.g. "Drinking more · also peeing more" or "Normal". */
    private String describeDay(Map<String, List<String>> dayAnswers) {
        List<String> values = dayAnswers.get(signalId);
        if (values == null || values.isEmpty()) {
            return getString(R.string.signal_detail_not_logged_short);
        }
        List<String> parts = new java.util.ArrayList<>();
        for (String v : values) {
            parts.add("normal".equals(v) ? getString(R.string.recap_legend_normal)
                    : HealthRecap.offPhrase(signalId, v));
        }
        String extra = followUpPhrase(dayAnswers);
        if (extra != null) parts.add(extra);
        return String.join(" · ", parts);
    }

    private String followUpPhrase(Map<String, List<String>> dayAnswers) {
        if ("litter".equals(signalId)) {
            List<String> v = dayAnswers.get("urine_output");
            if (v == null || v.isEmpty()) return null;
            switch (v.get(0)) {
                case "little_or_none": return getString(R.string.signal_follow_urine_little);
                case "some": return getString(R.string.signal_follow_urine_some);
                default: return null;
            }
        }
        if ("appetite".equals(signalId)) {
            List<String> v = dayAnswers.get("vomiting");
            if (v == null || v.isEmpty()) return null;
            if ("once".equals(v.get(0))) return getString(R.string.signal_follow_vomit_once);
            if ("repeated".equals(v.get(0))) return getString(R.string.signal_follow_vomit_repeated);
            return null;
        }
        if ("water".equals(signalId)) {
            List<String> v = dayAnswers.get("urination_more");
            if (v != null && v.contains("yes")) return getString(R.string.signal_follow_peeing_more);
        }
        return null;
    }

    private GradientDrawable makeDotDrawable(DayState state, boolean isFuture) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (isFuture) {
            d.setColor(ContextCompat.getColor(this, android.R.color.transparent));
            return d;
        }
        switch (state) {
            case NORMAL:
                d.setColor(ContextCompat.getColor(this, R.color.card_sage));
                d.setStroke((int) (1.5f * density), ContextCompat.getColor(this, R.color.sage_primary));
                break;
            case OFF:
                d.setColor(ContextCompat.getColor(this, R.color.card_clay));
                d.setStroke((int) (1.5f * density), ContextCompat.getColor(this, R.color.muted_pinkish_brown));
                break;
            case CONCERN:
                d.setColor(ContextCompat.getColor(this, R.color.pastel_coral));
                d.setStroke((int) (1.5f * density), ContextCompat.getColor(this, R.color.error_red));
                break;
            case NOT_LOGGED:
            default:
                d.setColor(ContextCompat.getColor(this, R.color.surface_card));
                break;
        }
        return d;
    }

    private DayState classifyDayForSignal(String sigId,
                                          Map<String, List<String>> answers,
                                          Map<String, String> normalValues,
                                          Set<String> concernPairs) {
        if (answers == null || answers.isEmpty()) return DayState.NOT_LOGGED;
        if ("litter".equals(sigId)) {
            List<String> straining = answers.get("litter");
            if (straining != null && straining.contains("straining")) return DayState.CONCERN;
            List<String> urineOutput = answers.get("urine_output");
            if (urineOutput != null && urineOutput.contains("little_or_none")) return DayState.CONCERN;
        } else if ("appetite".equals(sigId)) {
            List<String> vomit = answers.get("vomiting");
            if (vomit != null && vomit.contains("repeated")) return DayState.CONCERN;
        }
        List<String> vals = answers.get(sigId);
        if (vals == null || vals.isEmpty()) return DayState.NOT_LOGGED;
        for (String val : vals) {
            if (concernPairs.contains(sigId + ":" + val)) return DayState.CONCERN;
        }
        String normalVal = normalValues.getOrDefault(sigId, "normal");
        if (vals.contains(normalVal)) return DayState.NORMAL;
        return DayState.OFF;
    }

    private String loadRawString(int rawRes) {
        try (InputStream in = getResources().openRawResource(rawRes)) {
            byte[] buf = new byte[in.available()];
            int read = in.read(buf);
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
