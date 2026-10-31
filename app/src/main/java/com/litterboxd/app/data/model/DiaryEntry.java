package com.litterboxd.app.data.model;

import java.util.ArrayList;
import java.util.List;

/** A Cat Diary entry written by the owner: a memory or a milestone. */
public class DiaryEntry {

    public enum Type {
        MEMORY,
        MILESTONE;

        public static Type fromStorage(String value) {
            return "MILESTONE".equals(value) ? MILESTONE : MEMORY;
        }
    }

    private long id;
    private long catId;
    private Type type = Type.MEMORY;
    private String title;
    private String description;
    private long entryAt;
    private String photoPath;
    private List<String> tags = new ArrayList<>();
    private long createdAt;
    private long updatedAt;

    public DiaryEntry() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCatId() { return catId; }
    public void setCatId(long catId) { this.catId = catId; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** When the moment happened, in millis. */
    public long getEntryAt() { return entryAt; }
    public void setEntryAt(long entryAt) { this.entryAt = entryAt; }

    /** Private file name of the photo (see FileStorageHelper), or null. */
    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    /** Stable tag keys (see util.DiaryTags), never null. */
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public boolean hasPhoto() {
        return photoPath != null && !photoPath.isEmpty();
    }
}
