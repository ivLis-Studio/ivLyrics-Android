#!/usr/bin/env python3
"""Exercise the production module karaoke controller with a deterministic JVM host.

Only Android scheduling and Spotify-facing collaborators are substituted. Native
audio, account support, and real Cosmos routing require separate device checks.
No Android SDK, Spotify APK, account, device, or network is needed for this suite.
"""
import hashlib
import subprocess

from regression_runtime import ROOT, REPORTS, java_tool


SOURCE = ROOT / "spotify-module/src/main/java/dev/ivlyrics/spotify/SpotifyKaraokeController.java"
WORK = REPORTS / "karaoke-controller"
FILES = {
    "android/os/Looper.java": r'''
package android.os;
public final class Looper {
    private static final Looper MAIN = new Looper();
    private static boolean onMain = true;
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return onMain ? MAIN : null; }
    public static void testMainThread(boolean value) { onMain = value; }
}
''',
    "android/os/Handler.java": r'''
package android.os;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
public final class Handler {
    private static final class Task {
        final Runnable action;
        final long due, order;
        Task(Runnable action, long due, long order) {
            this.action = action; this.due = due; this.order = order;
        }
    }
    private static final List<Task> queue = new ArrayList<>();
    private static long now, sequence;
    public Handler(Looper looper) { }
    public boolean post(Runnable action) { return postDelayed(action, 0); }
    public boolean postDelayed(Runnable action, long delay) {
        queue.add(new Task(action, now + delay, sequence++)); return true;
    }
    public void removeCallbacks(Runnable action) { queue.removeIf(task -> task.action == action); }
    public static void drain() {
        Looper.testMainThread(true);
        int limit = 10000;
        while (true) {
            queue.sort(Comparator.comparingLong((Task task) -> task.due).thenComparingLong(task -> task.order));
            if (queue.isEmpty() || queue.get(0).due > now) return;
            if (--limit == 0) throw new AssertionError("Main queue did not settle");
            queue.remove(0).action.run();
        }
    }
    public static void advance(long duration) { now += duration; drain(); }
    public static void reset() { queue.clear(); now = 0; sequence = 0; Looper.testMainThread(true); }
}
''',
    "android/util/Log.java": r'''
package android.util;
import java.util.ArrayList;
import java.util.List;
public final class Log {
    public static final List<String> messages = new ArrayList<>();
    public static int i(String tag, String message) { messages.add(message); return 0; }
    public static int w(String tag, String message) { messages.add(message); return 0; }
}
''',
    "dev/ivlyrics/spotify/SpotifyKaraokeTransport.java": r'''
package dev.ivlyrics.spotify;
import java.util.ArrayList;
import java.util.List;
public final class SpotifyKaraokeTransport {
    public interface Result { void onComplete(); void onError(Throwable error); }
    public interface Events {
        void onEvent(int id, String track, String message, int code);
        void onError(Throwable error);
    }
    public interface Subscription { void dispose(); }
    static class Operation implements Subscription {
        final String kind;
        final float volume;
        final Result result;
        boolean disposed;
        Operation(String kind, float volume, Result result) {
            this.kind = kind; this.volume = volume; this.result = result;
        }
        public void dispose() { disposed = true; }
    }
    static class EventSubscription implements Subscription {
        final Events callback;
        boolean disposed;
        EventSubscription(Events callback) { this.callback = callback; }
        public void dispose() { disposed = true; }
    }
    static boolean ready = true;
    static final List<Operation> operations = new ArrayList<>();
    static final List<EventSubscription> streams = new ArrayList<>();
    static final List<String> order = new ArrayList<>();
    public static boolean isReady() { return ready; }
    public static Subscription subscribeEvents(Events callback) {
        EventSubscription stream = new EventSubscription(callback);
        streams.add(stream); order.add("subscribe"); return stream;
    }
    public static Subscription postStatus(boolean enabled, Result result) {
        Operation operation = new Operation(enabled ? "enable" : "disable", Float.NaN, result);
        operations.add(operation); order.add(operation.kind); return operation;
    }
    public static Subscription setVolume(float volume, Result result) {
        Operation operation = new Operation("volume", volume, result);
        operations.add(operation); order.add(operation.kind); return operation;
    }
    static void reset() { operations.clear(); streams.clear(); order.clear(); ready = true; }
}
''',
    "dev/ivlyrics/spotify/SpotifyKaraokeEligibility.java": r'''
package dev.ivlyrics.spotify;
public final class SpotifyKaraokeEligibility {
    public interface Listener { void onEligibility(String uri, boolean loaded, boolean supported); }
    static Listener listener;
    public static void setListener(Listener value) { listener = value; }
    public static void onTrack(String track) { }
    public static void stop() { listener = null; }
    static void emit(String track, boolean loaded, boolean supported) {
        if (listener != null) listener.onEligibility(track, loaded, supported);
    }
}
''',
    "dev/ivlyrics/spotify/SpotifyKaraokePlayback.java": r'''
package dev.ivlyrics.spotify;
public final class SpotifyKaraokePlayback {
    interface Listener { void onPlayback(boolean known, boolean allowed, String reason); }
    static Listener listener;
    public static void setListener(Listener value) { listener = value; }
    static void emit(boolean known, boolean allowed, String reason) {
        if (listener != null) listener.onPlayback(known, allowed, reason);
    }
}
''',
    "dev/ivlyrics/spotify/KaraokeControllerRegression.java": r'''
package dev.ivlyrics.spotify;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import static dev.ivlyrics.spotify.SpotifyKaraokeController.State;
import static dev.ivlyrics.spotify.SpotifyKaraokeTransport.Operation;
import static dev.ivlyrics.spotify.SpotifyKaraokeTransport.EventSubscription;

public final class KaraokeControllerRegression {
    private static final String A = "spotify:track:AAAAAAAAAAAAAAAAAAAAAA";
    private static final String B = "spotify:track:BBBBBBBBBBBBBBBBBBBBBB";
    private static int assertions, scenarios;
    private static final List<String> failures = new ArrayList<>();

    private static void check(String label, boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private static int count(String kind) {
        return (int) SpotifyKaraokeTransport.operations.stream().filter(op -> op.kind.equals(kind)).count();
    }
    private static Operation latest(String kind) {
        for (int i = SpotifyKaraokeTransport.operations.size() - 1; i >= 0; i--) {
            Operation op = SpotifyKaraokeTransport.operations.get(i);
            if (op.kind.equals(kind)) return op;
        }
        throw new AssertionError("No " + kind + " command");
    }
    private static EventSubscription stream() {
        return SpotifyKaraokeTransport.streams.get(SpotifyKaraokeTransport.streams.size() - 1);
    }
    private static void complete(Operation operation) {
        // Intentionally deliver even after dispose: model a callback already in flight.
        Looper.testMainThread(false);
        operation.result.onComplete();
        Handler.drain();
    }
    private static void reject(Operation operation) {
        Looper.testMainThread(false);
        operation.result.onError(new IllegalStateException("fixture-sensitive-host-detail"));
        Handler.drain();
    }
    private static void event(EventSubscription stream, int id, String track, int code) {
        Looper.testMainThread(false);
        stream.callback.onEvent(id, track, "fixture-sensitive-host-detail", code);
        Handler.drain();
    }
    private static final class Page {
        final SpotifyKaraokeController controller;
        State state;
        boolean available;
        String reason;
        int notifications;
        Page() {
            controller = new SpotifyKaraokeController((next, allowed, why) -> {
                check("Listener runs on main", Looper.myLooper() == Looper.getMainLooper());
                state = next; available = allowed; reason = why; notifications++;
            });
            controller.trackChanged(A);
            controller.resume();
            support(A);
        }
        void support(String track) {
            SpotifyKaraokeEligibility.emit(track, true, true);
            SpotifyKaraokePlayback.emit(true, true, "");
        }
        void start() {
            controller.toggle();
            check("Start is loading", state == State.LOADING);
        }
        void activate() {
            start();
            complete(latest("enable"));
            event(stream(), 0, A, 0);
            complete(latest("volume"));
            check("Ready command produces LOW", state == State.LOW);
        }
        void closeCleanly() {
            int before = count("disable");
            controller.destroy();
            if (count("disable") > before) complete(latest("disable"));
        }
    }

    private static void readiness(boolean maskFirst) {
        Page page = new Page();
        check("Supported native route available", page.available);
        page.start();
        check("Events subscribed before native enable", SpotifyKaraokeTransport.order.subList(0, 2)
                .equals(List.of("subscribe", "enable")));
        Operation enable = latest("enable");
        EventSubscription events = stream();
        if (maskFirst) event(events, 0, A, 0); else complete(enable);
        check("One prerequisite never sends volume", count("volume") == 0 && page.state == State.LOADING);
        if (maskFirst) complete(enable); else event(events, 0, A, 0);
        check("Both prerequisites send one LOW volume", count("volume") == 1 && latest("volume").volume == .1f);
        check("Still loading until volume ack", page.state == State.LOADING);
        event(events, 0, A, 0);
        check("Repeated ready event does not duplicate volume", count("volume") == 1);
        complete(latest("volume"));
        check("Volume ack commits LOW", page.state == State.LOW);
        page.closeCleanly();
    }

    private static void anotherTrackIgnored() {
        Page page = new Page();
        page.start();
        complete(latest("enable"));
        event(stream(), 0, B, 0);
        event(stream(), 1, B, 12);
        check("Other track ready/error ignored", count("volume") == 0 && count("disable") == 0
                && page.state == State.LOADING);
        event(stream(), 0, A, 0);
        complete(latest("volume"));
        check("Current track can still become ready", page.state == State.LOW);
        page.closeCleanly();
    }

    private static void trackChangeDuringPreparation() {
        Page page = new Page();
        page.start();
        Operation staleEnable = latest("enable");
        EventSubscription staleStream = stream();
        page.controller.trackChanged(B);
        check("Track change starts disable", page.state == State.STOPPING && count("disable") == 1);
        check("Old commands and stream canceled", staleEnable.disposed && staleStream.disposed);
        complete(staleEnable);
        event(staleStream, 0, A, 0);
        check("Old preparation cannot write new volume", count("volume") == 0 && page.state == State.STOPPING);
        complete(latest("disable"));
        check("Reset completes before new activation", page.state == State.OFF);
        page.support(B);
        page.start();
        event(staleStream, 0, A, 0);
        check("Old stream stays ignored in new operation", count("volume") == 0);
        complete(latest("enable"));
        event(stream(), 0, B, 0);
        complete(latest("volume"));
        check("New track becomes ready independently", page.state == State.LOW);
        page.closeCleanly();
    }

    private static void closeDuringPreparation() {
        Page page = new Page();
        page.start();
        Operation enable = latest("enable");
        EventSubscription events = stream();
        page.controller.pause();
        check("Paused page disables and is unavailable", page.state == State.STOPPING && !page.available);
        complete(enable);
        event(events, 0, A, 0);
        check("Closed page never sets volume", count("volume") == 0);
        complete(latest("disable"));
        check("Closed page receives native reset acknowledgement", page.state == State.OFF);
        page.closeCleanly();
    }

    private static void lateVolumeCannotEnable() {
        Page page = new Page();
        page.start();
        complete(latest("enable"));
        event(stream(), 0, A, 0);
        Operation oldVolume = latest("volume");
        page.controller.toggle();
        complete(oldVolume);
        check("Late initial volume acknowledgement cannot re-enable", page.state == State.STOPPING);
        complete(latest("disable"));
        check("Disable wins over stale volume", page.state == State.OFF);
        page.closeCleanly();
    }

    private static void nativeError(int code, String reason) {
        Page page = new Page();
        page.start();
        event(stream(), 1, A, code);
        check("Native error immediately starts reset", page.state == State.STOPPING && count("disable") == 1);
        complete(latest("disable"));
        check("Native error reason retained", page.state == State.ERROR && reason.equals(page.reason));
        check("Native message never logged", Log.messages.stream().noneMatch(message -> message.contains("fixture-sensitive")));
        page.closeCleanly();
    }

    private static void streamFailure() {
        Page page = new Page();
        page.start();
        Looper.testMainThread(false);
        stream().callback.onError(new IllegalStateException("fixture-sensitive-host-detail"));
        Handler.drain();
        complete(latest("disable"));
        check("Stream failure resets and reports service error", page.state == State.ERROR && "SERVICE_ERROR".equals(page.reason));
        page.closeCleanly();
    }

    private static void volumeSwitch() {
        Page page = new Page();
        page.activate();
        page.controller.changeVolume();
        Operation higher = latest("volume");
        check("HIGH uses native .25 scalar", higher.volume == .25f && page.state == State.LOW);
        page.controller.changeVolume();
        check("Repeated tap while volume pending is ignored", count("volume") == 2);
        complete(higher);
        check("HIGH commits only after acknowledgement", page.state == State.HIGH);
        page.controller.changeVolume();
        check("Return to LOW uses native .1 scalar", latest("volume").volume == .1f);
        complete(latest("volume"));
        check("LOW restored", page.state == State.LOW);
        page.closeCleanly();
    }

    private static void preparationTimeout() {
        Page page = new Page();
        page.start();
        Operation staleEnable = latest("enable");
        Handler.advance(14999);
        check("Preparation does not expire early", page.state == State.LOADING && count("disable") == 0);
        Handler.advance(1);
        check("Preparation timeout resets native state", page.state == State.STOPPING && count("disable") == 1);
        complete(staleEnable);
        check("Timed out enable cannot set volume", count("volume") == 0);
        complete(latest("disable"));
        check("Preparation timeout reported", page.state == State.ERROR && "TIMEOUT".equals(page.reason));
        page.closeCleanly();
    }

    private static void volumeTimeout() {
        Page page = new Page();
        page.activate();
        Handler.advance(20000);
        check("Successful activation canceled preparation timeout", page.state == State.LOW && count("disable") == 0);
        page.controller.changeVolume();
        Operation volume = latest("volume");
        Handler.advance(15000);
        check("Stalled volume change resets", page.state == State.STOPPING && count("disable") == 1);
        complete(volume);
        check("Stalled volume's late ack ignored", page.state == State.STOPPING);
        complete(latest("disable"));
        check("Volume timeout reported", page.state == State.ERROR && "TIMEOUT".equals(page.reason));
        page.closeCleanly();
    }

    private static void failedDisable(boolean timeout) {
        Page page = new Page();
        page.activate();
        page.controller.toggle();
        if (timeout) Handler.advance(8000); else reject(latest("disable"));
        check("Unconfirmed reset is an error", page.state == State.ERROR && "RESET_FAILED".equals(page.reason));
        int enables = count("enable");
        page.controller.toggle();
        check("Further click retries disable before any enable", count("enable") == enables && count("disable") == 2
                && page.state == State.STOPPING);
        complete(latest("disable"));
        page.controller.toggle();
        check("Confirmed reset permits a new enable", count("enable") == enables + 1 && page.state == State.LOADING);
        page.closeCleanly();
    }

    private static void playbackGateLoss() {
        Page page = new Page();
        page.activate();
        SpotifyKaraokePlayback.emit(true, false, "REMOTE_DEVICE");
        check("Remote playback loss disables", count("disable") == 1 && !page.available);
        complete(latest("disable"));
        int before = count("enable");
        page.controller.toggle();
        check("Cannot enable on blocked playback", count("enable") == before && "REMOTE_DEVICE".equals(page.reason));
        page.closeCleanly();
    }

    private static void unavailableRoutes() {
        Page page = new Page();
        SpotifyKaraokeEligibility.emit(A, true, false);
        page.controller.toggle();
        check("Unsupported track cannot enable", count("enable") == 0 && !page.available);
        page.support(A);
        SpotifyKaraokeTransport.ready = false;
        page.controller.toggle();
        check("Uncaptured native transport cannot enable", count("enable") == 0);
        SpotifyKaraokeTransport.ready = true;
        SpotifyKaraokePlayback.emit(false, false, "WAITING");
        page.controller.toggle();
        check("Unknown playback cannot enable", count("enable") == 0 && !page.available);
        page.closeCleanly();
    }

    private static void repeatedUnsupportedDuringReset() {
        Page page = new Page();
        page.activate();
        SpotifyKaraokeEligibility.emit(A, true, false);
        Operation reset = latest("disable");
        Handler.advance(4000);
        SpotifyKaraokeEligibility.emit(A, true, false);
        SpotifyKaraokeEligibility.emit(A, false, false);
        check("Repeated unsupported updates preserve pending native reset", count("disable") == 1 && !reset.disposed);
        Handler.advance(4000);
        check("Repeated unsupported updates cannot postpone reset timeout", page.state == State.ERROR
                && "RESET_FAILED".equals(page.reason));
        complete(reset);
        page.closeCleanly();
    }

    private static void destroyedPageCannotNotify() {
        Page page = new Page();
        page.start();
        page.controller.destroy();
        int before = page.notifications;
        complete(latest("disable"));
        Handler.advance(30000);
        check("Destroyed listener is never called by native or timer callbacks", page.notifications == before);
    }

    private static void reopenedPageAfterFailedReset() {
        Page page = new Page();
        page.activate();
        page.controller.pause();
        page.controller.setListener(null);
        int oldNotifications = page.notifications;
        reject(latest("disable"));
        check("Detached page receives no reset callback", page.notifications == oldNotifications);
        State[] nextState = {null};
        String[] nextReason = {null};
        page.controller.setListener((state, available, reason) -> {
            check("Replacement listener runs on main", Looper.myLooper() == Looper.getMainLooper());
            nextState[0] = state; nextReason[0] = reason;
        });
        page.controller.resume();
        page.support(A);
        check("Reopened page remembers failed native cleanup", nextState[0] == State.ERROR
                && "RESET_FAILED".equals(nextReason[0]));
        int enables = count("enable");
        page.controller.toggle();
        check("Reopened page retries reset before enable", count("enable") == enables
                && count("disable") == 2 && nextState[0] == State.STOPPING);
        complete(latest("disable"));
        page.controller.toggle();
        check("Reopened page can enable after confirmed cleanup", count("enable") == enables + 1
                && nextState[0] == State.LOADING);
        page.closeCleanly();
    }

    private static void repeatedLifecycleCleanup() {
        Page page = new Page();
        page.activate();
        page.controller.pause();
        Operation reset = latest("disable");
        page.controller.pause();
        check("Repeated pause preserves pending native reset", count("disable") == 1 && !reset.disposed);
        page.controller.setListener(null);
        page.controller.trackChanged(B);
        page.controller.resume();
        page.support(B);
        check("Track change on reopened page preserves pending reset", count("disable") == 1 && !reset.disposed);
        int enables = count("enable");
        page.controller.toggle();
        check("Reopened page cannot enable before pending reset finishes", count("enable") == enables);
        complete(reset);
        page.controller.toggle();
        check("Original reset acknowledgement unlocks reopened page", count("enable") == enables + 1);
        page.closeCleanly();
    }

    private static void singleButtonCycle() {
        Page page = new Page();
        page.controller.cycle();
        check("Cycle starts from OFF through native preparation", page.state == State.LOADING);
        complete(latest("enable"));
        event(stream(), 0, A, 0);
        check("First cycle uses LOW scalar", latest("volume").volume == .1f);
        complete(latest("volume"));
        check("First completed cycle is LOW", page.state == State.LOW);
        page.controller.cycle();
        check("Second cycle requests HIGH scalar", latest("volume").volume == .25f && page.state == State.LOW);
        complete(latest("volume"));
        check("Second completed cycle is HIGH", page.state == State.HIGH);
        page.controller.cycle();
        check("Third cycle requests native disable", page.state == State.STOPPING);
        complete(latest("disable"));
        check("Third completed cycle is OFF", page.state == State.OFF);
        check("One complete cycle has exact native command order", SpotifyKaraokeTransport.order
                .equals(List.of("subscribe", "enable", "volume", "volume", "disable")));
        check("HIGH-to-OFF does not send a LOW volume command", count("volume") == 2);
        page.controller.cycle();
        check("Next cycle begins a fresh native preparation", page.state == State.LOADING && count("enable") == 2);
        page.closeCleanly();
    }

    private static void singleButtonRapidVolumeTaps() {
        Page page = new Page();
        page.controller.cycle();
        complete(latest("enable"));
        event(stream(), 0, A, 0);
        Operation initialVolume = latest("volume");
        page.controller.cycle();
        page.controller.cycle();
        check("Repeated taps during initial volume command are ignored", count("volume") == 1
                && count("disable") == 0 && page.state == State.LOADING);
        complete(initialVolume);
        page.controller.cycle();
        Operation higher = latest("volume");
        page.controller.cycle();
        page.controller.cycle();
        check("Repeated taps while LOW advances to HIGH are ignored", count("volume") == 2
                && count("disable") == 0 && page.state == State.LOW);
        complete(higher);
        // Retained changeVolume() API can also leave a HIGH state with a pending command.
        page.controller.changeVolume();
        Operation lower = latest("volume");
        page.controller.cycle();
        check("Pending command in HIGH cannot be interrupted by cycle", count("disable") == 0
                && count("volume") == 3 && page.state == State.HIGH);
        complete(lower);
        page.controller.cycle();
        complete(latest("volume"));
        page.controller.cycle();
        Operation disable = latest("disable");
        page.controller.cycle();
        page.controller.cycle();
        check("Repeated taps while stopping preserve original disable", count("disable") == 1 && !disable.disposed);
        complete(disable);
        check("Rapid taps still finish with confirmed OFF", page.state == State.OFF);
        page.closeCleanly();
    }

    private static void singleButtonCancelsPreparation() {
        Page page = new Page();
        page.controller.cycle();
        Operation oldEnable = latest("enable");
        EventSubscription oldStream = stream();
        page.controller.cycle();
        check("Cycle cancels preparation before mask readiness", page.state == State.STOPPING
                && oldEnable.disposed && oldStream.disposed);
        page.controller.cycle();
        complete(oldEnable);
        event(oldStream, 0, A, 0);
        check("Canceled preparation cannot advance volume", count("volume") == 0 && count("disable") == 1);
        complete(latest("disable"));
        check("Canceled preparation returns to OFF", page.state == State.OFF);
        check("Canceled cycle has exact native command order", SpotifyKaraokeTransport.order
                .equals(List.of("subscribe", "enable", "disable")));
        page.closeCleanly();
    }

    private static void singleButtonLifecycleRace() {
        Page page = new Page();
        page.activate();
        page.controller.cycle();
        Operation oldVolume = latest("volume");
        page.controller.pause();
        check("Page close still cancels pending cycle volume", page.state == State.STOPPING && oldVolume.disposed);
        complete(oldVolume);
        check("Late cycle volume ack cannot resurrect closed page", page.state == State.STOPPING);
        complete(latest("disable"));
        page.controller.resume();
        page.support(A);
        page.controller.cycle();
        Operation oldEnable = latest("enable");
        EventSubscription oldStream = stream();
        page.controller.trackChanged(B);
        complete(oldEnable);
        event(oldStream, 0, A, 0);
        check("Track change wins over restarted cycle callbacks", page.state == State.STOPPING && count("volume") == 2);
        complete(latest("disable"));
        check("Lifecycle interrupted cycles finish OFF", page.state == State.OFF);
        page.closeCleanly();
    }

    private static void singleButtonRetriesFailedReset() {
        Page page = new Page();
        page.activate();
        page.controller.cycle();
        complete(latest("volume"));
        page.controller.cycle();
        reject(latest("disable"));
        check("Cycle reset failure is retained", page.state == State.ERROR && "RESET_FAILED".equals(page.reason));
        int enables = count("enable");
        page.controller.cycle();
        check("Cycle in ERROR retries cleanup instead of enabling", count("enable") == enables
                && count("disable") == 2 && page.state == State.STOPPING);
        complete(latest("disable"));
        page.controller.cycle();
        check("Cycle can restart after confirmed cleanup", count("enable") == enables + 1 && page.state == State.LOADING);
        page.closeCleanly();
    }

    private static void songSupportControlsVisibility() {
        SpotifyKaraokeController controller = new SpotifyKaraokeController(null);
        check("Button hidden before a page is active", !controller.visible());
        controller.trackChanged(A);
        controller.resume();
        SpotifyKaraokePlayback.emit(true, true, "");
        check("Button hidden while song support is unknown", !controller.visible());
        SpotifyKaraokeEligibility.emit(A, true, false);
        check("Button hidden for loaded unsupported song", !controller.visible());
        SpotifyKaraokeEligibility.emit(A, false, true);
        check("Unloaded support data cannot show button", !controller.visible());
        SpotifyKaraokeEligibility.emit(A, true, true);
        check("Supported active song shows available button", controller.visible() && controller.available());
        SpotifyKaraokePlayback.emit(true, false, "REMOTE_DEVICE");
        check("Supported remote song stays visible but disabled", controller.visible() && !controller.available()
                && "REMOTE_DEVICE".equals(controller.reason()));
        SpotifyKaraokePlayback.emit(true, false, "OFFLINE");
        check("Supported offline song stays visible but disabled", controller.visible() && !controller.available());
        SpotifyKaraokePlayback.emit(true, true, "");
        SpotifyKaraokeTransport.ready = false;
        check("Missing transport does not erase known song support", controller.visible() && !controller.available());
        SpotifyKaraokeTransport.ready = true;
        controller.trackChanged(B);
        check("New song hides button until support arrives", !controller.visible());
        SpotifyKaraokeEligibility.emit(A, true, true);
        check("Stale support cannot show new song button", !controller.visible());
        controller.destroy();
    }

    private static void cleanupStaysVisibleAfterSupportLoss() {
        Page page = new Page();
        page.activate();
        SpotifyKaraokeEligibility.emit(A, true, false);
        check("Support loss keeps pending cleanup visible", page.state == State.STOPPING
                && page.controller.visible() && !page.controller.available());
        reject(latest("disable"));
        check("Failed cleanup stays visible on unsupported song", page.state == State.ERROR && page.controller.visible());
        page.controller.cycle();
        check("Visible unsupported-song control retries cleanup", page.state == State.STOPPING
                && count("disable") == 2 && count("enable") == 1);
        complete(latest("disable"));
        check("Confirmed cleanup hides unsupported-song button", !page.controller.visible());
        page.support(A);
        check("New support shows button again", page.controller.visible());
        page.controller.cycle();
        page.controller.pause();
        check("Page pause preserves unconfirmed cleanup visibility", page.controller.visible() && !page.controller.available());
        complete(latest("disable"));
        check("Inactive page hidden after confirmed cleanup", !page.controller.visible());
        page.closeCleanly();
    }

    private static void scenario(String name, Runnable test) {
        Handler.reset();
        SpotifyKaraokeTransport.reset();
        SpotifyKaraokeEligibility.listener = null;
        SpotifyKaraokePlayback.listener = null;
        Log.messages.clear();
        scenarios++;
        try { test.run(); System.out.println("PASS " + name); }
        catch (Throwable failure) {
            failures.add(name + ": " + failure);
            System.out.println("FAIL " + name + ": " + failure);
        }
    }

    public static void main(String[] args) {
        scenario("mask ready before enable acknowledgement", () -> readiness(true));
        scenario("enable acknowledgement before mask ready", () -> readiness(false));
        scenario("events from another track ignored", KaraokeControllerRegression::anotherTrackIgnored);
        scenario("track change while preparing and stale callbacks", KaraokeControllerRegression::trackChangeDuringPreparation);
        scenario("close while preparing", KaraokeControllerRegression::closeDuringPreparation);
        scenario("stale volume completion cannot enable", KaraokeControllerRegression::lateVolumeCannotEnable);
        scenario("native account-disabled error 12", () -> nativeError(12, "UNSUPPORTED_ACCOUNT"));
        scenario("native mask error", () -> nativeError(10, "MASK_ERROR"));
        scenario("stream failure", KaraokeControllerRegression::streamFailure);
        scenario("LOW/HIGH volume switch", KaraokeControllerRegression::volumeSwitch);
        scenario("preparation timeout", KaraokeControllerRegression::preparationTimeout);
        scenario("volume timeout", KaraokeControllerRegression::volumeTimeout);
        scenario("failed reset blocks further enable", () -> failedDisable(false));
        scenario("reset timeout blocks further enable", () -> failedDisable(true));
        scenario("playback gate loss", KaraokeControllerRegression::playbackGateLoss);
        scenario("unsupported and unknown routes remain closed", KaraokeControllerRegression::unavailableRoutes);
        scenario("repeated unsupported updates during reset", KaraokeControllerRegression::repeatedUnsupportedDuringReset);
        scenario("destroyed listener ignores late callbacks", KaraokeControllerRegression::destroyedPageCannotNotify);
        scenario("reopened page remembers failed reset", KaraokeControllerRegression::reopenedPageAfterFailedReset);
        scenario("repeated lifecycle cleanup preserves pending reset", KaraokeControllerRegression::repeatedLifecycleCleanup);
        scenario("one-button OFF LOW HIGH OFF native command order", KaraokeControllerRegression::singleButtonCycle);
        scenario("one-button repeated taps during volume and reset", KaraokeControllerRegression::singleButtonRapidVolumeTaps);
        scenario("one-button cancels pending preparation", KaraokeControllerRegression::singleButtonCancelsPreparation);
        scenario("one-button page and track lifecycle races", KaraokeControllerRegression::singleButtonLifecycleRace);
        scenario("one-button safely retries failed reset", KaraokeControllerRegression::singleButtonRetriesFailedReset);
        scenario("song support controls button visibility", KaraokeControllerRegression::songSupportControlsVisibility);
        scenario("native cleanup remains visible after support loss", KaraokeControllerRegression::cleanupStaysVisibleAfterSupportLoss);
        System.out.println(scenarios + " scenarios, " + assertions + " assertions, " + failures.size() + " failures");
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }
}
''',
}


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    fixtures = []
    for name, content in FILES.items():
        path = WORK / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        fixtures.append(path)
    classes = WORK / "classes"
    classes.mkdir(exist_ok=True)
    subprocess.run([java_tool("javac"), "--release", "17", "-d", str(classes),
                    str(SOURCE), *map(str, fixtures)], check=True)
    result = subprocess.run([java_tool("java"), "-cp", str(classes),
                             "dev.ivlyrics.spotify.KaraokeControllerRegression"],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    report = "Production karaoke controller; deterministic scheduling and synthetic native collaborators.\n"
    report += "Does not verify live Spotify service eligibility, routing, masks, or audio effects.\n"
    report += f"SpotifyKaraokeController.java SHA256 {hashlib.sha256(SOURCE.read_bytes()).hexdigest()}\n"
    report += result.stdout
    (WORK / "result.txt").write_text(report)
    print(report, end="")
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
