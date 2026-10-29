package com.litterboxd.app.ui.profile;

import android.content.Context;
import android.widget.ImageView;

import com.litterboxd.app.R;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.session.AppPrefs;

/**
 * The owner's avatar (never a cat's photo). It is stored in AppPrefs as one string:
 *  - "bundled:avatar_ginger_cat": one of the illustrated avatars in res/drawable-nodpi, saved by
 *    resource NAME because generated resource ids can change between builds;
 *  - "file:owner_avatar_….jpg": a downscaled photo in the app's private files (FileStorageHelper);
 *  - nothing: the default person avatar.
 */
public final class OwnerAvatars {

    /** The bundled avatars, in display order, with their accessible names. */
    public static final int[] BUNDLED = {
            R.drawable.avatar_cream_cat,
            R.drawable.avatar_ginger_cat,
            R.drawable.avatar_calico_cat,
            R.drawable.avatar_tuxedo_cat,
    };
    public static final int[] BUNDLED_NAMES = {
            R.string.avatar_cream_cat,
            R.string.avatar_ginger_cat,
            R.string.avatar_calico_cat,
            R.string.avatar_tuxedo_cat,
    };

    private OwnerAvatars() {}

    public static String bundledValue(Context context, int drawableRes) {
        return AppPrefs.AVATAR_BUNDLED_PREFIX + context.getResources().getResourceEntryName(drawableRes);
    }

    public static String fileValue(String fileName) {
        return AppPrefs.AVATAR_FILE_PREFIX + fileName;
    }

    /** Index into BUNDLED for a stored value, or -1 if it isn't a (known) bundled avatar. */
    public static int bundledIndex(Context context, String value) {
        if (value == null || !value.startsWith(AppPrefs.AVATAR_BUNDLED_PREFIX)) return -1;
        String name = value.substring(AppPrefs.AVATAR_BUNDLED_PREFIX.length());
        for (int i = 0; i < BUNDLED.length; i++) {
            if (context.getResources().getResourceEntryName(BUNDLED[i]).equals(name)) return i;
        }
        return -1;
    }

    /** The private file name of a custom photo value, or null. */
    public static String fileName(String value) {
        if (value == null || !value.startsWith(AppPrefs.AVATAR_FILE_PREFIX)) return null;
        return value.substring(AppPrefs.AVATAR_FILE_PREFIX.length());
    }

    /**
     * Shows the avatar in {@code view}. A custom photo is decoded off the UI thread; if its file is
     * missing (or the bundled name is unknown) the default avatar is shown instead.
     */
    public static void show(Context context, ImageView view, String value) {
        int index = bundledIndex(context, value);
        String file = fileName(value);
        if (index >= 0) {
            view.setImageResource(BUNDLED[index]);
        } else if (file != null) {
            new FileStorageHelper(context).showImageFile(view, file, R.drawable.ic_default_owner_avatar);
        } else {
            view.setImageResource(R.drawable.ic_default_owner_avatar);
        }
    }
}
