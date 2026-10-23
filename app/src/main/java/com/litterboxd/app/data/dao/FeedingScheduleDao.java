package com.litterboxd.app.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.litterboxd.app.data.db.LitterboxdContract;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.FeedingMeal;

import java.util.ArrayList;
import java.util.List;

public class FeedingScheduleDao {
    private final LitterboxdDbHelper dbHelper;

    public FeedingScheduleDao(LitterboxdDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertFeedingMeal(FeedingMeal meal) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LitterboxdContract.FeedingScheduleEntry.COLUMN_CAT_ID, meal.getCatId());
        values.put(LitterboxdContract.FeedingScheduleEntry.COLUMN_MEAL_LABEL, meal.getMealLabel());
        values.put(LitterboxdContract.FeedingScheduleEntry.COLUMN_MEAL_TIME, meal.getMealTime());
        values.put(LitterboxdContract.FeedingScheduleEntry.COLUMN_PORTION_GRAMS, meal.getPortionGrams());
        return db.insert(LitterboxdContract.FeedingScheduleEntry.TABLE_NAME, null, values);
    }

    public int deleteFeedingMealsForCat(long catId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(LitterboxdContract.FeedingScheduleEntry.TABLE_NAME,
                LitterboxdContract.FeedingScheduleEntry.COLUMN_CAT_ID + " = ?",
                new String[]{String.valueOf(catId)});
    }

    public List<FeedingMeal> getFeedingMealsForCat(long catId) {
        List<FeedingMeal> meals = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(LitterboxdContract.FeedingScheduleEntry.TABLE_NAME,
                null,
                LitterboxdContract.FeedingScheduleEntry.COLUMN_CAT_ID + " = ?",
                new String[]{String.valueOf(catId)},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    FeedingMeal meal = new FeedingMeal();
                    meal.setId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.FeedingScheduleEntry.COLUMN_ID)));
                    meal.setCatId(cursor.getLong(cursor.getColumnIndexOrThrow(LitterboxdContract.FeedingScheduleEntry.COLUMN_CAT_ID)));
                    meal.setMealLabel(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.FeedingScheduleEntry.COLUMN_MEAL_LABEL)));
                    meal.setMealTime(cursor.getString(cursor.getColumnIndexOrThrow(LitterboxdContract.FeedingScheduleEntry.COLUMN_MEAL_TIME)));
                    meal.setPortionGrams(cursor.getDouble(cursor.getColumnIndexOrThrow(LitterboxdContract.FeedingScheduleEntry.COLUMN_PORTION_GRAMS)));
                    meals.add(meal);
                } while (cursor.moveToNext());
            }
        }
        return meals;
    }
}
