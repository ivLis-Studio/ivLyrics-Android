package dev.ivlyrics.spotify;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Removes the second native lyric gate only while building the song's context menu. */
final class SpotifyInlineLyricsMenu {
    private static final ThreadLocal<Integer> MENU_DEPTH = ThreadLocal.withInitial(() -> 0);

    private SpotifyInlineLyricsMenu() {}

    static List<XC_MethodHook.Unhook> install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> track = Class.forName("com.spotify.player.model.ContextTrack", false, loader);
        Method menu = Class.forName("p.b9p0", false, loader).getDeclaredMethod("apply", Object.class);
        Method available = Class.forName("p.d2w0", false, loader).getDeclaredMethod("e", track);
        Method uri = track.getMethod("uri");
        Method metadata = track.getMethod("metadata");
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            // b9p0.apply builds npv_lyrics_toggle only after both pca0.a (jm7) and
            // d2w0.e accept the track. Keep d2w0.e unchanged for its other consumers,
            // including native lyric requests and vocal-removal eligibility.
            hooks.add(XposedBridge.hookMethod(menu, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    MENU_DEPTH.set(MENU_DEPTH.get() + 1);
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    int depth = MENU_DEPTH.get() - 1;
                    if (depth <= 0) MENU_DEPTH.remove();
                    else MENU_DEPTH.set(depth);
                }
            }));
            hooks.add(XposedBridge.hookMethod(available, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws ReflectiveOperationException {
                    if (MENU_DEPTH.get() == 0 || param.hasThrowable() || !track.isInstance(param.args[0])) return;
                    @SuppressWarnings("unchecked") Map<String, String> values =
                            (Map<String, String>) metadata.invoke(param.args[0]);
                    param.setResult(songAllows((String) uri.invoke(param.args[0]),
                            values == null ? null : values.get("parent_episode_uri")));
                }
            }));
            return hooks;
        } catch (RuntimeException error) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            throw error;
        }
    }

    static boolean songAllows(String uri, String parentEpisode) {
        return uri != null && uri.startsWith("spotify:track:")
                && (parentEpisode == null || parentEpisode.trim().isEmpty());
    }
}
