package com.litterboxd.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.auth.AuthRepository;
import com.litterboxd.app.auth.LocalAuthRepository;
import com.litterboxd.app.data.dao.UserDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.User;
import com.litterboxd.app.data.session.SessionManager;
import com.litterboxd.app.ui.auth.WelcomeActivity;
import com.litterboxd.app.util.AppExecutors;

/**
 * HomeActivity placeholder: displays welcome message for logged-in user or guest,
 * and provides logout capability with confirmation.
 */
public class HomeActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        TextView tvWelcomeUser = findViewById(R.id.tvWelcomeUser);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        LitterboxdDbHelper dbHelper = new LitterboxdDbHelper(this);
        UserDao userDao = new UserDao(dbHelper);
        sessionManager = new SessionManager(this);
        authRepository = new LocalAuthRepository(userDao, sessionManager, AppExecutors.getInstance());

        if (sessionManager.isGuest()) {
            tvWelcomeUser.setText(getString(R.string.welcome_guest));
        } else {
            User currentUser = authRepository.getCurrentUser();
            if (currentUser != null) {
                tvWelcomeUser.setText(getString(R.string.welcome_user, currentUser.getEmail()));
            } else {
                tvWelcomeUser.setText(getString(R.string.welcome_guest));
            }
        }

        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.msg_logout_title)
                .setMessage(R.string.msg_logout_message)
                .setPositiveButton(R.string.btn_yes, (dialog, which) -> {
                    authRepository.logout();
                    Intent intent = new Intent(HomeActivity.this, WelcomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
