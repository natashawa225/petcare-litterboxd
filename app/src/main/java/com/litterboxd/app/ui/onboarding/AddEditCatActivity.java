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
import com.litterboxd.app.util.Validators;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.UUID;

public class AddEditCatActivity extends AppCompatActivity {

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
        String name = etCatName.getText() != null ? etCatName.getText().toString().trim() : "";
        if (!Validators.isValidCatName(name)) {
            tilCatName.setError(getString(R.string.error_select_cat_name));
            return;
        }
        tilCatName.setError(null);

        String weightStr = etWeight.getText() != null ? etWeight.getText().toString().trim() : "";
        double weight = 4.0;
        try {
            weight = Double.parseDouble(weightStr);
        } catch (NumberFormatException ignored) {}

        if (!Validators.isValidWeight(weight)) {
            tilWeight.setError(getString(R.string.error_invalid_weight));
            return;
        }
        tilWeight.setError(null);

        progressBar.setVisibility(View.VISIBLE);
        btnSaveCat.setEnabled(false);

        final double finalWeight = weight;
        AppExecutors.getInstance().diskIO().execute(() -> {
            if (pickedImageUri != null) {
                savedPhotoFileName = "cat_photo_" + UUID.randomUUID().toString() + ".jpg";
                downscaleAndSaveImage(pickedImageUri, savedPhotoFileName);
            }

            Cat cat = new Cat();
            cat.setName(name);
            cat.setPhotoPath(savedPhotoFileName);
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
            cat.setWeight(finalWeight);

            String coat = "Black";
            int coatId = rgCoatColor.getCheckedRadioButtonId();
            if (coatId == R.id.rbColorWhite) coat = "White";
            else if (coatId == R.id.rbColorOrange) coat = "Ginger";
            else if (coatId == R.id.rbColorGrey) coat = "Grey";
            else if (coatId == R.id.rbColorCalico) coat = "Calico";
            else if (coatId == R.id.rbColorTabby) coat = "Tabby";
            cat.setCoatColor(coat);

            String lifestyle = "Indoor only";
            int lifeId = rgLifestyle.getCheckedRadioButtonId();
            if (lifeId == R.id.rbLifestyleOutdoor) lifestyle = "Outdoor";
            else if (lifeId == R.id.rbLifestyleBoth) lifestyle = "Indoor / Outdoor";
            cat.setLifestyle(lifestyle);

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
