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
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileWriter;
import java.io.IOException;

public class FileStorageHelper {
    private final Context context;

    public FileStorageHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Saves a cat profile image from a content URI (e.g. ACTION_GET_CONTENT) to internal storage.
     * Should be called on a background thread.
     *
     * @param sourceUri content URI of the picked image
     * @param fileName  target filename in internal storage
     * @return true if successful, false otherwise
     */
    public boolean saveImageToInternal(Uri sourceUri, String fileName) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
            if (inputStream == null) return false;
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Returns a File object representing an image stored in internal storage.
     */
    public File getInternalImageFile(String fileName) {
        return new File(context.getFilesDir(), fileName);
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
