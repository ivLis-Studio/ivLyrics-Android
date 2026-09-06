package kr.ivlis.ivlyricsandroid;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Uses Spotify's existing public TrackV4 metadata service; no authentication data is accessed. */
final class SpotifyNativeMetadataClient {
    private static final String TAG = "ivLyricsMetadata";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Long> RETRY_AFTER = new LinkedHashMap<String, Long>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> value) { return size() > 256; }
    };
    private static Object client;
    private static boolean availabilityLogged;
    private static Object subscription;
    private static String pendingUri = "";
    private static int generation;

    private SpotifyNativeMetadataClient() {}

    static void attach(Object value) {
        MAIN.post(() -> {
            if (value == null || !value.getClass().getName().equals("p.w1x")) return;
            if (client == value) return;
            client = value;
            if (!availabilityLogged) {
                availabilityLogged = true;
                Log.i(TAG, "Native TrackV4 metadata client available");
            }
            TrackSnapshot track = NowPlayingService.getLatestSnapshot();
            if (track != null) request(track.mediaId, !track.isrc.isEmpty());
        });
    }

    static void request(String value, boolean alreadyResolved) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> request(value, alreadyResolved));
            return;
        }
        String uri = NativeTrackIdentity.canonicalUri(value);
        if (!pendingUri.isEmpty() && (!pendingUri.equals(uri) || alreadyResolved)) cancel();
        if (uri.isEmpty() || alreadyResolved || client == null || uri.equals(pendingUri)) return;
        Long retryAfter = RETRY_AFTER.get(uri);
        if (retryAfter != null && retryAfter > SystemClock.elapsedRealtime()) return;
        RETRY_AFTER.put(uri, SystemClock.elapsedRealtime() + 120_000L);
        pendingUri = uri;
        final int requestGeneration = ++generation;
        try {
            ClassLoader loader = client.getClass().getClassLoader();
            Class<?> function = Class.forName("kotlin.jvm.functions.Function1", false, loader);
            Class<?> trackType = Class.forName("p.fs61", false, loader);
            Object unit = Class.forName("p.x181", false, loader).getField("a").get(null);
            Object query = Proxy.newProxyInstance(loader, new Class<?>[]{function}, (proxy, method, args) -> {
                if (method.getName().equals("invoke")) {
                    args[0].getClass().getMethod("a", String.class, Class.class, boolean.class)
                        .invoke(args[0], uri, trackType, false);
                    return unit;
                }
                return proxyObjectMethod(proxy, method, args);
            });
            Class<?> requestType = Class.forName("p.yh", false, loader);
            Object request = requestType.getConstructor(String.class, boolean.class, function)
                .newInstance("ivlyrics-isrc", false, query);
            Object observable = client.getClass().getMethod("b", requestType).invoke(client, request);
            Class<?> observableType = Class.forName("io.reactivex.rxjava3.core.Observable", false, loader);
            observable = observableType.getMethod("timeout", long.class, TimeUnit.class)
                .invoke(observable, 12L, TimeUnit.SECONDS);
            Class<?> consumerType = Class.forName("io.reactivex.rxjava3.functions.Consumer", false, loader);
            Class<?> actionType = Class.forName("io.reactivex.rxjava3.functions.Action", false, loader);
            Object success = Proxy.newProxyInstance(loader, new Class<?>[]{consumerType}, (proxy, method, args) -> {
                if (method.getName().equals("accept")) {
                    Object response = args[0];
                    MAIN.post(() -> consume(requestGeneration, uri, trackType, response));
                    return null;
                }
                return proxyObjectMethod(proxy, method, args);
            });
            Object failure = Proxy.newProxyInstance(loader, new Class<?>[]{consumerType}, (proxy, method, args) -> {
                if (method.getName().equals("accept")) {
                    // Deliberately do not read or stringify the native Throwable argument.
                    MAIN.post(() -> finish(requestGeneration, "Native ISRC request unavailable"));
                    return null;
                }
                return proxyObjectMethod(proxy, method, args);
            });
            Object complete = Proxy.newProxyInstance(loader, new Class<?>[]{actionType}, (proxy, method, args) -> {
                if (method.getName().equals("run")) {
                    MAIN.post(() -> finish(requestGeneration, "Native ISRC request completed"));
                    return null;
                }
                return proxyObjectMethod(proxy, method, args);
            });
            subscription = observableType.getMethod("subscribe", consumerType, consumerType, actionType)
                .invoke(observable, success, failure, complete);
            Log.i(TAG, "Requesting current-track public ISRC through native metadata");
            // An absolute bound also covers streams which repeatedly emit incomplete metadata.
            MAIN.postDelayed(() -> finish(requestGeneration, "Native ISRC request time limit"), 12_000L);
        } catch (ReflectiveOperationException | RuntimeException error) {
            finish(requestGeneration, "Native ISRC request setup failed: " + error.getClass().getSimpleName());
        }
    }

    private static void consume(int expectedGeneration, String uri, Class<?> trackType, Object response) {
        if (expectedGeneration != generation) return;
        try {
            Object result = response.getClass().getMethod("a", Class.class, String.class).invoke(response, trackType, uri);
            if (!uri.equals(NativeTrackIdentity.field(result, "a"))) return;
            Object track = NativeTrackIdentity.field(result, "b");
            if (track == null) return; // Keep observing a loading response until metadata or timeout.
            if (!trackType.isInstance(track)) throw new IllegalStateException("Unexpected native metadata type");
            boolean resolved = SpotifyMetadataBridge.onRequestedTrackV4(uri, track);
            finish(expectedGeneration, resolved ? "Native ISRC lookup resolved" : "Native metadata has no ISRC");
        } catch (ReflectiveOperationException | RuntimeException error) {
            finish(expectedGeneration, "Native ISRC response shape failed: " + error.getClass().getSimpleName());
        }
    }

    private static void finish(int expectedGeneration, String status) {
        if (expectedGeneration != generation) return;
        Log.i(TAG, status);
        cancel();
    }

    private static void cancel() {
        generation++;
        Object current = subscription;
        subscription = null;
        pendingUri = "";
        if (current == null) return;
        try {
            Class<?> disposable = Class.forName("io.reactivex.rxjava3.disposables.Disposable", false, current.getClass().getClassLoader());
            disposable.getMethod("dispose").invoke(current);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static Object proxyObjectMethod(Object proxy, Method method, Object[] args) {
        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
        if (method.getName().equals("equals")) return proxy == args[0];
        if (method.getName().equals("toString")) return "ivLyrics public metadata callback";
        throw new UnsupportedOperationException(method.getName());
    }
}
