package com.litterboxd.app;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.dao.DiaryDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.DiaryEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DiaryDaoTest {
    private LitterboxdDbHelper dbHelper;
    private CatDao catDao;
    private DiaryDao diaryDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        dbHelper = LitterboxdDbHelper.createInMemoryForTests(context); // never touches real data
        catDao = new CatDao(dbHelper);
        diaryDao = new DiaryDao(dbHelper);
    }

    @After
    public void tearDown() {
        dbHelper.close();
    }

    private long newCat(String name) {
        Cat cat = new Cat();
        cat.setName(name);
        cat.setCreatedAt(System.currentTimeMillis());
        return catDao.insertCat(cat);
    }

    private DiaryEntry entry(long catId, DiaryEntry.Type type, String title, long at, String photo) {
        DiaryEntry e = new DiaryEntry();
        e.setCatId(catId);
        e.setType(type);
        e.setTitle(title);
        e.setEntryAt(at);
        e.setPhotoPath(photo);
        return e;
    }

    @Test
    public void entriesAreNewestFirstAndPerCat() {
        long mochi = newCat("Mochi");
        long biscuit = newCat("Biscuit");
        long now = System.currentTimeMillis();
        diaryDao.insertEntry(entry(mochi, DiaryEntry.Type.MILESTONE, "First day home", now - 400L * 86_400_000L, null));
        diaryDao.insertEntry(entry(mochi, DiaryEntry.Type.MEMORY, "Sunbeam nap", now, null));
        diaryDao.insertEntry(entry(biscuit, DiaryEntry.Type.MEMORY, "Box fort", now, null));

        List<DiaryEntry> mochiEntries = diaryDao.getEntriesForCat(mochi);
        assertEquals(2, mochiEntries.size());
        assertEquals("Sunbeam nap", mochiEntries.get(0).getTitle());
        assertEquals(DiaryEntry.Type.MILESTONE, mochiEntries.get(1).getType());
        assertEquals(1, diaryDao.getEntriesForCat(biscuit).size());
    }

    @Test
    public void updateKeepsIdAndSavesTagsAndCaption() {
        long cat = newCat("Mochi");
        DiaryEntry e = entry(cat, DiaryEntry.Type.MEMORY, "Zoomies", System.currentTimeMillis(), null);
        long id = diaryDao.insertEntry(e);
        e.setTitle("Midnight zoomies");
        e.setDescription("Down the hallway");
        e.setTags(Arrays.asList("play", "funny"));
        assertEquals(1, diaryDao.updateEntry(e));

        DiaryEntry fetched = diaryDao.getEntryById(id);
        assertEquals("Midnight zoomies", fetched.getTitle());
        assertEquals("Down the hallway", fetched.getDescription());
        assertEquals(Arrays.asList("play", "funny"), fetched.getTags());
        assertTrue(fetched.getUpdatedAt() >= fetched.getCreatedAt());
    }

    @Test
    public void photoReferencesAndDelete() {
        long cat = newCat("Mochi");
        long now = System.currentTimeMillis();
        long a = diaryDao.insertEntry(entry(cat, DiaryEntry.Type.MEMORY, "A", now, "diary_photo_x.jpg"));
        diaryDao.insertEntry(entry(cat, DiaryEntry.Type.MEMORY, "B", now, "diary_photo_x.jpg"));
        assertEquals(2, diaryDao.countPhotoReferences("diary_photo_x.jpg"));

        diaryDao.deleteEntry(a);
        assertNull(diaryDao.getEntryById(a));
        assertEquals(1, diaryDao.countPhotoReferences("diary_photo_x.jpg"));
        assertEquals(Arrays.asList("diary_photo_x.jpg"), diaryDao.getPhotoPathsForCat(cat));
    }

    @Test
    public void deletingCatDeletesItsEntries() {
        long cat = newCat("Biscuit");
        diaryDao.insertEntry(entry(cat, DiaryEntry.Type.MEMORY, "Box fort", System.currentTimeMillis(), "diary_photo_y.jpg"));
        catDao.deleteCat(cat);
        assertTrue(diaryDao.getEntriesForCat(cat).isEmpty());
        assertEquals(0, diaryDao.countPhotoReferences("diary_photo_y.jpg"));
    }
}
