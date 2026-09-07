#!/usr/bin/env python3
"""Run the production inline update method with a deterministic host-side view.

No Android runtime, emulator, device, account, or network is used. Only renderer
and playback collaborators are substituted; visibility/cache decisions are real.
"""
import subprocess

from regression_runtime import ROOT, REPORTS, java_tool

source = (ROOT / "shared/src/main/java/kr/ivlis/ivlyricsandroid/IvLyricsBridge.java").read_text()
start = source.index("@Override void updatePosition(Engine state)", source.index("class NativeInline"))
cursor = source.index("{", start) + 1
depth = 1
while depth:
    depth += (source[cursor] == "{") - (source[cursor] == "}")
    cursor += 1
method = source[start:cursor]
work = REPORTS / "inline-lifecycle"
work.mkdir(parents=True, exist_ok=True)
test = work / "InlineLifecycleRegression.java"
test.write_text(r'''
import java.util.*;
public final class InlineLifecycleRegression {
    static class View {
        static final int VISIBLE = 0, GONE = 8;
        int visibility = VISIBLE;
        int getVisibility() { return visibility; }
        void setVisibility(int value) { visibility = value; }
        void updatePosition(Engine state) { }
    }
    static class SystemClock {
        static long now = 100;
        static long uptimeMillis() { return now; }
    }
    static class AiLyricsSettings {
        static final int PREVIEW_ITEM_NONE = 0;
        static class Snapshot { int previewItems = 1; }
    }
    static class Track {
        String mediaId = "spotify:track:test", trackId = "test";
        boolean dj, metadata = true, playing = true;
        boolean hasUsableMetadata() { return metadata; }
        boolean isSpotifyDjSegment() { return dj; }
    }
    static class Result { String detail = "No lyrics"; }
    static class Engine {
        Track track = new Track();
        String key = "song";
        Result result = new Result();
        boolean loading;
        long position() { return 1000; }
        String ui(String key) { return key; }
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
    }
    static class InlineLyricPreviewModel {
        static class PreviewEntry { long startTimeMs = 0, endTimeMs = 5000; }
        final PreviewEntry entry = new PreviewEntry();
        PreviewEntry at(long position) { return entry; }
        List<MainLyricPreviewView.PreviewLine> rows(PreviewEntry entry) {
            return Collections.singletonList(new MainLyricPreviewView.PreviewLine("Song words", true));
        }
    }
    static class Inline extends View {
        AiLyricsSettings.Snapshot appliedSettings = new AiLyricsSettings.Snapshot();
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
    public static void main(String[] args) {
        returnsFromSpeech(); returnsFromMissingTrackAndToggle(); returnsWhileLoading();
        System.out.println("Inline lifecycle: " + assertions + " assertions passed");
    }
}
''')
subprocess.run([str(java_tool("javac")), "-d", str(work), str(test)], check=True)
subprocess.run([str(java_tool("java")), "-cp", str(work), "InlineLifecycleRegression"], check=True)
