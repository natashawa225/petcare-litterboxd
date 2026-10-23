package com.litterboxd.app.ui.auth;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;

import java.io.InputStream;

/**
 * WelcomeActivity: landing screen with full-screen cat wallpaper background,
 * interactive cat animation using TextureView with precise centerCrop matrix scaling
 * identical to ImageView.ScaleType.CENTER_CROP, static title header,
 * and a single "Get started" button.
 */
public class WelcomeActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private ImageView imgCatStatic;
    private TextureView textureViewCat;
    private ConstraintLayout rootWelcomeLayout;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private boolean pendingPlay = false;
    private Surface surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        imgCatStatic = findViewById(R.id.imgCatStatic);
        textureViewCat = findViewById(R.id.textureViewCat);
        rootWelcomeLayout = findViewById(R.id.rootWelcomeLayout);

        MaterialButton btnGetStarted = findViewById(R.id.btnGetStarted);

        // Load static cat wallpaper as default idle state filling the full screen
        loadStaticCatWallpaper();

        textureViewCat.setSurfaceTextureListener(this);

        View.OnClickListener animationTriggerListener = v -> playCatAnimation();

        imgCatStatic.setOnClickListener(animationTriggerListener);
        textureViewCat.setOnClickListener(animationTriggerListener);
        rootWelcomeLayout.setOnClickListener(animationTriggerListener);

        btnGetStarted.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, com.litterboxd.app.ui.onboarding.OwnerNameActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        surface = new Surface(surfaceTexture);
        initMediaPlayer();
        if (pendingPlay) {
            pendingPlay = false;
            playCatAnimation();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        if (mediaPlayer != null) {
            adjustVideoScaling(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
        resetToIdleState();
        releaseMediaPlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
        if (isPlaying) {
            imgCatStatic.setVisibility(View.INVISIBLE);
        }
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null || surface == null) return;
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.home_page_animation);
            if (mediaPlayer != null) {
                mediaPlayer.setSurface(surface);
                mediaPlayer.setLooping(false);
                mediaPlayer.setOnCompletionListener(mp -> resetToIdleState());
                mediaPlayer.setOnVideoSizeChangedListener((mp, w, h) -> adjustVideoScaling(w, h));
                mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && isPlaying) {
                        // First video frame rendered: seamlessly hide static image
                        imgCatStatic.setVisibility(View.GONE);
                        return true;
                    }
                    return false;
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e("LitterboxdVideo", "MediaPlayer error: what=" + what + ", extra=" + extra);
                    resetToIdleState();
                    releaseMediaPlayer();
                    return true;
                });
                adjustVideoScaling(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
            } else {
                Log.e("LitterboxdVideo", "MediaPlayer.create returned null for home_page_animation");
            }
        } catch (Exception e) {
            Log.e("LitterboxdVideo", "Failed to initialize MediaPlayer", e);
        }
    }

    private void adjustVideoScaling(int videoWidth, int videoHeight) {
        if (textureViewCat == null || videoWidth <= 0 || videoHeight <= 0) return;
        float viewWidth = textureViewCat.getWidth();
        float viewHeight = textureViewCat.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        float scaleX = viewWidth / (float) videoWidth;
        float scaleY = viewHeight / (float) videoHeight;
        float maxScale = Math.max(scaleX, scaleY);

        float actualScaleX = maxScale / scaleX;
        float actualScaleY = maxScale / scaleY;

        Matrix matrix = new Matrix();
        matrix.setScale(actualScaleX, actualScaleY, viewWidth / 2f, viewHeight / 2f);
        textureViewCat.setTransform(matrix);
    }

    private void loadStaticCatWallpaper() {
        try {
            InputStream is = getResources().openRawResource(R.raw.homepage_cat);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            imgCatStatic.setImageBitmap(bitmap);
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playCatAnimation() {
        if (isPlaying) return;
        if (mediaPlayer == null) {
            initMediaPlayer();
        }
        if (mediaPlayer == null) {
            pendingPlay = surface == null;
            return;
        }

        isPlaying = true;
        try {
            mediaPlayer.seekTo(0);
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e("LitterboxdVideo", "Failed to start video playback", e);
            resetToIdleState();
        }
    }

    private void resetToIdleState() {
        isPlaying = false;
        pendingPlay = false;
        if (imgCatStatic != null) {
            imgCatStatic.setVisibility(View.VISIBLE);
        }
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
                mediaPlayer.seekTo(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resetToIdleState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
    }
}
