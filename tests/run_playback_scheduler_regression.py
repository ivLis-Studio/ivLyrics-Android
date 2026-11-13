#!/usr/bin/env python3
"""Exercise the production scheduler with a deterministic vsync queue and timer."""
import subprocess
from regression_runtime import SHARED, REPORTS, java_tool

source = (SHARED / "BaseLyricsActivity.java").read_text()
start = source.index("    private boolean playbackClockActive;")
end = source.index("    private LyricsActivityHost lyricsActivityHost;", start)
production = source[start:end]
work = REPORTS / "playback-scheduler"
work.mkdir(parents=True, exist_ok=True)
java = work / "PlaybackSchedulerRegression.java"
java.write_text(r"""
import java.util.*;
public final class PlaybackSchedulerRegression {
    static int checks;
    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    static final class Choreographer {
        interface FrameCallback { void doFrame(long nanos); }
        static final Choreographer instance = new Choreographer();
        final Set<FrameCallback> frames = new LinkedHashSet<>();
        static Choreographer getInstance() { return instance; }
        void postFrameCallback(FrameCallback callback) {
            check(frames.add(callback), "duplicate vsync");
        }
        void removeFrameCallback(FrameCallback callback) { frames.remove(callback); }
        void frame() {
            var due = new ArrayList<>(frames); frames.clear();
            for (var callback : due) callback.doFrame(1_000_000);
        }
    }
    static final class Handler {
        final Set<Runnable> timers = new LinkedHashSet<>();
        void removeCallbacks(Runnable r) { timers.remove(r); }
        void postDelayed(Runnable r, long delay) {
            check(delay == 500, "idle cadence");
            check(timers.add(r), "duplicate idle timer");
        }
        void fire() {
            var due = new ArrayList<>(timers); timers.clear();
            for (var callback : due) callback.run();
        }
    }
    static final class Track { boolean playing; }
    static final class Preview {
        boolean visible, animated;
        boolean isShown() { return visible; }
        boolean needsHostFrames() { return animated; }
    }
    static final class Vinyl {
        final Preview preview = new Preview();
        Preview lyricView() { return preview; }
    }
    final Handler handler = new Handler();
    Track currentTrack = new Track();
    long pendingSeekPositionMs = -1, lastProgressUiUpdateMs;
    boolean vinylModeVisible;
    Preview lyricPreviewView = new Preview();
    Vinyl vinylPlayerModeView = new Vinyl();
    int ticks;
    boolean acknowledgeSeek;
    void updatePlaybackUi(long frameNanos) {
        ticks++;
        if (acknowledgeSeek) pendingSeekPositionMs = -1;
    }
""" + production + r"""
    public static void main(String[] args) {
        var model = new PlaybackSchedulerRegression();
        var vsync = Choreographer.instance;
        model.startPlaybackClock();
        check(vsync.frames.size() == 1, "start schedules next vsync");
        vsync.frame();
        check(vsync.frames.isEmpty() && model.handler.timers.size() == 1, "pause idles");
        model.handler.fire(); vsync.frame();
        check(model.ticks == 2 && vsync.frames.isEmpty(), "idle refresh then sleeps again");
        model.currentTrack.playing = true;
        for (int i = 0; i < 20; i++) model.requestPlaybackFrame();
        check(vsync.frames.size() == 1 && model.handler.timers.isEmpty(), "resume coalesces and cancels idle");
        for (int i = 0; i < 120; i++) vsync.frame();
        check(model.ticks == 122 && vsync.frames.size() == 1, "playing keeps each frame");
        model.currentTrack.playing = false;
        model.pendingSeekPositionMs = 6000;
        vsync.frame();
        check(vsync.frames.size() == 1, "paused optimistic seek stays live");
        model.acknowledgeSeek = true;
        vsync.frame();
        check(vsync.frames.isEmpty(), "acknowledged seek idles");
        model.lyricPreviewView.visible = true;
        model.lyricPreviewView.animated = true;
        model.requestPlaybackFrame(); vsync.frame();
        check(vsync.frames.size() == 1, "paused effects keep frames");
        model.lyricPreviewView.visible = false;
        vsync.frame();
        check(vsync.frames.isEmpty(), "covered effects do not keep frames");
        model.vinylModeVisible = true;
        model.vinylPlayerModeView.preview.visible = true;
        model.vinylPlayerModeView.preview.animated = true;
        model.requestPlaybackFrame(); vsync.frame();
        check(vsync.frames.size() == 1, "LP effects keep frames");
        model.stopPlaybackClock();
        model.requestPlaybackFrame(); model.handler.fire();
        check(vsync.frames.isEmpty() && model.handler.timers.isEmpty(), "paused Activity cannot restart");
        model.currentTrack = null;
        model.vinylPlayerModeView.preview.animated = false;
        model.startPlaybackClock(); vsync.frame();
        check(vsync.frames.isEmpty(), "absent metadata idles");
        model.stopPlaybackClock();
        System.out.println("PLAYBACK_SCHEDULER_PASSED checks=" + checks);
    }
}
""")
subprocess.run([java_tool("javac"), "-d", str(work), str(java)], check=True)
subprocess.run([java_tool("java"), "-cp", str(work), "PlaybackSchedulerRegression"], check=True)
