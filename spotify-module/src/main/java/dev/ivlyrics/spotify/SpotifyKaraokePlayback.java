package dev.ivlyrics.spotify;

import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Read-only Spotify 9.1.80 playback gates for its native karaoke service. */
public final class SpotifyKaraokePlayback {
    public interface Listener {
        void onPlayback(boolean known, boolean allowed, String reason);
    }

    private static final String TAG = "ivLyricsKaraoke";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static final AtomicBoolean QUEUED = new AtomicBoolean();
    private static boolean attempted;
    private static boolean supported;
    private static boolean offlineKnown;
    private static boolean offline;
    private static boolean qualityKnown;
    private static boolean lowQuality;
    private static long qualityTimestamp = Long.MIN_VALUE;
    private static boolean routeKnown;
    private static boolean local;

    // MediaController registration and listener ownership are confined to the main thread.
    private static MediaController controller;
    private static MediaController.Callback controllerCallback;
    private static Listener listener;
    private static Listener deliveredListener;
    private static String deliveredReason;
    private static boolean deliveredKnown;
    private static boolean deliveredAllowed;

    private SpotifyKaraokePlayback() { }

    /** Compatibility failure is isolated from the lyrics UI; no host values are replaced. */
    public static synchronized void install(ClassLoader loader, boolean oldVersion) {
        synchronized (LOCK) {
            if (attempted) return;
            attempted = true;
        }
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            if (oldVersion) return;
            Binding mapping = new Binding(loader);
            hooks.addAll(XposedBridge.hookAllConstructors(mapping.settings, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) mapping.readSettings(param.thisObject);
                }
            }));
            hooks.addAll(XposedBridge.hookAllConstructors(mapping.playerState, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) mapping.readQuality(param.thisObject, null);
                }
            }));
            // Also observe immutable instances created before the installation point.
            hooks.add(XposedBridge.hookMethod(mapping.qualityGetter, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) mapping.readQuality(param.thisObject, param.getResult());
                }
            }));
            synchronized (LOCK) { supported = true; }
        } catch (Throwable ignored) {
            for (XC_MethodHook.Unhook hook : hooks) {
                try { hook.unhook(); } catch (Throwable ignoredUnhook) { }
            }
            Log.w(TAG, "Playback gate mapping unavailable");
        } finally {
            scheduleDelivery();
        }
    }

    /** The caller must already have filtered this to Spotify's own MediaSession tag. */
    public static void onMediaSession(MediaSession session) {
        try {
            MediaController next = session == null ? null : session.getController();
            MAIN.post(() -> {
                if (controller != next) {
                    detachController();
                    controller = next;
                }
                attachController();
                refreshRoute();
            });
        } catch (Throwable ignored) {
            // Do not clear a newer session if an obsolete constructor cannot provide its controller.
        }
    }

    /** Callback values and all subsequent changes are delivered on Android's main thread. */
    public static void setListener(Listener next) {
        MAIN.post(() -> {
            listener = next;
            deliveredListener = null;
            if (next == null) detachController();
            else {
                attachController();
                refreshRoute();
                deliver();
            }
        });
    }

    private static void attachController() {
        if (listener == null || controller == null || controllerCallback != null) return;
        MediaController owner = controller;
        MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onAudioInfoChanged(MediaController.PlaybackInfo info) {
                if (controller == owner && controllerCallback == this) updateRoute(info);
            }

            @Override public void onSessionDestroyed() {
                if (controller != owner || controllerCallback != this) return;
                detachController();
                controller = null;
                updateRoute(null);
            }
        };
        try {
            controllerCallback = callback;
            owner.registerCallback(callback, MAIN);
        } catch (Throwable ignored) {
            controllerCallback = null;
            updateRoute(null);
        }
    }

    private static void detachController() {
        if (controller != null && controllerCallback != null) {
            try { controller.unregisterCallback(controllerCallback); } catch (Throwable ignored) { }
        }
        controllerCallback = null;
    }

    private static void refreshRoute() {
        try {
            // A route whose changes cannot be observed is not sufficient to enable karaoke.
            updateRoute(controller == null || controllerCallback == null ? null : controller.getPlaybackInfo());
        } catch (Throwable ignored) { updateRoute(null); }
    }

    private static void updateRoute(MediaController.PlaybackInfo info) {
        int type = info == null ? 0 : info.getPlaybackType();
        synchronized (LOCK) {
            routeKnown = type == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL
                    || type == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE;
            local = type == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
        }
        scheduleDelivery();
    }

    private static void scheduleDelivery() {
        if (QUEUED.compareAndSet(false, true)) {
            MAIN.post(() -> {
                QUEUED.set(false);
                deliver();
            });
        }
    }

    private static void deliver() {
        if (listener == null) return;
        String reason;
        boolean known;
        boolean allowed;
        synchronized (LOCK) {
            if (!attempted) reason = "WAITING";
            else if (!supported) reason = "UNSUPPORTED";
            else if (offlineKnown && offline) reason = "OFFLINE";
            else if (routeKnown && !local) reason = "REMOTE";
            else if (qualityKnown && lowQuality) reason = "LOW_QUALITY";
            else if (!offlineKnown || !qualityKnown || !routeKnown) reason = "WAITING";
            else reason = "";
            allowed = reason.isEmpty();
            known = !reason.equals("WAITING") && !reason.equals("UNSUPPORTED");
        }
        if (listener == deliveredListener && known == deliveredKnown && allowed == deliveredAllowed
                && reason.equals(deliveredReason)) return;
        deliveredListener = listener;
        deliveredKnown = known;
        deliveredAllowed = allowed;
        deliveredReason = reason;
        try { listener.onPlayback(known, allowed, reason); } catch (Throwable ignored) { }
    }

    private static final class Binding {
        final Class<?> settings;
        final Class<?> playerState;
        final Field offlineField;
        final Field qualityField;
        final Field timestampField;
        final Method qualityGetter;
        final Method optionalPresent;
        final Method optionalGet;
        final Method bitrateLevel;
        boolean settingsFailed;
        boolean qualityFailed;

        Binding(ClassLoader loader) throws ReflectiveOperationException {
            settings = Class.forName("p.xky0", false, loader);
            playerState = Class.forName("com.spotify.player.model.AutoValue_PlayerState", false, loader);
            offlineField = settings.getDeclaredField("a");
            if (offlineField.getType() != boolean.class) throw new NoSuchFieldException("offline");
            qualityField = playerState.getDeclaredField("playbackQuality");
            timestampField = playerState.getDeclaredField("timestamp");
            qualityGetter = playerState.getDeclaredMethod("playbackQuality");
            Class<?> optional = Class.forName("p.pgk0", false, loader);
            optionalPresent = optional.getMethod("c");
            optionalGet = optional.getMethod("b");
            bitrateLevel = Class.forName("com.spotify.player.model.PlaybackQuality", false, loader)
                    .getMethod("bitrateLevel");
            offlineField.setAccessible(true);
            qualityField.setAccessible(true);
            timestampField.setAccessible(true);
            qualityGetter.setAccessible(true);
        }

        void readSettings(Object value) {
            try {
                boolean next = offlineField.getBoolean(value);
                synchronized (LOCK) {
                    if (offlineKnown && offline == next) return;
                    offlineKnown = true;
                    offline = next;
                }
                scheduleDelivery();
            } catch (Throwable ignored) {
                synchronized (LOCK) {
                    offlineKnown = false;
                    if (!settingsFailed) {
                        settingsFailed = true;
                        Log.w(TAG, "Playback offline state unavailable");
                    }
                }
                scheduleDelivery();
            }
        }

        void readQuality(Object state, Object observedOptional) {
            try {
                long timestamp = timestampField.getLong(state);
                Object optional = observedOptional == null ? qualityField.get(state) : observedOptional;
                Object quality = optional != null && Boolean.TRUE.equals(optionalPresent.invoke(optional))
                        ? optionalGet.invoke(optional) : null;
                Object level = quality == null ? null : bitrateLevel.invoke(quality);
                String name = level instanceof Enum<?> ? ((Enum<?>) level).name() : "UNKNOWN";
                boolean known = !name.equals("UNKNOWN");
                boolean low = name.equals("LOW");
                synchronized (LOCK) {
                    // Getter calls on an older immutable snapshot cannot replace a newer gate.
                    if (timestamp < qualityTimestamp) return;
                    qualityTimestamp = timestamp;
                    if (qualityKnown == known && lowQuality == low) return;
                    qualityKnown = known;
                    lowQuality = low;
                }
                scheduleDelivery();
            } catch (Throwable ignored) {
                synchronized (LOCK) {
                    qualityKnown = false;
                    if (!qualityFailed) {
                        qualityFailed = true;
                        Log.w(TAG, "Playback quality state unavailable");
                    }
                }
                scheduleDelivery();
            }
        }
    }
}
