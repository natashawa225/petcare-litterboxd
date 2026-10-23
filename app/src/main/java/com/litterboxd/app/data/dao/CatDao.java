package com.litterboxd.app.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;

import java.util.ArrayList;
import java.util.List;

public class CatDao {
    private final LitterboxdDbHelper dbHelper;

    public CatDao(LitterboxdDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertCat(Cat cat) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LitterboxdContract.CatEntry.COLUMN_NAME, cat.getName());
        values.put(LitterboxdContract.CatEntry.COLUMN_PHOTO_PATH, cat.getPhotoPath());
        if (cat.getBirthday() != null) {
            values.put(LitterboxdContract.CatEntry.COLUMN_BIRTHDAY, cat.getBirthday());
        }
        values.put(LitterboxdContract.CatEntry.COLUMN_APPROX_AGE_YEARS, cat.getApproxAgeYears());
        values.put(LitterboxdContract.CatEntry.COLUMN_APPROX_AGE_MONTHS, cat.getApproxAgeMonths());
        values.put(LitterboxdContract.CatEntry.COLUMN_BREED, cat.getBreed());
        values.put(LitterboxdContract.CatEntry.COLUMN_SEX, cat.getSex());
        values.put(LitterboxdContract.CatEntry.COLUMN_NEUTERED, cat.isNeutered() ? 1 : 0);
        values.put(LitterboxdContract.CatEntry.COLUMN_WEIGHT, cat.getWeight());
        values.put(LitterboxdContract.CatEntry.COLUMN_COAT_COLOR, cat.getCoatColor());
        values.put(LitterboxdContract.CatEntry.COLUMN_LIFESTYLE, cat.getLifestyle());
        values.put(LitterboxdContract.CatEntry.COLUMN_CREATED_AT, cat.getCreatedAt() > 0 ? cat.getCreatedAt() : System.currentTimeMillis());

        return db.insert(LitterboxdContract.CatEntry.TABLE_NAME, null, values);
    }

    public int updateCat(Cat cat) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LitterboxdContract.CatEntry.COLUMN_NAME, cat.getName());
        values.put(LitterboxdContract.CatEntry.COLUMN_PHOTO_PATH, cat.getPhotoPath());
        if (cat.getBirthday() != null) {
            values.put(LitterboxdContract.CatEntry.COLUMN_BIRTHDAY, cat.getBirthday());
        } else {
            values.putNull(LitterboxdContract.CatEntry.COLUMN_BIRTHDAY);
        }
        values.put(LitterboxdContract.CatEntry.COLUMN_APPROX_AGE_YEARS, cat.getApproxAgeYears());
        values.put(LitterboxdContract.CatEntry.COLUMN_APPROX_AGE_MONTHS, cat.getApproxAgeMonths());
        values.put(LitterboxdContract.CatEntry.COLUMN_BREED, cat.getBreed());
        values.put(LitterboxdContract.CatEntry.COLUMN_SEX, cat.getSex());
        values.put(LitterboxdContract.CatEntry.COLUMN_NEUTERED, cat.isNeutered() ? 1 : 0);
        values.put(LitterboxdContract.CatEntry.COLUMN_WEIGHT, cat.getWeight());
        values.put(LitterboxdContract.CatEntry.COLUMN_COAT_COLOR, cat.getCoatColor());
        values.put(LitterboxdContract.CatEntry.COLUMN_LIFESTYLE, cat.getLifestyle());

        return db.update(LitterboxdContract.CatEntry.TABLE_NAME, values,
                LitterboxdContract.CatEntry.COLUMN_ID + " = ?",
                new String[]{String.valueOf(cat.getId())});
    }

    public int deleteCat(long catId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(LitterboxdContract.CatEntry.TABLE_NAME,
                LitterboxdContract.CatEntry.COLUMN_ID + " = ?",
                new String[]{String.valueOf(catId)});
    }

    public Cat getCatById(long catId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cat cat = null;
        try (Cursor cursor = db.query(LitterboxdContract.CatEntry.TABLE_NAME,
                null,
                LitterboxdContract.CatEntry.COLUMN_ID + " = ?",
                new String[]{String.valueOf(catId)},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                cat = cursorToCat(cursor);
            }
        }
        return cat;
    }

    public List<Cat> getAllCats() {
        List<Cat> cats = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(LitterboxdContract.CatEntry.TABLE_NAME,
                null, null, null, null, null,
                LitterboxdContract.CatEntry.COLUMN_CREATED_AT + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    cats.add(cursorToCat(cursor));
                } while (cursor.moveToNext());
            }
        }
        return cats;
    }

    private Cat cursorToCat(Cursor cursor) {
        Cat cat = new Cat();
        cat.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_ID)));
        cat.setName(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_NAME)));
        cat.setPhotoPath(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_PHOTO_PATH)));
        int birthdayCol = cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_BIRTHDAY);
        if (!cursor.isNull(birthdayCol)) {
            cat.setBirthday(cursor.getLong(birthdayCol));
        }
        cat.setApproxAgeYears(cursor.getInt(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_APPROX_AGE_YEARS)));
        cat.setApproxAgeMonths(cursor.getInt(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_APPROX_AGE_MONTHS)));
        cat.setBreed(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_BREED)));
        cat.setSex(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_SEX)));
        cat.setNeutered(cursor.getInt(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_NEUTERED)) == 1);
        cat.setWeight(cursor.getDouble(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_WEIGHT)));
        cat.setCoatColor(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_COAT_COLOR)));
        cat.setLifestyle(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_LIFESTYLE)));
        cat.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.CatEntry.COLUMN_CREATED_AT)));
        return cat;
    }
}
