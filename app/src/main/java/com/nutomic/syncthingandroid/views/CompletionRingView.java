package com.nutomic.syncthingandroid.views;

import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.View;
import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nutomic.syncthingandroid.R;

/**
 * Smartisan-style completion ring: a soft grey track with a single accent (#A8C86A)
 * progress arc and a bold percentage in the center. The arc animates smoothly between
 * values so the status page feels alive without being noisy.
 */
public class CompletionRingView extends View {

    private static final int PROGRESS_COLOR = 0xFFA8C86A;
    private static final float STROKE_WIDTH_DP = 8f;
    private static final float TEXT_SIZE_DP = 34f;
    private static final int ANIM_DURATION_MS = 500;

    private final Paint mTrackPaint;
    private final Paint mProgressPaint;
    private final Paint mTextPaint;

    private int mTargetProgress = 0;
    private int mAnimatedProgress = 0;
    private String mCenterText = "";
    private ValueAnimator mAnimator;

    public CompletionRingView(Context context) {
        this(context, null);
    }

    public CompletionRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        final float density = Resources.getSystem().getDisplayMetrics().density;

        mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeWidth(STROKE_WIDTH_DP * density);
        // Day/night aware track + ink via color resources.
        mTrackPaint.setColor(ContextCompat.getColor(context, R.color.sm_track));
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);

        mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setStrokeWidth(STROKE_WIDTH_DP * density);
        mProgressPaint.setColor(PROGRESS_COLOR);
        mProgressPaint.setStrokeCap(Paint.Cap.ROUND);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(ContextCompat.getColor(context, R.color.sm_ink));
        mTextPaint.setTextSize(TEXT_SIZE_DP * density);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setFakeBoldText(true);
    }

    /**
     * Sets the completion percentage (0..100). The arc animates from its current value.
     */
    public void setProgress(int progress) {
        int clamped = Math.max(0, Math.min(100, progress));
        mTargetProgress = clamped;
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofInt(mAnimatedProgress, clamped);
        mAnimator.setDuration(ANIM_DURATION_MS);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mAnimatedProgress = (int) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.start();
    }

    /**
     * Sets the bold percentage text drawn in the center of the ring.
     */
    public void setCenterText(String text) {
        if (TextUtils.equals(mCenterText, text)) {
            return;
        }
        mCenterText = text == null ? "" : text;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        final float density = Resources.getSystem().getDisplayMetrics().density;
        final float stroke = STROKE_WIDTH_DP * density;
        final float radius = (Math.min(w, h) - stroke) / 2f;
        final float cx = w / 2f;
        final float cy = h / 2f;

        // Background track (full circle).
        canvas.drawCircle(cx, cy, radius, mTrackPaint);

        // Accent progress arc, starting at 12 o'clock.
        if (mAnimatedProgress > 0) {
            final float sweep = 360f * mAnimatedProgress / 100f;
            final RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(oval, -90f, sweep, false, mProgressPaint);
        }

        // Center percentage.
        if (!TextUtils.isEmpty(mCenterText)) {
            final Paint.FontMetrics fm = mTextPaint.getFontMetrics();
            final float textY = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(mCenterText, cx, textY, mTextPaint);
        }
    }
}
