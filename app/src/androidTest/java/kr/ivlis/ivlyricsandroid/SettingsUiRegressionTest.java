package kr.ivlis.ivlyricsandroid;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
public final class SettingsUiRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private SettingsUiHarnessActivity activity;
    private int captures;

    @After public void cleanup() {
        if (activity != null) instrumentation.runOnMainSync(() -> { activity.dismissPopup(); activity.finish(); });
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_UNFREEZE);
    }

    @Test public void koreanSmallPortrait() throws Exception { scenario("ko", 1f, 320, 640, false); }
    @Test public void koreanLandscapeLargeText() throws Exception { scenario("ko", 1.6f, 568, 320, true); }
    @Test public void englishNarrowLargeText() throws Exception { scenario("en", 1.8f, 280, 600, false); }
    @Test public void germanLandscapeLargeText() throws Exception { scenario("de", 2f, 568, 320, true); }
    @Test public void arabicRtlLargeText() throws Exception { scenario("ar", 1.6f, 320, 640, false); }

    private void scenario(String language, float fontScale, int width, int height, boolean landscape) throws Exception {
        instrumentation.getUiAutomation().setRotation(landscape ? Surface.ROTATION_90 : Surface.ROTATION_0);
        instrumentation.waitForIdleSync();
        SettingsUiHarnessActivity.prepareConfiguration(language, fontScale);
        Intent intent = new Intent(instrumentation.getTargetContext(), SettingsUiHarnessActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("uiLanguage", language).putExtra("fontScale", fontScale);
        activity = (SettingsUiHarnessActivity) instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        assertEquals(fontScale, activity.getResources().getConfiguration().fontScale, 0.01f);
        final String prefix = language + "-" + width + "x" + height + "-font" + fontScale;
        verifyRealWindowTaps(prefix, language);
        onMain(() -> {
            assertFalse(((AiLyricsSettings) activity.value("aiLyricsSettings")).snapshot().hasApiKey());
            assertFalse(activity.usesLivePlayback());
        });
        for (String tab : new String[]{"general", "ai", "appearance", "lyrics", "player", "providers", "system"}) {
            onMain(() -> activity.settings(tab));
            screenshot(prefix + "-window-settings-" + tab);
            onMain(() -> {
                View panel = activity.view("settingsPanel");
                int oldWidth = panel.getWidth(), oldHeight = panel.getHeight();
                render(panel, width, height);
                save(panel, prefix + "-settings-" + tab);
                assertControls(panel);
                assertMinimum(activity.view("settingsSearchInput"), "settings search");
                assertChevron((TextView) activity.view("uiLanguageSelectButton"));
                assertChevron((TextView) activity.view("outputLanguageSelectButton"));
                assertChevron((TextView) activity.view("pronunciationNotationSelectButton"));
                ScrollView scroll = (ScrollView) activity.value("settingsScrollView");
                assertTrue("landscape body remains scrollable", scroll.getHeight() >= dp(80));
                if ("ai".equals(tab)) {
                    View providers = activity.view("providerButtonsContainer");
                    int[] child = new int[2], parent = new int[2];
                    providers.getLocationInWindow(child); scroll.getChildAt(0).getLocationInWindow(parent);
                    scroll.scrollTo(0, Math.max(0, child[1] - parent[1]));
                    save(panel, prefix + "-settings-ai-providers");
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
            activity.settings("general");
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
            search.requestFocus();
            ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        awaitKeyboard();
        onMain(() -> {
            assertMinimum(activity.view("settingsSearchInput"), "search with IME");
            ScrollView scroll = (ScrollView) activity.value("settingsScrollView");
            assertTrue("IME leaves an accessible scroll viewport", scroll.getHeight() > 0);
        });
        screenshot(prefix + "-window-settings-keyboard");
        assertTrue("screenshots were written", captures >= 12);
    }

    private void awaitKeyboard() {
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] visible = new boolean[1];
            onMain(() -> {
                WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
                visible[0] = insets != null && insets.isVisible(WindowInsets.Type.ime());
            });
            if (visible[0]) return;
            SystemClock.sleep(50);
        }
        fail("Software keyboard did not appear; run emulator with a software IME enabled");
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
        onMain(() -> {
            assertTrue("real target enabled", target.isEnabled());
            target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), true);
        });
        final float[] point = new float[2];
        onMain(() -> {
            Rect visible = new Rect();
            assertTrue("real-window target visible", target.getLocalVisibleRect(visible));
            assertTrue("actual visible hit height >=48dp: " + visible, visible.height() >= dp(48));
            assertTrue("actual visible hit width >=48dp: " + visible, visible.width() >= dp(48));
            int[] position = new int[2]; target.getLocationOnScreen(position);
            point[0] = position[0] + visible.exactCenterX(); point[1] = position[1] + visible.exactCenterY();
            Rect window = new Rect(); target.getRootView().getWindowVisibleDisplayFrame(window);
            assertTrue("touch center inside real visible window: " + window,
                    window.contains(Math.round(point[0]), Math.round(point[1])));
            View root = target.getRootView(); int[] origin = new int[2]; root.getLocationOnScreen(origin);
            assertTrue("actual popup/window not wider than screen", root.getWidth() <= activity.getResources().getDisplayMetrics().widthPixels);
            assertTrue("actual touch target inside window content", point[0] >= origin[0] && point[0] < origin[0] + root.getWidth()
                    && point[1] >= origin[1] && point[1] < origin[1] + root.getHeight());
        });
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, point[0], point[1], 0);
        instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up);
        down.recycle(); up.recycle(); instrumentation.waitForIdleSync();
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
        Bitmap window = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("native window screenshot", window);
        write(window, name); window.recycle();
    }

    private void assertControls(View root) {
        if (root.getVisibility() != View.VISIBLE) return;
        if (root instanceof Button || root instanceof EditText || root instanceof Switch) {
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
        Bitmap control = Bitmap.createBitmap(toggle.getWidth(), toggle.getHeight(), Bitmap.Config.ARGB_8888);
        toggle.jumpDrawablesToCurrentState(); toggle.draw(new Canvas(control)); control.recycle();
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

    private void save(View view, String name) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(SettingsAppearance.BACKGROUND); view.draw(new Canvas(bitmap));
        write(bitmap, name); bitmap.recycle();
    }

    private void write(Bitmap bitmap, String name) {
        File directory = instrumentation.getTargetContext().getExternalFilesDir("ui-regressions");
        assertNotNull(directory);
        if (!directory.isDirectory()) assertTrue(directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); captures++;
        } catch (java.io.IOException error) { throw new AssertionError(name, error); }
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private void onMain(Runnable action) { instrumentation.runOnMainSync(action); instrumentation.waitForIdleSync(); }
}
