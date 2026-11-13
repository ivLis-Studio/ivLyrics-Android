package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

/**
 * Pronunciation-first toggle beside the lyrics attribution. Like the Spotify module's vocal
 * control it appears on touch and fades after a few idle seconds; it only exists while the
 * current song shows a pronunciation.
 */
final class PronunciationFirstButton extends FrameLayout {
    interface Listener {
        void onToggle(boolean enabled);
    }

    private static final int ACCENT = 0xff1ed760;
    private static final long IDLE_HIDE_MS = 3000L;
    private final ImageButton button;
    private Listener listener;
    private boolean enabled;
    private boolean available;
    private String onDescription = "";
    private String offDescription = "";
    private final Runnable hideWhenIdle = () -> animate().alpha(0f).setDuration(180L)
            .withEndAction(() -> setVisibility(INVISIBLE)).start();

    PronunciationFirstButton(Context context) {
        super(context);
        setTag("ivLyricsPronunciationFirst");
        button = new ImageButton(context);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = dp(10);
        button.setPadding(padding, padding, padding, padding);
        button.setImageDrawable(new Icon());
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0x0cffffff);
        fill.setCornerRadius(dp(22));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(22));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24ffffff), fill, mask));
        button.setFocusable(true);
        button.setOnClickListener(view -> {
            enabled = !enabled;
            render();
            revealTemporarily();
            if (listener != null) listener.onToggle(enabled);
        });
        addView(button, new LayoutParams(dp(44), dp(44)));
        setVisibility(GONE);
        render();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setDescriptions(String on, String off) {
        onDescription = on == null ? "" : on;
        offDescription = off == null ? "" : off;
        render();
    }

    /** Shows the control only while the current song has a pronunciation to swap. */
    void setState(boolean available, boolean enabled) {
        boolean wasAvailable = this.available;
        this.available = available;
        this.enabled = enabled;
        render();
        if (!available) {
            removeCallbacks(hideWhenIdle);
            animate().cancel();
            setVisibility(GONE);
        } else if (!wasAvailable) {
            revealTemporarily();
        }
    }

    boolean isOn() {
        return enabled;
    }

    void revealTemporarily() {
        if (!available) return;
        removeCallbacks(hideWhenIdle);
        animate().cancel();
        setAlpha(1f);
        setVisibility(VISIBLE);
        postDelayed(hideWhenIdle, IDLE_HIDE_MS);
    }

    private void render() {
        button.setColorFilter(enabled ? ACCENT : Color.WHITE);
        button.setSelected(enabled);
        String description = enabled ? onDescription : offDescription;
        button.setContentDescription(description);
        button.setTooltipText(description);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hideWhenIdle);
        animate().cancel();
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Two lyric rows trading places: three text lines and a vertical double arrow. */
    private static final class Icon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
            canvas.drawLine(3, 7, 13, 7, paint);
            canvas.drawLine(3, 12, 9, 12, paint);
            canvas.drawLine(3, 17, 13, 17, paint);
            canvas.drawLine(18, 4, 18, 20, paint);
            canvas.drawLine(15, 7, 18, 4, paint);
            canvas.drawLine(18, 4, 21, 7, paint);
            canvas.drawLine(15, 17, 18, 20, paint);
            canvas.drawLine(18, 20, 21, 17, paint);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
