package com.litterboxd.app.ui.diary;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DiaryEntry;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one diary entry: the photo large and sharp in a rounded 4:5 frame, then the story text.
 * Edit opens DiaryEntryEditActivity; coming back reloads, and a deleted entry closes this screen.
 */
public class DiaryEntryDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    public static final String EXTRA_ENTRY_ID = "diary_entry_id";

    private long catId;
    private long entryId;
    private View layoutPhoto;
    private ImageView imgPhoto;
    private TextView tvPhotoHint, tvCat, tvType, tvDate, tvTitle, tvText, tvTags;
    /** false = centerCrop (fills the frame), true = fitCenter (whole photo). */
    private boolean showWholePhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary_entry_detail);

        catId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        entryId = getIntent().getLongExtra(EXTRA_ENTRY_ID, -1L);
        if (catId <= 0 || entryId <= 0) {
            finish();
            return;
        }

        layoutPhoto = findViewById(R.id.layoutDiaryDetailPhoto);
        imgPhoto = findViewById(R.id.imgDiaryDetailPhoto);
        tvPhotoHint = findViewById(R.id.tvDiaryDetailPhotoHint);
        tvCat = findViewById(R.id.tvDiaryDetailCat);
        tvType = findViewById(R.id.tvDiaryDetailType);
        tvDate = findViewById(R.id.tvDiaryDetailDate);
        tvTitle = findViewById(R.id.tvDiaryDetailTitle);
        tvText = findViewById(R.id.tvDiaryDetailText);
        tvTags = findViewById(R.id.tvDiaryDetailTags);

        findViewById(R.id.btnDiaryDetailBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDiaryDetailEdit).setOnClickListener(v -> {
            Intent intent = new Intent(this, DiaryEntryEditActivity.class);
            intent.putExtra(DiaryEntryEditActivity.EXTRA_CAT_ID, catId);
            intent.putExtra(DiaryEntryEditActivity.EXTRA_ENTRY_ID, entryId);
            startActivity(intent);
        });
        imgPhoto.setOnClickListener(v -> {
            showWholePhoto = !showWholePhoto;
            imgPhoto.setScaleType(showWholePhoto
                    ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(); // picks up edits made in DiaryEntryEditActivity
    }

    private void load() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(this);
            DiaryEntry entry = new DiaryDao(db).getEntryById(entryId);
            Cat cat = new CatDao(db).getCatById(catId);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (entry == null) {
                    finish(); // deleted from the editor
                    return;
                }
                bind(entry, cat);
            });
        });
    }

    private void bind(DiaryEntry entry, Cat cat) {
        tvCat.setText(cat != null ? getString(R.string.diary_detail_of_cat, cat.getName()) : "");

        boolean milestone = entry.getType() == DiaryEntry.Type.MILESTONE;
        tvType.setText(milestone ? R.string.diary_type_milestone : R.string.diary_type_memory);
        tvType.setBackground(pill(milestone ? R.color.card_oat : R.color.card_sage));
        // Milestones are about the day; memories also show the time (as on the timeline).
        tvDate.setText(milestone
                ? DateFormatUtils.formatDate(entry.getEntryAt())
                : getString(R.string.diary_detail_date_time,
                        DateFormatUtils.formatDate(entry.getEntryAt()),
                        DateFormatUtils.formatTime(entry.getEntryAt())));
        tvTitle.setText(entry.getTitle());

        String text = entry.getDescription();
        tvText.setVisibility(text == null || text.trim().isEmpty() ? View.GONE : View.VISIBLE);
        tvText.setText(text);

        String tags = tagLabels(entry.getTags());
        tvTags.setVisibility(tags.isEmpty() ? View.GONE : View.VISIBLE);
        tvTags.setText(tags);

        File photo = entry.hasPhoto()
                ? new FileStorageHelper(this).getInternalImageFile(entry.getPhotoPath()) : null;
        boolean hasPhoto = photo != null && photo.exists();
        layoutPhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        tvPhotoHint.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        if (hasPhoto) {
            imgPhoto.setContentDescription(getString(R.string.cd_diary_photo, entry.getTitle()));
            // Glide decodes the file at the frame's real size (sharp, not the 256px preview) and
            // keys the cache on the file's last change, so a photo replaced in Edit shows at once.
            Glide.with(this)
                    .load(photo)
                    .signature(new ObjectKey(photo.lastModified()))
                    .into(imgPhoto);
        }
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
        return String.join("   ", shown);
    }

    private GradientDrawable pill(int fillRes) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setColor(ContextCompat.getColor(this, fillRes));
        d.setCornerRadius(999 * density);
        d.setStroke(Math.round(1.5f * density), ContextCompat.getColor(this, R.color.ink));
        return d;
    }
}
