package dev.ivlyrics.spotify;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.RequiresApi;

import kr.ivlis.ivlyricsandroid.IvLyricsBridge;

/**
 * Lets the player's ivLyrics card and inline preview stop drawing while the translucent sheet
 * hides them. They pause only after the sheet, fully open and at rest in a resumed Activity,
 * has drawn a frame. {@link #uncover()} runs before anything moves, rebuilds or closes the
 * sheet and repaints them for that same frame; pausing the Activity uncovers them too.
 */
@RequiresApi(30)
final class PlayerSurfaceCover implements Application.ActivityLifecycleCallbacks {
    private final Activity activity;
    private View restingRoot;
    private View drawRoot;
    private ViewTreeObserver drawObserver;
    private ViewTreeObserver.OnDrawListener drawListener;
    private int armGeneration;
    private boolean resumed;
    private boolean covered;
    private boolean released;
    // A finger is on a drag handle: the player draws, still hidden, so a drag finds it ready.
    private boolean prewarming;
    private boolean prewarmFromCover;

    PlayerSurfaceCover(Activity activity) {
        this.activity = activity;
        activity.registerActivityLifecycleCallbacks(this);
    }

    /** The sheet came to rest fully open. */
    void settledOpen(View root) {
        if (released || root == null) return;
        restingRoot = root;
        arm();
    }

    /** The sheet is about to move, rebuild or close: the player draws live from this frame. */
    void uncover() {
        restingRoot = null;
        prewarming = false;
        disarm();
        setCovered(false);
    }

    /** A drag may start soon: the player repaints now, while the resting sheet still hides it. */
    void prewarm() {
        if (released || restingRoot == null || prewarming) return;
        prewarming = true;
        prewarmFromCover = covered;
        disarm();
        setCovered(false);
    }

    /** The touch ended without moving the sheet (moving it would have called uncover()). */
    void endPrewarm() {
        if (!prewarming) return;
        prewarming = false;
        View root = restingRoot;
        if (root == null) return;
        // The sheet never moved, so the resting frame that covered the player is still on screen.
        if (prewarmFromCover && canCover(root)) setCovered(true);
        else arm();
    }

    void release() {
        if (released) return;
        uncover();
        released = true;
        activity.unregisterActivityLifecycleCallbacks(this);
    }

    private void arm() {
        disarm();
        View root = restingRoot;
        if (root == null || !resumed || covered || prewarming) return;
        int generation = armGeneration;
        ViewTreeObserver.OnDrawListener listener = new ViewTreeObserver.OnDrawListener() {
            private boolean drawn;
            @Override public void onDraw() {
                if (drawn || generation != armGeneration || root.getTranslationY() > 0.5f) return;
                drawn = true;
                // A listener cannot be removed while onDraw is dispatched. Cover once this
                // resting frame has been drawn, in the next frame's animation callbacks.
                root.postOnAnimation(() -> {
                    if (generation != armGeneration) return;
                    disarm();
                    if (canCover(root)) setCovered(true);
                });
            }
        };
        drawRoot = root;
        drawObserver = root.getViewTreeObserver();
        drawListener = listener;
        drawObserver.addOnDrawListener(listener);
    }

    private void disarm() {
        armGeneration++;
        if (drawListener != null) {
            // A listener added before the root attached moved to its window's observer.
            if (drawObserver.isAlive()) drawObserver.removeOnDrawListener(drawListener);
            ViewTreeObserver current = drawRoot.getViewTreeObserver();
            if (current != drawObserver && current.isAlive()) current.removeOnDrawListener(drawListener);
        }
        drawRoot = null;
        drawObserver = null;
        drawListener = null;
    }

    private boolean canCover(View root) {
        return !released && resumed && restingRoot == root && root.isAttachedToWindow() && root.isLaidOut()
                && root.isShown() && root.getWindowVisibility() == View.VISIBLE
                && root.getTranslationY() <= 0.5f;
    }

    private void setCovered(boolean next) {
        if (covered == next) return;
        covered = next;
        if (next) IvLyricsBridge.coverPlayerSurfaces(this, restingRoot);
        else IvLyricsBridge.uncoverPlayerSurfaces(this);
    }

    @Override public void onActivityPostResumed(Activity resumedActivity) {
        resumed = true;
        if (restingRoot != null) arm();
    }

    @Override public void onActivityPrePaused(Activity pausingActivity) {
        // What follows a pause (another Activity, leaving the task, a system finish) can reveal
        // the player without moving the sheet.
        resumed = false;
        // A touch interrupted by the pause may never report its end; resuming covers again.
        prewarming = false;
        disarm();
        setCovered(false);
    }

    @Override public void onActivityCreated(Activity created, Bundle state) { }
    @Override public void onActivityStarted(Activity started) { }
    @Override public void onActivityResumed(Activity resumedActivity) { }
    @Override public void onActivityPaused(Activity paused) { }
    @Override public void onActivityStopped(Activity stopped) { }
    @Override public void onActivitySaveInstanceState(Activity saving, Bundle state) { }
    @Override public void onActivityDestroyed(Activity destroyed) { }
}
