package com.litterboxd.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.UserDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.session.SessionManager;
import com.litterboxd.app.auth.LocalAuthRepository;
import com.litterboxd.app.util.AppExecutors;
import com.litterboxd.app.ui.home.HomeActivity;

/**
 * WelcomeActivity: landing screen with app name, tagline, mascot, and entry buttons.
 */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button btnSignUp = findViewById(R.id.btnSignUp);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnGuest = findViewById(R.id.btnGuest);

        btnSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        btnGuest.setOnClickListener(v -> {
            SessionManager sessionManager = new SessionManager(WelcomeActivity.this);
            sessionManager.createGuestSession();
            Intent intent = new Intent(WelcomeActivity.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
