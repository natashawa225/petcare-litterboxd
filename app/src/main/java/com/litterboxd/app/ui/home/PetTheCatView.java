package com.litterboxd.app.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Transparent layer over the Daily header video that turns touches into "petting".
 *
 * Touch (lecture: Touch Events & Multi-Touch):
 *  - onTouchEvent() returns true on ACTION_DOWN so we keep receiving MOVE / UP.
 *  - getActionMasked() + getActionIndex() + getPointerId() track every finger by its ID
 *    in a SparseArray, so the second finger (ACTION_POINTER_DOWN) is handled too.
 *  - One finger on a cat, then lift  -> that cat's clip.
 *  - Two fingers, one on each cat    -> the "both cats" clip.
 *
 * Graphics: hearts float up from each finger and fade out. Touch only adds hearts; a
 * background thread moves them and calls postInvalidate() about every 16 ms (~60 FPS);
 * onDraw() just draws the current state.
 */
public class PetTheCatView extends View {

    public static final int CAT_LEFT = 0;
    public static final int CAT_RIGHT = 1;
    public static final int CAT_BOTH = 2;

    /** In the clips the grey cat fills the left ~40% of the frame and the ginger cat the rest. */
    private static final float SPLIT_FRACTION = 0.45f;
    private static final int FRAME_MS = 16;
    private static final int MAX_HEARTS = 60;
    private static final int[] HEART_COLOURS = {
            Color.parseColor("#F28CA6"), Color.parseColor("#F6B1C3"), Color.parseColor("#F2A087")
    };

    public interface OnPetListener {
        /** Called on the main thread with CAT_LEFT, CAT_RIGHT or CAT_BOTH. */
        void onPet(int which);
    }

    /** One floating heart. Fields are only touched while holding the {@link #hearts} lock. */
    private static final class Heart {
        float x, y, vx, vy, size, life;
        int colour;
    }

    private final List<Heart> hearts = new ArrayList<>();
    /** Fingers currently down, keyed by pointer ID (stable), not index (can change). */
    private final SparseArray<PointF> pointers = new SparseArray<>();
    /** Where each finger last dropped a heart, so stroking leaves an even trail. */
    private final SparseArray<PointF> lastSpawn = new SparseArray<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path heartPath = new Path();
    private final Random random = new Random();
    private final float density;

    private OnPetListener listener;
    private Thread animator;
    /** True once a second finger joined this gesture, so lifting does not also fire a single pet. */
    private boolean twoFingerGesture;
    private int pendingCat = CAT_BOTH;

    public PetTheCatView(Context context) {
        this(context, null);
    }

    public PetTheCatView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        buildUnitHeart();
        setClickable(true);
    }

    public void setOnPetListener(@Nullable OnPetListener listener) {
        this.listener = listener;
    }

    // ---- Touch ----

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int id = event.getPointerId(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                // First finger. Keep the Daily ScrollView from stealing the stroke.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                pointers.clear();
                lastSpawn.clear();
                twoFingerGesture = false;
                addPointer(id, event.getX(index), event.getY(index));
                return true; // must consume DOWN or we never see MOVE / UP
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                addPointer(id, event.getX(index), event.getY(index));
                if (!twoFingerGesture && pointers.size() >= 2) {
                    twoFingerGesture = true;
                    notifyPet(touchesBothCats() ? CAT_BOTH : catAt(event.getX(index)));
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                // MOVE carries every finger; look each one up by ID.
                for (int i = 0; i < event.getPointerCount(); i++) {
                    PointF point = pointers.get(event.getPointerId(i));
                    if (point == null) continue;
                    point.set(event.getX(i), event.getY(i));
                    trailHeart(event.getPointerId(i), point);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                pointers.remove(id);
                lastSpawn.remove(id);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                boolean single = !twoFingerGesture;
                float upX = event.getX(index);
                pointers.clear();
                lastSpawn.clear();
                if (single) {
                    pendingCat = catAt(upX);
                    performClick(); // a one-finger pet counts as a click (accessibility)
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                pointers.clear();
                lastSpawn.clear();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        notifyPet(pendingCat);
        pendingCat = CAT_BOTH; // TalkBack's double-tap (no position) pets both cats
        return true;
    }

    private void addPointer(int id, float x, float y) {
        pointers.put(id, new PointF(x, y));
        lastSpawn.put(id, new PointF(x, y));
        for (int i = 0; i < 3; i++) spawnHeart(x, y);
    }

    private void trailHeart(int id, PointF point) {
        PointF last = lastSpawn.get(id);
        if (last == null) return;
        float dx = point.x - last.x;
        float dy = point.y - last.y;
        float step = 24 * density;
        if (dx * dx + dy * dy >= step * step) {
            spawnHeart(point.x, point.y);
            last.set(point.x, point.y);
        }
    }

    private boolean touchesBothCats() {
        boolean left = false;
        boolean right = false;
        for (int i = 0; i < pointers.size(); i++) {
            if (catAt(pointers.valueAt(i).x) == CAT_LEFT) left = true;
            else right = true;
        }
        return left && right;
    }

    private int catAt(float x) {
        return x < getWidth() * SPLIT_FRACTION ? CAT_LEFT : CAT_RIGHT;
    }

    private void notifyPet(int which) {
        if (listener != null) listener.onPet(which);
    }

    // ---- Hearts: touch adds them, a background thread moves them, onDraw draws them ----

    private void spawnHeart(float x, float y) {
        Heart heart = new Heart();
        heart.x = x;
        heart.y = y;
        heart.vx = (random.nextFloat() - 0.5f) * 0.6f * density;  // gentle sideways drift
        heart.vy = -(0.8f + random.nextFloat() * 0.8f) * density; // float upwards
        heart.size = (10 + random.nextFloat() * 8) * density;
        heart.life = 1f;
        heart.colour = HEART_COLOURS[random.nextInt(HEART_COLOURS.length)];
        synchronized (hearts) {
            if (hearts.size() >= MAX_HEARTS) hearts.remove(0);
            hearts.add(heart);
        }
        startAnimator();
    }

    /** A finished Thread can't be restarted, so a new one is created whenever hearts appear. */
    private void startAnimator() {
        if (animator != null && animator.isAlive()) return;
        animator = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    boolean anyLeft = stepHearts();
                    postInvalidate();
                    if (!anyLeft) return; // nothing to animate: let the thread finish
                    Thread.sleep(FRAME_MS);
                }
            } catch (InterruptedException ignored) {
                // stopAnimation() interrupts the sleep
            }
        }, "PetTheCatHearts");
        animator.start();
    }

    /** Background thread. Moves and fades every heart; returns whether any are still visible. */
    private boolean stepHearts() {
        synchronized (hearts) {
            Iterator<Heart> it = hearts.iterator();
            while (it.hasNext()) {
                Heart heart = it.next();
                heart.x += heart.vx;
                heart.y += heart.vy;
                heart.life -= 0.018f; // about one second on screen
                if (heart.life <= 0f) it.remove();
            }
            return !hearts.isEmpty();
        }
    }

    /** Stops the heart thread and clears hearts (Daily calls this from onPause). */
    public void stopAnimation() {
        if (animator != null) {
            animator.interrupt();
            animator = null;
        }
        synchronized (hearts) {
            hearts.clear();
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        synchronized (hearts) {
            for (Heart heart : hearts) {
                paint.setColor(heart.colour);
                paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, heart.life))));
                float scale = heart.size * (0.7f + 0.3f * heart.life);
                canvas.save();
                canvas.translate(heart.x, heart.y);
                canvas.scale(scale, scale);
                canvas.drawPath(heartPath, paint);
                canvas.restore();
            }
        }
    }

    /** A heart one unit wide, centred on (0, 0), scaled per heart in onDraw. */
    private void buildUnitHeart() {
        heartPath.reset();
        heartPath.moveTo(0f, 0.35f);
        heartPath.cubicTo(-0.55f, -0.05f, -0.45f, -0.55f, 0f, -0.25f);
        heartPath.cubicTo(0.45f, -0.55f, 0.55f, -0.05f, 0f, 0.35f);
        heartPath.close();
    }
}
