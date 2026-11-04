package com.litterboxd.app.ui.checkin;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.checkin.AnswerOption;
import com.litterboxd.app.checkin.CheckInCategory;
import com.litterboxd.app.checkin.CheckInSession;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.HistoryTriggerEvaluator;
import com.litterboxd.app.checkin.Question;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DailyCheckin;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.health.HealthAlert;
import com.litterboxd.app.health.HealthContent;
import com.litterboxd.app.health.HealthRule;
import com.litterboxd.app.health.HealthRuleEvaluator;
import com.litterboxd.app.health.WeightPoints;
import com.litterboxd.app.health.WeightTrend;
import com.litterboxd.app.ui.health.HealthAlertsActivity;
import com.litterboxd.app.util.AppExecutors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily check-in. Screen 1 shows symptom chips grouped in category cards; screen 2 asks the
 * follow-up questions that the selected chips lead to. Both are panels of this one Activity.
 * All the branching lives in CheckInSession; this class only draws its state and forwards taps.
 *
 * Reopening the check-in on the same day brings back today's saved answers, and saving updates
 * that entry (one check-in per cat per day). Launch with the Intent extra EXTRA_CAT_ID.
 */
public class CheckInActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    /** Optional ISO date (yyyy-MM-dd). Defaults to today. Future dates are rejected. */
    public static final String EXTRA_CHECKIN_DATE = "checkin_date";

    private static final String STATE_ANSWER_IDS = "state_answer_ids";
    private static final String STATE_ANSWER_VALUES = "state_answer_values";
    private static final String STATE_SHOWING_FOLLOW_UPS = "state_showing_follow_ups";
    private static final String STATE_CHECKIN_DATE = "state_checkin_date";
    private static final String TAG = "CheckInActivity";

    private ScrollView scrollView;
    private TextView tvSubtitle, tvFollowUpsHint;
    private View panelSymptoms, panelFollowUps;
    private LinearLayout layoutCategories, layoutFollowUps;
    private MaterialButton btnAllNormal, btnPrimary, btnBack;

    private final Map<String, MaterialButton> chipViews = new HashMap<>();
    private LitterboxdDbHelper dbHelper;
    private long catId;
    private String catName;
    private String checkinDate;
    private DecisionTree tree;
    private CheckInSession session;
    private boolean showingFollowUps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_in);

        scrollView = findViewById(R.id.scrollCheckIn);
        tvSubtitle = findViewById(R.id.tvCheckInSubtitle);
        tvFollowUpsHint = findViewById(R.id.tvCheckInFollowUpsHint);
        panelSymptoms = findViewById(R.id.panelSymptoms);
        panelFollowUps = findViewById(R.id.panelFollowUps);
        layoutCategories = findViewById(R.id.layoutCategories);
        layoutFollowUps = findViewById(R.id.layoutFollowUps);
        btnAllNormal = findViewById(R.id.btnAllNormal);
        btnPrimary = findViewById(R.id.btnCheckInPrimary);
        btnBack = findViewById(R.id.btnCheckInBack);

        btnAllNormal.setOnClickListener(v -> {
            session.selectAllNormal();
            render();
        });
        btnPrimary.setOnClickListener(v -> onPrimaryClicked());
        btnBack.setOnClickListener(v -> goBack());

        catId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        if (catId == -1L) {
            Toast.makeText(this, R.string.checkin_error_no_cat, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // The first screen (chips) stays disabled until the cat and the questions are loaded.
        btnAllNormal.setEnabled(false);
        btnPrimary.setEnabled(false);

        // Answers kept across a rotation take priority over what was saved in the database.
        final Map<String, List<String>> savedState = readSavedState(savedInstanceState);
        final boolean savedShowingFollowUps = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_SHOWING_FOLLOW_UPS, false);

        dbHelper = LitterboxdDbHelper.getInstance(this);
        if (savedInstanceState != null && savedInstanceState.getString(STATE_CHECKIN_DATE) != null) {
            checkinDate = savedInstanceState.getString(STATE_CHECKIN_DATE);
        } else {
            checkinDate = resolveCheckinDate(getIntent().getStringExtra(EXTRA_CHECKIN_DATE));
        }
        if (checkinDate == null) {
            Toast.makeText(this, R.string.checkin_error_future_date, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Reading the database and the question file happens off the UI thread.
        AppExecutors.getInstance().diskIO().execute(() -> {
            Cat cat = new CatDao(dbHelper).getCatById(catId);
            DecisionTree loadedTree = CheckInContent.loadTree(this);
            DailyCheckinDao dao = new DailyCheckinDao(dbHelper);
            DailyCheckin existing = cat == null ? null : dao.getCheckin(catId, checkinDate);
            Map<String, Map<String, List<String>>> answersByDate = cat == null
                    ? new LinkedHashMap<String, Map<String, List<String>>>()
                    : dao.getAnswersByDateForCat(cat.getId());
            List<String> historyFollowUps = Collections.emptyList();
            if (loadedTree != null) {
                historyFollowUps = HistoryTriggerEvaluator.evaluate(
                        loadedTree.getHistoryTriggers(),
                        LocalDate.parse(checkinDate),
                        answersByDate);
            }

            final List<String> finalHistoryFollowUps = historyFollowUps;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (cat == null || loadedTree == null) {
                    Toast.makeText(this, R.string.checkin_error_load, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                catName = cat.getName();
                tree = loadedTree;
                session = new CheckInSession(tree);
                session.setHistoryFollowUps(finalHistoryFollowUps);
                if (savedState != null) {
                    session.restore(savedState);
                } else if (existing != null) {
                    session.restore(existing.getAnswers());
                }
                showingFollowUps = savedShowingFollowUps && session.hasFollowUps();

                buildSymptomChips();
                btnAllNormal.setEnabled(true);
                btnPrimary.setEnabled(true);
                render();
            });
        });
    }

    /** Returns an ISO date string, or null when the value is missing/invalid/in the future. */
    private static String resolveCheckinDate(String requested) {
        LocalDate today = LocalDate.now();
        if (requested == null || requested.trim().isEmpty()) {
            return today.toString();
        }
        try {
            LocalDate date = LocalDate.parse(requested.trim());
            if (date.isAfter(today)) {
                return null;
            }
            return date.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (session == null) return;
        List<String> ids = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : session.getAnswers().entrySet()) {
            for (String value : entry.getValue()) {
                ids.add(entry.getKey());
                values.add(value);
            }
        }
        outState.putStringArray(STATE_ANSWER_IDS, ids.toArray(new String[0]));
        outState.putStringArray(STATE_ANSWER_VALUES, values.toArray(new String[0]));
        outState.putBoolean(STATE_SHOWING_FOLLOW_UPS, showingFollowUps);
        outState.putString(STATE_CHECKIN_DATE, checkinDate);
    }

    @Override
    public void onBackPressed() {
        // From the follow-up questions, Back returns to the chips before it leaves the screen.
        if (showingFollowUps) {
            goBack();
        } else {
            super.onBackPressed();
        }
    }

    private Map<String, List<String>> readSavedState(Bundle state) {
        if (state == null) return null;
        String[] ids = state.getStringArray(STATE_ANSWER_IDS);
        String[] values = state.getStringArray(STATE_ANSWER_VALUES);
        if (ids == null || values == null || ids.length != values.length) return null;

        Map<String, List<String>> answers = new LinkedHashMap<>();
        for (int i = 0; i < ids.length; i++) {
            List<String> list = answers.get(ids[i]);
            if (list == null) {
                list = new ArrayList<>();
                answers.put(ids[i], list);
            }
            list.add(values[i]);
        }
        return answers;
    }

    // ---- Drawing ----

    /** Builds the category cards and chips once; render() then only updates which chips are checked. */
    private void buildSymptomChips() {
        LayoutInflater inflater = LayoutInflater.from(this);
        layoutCategories.removeAllViews();
        chipViews.clear();

        for (CheckInCategory category : tree.getCategories()) {
            View card = inflater.inflate(R.layout.item_check_in_category, layoutCategories, false);
            ((TextView) card.findViewById(R.id.tvCategoryTitle)).setText(stringByKey(category.getTitleKey()));
            LinearLayout layoutGroups = card.findViewById(R.id.layoutGroups);

            for (Question group : category.getGroups()) {
                View section = inflater.inflate(R.layout.item_check_in_section, layoutGroups, false);
                ((TextView) section.findViewById(R.id.tvSectionTitle)).setText(stringByKey(group.getTextKey()));
                GridLayout grid = section.findViewById(R.id.gridChips);

                for (AnswerOption option : group.getOptions()) {
                    MaterialButton chip = createChip(inflater, grid, option);
                    chip.setOnClickListener(v -> {
                        session.toggle(group.getId(), option.getValue());
                        render();
                    });
                    chipViews.put(chipKey(group.getId(), option.getValue()), chip);
                    grid.addView(chip);
                }
                layoutGroups.addView(section);
            }
            layoutCategories.addView(card);
        }
    }

    private void renderFollowUps() {
        LayoutInflater inflater = LayoutInflater.from(this);
        layoutFollowUps.removeAllViews();

        for (Question question : session.getActiveFollowUps()) {
            View section = inflater.inflate(R.layout.item_check_in_section, layoutFollowUps, false);
            ((TextView) section.findViewById(R.id.tvSectionTitle))
                    .setText(stringByKey(question.getTextKey(), catName));
            TextView reason = section.findViewById(R.id.tvSectionReason);
            String reasonText = followUpReasonText(question.getId());
            if (reasonText == null) {
                reason.setVisibility(View.GONE);
            } else {
                reason.setVisibility(View.VISIBLE);
                reason.setText(reasonText);
            }
            GridLayout grid = section.findViewById(R.id.gridChips);

            for (AnswerOption option : question.getOptions()) {
                MaterialButton chip = createChip(inflater, grid, option);
                chip.setChecked(option.getValue().equals(session.getFollowUpAnswer(question.getId())));
                chip.setOnClickListener(v -> {
                    session.answerFollowUp(question.getId(), option.getValue());
                    render();
                });
                grid.addView(chip);
            }
            layoutFollowUps.addView(section);
        }
    }

    /** Explains why this follow-up appeared (chip tap and/or history). */
    private String followUpReasonText(String followUpId) {
        List<String> labelKeys = session.getFollowUpReasonLabelKeys(followUpId);
        boolean history = session.isHistorySourcedFollowUp(followUpId);
        if (labelKeys.isEmpty() && !history) return null;
        if (labelKeys.isEmpty()) {
            return getString(R.string.checkin_because_history);
        }
        List<String> labels = new ArrayList<>();
        for (String key : labelKeys) {
            labels.add(stringByKey(key));
        }
        String joined = String.join(", ", labels);
        if (history) {
            return getString(R.string.checkin_because_marked_and_history, joined);
        }
        return getString(R.string.checkin_because_marked, joined);
    }

    private MaterialButton createChip(LayoutInflater inflater, GridLayout parent, AnswerOption option) {
        MaterialButton chip = (MaterialButton) inflater.inflate(R.layout.item_check_in_chip, parent, false);
        chip.setText(stringByKey(option.getLabelKey()));
        return chip;
    }

    /** Redraws everything from the session. Called after every tap. */
    private void render() {
        if (LocalDate.now().toString().equals(checkinDate)) {
            tvSubtitle.setText(getString(R.string.checkin_subtitle, catName));
        } else {
            tvSubtitle.setText(getString(R.string.checkin_subtitle_dated, catName, checkinDate));
        }
        panelSymptoms.setVisibility(showingFollowUps ? View.GONE : View.VISIBLE);
        panelFollowUps.setVisibility(showingFollowUps ? View.VISIBLE : View.GONE);
        btnBack.setVisibility(showingFollowUps ? View.VISIBLE : View.GONE);

        for (Question group : tree.getGroups()) {
            for (AnswerOption option : group.getOptions()) {
                MaterialButton chip = chipViews.get(chipKey(group.getId(), option.getValue()));
                if (chip != null) {
                    chip.setChecked(session.isSelected(group.getId(), option.getValue()));
                }
            }
        }
        if (showingFollowUps) {
            renderFollowUps();
            tvFollowUpsHint.setText(session.hasHistoryFollowUpsActive()
                    ? R.string.checkin_followups_hint_history
                    : R.string.checkin_followups_hint);
        }

        // On the chips screen the button says Continue only when follow-up questions are coming.
        boolean continueToFollowUps = !showingFollowUps && session.hasFollowUps();
        btnPrimary.setText(continueToFollowUps ? R.string.btn_continue : R.string.checkin_save);
    }

    // ---- Actions ----

    private void onPrimaryClicked() {
        if (!showingFollowUps) {
            if (!session.hasAnySelection()) {
                Toast.makeText(this, R.string.checkin_pick_something, Toast.LENGTH_SHORT).show();
                return;
            }
            if (session.hasFollowUps()) {
                showingFollowUps = true;
                render();
                scrollView.post(() -> scrollView.scrollTo(0, 0));
                return;
            }
        }
        saveCheckIn();
    }

    private void goBack() {
        if (showingFollowUps) {
            showingFollowUps = false;
            render();
            scrollView.post(() -> scrollView.scrollTo(0, 0));
        } else {
            finish();
        }
    }

    private void saveCheckIn() {
        btnPrimary.setEnabled(false);
        final Map<String, List<String>> answers = session.getAnswers();

        AppExecutors.getInstance().diskIO().execute(() -> {
            boolean saved;
            HealthRule urgentRule = null;
            try {
                String cutoff = LocalDate.now().minusDays(DailyCheckinDao.RETENTION_DAYS).toString();
                new DailyCheckinDao(dbHelper).saveCheckinAndTrim(catId, checkinDate, answers, cutoff);
                saved = true;
            } catch (SQLiteException e) {
                Log.e(TAG, "Could not save the check-in", e);
                saved = false;
            }
            if (saved) {
                try {
                    urgentRule = findUrgentRule();
                } catch (SQLiteException e) {
                    Log.w(TAG, "Saved, but could not check health alerts", e);
                }
            }
            final boolean success = saved;
            final HealthRule finalUrgentRule = urgentRule;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (success) {
                    Toast.makeText(this, getString(R.string.checkin_saved, catName), Toast.LENGTH_SHORT).show();
                    if (finalUrgentRule != null) {
                        showUrgentAlert(finalUrgentRule);
                    } else {
                        finish();
                    }
                } else {
                    Toast.makeText(this, R.string.checkin_error_save, Toast.LENGTH_LONG).show();
                    btnPrimary.setEnabled(true);
                }
            });
        });
    }

    /**
     * Background thread. Re-runs the health rules after saving and returns the first urgent
     * one (e.g. possible urinary blockage) that the owner hasn't dismissed, or null.
     */
    private HealthRule findUrgentRule() {
        HealthContent content = HealthContent.load(this);
        if (content == null) return null;
        Map<String, Map<String, List<String>>> answersByDate =
                new DailyCheckinDao(dbHelper).getAnswersByDateForCat(catId);
        List<WeightTrend.Point> weights = WeightPoints.fromLogs(
                new WeightLogDao(dbHelper).getWeightLogsForCat(catId));
        List<HealthAlert> alerts = HealthRuleEvaluator.evaluate(
                content.getRules(), content.getArticlesById(), answersByDate, LocalDate.now(),
                new AppPrefs(this).getDismissedHealthAlertIds(catId), weights);
        for (HealthAlert alert : alerts) {
            if (alert.getRule().isUrgent()) return alert.getRule();
        }
        return null;
    }

    /** Urgent patterns shouldn't wait behind a counter on Daily: tell the owner straight away. */
    private void showUrgentAlert(HealthRule rule) {
        new AlertDialog.Builder(this)
                .setTitle(stringByKey(rule.getTitleKey(), catName))
                .setMessage(stringByKey(rule.getMessageKey(), catName))
                .setPositiveButton(R.string.checkin_urgent_view, (d, w) -> {
                    Intent intent = new Intent(this, HealthAlertsActivity.class);
                    intent.putExtra(HealthAlertsActivity.EXTRA_CAT_ID, catId);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.checkin_urgent_later, null)
                .setOnDismissListener(d -> finish())
                .show();
    }

    // ---- Helpers ----

    private static String chipKey(String groupId, String value) {
        return groupId + "|" + value;
    }

    private String stringByKey(String key, Object... formatArgs) {
        return CheckInContent.text(this, key, formatArgs);
    }
}
