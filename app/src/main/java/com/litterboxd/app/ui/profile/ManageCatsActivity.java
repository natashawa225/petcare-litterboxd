package com.litterboxd.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.ui.onboarding.AddEditCatActivity;
import com.litterboxd.app.util.AgeUtils;
import com.litterboxd.app.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

public class ManageCatsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewCats;
    private View layoutEmpty;
    private CatDao catDao;
    private FileStorageHelper fileStorageHelper;
    private CatAdapter adapter;
    private final List<Cat> catList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_cats);

        catDao = new CatDao(LitterboxdDbHelper.getInstance(this));
        fileStorageHelper = new FileStorageHelper(this);

        recyclerViewCats = findViewById(R.id.recyclerViewCats);
        layoutEmpty = findViewById(R.id.layoutEmptyCats);
        MaterialButton btnAddCat = findViewById(R.id.btnAddCat);

        recyclerViewCats.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CatAdapter();
        recyclerViewCats.setAdapter(adapter);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
        btnAddCat.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditCatActivity.class);
            intent.putExtra(AddEditCatActivity.EXTRA_ADD_ANOTHER, true);
            startActivity(intent);
        });
        findViewById(R.id.btnAddCatEmpty).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditCatActivity.class);
            intent.putExtra(AddEditCatActivity.EXTRA_ADD_ANOTHER, true);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCats();
    }

    private void loadCats() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<Cat> cats = catDao.getAllCats();
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                catList.clear();
                catList.addAll(cats);
                adapter.notifyDataSetChanged();
                boolean isEmpty = catList.isEmpty();
                recyclerViewCats.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            });
        });
    }

    private class CatAdapter extends RecyclerView.Adapter<CatAdapter.CatViewHolder> {
        @NonNull
        @Override
        public CatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manage_cat, parent, false);
            return new CatViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CatViewHolder holder, int position) {
            Cat cat = catList.get(position);
            holder.tvName.setText(cat.getName());
            String lifeStage = AgeUtils.calculateLifeStage(cat.getBirthday(), cat.getApproxAgeYears(), cat.getApproxAgeMonths());
            String details = getString(R.string.profile_details_summary, cat.getBreed(), lifeStage);
            holder.tvDetails.setText(details);
            fileStorageHelper.showCatPhoto(holder.imgAvatar, cat.getPhotoPath());

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ManageCatsActivity.this, CatProfileActivity.class);
                intent.putExtra("cat_id", cat.getId());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return catList.size();
        }

        class CatViewHolder extends RecyclerView.ViewHolder {
            ImageView imgAvatar;
            TextView tvName, tvDetails;

            CatViewHolder(@NonNull View itemView) {
                super(itemView);
                imgAvatar = itemView.findViewById(R.id.imgCatAvatar);
                tvName = itemView.findViewById(R.id.tvCatName);
                tvDetails = itemView.findViewById(R.id.tvCatDetails);
            }
        }
    }
}
