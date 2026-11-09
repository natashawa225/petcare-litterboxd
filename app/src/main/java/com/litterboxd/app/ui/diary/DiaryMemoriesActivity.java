package com.litterboxd.app.ui.diary;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DiaryEntry;
import com.litterboxd.app.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * Memories: every photo from the selected cat's diary, in one asymmetrical scrapbook collage.
 * Read-only; opened from the "Memories" banner on the Diary tab.
 */
public class DiaryMemoriesActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";

    private long catId;
    private TextView tvTitle;
    private RecyclerView recyclerMemories;
    private View tvEmpty;

    private CatDao catDao;
    private DiaryDao diaryDao;
    private FileStorageHelper fileStorageHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary_memories);

        LitterboxdDbHelper dbHelper = LitterboxdDbHelper.getInstance(this);
        catDao = new CatDao(dbHelper);
        diaryDao = new DiaryDao(dbHelper);
        fileStorageHelper = new FileStorageHelper(this);

        catId = getIntent().getLongExtra(EXTRA_CAT_ID, -1L);
        if (catId == -1L) {
            finish();
            return;
        }

        tvTitle = findViewById(R.id.tvMemoriesTitle);
        recyclerMemories = findViewById(R.id.recyclerMemories);
        tvEmpty = findViewById(R.id.tvMemoriesEmpty);
        findViewById(R.id.btnMemoriesBack).setOnClickListener(v -> finish());

        recyclerMemories.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));

        loadMemories();
    }

    private void loadMemories() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            Cat cat = catDao.getCatById(catId);
            List<DiaryEntry> withPhotos = new ArrayList<>();
            if (cat != null) {
                for (DiaryEntry entry : diaryDao.getEntriesForCat(catId)) {
                    if (entry.hasPhoto()) withPhotos.add(entry);
                }
            }
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (cat == null) {
                    finish();
                    return;
                }
                tvTitle.setText(getString(R.string.diary_memories_screen_title, cat.getName()));
                boolean hasPhotos = !withPhotos.isEmpty();
                recyclerMemories.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
                tvEmpty.setVisibility(hasPhotos ? View.GONE : View.VISIBLE);
                if (hasPhotos) {
                    recyclerMemories.setAdapter(new MemoriesPhotoAdapter(withPhotos, fileStorageHelper));
                }
            });
        });
    }
}
