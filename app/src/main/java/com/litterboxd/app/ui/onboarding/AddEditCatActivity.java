package com.litterboxd.app.ui.onboarding;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import androidx.activity.result.ActivityResultLauncher;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.UUID;

/**
 * Add mode (default): onboarding step 2, inserts a cat and continues to the feeding schedule.
 * Edit mode (EXTRA_CAT_ID set): pre-fills the cat, updates it and returns to the caller.
 */
public class AddEditCatActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";

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
    private String savedPhotoFileName = null;
    private FileStorageHelper fileStorageHelper;
    private AppPrefs appPrefs;
    private CatDao catDao;

    private long editCatId = -1L;
    private Cat editingCat;

    private final ActivityResultLauncher<String> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    pickedImageUri = uri;
                    imgCatPhoto.setImageURI(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_cat);

        fileStorageHelper = new FileStorageHelper(this);
        appPrefs = new AppPrefs(this);
        catDao = new CatDao(new LitterboxdDbHelper(this));
        editCatId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);

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

        Calendar calendar = Calendar.getInstance();
        selectedBirthdayMs = calendar.getTimeInMillis();
        btnSelectBirthday.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedBirthdayMs != null) cal.setTimeInMillis(selectedBirthdayMs);
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar c = Calendar.getInstance();
                c.set(year, month, dayOfMonth);
                selectedBirthdayMs = c.getTimeInMillis();
                btnSelectBirthday.setText(year + "-" + (month + 1) + "-" + dayOfMonth);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnSaveCat.setOnClickListener(v -> saveCatData());

        if (isEditMode()) {
            ((TextView) findViewById(R.id.tvTitle)).setText(R.string.title_edit_cat);
            findViewById(R.id.tvStepIndicator).setVisibility(View.GONE);
            btnSaveCat.setText(R.string.btn_save);
            loadCatForEdit(savedInstanceState == null);
        }
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
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void fillForm(Cat cat) {
        etCatName.setText(cat.getName());
        etWeight.setText(String.valueOf(cat.getWeight()));

        if (cat.getBirthday() != null) {
            rgAgeType.check(R.id.rbBirthday);
            selectedBirthdayMs = cat.getBirthday();
            btnSelectBirthday.setText(DateFormatUtils.formatDate(cat.getBirthday()));
        } else {
            rgAgeType.check(R.id.rbApproxAge);
            spinnerYears.setSelection(Math.min(cat.getApproxAgeYears(), spinnerYears.getCount() - 1));
            spinnerMonths.setSelection(Math.min(cat.getApproxAgeMonths(), spinnerMonths.getCount() - 1));
        }

        ArrayAdapter<String> breedAdapter = (ArrayAdapter<String>) spinnerBreed.getAdapter();
        int breedPos = breedAdapter.getPosition(cat.getBreed());
        spinnerBreed.setSelection(breedPos >= 0 ? breedPos : breedAdapter.getCount() - 1);

        rgSex.check("Male".equals(cat.getSex()) ? R.id.rbMale : R.id.rbFemale);
        rgNeutered.check(cat.isNeutered() ? R.id.rbNeuteredYes : R.id.rbNeuteredNo);
        rgCoatColor.check(coatToId(cat.getCoatColor()));
        rgLifestyle.check(lifestyleToId(cat.getLifestyle()));

        if (cat.getPhotoPath() != null) {
            File file = fileStorageHelper.getInternalImageFile(cat.getPhotoPath());
            if (file.exists()) imgCatPhoto.setImageURI(Uri.fromFile(file));
        }
    }

    private void setupSpinners() {
        String[] breeds = {"Domestic Shorthair", "Maine Coon", "Siamese", "Persian", "Ragdoll", "British Shorthair", "Sphynx", "Bengal", "Other"};
        ArrayAdapter<String> breedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, breeds);
        breedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBreed.setAdapter(breedAdapter);

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
            savedPhotoFileName = oldPhoto;
            if (photoUri != null) {
                savedPhotoFileName = "cat_photo_" + UUID.randomUUID().toString() + ".jpg";
                downscaleAndSaveImage(photoUri, savedPhotoFileName);
            }
            cat.setPhotoPath(savedPhotoFileName);

            if (isEditMode()) {
                catDao.updateCat(cat);
                // Replaced photo: remove the old file so it does not linger in internal storage.
                if (oldPhoto != null && !oldPhoto.equals(savedPhotoFileName)) {
                    fileStorageHelper.getInternalImageFile(oldPhoto).delete();
                }
                AppExecutors.getInstance().mainThread().execute(this::finish);
                return;
            }

            cat.setCreatedAt(System.currentTimeMillis());
            long catId = catDao.insertCat(cat);
            if (appPrefs.getSelectedCatId() == -1L) {
                appPrefs.setSelectedCatId(catId);
            }

            AppExecutors.getInstance().mainThread().execute(() -> {
                progressBar.setVisibility(View.GONE);
                btnSaveCat.setEnabled(true);
                Intent intent = new Intent(AddEditCatActivity.this, FeedingScheduleActivity.class);
                intent.putExtra("cat_id", catId);
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

    private void downscaleAndSaveImage(Uri sourceUri, String fileName) {
        try (InputStream inputStream = getContentResolver().openInputStream(sourceUri)) {
            if (inputStream == null) return;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);

            int scale = 1;
            int width = options.outWidth;
            int height = options.outHeight;
            while (width / 2 >= 1024 && height / 2 >= 1024) {
                width /= 2;
                height /= 2;
                scale *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;

            try (InputStream is2 = getContentResolver().openInputStream(sourceUri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is2, null, options);
                if (bitmap != null) {
                    File file = fileStorageHelper.getInternalImageFile(fileName);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                        fos.flush();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
