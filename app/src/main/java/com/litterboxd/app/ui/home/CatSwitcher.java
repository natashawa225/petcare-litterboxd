package com.litterboxd.app.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.litterboxd.app.R;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.onboarding.AddEditCatActivity;

import java.util.List;

/**
 * The "Select cat" dialog shared by the Daily, Tasks and Diary tabs: one row per cat (with its own
 * avatar) plus a last row to add another cat. Picking a cat saves it as the selected cat, so every
 * tab shows the same cat.
 */
public final class CatSwitcher {

    public interface OnCatPicked {
        void onCatPicked(Cat cat);
    }

    private CatSwitcher() {}

    public static void show(Activity activity, List<Cat> cats, OnCatPicked listener) {
        if (cats == null || cats.isEmpty()) return;
        // Each row shows that cat's own profile picture (cats.photo_path), never the owner's avatar.
        FileStorageHelper storage = new FileStorageHelper(activity);
        ArrayAdapter<Cat> adapter = new ArrayAdapter<Cat>(activity, R.layout.item_cat_switcher_row, cats) {
            @Override
            public int getCount() {
                return cats.size() + 1; // + "Add another cat"
            }

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_cat_switcher_row, parent, false);
                ImageView avatar = row.findViewById(R.id.imgSwitcherCat);
                TextView name = row.findViewById(R.id.tvSwitcherCatName);
                if (position < cats.size()) {
                    Cat cat = cats.get(position);
                    name.setText(cat.getName());
                    storage.showCatPhoto(avatar, cat.getPhotoPath());
                } else {
                    name.setText(R.string.add_another_cat);
                    avatar.setTag(null); // cancels a pending photo load from a recycled row
                    avatar.setImageResource(R.drawable.ic_add);
                }
                return row;
            }
        };

        new AlertDialog.Builder(activity)
                .setTitle(R.string.title_cat_switcher)
                .setAdapter(adapter, (dialog, which) -> {
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
