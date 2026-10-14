#!/usr/bin/env python3
"""SDK-free state/localization contracts. Native geometry acceptance is androidTest."""
import subprocess
import re
from regression_runtime import ROOT, SHARED, REPORTS, java_tool


def declaration(source, signature):
    start = source.index(signature)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


source = (SHARED / "BaseLyricsActivity.java").read_text()
appearance = (SHARED / "SettingsAppearance.java").read_text()
harness = (ROOT / "app/src/debug/java/kr/ivlis/ivlyricsandroid/SettingsUiHarnessActivity.java").read_text()
native = (ROOT / "app/src/androidTest/java/kr/ivlis/ivlyricsandroid/SettingsUiRegressionTest.java").read_text()
assert ' + "  v"' not in source, "dropdown labels must not add a second textual chevron"
assert "setThumbDrawable" not in appearance and "setThumbTintList(states(" in appearance
assert "setMinimumHeight(dp(context, 48))" in appearance
input_method = declaration(source, "private EditText settingEditText(")
assert input_method.index("input.setInputType(type)") < input_method.index("input.setMinimumHeight(")
for name in ("requestAiLyrics", "requestCulturalAnnotations", "showVideoSelectionDialog", "performManualLrclibSearch", "requestMetadataTranslation"):
    assert "@Override protected void " + name in harness, "offline callback missing: " + name
assert "usesLivePlayback() { return false; }" in harness
assert "super.attachBaseContext(base.createConfigurationContext(config))" in harness
assert "applyOverrideConfiguration(config)" not in harness
assert "new Configuration(base.getResources().getConfiguration())" not in harness
assert "SettingsUiHarnessActivity.prepareConfiguration(language, fontScale)" in native
assert "sendPointerSync" in native and "getLocalVisibleRect" in native
hit_frame = declaration(native, "private Rect visibleHitFrame(")
assert "visible.offset(-target.getScrollX(), -target.getScrollY())" in hit_frame
assert "visible.intersect(0, 0, target.getWidth(), target.getHeight())" in hit_frame
assert "Rect visible = visibleHitFrame(target)" in native
assert "desired.offset(target.getScrollX(), target.getScrollY())" in native
assert "assertEditorHitFrame((EditText) target)" in native
editor_frame = declaration(native, "private void assertEditorHitFrame(")
assert "new int[]{-dp(400), dp(400)}" in editor_frame and "visibleHitFrame(editor)" in editor_frame
assert "finally { editor.scrollTo(scrollX, scrollY); }" in editor_frame
assert 'getExternalFilesDir("ui-regressions")' in native
assert "peakAlpha * .9f" in native and "aspect >= .88f && aspect <= 1.14f" in native
assert "genuineWindowCaptures >= 8" in native
assert "software-root-diagnostic" in native
assert "search.requestFocus()" not in native, "keyboard test must enter the real editor through native touch"
assert "tap(search);\n        awaitInputConnection(search);" in native
connection = declaration(native, "private void awaitInputConnection(")
assert "input.isActive(search)" in connection and "input.isAcceptingText()" in connection
assert "search.hasWindowFocus()" in connection and "search.hasFocus()" in connection
keyboard = declaration(native, "private void awaitKeyboard(")
assert "insets.isVisible(WindowInsets.Type.ime())" in keyboard, "a request is not keyboard visibility"
capture = declaration(native, "private void write(")
assert 'name + ".png.tmp"' in capture
assert "new FileOutputStream(temporary)" in capture
assert capture.index("catch (java.io.IOException") < capture.index("temporary.renameTo(complete)")
assert capture.index("temporary.renameTo(complete)") < capture.index('"Saved " + complete.getAbsolutePath()')
for signature in ("private View providerButton(", "private View buildLyricsProviderSettingsCard("):
    assert "ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)" not in declaration(source, signature)

def luminance(color):
    channels = [(color >> shift & 255) / 255 for shift in (16, 8, 0)]
    linear = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4 for value in channels]
    return sum(value * weight for value, weight in zip(linear, (0.2126, 0.7152, 0.0722)))

tokens = {name: int(value, 16) for name, value in re.findall(r"static final int (\w+) = 0x([A-Fa-f0-9]+);", appearance)}
for foreground in ("TEXT", "SECONDARY", "MUTED", "ACCENT"):
    ratio = (luminance(tokens[foreground]) + .05) / (luminance(tokens["SURFACE"]) + .05)
    assert ratio >= 4.5, f"{foreground} enabled text contrast {ratio:.2f} <4.5"
print(f"Enabled settings description/hint contrast: {(luminance(tokens['MUTED'])+.05)/(luminance(tokens['SURFACE'])+.05):.2f}:1")

work = REPORTS / "settings-ui"
work.mkdir(parents=True, exist_ok=True)
fixture = r'''
package kr.ivlis.ivlyricsandroid;
import java.util.*;
public final class SettingsUiContractRegression {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    static class View { static final int ACCESSIBILITY_LIVE_REGION_POLITE = 1; }
    static class Widget {
        boolean enabled; String text; float alpha;
        void setEnabled(boolean value) { enabled = value; }
        boolean isEnabled() { return enabled; }
        void setText(String value) { text = value; }
        void setAlpha(float value) { alpha = value; }
        void setAccessibilityLiveRegion(int unused) {}
    }
    static class Result { List<String> lines = new ArrayList<>(); }
    static class State {
        Object currentTrack;
        Result currentBaseLyricsResult;
        boolean lyricsLookupInFlight, aiLyricsGenerating, lyricsSupplementPronunciationLoading,
            lyricsSupplementTranslationLoading, lyricsSupplementFuriganaLoading, lyricsCulturalAnnotationsLoading;
        Widget lyricsRegenerateButton = new Widget(), culturalAnnotationRegenerateButton = new Widget();
        String language;
        String ui(String key) { return AppI18n.t(language, key); }
        // METHOD
    }
    @SuppressWarnings("unchecked") public static void main(String[] args) throws Exception {
        var field = AppI18n.class.getDeclaredField("STRINGS"); field.setAccessible(true);
        Map<String,Map<String,String>> tables = (Map<String,Map<String,String>>) field.get(null);
        check(AppI18n.UI_LANGUAGES.size() == 22);
        for (var language : AppI18n.UI_LANGUAGES) {
            String label = tables.get(language.code).get("lyrics.regenerate_supplements");
            check(label != null && !label.isBlank()); // No English fallback accepted.
            for (int track = 0; track < 2; track++) for (int base = 0; base < 3; base++) for (int mask = 0; mask < 64; mask++) {
                State state = new State(); state.language = language.code;
                state.currentTrack = track == 0 ? null : new Object();
                if (base != 0) { state.currentBaseLyricsResult = new Result(); if (base == 2) state.currentBaseLyricsResult.lines.add("fixture"); }
                state.lyricsLookupInFlight = (mask & 1) != 0;
                state.aiLyricsGenerating = (mask & 2) != 0;
                state.lyricsSupplementPronunciationLoading = (mask & 4) != 0;
                state.lyricsSupplementTranslationLoading = (mask & 8) != 0;
                state.lyricsSupplementFuriganaLoading = (mask & 16) != 0;
                state.lyricsCulturalAnnotationsLoading = (mask & 32) != 0;
                state.updateLyricsRegenerateActions();
                boolean enabled = track == 1 && base == 2 && mask == 0;
                check(state.lyricsRegenerateButton.enabled == enabled);
                check(state.culturalAnnotationRegenerateButton.enabled == enabled);
                check(state.lyricsRegenerateButton.alpha == (enabled ? 1f : .5f));
                check(state.lyricsRegenerateButton.text.equals((mask & 30) == 0 ? label : AppI18n.t(language.code, "status.ai_generating")));
            }
            State absent = new State(); absent.language = language.code;
            absent.lyricsRegenerateButton = null; absent.culturalAnnotationRegenerateButton = null;
            absent.updateLyricsRegenerateActions();
        }
        check(AppI18n.t("ko", "lyrics.regenerate_supplements").equals("번역·발음 재생성"));
        check(AppI18n.t("en", "lyrics.regenerate_supplements").equals("Regenerate translation & pronunciation"));
        System.out.println("Settings UI contracts: " + checks + " checks passed (22 locales, all loading/missing-lyrics states). Native geometry still requires connectedDebugAndroidTest.");
    }
}
class AiLyricsSettings {
    static String normalizeLanguageCode(String value) { return value; }
    static final class Language {
        final String code, name, nativeName;
        Language(String code, String name, String nativeName, String unused) { this.code=code; this.name=name; this.nativeName=nativeName; }
    }
}
'''.replace("// METHOD", declaration(source, "private void updateLyricsRegenerateActions()"))
java = work / "SettingsUiContractRegression.java"
java.write_text(fixture)
sources = [java] + [SHARED / name for name in (
    "AppI18n.java", "ResearchI18n.java", "LocalAiI18n.java", "OpenAIConnectionI18n.java",
    "SettingsTranslationOverrides.java", "LyricsToolsTranslationOverrides.java",
    "VideoSelectionTranslations.java", "SettingsExperienceTranslations.java")]
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-d", str(work), *map(str, sources)], check=True)
subprocess.run([java_tool("java"), "-cp", str(work), "kr.ivlis.ivlyricsandroid.SettingsUiContractRegression"], check=True)
