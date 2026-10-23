package com.litterboxd.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.auth.WelcomeActivity;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.ui.onboarding.FeedingScheduleActivity;

import java.util.List;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        AppPrefs appPrefs = new AppPrefs(this);
        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        CatDao catDao = new CatDao(dbHelper);

        List<Cat> cats = catDao.getAllCats();
        Intent intent;
        if (cats.isEmpty()) {
            intent = new Intent(this, WelcomeActivity.class);
        } else if (appPrefs.isOnboardingComplete()) {
            intent = new Intent(this, HomeActivity.class);
        } else {
            // App was closed after step 2 saved the cat: resume at step 3 instead of
            // restarting onboarding (which would add the same cat again).
            intent = new Intent(this, FeedingScheduleActivity.class);
            intent.putExtra("cat_id", cats.get(0).getId());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
