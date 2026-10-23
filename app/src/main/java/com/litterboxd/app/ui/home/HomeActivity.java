package com.litterboxd.app.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.profile.CatProfileActivity;
import com.litterboxd.app.ui.settings.SettingsActivity;
import com.litterboxd.app.util.AgeUtils;
import com.litterboxd.app.util.AppExecutors;

import java.io.File;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private TextView tvWelcomeUser, tvHomeCatName, tvHomeCatDetails;
    private ImageView imgHomeCatPhoto;
    private MaterialButton btnSwitchCat, btnViewCatProfile, btnSettings;

    private AppPrefs appPrefs;
    private CatDao catDao;
    private FileStorageHelper fileStorageHelper;
    private Cat currentCat;
    private List<Cat> allCats;

    private static final int PERMISSION_REQUEST_CODE_NOTIFICATION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        appPrefs = new AppPrefs(this);
        catDao = new CatDao(new LitterboxdDbHelper(this));
        fileStorageHelper = new FileStorageHelper(this);

        tvWelcomeUser = findViewById(R.id.tvWelcomeUser);
        tvHomeCatName = findViewById(R.id.tvHomeCatName);
        tvHomeCatDetails = findViewById(R.id.tvHomeCatDetails);
        imgHomeCatPhoto = findViewById(R.id.imgHomeCatPhoto);
        btnSwitchCat = findViewById(R.id.btnSwitchCat);
        btnViewCatProfile = findViewById(R.id.btnViewCatProfile);
        btnSettings = findViewById(R.id.btnSettings);

        btnSwitchCat.setOnClickListener(v -> showCatSwitcherDialog());
        btnViewCatProfile.setOnClickListener(v -> {
            if (currentCat != null) {
                Intent intent = new Intent(this, CatProfileActivity.class);
                intent.putExtra("cat_id", currentCat.getId());
                startActivity(intent);
            }
        });
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        checkNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String ownerName = appPrefs.getOwnerName();
            if (ownerName == null || ownerName.isEmpty()) {
                ownerName = "Cat Parent";
            }
            final String greeting = getString(R.string.home_greeting, ownerName);

            allCats = catDao.getAllCats();
            long selectedId = appPrefs.getSelectedCatId();
            currentCat = null;
            if (!allCats.isEmpty()) {
                for (Cat c : allCats) {
                    if (c.getId() == selectedId) {
                        currentCat = c;
                        break;
                    }
                }
                if (currentCat == null) {
                    currentCat = allCats.get(0);
                    appPrefs.setSelectedCatId(currentCat.getId());
                }
            }

            AppExecutors.getInstance().mainThread().execute(() -> {
                tvWelcomeUser.setText(greeting);
                updateCatCard();
            });
        });
    }

    private void updateCatCard() {
        if (currentCat != null) {
            tvHomeCatName.setText(currentCat.getName());
            String ageStr = AgeUtils.getAgeDisplay(currentCat.getBirthday(), currentCat.getApproxAgeYears(), currentCat.getApproxAgeMonths());
            String details = currentCat.getBreed() + " • " + ageStr + " • " + currentCat.getWeight() + " kg";
            tvHomeCatDetails.setText(details);

            if (currentCat.getPhotoPath() != null && !currentCat.getPhotoPath().isEmpty()) {
                File file = fileStorageHelper.getInternalImageFile(currentCat.getPhotoPath());
                if (file.exists()) {
                    imgHomeCatPhoto.setImageURI(Uri.fromFile(file));
                } else {
                    imgHomeCatPhoto.setImageResource(R.drawable.ic_cat_face);
                }
            } else {
                imgHomeCatPhoto.setImageResource(R.drawable.ic_cat_face);
            }
            btnSwitchCat.setVisibility(allCats.size() > 1 ? View.VISIBLE : View.GONE);
        } else {
            tvHomeCatName.setText("No cat selected");
            tvHomeCatDetails.setText("");
            imgHomeCatPhoto.setImageResource(R.drawable.ic_cat_face);
            btnSwitchCat.setVisibility(View.GONE);
        }
    }

    private void showCatSwitcherDialog() {
        if (allCats == null || allCats.isEmpty()) return;
        String[] catNames = new String[allCats.size()];
        for (int i = 0; i < allCats.size(); i++) {
            catNames[i] = allCats.get(i).getName();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_cat_switcher)
                .setItems(catNames, (dialog, which) -> {
                    Cat chosen = allCats.get(which);
                    appPrefs.setSelectedCatId(chosen.getId());
                    currentCat = chosen;
                    updateCatCard();
                })
                .show();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!appPrefs.isNotificationPermissionAsked()) {
                appPrefs.setNotificationPermissionAsked(true);
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE_NOTIFICATION);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_NOTIFICATION
                && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, R.string.msg_notifications_denied, Toast.LENGTH_LONG).show();
        }
    }
}
