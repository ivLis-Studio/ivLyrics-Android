package dev.ivlyrics.spotify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;

import kr.ivlis.ivlyricsandroid.LyricsActivityHost;

/** Owns the embedded page's vertical motion; Android does not animate the host Activity. */
public final class LyricsSheetTransition implements LyricsActivityHost {
    private final Activity activity;
    private final float density;
    private View root;
    private ViewOutlineProvider originalOutline;
    private boolean originalClipToOutline;
    private ValueAnimator animator;
    private ViewTreeObserver.OnPreDrawListener pendingLayout;
    private Runnable pendingOpen;
    private Runnable unregisterBack;
    private Runnable pendingFinish;
    private boolean closing;
    private boolean destroyed;
    private boolean moving;

    private final ViewOutlineProvider sheetOutline = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            float radius = moving || view.getTranslationY() > 0.5f ? 28f * density : 0f;
            // Extend the lower corners beyond the view so only its top corners are rounded.
            outline.setRoundRect(0, 0, view.getWidth(),
                    view.getHeight() + (int) Math.ceil(radius), radius);
        }
    };

    public LyricsSheetTransition(Activity activity, View root) {
        this.activity = activity;
        density = activity.getResources().getDisplayMetrics().density;
        prepareWindow();
        if (Build.VERSION.SDK_INT >= 33) unregisterBack = Api33.registerBack(activity);
        attachRoot(root, true);
    }

    @SuppressWarnings("deprecation")
    private void prepareWindow() {
        Window window = activity.getWindow();
        window.setWindowAnimations(0);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= 30) {
            // Updating only the local theme cannot change Spotify's opaque ActivityRecord.
            // This public API asks the system to keep the previous Spotify screen visible.
            try { activity.setTranslucent(true); } catch (RuntimeException ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            // API 26–29 retain their opaque host window; no hidden API is required.
            activity.overridePendingTransition(0, 0);
        }
    }

    /** A rebuilt page is already open; only the first root receives the entrance animation. */
    public void attachRoot(View nextRoot, boolean animate) {
        if (destroyed || nextRoot == null || nextRoot == root) return;
        float previousFraction = root == null ? 0f : root.getTranslationY() / sheetHeight();
        cancelPendingOpen();
        cancelAnimator();
        restoreOutline();
        root = nextRoot;
        originalOutline = root.getOutlineProvider();
        originalClipToOutline = root.getClipToOutline();
        root.setOutlineProvider(sheetOutline);
        root.setClipToOutline(true);
        moving = false;
        if (closing) {
            // A configuration rebuild must not reopen a page already being dismissed.
            setTranslationInternal(sheetHeight() * previousFraction);
            continueClose();
            return;
        }
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            setTranslationInternal(0f);
            return;
        }
        moving = true;
        setTranslationInternal(sheetHeight());
        View initialRoot = root;
        pendingLayout = () -> {
            if (initialRoot.getHeight() <= 0) return true;
            removeLayoutListener(initialRoot);
            if (destroyed || closing || root != initialRoot) return true;
            setTranslationInternal(sheetHeight());
            pendingOpen = () -> {
                pendingOpen = null;
                if (!destroyed && !closing && root == initialRoot) animateTo(0f, 330, null);
            };
            initialRoot.postOnAnimation(pendingOpen);
            return true;
        };
        initialRoot.getViewTreeObserver().addOnPreDrawListener(pendingLayout);
    }

    /** Cancels an opening/settling animation at its current position before a drag begins. */
    public void interruptDrag() {
        if (destroyed || closing) return;
        cancelPendingOpen();
        cancelAnimator();
        moving = root != null && root.getTranslationY() > 0.5f;
        if (root != null) root.invalidateOutline();
    }

    public boolean isClosing() { return closing; }

    /** Translation and velocity use physical pixels, matching MotionEvent/VelocityTracker. */
    public void dragTo(float translationY) {
        if (destroyed || closing || root == null) return;
        interruptDrag();
        moving = translationY > 0.5f;
        setTranslationInternal(translationY);
    }

    public void setTranslation(float translationY) { dragTo(translationY); }

    public void settle(float velocityY, Runnable finishImmediately) {
        if (destroyed || closing || root == null) return;
        float distance = root.getTranslationY();
        if (distance >= sheetHeight() * 0.30f
                || (velocityY > 1200f * density && distance > 42f * density)) {
            close(finishImmediately);
        } else {
            settleOpen();
        }
    }

    public void settleOpen() {
        if (destroyed || closing || root == null) return;
        cancelPendingOpen();
        animateTo(0f, 210, null);
    }

    public void close(Runnable finishImmediately) {
        if (destroyed || closing) return;
        closing = true;
        pendingFinish = finishImmediately;
        cancelPendingOpen();
        continueClose();
    }

    private void continueClose() {
        float height = sheetHeight();
        float distance = root == null ? 0f : Math.max(0f, height - root.getTranslationY());
        long duration = Math.max(120L, Math.min(280L, Math.round(280f * distance / height)));
        animateTo(height, duration, () -> {
            Runnable finish = pendingFinish;
            pendingFinish = null;
            if (finish != null) finish.run();
        });
    }

    private float sheetHeight() {
        int height = root == null ? 0 : root.getHeight();
        if (height <= 0) height = activity.getWindow().getDecorView().getHeight();
        if (height <= 0) height = activity.getResources().getDisplayMetrics().heightPixels;
        return Math.max(1, height);
    }

    private void setTranslationInternal(float translationY) {
        if (root == null) return;
        root.setTranslationY(Math.max(0f, Math.min(sheetHeight(), translationY)));
        root.invalidateOutline();
    }

    private void animateTo(float destination, long duration, Runnable completion) {
        cancelAnimator();
        if (root == null || !ValueAnimator.areAnimatorsEnabled()
                || Math.abs(root.getTranslationY() - destination) < 0.5f) {
            moving = false;
            setTranslationInternal(destination);
            if (!destroyed && completion != null) completion.run();
            return;
        }
        moving = true;
        root.invalidateOutline();
        ValueAnimator next = ValueAnimator.ofFloat(root.getTranslationY(), destination);
        animator = next;
        next.setDuration(duration);
        next.setInterpolator(new DecelerateInterpolator(1.5f));
        next.addUpdateListener(value -> setTranslationInternal((float) value.getAnimatedValue()));
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != next) return; // Canceled animations cannot finish the Activity.
                animator = null;
                moving = false;
                setTranslationInternal(destination);
                if (!destroyed && completion != null) completion.run();
            }
        });
        next.start();
    }

    private void cancelAnimator() {
        ValueAnimator old = animator;
        animator = null;
        if (old != null) old.cancel();
    }

    private void removeLayoutListener(View view) {
        if (pendingLayout != null && view.getViewTreeObserver().isAlive()) {
            view.getViewTreeObserver().removeOnPreDrawListener(pendingLayout);
        }
        pendingLayout = null;
    }

    private void cancelPendingOpen() {
        if (root != null) {
            removeLayoutListener(root);
            if (pendingOpen != null) root.removeCallbacks(pendingOpen);
        }
        pendingOpen = null;
    }

    private void restoreOutline() {
        if (root == null) return;
        root.setOutlineProvider(originalOutline);
        root.setClipToOutline(originalClipToOutline);
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        cancelPendingOpen();
        cancelAnimator();
        pendingFinish = null;
        if (unregisterBack != null) {
            unregisterBack.run();
            unregisterBack = null;
        }
        restoreOutline();
        root = null;
    }

    @android.annotation.TargetApi(33)
    private static final class Api33 {
        @SuppressWarnings("deprecation")
        static Runnable registerBack(Activity activity) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = activity::onBackPressed;
            dispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
}
