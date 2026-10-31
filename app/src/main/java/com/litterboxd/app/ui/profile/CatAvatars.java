package com.litterboxd.app.ui.profile;

import android.content.Context;

import com.litterboxd.app.R;
import com.litterboxd.app.data.session.AppPrefs;

public final class CatAvatars {

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

    private CatAvatars() {}

    public static String bundledValue(Context context, int drawableRes) {
        return AppPrefs.AVATAR_BUNDLED_PREFIX + context.getResources().getResourceEntryName(drawableRes);
    }

    public static String fileValue(String fileName) {
        return AppPrefs.AVATAR_FILE_PREFIX + fileName;
    }

    public static int bundledIndex(Context context, String value) {
        if (value == null || !value.startsWith(AppPrefs.AVATAR_BUNDLED_PREFIX)) return -1;
        String name = value.substring(AppPrefs.AVATAR_BUNDLED_PREFIX.length());
        for (int i = 0; i < BUNDLED.length; i++) {
            if (context.getResources().getResourceEntryName(BUNDLED[i]).equals(name)) return i;
        }
        return -1;
    }

    public static String fileName(String value) {
        if (value == null || !value.startsWith(AppPrefs.AVATAR_FILE_PREFIX)) return null;
        return value.substring(AppPrefs.AVATAR_FILE_PREFIX.length());
    }
}
