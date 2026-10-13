package com.litterboxd.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.litterboxd.app.R;
import com.litterboxd.app.auth.AuthCallback;
import com.litterboxd.app.auth.AuthRepository;
import com.litterboxd.app.auth.LocalAuthRepository;
import com.litterboxd.app.auth.Validator;
import com.litterboxd.app.data.dao.UserDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.User;
import com.litterboxd.app.data.session.SessionManager;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.util.AppExecutors;

/**
 * LoginActivity: email and password login screen with forgot password dialog.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilLoginEmail, tilLoginPassword;
    private TextInputEditText etLoginEmail, etLoginPassword;
    private MaterialButton btnLoginSubmit;
    private ProgressBar progressBarLogin;
    private TextView tvForgotPassword;

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        tilLoginEmail = findViewById(R.id.tilLoginEmail);
        tilLoginPassword = findViewById(R.id.tilLoginPassword);
        etLoginEmail = findViewById(R.id.etLoginEmail);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        btnLoginSubmit = findViewById(R.id.btnLoginSubmit);
        progressBarLogin = findViewById(R.id.progressBarLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        LitterboxdDbHelper dbHelper = new LitterboxdDbHelper(this);
        UserDao userDao = new UserDao(dbHelper);
        SessionManager sessionManager = new SessionManager(this);
        authRepository = new LocalAuthRepository(userDao, sessionManager, AppExecutors.getInstance());

        etLoginPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        btnLoginSubmit.setOnClickListener(v -> attemptLogin());

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void showForgotPasswordDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.msg_forgot_password_title)
                .setMessage(R.string.msg_forgot_password_message)
                .setPositiveButton(R.string.btn_create_new_account, (dialog, which) -> {
                    Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void attemptLogin() {
        tilLoginEmail.setError(null);
        tilLoginPassword.setError(null);

        String email = etLoginEmail.getText() != null ? etLoginEmail.getText().toString() : "";
        String password = etLoginPassword.getText() != null ? etLoginPassword.getText().toString() : "";

        boolean cancel = false;
        View focusView = null;

        if (!Validator.isValidPassword(password)) {
            tilLoginPassword.setError(getString(R.string.error_invalid_password));
            focusView = etLoginPassword;
            cancel = true;
        }

        if (!Validator.isValidEmail(email)) {
            tilLoginEmail.setError(getString(R.string.error_invalid_email));
            focusView = etLoginEmail;
            cancel = true;
        }

        if (cancel) {
            focusView.requestFocus();
        } else {
            setLoading(true);
            authRepository.login(email, password, new AuthCallback() {
                @Override
                public void onSuccess(User user) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    tilLoginEmail.setError(message);
                    etLoginEmail.requestFocus();
                }
            });
        }
    }

    private void setLoading(boolean loading) {
        progressBarLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLoginSubmit.setEnabled(!loading);
        etLoginEmail.setEnabled(!loading);
        etLoginPassword.setEnabled(!loading);
    }
}
