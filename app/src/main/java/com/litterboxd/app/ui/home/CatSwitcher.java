package com.litterboxd.app.ui.home;

import android.app.Activity;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import com.litterboxd.app.R;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.onboarding.AddEditCatActivity;

import java.util.List;

/**
 * The "Select cat" dialog shared by the Daily and Tasks tabs: one row per cat plus a last row to
 * add another cat. Picking a cat saves it as the selected cat, so every tab shows the same cat.
 */
public final class CatSwitcher {

    public interface OnCatPicked {
        void onCatPicked(Cat cat);
    }

    private CatSwitcher() {}

    public static void show(Activity activity, List<Cat> cats, OnCatPicked listener) {
        if (cats == null || cats.isEmpty()) return;
        String[] items = new String[cats.size() + 1];
        for (int i = 0; i < cats.size(); i++) {
            items[i] = cats.get(i).getName();
        }
        items[cats.size()] = activity.getString(R.string.add_another_cat);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.title_cat_switcher)
                .setItems(items, (dialog, which) -> {
                    if (which == cats.size()) {
                        Intent intent = new Intent(activity, AddEditCatActivity.class);
                        intent.putExtra(AddEditCatActivity.EXTRA_ADD_ANOTHER, true);
                        activity.startActivity(intent);
                        return;
                    }
                    Cat chosen = cats.get(which);
                    new AppPrefs(activity).setSelectedCatId(chosen.getId());
                    listener.onCatPicked(chosen);
                })
                .show();
    }
}
