package com.litterboxd.app.ui.diary;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.litterboxd.app.R;
import com.litterboxd.app.checkin.AnswerOption;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.Question;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DailyCheckinDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.dao.WeightLogDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DailyCheckin;
import com.litterboxd.app.data.model.DiaryEntry;
import com.litterboxd.app.data.model.WeightLog;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.checkin.CheckInActivity;
import com.litterboxd.app.ui.checkin.CheckInContent;
import com.litterboxd.app.ui.home.CatSwitcher;
import com.litterboxd.app.ui.nav.BottomNavHelper;
import com.litterboxd.app.ui.profile.CatProfileActivity;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;
import com.litterboxd.app.util.DiaryDates;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Diary tab: a personal archive of the selected cat's life. Shows the owner's memories and
 * milestones (diary_entries) mixed with a quiet line of care history read straight from the
 * existing weight and check-in tables, newest first under date headings, with simple filters.
 */
public class DiaryActivity extends AppCompatActivity {

    private static final String STATE_FILTER = "diary_filter";
    private static final int FILTER_ALL = 0, FILTER_MEMORIES = 1, FILTER_MILESTONES = 2, FILTER_HISTORY = 3;
    /** How many photos the "Recent snaps" strip shows. */
    private static final int MAX_STRIP_PHOTOS = 12;

    /** One row of the timeline: a diary entry or a piece of care history. */
    private static final class TimelineItem {
        static final int ENTRY = 0, WEIGHT = 1, CHECKIN = 2;
        final int kind;
        final long time;
        DiaryEntry entry;
        WeightLog weight;
        DailyCheckin checkin;

        TimelineItem(int kind, long time) {
            this.kind = kind;
            this.time = time;
        }
    }

    private TextView tvCatName, tvCounts, tvTimelineEmpty, tvRecentPhotos, tvNoPhotosBody;
    private ShapeableImageView imgCat;
    private LinearLayout layoutTimeline, layoutPhotos;
    private View scrollPhotos, cardNoPhotos;
    private MaterialButton btnAddMemory, btnSwitchCat;
    private MaterialButton[] filterButtons;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private DiaryDao diaryDao;
    private WeightLogDao weightLogDao;
    private DailyCheckinDao checkinDao;
    private FileStorageHelper fileStorageHelper;
    private DecisionTree checkInTree;

    private Cat currentCat;
    private List<Cat> allCats = new ArrayList<>();
    private List<TimelineItem> items = new ArrayList<>();
    private int filter = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary);

        appPrefs = new AppPrefs(this);
        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        diaryDao = new DiaryDao(dbHelper);
        weightLogDao = new WeightLogDao(dbHelper);
        checkinDao = new DailyCheckinDao(dbHelper);
        fileStorageHelper = new FileStorageHelper(this);

        tvCatName = findViewById(R.id.tvDiaryCatName);
        tvCounts = findViewById(R.id.tvDiaryCounts);
        imgCat = findViewById(R.id.imgDiaryCat);
        tvTimelineEmpty = findViewById(R.id.tvTimelineEmpty);
        tvRecentPhotos = findViewById(R.id.tvRecentPhotos);
        tvNoPhotosBody = findViewById(R.id.tvNoPhotosBody);
        layoutTimeline = findViewById(R.id.layoutTimeline);
        layoutPhotos = findViewById(R.id.layoutDiaryPhotos);
        scrollPhotos = findViewById(R.id.scrollDiaryPhotos);
        cardNoPhotos = findViewById(R.id.cardNoPhotos);
        btnAddMemory = findViewById(R.id.btnAddMemory);
        btnSwitchCat = findViewById(R.id.btnDiarySwitchCat);

        BottomNavHelper.setup(this, (BottomNavigationView) findViewById(R.id.bottomNav), R.id.nav_diary);
        btnAddMemory.setOnClickListener(v -> openEditor(-1L));
        findViewById(R.id.btnAddFirstMemory).setOnClickListener(v -> openEditor(-1L));
        btnSwitchCat.setOnClickListener(v -> CatSwitcher.show(this, allCats, cat -> loadData()));

        filterButtons = new MaterialButton[]{
                findViewById(R.id.btnFilterAll), findViewById(R.id.btnFilterMemories),
                findViewById(R.id.btnFilterMilestones), findViewById(R.id.btnFilterHistory)};
        for (int i = 0; i < filterButtons.length; i++) {
            final int which = i;
            filterButtons[i].setOnClickListener(v -> {
                filter = which;
                renderTimeline();
            });
        }
        if (savedInstanceState != null) {
            filter = savedInstanceState.getInt(STATE_FILTER, FILTER_ALL);
        }
        updateFilterButtons();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILTER, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload when shown: the selected cat, entries, weights or check-ins may have changed.
        loadData();
    }

    /** Back on a secondary tab returns to the Daily tab rather than leaving the app. */
    @Override
    public void onBackPressed() {
        BottomNavHelper.openTab(this, R.id.nav_daily);
    }

    private void openEditor(long entryId) {
        if (currentCat == null) return;
        Intent intent = new Intent(this, DiaryEntryEditActivity.class);
        intent.putExtra(DiaryEntryEditActivity.EXTRA_CAT_ID, currentCat.getId());
        intent.putExtra(DiaryEntryEditActivity.EXTRA_ENTRY_ID, entryId);
        startActivity(intent);
    }

    // ---- Loading ----

    private void loadData() {
        final DecisionTree cachedTree = checkInTree;
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
            DecisionTree tree = cachedTree != null ? cachedTree : CheckInContent.loadTree(this);

            // Every source is filtered by the selected cat's id.
            List<TimelineItem> loaded = new ArrayList<>();
            if (selected != null) {
                for (DiaryEntry entry : diaryDao.getEntriesForCat(selected.getId())) {
                    TimelineItem item = new TimelineItem(TimelineItem.ENTRY, entry.getEntryAt());
                    item.entry = entry;
                    loaded.add(item);
                }
                for (WeightLog log : weightLogDao.getWeightLogsForCat(selected.getId())) {
                    TimelineItem item = new TimelineItem(TimelineItem.WEIGHT, log.getDate());
                    item.weight = log;
                    loaded.add(item);
                }
                for (DailyCheckin checkin : checkinDao.getCheckinsForCat(selected.getId())) {
                    TimelineItem item = new TimelineItem(TimelineItem.CHECKIN, checkin.getCreatedAt());
                    item.checkin = checkin;
                    loaded.add(item);
                }
                // Newest first; Collections.sort is stable, so equal times keep each source's order.
                Collections.sort(loaded, (a, b) -> Long.compare(b.time, a.time));
            }
            final Cat finalSelected = selected;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                allCats = cats;
                currentCat = finalSelected;
                checkInTree = tree;
                items = loaded;
                renderHeader();
                renderPhotoStrip();
                renderTimeline();
            });
        });
    }

    // ---- Header and photo strip ----

    private void renderHeader() {
        btnAddMemory.setEnabled(currentCat != null);
        btnSwitchCat.setVisibility(currentCat != null ? View.VISIBLE : View.GONE);
        if (currentCat == null) {
            tvCatName.setText(R.string.no_cat_selected);
            tvCounts.setText(R.string.daily_no_cat);
            imgCat.setImageResource(R.drawable.ic_cat_face);
            return;
        }
        tvCatName.setText(currentCat.getName());
        imgCat.setContentDescription(getString(R.string.cd_cat_photo, currentCat.getName()));
        fileStorageHelper.showCatPhoto(imgCat, currentCat.getPhotoPath());
        int memories = 0, milestones = 0;
        for (TimelineItem item : items) {
            if (item.kind != TimelineItem.ENTRY) continue;
            if (item.entry.getType() == DiaryEntry.Type.MILESTONE) milestones++; else memories++;
        }
        tvCounts.setText(getString(R.string.diary_counts,
                getResources().getQuantityString(R.plurals.diary_memory_count, memories, memories),
                getResources().getQuantityString(R.plurals.diary_milestone_count, milestones, milestones)));
    }

    private void renderPhotoStrip() {
        layoutPhotos.removeAllViews();
        int size = getResources().getDimensionPixelSize(R.dimen.diary_strip_photo_size);
        int gap = getResources().getDimensionPixelSize(R.dimen.spacing_small);
        int photos = 0, shown = 0;
        for (TimelineItem item : items) {
            if (item.kind != TimelineItem.ENTRY || !item.entry.hasPhoto()) continue;
            photos++;
            if (shown >= MAX_STRIP_PHOTOS) continue;
            shown++;
            final DiaryEntry entry = item.entry;
            ShapeableImageView photo = (ShapeableImageView) LayoutInflater.from(this)
                    .inflate(R.layout.item_diary_strip_photo, layoutPhotos, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(gap);
            photo.setLayoutParams(params);
            photo.setContentDescription(getString(R.string.cd_diary_photo, entry.getTitle()));
            fileStorageHelper.showImageFile(photo, entry.getPhotoPath(), R.drawable.ic_cat_face);
            photo.setOnClickListener(v -> openEditor(entry.getId()));
            layoutPhotos.addView(photo);
        }
        boolean hasPhotos = photos > 0;
        scrollPhotos.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        cardNoPhotos.setVisibility(hasPhotos || currentCat == null ? View.GONE : View.VISIBLE);
        tvRecentPhotos.setText(hasPhotos
                ? getString(R.string.diary_counts, getString(R.string.diary_recent_photos),
                getResources().getQuantityString(R.plurals.diary_photo_count, photos, photos))
                : getString(R.string.diary_recent_photos));
        if (currentCat != null) {
            tvNoPhotosBody.setText(getString(R.string.diary_no_photos_body, currentCat.getName()));
        }
    }

    // ---- Timeline ----

    private void updateFilterButtons() {
        for (int i = 0; i < filterButtons.length; i++) {
            filterButtons[i].setChecked(i == filter);
        }
    }

    private boolean matchesFilter(TimelineItem item) {
        switch (filter) {
            case FILTER_MEMORIES:
                return item.kind == TimelineItem.ENTRY && item.entry.getType() == DiaryEntry.Type.MEMORY;
            case FILTER_MILESTONES:
                return item.kind == TimelineItem.ENTRY && item.entry.getType() == DiaryEntry.Type.MILESTONE;
            case FILTER_HISTORY:
                return item.kind != TimelineItem.ENTRY;
            default:
                return true;
        }
    }

    private void renderTimeline() {
        updateFilterButtons();
        layoutTimeline.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        long now = System.currentTimeMillis();
        String lastHeading = null;
        int shown = 0;
        for (TimelineItem item : items) {
            if (!matchesFilter(item)) continue;
            // One bad record must not break the whole diary.
            try {
                DiaryDates.Heading heading = DiaryDates.headingFor(item.time, now);
                if (!heading.key().equals(lastHeading)) {
                    TextView title = (TextView) inflater.inflate(R.layout.item_diary_heading, layoutTimeline, false);
                    title.setText(headingText(heading, item.time));
                    ViewCompat.setAccessibilityHeading(title, true);
                    layoutTimeline.addView(title);
                    lastHeading = heading.key();
                }
                layoutTimeline.addView(item.kind == TimelineItem.ENTRY
                        ? createEntryCard(inflater, item.entry)
                        : createHistoryRow(inflater, item));
                shown++;
            } catch (RuntimeException e) {
                Log.w("DiaryActivity", "Skipping a diary item that could not be shown", e);
            }
        }
        if (shown == 0 && currentCat != null) {
            tvTimelineEmpty.setVisibility(View.VISIBLE);
            tvTimelineEmpty.setText(emptyText());
        } else {
            tvTimelineEmpty.setVisibility(View.GONE);
        }
    }

    private String emptyText() {
        switch (filter) {
            case FILTER_MEMORIES: return getString(R.string.diary_empty_memories);
            case FILTER_MILESTONES: return getString(R.string.diary_empty_milestones);
            case FILTER_HISTORY: return getString(R.string.diary_empty_history);
            default: return getString(R.string.diary_empty_all, currentCat.getName());
        }
    }

    private String headingText(DiaryDates.Heading heading, long time) {
        Locale locale = Locale.getDefault();
        Date date = new Date(time);
        switch (heading.kind) {
            case TODAY: return getString(R.string.diary_today);
            case YESTERDAY: return getString(R.string.diary_yesterday);
            case DAY:
                boolean thisYear = heading.year == Calendar.getInstance().get(Calendar.YEAR);
                return DateFormat.format(DateFormat.getBestDateTimePattern(locale, thisYear ? "MMMMd" : "yMMMMd"), date).toString();
            default:
                return DateFormat.format(DateFormat.getBestDateTimePattern(locale, "yMMMM"), date).toString();
        }
    }

    private View createEntryCard(LayoutInflater inflater, DiaryEntry entry) {
        View card = inflater.inflate(R.layout.item_diary_entry, layoutTimeline, false);
        ShapeableImageView thumb = card.findViewById(R.id.imgDiaryThumb);
        TextView type = card.findViewById(R.id.tvDiaryType);
        TextView title = card.findViewById(R.id.tvDiaryEntryTitle);
        TextView caption = card.findViewById(R.id.tvDiaryCaption);
        TextView meta = card.findViewById(R.id.tvDiaryMeta);

        boolean milestone = entry.getType() == DiaryEntry.Type.MILESTONE;
        // Milestones: star + warm card; memories: the cat mascot. Label text, not colour alone.
        type.setText(milestone ? R.string.diary_type_milestone : R.string.diary_type_memory);
        type.setCompoundDrawablesRelativeWithIntrinsicBounds(typeIcon(milestone), null, null, null);
        if (milestone) {
            ((MaterialCardView) card)
                    .setCardBackgroundColor(ContextCompat.getColor(this, R.color.pastel_coral));
        }
        title.setText(entry.getTitle());
        if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
            caption.setVisibility(View.VISIBLE);
            caption.setText(entry.getDescription());
        }
        // Milestones are about the day; memories also show the time.
        String when = milestone ? DateFormatUtils.formatDate(entry.getEntryAt()) : DateFormatUtils.formatTime(entry.getEntryAt());
        String tags = tagLabels(entry.getTags());
        meta.setText(tags.isEmpty() ? when : getString(R.string.diary_meta_tags, when, tags));
        if (entry.hasPhoto()) {
            thumb.setVisibility(View.VISIBLE);
            thumb.setContentDescription(getString(R.string.cd_diary_photo, entry.getTitle()));
            // Missing or unreadable file: the placeholder stays.
            fileStorageHelper.showImageFile(thumb, entry.getPhotoPath(), R.drawable.ic_cat_face);
        }
        card.setOnClickListener(v -> openEditor(entry.getId()));
        return card;
    }

    private Drawable typeIcon(boolean milestone) {
        Drawable icon = ContextCompat.getDrawable(this,
                milestone ? R.drawable.ic_star : R.drawable.ic_cat_face);
        if (icon == null) return null;
        icon = icon.mutate();
        int size = getResources().getDimensionPixelSize(R.dimen.diary_type_icon_size);
        icon.setBounds(0, 0, size, size);
        icon.setTint(ContextCompat.getColor(this, milestone ? R.color.muted_pinkish_brown : R.color.sage_primary));
        return icon;
    }

    private String tagLabels(List<String> keys) {
        String[] allKeys = getResources().getStringArray(R.array.diary_tag_keys);
        String[] labels = getResources().getStringArray(R.array.diary_tag_labels);
        List<String> shown = new ArrayList<>();
        for (String key : keys) {
            for (int i = 0; i < allKeys.length && i < labels.length; i++) {
                if (allKeys[i].equals(key)) shown.add("#" + labels[i]);
            }
        }
        return String.join(" ", shown);
    }

    /** Care history stays small and quiet: one line plus "Logged observation, not a diagnosis". */
    private View createHistoryRow(LayoutInflater inflater, TimelineItem item) {
        View row = inflater.inflate(R.layout.item_diary_history, layoutTimeline, false);
        TextView text = row.findViewById(R.id.tvHistoryText);
        if (item.kind == TimelineItem.WEIGHT) {
            WeightLog log = item.weight;
            String kg = String.valueOf(log.getWeight());
            text.setText(log.getNotes() != null && !log.getNotes().isEmpty()
                    ? getString(R.string.diary_history_weight_note, kg, log.getNotes())
                    : getString(R.string.diary_history_weight, kg));
            // The cat profile is where weight history lives.
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, CatProfileActivity.class);
                intent.putExtra("cat_id", log.getCatId());
                startActivity(intent);
            });
        } else {
            DailyCheckin checkin = item.checkin;
            text.setText(getString(R.string.diary_history_checkin, checkinSummary(checkin)));
            // Only today's check-in can be opened (and updated); past days have no detail screen.
            if (LocalDate.now().toString().equals(checkin.getCheckinDate())) {
                row.setOnClickListener(v -> {
                    Intent intent = new Intent(this, CheckInActivity.class);
                    intent.putExtra(CheckInActivity.EXTRA_CAT_ID, checkin.getCatId());
                    startActivity(intent);
                });
            } else {
                row.setClickable(false);
                row.setBackground(null);
            }
        }
        return row;
    }

    /** The answers that weren't the "usual" choice, e.g. "Ate less, Low energy", else "all as usual". */
    private String checkinSummary(DailyCheckin checkin) {
        if (checkInTree == null) return getString(R.string.diary_history_all_normal);
        List<String> unusual = new ArrayList<>();
        for (Question group : checkInTree.getGroups()) {
            List<String> values = checkin.getAnswers().get(group.getId());
            if (values == null) continue;
            for (String value : values) {
                AnswerOption option = group.getOption(value);
                if (option != null && !option.isNormal()) {
                    unusual.add(CheckInContent.text(this, option.getLabelKey()));
                }
            }
        }
        return unusual.isEmpty() ? getString(R.string.diary_history_all_normal) : String.join(", ", unusual);
    }
}
