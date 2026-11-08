package com.litterboxd.app.ui.home;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.litterboxd.app.R;
import com.litterboxd.app.data.file.FileStorageHelper;
import com.litterboxd.app.data.model.Cat;

import java.util.List;

/**
 * The Daily "My cats" row: one colourful card per cat and an "Add cat" card at the end, inside a
 * HorizontalScrollView so it swipes left and right once there are more cats than fit.
 *
 * Tapping another cat selects it; tapping the selected (lifted) cat opens its profile.
 */
final class CatCardRow {

    interface Listener {
        void onSelectCat(Cat cat);
        void onOpenProfile(Cat cat);
        void onAddCat();
    }

    /** Soft earthy card colours (oat, sage, clay, blush), used in turn; dark text on all. */
    @ColorRes private static final int[] CARD_COLOURS = {
            R.color.card_oat, R.color.card_sage, R.color.card_clay, R.color.blush_pink
    };

    private final Context context;
    private final HorizontalScrollView scroller;
    private final LinearLayout container;
    private final FileStorageHelper storage;
    private final float density;

    CatCardRow(@NonNull Context context, @NonNull HorizontalScrollView scroller,
               @NonNull LinearLayout container) {
        this.context = context;
        this.scroller = scroller;
        this.container = container;
        this.storage = new FileStorageHelper(context);
        this.density = context.getResources().getDisplayMetrics().density;
    }

    void bind(@NonNull List<Cat> cats, long selectedId, @NonNull Listener listener) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        View selectedCard = null;

        for (int i = 0; i < cats.size(); i++) {
            Cat cat = cats.get(i);
            boolean selected = cat.getId() == selectedId;
            @ColorRes int colour = CARD_COLOURS[i % CARD_COLOURS.length];

            View card = inflater.inflate(R.layout.item_cat_card, container, false);
            card.setBackground(cardBackground(ContextCompat.getColor(context, colour), selected));

            TextView name = card.findViewById(R.id.tvCatCardName);
            ImageView chevron = card.findViewById(R.id.imgCatCardChevron);
            int textColour = ContextCompat.getColor(context, R.color.black);
            name.setText(cat.getName());
            name.setTextColor(textColour);
            chevron.setVisibility(selected ? View.VISIBLE : View.GONE);
            ImageViewCompat.setImageTintList(chevron,
                    android.content.res.ColorStateList.valueOf(textColour));
            storage.showCatPhoto(card.findViewById(R.id.imgCatCardPhoto), cat.getPhotoPath());

            card.setContentDescription(context.getString(
                    selected ? R.string.cd_cat_card_selected : R.string.cd_cat_card, cat.getName()));
            card.setOnClickListener(v -> {
                if (selected) listener.onOpenProfile(cat);
                else listener.onSelectCat(cat);
            });
            container.addView(card);
            if (selected) selectedCard = card;
        }

        View add = inflater.inflate(R.layout.item_cat_card_add, container, false);
        add.setOnClickListener(v -> listener.onAddCat());
        container.addView(add);

        // Bring the selected cat into view (e.g. the 5th cat picked from another tab).
        final View target = selectedCard;
        if (target != null) {
            scroller.post(() -> {
                int left = target.getLeft() - (int) (16 * density);
                scroller.smoothScrollTo(Math.max(0, left), 0);
            });
        }
    }

    /**
     * Rounded card with an ink outline. The selected cat is "lifted": an ink shadow sits 4dp
     * down-right, the same look as the Add task button.
     */
    private Drawable cardBackground(int fill, boolean selected) {
        float radius = context.getResources().getDimension(R.dimen.cat_card_corner);
        int ink = ContextCompat.getColor(context, R.color.ink);
        int stroke = Math.round(2 * density);
        int lift = Math.round(4 * density);

        GradientDrawable face = new GradientDrawable();
        face.setColor(fill);
        face.setCornerRadius(radius);
        face.setStroke(stroke, ink);

        GradientDrawable shadow = new GradientDrawable();
        shadow.setColor(selected ? ink : android.graphics.Color.TRANSPARENT);
        shadow.setCornerRadius(radius);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{shadow, face});
        layers.setLayerInset(0, lift, lift, 0, 0);   // shadow: pushed down-right
        layers.setLayerInset(1, 0, 0, lift, lift);   // face: leaves room for the shadow
        return layers;
    }
}
