package com.litterboxd.app.ui.diary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.litterboxd.app.R;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.DiaryEntry;

import java.util.List;

/** Binds one cat's diary photos into the Memories staggered-grid collage. */
public class MemoriesPhotoAdapter extends RecyclerView.Adapter<MemoriesPhotoAdapter.PhotoViewHolder> {

    private final List<DiaryEntry> entries;
    private final FileStorageHelper fileStorageHelper;

    public MemoriesPhotoAdapter(List<DiaryEntry> entries, FileStorageHelper fileStorageHelper) {
        this.entries = entries;
        this.fileStorageHelper = fileStorageHelper;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_memory_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        DiaryEntry entry = entries.get(position);
        holder.image.setContentDescription(
                holder.image.getContext().getString(R.string.cd_diary_photo, entry.getTitle()));
        fileStorageHelper.showImageFile(holder.image, entry.getPhotoPath(), R.drawable.ic_cat_face);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imgMemoryPhoto);
        }
    }
}
