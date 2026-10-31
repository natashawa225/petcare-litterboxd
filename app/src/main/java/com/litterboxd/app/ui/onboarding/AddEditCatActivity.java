package com.litterboxd.app.ui.onboarding;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.data.CareTaskSeeder;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.profile.CatAvatars;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;
import com.litterboxd.app.util.Validators;

import java.io.File;
import java.util.Calendar;
import java.util.UUID;

public class AddEditCatActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    public static final String EXTRA_ADD_ANOTHER = "add_another";
    private static final String STATE_PHOTO_URI = "photo_uri";
    private static final String STATE_BIRTHDAY = "birthday";
    private static final String STATE_AVATAR_VALUE = "cat_avatar_value";

    private TextInputEditText etCatName, etWeight;
    private TextInputLayout tilCatName, tilWeight;
    private ShapeableImageView imgCatPhoto;
    private ProgressBar progressAvatar;
    private GridLayout gridAvatars;
    private RadioGroup rgAgeType, rgSex, rgNeutered, rgCoatColor, rgLifestyle;
    private LinearLayout layoutBirthday, layoutApproxAge;
    private MaterialButton btnSelectBirthday, btnSaveCat;
    private Spinner spinnerYears, spinnerMonths, spinnerBreed;
    private ProgressBar progressBar;

    private Long selectedBirthdayMs = null;
    private Uri pickedImageUri = null;
    private String pendingAvatarValue = null;
    private FileStorageHelper fileStorageHelper;
    private AppPrefs appPrefs;
    private CatDao catDao;

    private long editCatId = -1L;
    private boolean addAnother;
    private Cat editingCat;

    private final ActivityResultLauncher<String> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    pickedImageUri = uri;
                    pendingAvatarValue = null;
                    renderSelection();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_cat);

        fileStorageHelper = new FileStorageHelper(this);
        appPrefs = new AppPrefs(this);
        catDao = new CatDao(LitterboxdDbHelper.getInstance(this));
        editCatId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        addAnother = getIntent().getBooleanExtra(EXTRA_ADD_ANOTHER, false);

        etCatName = findViewById(R.id.etCatName);
        etWeight = findViewById(R.id.etWeight);
        tilCatName = findViewById(R.id.tilCatName);
        tilWeight = findViewById(R.id.tilWeight);
        imgCatPhoto = findViewById(R.id.imgCatPhoto);
        progressAvatar = findViewById(R.id.progressAvatar);
        gridAvatars = findViewById(R.id.gridAvatars);
        MaterialButton btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        MaterialButton btnUseDefaultAvatar = findViewById(R.id.btnUseDefaultAvatar);
        rgAgeType = findViewById(R.id.rgAgeType);
        layoutBirthday = findViewById(R.id.layoutBirthday);
        layoutApproxAge = findViewById(R.id.layoutApproxAge);
        btnSelectBirthday = findViewById(R.id.btnSelectBirthday);
        spinnerYears = findViewById(R.id.spinnerYears);
        spinnerMonths = findViewById(R.id.spinnerMonths);
        spinnerBreed = findViewById(R.id.spinnerBreed);
        rgSex = findViewById(R.id.rgSex);
        rgNeutered = findViewById(R.id.rgNeutered);
        rgCoatColor = findViewById(R.id.rgCoatColor);
        rgLifestyle = findViewById(R.id.rgLifestyle);
        progressBar = findViewById(R.id.progressBar);
        btnSaveCat = findViewById(R.id.btnSaveCat);

        setupSpinners();
        buildAvatarGrid();

        rgAgeType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbBirthday) {
                layoutBirthday.setVisibility(View.VISIBLE);
                layoutApproxAge.setVisibility(View.GONE);
            } else {
                layoutBirthday.setVisibility(View.GONE);
                layoutApproxAge.setVisibility(View.VISIBLE);
            }
        });

        btnSelectPhoto.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));
        btnUseDefaultAvatar.setOnClickListener(v -> {
            pickedImageUri = null;
            pendingAvatarValue = null;
            renderSelection();
        });

        btnSelectBirthday.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedBirthdayMs != null) cal.setTimeInMillis(selectedBirthdayMs);
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar c = Calendar.getInstance();
                c.set(year, month, dayOfMonth);
                setBirthday(c.getTimeInMillis());
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            dialog.show();
        });

        btnSaveCat.setOnClickListener(v -> saveCatData());

        if (savedInstanceState != null) {
            pickedImageUri = savedInstanceState.getParcelable(STATE_PHOTO_URI);
            pendingAvatarValue = savedInstanceState.getString(STATE_AVATAR_VALUE);
            long birthday = savedInstanceState.getLong(STATE_BIRTHDAY, -1L);
            if (birthday != -1L) setBirthday(birthday);
        }

        renderSelection();

        if (addAnother) {
            findViewById(R.id.tvStepIndicator).setVisibility(View.GONE);
        }

        if (isEditMode()) {
            ((TextView) findViewById(R.id.tvTitle)).setText(R.string.title_edit_cat);
            findViewById(R.id.tvStepIndicator).setVisibility(View.GONE);
            btnSaveCat.setText(R.string.btn_save);
            loadCatForEdit(savedInstanceState == null);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_PHOTO_URI, pickedImageUri);
        outState.putString(STATE_AVATAR_VALUE, pendingAvatarValue);
        outState.putLong(STATE_BIRTHDAY, selectedBirthdayMs != null ? selectedBirthdayMs : -1L);
    }

    private void buildAvatarGrid() {
        int size = getResources().getDimensionPixelSize(R.dimen.avatar_choice_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.spacing_tiny);
        ShapeAppearanceModel circle = ShapeAppearanceModel.builder().setAllCornerSizes(size / 2f).build();
        int columns = getResources().getInteger(R.integer.avatar_grid_columns);
        for (int i = 0; i < CatAvatars.BUNDLED.length; i++) {
            final int index = i;
            ShapeableImageView option = new ShapeableImageView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / columns), GridLayout.spec(i % columns, 1f));
            params.width = size;
            params.height = size;
            params.setGravity(Gravity.CENTER);
            params.setMargins(margin, margin, margin, margin);
            option.setLayoutParams(params);
            option.setScaleType(ShapeableImageView.ScaleType.CENTER_CROP);
            option.setShapeAppearanceModel(circle);
            option.setImageResource(CatAvatars.BUNDLED[i]);
            option.setStrokeColorResource(R.color.ink);
            option.setOnClickListener(v -> {
                pickedImageUri = null;
                pendingAvatarValue = CatAvatars.bundledValue(this, CatAvatars.BUNDLED[index]);
                renderSelection();
            });
            gridAvatars.addView(option);
        }
    }

    private void renderSelection() {
        int selected = pickedImageUri == null ? CatAvatars.bundledIndex(this, pendingAvatarValue) : -1;
        int thin = getResources().getDimensionPixelSize(R.dimen.avatar_stroke);
        int thick = getResources().getDimensionPixelSize(R.dimen.avatar_stroke_selected);
        for (int i = 0; i < gridAvatars.getChildCount(); i++) {
            ShapeableImageView option = (ShapeableImageView) gridAvatars.getChildAt(i);
            boolean isSelected = i == selected;
            option.setStrokeWidth(isSelected ? thick : thin);
            option.setStrokeColor(ContextCompat.getColorStateList(this,
                    isSelected ? R.color.sage_primary : R.color.ink));
            option.setPadding(isSelected ? thick : thin, isSelected ? thick : thin,
                    isSelected ? thick : thin, isSelected ? thick : thin);
        }

        if (pickedImageUri != null) {
            final Uri uri = pickedImageUri;
            progressAvatar.setVisibility(View.VISIBLE);
            AppExecutors.getInstance().diskIO().execute(() -> {
                Bitmap preview = fileStorageHelper.loadScaledBitmap(uri, FileStorageHelper.PREVIEW_MIN_SIDE_PX);
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (isFinishing() || isDestroyed() || uri != pickedImageUri) return;
                    progressAvatar.setVisibility(View.GONE);
                    if (preview != null) {
                        imgCatPhoto.setImageBitmap(preview);
                    } else {
                        pickedImageUri = null;
                        Toast.makeText(this, R.string.error_photo_save, Toast.LENGTH_SHORT).show();
                        renderSelection();
                    }
                });
            });
            return;
        }

        int bundledIdx = CatAvatars.bundledIndex(this, pendingAvatarValue);
        if (bundledIdx >= 0) {
            imgCatPhoto.setImageResource(CatAvatars.BUNDLED[bundledIdx]);
        } else if (pendingAvatarValue != null && !pendingAvatarValue.isEmpty()) {
            fileStorageHelper.showCatPhoto(imgCatPhoto, pendingAvatarValue);
        } else {
            imgCatPhoto.setImageResource(R.drawable.ic_cat_face);
        }
    }

    private void setBirthday(long birthdayMs) {
        selectedBirthdayMs = birthdayMs;
        btnSelectBirthday.setText(DateFormatUtils.formatDate(birthdayMs));
    }

    private boolean isEditMode() {
        return editCatId != -1L;
    }

    private void loadCatForEdit(boolean fillForm) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            Cat cat = catDao.getCatById(editCatId);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (cat == null) {
                    finish();
                    return;
                }
                editingCat = cat;
                if (fillForm) fillForm(cat);
                if (pickedImageUri == null && pendingAvatarValue == null) {
                    pendingAvatarValue = cat.getPhotoPath();
                    renderSelection();
                }
            });
        });
    }

    private void fillForm(Cat cat) {
        etCatName.setText(cat.getName());
        etWeight.setText(String.valueOf(cat.getWeight()));

        if (cat.getBirthday() != null) {
            rgAgeType.check(R.id.rbBirthday);
            setBirthday(cat.getBirthday());
        } else {
            rgAgeType.check(R.id.rbApproxAge);
            spinnerYears.setSelection(Math.min(cat.getApproxAgeYears(), spinnerYears.getCount() - 1));
            spinnerMonths.setSelection(Math.min(cat.getApproxAgeMonths(), spinnerMonths.getCount() - 1));
        }

        String[] breeds = getResources().getStringArray(R.array.cat_breeds);
        int breedPos = breeds.length - 1;
        for (int i = 0; i < breeds.length; i++) {
            if (breeds[i].equals(cat.getBreed())) breedPos = i;
        }
        spinnerBreed.setSelection(breedPos);

        rgSex.check("Male".equals(cat.getSex()) ? R.id.rbMale : R.id.rbFemale);
        rgNeutered.check(cat.isNeutered() ? R.id.rbNeuteredYes : R.id.rbNeuteredNo);
        rgCoatColor.check(coatToId(cat.getCoatColor()));
        rgLifestyle.check(lifestyleToId(cat.getLifestyle()));
    }

    private void setupSpinners() {
        Integer[] years = new Integer[26];
        for (int i = 0; i <= 25; i++) years[i] = i;
        ArrayAdapter<Integer> yearsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, years);
        yearsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYears.setAdapter(yearsAdapter);

        Integer[] months = new Integer[12];
        for (int i = 0; i < 12; i++) months[i] = i;
        ArrayAdapter<Integer> monthsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, months);
        monthsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonths.setAdapter(monthsAdapter);
    }

    private void saveCatData() {
        if (isEditMode() && editingCat == null) return;

        String name = etCatName.getText() != null ? etCatName.getText().toString().trim() : "";
        if (!Validators.isValidCatName(name)) {
            tilCatName.setError(getString(R.string.error_select_cat_name));
            return;
        }
        tilCatName.setError(null);

        String weightStr = etWeight.getText() != null ? etWeight.getText().toString().trim() : "";
        if (rgAgeType.getCheckedRadioButtonId() == R.id.rbBirthday && selectedBirthdayMs == null) {
            Toast.makeText(this, R.string.error_select_birthday, Toast.LENGTH_SHORT).show();
            return;
        }

        Double weight = Validators.parseDecimal(weightStr);
        if (weight == null || !Validators.isValidWeight(weight)) {
            tilWeight.setError(getString(R.string.error_invalid_weight));
            return;
        }
        tilWeight.setError(null);

        progressBar.setVisibility(View.VISIBLE);
        btnSaveCat.setEnabled(false);

        Cat cat = isEditMode() ? editingCat : new Cat();
        cat.setName(name);
        if (rgAgeType.getCheckedRadioButtonId() == R.id.rbBirthday) {
            cat.setBirthday(selectedBirthdayMs);
        } else {
            cat.setBirthday(null);
            cat.setApproxAgeYears((Integer) spinnerYears.getSelectedItem());
            cat.setApproxAgeMonths((Integer) spinnerMonths.getSelectedItem());
        }
        cat.setBreed(spinnerBreed.getSelectedItem().toString());
        cat.setSex(rgSex.getCheckedRadioButtonId() == R.id.rbMale ? "Male" : "Female");
        cat.setNeutered(rgNeutered.getCheckedRadioButtonId() == R.id.rbNeuteredYes);
        cat.setWeight(weight);
        cat.setCoatColor(idToCoat(rgCoatColor.getCheckedRadioButtonId()));
        cat.setLifestyle(idToLifestyle(rgLifestyle.getCheckedRadioButtonId()));

        final String oldPhoto = cat.getPhotoPath();
        final Uri photoUri = pickedImageUri;
        final String chosenValue = pendingAvatarValue;

        AppExecutors.getInstance().diskIO().execute(() -> {
            String photoFileName = chosenValue;
            if (photoUri != null) {
                String newFileName = FileStorageHelper.CAT_PHOTO_PREFIX + UUID.randomUUID().toString() + ".jpg";
                if (fileStorageHelper.saveDownscaledImage(photoUri, newFileName)) {
                    photoFileName = newFileName;
                }
            }
            cat.setPhotoPath(photoFileName);

            if (isEditMode()) {
                catDao.updateCat(cat);
                if (oldPhoto != null && !oldPhoto.equals(photoFileName)
                        && oldPhoto.startsWith(FileStorageHelper.CAT_PHOTO_PREFIX)) {
                    fileStorageHelper.deleteImage(oldPhoto);
                }
                AppExecutors.getInstance().mainThread().execute(this::finish);
                return;
            }

            cat.setCreatedAt(System.currentTimeMillis());
            long catId = catDao.insertCat(cat);
            CareTaskSeeder.seedIfEmpty(AddEditCatActivity.this,
                    new CareTaskDao(LitterboxdDbHelper.getInstance(AddEditCatActivity.this)), catId);
            if (addAnother || appPrefs.getSelectedCatId() == -1L) {
                appPrefs.setSelectedCatId(catId);
            }

            AppExecutors.getInstance().mainThread().execute(() -> {
                progressBar.setVisibility(View.GONE);
                btnSaveCat.setEnabled(true);
                Intent intent = new Intent(AddEditCatActivity.this, FeedingScheduleActivity.class);
                intent.putExtra(FeedingScheduleActivity.EXTRA_CAT_ID, catId);
                intent.putExtra(FeedingScheduleActivity.EXTRA_EDIT_MODE, addAnother);
                startActivity(intent);
                finish();
            });
        });
    }

    private static String idToCoat(int id) {
        if (id == R.id.rbColorWhite) return "White";
        if (id == R.id.rbColorOrange) return "Ginger";
        if (id == R.id.rbColorGrey) return "Grey";
        if (id == R.id.rbColorCalico) return "Calico";
        if (id == R.id.rbColorTabby) return "Tabby";
        return "Black";
    }

    private static int coatToId(String coat) {
        if ("White".equals(coat)) return R.id.rbColorWhite;
        if ("Ginger".equals(coat)) return R.id.rbColorOrange;
        if ("Grey".equals(coat)) return R.id.rbColorGrey;
        if ("Calico".equals(coat)) return R.id.rbColorCalico;
        if ("Tabby".equals(coat)) return R.id.rbColorTabby;
        return R.id.rbColorBlack;
    }

    private static String idToLifestyle(int id) {
        if (id == R.id.rbLifestyleOutdoor) return "Outdoor";
        if (id == R.id.rbLifestyleBoth) return "Indoor / Outdoor";
        return "Indoor only";
    }

    private static int lifestyleToId(String lifestyle) {
        if ("Outdoor".equals(lifestyle)) return R.id.rbLifestyleOutdoor;
        if ("Indoor / Outdoor".equals(lifestyle)) return R.id.rbLifestyleBoth;
        return R.id.rbLifestyleIndoor;
    }
}
