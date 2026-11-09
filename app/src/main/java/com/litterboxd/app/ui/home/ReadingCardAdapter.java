package com.litterboxd.app.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.card.MaterialCardView;
import com.litterboxd.app.R;
import com.litterboxd.app.health.ReadingCard;

import java.util.ArrayList;
import java.util.List;

/** Shows the Daily "Reading Corner" carousel items (item_reading_card) in a horizontal RecyclerView. */
final class ReadingCardAdapter extends RecyclerView.Adapter<ReadingCardAdapter.Holder> {

    interface OnCardClick {
        void onClick(ReadingCard card);
    }

    private final List<ReadingCard> cards = new ArrayList<>();
    private final OnCardClick listener;

    ReadingCardAdapter(@NonNull OnCardClick listener) {
        this.listener = listener;
    }

    /** Replaces every card: the old ones are removed and the new ones inserted. */
    void submit(@NonNull List<ReadingCard> newCards) {
        int removed = cards.size();
        cards.clear();
        if (removed > 0) notifyItemRangeRemoved(0, removed);
        cards.addAll(newCards);
        if (!cards.isEmpty()) notifyItemRangeInserted(0, cards.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reading_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ReadingCard card = cards.get(position);
        Context context = holder.itemView.getContext();

        holder.label.setText(card.isForYou()
                ? R.string.reading_label_for_you : R.string.reading_label_cat_care);
        holder.label.setTextColor(ContextCompat.getColor(context,
                card.isForYou() ? R.color.rug_burgundy : R.color.sage_primary));
        holder.title.setText(card.getTitle());
        holder.meta.setText(card.getMinutes() > 0
                ? context.getString(R.string.reading_meta_minutes, card.getSource(), card.getMinutes())
                : card.getSource());
        holder.thumb.setText(card.getEmoji());
        holder.thumbCard.setCardBackgroundColor(ContextCompat.getColor(context, tintColour(card.getTint())));
        // Teaser image over the emoji tile. Glide downsizes it to the 84dp tile, caches it on disk
        // (so it isn't re-downloaded on every visit) and cancels the load if this row is recycled.
        // On failure the image stays empty and the emoji tile underneath shows through.
        if (card.hasImage()) {
            Glide.with(holder.image)
                    .load(card.getImageUrl())
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.image);
        } else {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(null);
        }
        holder.itemView.setContentDescription(context.getString(R.string.cd_reading_card,
                holder.label.getText(), card.getTitle(), card.getSource()));
        holder.itemView.setOnClickListener(v -> listener.onClick(card));
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    private static int tintColour(String tint) {
        if ("sage".equals(tint)) return R.color.card_sage;
        if ("clay".equals(tint)) return R.color.card_clay;
        if ("blush".equals(tint)) return R.color.blush_pink;
        return R.color.card_oat;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView label, title, meta, thumb;
        final ImageView image;
        final MaterialCardView thumbCard;

        Holder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.tvReadingLabel);
            title = itemView.findViewById(R.id.tvReadingTitle);
            meta = itemView.findViewById(R.id.tvReadingMeta);
            thumb = itemView.findViewById(R.id.tvReadingThumb);
            thumbCard = itemView.findViewById(R.id.cardReadingThumb);
            image = itemView.findViewById(R.id.imgReadingThumb);
        }
    }
}
