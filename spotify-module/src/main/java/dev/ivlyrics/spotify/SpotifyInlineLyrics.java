package dev.ivlyrics.spotify;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import kr.ivlis.ivlyricsandroid.IvLyricsBridge;

/** Supplies Spotify 9.1.80's Now Playing lyric line and its existing visibility option. */
final class SpotifyInlineLyrics {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<WeakReference<InlineHost>> HOSTS = new ArrayList<>();
    private static final AtomicBoolean UPDATE_PENDING = new AtomicBoolean();
    private static volatile boolean nativeLayoutAllows;
    private static volatile boolean nativePlaybackAllows;
    private static volatile boolean userEnabled;
    private static volatile boolean visibilityObserved;
    private static volatile boolean playbackObserved;
    private static volatile boolean rendererObserved;
    private static volatile boolean canvasBlocked;
    private static volatile boolean mixingActive;
    private static volatile boolean automobileActive;
    private static volatile boolean automobileAllowed;
    private static volatile boolean videoPlayerBlocked;
    private static String lastGateLog = "";

    private SpotifyInlineLyrics() {}

    static List<XC_MethodHook.Unhook> install(ClassLoader loader) {
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            Class<?> renderer = Class.forName("p.nba0", false, loader);
            Class<?> visibility = Class.forName("p.cp5", false, loader);
            Class<?> availability = Class.forName("p.jm7", false, loader);
            Class<?> model = Class.forName("p.lba0", false, loader);
            Class<?> composer = Class.forName("p.ryz", false, loader);
            Object unit = Class.forName("p.x181", true, loader).getField("a").get(null);
            Field renderBranch = intField(renderer, "a");
            Field visibilityBranch = intField(visibility, "a");
            Field playbackBranch = intField(availability, "a");
            Field layoutBlocked = booleanField(visibility, "b");
            Field preferenceEnabled = booleanField(visibility, "c");
            Field playbackAvailable = booleanField(visibility, "d");
            Field mixingBlocked = booleanField(visibility, "e");
            Field automobileDevice = booleanField(availability, "b");
            Field automobileException = booleanField(availability, "d");
            Field videoPlayerMode = booleanField(availability, "c");
            Method render = renderer.getDeclaredMethod("c1", Object.class, Object.class,
                    Object.class, Object.class, Object.class);
            Method visibilityUpdate = visibility.getDeclaredMethod("invokeSuspend", Object.class);
            Method availabilityUpdate = availability.getDeclaredMethod("invokeSuspend", Object.class);

            // jm7 case 1 combines native lyric presence with automobile/video-player restrictions.
            // b is Connect AUTOMOBILE, d allows that device, c is track_player=video.
            // This result also feeds the Lyrics On/Off menu. Observing it without replacing
            // the native lyric check leaves the option absent on songs resolved by ivLyrics.
            hooks.add(XposedBridge.hookMethod(availabilityUpdate, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    if (param.hasThrowable() || playbackBranch.getInt(param.thisObject) != 1) return;
                    automobileActive = automobileDevice.getBoolean(param.thisObject);
                    automobileAllowed = automobileException.getBoolean(param.thisObject);
                    videoPlayerBlocked = videoPlayerMode.getBoolean(param.thisObject);
                    nativePlaybackAllows = Gates.playbackAllows(automobileActive, automobileAllowed, videoPlayerBlocked);
                    param.setResult(nativePlaybackAllows);
                    playbackObserved = true;
                    updateHosts();
                }
            }));
            // cp5 case 2 observes the existing key_lyrics_on_npv_visible setting, canvas
            // visibility and mixing transitions. DJ/automix is a playback mode, not a
            // reason to hide song lyrics; the shared preview excludes DJ speech itself.
            hooks.add(XposedBridge.hookMethod(visibilityUpdate, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    if (param.hasThrowable() || visibilityBranch.getInt(param.thisObject) != 2) return;
                    userEnabled = preferenceEnabled.getBoolean(param.thisObject);
                    canvasBlocked = layoutBlocked.getBoolean(param.thisObject);
                    mixingActive = mixingBlocked.getBoolean(param.thisObject);
                    nativeLayoutAllows = Gates.layoutAllows(canvasBlocked, mixingActive);
                    param.setResult(Gates.show(nativeLayoutAllows,
                            playbackAvailable.getBoolean(param.thisObject), userEnabled));
                    visibilityObserved = true;
                    updateHosts();
                }
            }));
            hooks.addAll(SpotifyInlineLyricsMenu.install(loader));
            hooks.add(XposedBridge.hookMethod(render, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    if (renderBranch.getInt(param.thisObject) != 0 || !model.isInstance(param.args[1])
                            || !composer.isInstance(param.args[3])) return;
                    if (!rendererObserved) { rendererObserved = true; updateHosts(); }
                    try {
                        ComposeAdapter.prepareInline(param.args[3]);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        Log.e("ivLyricsInline", "Inline mappings unavailable before composition", error);
                        return;
                    }
                    // AndroidView opens Compose groups. Once entered, never run the native
                    // body against that partially advanced composer on an invocation failure.
                    param.setResult(unit);
                    try {
                        ComposeAdapter.renderInline(param.args[3]);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        Throwable cause = error instanceof InvocationTargetException
                                && error.getCause() != null ? error.getCause() : error;
                        Log.e("ivLyricsInline", "Native inline composition failed", cause);
                        param.setThrowable(cause);
                    }
                }
            }));
            Log.i("ivLyricsInline", "Now Playing inline lyrics hooks installed");
            return hooks;
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            Log.e("ivLyricsInline", "Inline hooks unavailable: " + error.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    private static Field intField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getField(name);
        if (field.getType() != int.class) throw new NoSuchFieldException(name);
        return field;
    }

    private static Field booleanField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getField(name);
        if (field.getType() != boolean.class) throw new NoSuchFieldException(name);
        return field;
    }

    static View createView(Context context) { return new InlineHost(context); }

    /** Pure player restrictions; Spotify lyric presence is intentionally not an input. */
    static final class Gates {
        static boolean playbackAllows(boolean automobileActive, boolean automobileAllowed, boolean videoPlayerBlocked) {
            return (!automobileActive || automobileAllowed) && !videoPlayerBlocked;
        }
        static boolean layoutAllows(boolean canvasBlocked, boolean mixing) {
            return !canvasBlocked;
        }
        static boolean show(boolean layout, boolean playback, boolean enabled) {
            return layout && playback && enabled;
        }
    }

    private static void logGateState() {
        int mounted = 0;
        for (WeakReference<InlineHost> reference : HOSTS) {
            InlineHost host = reference.get();
            if (host != null && host.preview != null) mounted++;
        }
        String state = "enabled=" + userEnabled + " layout=" + nativeLayoutAllows
                + " playback=" + nativePlaybackAllows + " canvas=" + canvasBlocked
                + " mixing=" + mixingActive + " automobile=" + automobileActive
                + " automobileAllowed=" + automobileAllowed + " videoPlayerBlocked=" + videoPlayerBlocked
                + " observed=" + visibilityObserved + "/" + playbackObserved + "/" + rendererObserved
                + " hosts=" + HOSTS.size() + " mounted=" + mounted;
        if (!state.equals(lastGateLog)) {
            lastGateLog = state;
            Log.d("ivLyricsInline", state);
        }
    }

    private static void updateHosts() {
        if (!UPDATE_PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            UPDATE_PENDING.set(false);
            HOSTS.removeIf(reference -> reference.get() == null);
            for (WeakReference<InlineHost> reference : HOSTS) {
                InlineHost host = reference.get();
                if (host != null) host.update();
            }
            logGateState();
        });
    }

    private static final class InlineHost extends FrameLayout {
        private View preview;

        InlineHost(Context context) {
            super(context);
            setTag("ivLyricsInlineHost");
            setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            HOSTS.add(new WeakReference<>(this));
            update();
            logGateState();
        }

        @Override protected void onDetachedFromWindow() {
            HOSTS.removeIf(reference -> reference.get() == null || reference.get() == this);
            removeAllViews();
            preview = null;
            super.onDetachedFromWindow();
            logGateState();
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (width != oldWidth || height != oldHeight) {
                Log.d("ivLyricsInline", "host size=" + width + "x" + height + " mounted=" + (preview != null));
            }
        }

        void update() {
            if (!isAttachedToWindow()) return;
            boolean show = Gates.show(nativeLayoutAllows, nativePlaybackAllows, userEnabled);
            if (show && preview == null) {
                preview = IvLyricsBridge.createInlinePreview(getContext());
                addView(preview, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            } else if (!show && preview != null) {
                removeView(preview);
                preview = null;
            }
        }
    }
}
