package com.litterboxd.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.litterboxd.app.R;
import com.litterboxd.app.data.session.SessionManager;
import com.litterboxd.app.ui.auth.WelcomeActivity;
import com.litterboxd.app.ui.home.HomeActivity;

/**
 * SplashActivity (launcher): no artificial delay.
 * Checks SessionManager and immediately routes to HomeActivity (if logged in or guest)
 * or WelcomeActivity, then finishes itself.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        SessionManager sessionManager = new SessionManager(this);
        Intent intent;
        if (sessionManager.isLoggedIn()) {
            intent = new Intent(this, HomeActivity.class);
        } else {
            intent = new Intent(this, WelcomeActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
