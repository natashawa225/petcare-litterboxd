package com.litterboxd.app.data.model;

public class FeedingMeal {
    private long id;
    private long catId;
    private String mealLabel;
    private String mealTime;
    private double portionGrams;

    public FeedingMeal() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCatId() { return catId; }
    public void setCatId(long catId) { this.catId = catId; }

    public String getMealLabel() { return mealLabel; }
    public void setMealLabel(String mealLabel) { this.mealLabel = mealLabel; }

    public String getMealTime() { return mealTime; }
    public void setMealTime(String mealTime) { this.mealTime = mealTime; }

    public double getPortionGrams() { return portionGrams; }
    public void setPortionGrams(double portionGrams) { this.portionGrams = portionGrams; }
}
