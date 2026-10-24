package com.litterboxd.app.ui.onboarding;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.util.DateFormatUtils;
import com.litterboxd.app.util.Validators;

import java.util.Calendar;
import java.util.UUID;

/**
 * Add mode (default): onboarding step 2, inserts a cat and continues to the feeding schedule.
 * Add-another mode (EXTRA_ADD_ANOTHER, from Home): inserts and selects the cat, then opens its
 * feeding schedule in edit mode, which returns to Home. Never touches onboarding_complete.
 * Edit mode (EXTRA_CAT_ID set): pre-fills the cat, updates it and returns to the caller.
 */
public class AddEditCatActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    public static final String EXTRA_ADD_ANOTHER = "add_another";
    private static final String STATE_PHOTO_URI = "photo_uri";
    private static final String STATE_BIRTHDAY = "birthday";

    private TextInputEditText etCatName, etWeight;
    private TextInputLayout tilCatName, tilWeight;
    private ImageView imgCatPhoto;
    private RadioGroup rgAgeType, rgSex, rgNeutered, rgCoatColor, rgLifestyle;
    private LinearLayout layoutBirthday, layoutApproxAge;
    private MaterialButton btnSelectBirthday, btnSaveCat;
    private Spinner spinnerYears, spinnerMonths, spinnerBreed;
    private ProgressBar progressBar;

    private Long selectedBirthdayMs = null;
    private Uri pickedImageUri = null;
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
                    showPreview(uri);
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
        MaterialButton btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
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

        // Fields that are not views lose their value on rotation; restore them from the Bundle.
        if (savedInstanceState != null) {
            pickedImageUri = savedInstanceState.getParcelable(STATE_PHOTO_URI);
            if (pickedImageUri != null) showPreview(pickedImageUri);
            long birthday = savedInstanceState.getLong(STATE_BIRTHDAY, -1L);
            if (birthday != -1L) setBirthday(birthday);
        }

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
        outState.putLong(STATE_BIRTHDAY, selectedBirthdayMs != null ? selectedBirthdayMs : -1L);
    }

    private void setBirthday(long birthdayMs) {
        selectedBirthdayMs = birthdayMs;
        btnSelectBirthday.setText(DateFormatUtils.formatDate(birthdayMs));
    }

    private boolean isEditMode() {
        return editCatId != -1L;
    }

    /** Loads the cat being edited; fills the form only on first creation (views restore themselves after rotation). */
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
                if (pickedImageUri == null) showSavedPhoto(cat);
            });
        });
    }

    private void showSavedPhoto(Cat cat) {
        if (cat.getPhotoPath() == null) return;
        showPreview(Uri.fromFile(fileStorageHelper.getInternalImageFile(cat.getPhotoPath())));
    }

    /** Decodes a small preview off the UI thread (a full-size camera photo could run out of memory). */
    private void showPreview(Uri uri) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            Bitmap preview = fileStorageHelper.loadScaledBitmap(uri, FileStorageHelper.PREVIEW_MIN_SIDE_PX);
            if (preview != null) {
                AppExecutors.getInstance().mainThread().execute(() -> imgCatPhoto.setImageBitmap(preview));
            }
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

        // Breeds come from @array/cat_breeds (android:entries); unknown values fall back to the last item, "Other".
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
        if (isEditMode() && editingCat == null) return; // still loading

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

        // Read every view here on the UI thread; the worker thread only touches files and the database.
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

        AppExecutors.getInstance().diskIO().execute(() -> {
            String photoFileName = oldPhoto;
            if (photoUri != null) {
                String newFileName = FileStorageHelper.CAT_PHOTO_PREFIX + UUID.randomUUID().toString() + ".jpg";
                // Only point the cat at the new file if it was actually written.
                if (fileStorageHelper.saveDownscaledImage(photoUri, newFileName)) {
                    photoFileName = newFileName;
                }
            }
            cat.setPhotoPath(photoFileName);

            if (isEditMode()) {
                catDao.updateCat(cat);
                // Replaced photo: remove the old file so it does not linger in internal storage.
                if (oldPhoto != null && !oldPhoto.equals(photoFileName)) {
                    fileStorageHelper.deleteImage(oldPhoto);
                }
                AppExecutors.getInstance().mainThread().execute(this::finish);
                return;
            }

            cat.setCreatedAt(System.currentTimeMillis());
            long catId = catDao.insertCat(cat);
            if (addAnother || appPrefs.getSelectedCatId() == -1L) {
                appPrefs.setSelectedCatId(catId);
            }

            AppExecutors.getInstance().mainThread().execute(() -> {
                progressBar.setVisibility(View.GONE);
                btnSaveCat.setEnabled(true);
                Intent intent = new Intent(AddEditCatActivity.this, FeedingScheduleActivity.class);
                intent.putExtra(FeedingScheduleActivity.EXTRA_CAT_ID, catId);
                // From Home: edit mode, so Save/Back return to Home without onboarding logic.
                intent.putExtra(FeedingScheduleActivity.EXTRA_EDIT_MODE, addAnother);
                startActivity(intent);
                finish();
            });
        });
    }

    // Coat colour and lifestyle are stored as plain English values in the database.
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
