package com.litterboxd.app.ui.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.litterboxd.app.R;
import com.litterboxd.app.data.model.WeightLog;
import com.litterboxd.app.util.DateFormatUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Custom Canvas chart for weight history:
 * - Line with soft area fill in sage theme colours
 * - Older to newer (left to right)
 * - Emphasized latest point
 * - Light horizontal gridlines with kg labels
 * - First and last date labels on x-axis
 * - Tap / drag interaction with value pill (date + kg)
 * - Period filtering (7d, 30d, All)
 */
public class WeightChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Value pill
    private final Paint pillBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();

    private final Path linePath = new Path();
    private final Path areaPath = new Path();

    private final List<WeightLog> allLogs = new ArrayList<>();
    private final List<WeightLog> displayedPoints = new ArrayList<>();

    private int periodDays = -1; // -1 = All
    private LocalDate asOfDate = LocalDate.now();

    private float[] pointX = new float[0];
    private float[] pointY = new float[0];
    private int selectedIndex = -1;

    private static final DateTimeFormatter SHORT_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());

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

        // Soft sage area fill (20% alpha)
        int sageColor = ContextCompat.getColor(getContext(), R.color.card_sage);
        areaPaint.setColor(sageColor);
        areaPaint.setStyle(Paint.Style.FILL);
        areaPaint.setAlpha(120);

        pointPaint.setColor(ContextCompat.getColor(getContext(), R.color.coral_secondary));
        pointPaint.setStyle(Paint.Style.FILL);

        latestPointPaint.setColor(ContextCompat.getColor(getContext(), R.color.coral_secondary));
        latestPointPaint.setStyle(Paint.Style.FILL);

        latestRingPaint.setColor(ContextCompat.getColor(getContext(), R.color.ink));
        latestRingPaint.setStyle(Paint.Style.STROKE);
        latestRingPaint.setStrokeWidth(2f * density);

        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.surface_card));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);

        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dark_gray));
        labelPaint.setTextSize(11f * density);

        pillBgPaint.setColor(ContextCompat.getColor(getContext(), R.color.white));
        pillBgPaint.setStyle(Paint.Style.FILL);

        pillStrokePaint.setColor(ContextCompat.getColor(getContext(), R.color.ink));
        pillStrokePaint.setStyle(Paint.Style.STROKE);
        pillStrokePaint.setStrokeWidth(1.5f * density);

        pillTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.ink));
        pillTextPaint.setTextSize(11f * density);
        pillTextPaint.setFakeBoldText(true);
        pillTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Pass weight logs in any order; the chart stores and sorts oldest → newest. */
    public void setWeightLogs(List<WeightLog> logs) {
        allLogs.clear();
        if (logs != null) {
            allLogs.addAll(logs);
            allLogs.sort(Comparator.comparingLong(WeightLog::getDate));
        }
        updateDisplayedPoints();
    }

    /**
     * Filters displayed points by period: 7, 30, or -1 (All).
     */
    public void setPeriod(int days, LocalDate today) {
        this.periodDays = days;
        if (today != null) {
            this.asOfDate = today;
        }
        updateDisplayedPoints();
    }

    private void updateDisplayedPoints() {
        displayedPoints.clear();
        if (allLogs.isEmpty()) {
            setVisibility(GONE);
            invalidate();
            return;
        }

        if (periodDays > 0) {
            LocalDate cutoff = asOfDate.minusDays(periodDays);
            for (WeightLog log : allLogs) {
                LocalDate logDate = Instant.ofEpochMilli(log.getDate())
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                if (!logDate.isBefore(cutoff) && !logDate.isAfter(asOfDate)) {
                    displayedPoints.add(log);
                }
            }
            // If the filtered window has fewer than 2 logs, show at least the recent logs
            // so the owner doesn't see a blank chart when switching toggles
            if (displayedPoints.size() < 2) {
                displayedPoints.clear();
                displayedPoints.addAll(allLogs);
            }
        } else {
            displayedPoints.addAll(allLogs);
        }

        if (selectedIndex >= displayedPoints.size()) {
            selectedIndex = -1;
        }

        setVisibility(displayedPoints.size() >= 2 ? VISIBLE : GONE);
        if (displayedPoints.size() >= 2) {
            WeightLog newest = displayedPoints.get(displayedPoints.size() - 1);
            setContentDescription(getContext().getString(R.string.cd_reading_card,
                    "Weight chart",
                    String.format(Locale.getDefault(), "%.1f kg", newest.getWeight()),
                    String.valueOf(displayedPoints.size())));
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (displayedPoints.size() < 2 || pointX.length != displayedPoints.size()) {
            return super.onTouchEvent(event);
        }

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                float touchX = event.getX();
                int nearest = 0;
                float minDistance = Float.MAX_VALUE;
                for (int i = 0; i < pointX.length; i++) {
                    float dist = Math.abs(pointX[i] - touchX);
                    if (dist < minDistance) {
                        minDistance = dist;
                        nearest = i;
                    }
                }
                if (nearest != selectedIndex) {
                    selectedIndex = nearest;
                    invalidate();
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (displayedPoints.size() < 2) return;

        float density = getResources().getDisplayMetrics().density;
        float padL = getPaddingLeft() + labelPaint.measureText("00.00") + 12f * density;
        float padR = getPaddingRight() + 16f * density;
        float padT = getPaddingTop() + 26f * density; // room for value pill
        float padB = getPaddingBottom() + 22f * density; // room for x-axis date labels
        float w = getWidth() - padL - padR;
        float h = getHeight() - padT - padB;
        if (w <= 0 || h <= 0) return;

        double min = displayedPoints.get(0).getWeight();
        double max = min;
        for (WeightLog log : displayedPoints) {
            min = Math.min(min, log.getWeight());
            max = Math.max(max, log.getWeight());
        }
        if (max - min < 0.2) {
            min -= 0.1;
            max += 0.1;
        }

        // 3 horizontal guide lines (max, mid, min)
        for (int i = 0; i < 3; i++) {
            float y = padT + h * i / 2f;
            canvas.drawLine(padL, y, padL + w, y, gridPaint);
            double val = max - (max - min) * (i / 2.0);
            String label = formatKg(val);
            canvas.drawText(label, getPaddingLeft(), y + labelPaint.getTextSize() / 3f, labelPaint);
        }

        // Compute points coordinates
        int count = displayedPoints.size();
        if (pointX.length != count) {
            pointX = new float[count];
            pointY = new float[count];
        }
        for (int i = 0; i < count; i++) {
            pointX[i] = padL + (count == 1 ? w / 2f : w * i / (count - 1f));
            pointY[i] = padT + h * (float) ((max - displayedPoints.get(i).getWeight()) / (max - min));
        }

        // Soft area fill
        areaPath.reset();
        areaPath.moveTo(pointX[0], padT + h);
        for (int i = 0; i < count; i++) {
            areaPath.lineTo(pointX[i], pointY[i]);
        }
        areaPath.lineTo(pointX[count - 1], padT + h);
        areaPath.close();
        canvas.drawPath(areaPath, areaPaint);

        // Main line
        linePath.reset();
        linePath.moveTo(pointX[0], pointY[0]);
        for (int i = 1; i < count; i++) {
            linePath.lineTo(pointX[i], pointY[i]);
        }
        canvas.drawPath(linePath, linePaint);

        // Date labels on x-axis (first and last)
        String firstDate = formatDateShort(displayedPoints.get(0).getDate());
        String lastDate = formatDateShort(displayedPoints.get(count - 1).getDate());
        float dateY = padT + h + labelPaint.getTextSize() + 4f * density;
        canvas.drawText(firstDate, padL, dateY, labelPaint);
        float lastDateWidth = labelPaint.measureText(lastDate);
        canvas.drawText(lastDate, padL + w - lastDateWidth, dateY, labelPaint);

        // Dots: normal dots for all but latest, emphasized latest point
        float normalRadius = 4f * density;
        float latestRadius = 6.5f * density;
        for (int i = 0; i < count; i++) {
            if (i == count - 1) {
                // Emphasized latest point
                canvas.drawCircle(pointX[i], pointY[i], latestRadius, latestPointPaint);
                canvas.drawCircle(pointX[i], pointY[i], latestRadius, latestRingPaint);
            } else {
                canvas.drawCircle(pointX[i], pointY[i], normalRadius, pointPaint);
            }
        }

        // Selected value pill (date + kg)
        if (selectedIndex >= 0 && selectedIndex < count) {
            WeightLog selected = displayedPoints.get(selectedIndex);
            String dateStr = formatDateShort(selected.getDate());
            String pillText = formatKg(selected.getWeight()) + " kg · " + dateStr;

            float pillW = pillTextPaint.measureText(pillText) + 20f * density;
            float pillH = 24f * density;
            float pillCenterX = pointX[selectedIndex];

            // Keep pill inside chart horizontal bounds
            float pillLeft = pillCenterX - pillW / 2f;
            if (pillLeft < getPaddingLeft()) {
                pillLeft = getPaddingLeft();
            } else if (pillLeft + pillW > getWidth() - getPaddingRight()) {
                pillLeft = getWidth() - getPaddingRight() - pillW;
            }
            float pillRight = pillLeft + pillW;

            // Place above the point, or below if too close to top
            float pillTop = pointY[selectedIndex] - pillH - 8f * density;
            if (pillTop < getPaddingTop()) {
                pillTop = pointY[selectedIndex] + 12f * density;
            }
            float pillBottom = pillTop + pillH;

            pillRect.set(pillLeft, pillTop, pillRight, pillBottom);
            canvas.drawRoundRect(pillRect, 12f * density, 12f * density, pillBgPaint);
            canvas.drawRoundRect(pillRect, 12f * density, 12f * density, pillStrokePaint);

            float textY = pillTop + (pillH / 2f) + (pillTextPaint.getTextSize() / 3f);
            canvas.drawText(pillText, (pillLeft + pillRight) / 2f, textY, pillTextPaint);
        }
    }

    /** "6.65" or "6.5": two decimals at most, so close weigh-ins and guide lines stay distinct. */
    static String formatKg(double kg) {
        String s = String.format(Locale.US, "%.2f", kg);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String formatDateShort(long epochMillis) {
        try {
            LocalDate date = Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            return date.format(SHORT_DATE_FMT);
        } catch (Exception e) {
            return DateFormatUtils.formatDate(epochMillis);
        }
    }
}
