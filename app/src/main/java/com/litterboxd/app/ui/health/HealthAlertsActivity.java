package com.litterboxd.app.ui.health;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.litterboxd.app.BuildConfig;
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
import com.litterboxd.app.health.RecentLogs;
import com.litterboxd.app.health.WeightPoints;
import com.litterboxd.app.health.WeightTrend;
import com.litterboxd.app.location.AmapVetSearch;
import com.litterboxd.app.location.LocationHelper;
import com.litterboxd.app.location.VetClinic;
import com.litterboxd.app.ui.checkin.CheckInActivity;
import com.litterboxd.app.ui.checkin.CheckInContent;
import com.litterboxd.app.util.AppExecutors;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shows pattern-based health alerts for the selected cat. More info searches current reading.
 * Wording stays non-diagnostic ("see a vet" / "worth watching").
 */
public class HealthAlertsActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";

    private static final String TAG = "HealthAlerts";
    private static final int REQUEST_LOCATION = 41;
    private static final String STATE_VETS = "nearby_vets";
    private static final String STATE_VETS_APPROXIMATE = "nearby_vets_approximate";

    private TextView tvSubtitle;
    private View cardAllClear;
    private TextView tvAllClearEmoji, tvAllClearTitle, tvAllClearBody, tvWeekCount;
    private LinearLayout layoutWeekDots;
    private MaterialButton btnAllClearLog;
    private boolean[] recentDays = new boolean[RecentLogs.WEEK];
    private LinearLayout layoutAlerts;

    private View cardNearbyVets;
    private ProgressBar progressVets;
    private TextView tvVetsStatus;
    private LinearLayout layoutVets;
    private MaterialButton btnFindVets;
    private LocationHelper locationHelper;
    private ArrayList<VetClinic> vets = new ArrayList<>();
    private boolean vetsApproximate;
    /** Set when Android will no longer show the permission popup; the button then opens Settings. */
    private boolean locationBlocked;
    private boolean locating;
    private int vetRequestId;

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
        cardAllClear = findViewById(R.id.cardAllClear);
        tvAllClearEmoji = findViewById(R.id.tvAllClearEmoji);
        tvAllClearTitle = findViewById(R.id.tvAllClearTitle);
        tvAllClearBody = findViewById(R.id.tvAllClearBody);
        tvWeekCount = findViewById(R.id.tvWeekCount);
        layoutWeekDots = findViewById(R.id.layoutWeekDots);
        btnAllClearLog = findViewById(R.id.btnAllClearLog);
        btnAllClearLog.setOnClickListener(v -> openCheckIn());
        layoutAlerts = findViewById(R.id.layoutHealthAlerts);
        cardNearbyVets = findViewById(R.id.cardNearbyVets);
        progressVets = findViewById(R.id.progressNearbyVets);
        tvVetsStatus = findViewById(R.id.tvNearbyVetsStatus);
        layoutVets = findViewById(R.id.layoutNearbyVets);
        btnFindVets = findViewById(R.id.btnFindVets);
        locationHelper = new LocationHelper(this);
        btnFindVets.setOnClickListener(v -> onFindVetsClicked());
        if (savedInstanceState != null) {
            restoreVets(savedInstanceState);
        }

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
            final boolean[] finalRecent = RecentLogs.loggedDays(answersByDate, LocalDate.now(), RecentLogs.WEEK);
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
                recentDays = finalRecent;
                tvSubtitle.setText(getString(R.string.health_alerts_subtitle, catName));
                renderAlerts();
            });
        });
    }

    private void renderAlerts() {
        layoutAlerts.removeAllViews();
        cardNearbyVets.setVisibility(alerts.isEmpty() ? View.GONE : View.VISIBLE);
        if (alerts.isEmpty()) {
            renderAllClear();
            return;
        }
        cardAllClear.setVisibility(View.GONE);
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

    // ---- No alerts ----

    /**
     * "All clear" only means something if the cat has been logged; with too few days the card
     * asks for more logs instead. Either way it shows the last 7 days and a log button.
     */
    private void renderAllClear() {
        cardAllClear.setVisibility(View.VISIBLE);
        int logged = RecentLogs.count(recentDays);
        boolean enough = logged >= RecentLogs.ENOUGH_FOR_ALL_CLEAR;
        tvAllClearEmoji.setText(enough ? R.string.all_clear_emoji : R.string.all_clear_few_emoji);
        tvAllClearTitle.setText(enough
                ? getString(R.string.all_clear_title, catName)
                : getString(R.string.all_clear_few_title));
        tvAllClearBody.setText(getString(
                enough ? R.string.all_clear_body : R.string.all_clear_few_body, catName));
        tvWeekCount.setText(getString(R.string.all_clear_week_count, logged));
        boolean loggedToday = recentDays[recentDays.length - 1];
        btnAllClearLog.setText(loggedToday ? R.string.btn_update_log : R.string.btn_log_today);
        renderWeekDots();
    }

    /** Seven columns, oldest on the left: weekday letter over a filled (logged) or empty dot. */
    private void renderWeekDots() {
        layoutWeekDots.removeAllViews();
        int dotSize = getResources().getDimensionPixelSize(R.dimen.week_dot);
        int gap = getResources().getDimensionPixelSize(R.dimen.spacing_tiny);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < recentDays.length; i++) {
            LocalDate day = today.minusDays(recentDays.length - 1 - i);
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            column.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView letter = new TextView(this);
            letter.setText(day.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.getDefault()));
            letter.setTextColor(ContextCompat.getColor(this, R.color.text_dark_gray));
            letter.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            if (i == recentDays.length - 1) letter.setTypeface(null, android.graphics.Typeface.BOLD);
            column.addView(letter);

            View dot = new View(this);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotParams.topMargin = gap;
            dot.setLayoutParams(dotParams);
            dot.setBackgroundResource(recentDays[i]
                    ? R.drawable.bg_calendar_dot_normal : R.drawable.bg_week_dot_empty);
            column.addView(dot);
            layoutWeekDots.addView(column);
        }
    }

    private void openCheckIn() {
        Intent intent = new Intent(this, CheckInActivity.class);
        intent.putExtra(CheckInActivity.EXTRA_CAT_ID, catId);
        startActivity(intent);
    }

    // ---- Nearby vets (AMap) ----

    @SuppressWarnings("unchecked")
    private void restoreVets(Bundle state) {
        Object saved = state.getSerializable(STATE_VETS);
        if (saved instanceof ArrayList) vets = (ArrayList<VetClinic>) saved;
        vetsApproximate = state.getBoolean(STATE_VETS_APPROXIMATE);
        renderVets();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_VETS, vets);
        outState.putBoolean(STATE_VETS_APPROXIMATE, vetsApproximate);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't keep GPS running in the background; the owner can tap again.
        if (locating) {
            locationHelper.stop();
            locating = false;
            vetRequestId++;
            showVetsStatus(R.string.vets_stopped);
        }
    }

    private void onFindVetsClicked() {
        if (locationBlocked) {
            openAppSettings();
            return;
        }
        if (!new AmapVetSearch(BuildConfig.AMAP_KEY).hasKey()) {
            showVetsStatus(R.string.vets_not_set_up);
            return;
        }
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            locateAndSearch();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION) return;
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            locateAndSearch();
            return;
        }
        locationBlocked = !ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION);
        btnFindVets.setText(locationBlocked ? R.string.btn_open_settings : R.string.btn_find_vets);
        showVetsStatus(locationBlocked ? R.string.vets_location_blocked : R.string.vets_location_denied);
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void openAppSettings() {
        locationBlocked = false;
        btnFindVets.setText(R.string.btn_find_vets);
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    private void locateAndSearch() {
        final int id = ++vetRequestId;
        vetsApproximate = !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        showVetsBusy(R.string.vets_locating);
        locating = true;
        locationHelper.getSingleLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onLocationResult(Location location) {
                if (id != vetRequestId) return;
                locating = false;
                searchVets(id, location.getLatitude(), location.getLongitude());
            }

            @Override
            public void onError(String errorMessage) {
                if (id != vetRequestId) return;
                locating = false;
                Log.w(TAG, "No location: " + errorMessage);
                showVetsStatus(R.string.vets_no_location);
            }
        });
    }

    /** The position is only sent to AMap for this one search; it is never stored. */
    private void searchVets(int id, double latitude, double longitude) {
        showVetsBusy(R.string.vets_searching);
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<VetClinic> found = null;
            int error = 0;
            try {
                found = new AmapVetSearch(BuildConfig.AMAP_KEY).findNearby(latitude, longitude);
            } catch (IOException e) {
                Log.w(TAG, "Vet search network error", e);
                error = R.string.vets_network_error;
            } catch (AmapVetSearch.ServiceException e) {
                Log.w(TAG, "AMap refused the vet search: " + e.getMessage());
                error = R.string.vets_service_error;
            }
            final List<VetClinic> finalFound = found;
            final int finalError = error;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed() || id != vetRequestId) return;
                if (finalError != 0) {
                    showVetsStatus(finalError);
                    return;
                }
                vets = new ArrayList<>(finalFound);
                btnFindVets.setText(R.string.btn_find_vets_again);
                if (vets.isEmpty()) {
                    showVetsStatus(R.string.vets_none);
                } else {
                    renderVets();
                }
            });
        });
    }

    private void showVetsBusy(int messageRes) {
        vets.clear();
        layoutVets.removeAllViews();
        progressVets.setVisibility(View.VISIBLE);
        tvVetsStatus.setVisibility(View.VISIBLE);
        tvVetsStatus.setText(messageRes);
        btnFindVets.setEnabled(false);
    }

    private void showVetsStatus(int messageRes) {
        progressVets.setVisibility(View.GONE);
        tvVetsStatus.setVisibility(View.VISIBLE);
        tvVetsStatus.setText(messageRes);
        btnFindVets.setEnabled(true);
    }

    /** Up to 3 clinics, closest first and highlighted. */
    private void renderVets() {
        layoutVets.removeAllViews();
        progressVets.setVisibility(View.GONE);
        btnFindVets.setEnabled(true);
        if (vets.isEmpty()) {
            tvVetsStatus.setVisibility(View.GONE);
            return;
        }
        if (vetsApproximate) {
            tvVetsStatus.setVisibility(View.VISIBLE);
            tvVetsStatus.setText(R.string.vets_approximate);
        } else {
            tvVetsStatus.setVisibility(View.GONE);
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < vets.size(); i++) {
            VetClinic vet = vets.get(i);
            MaterialCardView row = (MaterialCardView) inflater.inflate(
                    R.layout.item_nearby_vet, layoutVets, false);
            boolean closest = i == 0;
            row.findViewById(R.id.tvVetClosest).setVisibility(closest ? View.VISIBLE : View.GONE);
            if (closest) {
                row.setCardBackgroundColor(ContextCompat.getColor(this, R.color.pastel_sage));
                row.setStrokeColor(ContextCompat.getColor(this, R.color.sage_primary));
            }
            ((TextView) row.findViewById(R.id.tvVetName)).setText(vet.getName());
            setOptional(row.findViewById(R.id.tvVetDistance), distanceText(vet.getDistanceMeters()));
            setOptional(row.findViewById(R.id.tvVetAddress), vet.getAddress());
            setOptional(row.findViewById(R.id.tvVetPhone), vet.getPhone().isEmpty()
                    ? "" : getString(R.string.vets_phone, vet.getPhone()));
            layoutVets.addView(row);
        }
    }

    private String distanceText(int meters) {
        if (meters == VetClinic.UNKNOWN_DISTANCE) return "";
        if (meters < 1000) return getString(R.string.vets_distance_m, meters);
        return getString(R.string.vets_distance_km, meters / 1000.0);
    }

    private static void setOptional(TextView view, String text) {
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setText(text);
    }
}
