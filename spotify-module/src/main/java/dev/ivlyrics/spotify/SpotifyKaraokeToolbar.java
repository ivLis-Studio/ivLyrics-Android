package dev.ivlyrics.spotify;

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
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.function.Function;

/** Compact native-player controls, owned and packaged only by the Spotify module. */
final class SpotifyKaraokeToolbar extends FrameLayout {
    private static final int ACCENT = 0xff1ed760;
    private static final long IDLE_HIDE_MS = 3000L;
    private final SpotifyKaraokeController controller;
    /** Looks up sing_along.* text in the app's UI language. */
    private final Function<String, String> text;
    private final ImageButton microphone;
    private final ProgressBar progress;
    private SpotifyKaraokeController.State state = SpotifyKaraokeController.State.OFF;
    private boolean available;
    private boolean controlVisible;
    private String reason = "WAITING";
    private String lastError = "";
    private final Runnable hideWhenIdle = () -> animate().alpha(0f).setDuration(180L)
            .withEndAction(() -> setVisibility(INVISIBLE)).start();

    SpotifyKaraokeToolbar(Context context, SpotifyKaraokeController controller, Function<String, String> text) {
        super(context);
        this.controller = controller;
        this.text = text;
        setTag("ivLyricsKaraokeToolbar");
        microphone = button();
        microphone.setTag("ivLyricsKaraokeToggle");
        addView(microphone, new FrameLayout.LayoutParams(dp(44), dp(44)));
        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        progress.setClickable(false);
        addView(progress, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        microphone.setOnClickListener(view -> {
            if (!available && state != SpotifyKaraokeController.State.LOW
                    && state != SpotifyKaraokeController.State.HIGH
                    && state != SpotifyKaraokeController.State.LOADING
                    && state != SpotifyKaraokeController.State.ERROR) {
                Toast.makeText(context, reasonText(reason), Toast.LENGTH_SHORT).show();
                return;
            }
            controller.cycle();
        });
        render(state, false, "WAITING");
    }

    void render(SpotifyKaraokeController.State next, boolean canEnable, String detail) {
        state = next;
        available = canEnable;
        reason = detail;
        boolean wasVisible = controlVisible;
        controlVisible = controller.visible();
        if (!controlVisible) {
            removeCallbacks(hideWhenIdle);
            animate().cancel();
            setVisibility(GONE);
        } else if (!wasVisible) {
            revealTemporarily();
        }
        boolean enabled = next == SpotifyKaraokeController.State.LOW || next == SpotifyKaraokeController.State.HIGH;
        boolean waiting = next == SpotifyKaraokeController.State.LOADING || next == SpotifyKaraokeController.State.STOPPING;
        microphone.setImageDrawable(new Icon(next == SpotifyKaraokeController.State.LOW ? 1
                : next == SpotifyKaraokeController.State.HIGH ? 2 : 0));
        microphone.setImageAlpha(waiting ? 0 : 255);
        microphone.setColorFilter(enabled ? ACCENT : Color.WHITE);
        microphone.setAlpha(canEnable || enabled || waiting ? 1f : .42f);
        microphone.setSelected(enabled);
        microphone.setEnabled(next != SpotifyKaraokeController.State.STOPPING);
        progress.setVisibility(waiting ? VISIBLE : GONE);
        String label = next == SpotifyKaraokeController.State.LOW ? "sing_along.state_low"
                : next == SpotifyKaraokeController.State.HIGH ? "sing_along.state_high"
                : next == SpotifyKaraokeController.State.LOADING ? "sing_along.cancel"
                : next == SpotifyKaraokeController.State.STOPPING ? "sing_along.stopping"
                : "sing_along.state_off";
        String description = text.apply(label);
        if (!canEnable && !enabled && !waiting && detail != null && !detail.isEmpty()) {
            description += ": " + reasonText(detail);
        }
        microphone.setContentDescription(description);
        microphone.setTooltipText(description);
        if (next == SpotifyKaraokeController.State.ERROR && !detail.equals(lastError)) {
            lastError = detail;
            Toast.makeText(getContext(), reasonText(detail), Toast.LENGTH_LONG).show();
        } else if (next != SpotifyKaraokeController.State.ERROR) lastError = "";
    }

    void revealTemporarily() {
        if (!controlVisible) return;
        removeCallbacks(hideWhenIdle);
        animate().cancel();
        setAlpha(1f);
        setVisibility(VISIBLE);
        postDelayed(hideWhenIdle, IDLE_HIDE_MS);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        revealTemporarily();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(hideWhenIdle);
        animate().cancel();
        super.onDetachedFromWindow();
    }

    private String reasonText(String value) {
        String key;
        switch (value == null ? "" : value) {
            case "NO_TRACK": key = "sing_along.no_track"; break;
            case "REMOTE": key = "sing_along.remote"; break;
            case "OFFLINE": key = "sing_along.offline"; break;
            case "LOW_QUALITY": key = "sing_along.quality"; break;
            case "UNSUPPORTED": key = "sing_along.version"; break;
            case "UNSUPPORTED_TRACK": key = "sing_along.unsupported_track"; break;
            case "UNSUPPORTED_ACCOUNT": key = "sing_along.unsupported_account"; break;
            case "MASK_ERROR": key = "sing_along.mask_error"; break;
            case "TIMEOUT": key = "sing_along.timeout"; break;
            case "RESET_FAILED": key = "sing_along.reset_failed"; break;
            case "SERVICE_ERROR": key = "sing_along.error"; break;
            default: key = "sing_along.waiting";
        }
        return text.apply(key);
    }

    private ImageButton button() {
        ImageButton button = new ImageButton(getContext());
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setImageDrawable(new Icon(0));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(0x0cffffff);
        fill.setCornerRadius(dp(22));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(22));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24ffffff), fill, mask));
        button.setFocusable(true);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class Icon extends Drawable {
        final int level;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(int level) {
            this.level = level;
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
            int mic = canvas.save();
            canvas.translate(2.16f, 0);
            canvas.scale(.82f, .82f);
            canvas.drawRoundRect(8, 2, 16, 15, 4, 4, paint);
            canvas.drawArc(5, 7, 19, 19, 0, 180, false, paint);
            canvas.drawLine(12, 19, 12, 22, paint);
            canvas.drawLine(9, 22, 15, 22, paint);
            canvas.restoreToCount(mic);
            paint.setStyle(Paint.Style.FILL);
            if (level == 1) canvas.drawCircle(12, 22, 1.2f, paint);
            if (level == 2) {
                canvas.drawCircle(9.5f, 22, 1.2f, paint);
                canvas.drawCircle(14.5f, 22, 1.2f, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
