package com.litterboxd.app.ui.auth;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * CenterCropVideoView: a custom VideoView that scales and measures itself
 * to achieve centerCrop behavior, matching ImageView's scaleType="centerCrop".
 */
public class CenterCropVideoView extends VideoView {
    private int videoWidth = 0;
    private int videoHeight = 0;

    public CenterCropVideoView(Context context) {
        super(context);
        init();
    }

    public CenterCropVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CenterCropVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnPreparedListener(mp -> {
            videoWidth = mp.getVideoWidth();
            videoHeight = mp.getVideoHeight();
            requestLayout();
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = getDefaultSize(0, widthMeasureSpec);
        int measuredHeight = getDefaultSize(0, heightMeasureSpec);

        if (videoWidth > 0 && videoHeight > 0) {
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            float videoRatio = (float) videoWidth / videoHeight;
            float screenRatio = (float) widthSpecSize / heightSpecSize;

            if (videoRatio > screenRatio) {
                // Video is wider than screen: fit height, crop width (centerCrop)
                measuredWidth = (int) (heightSpecSize * videoRatio);
                measuredHeight = heightSpecSize;
            } else {
                // Video is taller than screen: fit width, crop height (centerCrop)
                measuredWidth = widthSpecSize;
                measuredHeight = (int) (widthSpecSize / videoRatio);
            }
        }
        setMeasuredDimension(measuredWidth, measuredHeight);
    }
}
