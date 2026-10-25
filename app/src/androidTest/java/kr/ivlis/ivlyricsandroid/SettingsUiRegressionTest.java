package kr.ivlis.ivlyricsandroid;

import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Measured/drawn production Android controls, not HTML or SDK-free widget mocks. */
@android.annotation.TargetApi(30)
public final class SettingsUiRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private SettingsUiHarnessActivity activity;
    private int captures;
    private int genuineWindowCaptures;
    private int taps;
    private String capturePrefix = "startup";
    private boolean expectedLandscape;

    @After public void cleanup() {
        if (activity != null) {
            // Capture before dismiss/finish, even if a strict assertion failed.
            try { screenshot(capturePrefix + "-window-final-or-failure"); }
            catch (RuntimeException | AssertionError captureFailure) {
                android.util.Log.e("SettingsUiRegression", "Final evidence capture failed", captureFailure);
            }
        }
        if (activity != null) instrumentation.runOnMainSync(() -> { activity.dismissPopup(); activity.finish(); });
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_UNFREEZE);
    }

    @Test public void koreanSmallPortrait() throws Exception { scenario("ko", 1f, 320, 640, false); }
    @Test public void koreanLandscapeLargeText() throws Exception { scenario("ko", 1.6f, 568, 320, true); }
    @Test public void englishNarrowLargeText() throws Exception { scenario("en", 1.8f, 280, 600, false); }
    @Test public void englishNarrowLandscapeLargeText() throws Exception { scenario("en", 1.8f, 280, 320, true); }
    @Test public void germanLandscapeLargeText() throws Exception { scenario("de", 2f, 568, 320, true); }
    @Test public void arabicRtlLargeText() throws Exception { scenario("ar", 1.6f, 320, 640, false); }

    @Test public void standaloneLyricsSettings() { lyricsSettingsScenario(false, false, 1f); }
    @Test public void standaloneLyricsSettingsLargeText() { lyricsSettingsScenario(false, false, 2f); }
    @Test public void standaloneLandscapeSettingsLargeText() { lyricsSettingsScenario(false, true, 2f); }
    @Test public void embeddedLyricsSettingsLargeText() { lyricsSettingsScenario(true, false, 2f); }
    @Test public void embeddedLandscapeSettingsLargeText() { lyricsSettingsScenario(true, true, 2f); }

    private void lyricsSettingsScenario(boolean embedded, boolean landscape, float fontScale) {
        capturePrefix = "lyrics-settings-" + embedded + "-" + landscape + "-" + fontScale;
        launch("ko", fontScale, landscape, embedded);
        final View[] settings = new View[1];
        onMain(() -> {
            activity.view("settingsPanel").setVisibility(View.GONE);
            activity.call("updateTrackMetadataTextViews", new Class<?>[]{TrackSnapshot.class}, activity.value("currentTrack"));
            // Populate the production renderers without invoking repository/host publishing.
            LyricsResult lyrics = (LyricsResult) activity.value("currentLyricsResult");
            ((LyricsView) activity.view("lyricsView")).setResult(lyrics);
            if (activity.view("landscapeLyricsView") != null) {
                ((LyricsView) activity.view("landscapeLyricsView")).setResult(lyrics);
            }
            activity.call("markLyricsMetaTipShown");
            activity.call("showLyricsPage", new Class<?>[]{boolean.class}, true);
            ((TextView) activity.view("lyricsTitleView")).setText("Long title 日本語 한국어 that must not hide the settings action");
            if (fontScale > 1f) {
                activity.call("setLyricsSupplementLoading", new Class<?>[]{boolean.class, boolean.class}, true, true);
            }
            settings[0] = landscape && !embedded ? activity.view("landscapeMenuButton")
                    : activity.view("lyricsPage").findViewWithTag("lyrics.settings");
            assertNotNull("settings exists on the actual lyrics destination", settings[0]);
        });
        tap(settings[0], landscape && !embedded ? 44 : 48);
        onMain(() -> {
            assertEquals(View.VISIBLE, activity.view("settingsPanel").getVisibility());
            assertEquals("home", activity.value("settingsRoute"));
        });
        tap(activity.view("settingsNavBackButton"));
        long deadline = SystemClock.uptimeMillis() + 5000;
        final boolean[] closed = new boolean[1];
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(() -> closed[0] = activity.view("settingsPanel").getVisibility() == View.GONE);
            if (closed[0]) break;
            SystemClock.sleep(50);
        }
        onMain(() -> {
            assertEquals("settings closed", View.GONE, activity.view("settingsPanel").getVisibility());
            assertTrue("same settings action is still on screen", settings[0].isShown());
            if (embedded || !landscape) {
                assertEquals("lyrics page retained", Boolean.TRUE, activity.value("lyricsPageVisible"));
                assertEquals(View.VISIBLE, activity.view("lyricsPage").getVisibility());
            }
        });
        screenshot(capturePrefix + "-returned-to-lyrics");
    }

    @Test public void homeBrandingLightAndDark() {
        capturePrefix = "branding-home";
        launch("ko", 1f, false);
        onMain(() -> activity.settings("home"));
        for (int index = 0; index < 2; index++) {
            awaitWindow(activity.getWindow().getDecorView());
            onMain(() -> assertBrandHeading("ko"));
            boolean dark = ((SettingsKit) activity.value("settingsKit")).p.dark;
            screenshot("branding-home-" + (dark ? "dark" : "light") + "-window");
            tap(activity.view("settingsThemeButton"));
            awaitWindow(activity.getWindow().getDecorView());
            onMain(() -> assertEquals("native theme action rebuilds the settings home", !dark,
                    ((SettingsKit) activity.value("settingsKit")).p.dark));
        }
        onMain(() -> {
            activity.settings("general");
            assertEquals(AppI18n.t("ko", "tab.general"),
                    ((TextView) activity.view("settingsNavTitle")).getText().toString());
            assertEquals(AppI18n.t("ko", "settings.back"),
                    activity.view("settingsNavBackButton").getContentDescription());
        });
        tap(activity.view("settingsNavBackButton"));
        awaitWindow(activity.getWindow().getDecorView());
        onMain(() -> assertBrandHeading("ko"));
        tap(activity.view("settingsNavBackButton"));
        awaitWindow(activity.getWindow().getDecorView());
        onMain(() -> assertEquals("native close action still closes settings", View.GONE,
                activity.view("settingsPanel").getVisibility()));
    }

    private void launch(String language, float fontScale, boolean landscape) {
        launch(language, fontScale, landscape, true);
    }

    private void launch(String language, float fontScale, boolean landscape, boolean embedded) {
        expectedLandscape = landscape;
        instrumentation.getUiAutomation().setRotation(landscape ? Surface.ROTATION_90 : Surface.ROTATION_0);
        instrumentation.waitForIdleSync();
        SettingsUiHarnessActivity.prepareConfiguration(language, fontScale);
        Intent intent = new Intent(instrumentation.getTargetContext(), SettingsUiHarnessActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("uiLanguage", language).putExtra("fontScale", fontScale)
                .putExtra("uiEmbedded", embedded);
        activity = (SettingsUiHarnessActivity) instrumentation.startActivitySync(intent);
        // Request after launch too: a finishing prior Activity/launcher must not win
        // the rotation transition before this fixture owns its native window.
        assertTrue("native scenario rotation accepted", instrumentation.getUiAutomation()
                .setRotation(landscape ? Surface.ROTATION_90 : Surface.ROTATION_0));
        instrumentation.waitForIdleSync();
        awaitWindow(activity.getWindow().getDecorView());
        assertEquals(fontScale, activity.getResources().getConfiguration().fontScale, 0.01f);
    }

    private void scenario(String language, float fontScale, int width, int height, boolean landscape) throws Exception {
        launch(language, fontScale, landscape);
        final String prefix = language + "-" + width + "x" + height + "-font" + fontScale;
        capturePrefix = prefix;
        screenshot(prefix + "-window-initial");
        verifyRealWindowTaps(prefix, language);
        onMain(() -> {
            assertFalse(((AiLyricsSettings) activity.value("aiLyricsSettings")).snapshot().hasApiKey());
            assertFalse(activity.usesLivePlayback());
        });
        // "home" is the menu list with the search field; the others are the seven settings menus.
        for (String tab : new String[]{"home", "general", "ai", "appearance", "lyrics", "player", "providers", "system"}) {
            onMain(() -> activity.settings(tab));
            screenshot(prefix + "-window-settings-" + tab);
            onMain(() -> {
                View panel = activity.view("settingsPanel");
                int oldWidth = panel.getWidth(), oldHeight = panel.getHeight();
                render(panel, width, height);
                save(panel, prefix + "-settings-" + tab);
                assertControls(panel);
                if ("home".equals(tab)) assertBrandHeading(language);
                assertMinimum(activity.view("settingsSearchInput"), "settings search");
                assertChevron((TextView) activity.view("uiLanguageSelectButton"));
                assertChevron((TextView) activity.view("outputLanguageSelectButton"));
                if ("general".equals(tab)) {
                    // Pronunciation notation is now a set of radio cards instead of a drop-down.
                    for (View card : ((SettingsKit.Choice) activity.value("pronunciationNotationChoice")).items) {
                        assertMinimum(card, "pronunciation notation card");
                    }
                }
                ScrollView scroll = (ScrollView) activity.value("settingsScrollView");
                assertTrue("landscape body remains scrollable", scroll.getHeight() >= dp(80));
                if ("ai".equals(tab)) {
                    View providers = activity.view("providerButtonsContainer");
                    assertTrue("AI provider rank badges checked", assertCenteredProviderBadges(providers, dp(24)) > 0);
                    assertTrue("AI connection initials checked", assertCenteredProviderBadges(
                            activity.view("aiConnectionsContainer"), dp(34)) > 0);
                    int[] child = new int[2], parent = new int[2];
                    providers.getLocationInWindow(child); scroll.getChildAt(0).getLocationInWindow(parent);
                    scroll.scrollTo(0, Math.max(0, child[1] - parent[1]));
                    save(panel, prefix + "-settings-ai-providers");
                }
                if ("providers".equals(tab)) {
                    assertTrue("lyrics provider rank badges checked", assertCenteredProviderBadges(
                            activity.view("lyricsProviderSettingsContainer"), dp(24)) > 0);
                }
                restore(panel, oldWidth, oldHeight);
            });
        }
        for (String tab : new String[]{"language", "video", "sync", "background", "lrclib"}) {
            final View[] content = new View[1];
            onMain(() -> content[0] = activity.popup(tab));
            instrumentation.waitForIdleSync();
            screenshot(prefix + "-window-track-" + tab);
            onMain(() -> {
                int oldWidth = content[0].getWidth(), oldHeight = content[0].getHeight();
                assertControls(content[0]);
                // Same native PopupWindow tree, also measured at a deterministic narrow width.
                render(content[0], Math.min(width - 16, landscape ? 480 : 430), height - 24);
                save(content[0], prefix + "-track-" + tab);
                assertControls(content[0]);
                LinearLayout tabs = (LinearLayout) activity.value("lyricsPopupTabButtonsContainer");
                for (int index = 0; index < tabs.getChildCount(); index++) assertMinimum(tabs.getChildAt(index), "popup tab");
                if ("language".equals(tab)) {
                    assertChevron((TextView) activity.view("sourceLanguageSelectButton"));
                    assertMinimum(activity.view("lyricsRegenerateButton"), "regenerate translation/pronunciation");
                    assertTrue(activity.view("lyricsRegenerateButton").isEnabled());
                    activity.call("setLyricsSupplementLoading", new Class<?>[]{boolean.class, boolean.class}, true, true);
                    assertFalse("cannot regenerate twice", activity.view("lyricsRegenerateButton").isEnabled());
                    save(content[0], prefix + "-track-language-loading");
                    activity.call("setLyricsSupplementLoading", new Class<?>[]{boolean.class, boolean.class}, false, false);
                    assertTrue(activity.view("lyricsRegenerateButton").isEnabled());
                    activity.put("currentBaseLyricsResult", null);
                    activity.call("updateLyricsRegenerateActions");
                    assertFalse("no lyrics is not an available action", activity.view("lyricsRegenerateButton").isEnabled());
                    activity.seedTrack();
                }
                restore(content[0], oldWidth, oldHeight);
            });
            onMain(activity::dismissPopup);
        }
        onMain(() -> {
            // Search lives on the settings home (menu list).
            activity.settings("home");
            EditText search = (EditText) activity.view("settingsSearchInput");
            search.setText(AppI18n.t(language, "setting.metadata_translation"));
            View panel = activity.view("settingsPanel");
            int oldWidth = panel.getWidth(), oldHeight = panel.getHeight();
            render(activity.view("settingsPanel"), width, height);
            save(activity.view("settingsPanel"), prefix + "-settings-search-results");
            ViewGroup results = (ViewGroup) activity.view("settingsSearchResults");
            assertTrue("search finds real settings", results.getChildCount() > 0);
            assertControls(results);
            search.setText("");
            restore(panel, oldWidth, oldHeight);
        });
        // The component metrics above temporarily relayout the production tree.
        // Let its real window restore, then enter the editor through native touch.
        // requestFocus/showSoftInput in the restoration pass races ViewRoot's
        // input connection (PHASE_CLIENT_VIEW_SERVED on the emulator).
        EditText search = (EditText) activity.view("settingsSearchInput");
        tap(search);
        awaitInputConnection(search);
        onMain(() -> {
            InputMethodManager input = activity.getSystemService(InputMethodManager.class);
            boolean requested = input.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            android.util.Log.i("SettingsUiRegression", "Served search IME request accepted=" + requested);
        });
        awaitKeyboard(search);
        onMain(() -> {
            assertMinimum(activity.view("settingsSearchInput"), "search with IME");
            ScrollView scroll = (ScrollView) activity.value("settingsScrollView");
            assertTrue("IME leaves an accessible scroll viewport", scroll.getHeight() > 0);
        });
        screenshot(prefix + "-window-settings-keyboard");
        assertTrue("screenshots were written", captures >= 12);
        assertTrue("genuine emulator-window screenshots are required for visual acceptance", genuineWindowCaptures >= 8);
    }

    private void awaitInputConnection(EditText search) {
        long deadline = SystemClock.uptimeMillis() + 4000;
        String lastState = "";
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] ready = new boolean[1];
            final String[] state = new String[1];
            onMain(() -> {
                InputMethodManager input = activity.getSystemService(InputMethodManager.class);
                boolean active = input.isActive(search), accepting = input.isAcceptingText();
                ready[0] = search.isAttachedToWindow() && search.hasWindowFocus() && search.hasFocus()
                        && active && accepting && !search.isLayoutRequested();
                state[0] = "attached=" + search.isAttachedToWindow() + ", windowFocused=" + search.hasWindowFocus()
                        + ", editorFocused=" + search.hasFocus() + ", active=" + active
                        + ", acceptingText=" + accepting + ", layoutRequested=" + search.isLayoutRequested();
            });
            if (!state[0].equals(lastState)) android.util.Log.i("SettingsUiRegression", "Search input connection: " + state[0]);
            lastState = state[0];
            if (ready[0]) return;
            SystemClock.sleep(50);
        }
        screenshot(capturePrefix + "-window-search-input-connection-failure");
        fail("Native search tap did not establish an input connection: " + lastState);
    }

    private void awaitKeyboard(EditText search) {
        long deadline = SystemClock.uptimeMillis() + 4000;
        String lastState = "";
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] visible = new boolean[1];
            final String[] state = new String[1];
            onMain(() -> {
                WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
                visible[0] = insets != null && insets.isVisible(WindowInsets.Type.ime());
                InputMethodManager input = activity.getSystemService(InputMethodManager.class);
                state[0] = "imeVisible=" + visible[0] + ", imeInsets="
                        + (insets == null ? "none" : insets.getInsets(WindowInsets.Type.ime()))
                        + ", editorFocused=" + search.hasFocus() + ", windowFocused=" + search.hasWindowFocus()
                        + ", active=" + input.isActive(search) + ", acceptingText=" + input.isAcceptingText();
            });
            if (!state[0].equals(lastState)) android.util.Log.i("SettingsUiRegression", "Keyboard settling: " + state[0]);
            lastState = state[0];
            if (visible[0]) return;
            SystemClock.sleep(50);
        }
        screenshot(capturePrefix + "-window-keyboard-visibility-failure");
        fail("Software keyboard did not appear; run emulator with a software IME enabled: " + lastState);
    }

    private void verifyRealWindowTaps(String prefix, String language) {
        onMain(() -> activity.settings("general"));
        Switch metadata = (Switch) activity.view("metadataTranslationSwitch");
        boolean before = metadata.isChecked();
        tap(metadata);
        onMain(() -> {
            assertEquals("native switch changed through touch", !before, metadata.isChecked());
            assertEquals("production settings listener saved toggle", !before,
                    ((AiLyricsSettings) activity.value("aiLyricsSettings")).snapshot().metadataTranslationEnabled);
            assertThumb(metadata);
        });
        screenshot(prefix + "-window-general-toggled");

        onMain(() -> activity.popup("language"));
        Switch translation = (Switch) activity.view("languageTranslationSwitch");
        boolean translationBefore = translation.isChecked();
        tap(translation);
        onMain(() -> {
            assertEquals("popup switch changed through touch", !translationBefore, translation.isChecked());
            assertEquals("production rule listener saved toggle", !translationBefore,
                    ((AiLyricsSettings) activity.value("aiLyricsSettings")).snapshot().ruleForSource("ja").translationEnabled);
            activity.call("setLyricsSupplementLoading", new Class<?>[]{boolean.class, boolean.class}, false, false);
        });
        int supplementsBefore = activity.supplementActions;
        tap(activity.view("lyricsRegenerateButton"));
        onMain(() -> {
            assertEquals("real regenerate click reaches the offline generation boundary", supplementsBefore + 1, activity.supplementActions);
            assertFalse("real click disables duplicate regeneration", activity.view("lyricsRegenerateButton").isEnabled());
        });
        screenshot(prefix + "-window-regenerate-tapped-loading");
        onMain(() -> {
            activity.call("setLyricsSupplementLoading", new Class<?>[]{boolean.class, boolean.class}, false, false);
            activity.dismissPopup();
            activity.popup("video");
        });
        View video = findButton(activity.view("videoSyncSettingsContent"), AppI18n.t(language, "video.choose"));
        assertNotNull("production video selector", video);
        int videoBefore = activity.videoActions;
        tap(video);
        assertEquals("video click reaches offline selector boundary", videoBefore + 1, activity.videoActions);
        screenshot(prefix + "-window-video-tapped");
        onMain(() -> { activity.dismissPopup(); activity.popup("lrclib"); });
        View search = findButton(activity.view("lyricsManualSearchContent"), AppI18n.t(language, "lyrics.lrclib_search.button"));
        assertNotNull("production LRCLIB search action", search);
        int searchBefore = activity.searchActions;
        tap(search);
        assertEquals("LRCLIB real click is offline", searchBefore + 1, activity.searchActions);
        onMain(activity::dismissPopup);
    }

    private View findButton(View root, String text) {
        if (root instanceof Button && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findButton(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void tap(View target) {
        tap(target, 48);
    }

    private void tap(View target, int minimumDp) {
        awaitWindow(target.getRootView());
        onMain(() -> {
            assertTrue("real target enabled", target.isEnabled());
            Rect desired = new Rect(0, 0, target.getWidth(), target.getHeight());
            // requestRectangleOnScreen takes content coordinates, not the view's
            // touch frame. Native EditText can scroll its text horizontally in RTL.
            desired.offset(target.getScrollX(), target.getScrollY());
            if (target instanceof Switch) {
                drawSwitch((Switch) target);
                desired.set(((Switch) target).getThumbDrawable().getBounds());
                desired.inset(-dp(16), -dp(16));
            }
            target.requestRectangleOnScreen(desired, true);
        });
        screenshot(capturePrefix + "-window-before-tap-" + (++taps));
        final float[] point = new float[2];
        onMain(() -> {
            if (target instanceof EditText) assertEditorHitFrame((EditText) target);
            Rect visible = visibleHitFrame(target);
            assertTrue("actual visible hit height >=" + minimumDp + "dp: " + visible, visible.height() >= dp(minimumDp));
            assertTrue("actual visible hit width >=" + minimumDp + "dp: " + visible, visible.width() >= dp(minimumDp));
            int[] position = new int[2]; target.getLocationOnScreen(position);
            point[0] = position[0] + visible.exactCenterX(); point[1] = position[1] + visible.exactCenterY();
            if (target instanceof Switch) {
                drawSwitch((Switch) target);
                Rect thumb = ((Switch) target).getThumbDrawable().getBounds();
                assertTrue("actual thumb is visible for the native tap: " + thumb + " in " + visible,
                        visible.contains(Math.round(thumb.exactCenterX()), Math.round(thumb.exactCenterY())));
                point[0] = position[0] + thumb.exactCenterX();
                point[1] = position[1] + thumb.exactCenterY();
            }
            Rect window = new Rect(); target.getRootView().getWindowVisibleDisplayFrame(window);
            android.util.Log.i("SettingsUiRegression", "Native hit frame=" + visible + ", origin="
                    + position[0] + "," + position[1] + ", scroll=" + target.getScrollX() + "," + target.getScrollY()
                    + ", point=" + point[0] + "," + point[1] + ", window=" + window);
            assertTrue("touch center " + point[0] + "," + point[1] + " inside real visible window: " + window,
                    window.contains(Math.round(point[0]), Math.round(point[1])));
            View root = target.getRootView(); int[] origin = new int[2]; root.getLocationOnScreen(origin);
            Rect actualWindow = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
            assertTrue("actual popup/window not wider than current window: " + root.getWidth() + " > " + actualWindow,
                    root.getWidth() <= actualWindow.width());
            assertTrue("actual touch target inside window content", point[0] >= origin[0] && point[0] < origin[0] + root.getWidth()
                    && point[1] >= origin[1] && point[1] < origin[1] + root.getHeight());
        });
        long now = SystemClock.uptimeMillis();
        android.util.Log.i("SettingsUiRegression", "Injecting native touch " + point[0] + "," + point[1]
                + "; active=" + activeWindowOwner());
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, point[0], point[1], 0);
        instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up);
        down.recycle(); up.recycle(); instrumentation.waitForIdleSync();
    }

    private Rect visibleHitFrame(View target) {
        Rect visible = new Rect();
        assertTrue("real-window target visible", target.getLocalVisibleRect(visible));
        // getLocalVisibleRect returns scroll-adjusted content coordinates. Undo
        // only this view's own scroll; keep every ancestor's actual clipping.
        visible.offset(-target.getScrollX(), -target.getScrollY());
        assertTrue("visible native touch frame", visible.intersect(0, 0, target.getWidth(), target.getHeight()));
        return visible;
    }

    private void assertEditorHitFrame(EditText editor) {
        Rect frame = visibleHitFrame(editor);
        int scrollX = editor.getScrollX(), scrollY = editor.getScrollY();
        try {
            for (int contentScroll : new int[]{-dp(400), dp(400)}) {
                editor.scrollTo(contentScroll, scrollY);
                assertEquals("native editor content scroll must not move its clipped hit frame",
                        frame, visibleHitFrame(editor));
            }
        } finally { editor.scrollTo(scrollX, scrollY); }
    }

    private void awaitWindow(View root) {
        AccessibilityServiceInfo service = instrumentation.getUiAutomation().getServiceInfo();
        service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        instrumentation.getUiAutomation().setServiceInfo(service);
        // Cold emulator focus delivery can itself take several seconds. The same
        // focus/size stability rules apply; allow startup IPC time, not bad bounds.
        long deadline = SystemClock.uptimeMillis() + 20000;
        int stable = 0, previousWidth = 0, previousHeight = 0;
        String lastState = "";
        while (SystemClock.uptimeMillis() < deadline) {
            // Android's first immersive-mode education is a system overlay, not a
            // production app control. Acknowledge only its specific "Got it" node.
            for (android.view.accessibility.AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
                AccessibilityNodeInfo node = window.getRoot();
                if (node == null) continue;
                for (String namespace : new String[]{"android", "com.android.systemui"}) {
                    java.util.List<AccessibilityNodeInfo> education = node.findAccessibilityNodeInfosByViewId(
                            namespace + ":id/immersive_cling_description");
                    if (!education.isEmpty()) {
                        for (AccessibilityNodeInfo button : node.findAccessibilityNodeInfosByViewId(namespace + ":id/ok")) {
                            boolean acknowledged = button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            android.util.Log.i("SettingsUiRegression", "Immersive education acknowledgement: "
                                    + namespace + ", accepted=" + acknowledged);
                            button.recycle();
                        }
                        stable = 0;
                    }
                    for (AccessibilityNodeInfo description : education) description.recycle();
                }
                node.recycle(); window.recycle();
            }
            final int[] size = new int[2];
            final boolean[] ready = new boolean[1];
            final String[] state = new String[1];
            onMain(() -> {
                size[0] = root.getWidth(); size[1] = root.getHeight();
                Rect actual = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
                ready[0] = root.hasWindowFocus() && size[0] > 0 && size[1] > 0 && size[0] <= actual.width()
                        && (actual.width() > actual.height()) == expectedLandscape
                        && activity.getResources().getConfiguration().orientation == (expectedLandscape
                                ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT);
                state[0] = "focused=" + root.hasWindowFocus() + ", root=" + size[0] + "x" + size[1]
                        + ", activityDecorFocused=" + activity.getWindow().getDecorView().hasWindowFocus()
                        + ", currentWindow=" + actual + ", resources="
                        + activity.getResources().getDisplayMetrics().widthPixels + "x"
                        + activity.getResources().getDisplayMetrics().heightPixels;
            });
            state[0] += ", active=" + activeWindowOwner();
            if (!state[0].equals(lastState)) android.util.Log.i("SettingsUiRegression", "Window settling: " + state[0]);
            lastState = state[0];
            if (ready[0] && size[0] == previousWidth && size[1] == previousHeight) stable++; else stable = 0;
            if (stable >= 15) return;
            previousWidth = size[0]; previousHeight = size[1]; SystemClock.sleep(100);
        }
        screenshot("unsettled-window-" + SystemClock.uptimeMillis());
        fail("Native window did not settle/focus before touch injection: " + lastState);
    }

    private String activeWindowOwner() {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null) return "none";
        String owner = String.valueOf(root.getPackageName()) + "/" + root.getClassName() + "#" + root.getWindowId();
        root.recycle();
        return owner;
    }

    private void render(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(dp(width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(height), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private void restore(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        root.requestLayout();
    }

    private void screenshot(String name) {
        // Always save the actual native window tree before strict assertions. This
        // is diagnostic software rasterization, NOT a GPU screenshot acceptance.
        onMain(() -> {
            android.widget.PopupWindow popup = (android.widget.PopupWindow) activity.value("lyricsMetaMenuPopup");
            View root = popup != null && popup.isShowing() ? popup.getContentView().getRootView()
                    : activity.getWindow().getDecorView();
            if (root.getWidth() > 0 && root.getHeight() > 0) save(root, name + "-software-root-diagnostic");
        });
        Bitmap window = instrumentation.getUiAutomation().takeScreenshot();
        if (window == null) {
            android.util.Log.w("SettingsUiRegression", "GPU screenshot unavailable; software diagnostic saved for " + name);
            return;
        }
        write(window, name); window.recycle(); genuineWindowCaptures++;
    }

    private void assertControls(View root) {
        if (root.getVisibility() != View.VISIBLE) return;
        if (root instanceof Button || root instanceof EditText || root instanceof android.widget.CompoundButton) {
            assertMinimum(root, root.getClass().getSimpleName() + " " + ((TextView) root).getText());
            TextView text = (TextView) root;
            Layout layout = text.getLayout();
            assertNotNull("text laid out", layout);
            if (!(root instanceof EditText)) {
                for (int line = 0; line < layout.getLineCount(); line++) assertEquals("no clipped ellipsis: " + text.getText(), 0, layout.getEllipsisCount(line));
                assertTrue("text height fits: " + text.getText(), layout.getHeight()
                        <= root.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + dp(1));
            }
            if (root instanceof Switch) assertThumb((Switch) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) assertControls(group.getChildAt(index));
        }
    }

    private void assertBrandHeading(String language) {
        TextView title = (TextView) ((ViewGroup) activity.view("settingsHomePage")).getChildAt(0);
        assertEquals("one adjacent brand label", "ivLyrics", title.getText().toString());
        assertEquals("existing title size", android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_SP, 28f, activity.getResources().getDisplayMetrics()),
                title.getTextSize(), .01f);
        assertEquals("neutral title color", ((SettingsKit) activity.value("settingsKit")).p.text,
                title.getCurrentTextColor());
        assertEquals("Latin brand direction in every locale", View.TEXT_DIRECTION_LTR, title.getTextDirection());
        assertTrue("brand remains an accessibility heading", title.isAccessibilityHeading());
        AccessibilityNodeInfo node = title.createAccessibilityNodeInfo();
        assertEquals("single readable accessibility label", "ivLyrics", node.getText().toString());
        assertTrue(node.isHeading());
        node.recycle();
        Layout layout = title.getLayout();
        assertNotNull("native brand text layout", layout);
        assertEquals("brand fits even with large text", 1, layout.getLineCount());
        assertEquals(0, layout.getEllipsisCount(0));
        assertTrue("native title height fits", layout.getHeight() <= title.getHeight()
                - title.getCompoundPaddingTop() - title.getCompoundPaddingBottom());
        assertTrue("adjacent LTR runs", layout.getPrimaryHorizontal(0) < layout.getPrimaryHorizontal(2)
                && layout.getPrimaryHorizontal(2) < layout.getPrimaryHorizontal(8));
        Spanned label = (Spanned) title.getText();
        SettingsKit.FontSpan[] spans = label.getSpans(0, label.length(), SettingsKit.FontSpan.class);
        assertEquals("only the two brand runs", 2, spans.length);
        for (SettingsKit.FontSpan span : spans) {
            int start = label.getSpanStart(span);
            assertTrue("brand run starts at iv or Lyrics", start == 0 || start == 2);
            assertEquals(start == 0 ? 2 : 8, label.getSpanEnd(span));
            TextPaint paint = new TextPaint(title.getPaint());
            span.updateDrawState(paint);
            assertEquals("actual native typeface weight", start == 0 ? 800 : 250, paint.getTypeface().getWeight());
            assertFalse("uses real outlines without synthetic bold", paint.isFakeBoldText());
            if (start == 2) {
                long actual = brandInk(paint);
                TextPaint reference = new TextPaint(paint);
                reference.setTypeface(AppFonts.brand(activity, 200));
                long light = brandInk(reference);
                reference.setTypeface(AppFonts.brand(activity, 300));
                long regular = brandInk(reference);
                assertTrue("250 renders between 200 and 300, not a rounded static weight: "
                        + light + " < " + actual + " < " + regular, light < actual && actual < regular);
                android.util.Log.i("SettingsUiRegression", "Brand accepted: locale=" + language
                        + ", title=28sp/" + title.getTextSize() + "px, weights=800/250, Lyrics ink at 200/250/300="
                        + light + "/" + actual + "/" + regular);
            }
        }
        assertEquals("localized settings nav label", AppI18n.t(language, "settings.title"),
                ((TextView) activity.view("settingsNavTitle")).getText().toString());
        assertEquals("localized close label", AppI18n.t(language, "button.close"),
                activity.view("settingsNavBackButton").getContentDescription());
        assertTrue("existing search remains available", activity.view("settingsSearchInput").isShown());
    }

    private long brandInk(TextPaint source) {
        TextPaint paint = new TextPaint(source);
        paint.setColor(Color.WHITE);
        paint.setTextSize(96f);
        Bitmap pixels = Bitmap.createBitmap(512, 144, Bitmap.Config.ARGB_8888);
        new Canvas(pixels).drawText("Lyrics", 8f, 108f, paint);
        long ink = 0;
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
            ink += Color.alpha(pixels.getPixel(x, y));
        }
        pixels.recycle();
        return ink;
    }

    private int assertCenteredProviderBadges(View root, int size) {
        int checked = 0;
        ViewGroup.LayoutParams params = root.getLayoutParams();
        if (root instanceof TextView && params != null && params.width == size && params.height == size) {
            TextView badge = (TextView) root;
            assertEquals("provider badge text alignment: " + badge.getText(),
                    View.TEXT_ALIGNMENT_CENTER, badge.getTextAlignment());
            Layout layout = badge.getLayout();
            assertNotNull("provider badge has a native text layout", layout);
            for (int line = 0; line < layout.getLineCount(); line++) {
                float center = badge.getCompoundPaddingLeft()
                        + (layout.getLineLeft(line) + layout.getLineRight(line)) / 2f;
                assertEquals("provider badge drawn text is centered: " + badge.getText(),
                        badge.getWidth() / 2f, center, 1.5f);
            }
            checked++;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                checked += assertCenteredProviderBadges(group.getChildAt(index), size);
            }
        }
        return checked;
    }

    private void assertMinimum(View view, String name) {
        assertTrue(name + " height " + view.getMeasuredHeight() + " < 48dp", view.getMeasuredHeight() >= dp(48));
        assertTrue(name + " width " + view.getMeasuredWidth() + " < 48dp", view.getMeasuredWidth() >= dp(48));
    }

    private void assertChevron(TextView dropdown) {
        int count = 0;
        for (Drawable drawable : dropdown.getCompoundDrawablesRelative()) if (drawable != null) count++;
        assertEquals("one graphical dropdown chevron", 1, count);
        assertFalse("no additional textual v: " + dropdown.getText(), dropdown.getText().toString().matches(".*\\s[v⌄⌃]\\s*$"));
    }

    private void assertThumb(Switch toggle) {
        // Draw the production control first, so Switch has assigned its actual thumb bounds.
        drawSwitch(toggle);
        Drawable thumb = toggle.getThumbDrawable();
        assertNotNull("native thumb", thumb);
        Rect padding = new Rect(); thumb.getPadding(padding);
        assertTrue("intrinsic thumb content width", thumb.getIntrinsicWidth() > padding.left + padding.right);
        assertTrue("intrinsic thumb content height", thumb.getIntrinsicHeight() > padding.top + padding.bottom);
        Rect bounds = new Rect(thumb.getBounds());
        assertTrue("drawn thumb bounds", bounds.width() > 0 && bounds.height() > 0);
        Bitmap pixels = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(pixels); canvas.translate(-bounds.left, -bounds.top); thumb.draw(canvas);
        Rect ink = new Rect(pixels.getWidth(), pixels.getHeight(), 0, 0);
        int peakAlpha = 0;
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
            peakAlpha = Math.max(peakAlpha, Color.alpha(pixels.getPixel(x, y)));
        }
        assertTrue("thumb has drawn ink even when disabled", peakAlpha > 0);
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
            if (Color.alpha(pixels.getPixel(x, y)) >= Math.ceil(peakAlpha * .9f)) {
                ink.left = Math.min(ink.left, x); ink.top = Math.min(ink.top, y);
                ink.right = Math.max(ink.right, x + 1); ink.bottom = Math.max(ink.bottom, y + 1);
            }
        }
        pixels.recycle();
        assertFalse("opaque native thumb ink", ink.isEmpty());
        float aspect = (float) ink.width() / ink.height();
        assertTrue("circular drawn thumb, not old 20x26 ellipse: " + aspect, aspect >= .88f && aspect <= 1.14f);
    }

    private void drawSwitch(Switch toggle) {
        Bitmap control = Bitmap.createBitmap(toggle.getWidth(), toggle.getHeight(), Bitmap.Config.ARGB_8888);
        toggle.jumpDrawablesToCurrentState(); toggle.draw(new Canvas(control)); control.recycle();
    }

    private void save(View view, String name) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(SettingsAppearance.BACKGROUND); view.draw(new Canvas(bitmap));
        write(bitmap, name); bitmap.recycle();
    }

    private void write(Bitmap bitmap, String name) {
        File directory = instrumentation.getTargetContext().getExternalFilesDir("ui-regressions");
        assertNotNull(directory);
        if (!directory.isDirectory()) assertTrue(directory.mkdirs());
        File temporary = new File(directory, name + ".png.tmp");
        File complete = new File(directory, name + ".png");
        // UTP removes app-scoped storage at teardown. The concurrent CI collector
        // pulls only *.png, so publish that name only after the stream is closed.
        try (FileOutputStream stream = new FileOutputStream(temporary)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (java.io.IOException error) { throw new AssertionError(name, error); }
        assertTrue("publish completed PNG " + name, temporary.renameTo(complete));
        captures++;
        android.util.Log.i("SettingsUiRegression", "Saved " + complete.getAbsolutePath());
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private void onMain(Runnable action) { instrumentation.runOnMainSync(action); instrumentation.waitForIdleSync(); }
}
