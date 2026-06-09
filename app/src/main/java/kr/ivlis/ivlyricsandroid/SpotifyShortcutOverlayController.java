package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.Locale;

final class SpotifyShortcutOverlayController {
    private static final String PREFS_NAME = "spotify_shortcut_overlay";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final int DEFAULT_X_DP = 18;
    private static final int DEFAULT_Y_DP = 180;
    private static final int BUBBLE_SIZE_DP = 54;
    private static final int CLICK_SLOP_DP = 8;

    private static volatile boolean appForeground;
    private static WeakReference<SpotifyShortcutOverlayController> activeController = new WeakReference<>(null);

    private final Context context;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private final int bubbleSizePx;
    private final int clickSlopPx;
    private View bubble;
    private WindowManager.LayoutParams params;
    private float downRawX;
    private float downRawY;
    private int downX;
    private int downY;
    private boolean dragging;

    SpotifyShortcutOverlayController(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.bubbleSizePx = dp(BUBBLE_SIZE_DP);
        this.clickSlopPx = dp(CLICK_SLOP_DP);
        activeController = new WeakReference<>(this);
    }

    static void setIvLyricsForeground(boolean foreground) {
        appForeground = foreground;
        SpotifyShortcutOverlayController controller = activeController.get();
        if (foreground && controller != null) {
            controller.hide();
        }
    }

    void update(TrackSnapshot snapshot) {
        if (shouldShow(snapshot)) {
            show();
        } else {
            hide();
        }
    }

    void destroy() {
        hide();
        if (activeController.get() == this) {
            activeController = new WeakReference<>(null);
        }
    }

    private boolean shouldShow(TrackSnapshot snapshot) {
        return !appForeground
                && snapshot != null
                && snapshot.hasUsableMetadata()
                && isSpotifyPackage(snapshot.packageName)
                && canDrawOverlays();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    private void show() {
        if (windowManager == null || bubble != null) {
            return;
        }
        bubble = createBubble();
        params = new WindowManager.LayoutParams(
                bubbleSizePx,
                bubbleSizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(KEY_X, dp(DEFAULT_X_DP));
        params.y = prefs.getInt(KEY_Y, dp(DEFAULT_Y_DP));
        try {
            windowManager.addView(bubble, params);
        } catch (RuntimeException ignored) {
            bubble = null;
            params = null;
        }
    }

    private void hide() {
        if (windowManager == null || bubble == null) {
            bubble = null;
            params = null;
            return;
        }
        try {
            windowManager.removeView(bubble);
        } catch (RuntimeException ignored) {
        } finally {
            bubble = null;
            params = null;
        }
    }

    private View createBubble() {
        FrameLayout view = new FrameLayout(context);
        view.setBackground(bubbleBackground());
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setContentDescription("Open ivLyrics lyrics");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(dp(10));
        }

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ivlyrics_logo);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        view.setOnTouchListener(this::handleTouch);
        view.setOnClickListener(target -> openLyricsPage());
        return view;
    }

    private boolean handleTouch(View target, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = params == null ? 0 : params.x;
                downY = params == null ? 0 : params.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (params == null || windowManager == null) {
                    return true;
                }
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (Math.abs(dx) > clickSlopPx || Math.abs(dy) > clickSlopPx) {
                    dragging = true;
                }
                params.x = downX + Math.round(dx);
                params.y = Math.max(0, downY + Math.round(dy));
                try {
                    windowManager.updateViewLayout(target, params);
                } catch (RuntimeException ignored) {
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    savePosition();
                } else {
                    target.performClick();
                }
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    savePosition();
                }
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    private void savePosition() {
        if (params == null) {
            return;
        }
        prefs.edit()
                .putInt(KEY_X, params.x)
                .putInt(KEY_Y, params.y)
                .apply();
    }

    private void openLyricsPage() {
        hide();
        appForeground = true;
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_LYRICS_PAGE, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(intent);
        } catch (RuntimeException ignored) {
            appForeground = false;
        }
    }

    private GradientDrawable bubbleBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.argb(216, 18, 20, 31));
        drawable.setStroke(dp(1), Color.argb(96, 255, 255, 255));
        return drawable;
    }

    private boolean isSpotifyPackage(String packageName) {
        String value = packageName == null ? "" : packageName.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("com.spotify.");
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
