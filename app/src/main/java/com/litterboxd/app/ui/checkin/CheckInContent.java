package com.litterboxd.app.ui.checkin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.litterboxd.app.R;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.TreeJsonParser;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Android side of the check-in content: reads res/raw/checkin_tree.json and turns the string
 * resource NAMES stored in it into text. Shared by CheckInActivity (the form) and the Daily tab
 * (today's summary) so both read the questions the same way.
 */
public final class CheckInContent {

    private static final String TAG = "CheckInContent";

    private CheckInContent() {}

    /** Loads and validates the question tree. Call off the UI thread. Returns null if it is broken. */
    public static DecisionTree loadTree(Context context) {
        try (InputStream in = context.getResources().openRawResource(R.raw.checkin_tree)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return TreeJsonParser.parse(out.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException | JSONException | IllegalArgumentException e) {
            Log.e(TAG, "Could not load the check-in questions", e);
            return null;
        }
    }

    /**
     * Looks up a string resource by name, filling in %1$s if present. The JSON stores names so all
     * wording stays in strings.xml. Falls back to the name itself so a typo is visible, not a crash.
     */
    @SuppressLint("DiscouragedApi")
    public static String text(Context context, String key, Object... formatArgs) {
        int id = context.getResources().getIdentifier(key, "string", context.getPackageName());
        if (id == 0) {
            Log.w(TAG, "Missing string resource: " + key);
            return key;
        }
        return context.getString(id, formatArgs);
    }
}
