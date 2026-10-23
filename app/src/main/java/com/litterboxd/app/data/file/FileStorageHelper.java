package com.litterboxd.app.data.file;

/**
 * FILE STORAGE HELPER
 * Storage Types Used & Why:
 * 1. Internal Storage (Context.openFileOutput / openFileInput, getFilesDir()): Used for private app data
 *    such as user-selected cat profile photos (secure, sandboxed, inaccessible to other apps).
 * 2. External App-Specific Storage (Context.getExternalFilesDir(null), Environment.MEDIA_MOUNTED check):
 *    Used for exportable files (e.g., CSV vet reports) that users may want to share or access externally,
 *    while still scoped to the app package and cleaned up on app uninstall.
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.widget.ImageView;

import com.litterboxd.app.R;
import com.litterboxd.app.util.AppExecutors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.IOException;

public class FileStorageHelper {
    public static final String CAT_PHOTO_PREFIX = "cat_photo_";
    public static final int PREVIEW_MIN_SIDE_PX = 256;
    private static final int SAVED_PHOTO_MIN_SIDE_PX = 1024;

    private final Context context;

    public FileStorageHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Saves a picked image (e.g. from ACTION_GET_CONTENT) into internal storage as a JPEG,
     * downscaled so its shorter side is at most ~2048 px. Call on a background thread.
     *
     * @return true if the file was written
     */
    public boolean saveDownscaledImage(Uri sourceUri, String fileName) {
        Bitmap bitmap = loadScaledBitmap(sourceUri, SAVED_PHOTO_MIN_SIDE_PX);
        if (bitmap == null) return false;
        try (FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Decodes an image (content:// or file:// Uri) using inSampleSize so a large camera photo
     * is never loaded at full resolution (avoids OutOfMemoryError). The result's shorter side is
     * at least minSidePx. Call on a background thread; returns null if it cannot be read.
     */
    public Bitmap loadScaledBitmap(Uri uri, int minSidePx) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            BitmapFactory.decodeStream(is, null, options);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        int scale = 1;
        int width = options.outWidth;
        int height = options.outHeight;
        while (width / 2 >= minSidePx && height / 2 >= minSidePx) {
            width /= 2;
            height /= 2;
            scale *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = scale;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            return BitmapFactory.decodeStream(is, null, options);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Shows a saved cat photo in an ImageView: placeholder first, then the downscaled photo
     * decoded on a worker thread and posted back with View.post(). Call from the UI thread.
     */
    public void showCatPhoto(ImageView imageView, String fileName) {
        imageView.setImageResource(R.drawable.ic_cat_face);
        imageView.setTag(fileName);
        if (fileName == null || fileName.isEmpty()) return;
        AppExecutors.getInstance().diskIO().execute(() -> {
            File file = getInternalImageFile(fileName);
            Bitmap bitmap = file.exists() ? loadScaledBitmap(Uri.fromFile(file), PREVIEW_MIN_SIDE_PX) : null;
            imageView.post(() -> {
                // Skip if the view was asked to show a different cat in the meantime.
                if (bitmap != null && fileName.equals(imageView.getTag())) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    /**
     * Returns a File object representing an image stored in internal storage.
     */
    public File getInternalImageFile(String fileName) {
        return new File(context.getFilesDir(), fileName);
    }

    /** Deletes one cat photo from internal storage; null or missing files are ignored. */
    public void deleteImage(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        File file = getInternalImageFile(fileName);
        if (file.exists()) file.delete();
    }

    /** Deletes every cat photo (files named cat_photo_*) from internal storage. Used by Reset app. */
    public void deleteAllCatPhotos() {
        File[] files = context.getFilesDir().listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(CAT_PHOTO_PREFIX)) file.delete();
        }
    }

    /**
     * Exports content (e.g., health logs CSV vet report) to external app-specific storage.
     * Verifies that external storage is mounted before writing.
     *
     * @param fileName name of the export file (e.g., "vet_report.csv")
     * @param content  CSV or text content to write
     * @return Result message or null if successful, or error description if failed.
     */
    public String exportCsv(String fileName, String content) {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return "Error: External storage is not mounted (Media not mounted).";
        }

        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            return "Error: External files directory is not available.";
        }

        File exportFile = new File(externalDir, fileName);
        try (FileWriter writer = new FileWriter(exportFile, false)) {
            writer.write(content);
            writer.flush();
            return null; // Null means success
        } catch (IOException e) {
            e.printStackTrace();
            return "Error writing file: " + e.getMessage();
        }
    }
}
