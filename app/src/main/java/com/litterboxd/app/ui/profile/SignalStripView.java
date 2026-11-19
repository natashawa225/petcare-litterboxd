package com.litterboxd.app.ui.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.litterboxd.app.R;
import com.litterboxd.app.health.DayState;

import java.util.Arrays;

/**
 * Custom View that renders daily health states for a signal:
 * - one rounded bar per day (7 or 30), centred in equal slots
 * - Solid fills only: pale sage for normal, clay for a bit off, red for a concern
 */
public class SignalStripView extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private DayState[] days = new DayState[0];

    private int colorNormal;
    private int colorOff;
    private int colorConcern;
    private int colorNotLogged;

    public SignalStripView(Context context) {
        super(context);
        init();
    }

    public SignalStripView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SignalStripView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context c = getContext();
        colorNormal = ContextCompat.getColor(c, R.color.card_sage);

        colorOff = ContextCompat.getColor(c, R.color.muted_pinkish_brown);

        colorConcern = ContextCompat.getColor(c, R.color.error_red);

        colorNotLogged = ContextCompat.getColor(c, R.color.surface_card);

        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void setDays(DayState[] dayStates) {
        if (dayStates == null) {
            this.days = new DayState[0];
        } else {
            this.days = Arrays.copyOf(dayStates, dayStates.length);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int defaultW = (int) (110f * density);
        int defaultH = (int) (24f * density);

        int w = resolveSize(defaultW, widthMeasureSpec);
        int h = resolveSize(defaultH, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (days == null || days.length == 0) return;

        int count = days.length;
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float cy = getPaddingTop() + h / 2f;

        // Same rounded bars for 7 and 30 days: each day gets an equal slot and its bar is
        // centred in it, so nothing touches (or is clipped at) the edges.
        float step = w / (float) count;
        float gap = (count <= 7 ? 6f : 1.5f) * density;
        float barW = Math.max(2.5f * density, Math.min(step - gap, 28f * density));
        float barH = Math.min(h - 4f * density, 16f * density);
        for (int i = 0; i < count; i++) {
            float cx = getPaddingLeft() + (i * step) + (step / 2f);
            float left = cx - barW / 2f;
            float top = cy - barH / 2f;
            barRect.set(left, top, left + barW, top + barH);
            drawIndicatorBar(canvas, barRect, days[i], Math.min(barW, barH) / 2f);
        }
    }

    private void drawIndicatorBar(Canvas canvas, RectF rect, DayState state, float cornerRadius) {
        fillPaint.setColor(colourFor(state));
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint);
    }

    /**
     * Fill only, no outlines: normal days are a quiet pale sage so the strip reads calm, and the
     * stronger clay / red days are the only things that catch the eye.
     */
    private int colourFor(DayState state) {
        switch (state) {
            case NORMAL: return colorNormal;
            case OFF: return colorOff;
            case CONCERN: return colorConcern;
            case NOT_LOGGED:
            default: return colorNotLogged;
        }
    }
}
