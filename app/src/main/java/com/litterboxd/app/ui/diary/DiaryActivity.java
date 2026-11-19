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
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DiaryEntry;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.home.CatSwitcher;
import com.litterboxd.app.ui.nav.BottomNavHelper;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DiaryDates;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Diary tab: a personal archive of the selected cat's life. Shows only the owner's own memories
 * and milestones (diary_entries), newest first under date headings, with simple filters.
 * Check-ins and weigh-ins stay on the Daily and profile screens so the diary reads as a diary.
 */
public class DiaryActivity extends AppCompatActivity {

    private static final String STATE_FILTER = "diary_filter";
    private static final int FILTER_ALL = 0, FILTER_MEMORIES = 1, FILTER_MILESTONES = 2;

    private TextView tvCatName, tvCounts, tvTimelineEmpty, tvMemoriesSubtitle, tvNoPhotosBody;
    private ShapeableImageView imgCat, imgMemoriesPreview;
    private LinearLayout layoutTimeline;
    private View cardMemories, cardNoPhotos;
    private MaterialButton btnAddMemory, btnSwitchCat;
    private MaterialButton[] filterButtons;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private DiaryDao diaryDao;
    private FileStorageHelper fileStorageHelper;

    private Cat currentCat;
    private List<Cat> allCats = new ArrayList<>();
    private List<DiaryEntry> items = new ArrayList<>();
    private int filter = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary);

        appPrefs = new AppPrefs(this);
        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        diaryDao = new DiaryDao(dbHelper);
        fileStorageHelper = new FileStorageHelper(this);

        tvCatName = findViewById(R.id.tvDiaryCatName);
        tvCounts = findViewById(R.id.tvDiaryCounts);
        imgCat = findViewById(R.id.imgDiaryCat);
        tvTimelineEmpty = findViewById(R.id.tvTimelineEmpty);
        tvMemoriesSubtitle = findViewById(R.id.tvMemoriesSubtitle);
        tvNoPhotosBody = findViewById(R.id.tvNoPhotosBody);
        layoutTimeline = findViewById(R.id.layoutTimeline);
        imgMemoriesPreview = findViewById(R.id.imgMemoriesPreview);
        cardMemories = findViewById(R.id.cardMemories);
        cardNoPhotos = findViewById(R.id.cardNoPhotos);
        btnAddMemory = findViewById(R.id.btnAddMemory);
        btnSwitchCat = findViewById(R.id.btnDiarySwitchCat);

        BottomNavHelper.setup(this, (BottomNavigationView) findViewById(R.id.bottomNav), R.id.nav_diary);
        btnAddMemory.setOnClickListener(v -> openEditor(-1L));
        findViewById(R.id.btnAddFirstMemory).setOnClickListener(v -> openEditor(-1L));
        btnSwitchCat.setOnClickListener(v -> CatSwitcher.show(this, allCats, cat -> loadData()));
        cardMemories.setOnClickListener(v -> openMemories());

        filterButtons = new MaterialButton[]{
                findViewById(R.id.btnFilterAll), findViewById(R.id.btnFilterMemories),
                findViewById(R.id.btnFilterMilestones)};
        for (int i = 0; i < filterButtons.length; i++) {
            final int which = i;
            filterButtons[i].setOnClickListener(v -> {
                filter = which;
                renderTimeline();
            });
        }
        if (savedInstanceState != null) {
            // Clamp: an older build also had a "Care history" filter (index 3).
            filter = Math.min(savedInstanceState.getInt(STATE_FILTER, FILTER_ALL), FILTER_MILESTONES);
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

    /** Tapping an entry reads it first; editing is one tap away on that screen. */
    private void openDetail(long entryId) {
        if (currentCat == null) return;
        Intent intent = new Intent(this, DiaryEntryDetailActivity.class);
        intent.putExtra(DiaryEntryDetailActivity.EXTRA_CAT_ID, currentCat.getId());
        intent.putExtra(DiaryEntryDetailActivity.EXTRA_ENTRY_ID, entryId);
        startActivity(intent);
    }

    private void openEditor(long entryId) {
        if (currentCat == null) return;
        Intent intent = new Intent(this, DiaryEntryEditActivity.class);
        intent.putExtra(DiaryEntryEditActivity.EXTRA_CAT_ID, currentCat.getId());
        intent.putExtra(DiaryEntryEditActivity.EXTRA_ENTRY_ID, entryId);
        startActivity(intent);
    }

    /** Opens the full photo collage for the selected cat's memories. */
    private void openMemories() {
        if (currentCat == null) return;
        Intent intent = new Intent(this, DiaryMemoriesActivity.class);
        intent.putExtra(DiaryMemoriesActivity.EXTRA_CAT_ID, currentCat.getId());
        startActivity(intent);
    }

    // ---- Loading ----

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
            // Newest first (DiaryDao orders by entry time), for the selected cat only.
            List<DiaryEntry> loaded = selected != null
                    ? diaryDao.getEntriesForCat(selected.getId()) : new ArrayList<>();
            final Cat finalSelected = selected;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                allCats = cats;
                currentCat = finalSelected;
                items = loaded;
                renderHeader();
                renderMemoriesCard();
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
        for (DiaryEntry entry : items) {
            if (entry.getType() == DiaryEntry.Type.MILESTONE) milestones++; else memories++;
        }
        tvCounts.setText(getString(R.string.diary_counts,
                getResources().getQuantityString(R.plurals.diary_memory_count, memories, memories),
                getResources().getQuantityString(R.plurals.diary_milestone_count, milestones, milestones)));
    }

    /** Updates the "Memories" banner: a preview of the latest photo and how many there are. */
    private void renderMemoriesCard() {
        DiaryEntry latestWithPhoto = null;
        int photos = 0;
        for (DiaryEntry entry : items) {
            if (!entry.hasPhoto()) continue;
            photos++;
            if (latestWithPhoto == null) latestWithPhoto = entry; // items is newest-first
        }
        boolean hasPhotos = photos > 0;
        cardMemories.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        cardNoPhotos.setVisibility(hasPhotos || currentCat == null ? View.GONE : View.VISIBLE);
        if (hasPhotos) {
            tvMemoriesSubtitle.setText(getResources().getQuantityString(R.plurals.diary_photo_count, photos, photos));
            fileStorageHelper.showImageFile(imgMemoriesPreview, latestWithPhoto.getPhotoPath(), R.drawable.ic_cat_face);
        }
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

    private boolean matchesFilter(DiaryEntry entry) {
        switch (filter) {
            case FILTER_MEMORIES: return entry.getType() == DiaryEntry.Type.MEMORY;
            case FILTER_MILESTONES: return entry.getType() == DiaryEntry.Type.MILESTONE;
            default: return true;
        }
    }

    private void renderTimeline() {
        updateFilterButtons();
        layoutTimeline.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        long now = System.currentTimeMillis();
        String lastHeading = null;
        int shown = 0;
        for (DiaryEntry entry : items) {
            if (!matchesFilter(entry)) continue;
            // One bad record must not break the whole diary.
            try {
                DiaryDates.Heading heading = DiaryDates.headingFor(entry.getEntryAt(), now);
                if (!heading.key().equals(lastHeading)) {
                    TextView title = (TextView) inflater.inflate(R.layout.item_diary_heading, layoutTimeline, false);
                    title.setText(headingText(heading, entry.getEntryAt()));
                    ViewCompat.setAccessibilityHeading(title, true);
                    layoutTimeline.addView(title);
                    lastHeading = heading.key();
                }
                layoutTimeline.addView(createEntryCard(inflater, entry));
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
        // The date heading above already says when; the card only adds the owner's tags.
        String tags = tagLabels(entry.getTags());
        meta.setVisibility(tags.isEmpty() ? View.GONE : View.VISIBLE);
        meta.setText(tags);
        if (entry.hasPhoto()) {
            thumb.setVisibility(View.VISIBLE);
            thumb.setContentDescription(getString(R.string.cd_diary_photo, entry.getTitle()));
            // Missing or unreadable file: the placeholder stays.
            fileStorageHelper.showImageFile(thumb, entry.getPhotoPath(), R.drawable.ic_cat_face);
        }
        card.setOnClickListener(v -> openDetail(entry.getId()));
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
}
