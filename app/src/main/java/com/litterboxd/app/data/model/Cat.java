package com.litterboxd.app.data.model;

public class Cat {
    private long id;
    private String name;
    private String photoPath;
    private Long birthday; // timestamp in millis
    private int approxAgeYears;
    private int approxAgeMonths;
    private String breed;
    private String sex;
    private boolean neutered;
    private double weight;
    private String coatColor;
    private String lifestyle;
    private long createdAt;

    public Cat() {}

    public Cat(long id, String name, String photoPath, Long birthday, int approxAgeYears, int approxAgeMonths,
               String breed, String sex, boolean neutered, double weight, String coatColor, String lifestyle, long createdAt) {
        this.id = id;
        this.name = name;
        this.photoPath = photoPath;
        this.birthday = birthday;
        this.approxAgeYears = approxAgeYears;
        this.approxAgeMonths = approxAgeMonths;
        this.breed = breed;
        this.sex = sex;
        this.neutered = neutered;
        this.weight = weight;
        this.coatColor = coatColor;
        this.lifestyle = lifestyle;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public Long getBirthday() { return birthday; }
    public void setBirthday(Long birthday) { this.birthday = birthday; }

    public int getApproxAgeYears() { return approxAgeYears; }
    public void setApproxAgeYears(int approxAgeYears) { this.approxAgeYears = approxAgeYears; }

    public int getApproxAgeMonths() { return approxAgeMonths; }
    public void setApproxAgeMonths(int approxAgeMonths) { this.approxAgeMonths = approxAgeMonths; }

    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public boolean isNeutered() { return neutered; }
    public void setNeutered(boolean neutered) { this.neutered = neutered; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public String getCoatColor() { return coatColor; }
    public void setCoatColor(String coatColor) { this.coatColor = coatColor; }

    public String getLifestyle() { return lifestyle; }
    public void setLifestyle(String lifestyle) { this.lifestyle = lifestyle; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
