package dev.ivlyrics.spotify;

import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Keeps ivLyrics first among Spotify 9.1.80.2221's native Now Playing scroll cards. */
final class SpotifyLyricsOrder {
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private SpotifyLyricsOrder() {}

    static XC_MethodHook.Unhook install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner = Class.forName("p.ivi0", false, loader);
        Class<?> state = Class.forName("p.gx21", false, loader);
        Class<?> section = Class.forName("p.dvi0", false, loader);
        Class<?> lyrics = Class.forName("p.iui0", false, loader);
        Method filter = owner.getDeclaredMethod("c", state, Set.class);
        if (!Modifier.isStatic(filter.getModifiers()) || filter.getReturnType() != ArrayList.class
                || !state.isInterface() || !section.isInterface() || !section.isAssignableFrom(lyrics)) {
            throw new NoSuchMethodException("Native Now Playing section ordering shape changed");
        }

        // dx1 case 20 passes this filtered list to the adapter for both its initial state and
        // subsequent card updates. Reorder the output, leaving the source state and providers intact.
        return XposedBridge.hookMethod(filter, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || !(param.getResult() instanceof ArrayList<?>)) return;
                ArrayList<?> original = (ArrayList<?>) param.getResult();
                for (Object value : original) {
                    if (!section.isInstance(value)) return;
                }
                ArrayList<?> ordered = lyricsFirst(original, lyrics);
                if (ordered == original) return;
                param.setResult(ordered);
                if (REPORTED.compareAndSet(false, true)) {
                    Log.i("ivLyricsXposed", "ivLyrics prioritized in native Now Playing card order");
                }
            }
        });
    }

    /** Stable partition: every item and its identity survive, including multiple lyric sections. */
    static <T> ArrayList<T> lyricsFirst(ArrayList<T> source, Class<?> lyricsType) {
        boolean sawOther = false;
        boolean needsMove = false;
        for (T item : source) {
            if (lyricsType.isInstance(item)) {
                if (sawOther) needsMove = true;
            } else {
                sawOther = true;
            }
        }
        if (!needsMove) return source;
        ArrayList<T> ordered = new ArrayList<>(source.size());
        for (T item : source) if (lyricsType.isInstance(item)) ordered.add(item);
        for (T item : source) if (!lyricsType.isInstance(item)) ordered.add(item);
        return ordered;
    }
}
