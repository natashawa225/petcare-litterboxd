package com.litterboxd.app.ui.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import com.litterboxd.app.R;
import com.litterboxd.app.util.AppExecutors;

/**
 * Plays the "pet the cat" clips in the Daily header.
 *
 * While idle the widget shows a still image (the clips' first frame) on top of the TextureView.
 * A pet starts the matching clip; the still is hidden only once the first video frame is on
 * screen, and comes back when the clip ends, so there is never a black flash. Clips are muted.
 */
final class PetTheCatPlayer implements TextureView.SurfaceTextureListener {

    private static final String TAG = "PetTheCat";
    /** All clips are 1280x720 and start from the same frame. */
    private static final int VIDEO_W = 1280;
    private static final int VIDEO_H = 720;
    /** Same first frame for every visit to Daily, so it is only decoded once. */
    private static Bitmap cachedStill;

    private final Context appContext;
    private final TextureView textureView;
    private final ImageView still;

    private Surface surface;
    private MediaPlayer player;
    @RawRes private int playingClip;

    PetTheCatPlayer(@NonNull Context context, @NonNull TextureView textureView, @NonNull ImageView still) {
        this.appContext = context.getApplicationContext();
        this.textureView = textureView;
        this.still = still;
        textureView.setSurfaceTextureListener(this);
        still.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitStill());
        loadStill();
    }

    /** Plays the clip for {@link PetTheCatView#CAT_LEFT}, CAT_RIGHT or CAT_BOTH. */
    void play(int which) {
        @RawRes int clip = which == PetTheCatView.CAT_LEFT ? R.raw.pet_left_cat
                : which == PetTheCatView.CAT_RIGHT ? R.raw.pet_right_cat
                : R.raw.pet_both_cats;
        if (surface == null) return;                  // view not ready yet
        if (player != null && clip == playingClip) return; // already playing this one
        stop();
        playingClip = clip;
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setDataSource(appContext, rawUri(clip));
            mp.setSurface(surface);
            mp.setVolume(0f, 0f); // the Daily tab should stay quiet
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnInfoListener((p, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && p == player) {
                    still.setVisibility(View.INVISIBLE);
                }
                return false;
            });
            mp.setOnCompletionListener(p -> {
                if (p == player) stop();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w(TAG, "Clip failed: what=" + what + " extra=" + extra);
                if (p == player) stop();
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "Could not play clip", e);
            stop();
        }
    }

    /** Back to the still image. Safe to call any time (Daily calls it from onPause). */
    void stop() {
        still.setVisibility(View.VISIBLE);
        playingClip = 0;
        if (player != null) {
            MediaPlayer old = player;
            player = null;
            try {
                old.release();
            } catch (RuntimeException ignored) {
                // already released
            }
        }
    }

    // ---- TextureView ----

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        fitVideo(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int width, int height) {
        fitVideo(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
        stop();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {
        // nothing to do per frame
    }

    // ---- Framing: fill the widget and keep the cats (bottom of the clip) in view ----

    private void fitVideo(int viewW, int viewH) {
        if (viewW == 0 || viewH == 0) return;
        float scale = Math.max((float) viewW / VIDEO_W, (float) viewH / VIDEO_H);
        // TextureView stretches the video to the view; undo that, then scale uniformly.
        Matrix m = new Matrix();
        m.setScale(scale * VIDEO_W / viewW, scale * VIDEO_H / viewH);
        m.postTranslate((viewW - VIDEO_W * scale) / 2f, viewH - VIDEO_H * scale);
        textureView.setTransform(m);
    }

    private void fitStill() {
        if (still.getDrawable() == null) return;
        int viewW = still.getWidth();
        int viewH = still.getHeight();
        int imgW = still.getDrawable().getIntrinsicWidth();
        int imgH = still.getDrawable().getIntrinsicHeight();
        if (viewW == 0 || viewH == 0 || imgW <= 0 || imgH <= 0) return;
        float scale = Math.max((float) viewW / imgW, (float) viewH / imgH);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate((viewW - imgW * scale) / 2f, viewH - imgH * scale);
        still.setScaleType(ImageView.ScaleType.MATRIX);
        still.setImageMatrix(m);
    }

    /** The placeholder image shows at once; the clips' real first frame replaces it when decoded. */
    private void loadStill() {
        if (cachedStill != null) {
            showStill(cachedStill);
            return;
        }
        AppExecutors.getInstance().diskIO().execute(() -> {
            Bitmap frame = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(appContext, rawUri(R.raw.pet_left_cat));
                frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not read the first frame", e);
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                    // nothing else to free
                }
            }
            if (frame == null) return;
            cachedStill = frame;
            final Bitmap result = frame;
            AppExecutors.getInstance().mainThread().execute(() -> showStill(result));
        });
    }

    private void showStill(Bitmap bitmap) {
        still.setImageBitmap(bitmap);
        fitStill();
    }

    private Uri rawUri(@RawRes int res) {
        return Uri.parse("android.resource://" + appContext.getPackageName() + "/" + res);
    }
}
