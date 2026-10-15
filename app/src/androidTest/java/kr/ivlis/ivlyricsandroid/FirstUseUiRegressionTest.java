package kr.ivlis.ivlyricsandroid;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import static org.junit.Assert.*;

/** Real production first-use views, touch dispatch, persistence and native window captures. */
@android.annotation.TargetApi(30)
public final class FirstUseUiRegressionTest {
    // Exceeds both stock OverScroller's 250ms animation and queued focus/scroll cadence
    // (300ms in the regression). Unlike isDirty(), OnDraw works on software rendering too.
    private static final long DRAW_QUIET_MS = 400;
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private FirstUseHarnessActivity activity;
    private String language;
    private Runnable onBlockedInput;

    @Rule public final TestWatcher nativeEvidence = new TestWatcher() {
        @Override protected void failed(Throwable error, Description test) {
            // Capture the failing native window BEFORE cleanup, without waiting for a layout
            // which may itself be the failure. The CI collector already preserves these PNGs.
            try {
                if (activity != null) saveNativeScreenshot("failure-" + test.getMethodName());
            } catch (Throwable captureError) {
                error.addSuppressed(captureError);
            }
        }
        @Override protected void finished(Description test) { cleanup(); }
    };

    private void cleanup() {
        if (activity != null) onMain(() -> { activity.dismissPrompt(); activity.finish(); });
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_UNFREEZE);
    }

    @Test public void onboardingBackRetryAndRecreationKeepDrafts() throws Exception {
        launch("ko", 1f, true, false, "dark");
        capture("ko-onboarding-1-dark");
        View panel = activity.view("spotifySetupPanel");
        assertControls(panel);
        tap(switches(panel).get(0));
        onMain(() -> assertFalse(((SettingsKit) activity.value("onboardingKit")).p.dark));
        capture("ko-onboarding-1-light");
        tap(activity.view("onboardingNextButton"));
        onMain(() -> assertEquals(1, activity.value("onboardingStep")));
        capture("ko-onboarding-2-light");
        if (!NowPlayingService.isNotificationAccessEnabled(activity)) {
            tap(activity.view("onboardingNextButton"));
            onMain(() -> {
                assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, activity.permissionIntent.getAction());
                assertEquals(1, activity.value("onboardingStep"));
            });
        }
        // Platform grant itself is outside the offline fixture. Enter the same production phase.
        onMain(() -> step(2));
        capture("ko-onboarding-3-light");
        tap(activity.view("onboardingNextButton"));
        onMain(() -> {
            assertEquals(ui("toast.spotify_missing"), ((TextView) activity.view("spotifySetupStatusView")).getText().toString());
            assertFalse(settings().snapshot().hasSpotifyApiCredentials());
            ((EditText) activity.view("spotifySetupClientIdInput")).setText("fixture-client");
            ((EditText) activity.view("spotifySetupClientSecretInput")).setText("fixture-secret");
        });
        capture("ko-onboarding-3-missing-light");
        tap(activity.view("onboardingBackButton"));
        onMain(() -> step(2));
        assertDrafts();
        onMain(() -> {
            activity.put("spotifyCredentialsValidationInFlight", true);
            activity.call("setSpotifyValidationStatus", new Class<?>[]{String.class}, ui("spotify.status_checking"));
            assertFalse(activity.view("onboardingNextButton").isEnabled());
            assertFalse(activity.view("spotifySetupClientSecretInput").isEnabled());
            step(1); step(2);
            assertFalse(activity.view("onboardingNextButton").isEnabled());
            activity.put("spotifyCredentialsValidationInFlight", false);
            activity.call("setSpotifyValidationStatus", new Class<?>[]{String.class}, "Fixture validation error");
            assertTrue(activity.view("onboardingNextButton").isEnabled());
        });
        assertDrafts();
        onMain(() -> {
            settings().setUiLang("en");
            activity.call("applyUiLanguageChange");
        });
        language = "en";
        assertDrafts();
        // Android recreation restores the phase and drafts through the actual saved-instance path.
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(FirstUseHarnessActivity.class.getName(), null, false);
        onMain(() -> activity.recreate());
        FirstUseHarnessActivity recreated = (FirstUseHarnessActivity) instrumentation.waitForMonitorWithTimeout(monitor, 15000);
        instrumentation.removeMonitor(monitor);
        assertNotNull("Activity recreation", recreated);
        activity = recreated;
        instrumentation.waitForIdleSync();
        assertDrafts();
        onMain(() -> {
            assertEquals(2, activity.value("onboardingStep"));
            assertFalse(settings().snapshot().hasSpotifyApiCredentials());
            assertFalse(settings().snapshot().isAiProviderEnabled(AiLyricsSettings.Provider.LOCAL_AI_ID));
        });
        capture("en-onboarding-3-restored-light");
    }

    @Test public void germanNarrowLargeTextAndGuide() throws Exception {
        launch("de", 2f, true, false, "light");
        capture("de-onboarding-1-font2-light");
        assertControls(activity.view("spotifySetupPanel"));
        tap(activity.view("onboardingNextButton"));
        capture("de-onboarding-2-font2-light");
        onMain(() -> step(2));
        Button help = button(activity.view("spotifySetupPanel"), ui("onboarding.spotify_help"));
        tap(help);
        onMain(() -> assertEquals(true, activity.value("onboardingSpotifyHelpExpanded")));
        assertControls(activity.view("spotifySetupPanel"));
        tap(button(activity.view("spotifySetupPanel"), ui("button.next")));
        onMain(() -> {
            assertEquals(1, activity.value("spotifyInstructionStep"));
            step(1); step(2);
            assertEquals(true, activity.value("onboardingSpotifyHelpExpanded"));
            assertEquals(1, activity.value("spotifyInstructionStep"));
        });
        capture("de-onboarding-3-guide-font2-light");
        tap(activity.view("onboardingNextButton"));
        capture("de-onboarding-3-action-font2-light");
    }

    @Test public void koreanOnboardingLandscapeScroll() throws Exception {
        launch("ko", 1.6f, true, true, "dark");
        assertControls(activity.view("spotifySetupPanel"));
        capture("ko-onboarding-landscape-font1.6-dark-top");
        tap(activity.view("onboardingNextButton"));
        onMain(() -> assertEquals(1, activity.value("onboardingStep")));
        capture("ko-onboarding-landscape-font1.6-dark-permission");
        onMain(() -> step(2));
        assertControls(activity.view("spotifySetupPanel"));
        reveal(activity.view("onboardingNextButton"));
        capture("ko-onboarding-landscape-font1.6-dark-action");
        tap(activity.view("onboardingBackButton"));
        onMain(() -> assertEquals(1, activity.value("onboardingStep")));
    }

    @Test public void onboardingTapWaitsForClampedScroll() throws Exception {
        launch("ko", 1.6f, true, true, "dark");
        tapAfterQueuedClampedScroll(activity.view("onboardingNextButton"));
        onMain(() -> assertEquals(1, activity.value("onboardingStep")));
        onMain(() -> step(2));
        tapAfterQueuedClampedScroll(activity.view("onboardingBackButton"));
        onMain(() -> assertEquals(1, activity.value("onboardingStep")));
    }

    @Test public void onboardingWaitsForNonFocusableInputOverlay() throws Exception {
        launch("ko", 1.6f, true, true, "dark");
        View next = activity.view("onboardingNextButton");
        reveal(next);
        View blocker = new View(activity);
        AtomicInteger blockedTouches = new AtomicInteger();
        blocker.setOnTouchListener((view, event) -> { blockedTouches.incrementAndGet(); return true; });
        WindowManager manager = activity.getWindowManager();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.token = activity.getWindow().getDecorView().getWindowToken();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setTitle("first-use-input-blocker");
        onMain(() -> manager.addView(blocker, params));
        AtomicInteger observedBlockers = new AtomicInteger();
        onBlockedInput = () -> {
            assertTrue("app focus does not prove native hit ownership", activity.hasWindowFocus());
            assertTrue(blocker.isAttachedToWindow());
            observedBlockers.incrementAndGet();
            // Remove only this test-owned transparent input window, after the real input
            // snapshot observed it. Do not hide system UI or inject a preliminary/retry tap.
            blocker.postDelayed(() -> manager.removeViewImmediate(blocker), 600);
        };
        try {
            tap(next, () -> {
                assertEquals("waiter observed the native nonfocusable input blocker", 1, observedBlockers.get());
                assertFalse("blocker left before the single DOWN", blocker.isAttachedToWindow());
            });
            assertEquals("no native pointer was sent into the covering window", 0, blockedTouches.get());
            onMain(() -> assertEquals(1, activity.value("onboardingStep")));
            capture("ko-onboarding-landscape-after-input-overlay");
        } finally {
            onBlockedInput = null;
            onMain(() -> { if (blocker.isAttachedToWindow()) manager.removeViewImmediate(blocker); });
        }
    }

    private void tapAfterQueuedClampedScroll(View target) {
        reveal(target);
        // Like queued focus/rectangle requests, these native scrolls do not move the
        // button at the edge, but OverScroller is active and can intercept the first DOWN.
        ScrollView scroll = (ScrollView) activity.view("spotifySetupScrollView");
        AtomicInteger requests = new AtomicInteger();
        AtomicLong lastRequest = new AtomicLong();
        Runnable clampedScroll = () -> {
            scroll.smoothScrollBy(0, 0);
            requests.incrementAndGet();
            lastRequest.set(SystemClock.uptimeMillis());
        };
        onMain(() -> {
            for (int index = 0; index < 8; index++) scroll.postDelayed(clampedScroll, index * 300L);
        });
        try {
            // Assert BEFORE input: a coincidentally successful click must not hide a waiter
            // returning between 300ms requests or inside the stock 250ms scroll animation.
            tap(target, () -> {
                assertEquals("all queued native scrolls completed before the single DOWN", 8, requests.get());
                assertTrue("stock 250ms native scroll completed before DOWN",
                        SystemClock.uptimeMillis() - lastRequest.get() > 250);
            });
        } finally {
            onMain(() -> scroll.removeCallbacks(clampedScroll));
        }
    }

    @Test public void koreanLanguageChoicesPersistIndependently() throws Exception {
        launch("ko", 1f, false, false, "dark");
        showPrompt("ja", "fixture:ja");
        View root = promptRoot();
        List<Switch> toggles = switches(root);
        assertEquals(2, toggles.size());
        assertEquals(1, buttons(root).size());
        assertControls(root);
        assertHint(false);
        capture("ko-language-none-dark");
        tap(toggles.get(0));
        assertHint(false); // Google is translation-only and needs no pronunciation notice here.
        capture("ko-language-translation-dark");
        tap(button(root, ui("first_language.apply")));
        assertRule("ja", true, false);

        showPrompt("fr", "fixture:fr");
        root = promptRoot(); toggles = switches(root);
        tap(toggles.get(1));
        assertHint(true);
        assertEquals(ui("first_language.pronunciation_ai_provider_hint"),
                ((TextView) root.findViewWithTag("first-language-provider-hint")).getText().toString());
        capture("ko-language-pronunciation-dark");
        tap(button(root, ui("first_language.apply")));
        assertRule("fr", false, true);

        showPrompt("de", "fixture:de");
        root = promptRoot(); toggles = switches(root);
        tap(toggles.get(0)); tap(toggles.get(1));
        tap(button(root, ui("first_language.apply")));
        assertRule("de", true, true);

        showPrompt("ru", "fixture:ru");
        tap(button(promptRoot(), ui("first_language.original")));
        onMain(() -> {
            AiLyricsSettings reloaded = new AiLyricsSettings(activity.getApplicationContext());
            assertFalse(reloaded.shouldPromptForFirstLanguage("ru"));
            assertFalse(reloaded.snapshot().languageRules.get("ru").enabled());
            assertFalse(reloaded.snapshot().hasEnabledAiProvider());
            assertFalse(reloaded.snapshot().isAiProviderEnabled(AiLyricsSettings.Provider.LOCAL_AI_ID));
            reloaded.shutdown();
            assertEquals(4, activity.supplementRequests);
        });
        onMain(() -> {
            activity.seedTrack("ja", "fixture:ja-reopen");
            assertEquals(true, activity.call("showFirstLanguagePrompt", new Class<?>[]{String.class}, "ja"));
        });
        root = promptRoot();
        assertTrue(switches(root).get(0).isChecked());
        assertFalse(switches(root).get(1).isChecked());
        tap(switches(root).get(1));
        capture("ko-language-reopened-current-dark");
        tap(find(root, ImageButton.class).get(0));
        assertRule("ja", true, false); // Closing discards uncommitted edits.
        onMain(() -> assertEquals(5, activity.supplementRequests));
    }

    @Test public void languagePromptLandscapeLargeTextAndStaleSong() throws Exception {
        launch("de", 2f, false, true, "light");
        showPrompt("ja", "fixture:ja");
        View root = promptRoot();
        capture("de-language-landscape-font2-light-top");
        tap(switches(root).get(1));
        assertHint(true);
        assertControls(root);
        capture("de-language-landscape-font2-light-choices");
        Button apply = button(root, ui("first_language.apply"));
        reveal(apply);
        capture("de-language-landscape-font2-light-action");
        onMain(() -> activity.put("currentLyricsKey", "fixture:new-song"));
        tap(apply);
        onMain(() -> {
            assertFalse(settings().snapshot().languageRules.containsKey("ja"));
            assertEquals(0, activity.supplementRequests);
        });
    }

    @Test public void arabicRtlAndMissingTranslationProvider() throws Exception {
        launch("ar", 1.8f, false, false, "light");
        onMain(() -> settings().setGoogleTranslateEnabled(false));
        showPrompt("ja", "fixture:ja");
        View root = promptRoot();
        tap(switches(root).get(0));
        assertHint(true);
        assertEquals(ui("first_language.translation_provider_hint"),
                ((TextView) root.findViewWithTag("first-language-provider-hint")).getText().toString());
        assertControls(root);
        capture("ar-language-font1.8-light");
        reveal(button(root, ui("first_language.apply")));
        capture("ar-language-font1.8-light-action");
        tap(switches(root).get(0));
        assertHint(false);
        onMain(() -> ((AlertDialog) activity.value("firstLanguagePromptDialog")).cancel());
        onMain(() -> assertEquals(1, activity.supplementRequests));
    }

    private void launch(String locale, float scale, boolean onboarding, boolean landscape, String theme) {
        language = locale;
        AccessibilityServiceInfo service = instrumentation.getUiAutomation().getServiceInfo();
        service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        instrumentation.getUiAutomation().setServiceInfo(service);
        instrumentation.getUiAutomation().setRotation(landscape ? Surface.ROTATION_90 : Surface.ROTATION_0);
        FirstUseHarnessActivity.prepare(locale, scale);
        Intent intent = new Intent(instrumentation.getTargetContext(), FirstUseHarnessActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("language", locale).putExtra("theme", theme)
                .putExtra("onboarding", onboarding);
        activity = (FirstUseHarnessActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        SystemClock.sleep(400);
        awaitLayout(activity.getWindow().getDecorView());
        assertEquals(scale, activity.getResources().getConfiguration().fontScale, 0.01f);
        assertEquals("real Activity orientation", landscape ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT, activity.getResources().getConfiguration().orientation);
        Rect window = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        assertEquals("real native window aspect: " + window, landscape, window.width() > window.height());
    }

    private void showPrompt(String source, String key) {
        onMain(() -> {
            activity.seedTrack(source, key);
            assertEquals(true, activity.call("maybeShowFirstLanguagePrompt"));
        });
    }
    private View promptRoot() {
        AlertDialog dialog = (AlertDialog) activity.value("firstLanguagePromptDialog");
        assertNotNull(dialog);
        return dialog.getWindow().getDecorView();
    }
    private void assertHint(boolean visible) {
        onMain(() -> assertEquals(visible ? View.VISIBLE : View.GONE,
                promptRoot().findViewWithTag("first-language-provider-hint").getVisibility()));
    }
    private void assertRule(String source, boolean translation, boolean pronunciation) {
        onMain(() -> {
            AiLyricsSettings reloaded = new AiLyricsSettings(activity.getApplicationContext());
            AiLyricsSettings.LanguageRule rule = reloaded.snapshot().languageRules.get(source);
            assertNotNull(source, rule);
            assertEquals(translation, rule.translationEnabled);
            assertEquals(pronunciation, rule.pronunciationEnabled);
            assertFalse(reloaded.shouldPromptForFirstLanguage(source));
            reloaded.shutdown();
        });
    }
    private void assertDrafts() {
        onMain(() -> {
            assertEquals("fixture-client", ((EditText) activity.view("spotifySetupClientIdInput")).getText().toString());
            assertEquals("fixture-secret", ((EditText) activity.view("spotifySetupClientSecretInput")).getText().toString());
        });
    }
    private AiLyricsSettings settings() { return (AiLyricsSettings) activity.value("aiLyricsSettings"); }
    private void step(int phase) { activity.call("showOnboardingStep", new Class<?>[]{int.class}, phase); }
    private String ui(String key) { return AppI18n.t(language, key); }
    private void onMain(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        });
        instrumentation.waitForIdleSync();
        if (failure.get() instanceof Error) throw (Error) failure.get();
        if (failure.get() instanceof RuntimeException) throw (RuntimeException) failure.get();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private void reveal(View view) {
        awaitLayout(view);
        onMain(() -> {
            Rect desired = new Rect(0, 0, view.getWidth(), view.getHeight());
            desired.offset(view.getScrollX(), view.getScrollY());
            view.requestRectangleOnScreen(desired, true);
            // ScrollView's rectangle request can stop inside its padding. The production form
            // clips that padding to protect system bars, so reveal the actual native touch frame.
            android.view.ViewParent parent = view.getParent();
            while (parent instanceof View) {
                if (parent instanceof ScrollView) {
                    ScrollView scroll = (ScrollView) parent;
                    int[] target = new int[2], viewport = new int[2];
                    view.getLocationOnScreen(target);
                    scroll.getLocationOnScreen(viewport);
                    int top = viewport[1] + scroll.getPaddingTop();
                    int bottom = viewport[1] + scroll.getHeight() - scroll.getPaddingBottom();
                    int delta = target[1] + view.getHeight() > bottom
                            ? target[1] + view.getHeight() - bottom : target[1] < top ? target[1] - top : 0;
                    scroll.scrollBy(0, delta);
                }
                parent = ((View) parent).getParent();
            }
        });
        awaitLayout(view);
    }
    private void tap(View view) {
        tap(view, () -> {});
    }
    private void tap(View view, Runnable beforeDown) {
        reveal(view);
        TapPoint target = awaitReady(view, true);
        float x = target.x, y = target.y;
        android.util.Log.i("FirstUseUiRegression", "Single native tap at " + x + "," + y
                + ", hit=" + target.hit + ", target=" + view + ", step=" + activity.value("onboardingStep"));
        onMain(beforeDown);
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        instrumentation.sendPointerSync(down);
        SystemClock.sleep(60);
        MotionEvent up = MotionEvent.obtain(time, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        instrumentation.sendPointerSync(up);
        down.recycle(); up.recycle(); instrumentation.waitForIdleSync();
        AlertDialog dialog = (AlertDialog) activity.value("firstLanguagePromptDialog");
        awaitLayout(dialog == null ? activity.getWindow().getDecorView() : dialog.getWindow().getDecorView());
    }

    private void awaitLayout(View view) { awaitReady(view, false); }

    private TapPoint awaitReady(View view, boolean checkInput) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        Rect previous = new Rect();
        AtomicLong lastDraw = new AtomicLong();
        AtomicInteger draws = new AtomicInteger();
        ViewTreeObserver[] observer = new ViewTreeObserver[1];
        ViewTreeObserver.OnDrawListener draw = () -> {
            lastDraw.set(SystemClock.uptimeMillis());
            draws.incrementAndGet();
        };
        onMain(() -> {
            observer[0] = view.getViewTreeObserver();
            observer[0].addOnDrawListener(draw);
            view.getRootView().invalidate(); // Require a real frame, including after root rebuilds.
        });
        int stable = 0;
        long inputReadySince = 0;
        String inputState = "not sampled", previousInputState = "";
        try {
            while (SystemClock.uptimeMillis() < deadline) {
                acknowledgeImmersiveEducation();
                awaitUiFrame(view);
                Rect bounds = new Rect();
                TapPoint target = new TapPoint();
                final boolean[] ready = {false};
                onMain(() -> {
                    int[] xy = new int[2]; view.getLocationOnScreen(xy);
                    bounds.set(xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
                    ready[0] = view.isAttachedToWindow() && view.isShown() && view.isLaidOut()
                            && view.hasWindowFocus() && view.getWidth() > 0 && view.getHeight() > 0;
                    for (View ancestor = view; ancestor != null;
                            ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null) {
                        ready[0] &= !ancestor.isLayoutRequested();
                    }
                    if (checkInput && ready[0]) {
                        assertTrue("enabled native target: " + view, view.isEnabled());
                        assertTrue("48dp target: " + view, view.getWidth() >= dp(48) && view.getHeight() >= dp(48));
                        ready[0] = view.getLocalVisibleRect(target.hit);
                        target.hit.offset(-view.getScrollX(), -view.getScrollY());
                        ready[0] &= target.hit.intersect(0, 0, view.getWidth(), view.getHeight());
                        ready[0] &= target.hit.width() >= dp(48) && target.hit.height() >= dp(48);
                        target.x = xy[0] + target.hit.exactCenterX(); target.y = xy[1] + target.hit.exactCenterY();
                        Rect window = new Rect(); view.getWindowVisibleDisplayFrame(window);
                        ready[0] &= window.contains(Math.round(target.x), Math.round(target.y));
                        Rect metrics = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
                        target.width = metrics.width(); target.height = metrics.height();
                        target.title = ((WindowManager.LayoutParams) view.getRootView().getLayoutParams()).getTitle().toString();
                    }
                });
                stable = ready[0] && previous.equals(bounds) ? stable + 1 : 0;
                boolean inputReady = !checkInput;
                if (checkInput && ready[0]) {
                    NativeInputWindowState input = NativeInputWindowState.parse(readInputDump(), view.getDisplay().getDisplayId());
                    int uid = activity.getApplicationInfo().uid;
                    float halfTarget = dp(48) / 2f - 0.5f;
                    inputReady = input.width == target.width && input.height == target.height
                            && input.targets(target.x, target.y, uid, target.title);
                    for (float dx : new float[]{-halfTarget, halfTarget}) {
                        for (float dy : new float[]{-halfTarget, halfTarget}) {
                            inputReady &= input.targets(target.x + dx, target.y + dy, uid, target.title);
                        }
                    }
                    inputState = input.width + "x" + input.height + " @" + target.x + "," + target.y
                            + " -> " + input.targetAt(target.x, target.y);
                    if (!inputState.equals(previousInputState)) {
                        android.util.Log.i("FirstUseUiRegression", "Native input " + (inputReady ? "ready: " : "blocked: ") + inputState);
                        inputReadySince = 0;
                    }
                    previousInputState = inputState;
                    if (!inputReady && onBlockedInput != null) {
                        Runnable observed = onBlockedInput;
                        onBlockedInput = null;
                        onMain(observed);
                    }
                }
                if (!inputReady || !ready[0] || !previous.equals(bounds)) inputReadySince = 0;
                else if (inputReadySince == 0) inputReadySince = SystemClock.uptimeMillis();
                long now = SystemClock.uptimeMillis();
                if (stable >= 3 && lastDraw.get() > 0 && now - lastDraw.get() >= DRAW_QUIET_MS
                        && inputReady && (!checkInput || now - inputReadySince >= DRAW_QUIET_MS)) {
                    android.util.Log.i("FirstUseUiRegression", "Native settled: frames=" + draws.get()
                            + ", draw quiet=" + (now - lastDraw.get()) + "ms, input=" + inputState);
                    return target;
                }
                previous.set(bounds);
                SystemClock.sleep(80);
            }
            fail("Native layout/drawing/input did not settle: " + view + " at " + previous
                    + ", stable=" + stable + ", last draw age=" + (SystemClock.uptimeMillis() - lastDraw.get())
                    + ", input=" + inputState);
            throw new AssertionError("unreachable");
        } finally {
            onMain(() -> {
                if (observer[0].isAlive()) observer[0].removeOnDrawListener(draw);
            });
        }
    }

    private void awaitUiFrame(View view) {
        CountDownLatch traversed = new CountDownLatch(1);
        onMain(() -> view.postOnAnimation(() -> view.post(traversed::countDown)));
        try {
            assertTrue("native UI frame/traversal completed", traversed.await(3, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new AssertionError(error);
        }
        instrumentation.waitForIdleSync();
    }

    private String readInputDump() {
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand("dumpsys input"));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        } catch (java.io.IOException error) { throw new AssertionError("Native input snapshot", error); }
    }

    private static final class TapPoint {
        final Rect hit = new Rect();
        float x, y;
        int width, height;
        String title;
    }

    private void acknowledgeImmersiveEducation() {
        // Android can show this after the first layout or after rotation. Match only its known
        // education view; never click unrelated system dialogs or app actions to obtain focus.
        for (android.view.accessibility.AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) {
                for (String namespace : new String[]{"android", "com.android.systemui"}) {
                    if (!root.findAccessibilityNodeInfosByViewId(namespace + ":id/immersive_cling_description").isEmpty()) {
                        for (AccessibilityNodeInfo ok : root.findAccessibilityNodeInfosByViewId(namespace + ":id/ok")) {
                            ok.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            ok.recycle();
                        }
                    }
                }
                root.recycle();
            }
            window.recycle();
        }
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private void assertControls(View root) {
        onMain(() -> inspect(root));
    }
    private void inspect(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof Button || view instanceof EditText || view instanceof ImageButton) {
            assertTrue("minimum height: " + view, view.getHeight() >= dp(48));
            assertTrue("minimum width: " + view, view.getWidth() >= dp(48));
        }
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView) view;
            if (text.getLayout() != null) {
                for (int line = 0; line < text.getLayout().getLineCount(); line++) {
                    assertEquals("untruncated: " + text.getText(), 0, text.getLayout().getEllipsisCount(line));
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View child = group.getChildAt(index);
                if (child.getVisibility() != View.VISIBLE) continue;
                assertTrue("no horizontal overflow: " + child, child.getLeft() >= 0 && child.getRight() <= group.getWidth());
                inspect(child);
            }
        }
    }

    private static <T extends View> List<T> find(View root, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root) && root.getVisibility() == View.VISIBLE) result.add(type.cast(root));
        if (root instanceof ViewGroup && root.getVisibility() == View.VISIBLE) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) result.addAll(find(group.getChildAt(index), type));
        }
        return result;
    }
    private static List<Switch> switches(View root) { return find(root, Switch.class); }
    private static List<Button> buttons(View root) {
        List<Button> buttons = find(root, Button.class);
        buttons.removeIf(value -> value instanceof Switch);
        return buttons;
    }
    private static Button button(View root, String text) {
        for (Button candidate : buttons(root)) if (text.contentEquals(candidate.getText())) return candidate;
        throw new AssertionError("Missing button: " + text);
    }

    private void capture(String name) throws Exception {
        AlertDialog dialog = (AlertDialog) activity.value("firstLanguagePromptDialog");
        awaitLayout(dialog == null ? activity.getWindow().getDecorView() : dialog.getWindow().getDecorView());
        instrumentation.waitForIdleSync();
        SystemClock.sleep(300);
        saveNativeScreenshot(name);
    }

    private void saveNativeScreenshot(String name) throws Exception {
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("native window screenshot", bitmap);
        File directory = new File(activity.getExternalFilesDir(null), "first-use-screenshots");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File temporary = new File(directory, name + ".png.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        assertTrue("publish complete screenshot", temporary.renameTo(new File(directory, name + ".png")));
        bitmap.recycle();
    }
}
