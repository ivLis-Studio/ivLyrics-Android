#!/usr/bin/env python3
"""Run production inline render/update methods with a deterministic host-side view.

No Android runtime, emulator, device, account, or network is used. Renderer and
playback collaborators are substituted; visibility/cache decisions and language
detection use production code.
"""
import subprocess

from regression_runtime import ROOT, REPORTS, SHARED, java_tool

source = (ROOT / "shared/src/main/java/kr/ivlis/ivlyricsandroid/IvLyricsBridge.java").read_text()
def declaration(signature):
    start = source.index(signature, source.index("class NativeInline"))
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]
method = declaration("@Override void render(Engine state)") + "\n" + declaration("@Override void updatePosition(Engine state)")
detect_language = declaration("String detectLanguage(LyricsResult value)")
work = REPORTS / "inline-lifecycle"
work.mkdir(parents=True, exist_ok=True)
detector = work / "LyricsLanguageDetector.java"
detector.write_text((SHARED / "LyricsLanguageDetector.java").read_text().replace(
    "package kr.ivlis.ivlyricsandroid;", "", 1
))
test = work / "InlineLifecycleRegression.java"
test.write_text(r'''
import java.util.*;
public final class InlineLifecycleRegression {
    static class View {
        static final int VISIBLE = 0, GONE = 8;
        int visibility = VISIBLE;
        int getVisibility() { return visibility; }
        void setVisibility(int value) { visibility = value; }
        void setContentDescription(String value) { }
        void render(Engine state) { }
        void updatePosition(Engine state) { }
    }
    static class SystemClock {
        static long now = 100;
        static long uptimeMillis() { return now; }
    }
    static class AiLyricsSettings {
        static final int PREVIEW_ITEM_NONE = 0;
        Snapshot value = new Snapshot();
        Snapshot snapshot() { return value; }
        static class Snapshot {
            int previewItems = 1;
            Object typography;
            String karaokeDisplayGranularity = "", lyricsTextAlignment = "", uiLang = "en";
            boolean karaokeBounceEffectEnabled;
        }
    }
    static class Track {
        String mediaId = "spotify:track:test", trackId = "test";
        boolean dj, metadata = true, playing = true;
        long durationMs = 5000;
        boolean hasUsableMetadata() { return metadata; }
        boolean isSpotifyDjSegment() { return dj; }
    }
    static class LyricsLine {
        final String text;
        LyricsLine(String text) { this.text = text; }
    }
    static class LyricsResult {
        String detail = "No lyrics";
        List<LyricsLine> lines = List.of(new LyricsLine("Song words"));
    }
    static class Engine {
        Track track = new Track();
        String key = "song";
        LyricsResult result = new LyricsResult();
        AiLyricsSettings settings = new AiLyricsSettings();
        String sourceLanguage = "en";
        boolean loading, pronunciationLoading, translationLoading;
        long position() { return 1000; }
        String ui(String key) { return key; }
''' + detect_language + r'''
    }
    static class MainLyricPreviewView {
        List<PreviewLine> rows = Collections.emptyList();
        int sets, clears, ticks;
        static class PreviewLine {
            final String text;
            PreviewLine(String text, boolean original) { this.text = text; }
            static PreviewLine loading(String text) { return new PreviewLine(text, false); }
        }
        void clear() { rows = Collections.emptyList(); clears++; }
        void setPreview(List<PreviewLine> value, long p, long s, long e, boolean playing) {
            rows = value; sets++;
        }
        void setPlaybackPosition(long position, boolean playing) { ticks++; }
        void setTypographySettings(Object settings) { }
        void setKaraokeDisplayGranularity(String value) { }
        void setKaraokeBounceEffectEnabled(boolean value) { }
        void setLyricTextAlignment(String value) { }
        void setLyricsSegmentationLocale(String value) { }
    }
    static class InlineLyricPreviewModel {
        static class PreviewEntry { long startTimeMs = 0, endTimeMs = 5000; }
        final PreviewEntry entry = new PreviewEntry();
        boolean pronunciation, translation;
        InlineLyricPreviewModel() { }
        InlineLyricPreviewModel(LyricsResult result, AiLyricsSettings.Snapshot settings, long duration,
                boolean pronunciation, boolean translation, String source) {
            this.pronunciation = pronunciation; this.translation = translation;
        }
        PreviewEntry at(long position) { return entry; }
        List<MainLyricPreviewView.PreviewLine> rows(PreviewEntry entry) {
            List<MainLyricPreviewView.PreviewLine> rows = new ArrayList<>();
            rows.add(new MainLyricPreviewView.PreviewLine("Song words", true));
            if (pronunciation) rows.add(new MainLyricPreviewView.PreviewLine("Generating pronunciation", false));
            if (translation) rows.add(new MainLyricPreviewView.PreviewLine("Generating translation", false));
            return rows;
        }
    }
    static class Inline extends View {
        AiLyricsSettings.Snapshot appliedSettings = new AiLyricsSettings.Snapshot();
        LyricsResult appliedResult;
        long appliedDuration;
        boolean appliedPronunciationLoading, appliedTranslationLoading;
        String appliedSourceLanguage;
        final MainLyricPreviewView preview = new MainLyricPreviewView();
        InlineLyricPreviewModel model = new InlineLyricPreviewModel();
        InlineLyricPreviewModel.PreviewEntry appliedEntry;
        boolean entryApplied;
        String emptyKey = "";
        long emptySince;
''' + method + r'''
    }
    static int assertions;
    static void check(String name, boolean value) {
        assertions++;
        if (!value) throw new AssertionError(name);
    }
    static void returnsFromSpeech() {
        Inline view = new Inline(); Engine state = new Engine();
        view.updatePosition(state);
        check("initial song rendered", view.preview.sets == 1 && view.preview.rows.size() == 1);
        state.track.dj = true;
        view.updatePosition(state);
        check("DJ speech hidden", view.getVisibility() == View.GONE && view.preview.rows.isEmpty());
        state.track.dj = false;
        view.updatePosition(state);
        check("same cached song repopulated", view.getVisibility() == View.VISIBLE
                && view.preview.sets == 2 && view.preview.rows.size() == 1);
        view.updatePosition(state);
        check("normal playback still reuses row", view.preview.sets == 2 && view.preview.ticks == 1);
    }
    static void returnsFromMissingTrackAndToggle() {
        Inline view = new Inline(); Engine state = new Engine();
        view.updatePosition(state);
        Track song = state.track; state.track = null;
        view.updatePosition(state);
        state.track = song;
        view.updatePosition(state);
        check("transient missing track recovers", view.preview.sets == 2 && !view.preview.rows.isEmpty());
        view.appliedSettings.previewItems = 0;
        view.updatePosition(state);
        check("user disabled preview hides", view.getVisibility() == View.GONE);
        view.appliedSettings.previewItems = 1;
        view.updatePosition(state);
        check("re-enabled preview repopulates", view.preview.sets == 3 && !view.preview.rows.isEmpty());
    }
    static void returnsWhileLoading() {
        Inline view = new Inline(); Engine state = new Engine();
        view.model = null; state.loading = true;
        view.updatePosition(state);
        state.track.dj = true;
        view.updatePosition(state);
        state.track.dj = false;
        view.updatePosition(state);
        check("same loading detail restored", view.preview.sets == 2
                && view.preview.rows.get(0).text.equals("status.lyrics_loading"));
        state.loading = false;
        view.updatePosition(state);
        check("empty detail initially visible", view.getVisibility() == View.VISIBLE);
        SystemClock.now += 3001;
        view.updatePosition(state);
        check("empty detail still auto hides", view.getVisibility() == View.GONE);
        view.model = new InlineLyricPreviewModel();
        view.updatePosition(state);
        check("late lyrics become visible", view.getVisibility() == View.VISIBLE
                && view.preview.rows.get(0).text.equals("Song words"));
    }
    static void supplementCompletionRefreshesRetainedRows() {
        Inline view = new Inline(); Engine state = new Engine();
        state.pronunciationLoading = state.translationLoading = true;
        view.render(state);
        check("both tasks shown while generating", view.preview.rows.size() == 3);
        InlineLyricPreviewModel original = view.model;
        state.pronunciationLoading = false;
        view.render(state);
        check("pronunciation completion refreshes unchanged lyric result", view.model != original
                && view.preview.rows.size() == 2
                && view.preview.rows.get(1).text.equals("Generating translation"));
        state.translationLoading = false;
        view.render(state);
        check("terminal callback removes last loading row", view.preview.rows.size() == 1);
        int sets = view.preview.sets;
        view.render(state);
        check("finished row remains cached during playback", view.preview.sets == sets);
        state.track.dj = true;
        view.updatePosition(state);
        state.track.dj = false;
        view.updatePosition(state);
        check("reopened retained slot cannot restore generating", view.preview.rows.size() == 1);
        state.pronunciationLoading = true;
        view.render(state);
        state.settings.value = new AiLyricsSettings.Snapshot();
        state.settings.value.previewItems = 0;
        view.render(state);
        state.pronunciationLoading = false;
        view.render(state);
        state.settings.value = new AiLyricsSettings.Snapshot();
        view.render(state);
        check("completion while hidden is preserved on re-enable", view.preview.rows.size() == 1);
    }
    static void undetectedLanguageSurvivesRepeatedRenders() {
        Inline view = new Inline(); Engine state = new Engine();
        view.render(state);
        check("detector has no language for empty lyrics", LyricsLanguageDetector.detect("") == null);
        state.result = new LyricsResult();
        state.result.lines = Collections.emptyList();
        state.sourceLanguage = state.detectLanguage(state.result);
        view.render(state);
        // A changed result short-circuits the comparison on the first callback.
        // The next metadata/surface render must also tolerate an unknown language.
        view.render(state);
        check("empty result keeps a usable language", "auto".equals(state.sourceLanguage));
        check("missing lyrics show the empty state", view.model == null
                && view.preview.rows.get(0).text.equals("No lyrics"));
        int sets = view.preview.sets;
        view.render(state);
        check("repeated empty rendering reuses preview", view.preview.sets == sets);
        SystemClock.now += 3001;
        view.render(state);
        check("missing lyrics still hide after the existing delay", view.getVisibility() == View.GONE);

        state.result = new LyricsResult();
        state.result.lines = List.of(new LyricsLine("♪"));
        state.sourceLanguage = state.detectLanguage(state.result);
        view.render(state);
        view.render(state);
        check("nonempty unknown language is safe", "auto".equals(state.sourceLanguage)
                && view.model != null && view.getVisibility() == View.VISIBLE);

        state.result = new LyricsResult();
        state.result.lines = List.of(new LyricsLine("안녕하세요 사랑해요"));
        state.sourceLanguage = state.detectLanguage(state.result);
        view.render(state);
        view.render(state);
        check("detected language recovers after missing lyrics", "ko".equals(state.sourceLanguage)
                && view.getVisibility() == View.VISIBLE && view.model != null);
    }
    public static void main(String[] args) {
        returnsFromSpeech(); returnsFromMissingTrackAndToggle(); returnsWhileLoading();
        supplementCompletionRefreshesRetainedRows();
        undetectedLanguageSurvivesRepeatedRenders();
        System.out.println("Inline lifecycle: " + assertions + " assertions passed");
    }
}
''')
subprocess.run([str(java_tool("javac")), "-d", str(work), str(detector), str(test)], check=True)
subprocess.run([str(java_tool("java")), "-cp", str(work), "InlineLifecycleRegression"], check=True)
