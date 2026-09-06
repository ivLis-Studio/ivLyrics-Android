package dev.ivlyrics.spotify;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Page-scoped native karaoke commands. All state and callbacks run on the main thread. */
final class SpotifyKaraokeController {
    enum State { OFF, LOADING, LOW, HIGH, STOPPING, ERROR }
    interface Listener { void onChanged(State state, boolean available, String reason); }

    private static final String TAG = "ivLyricsKaraoke";
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private State state = State.OFF;
    private String uri = "";
    private String error = "";
    private String playbackReason = "WAITING";
    private boolean pageActive;
    private boolean supportKnown;
    private boolean supported;
    private boolean playbackKnown;
    private boolean playbackAllowed;
    private boolean nativeMayBeEnabled;
    private boolean maskReady;
    private boolean enableAcknowledged;
    private boolean volumePending;
    private int generation;
    private SpotifyKaraokeTransport.Subscription events;
    private SpotifyKaraokeTransport.Subscription command;
    private Runnable timeout;

    SpotifyKaraokeController(Listener listener) { this.listener = listener; }

    void setListener(Listener listener) { this.listener = listener; publish(); }

    void resume() {
        pageActive = true;
        SpotifyKaraokeEligibility.setListener((track, loaded, hasSupport) -> {
            if (!pageActive || !uri.equals(track)) return;
            supportKnown = true;
            supported = loaded && hasSupport;
            if (!supported && nativeMayBeEnabled && state != State.STOPPING) disable();
            publish();
        });
        SpotifyKaraokePlayback.setListener((known, allowed, reason) -> {
            if (!pageActive) return;
            playbackKnown = known;
            playbackAllowed = allowed;
            playbackReason = reason;
            if ((!known || !allowed) && nativeMayBeEnabled && state != State.STOPPING) disable();
            publish();
        });
        SpotifyKaraokeEligibility.onTrack(uri);
        publish();
    }

    void trackChanged(String track) {
        String next = track == null ? "" : track;
        if (uri.equals(next)) return;
        uri = next;
        supportKnown = false;
        supported = false;
        error = "";
        if (nativeMayBeEnabled) {
            if (state != State.STOPPING) disable();
        }
        else { cancelOperation(); state = State.OFF; }
        if (pageActive) SpotifyKaraokeEligibility.onTrack(uri);
        publish();
    }

    void pause() {
        pageActive = false;
        SpotifyKaraokeEligibility.stop();
        SpotifyKaraokePlayback.setListener(null);
        if (nativeMayBeEnabled) {
            if (state != State.STOPPING) disable();
        } else cancelOperation();
    }

    void destroy() { pause(); listener = null; }

    String reason() {
        if (!error.isEmpty()) return error;
        if (uri.isEmpty()) return "NO_TRACK";
        if (!playbackKnown || !playbackAllowed) return playbackReason;
        if (!supportKnown || !SpotifyKaraokeTransport.isReady()) return "WAITING";
        if (!supported) return "UNSUPPORTED_TRACK";
        return "";
    }

    boolean available() {
        return pageActive && !uri.isEmpty() && supportKnown && supported
                && playbackKnown && playbackAllowed && SpotifyKaraokeTransport.isReady();
    }

    /** Hide unknown/unsupported songs, but keep an unconfirmed native reset reachable. */
    boolean visible() {
        return nativeMayBeEnabled || (pageActive && supportKnown && supported);
    }

    /** One button cycles OFF -> LOW -> HIGH -> OFF after each native acknowledgement. */
    void cycle() {
        if (state == State.STOPPING || volumePending) return;
        if (state == State.LOW) changeVolume();
        else toggle();
    }

    void toggle() {
        if (state == State.STOPPING) return;
        if (nativeMayBeEnabled) { disable(); return; }
        if (!available()) { publish(); return; }
        cancelOperation();
        final int token = generation;
        final String requestedTrack = uri;
        error = "";
        state = State.LOADING;
        nativeMayBeEnabled = true;
        maskReady = false;
        enableAcknowledged = false;
        volumePending = false;
        publish();
        armTimeout(token, "TIMEOUT");
        // Queue subscription before enabling, as the native screen does.
        events = SpotifyKaraokeTransport.subscribeEvents(new SpotifyKaraokeTransport.Events() {
            @Override public void onEvent(int id, String track, String message, int code) {
                main.post(() -> {
                    if (!current(token) || !uri.equals(requestedTrack)) return;
                    if (track != null && !track.isEmpty() && !track.equals(requestedTrack)) return;
                    if (id != 0) {
                        Log.w(TAG, "Native karaoke error code=" + code);
                        fail(code == 12 ? "UNSUPPORTED_ACCOUNT" : "MASK_ERROR");
                    } else if (state == State.LOADING) {
                        maskReady = true;
                        Log.i(TAG, "Native karaoke mask ready");
                        finishEnable(token);
                    }
                });
            }
            @Override public void onError(Throwable failure) {
                main.post(() -> { if (current(token)) fail("SERVICE_ERROR"); });
            }
        });
        command = SpotifyKaraokeTransport.postStatus(true, result(token, () -> {
            enableAcknowledged = true;
            finishEnable(token);
        }, "SERVICE_ERROR"));
    }

    private void finishEnable(int token) {
        if (!current(token) || !maskReady || !enableAcknowledged || volumePending) return;
        volumePending = true;
        command = SpotifyKaraokeTransport.setVolume(0.1f, result(token, () -> {
            cancelTimeout();
            volumePending = false;
            state = State.LOW;
            Log.i(TAG, "Native karaoke enabled LOW");
            publish();
        }, "SERVICE_ERROR"));
    }

    void changeVolume() {
        if (volumePending || (state != State.LOW && state != State.HIGH)) return;
        final int token = generation;
        State next = state == State.LOW ? State.HIGH : State.LOW;
        volumePending = true;
        armTimeout(token, "TIMEOUT");
        command = SpotifyKaraokeTransport.setVolume(next == State.LOW ? 0.1f : 0.25f,
                result(token, () -> {
                    cancelTimeout();
                    volumePending = false;
                    state = next;
                    Log.i(TAG, "Native karaoke volume " + next);
                    publish();
                }, "SERVICE_ERROR"));
    }

    private void fail(String reason) {
        error = reason;
        disable();
    }

    private void disable() {
        cancelOperation();
        if (!nativeMayBeEnabled) { state = State.OFF; publish(); return; }
        final int token = generation;
        state = State.STOPPING;
        publish();
        timeout = () -> {
            if (current(token)) {
                error = "RESET_FAILED";
                state = State.ERROR;
                publish();
            }
        };
        main.postDelayed(timeout, 8000L);
        command = SpotifyKaraokeTransport.postStatus(false, new SpotifyKaraokeTransport.Result() {
            @Override public void onComplete() {
                main.post(() -> {
                    if (!current(token)) return;
                    cancelTimeout();
                    nativeMayBeEnabled = false;
                    state = error.isEmpty() ? State.OFF : State.ERROR;
                    Log.i(TAG, "Native karaoke disabled");
                    publish();
                });
            }
            @Override public void onError(Throwable failure) {
                main.post(() -> {
                    if (!current(token)) return;
                    cancelTimeout();
                    error = "RESET_FAILED";
                    state = State.ERROR;
                    Log.w(TAG, "Native karaoke disable failed");
                    publish();
                });
            }
        });
    }

    private SpotifyKaraokeTransport.Result result(int token, Runnable complete, String failureReason) {
        return new SpotifyKaraokeTransport.Result() {
            @Override public void onComplete() { main.post(() -> { if (current(token)) complete.run(); }); }
            @Override public void onError(Throwable failure) {
                main.post(() -> { if (current(token)) fail(failureReason); });
            }
        };
    }

    private boolean current(int token) { return token == generation; }

    private void armTimeout(int token, String reason) {
        cancelTimeout();
        timeout = () -> { if (current(token)) fail(reason); };
        main.postDelayed(timeout, 15000L);
    }

    private void cancelTimeout() {
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
    }

    private void cancelOperation() {
        generation++;
        cancelTimeout();
        if (events != null) events.dispose();
        if (command != null) command.dispose();
        events = null;
        command = null;
        volumePending = false;
    }

    private void publish() {
        if (listener != null) listener.onChanged(state, available(), reason());
    }
}
