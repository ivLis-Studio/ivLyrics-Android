package dev.ivlyrics.spotify;

import android.app.Activity;
import android.content.Context;
import android.media.session.MediaSession;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Map;
import kr.ivlis.ivlyricsandroid.IvLyricsBridge;
import kr.ivlis.ivlyricsandroid.SpotifyMetadataBridge;

/** Calls the host's own Compose AndroidView interop. No second Compose runtime is bundled. */
public final class ComposeAdapter {
    private static final String TAG = "ivLyricsPatch";
    private static volatile Interop interop;
    private static volatile Availability availability;

    private ComposeAdapter() {}

    public static void initialize(Context context) {
        IvLyricsBridge.initialize(context);
        Log.i(TAG, "ivLyrics initialized in " + context.getPackageName());
    }

    public static void onMediaSession(MediaSession session, String sessionTag) {
        // Spotify also constructs Media3 sessions for other surfaces. Bind only its music session.
        // The constructor tag is available on API 26; MediaController.getTag requires API 30.
        if (session == null || !"spotify-media-session".equals(sessionTag)) return;
        IvLyricsBridge.onMediaSession(session);
        SpotifyMetadataBridge.onMediaSession(session);
        Log.i(TAG, "Captured in-process MediaSession");
    }

    public static void openLyrics(Activity activity) {
        IvLyricsBridge.openLyrics(activity);
        activity.finish();
    }

    public static void renderCard(Object composer) {
        try {
            Interop current = interop;
            if (current == null) {
                synchronized (ComposeAdapter.class) {
                    current = interop;
                    if (current == null) interop = current = new Interop(composer);
                }
            }
            // Give Compose the ratio too, including its intrinsic measurement of AndroidView.
            current.render.invoke(null, current.factory, current.squareModifier, null, composer, 0, 4);
        } catch (ReflectiveOperationException error) {
            // Failing visibly is preferable to rendering the obsolete native lyric source.
            throw new IllegalStateException("ivLyrics could not mount Spotify's Compose AndroidView", error);
        }
    }

    /** Returns null only for an unrelated branch of an R8-merged provider, or on a mapping error. */
    public static Object cardAvailability(Object provider, Object track) {
        try {
            boolean oldVersion = provider.getClass().getName().equals("p.pf30");
            if (oldVersion && provider.getClass().getField("a").getInt(provider) != 0) return null;
            Availability current = availabilityFor(oldVersion, track);
            Object card = createSongCard(oldVersion, track);
            return card == null ? current.empty : current.maybeJust.newInstance(card);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.e(TAG, "Could not supply ivLyrics card availability", error);
            return null;
        }
    }

    /** Creates a song card without consulting Spotify's lyrics or server-selected card list. */
    static Object createSongCard(boolean oldVersion, Object track) throws ReflectiveOperationException {
        Availability current = availabilityFor(oldVersion, track);
        String uri = (String) current.uri.invoke(track);
        @SuppressWarnings("unchecked") Map<String, String> metadata = (Map<String, String>) current.metadata.invoke(track);
        // Keep non-song content outside the lyrics card; ivLyrics resolves song lyrics independently.
        if (uri == null || !uri.startsWith("spotify:track:") || !value(metadata, "parent_episode_uri").isEmpty()) {
            return null;
        }
        Object model = current.model.newInstance(current.emptyLyrics, uri, "absent",
                value(metadata, "title"), value(metadata, "artist_name"), value(metadata, "image_url"));
        return current.card.newInstance(model);
    }

    private static Availability availabilityFor(boolean oldVersion, Object track) throws ReflectiveOperationException {
        Availability current = availability;
        if (current == null) {
            synchronized (ComposeAdapter.class) {
                current = availability;
                if (current == null) availability = current = new Availability(oldVersion, track.getClass().getClassLoader());
            }
        }
        return current;
    }

    private static String value(Map<String, String> metadata, String key) {
        String result = metadata.get(key);
        return result == null ? "" : result;
    }

    private static final class Interop implements InvocationHandler {
        final Method render;
        final Object factory;
        final Object squareModifier;

        Interop(Object composer) throws ReflectiveOperationException {
            ClassLoader loader = composer.getClass().getClassLoader();
            boolean oldVersion = composer.getClass().getName().equals("p.gge");
            Class<?> function = Class.forName(oldVersion ? "p.heu" : "kotlin.jvm.functions.Function1", false, loader);
            Class<?> modifier = Class.forName(oldVersion ? "androidx.compose.ui.Modifier" : "p.wmg0", false, loader);
            Class<?> owner = Class.forName(oldVersion ? "p.f34" : "p.jy81", false, loader);
            render = owner.getDeclaredMethod("b", function, modifier, function, composer.getClass(), int.class, int.class);
            render.setAccessible(true);
            Constructor<?> aspectRatio = Class.forName(oldVersion ? "p.xx4" : "p.p16", false, loader)
                    .getDeclaredConstructor(float.class, boolean.class);
            aspectRatio.setAccessible(true);
            squareModifier = aspectRatio.newInstance(1f, false);
            if (!modifier.isInstance(squareModifier)) throw new IllegalStateException("Invalid aspect ratio modifier");
            factory = Proxy.newProxyInstance(loader, new Class<?>[]{function}, this);
            Log.i(TAG, "Mounted native Compose interop " + owner.getName());
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("invoke")) return IvLyricsBridge.createPreview((Context) args[0]);
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("toString")) return "ivLyrics native card factory";
            throw new UnsupportedOperationException(method.toString());
        }
    }

    private static final class Availability {
        final Method uri;
        final Method metadata;
        final Object emptyLyrics;
        final Constructor<?> model;
        final Constructor<?> card;
        final Constructor<?> maybeJust;
        final Object empty;

        Availability(boolean oldVersion, ClassLoader loader) throws ReflectiveOperationException {
            Class<?> track = Class.forName("com.spotify.player.model.ContextTrack", false, loader);
            uri = track.getMethod("uri");
            metadata = track.getMethod("metadata");
            Class<?> state = Class.forName(oldVersion ? "p.ea30" : "p.f5a0", true, loader);
            Object seed = state.getField(oldVersion ? "i" : "j").get(null);
            emptyLyrics = state.getField("a").get(seed);
            Class<?> modelType = Class.forName(oldVersion ? "p.q930" : "p.s4a0", false, loader);
            model = modelType.getConstructor(emptyLyrics.getClass(), String.class, String.class, String.class, String.class, String.class);
            card = Class.forName(oldVersion ? "p.uba0" : "p.iui0", false, loader).getConstructor(modelType);
            maybeJust = Class.forName("io.reactivex.rxjava3.internal.operators.maybe.MaybeJust", false, loader).getConstructor(Object.class);
            Class<?> emptyType = Class.forName("io.reactivex.rxjava3.internal.operators.maybe.MaybeEmpty", true, loader);
            Object emptyValue = null;
            for (Field field : emptyType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == emptyType) {
                    field.setAccessible(true);
                    emptyValue = field.get(null);
                    break;
                }
            }
            if (emptyValue == null) throw new NoSuchFieldException("MaybeEmpty singleton");
            empty = emptyValue;
            Log.i(TAG, "Native lyrics availability replaced with ivLyrics song card");
        }
    }
}
