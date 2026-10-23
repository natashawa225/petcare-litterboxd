package com.litterboxd.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.auth.WelcomeActivity;
import com.litterboxd.app.ui.home.HomeActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        AppPrefs appPrefs = new AppPrefs(this);
        LitterboxdDbHelper dbHelper = new LitterboxdDbHelper(this);
        CatDao catDao = new CatDao(dbHelper);

        boolean hasCats = !catDao.getAllCats().isEmpty();
        Intent intent;
        if (appPrefs.isOnboardingComplete() && hasCats) {
            intent = new Intent(this, HomeActivity.class);
        } else {
            intent = new Intent(this, WelcomeActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
