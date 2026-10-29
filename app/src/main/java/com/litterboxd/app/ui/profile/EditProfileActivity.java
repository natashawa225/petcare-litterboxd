package com.litterboxd.app.ui.profile;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ProgressBar;
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
import com.litterboxd.app.R;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.util.AppExecutors;

import java.util.UUID;

/**
 * Edits the owner's display name and avatar. Nothing is saved until Save is tapped; Cancel and
 * Back leave the profile as it was. The pending choice survives rotation (onSaveInstanceState).
 */
public class EditProfileActivity extends AppCompatActivity {

    private static final String STATE_AVATAR_VALUE = "avatar_value";
    private static final String STATE_PICKED_URI = "picked_uri";

    private ShapeableImageView imgAvatarPreview;
    private ProgressBar progressAvatar;
    private TextInputEditText etName;
    private GridLayout gridAvatars;
    private MaterialButton btnSave;

    private AppPrefs appPrefs;
    private FileStorageHelper fileStorageHelper;

    /** Chosen avatar as stored in AppPrefs (bundled/file/null = default), when no new photo is picked. */
    private String pendingAvatarValue;
    /** A photo picked in this session; it is copied into private storage only on Save. */
    private Uri pickedPhotoUri;

    private final ActivityResultLauncher<String> photoPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return; // picker cancelled: keep the current choice
                pickedPhotoUri = uri;
                renderSelection();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        appPrefs = new AppPrefs(this);
        fileStorageHelper = new FileStorageHelper(this);

        imgAvatarPreview = findViewById(R.id.imgAvatarPreview);
        progressAvatar = findViewById(R.id.progressAvatar);
        etName = findViewById(R.id.etOwnerDisplayName);
        gridAvatars = findViewById(R.id.gridAvatars);
        btnSave = findViewById(R.id.btnSaveProfile);

        if (savedInstanceState == null) {
            etName.setText(appPrefs.getOwnerName());
            pendingAvatarValue = appPrefs.getOwnerAvatar();
        } else {
            pendingAvatarValue = savedInstanceState.getString(STATE_AVATAR_VALUE);
            pickedPhotoUri = savedInstanceState.getParcelable(STATE_PICKED_URI);
        }

        buildAvatarGrid();
        renderSelection();

        // Only images can be picked; GetContent needs no storage permission.
        findViewById(R.id.btnUploadPhoto).setOnClickListener(v -> photoPicker.launch("image/*"));
        findViewById(R.id.btnUseDefaultAvatar).setOnClickListener(v -> {
            pickedPhotoUri = null;
            pendingAvatarValue = null;
            renderSelection();
        });
        findViewById(R.id.btnCancelProfile).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_AVATAR_VALUE, pendingAvatarValue);
        outState.putParcelable(STATE_PICKED_URI, pickedPhotoUri);
    }

    /** One circular, tappable thumbnail per bundled avatar, in a fixed-size grid. */
    private void buildAvatarGrid() {
        int size = getResources().getDimensionPixelSize(R.dimen.avatar_choice_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.spacing_tiny);
        ShapeAppearanceModel circle = ShapeAppearanceModel.builder().setAllCornerSizes(size / 2f).build();
        int columns = getResources().getInteger(R.integer.avatar_grid_columns);
        for (int i = 0; i < OwnerAvatars.BUNDLED.length; i++) {
            final int index = i;
            ShapeableImageView option = new ShapeableImageView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / columns), GridLayout.spec(i % columns, 1f));
            params.width = size;
            params.height = size;
            // Columns share the width (weight 1), but each thumbnail stays a fixed-size circle.
            params.setGravity(Gravity.CENTER);
            params.setMargins(margin, margin, margin, margin);
            option.setLayoutParams(params);
            option.setScaleType(ShapeableImageView.ScaleType.CENTER_CROP);
            option.setShapeAppearanceModel(circle);
            option.setImageResource(OwnerAvatars.BUNDLED[i]);
            option.setStrokeColorResource(R.color.ink);
            option.setOnClickListener(v -> {
                pickedPhotoUri = null;
                pendingAvatarValue = OwnerAvatars.bundledValue(this, OwnerAvatars.BUNDLED[index]);
                renderSelection();
            });
            gridAvatars.addView(option);
        }
    }

    /** Updates the large preview and marks the selected thumbnail (thicker ring + spoken "selected"). */
    private void renderSelection() {
        int selected = pickedPhotoUri == null ? OwnerAvatars.bundledIndex(this, pendingAvatarValue) : -1;
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
            String name = getString(OwnerAvatars.BUNDLED_NAMES[i]);
            option.setContentDescription(getString(isSelected ? R.string.cd_avatar_selected : R.string.cd_avatar_option, name));
        }

        if (pickedPhotoUri == null) {
            OwnerAvatars.show(this, imgAvatarPreview, pendingAvatarValue);
            return;
        }
        // Decode the picked photo downscaled and off the UI thread (it may be a large camera image).
        final Uri uri = pickedPhotoUri;
        progressAvatar.setVisibility(View.VISIBLE);
        AppExecutors.getInstance().diskIO().execute(() -> {
            Bitmap preview = fileStorageHelper.loadScaledBitmap(uri, FileStorageHelper.PREVIEW_MIN_SIDE_PX);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed() || uri != pickedPhotoUri) return;
                progressAvatar.setVisibility(View.GONE);
                if (preview != null) {
                    imgAvatarPreview.setImageBitmap(preview);
                } else {
                    // Not a readable image: drop it and keep the previous choice.
                    pickedPhotoUri = null;
                    Toast.makeText(this, R.string.error_photo_save, Toast.LENGTH_SHORT).show();
                    renderSelection();
                }
            });
        });
    }

    private void save() {
        btnSave.setEnabled(false);
        final String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        final Uri photo = pickedPhotoUri;
        final String chosenValue = pendingAvatarValue;
        final String oldValue = appPrefs.getOwnerAvatar();

        AppExecutors.getInstance().diskIO().execute(() -> {
            String newValue = chosenValue;
            if (photo != null) {
                String fileName = FileStorageHelper.OWNER_AVATAR_PREFIX + UUID.randomUUID() + ".jpg";
                if (!fileStorageHelper.saveDownscaledImage(photo, fileName)) {
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        btnSave.setEnabled(true);
                        Toast.makeText(this, R.string.error_photo_save, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                newValue = OwnerAvatars.fileValue(fileName);
            }
            // A replaced or removed custom photo is deleted so it doesn't linger in storage.
            String oldFile = OwnerAvatars.fileName(oldValue);
            if (oldFile != null && !oldFile.equals(OwnerAvatars.fileName(newValue))) {
                fileStorageHelper.deleteImage(oldFile);
            }
            appPrefs.setOwnerName(name);
            appPrefs.setOwnerAvatar(newValue);

            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, R.string.msg_profile_saved, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
