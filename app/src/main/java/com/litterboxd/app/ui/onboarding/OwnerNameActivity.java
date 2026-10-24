package com.litterboxd.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.util.Validators;

public class OwnerNameActivity extends AppCompatActivity {

    private TextInputEditText etOwnerName;
    private TextInputLayout tilOwnerName;
    private AppPrefs appPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_name);

        appPrefs = new AppPrefs(this);
        etOwnerName = findViewById(R.id.etOwnerName);
        tilOwnerName = findViewById(R.id.tilOwnerName);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);
        MaterialButton btnSkip = findViewById(R.id.btnSkip);

        // Pre-fill if already set
        String existingName = appPrefs.getOwnerName();
        if (existingName != null && !existingName.isEmpty()) {
            etOwnerName.setText(existingName);
        }

        btnContinue.setOnClickListener(v -> {
            String name = etOwnerName.getText() != null ? etOwnerName.getText().toString().trim() : "";
            if (!Validators.isValidOwnerName(name)) {
                tilOwnerName.setError(getString(R.string.error_field_required));
                return;
            }
            tilOwnerName.setError(null);
            appPrefs.setOwnerName(name);
            startAddEditCat();
        });

        btnSkip.setOnClickListener(v -> {
            if (appPrefs.getOwnerName().isEmpty()) {
                appPrefs.setOwnerName(getString(R.string.default_owner_name));
            }
            startAddEditCat();
        });
    }

    private void startAddEditCat() {
        Intent intent = new Intent(this, AddEditCatActivity.class);
        startActivity(intent);
    }
}
