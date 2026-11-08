package com.litterboxd.app.ui.health;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.health.HealthAlert;
import com.litterboxd.app.health.HealthArticle;
import com.litterboxd.app.health.HealthContent;
import com.litterboxd.app.health.HealthRule;
import com.litterboxd.app.health.HealthRuleEvaluator;
import com.litterboxd.app.health.WeightPoints;
import com.litterboxd.app.health.WeightTrend;
import com.litterboxd.app.ui.checkin.CheckInContent;
import com.litterboxd.app.util.AppExecutors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shows pattern-based health alerts for the selected cat. More info searches current reading.
 * Wording stays non-diagnostic ("see a vet" / "worth watching").
 */
public class HealthAlertsActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";

    private TextView tvSubtitle, tvEmpty;
    private LinearLayout layoutAlerts;

    private AppPrefs appPrefs;
    private long catId = -1L;
    private String catName = "";
    private HealthContent healthContent;
    private List<HealthAlert> alerts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_alerts);

        appPrefs = new AppPrefs(this);
        tvSubtitle = findViewById(R.id.tvHealthAlertsSubtitle);
        tvEmpty = findViewById(R.id.tvHealthAlertsEmpty);
        layoutAlerts = findViewById(R.id.layoutHealthAlerts);

        catId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        if (catId == -1L) {
            catId = appPrefs.getSelectedCatId();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAlerts();
    }

    private void loadAlerts() {
        final long id = catId;
        AppExecutors.getInstance().diskIO().execute(() -> {
            LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(this);
            Cat cat = new CatDao(db).getCatById(id);
            HealthContent content = HealthContent.load(this);
            Map<String, Map<String, List<String>>> answersByDate = cat == null
                    ? null
                    : new DailyCheckinDao(db).getAnswersByDateForCat(cat.getId());
            List<WeightTrend.Point> weights = cat == null
                    ? new ArrayList<>()
                    : WeightPoints.fromLogs(new WeightLogDao(db).getWeightLogsForCat(cat.getId()));
            Set<String> dismissed = cat == null
                    ? new HashSet<>()
                    : appPrefs.getDismissedHealthAlertIds(cat.getId());

            List<HealthAlert> found = new ArrayList<>();
            if (content != null && cat != null && answersByDate != null) {
                // Drop dismissals for rules that no longer match, so they can return later.
                List<HealthAlert> allMatching = HealthRuleEvaluator.evaluate(
                        content.getRules(), content.getArticlesById(), answersByDate,
                        LocalDate.now(), null, weights);
                Set<String> stillMatching = new HashSet<>();
                for (HealthAlert alert : allMatching) {
                    stillMatching.add(alert.getRule().getId());
                }
                Set<String> keptDismissed = new HashSet<>();
                for (String dismissedId : dismissed) {
                    if (stillMatching.contains(dismissedId)) {
                        keptDismissed.add(dismissedId);
                    }
                }
                if (keptDismissed.size() != dismissed.size()) {
                    appPrefs.setDismissedHealthAlertIds(cat.getId(), keptDismissed);
                }
                found = HealthRuleEvaluator.evaluate(
                        content.getRules(), content.getArticlesById(), answersByDate,
                        LocalDate.now(), keptDismissed, weights);
            }

            final Cat finalCat = cat;
            final HealthContent finalContent = content;
            final List<HealthAlert> finalAlerts = found;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalCat == null || finalContent == null) {
                    Toast.makeText(this, R.string.health_alerts_error, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                catId = finalCat.getId();
                catName = finalCat.getName();
                healthContent = finalContent;
                alerts = finalAlerts;
                tvSubtitle.setText(getString(R.string.health_alerts_subtitle, catName));
                renderAlerts();
            });
        });
    }

    private void renderAlerts() {
        layoutAlerts.removeAllViews();
        if (alerts.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (HealthAlert alert : alerts) {
            View card = inflater.inflate(R.layout.item_health_alert, layoutAlerts, false);
            TextView severity = card.findViewById(R.id.tvAlertSeverity);
            TextView title = card.findViewById(R.id.tvAlertTitle);
            TextView message = card.findViewById(R.id.tvAlertMessage);
            TextView reasons = card.findViewById(R.id.tvAlertReasons);
            TextView source = card.findViewById(R.id.tvAlertSource);
            MaterialButton btnArticles = card.findViewById(R.id.btnAlertArticles);
            MaterialButton btnDismiss = card.findViewById(R.id.btnAlertDismiss);

            HealthRule rule = alert.getRule();
            if (rule.isUrgent()) {
                severity.setText(R.string.alert_severity_urgent);
                severity.setTextColor(ContextCompat.getColor(this, R.color.error_red));
                ((MaterialCardView) card).setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.pastel_coral));
            } else if (rule.isVetVisit()) {
                severity.setText(R.string.alert_severity_vet_visit);
                severity.setTextColor(ContextCompat.getColor(this, R.color.coral_secondary));
                ((MaterialCardView) card).setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.pastel_coral));
            } else {
                severity.setText(R.string.alert_severity_watch);
                severity.setTextColor(ContextCompat.getColor(this, R.color.sage_primary));
                ((MaterialCardView) card).setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.pastel_sage));
            }
            title.setText(CheckInContent.text(this, rule.getTitleKey(), catName));
            message.setText(CheckInContent.text(this, rule.getMessageKey(), catName));
            reasons.setText(CheckInContent.text(this, rule.getReasonsKey()));
            HealthArticle article = HealthCitations.sourceOf(alert);
            source.setText(HealthCitations.sourceLine(this, article));
            source.setOnClickListener(v -> HealthCitations.open(this, article));

            btnArticles.setOnClickListener(v -> openArticles(rule.getId()));
            btnDismiss.setOnClickListener(v -> dismissAlert(rule.getId()));
            layoutAlerts.addView(card);
        }
    }

    private void openArticles(String ruleId) {
        Intent intent = new Intent(this, HealthArticlesActivity.class);
        intent.putExtra(HealthArticlesActivity.EXTRA_CAT_ID, catId);
        intent.putExtra(HealthArticlesActivity.EXTRA_RULE_ID, ruleId);
        startActivity(intent);
    }

    private void dismissAlert(String ruleId) {
        appPrefs.dismissHealthAlert(catId, ruleId);
        Toast.makeText(this, R.string.health_alert_dismissed, Toast.LENGTH_SHORT).show();
        loadAlerts();
    }
}
