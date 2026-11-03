package com.litterboxd.app.ui.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.litterboxd.app.R;
import com.litterboxd.app.data.model.WeightLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Simple sage line chart for weight history (oldest → newest left to right). */
public class WeightChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();

    private final List<WeightLog> points = new ArrayList<>();

    public WeightChartView(Context context) {
        super(context);
        init();
    }

    public WeightChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WeightChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.sage_primary));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setColor(ContextCompat.getColor(getContext(), R.color.coral_secondary));
        pointPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.surface_card));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);

        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dark_gray));
        labelPaint.setTextSize(11f * density);
    }

    /** Pass weight logs in any order; the chart sorts oldest → newest. */
    public void setWeightLogs(List<WeightLog> logs) {
        points.clear();
        if (logs != null) {
            points.addAll(logs);
            Collections.sort(points, Comparator.comparingLong(WeightLog::getDate));
        }
        setVisibility(points.size() >= 2 ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.size() < 2) return;

        float padL = getPaddingLeft() + labelPaint.measureText("00.0") + 8f;
        float padR = getPaddingRight() + 12f;
        float padT = getPaddingTop() + 12f;
        float padB = getPaddingBottom() + 12f;
        float w = getWidth() - padL - padR;
        float h = getHeight() - padT - padB;
        if (w <= 0 || h <= 0) return;

        double min = points.get(0).getWeight();
        double max = min;
        for (WeightLog log : points) {
            min = Math.min(min, log.getWeight());
            max = Math.max(max, log.getWeight());
        }
        if (max - min < 0.2) {
            min -= 0.1;
            max += 0.1;
        }

        // Light horizontal guides.
        for (int i = 0; i < 3; i++) {
            float y = padT + h * i / 2f;
            canvas.drawLine(padL, y, padL + w, y, gridPaint);
        }

        String minLabel = String.format(java.util.Locale.US, "%.1f", min);
        String maxLabel = String.format(java.util.Locale.US, "%.1f", max);
        canvas.drawText(maxLabel, getPaddingLeft(), padT + labelPaint.getTextSize(), labelPaint);
        canvas.drawText(minLabel, getPaddingLeft(), padT + h, labelPaint);

        linePath.reset();
        float radius = 4f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < points.size(); i++) {
            float x = padL + (points.size() == 1 ? w / 2f : w * i / (points.size() - 1f));
            float y = padT + h * (float) ((max - points.get(i).getWeight()) / (max - min));
            if (i == 0) linePath.moveTo(x, y);
            else linePath.lineTo(x, y);
        }
        canvas.drawPath(linePath, linePaint);
        for (int i = 0; i < points.size(); i++) {
            float x = padL + (points.size() == 1 ? w / 2f : w * i / (points.size() - 1f));
            float y = padT + h * (float) ((max - points.get(i).getWeight()) / (max - min));
            canvas.drawCircle(x, y, radius, pointPaint);
        }
    }
}
