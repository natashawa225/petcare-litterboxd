package com.litterboxd.app.ui.profile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.litterboxd.app.R;
import com.litterboxd.app.health.RecapResult;
import com.litterboxd.app.health.SignalSummary;

import java.util.List;

/**
 * Controller/binder for the Health Recap card in {@link CatProfileActivity}.
 * Binds the 7d/30d toggle, highlight lines, 5 signal rows, and logging consistency line.
 */
public class HealthRecapCardView {

    public interface OnSignalClickListener {
        void onSignalClick(String signalId, String signalLabel);
    }

    public interface OnAlertClickListener {
        void onAlertClick(String ruleId);
    }

    private final View cardView;
    private final MaterialButtonToggleGroup toggleRecapPeriod;
    private final View containerEmpty;
    private final TextView tvEmptyTitle;
    private final TextView tvEmptyMessage;
    private final View containerContent;
    private final LinearLayout containerHighlights;
    private final LinearLayout containerSignalRows;
    private final TextView tvLoggingLine;

    public HealthRecapCardView(View rootView) {
        this.cardView = rootView.findViewById(R.id.cardHealthRecap);
        this.toggleRecapPeriod = rootView.findViewById(R.id.toggleRecapPeriod);
        this.containerEmpty = rootView.findViewById(R.id.containerRecapEmpty);
        this.tvEmptyTitle = rootView.findViewById(R.id.tvRecapEmptyTitle);
        this.tvEmptyMessage = rootView.findViewById(R.id.tvRecapEmptyMessage);
        this.containerContent = rootView.findViewById(R.id.containerRecapContent);
        this.containerHighlights = rootView.findViewById(R.id.containerRecapHighlights);
        this.containerSignalRows = rootView.findViewById(R.id.containerSignalRows);
        this.tvLoggingLine = rootView.findViewById(R.id.tvRecapLoggingLine);
    }

    public MaterialButtonToggleGroup getToggleGroup() {
        return toggleRecapPeriod;
    }

    public void bind(RecapResult recap,
                     boolean isKitten,
                     OnSignalClickListener signalClickListener,
                     OnAlertClickListener alertClickListener) {
        if (recap == null) {
            cardView.setVisibility(View.GONE);
            return;
        }

        cardView.setVisibility(View.VISIBLE);
        Context context = cardView.getContext();

        if (recap.isNotEnoughData()) {
            tvLoggingLine.setVisibility(View.GONE);
            containerEmpty.setVisibility(View.VISIBLE);
            containerContent.setVisibility(View.GONE);

            String catName = recap.getCatName() != null ? recap.getCatName()
                    : context.getString(R.string.recap_your_cat);
            tvEmptyTitle.setText(context.getString(R.string.recap_empty_title, catName));
            tvEmptyMessage.setText(context.getString(R.string.recap_empty_message, recap.getDaysLogged()));
            return;
        }

        containerEmpty.setVisibility(View.GONE);
        containerContent.setVisibility(View.VISIBLE);
        tvLoggingLine.setVisibility(View.VISIBLE);

        // Bind Highlights
        bindHighlights(context, recap.getHighlights(), alertClickListener);

        // Bind 5 Signal Rows
        bindSignalRows(context, recap.getSignals(), signalClickListener);

        // Bind Logging Line
        if (recap.getCurrentStreak() > 0) {
            tvLoggingLine.setText(context.getString(R.string.recap_logged_line,
                    recap.getDaysLogged(), recap.getDaysInPeriod(), recap.getCurrentStreak()));
        } else {
            tvLoggingLine.setText(context.getString(R.string.recap_logged_line_no_streak,
                    recap.getDaysLogged(), recap.getDaysInPeriod()));
        }
    }

    private void bindHighlights(Context context,
                                List<RecapResult.Highlight> highlights,
                                OnAlertClickListener alertClickListener) {
        containerHighlights.removeAllViews();
        if (highlights == null || highlights.isEmpty()) {
            containerHighlights.setVisibility(View.GONE);
            return;
        }
        containerHighlights.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(context);

        // Only the most notable highlight: the strips below already show the rest at a glance.
        for (RecapResult.Highlight h : highlights.subList(0, 1)) {
            View row = inflater.inflate(R.layout.item_recap_highlight, containerHighlights, false);
            ImageView icon = row.findViewById(R.id.imgHighlightIcon);
            TextView text = row.findViewById(R.id.tvHighlight);

            int iconRes;
            int tint;
            int fill;
            if ("alert".equals(h.getIconType())) {
                iconRes = R.drawable.ic_alert_circle;
                tint = R.color.error_red;
                fill = R.color.pastel_coral;
            } else if ("trend_up".equals(h.getIconType())) {
                iconRes = R.drawable.ic_info_circle;
                tint = R.color.muted_pinkish_brown;
                fill = R.color.card_clay;
            } else {
                iconRes = R.drawable.ic_check_circle;
                tint = R.color.sage_primary;
                fill = R.color.card_sage;
            }
            icon.setImageResource(iconRes);
            icon.setColorFilter(ContextCompat.getColor(context, tint));
            row.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, fill)));
            text.setText(h.getText());

            if (h.getAlertRuleId() != null && alertClickListener != null) {
                row.setClickable(true);
                row.setFocusable(true);
                row.setOnClickListener(v -> alertClickListener.onAlertClick(h.getAlertRuleId()));
            }
            containerHighlights.addView(row);
        }
    }

    private void bindSignalRows(Context context,
                                List<SignalSummary> summaries,
                                OnSignalClickListener listener) {
        containerSignalRows.removeAllViews();
        if (summaries == null) return;
        LayoutInflater inflater = LayoutInflater.from(context);

        for (SignalSummary s : summaries) {
            String label = getSignalLabel(context, s.getSignalId());
            View row = inflater.inflate(R.layout.item_recap_signal_row, containerSignalRows, false);

            ((TextView) row.findViewById(R.id.tvSignalLabel)).setText(label);
            ((SignalStripView) row.findViewById(R.id.stripSignal)).setDays(s.getDays());
            ((TextView) row.findViewById(R.id.tvSignalRatio)).setText(context.getString(
                    R.string.recap_normal_ratio, s.getNormalCount(), s.getLoggedCount()));

            int trendDesc;
            if (s.getComparison() == SignalSummary.Comparison.MORE_OFF) {
                trendDesc = R.string.recap_trend_cd_more_off;
            } else if (s.getComparison() == SignalSummary.Comparison.STEADIER) {
                trendDesc = R.string.recap_trend_cd_steadier;
            } else {
                trendDesc = R.string.recap_trend_cd_same;
            }

            row.setContentDescription(context.getString(R.string.cd_signal_row,
                    label, s.getNormalCount(), s.getLoggedCount(), context.getString(trendDesc)));
            row.setOnClickListener(v -> {
                if (listener != null) listener.onSignalClick(s.getSignalId(), label);
            });
            containerSignalRows.addView(row);
        }
    }

    private String getSignalLabel(Context context, String signalId) {
        if ("appetite".equals(signalId)) return context.getString(R.string.recap_signal_appetite);
        if ("water".equals(signalId)) return context.getString(R.string.recap_signal_water);
        if ("litter".equals(signalId)) return context.getString(R.string.recap_signal_litter);
        if ("energy".equals(signalId)) return context.getString(R.string.recap_signal_energy);
        if ("mood".equals(signalId)) return context.getString(R.string.recap_signal_mood);
        return Character.toUpperCase(signalId.charAt(0)) + signalId.substring(1);
    }
}
