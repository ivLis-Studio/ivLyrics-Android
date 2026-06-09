package kr.ivlis.ivlyricsandroid;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
final class SpotifyShortcutOverlayController {
    private static final String PREFS_NAME = "spotify_shortcut_overlay";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final int DEFAULT_X_DP = 18;
    private static final int DEFAULT_Y_DP = 180;
    private static final int BUBBLE_SIZE_DP = 48;
    private static final int CLICK_SLOP_DP = 8;
    private static final long FOREGROUND_POLL_MS = 600L;
    private static final long USAGE_LOOKBACK_MS = 5_000L;

    private static volatile boolean appForeground;
    private static WeakReference<SpotifyShortcutOverlayController> activeController = new WeakReference<>(null);

    private final Context context;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable foregroundPoller = new Runnable() {
        @Override
        public void run() {
            pollingForeground = false;
            refreshOverlayState();
            updateForegroundPolling();
        }
    };
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
    private boolean pollingForeground;

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
            controller.updateForegroundPolling();
        }
    }

    void update(TrackSnapshot snapshot) {
        lastSnapshot = snapshot;
        refreshOverlayState();
        updateForegroundPolling();
    }

    void destroy() {
        handler.removeCallbacks(foregroundPoller);
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

    private void updateForegroundPolling() {
        if (!shouldPollForeground()) {
            handler.removeCallbacks(foregroundPoller);
            pollingForeground = false;
            return;
        }
        if (!pollingForeground) {
            pollingForeground = true;
            handler.postDelayed(foregroundPoller, FOREGROUND_POLL_MS);
        }
    }

    private boolean shouldShow() {
        return shouldPollForeground()
                && isSpotifyForeground();
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
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setContentDescription("Open ivLyrics lyrics");

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_overlay_music_note);
        icon.setColorFilter(Color.argb(236, 255, 255, 255));
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

    private boolean isSpotifyForeground() {
        String foregroundPackage = currentForegroundPackage();
        return isSpotifyPackage(foregroundPackage);
    }

    private String currentForegroundPackage() {
        String usageStatsPackage = currentForegroundPackageFromUsageStats();
        if (!usageStatsPackage.isEmpty()) {
            return usageStatsPackage;
        }
        return currentForegroundPackageFromProcesses();
    }

    private String currentForegroundPackageFromUsageStats() {
        if (!hasUsageStatsAccess()) {
            return "";
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return "";
        }

        long endTime = System.currentTimeMillis();
        UsageEvents events;
        try {
            events = manager.queryEvents(endTime - USAGE_LOOKBACK_MS, endTime);
        } catch (RuntimeException ignored) {
            return "";
        }
        if (events == null) {
            return "";
        }

        String foregroundPackage = "";
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int eventType = event.getEventType();
            boolean resumed = eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && eventType == UsageEvents.Event.ACTIVITY_RESUMED);
            if (resumed) {
                foregroundPackage = event.getPackageName() == null ? "" : event.getPackageName();
            }
        }
        return foregroundPackage;
    }

    private boolean hasUsageStatsAccess() {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.getPackageName()
                );
            } else {
                mode = appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.getPackageName()
                );
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private String currentForegroundPackageFromProcesses() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return "";
        }
        List<ActivityManager.RunningAppProcessInfo> processes;
        try {
            processes = manager.getRunningAppProcesses();
        } catch (RuntimeException ignored) {
            return "";
        }
        if (processes == null) {
            return "";
        }
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process == null
                    || process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    || process.pkgList == null) {
                continue;
            }
            for (String packageName : process.pkgList) {
                if (isSpotifyPackage(packageName)) {
                    return packageName;
                }
            }
        }
        return "";
    }

    private boolean isSpotifyPackage(String packageName) {
        String value = packageName == null ? "" : packageName.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("com.spotify.");
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
