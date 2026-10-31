package com.litterboxd.app.ui.diary;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DiaryEntry;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;

/**
 * Adds or edits one diary entry (EXTRA_ENTRY_ID = -1 for a new one) for EXTRA_CAT_ID.
 * Nothing is written until Save; Cancel or Back leave the diary unchanged. A picked photo is
 * copied (downscaled) into private storage on Save, so the entry never depends on the picker's
 * temporary Uri. Form state survives rotation.
 */
public class DiaryEntryEditActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    public static final String EXTRA_ENTRY_ID = "diary_entry_id";

    private static final String STATE_TYPE = "type";
    private static final String STATE_ENTRY_AT = "entry_at";
    private static final String STATE_PICKED_URI = "picked_uri";
    private static final String STATE_PHOTO_REMOVED = "photo_removed";
    private static final String STATE_TAGS = "tags";
    private static final String STATE_LOADED = "loaded";

    private TextView tvTitle, tvCat;
    private MaterialButton btnTypeMemory, btnTypeMilestone, btnDate, btnPickPhoto, btnRemovePhoto, btnSave;
    private ShapeableImageView imgPhoto;
    private ProgressBar progressPhoto;
    private TextInputLayout tilTitle;
    private TextInputEditText etTitle, etCaption;
    private GridLayout gridTags;

    private DiaryDao diaryDao;
    private CatDao catDao;
    private FileStorageHelper fileStorageHelper;

    private long catId;
    private long entryId;
    private String catName;
    /** The saved entry being edited (null for a new one). */
    private DiaryEntry existing;

    private DiaryEntry.Type type = DiaryEntry.Type.MEMORY;
    private long entryAt;
    private Uri pickedPhotoUri;
    private boolean photoRemoved;
    private final ArrayList<String> selectedTags = new ArrayList<>();
    /** Form already filled from the database (so a rotation doesn't overwrite the user's edits). */
    private boolean loaded;

    private final ActivityResultLauncher<String> photoPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return; // picker cancelled: keep whatever was there
                pickedPhotoUri = uri;
                photoRemoved = false;
                renderPhoto();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary_entry_edit);

        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        diaryDao = new DiaryDao(dbHelper);
        catDao = new CatDao(dbHelper);
        fileStorageHelper = new FileStorageHelper(this);

        catId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        entryId = getIntent().getLongExtra(EXTRA_ENTRY_ID, -1L);
        if (catId == -1L) {
            finish();
            return;
        }

        tvTitle = findViewById(R.id.tvDiaryEditTitle);
        tvCat = findViewById(R.id.tvDiaryEditCat);
        btnTypeMemory = findViewById(R.id.btnTypeMemory);
        btnTypeMilestone = findViewById(R.id.btnTypeMilestone);
        btnDate = findViewById(R.id.btnDiaryDate);
        btnPickPhoto = findViewById(R.id.btnPickDiaryPhoto);
        btnRemovePhoto = findViewById(R.id.btnRemoveDiaryPhoto);
        btnSave = findViewById(R.id.btnSaveDiaryEntry);
        imgPhoto = findViewById(R.id.imgDiaryEntryPhoto);
        progressPhoto = findViewById(R.id.progressDiaryPhoto);
        tilTitle = findViewById(R.id.tilDiaryTitle);
        etTitle = findViewById(R.id.etDiaryTitle);
        etCaption = findViewById(R.id.etDiaryCaption);
        gridTags = findViewById(R.id.gridDiaryTags);

        if (savedInstanceState != null) {
            type = DiaryEntry.Type.fromStorage(savedInstanceState.getString(STATE_TYPE));
            entryAt = savedInstanceState.getLong(STATE_ENTRY_AT);
            pickedPhotoUri = savedInstanceState.getParcelable(STATE_PICKED_URI);
            photoRemoved = savedInstanceState.getBoolean(STATE_PHOTO_REMOVED);
            ArrayList<String> tags = savedInstanceState.getStringArrayList(STATE_TAGS);
            if (tags != null) selectedTags.addAll(tags);
            loaded = savedInstanceState.getBoolean(STATE_LOADED);
        } else {
            entryAt = System.currentTimeMillis(); // a new entry defaults to now
        }

        buildTagChips();
        btnTypeMemory.setOnClickListener(v -> setType(DiaryEntry.Type.MEMORY));
        btnTypeMilestone.setOnClickListener(v -> setType(DiaryEntry.Type.MILESTONE));
        btnDate.setOnClickListener(v -> pickDate());
        btnPickPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        btnRemovePhoto.setOnClickListener(v -> {
            pickedPhotoUri = null;
            photoRemoved = true;
            renderPhoto();
        });
        findViewById(R.id.btnCancelDiaryEntry).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());
        findViewById(R.id.btnDeleteDiaryEntry).setOnClickListener(v -> confirmDelete());

        if (entryId != -1L) {
            tvTitle.setText(R.string.diary_edit_title);
            findViewById(R.id.btnDeleteDiaryEntry).setVisibility(View.VISIBLE);
            btnSave.setEnabled(false); // until the entry is loaded
        }
        renderType();
        renderDate();
        loadCatAndEntry();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TYPE, type.name());
        outState.putLong(STATE_ENTRY_AT, entryAt);
        outState.putParcelable(STATE_PICKED_URI, pickedPhotoUri);
        outState.putBoolean(STATE_PHOTO_REMOVED, photoRemoved);
        outState.putStringArrayList(STATE_TAGS, selectedTags);
        outState.putBoolean(STATE_LOADED, loaded);
    }

    private void loadCatAndEntry() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            Cat cat = catDao.getCatById(catId);
            DiaryEntry entry = entryId != -1L ? diaryDao.getEntryById(entryId) : null;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                // The cat or entry was deleted meanwhile (or belongs to another cat): nothing to edit.
                if (cat == null || (entryId != -1L && (entry == null || entry.getCatId() != catId))) {
                    finish();
                    return;
                }
                catName = cat.getName();
                tvCat.setText(getString(R.string.diary_for_cat, catName));
                existing = entry;
                if (entry != null && !loaded) {
                    type = entry.getType();
                    entryAt = entry.getEntryAt();
                    etTitle.setText(entry.getTitle());
                    etCaption.setText(entry.getDescription());
                    selectedTags.clear();
                    selectedTags.addAll(entry.getTags());
                }
                loaded = true;
                btnSave.setEnabled(true);
                renderType();
                renderDate();
                renderTags();
                renderPhoto();
            });
        });
    }

    // ---- Form pieces ----

    private void setType(DiaryEntry.Type newType) {
        type = newType;
        renderType();
    }

    private void renderType() {
        btnTypeMemory.setChecked(type == DiaryEntry.Type.MEMORY);
        btnTypeMilestone.setChecked(type == DiaryEntry.Type.MILESTONE);
    }

    private void pickDate() {
        Calendar current = Calendar.getInstance();
        current.setTimeInMillis(entryAt);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            // Keep the time of day; only the date changes.
            Calendar picked = Calendar.getInstance();
            picked.setTimeInMillis(entryAt);
            picked.set(y, m, d);
            entryAt = Math.min(picked.getTimeInMillis(), System.currentTimeMillis());
            renderDate();
        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH));
        // Any past date is fine (e.g. "First day home" years ago); the future isn't a memory yet.
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void renderDate() {
        btnDate.setText(getString(R.string.diary_date, DateFormatUtils.formatDate(entryAt)));
    }

    private void buildTagChips() {
        String[] keys = getResources().getStringArray(R.array.diary_tag_keys);
        String[] labels = getResources().getStringArray(R.array.diary_tag_labels);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < keys.length && i < labels.length; i++) {
            final String key = keys[i];
            MaterialButton chip = (MaterialButton) inflater.inflate(R.layout.item_check_in_chip, gridTags, false);
            chip.setText(labels[i]);
            chip.setTag(key);
            chip.setOnClickListener(v -> {
                if (selectedTags.contains(key)) selectedTags.remove(key); else selectedTags.add(key);
                renderTags();
            });
            gridTags.addView(chip);
        }
        renderTags();
    }

    private void renderTags() {
        for (int i = 0; i < gridTags.getChildCount(); i++) {
            MaterialButton chip = (MaterialButton) gridTags.getChildAt(i);
            chip.setChecked(selectedTags.contains((String) chip.getTag()));
        }
    }

    /** Shows the picked photo, else the saved one (unless removed), else hides the preview. */
    private void renderPhoto() {
        String savedPhoto = existing != null && !photoRemoved ? existing.getPhotoPath() : null;
        boolean hasPhoto = pickedPhotoUri != null || (savedPhoto != null && !savedPhoto.isEmpty());
        imgPhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        btnRemovePhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        btnPickPhoto.setText(hasPhoto ? R.string.btn_change_photo : R.string.btn_add_photo);
        if (pickedPhotoUri == null) {
            if (savedPhoto != null) {
                fileStorageHelper.showImageFile(imgPhoto, savedPhoto, R.drawable.ic_cat_face);
            }
            return;
        }
        // Decode the picked image downscaled, off the UI thread (it may be a large camera photo).
        final Uri uri = pickedPhotoUri;
        progressPhoto.setVisibility(View.VISIBLE);
        AppExecutors.getInstance().diskIO().execute(() -> {
            Bitmap preview = fileStorageHelper.loadScaledBitmap(uri, FileStorageHelper.PREVIEW_MIN_SIDE_PX);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed() || uri != pickedPhotoUri) return;
                progressPhoto.setVisibility(View.GONE);
                if (preview != null) {
                    imgPhoto.setImageBitmap(preview);
                } else {
                    // Not a readable image: drop it and keep the previous state.
                    pickedPhotoUri = null;
                    Toast.makeText(this, R.string.error_photo_save, Toast.LENGTH_SHORT).show();
                    renderPhoto();
                }
            });
        });
    }

    // ---- Save / delete ----

    private void save() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            tilTitle.setError(getString(R.string.error_diary_title_required));
            etTitle.requestFocus();
            return;
        }
        tilTitle.setError(null);
        String caption = etCaption.getText() != null ? etCaption.getText().toString().trim() : "";

        // Everything the worker needs is read here, on the UI thread.
        final DiaryEntry entry = new DiaryEntry();
        if (existing != null) {
            entry.setId(existing.getId());
            entry.setCreatedAt(existing.getCreatedAt());
        }
        entry.setCatId(catId);
        entry.setType(type);
        entry.setTitle(title);
        entry.setDescription(caption.isEmpty() ? null : caption);
        entry.setEntryAt(entryAt);
        entry.setTags(new ArrayList<>(selectedTags));
        final String oldPhoto = existing != null ? existing.getPhotoPath() : null;
        final Uri photo = pickedPhotoUri;
        final boolean removed = photoRemoved;
        final String name = catName;

        btnSave.setEnabled(false);
        AppExecutors.getInstance().diskIO().execute(() -> {
            String newPhoto = removed ? null : oldPhoto;
            if (photo != null) {
                String fileName = FileStorageHelper.DIARY_PHOTO_PREFIX + UUID.randomUUID() + ".jpg";
                if (!fileStorageHelper.saveDownscaledImage(photo, fileName)) {
                    showSaveError(R.string.error_photo_save);
                    return;
                }
                newPhoto = fileName;
            }
            entry.setPhotoPath(newPhoto);
            boolean ok = existing == null ? diaryDao.insertEntry(entry) > 0 : diaryDao.updateEntry(entry) > 0;
            if (!ok) {
                if (photo != null) fileStorageHelper.deleteImage(newPhoto); // don't orphan the new copy
                showSaveError(R.string.error_diary_save);
                return;
            }
            deletePhotoIfUnused(oldPhoto, newPhoto);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, getString(R.string.msg_diary_saved, name), Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void showSaveError(int messageRes) {
        AppExecutors.getInstance().mainThread().execute(() -> {
            if (isFinishing() || isDestroyed()) return;
            btnSave.setEnabled(true);
            Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
        });
    }

    private void confirmDelete() {
        if (existing == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.msg_delete_diary_title)
                .setMessage(getString(R.string.msg_delete_diary_message, existing.getTitle()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> delete())
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void delete() {
        final long id = existing.getId();
        final String photo = existing.getPhotoPath();
        AppExecutors.getInstance().diskIO().execute(() -> {
            diaryDao.deleteEntry(id);
            deletePhotoIfUnused(photo, null);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, R.string.msg_diary_deleted, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    /**
     * Deletes a replaced/removed diary photo, but only a diary photo (never a cat's profile photo or
     * the owner's avatar) and only when no other entry still points at it. Worker thread only.
     */
    private void deletePhotoIfUnused(String oldPhoto, String newPhoto) {
        if (oldPhoto == null || oldPhoto.equals(newPhoto)) return;
        if (!oldPhoto.startsWith(FileStorageHelper.DIARY_PHOTO_PREFIX)) return;
        if (diaryDao.countPhotoReferences(oldPhoto) == 0) {
            fileStorageHelper.deleteImage(oldPhoto);
        }
    }
}
