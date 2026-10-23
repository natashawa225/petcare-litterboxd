package com.litterboxd.app.data.model;

public class WeightLog {
    private long id;
    private long catId;
    private double weight;
    private long date;
    private String notes;

    public WeightLog() {}

    public WeightLog(long id, long catId, double weight, long date, String notes) {
        this.id = id;
        this.catId = catId;
        this.weight = weight;
        this.date = date;
        this.notes = notes;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCatId() { return catId; }
    public void setCatId(long catId) { this.catId = catId; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
