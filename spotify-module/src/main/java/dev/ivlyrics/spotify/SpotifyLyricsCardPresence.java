package dev.ivlyrics.spotify;

import android.util.Log;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Restores a song card when Spotify's server omits lyrics from the scroll sections. */
final class SpotifyLyricsCardPresence {
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private SpotifyLyricsCardPresence() {}

    static XC_MethodHook.Unhook install(ClassLoader loader, boolean oldVersion) throws ReflectiveOperationException {
        Class<?> track = Class.forName("com.spotify.player.model.ContextTrack", false, loader);
        Class<?> section = Class.forName(oldVersion ? "p.kca0" : "p.dvi0", false, loader);
        Class<?> lyrics = Class.forName(oldVersion ? "p.uba0" : "p.iui0", false, loader);
        Class<?> event = Class.forName(oldVersion ? "p.skr" : "p.mdw", false, loader);
        Constructor<?> ready = oldVersion
                ? event.getDeclaredConstructor(track, String.class, List.class)
                : event.getDeclaredConstructor(track, String.class, List.class,
                        List.class, Class.forName("p.mk61", false, loader));
        if (!section.isAssignableFrom(lyrics)) {
            throw new NoSuchMethodException("Native Now Playing section shape changed");
        }
        // This event carries its own ContextTrack, preserving the host's stale-response handling.
        // Never inject from a global or previously playing track.
        return XposedBridge.hookMethod(ready, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.args[2] instanceof List<?>)) return;
                List<?> original = (List<?>) param.args[2];
                for (Object value : original) {
                    if (!section.isInstance(value) || lyrics.isInstance(value)) return;
                }
                try {
                    Object card = ComposeAdapter.createSongCard(oldVersion, param.args[0]);
                    List<?> result = withLyricsIfMissing(original, lyrics, card);
                    if (result == original) return;
                    param.args[2] = result;
                    if (REPORTED.compareAndSet(false, true)) {
                        Log.i("ivLyricsLSPatch", "Restored ivLyrics card omitted from native scroll sections");
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    Log.e("ivLyricsLSPatch", "CARD_PRESENCE_FAILED " + error.getClass().getSimpleName());
                }
            }
        });
    }

    static List<?> withLyricsIfMissing(List<?> source, Class<?> lyricsType, Object candidate) {
        if (candidate == null || !lyricsType.isInstance(candidate)) return source;
        for (Object item : source) if (lyricsType.isInstance(item)) return source;
        ArrayList<Object> result = new ArrayList<>(source.size() + 1);
        result.add(candidate);
        result.addAll(source);
        return result;
    }
}
