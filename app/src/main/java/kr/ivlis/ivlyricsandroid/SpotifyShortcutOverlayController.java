package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
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
    private static final int BUBBLE_SIZE_DP = 48;
    private static final int CLICK_SLOP_DP = 8;

    private static volatile boolean appForeground;
    private static volatile boolean spotifyNowPlayingForeground;
    private static WeakReference<SpotifyShortcutOverlayController> activeController = new WeakReference<>(null);

    private final Context context;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private final int bubbleSizePx;
    private final int clickSlopPx;
    private TrackSnapshot lastSnapshot;
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
        if (controller != null) {
            controller.refreshOverlayState();
        }
    }

    static void setSpotifyNowPlayingForeground(boolean foreground) {
        spotifyNowPlayingForeground = foreground;
        SpotifyShortcutOverlayController controller = activeController.get();
        if (controller != null) {
            controller.refreshOverlayState();
        }
    }

    void update(TrackSnapshot snapshot) {
        lastSnapshot = snapshot;
        refreshOverlayState();
    }

    void destroy() {
        hide();
        if (activeController.get() == this) {
            activeController = new WeakReference<>(null);
        }
    }

    private void refreshOverlayState() {
        if (shouldShow()) {
            show();
        } else {
            hide();
        }
    }

    private boolean shouldShow() {
        return shouldPollForeground() && spotifyNowPlayingForeground;
    }

    private boolean shouldPollForeground() {
        TrackSnapshot snapshot = lastSnapshot;
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
                        ,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(KEY_X, dp(DEFAULT_X_DP));
        params.y = prefs.getInt(KEY_Y, dp(DEFAULT_Y_DP));
        clampPosition();
        try {
            windowManager.addView(bubble, params);
            bubble.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (clampPosition()) {
                    try {
                        windowManager.updateViewLayout(view, params);
                    } catch (RuntimeException ignored) {
                    }
                }
            });
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
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setContentDescription("Open ivLyrics lyrics");

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ivlyrics_overlay_symbol);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.addView(icon, new FrameLayout.LayoutParams(
                dp(34),
                dp(34),
                Gravity.CENTER
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
                params.y = downY + Math.round(dy);
                clampPosition();
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
        clampPosition();
        prefs.edit()
                .putInt(KEY_X, params.x)
                .putInt(KEY_Y, params.y)
                .apply();
    }

    private boolean clampPosition() {
        if (params == null || windowManager == null) {
            return false;
        }
        Rect bounds;
        int insetLeft = 0;
        int insetTop = 0;
        int insetRight = 0;
        int insetBottom = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            bounds = metrics.getBounds();
            android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
            );
            insetLeft = insets.left;
            insetTop = insets.top;
            insetRight = insets.right;
            insetBottom = insets.bottom;
        } else {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            bounds = new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
        }
        int minX = insetLeft;
        int minY = insetTop;
        int maxX = Math.max(minX, bounds.width() - insetRight - bubbleSizePx);
        int maxY = Math.max(minY, bounds.height() - insetBottom - bubbleSizePx);
        int oldX = params.x;
        int oldY = params.y;
        params.x = Math.max(minX, Math.min(maxX, params.x));
        params.y = Math.max(minY, Math.min(maxY, params.y));
        return oldX != params.x || oldY != params.y;
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

    private boolean isSpotifyPackage(String packageName) {
        String value = packageName == null ? "" : packageName.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("com.spotify.");
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
