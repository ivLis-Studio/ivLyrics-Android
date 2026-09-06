package dev.ivlyrics.spotify;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Reads native per-track eligibility without replacing Spotify's flags or lyrics responses. */
public final class SpotifyKaraokeEligibility {
    public interface Listener {
        /** Called on MAIN. Loaded means Spotify's own lyrics request completed successfully. */
        void onEligibility(String uri, boolean loaded, boolean supported);
    }

    private static final String TAG = "ivLyricsKaraoke";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long CACHE_MS = 120_000L;
    private static final long TIMEOUT_MS = 12_000L;
    private static final Pattern TRACK_URI = Pattern.compile("spotify:track:[A-Za-z0-9]{22}");
    private static volatile ProviderBinding providerBinding;
    private static volatile ProviderDependencies providerDependencies;
    private static final Map<String, Cached> CACHE = new LinkedHashMap<String, Cached>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> entry) {
            return size() > 128;
        }
    };
    private static Listener listener;
    private static Object repository;
    private static volatile Object playerSource;
    private static volatile Object playerSourceOwner;
    private static Object subscription;
    private static String currentUri = "";
    private static String pendingUri = "";
    private static int generation;
    private static boolean loaded;
    private static boolean supported;
    private static boolean mappingWarningLogged;

    private SpotifyKaraokeEligibility() {}

    /** A listener is installed only while the embedded full lyrics page is active. */
    public static void setListener(Listener value) {
        runOnMain(() -> {
            listener = value;
            if (value == null) {
                cancel();
                return;
            }
            publish();
            requestIfNeeded();
        });
    }

    /** The controller supplies the current media-session URI, never a queued card's URI. */
    public static void onTrack(String value) {
        final String uri = canonicalUri(value);
        runOnMain(() -> {
            if (!currentUri.equals(uri)) {
                cancel();
                currentUri = uri;
                loaded = false;
                supported = false;
                publish();
            }
            requestIfNeeded();
        });
    }

    /** Captures dependencies from a provider constructor (null track) or availability call. */
    public static void onCardProvider(Object provider, Object track) {
        if (provider == null || !provider.getClass().getName().equals("p.yca0")) return;
        try {
            ProviderBinding binding = providerBindingFor(provider.getClass());
            if (track != null) {
                if (!binding.contextTrack.isInstance(track)) return;
                String uri = canonicalUri((String) binding.trackUri.invoke(track));
                if (uri.isEmpty()) return;
            }
            ProviderDependencies captured = providerDependencies;
            // The provider fields and branch are final, but R8's merged mapper.c is
            // mutable. Recheck that identity before reusing its validated dependencies.
            if (captured == null || captured.provider != provider
                    || captured.repository != binding.mapperRepository.get(captured.mapper)) {
                captured = binding.capture(provider);
                if (captured == null) return;
            }
            if (captured.source == null) captured = binding.withPlayerSource(captured);
            providerDependencies = captured;
            final Object capturedRepository = captured.repository;
            final Object capturedSource = captured.source;
            final Object pool = captured.pool;
            runOnMain(() -> {
                boolean changed = playerSource != capturedSource;
                if (repository != capturedRepository) {
                    changed = true;
                    cancel();
                    repository = capturedRepository;
                    CACHE.clear();
                    loaded = false;
                    supported = false;
                }
                playerSource = capturedSource;
                playerSourceOwner = capturedSource != null ? pool : null;
                if (changed) publish();
                requestIfNeeded();
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            runOnMain(() -> {
                if (!mappingWarningLogged) {
                    mappingWarningLogged = true;
                    Log.w(TAG, "Native karaoke support mapping unavailable: " + error.getClass().getSimpleName());
                }
            });
        }
    }

    /** Existing native Flowable; this method does not subscribe or create a second player. */
    public static Object playerStateSource() {
        return playerSource;
    }

    public static void stop() {
        runOnMain(() -> {
            listener = null;
            currentUri = "";
            loaded = false;
            supported = false;
            cancel();
        });
    }

    private static void requestIfNeeded() {
        if (listener == null || currentUri.isEmpty() || repository == null || !pendingUri.isEmpty()) return;
        Cached cached = CACHE.get(currentUri);
        if (cached != null && SystemClock.elapsedRealtime() - cached.at < CACHE_MS) {
            if (loaded != cached.loaded || supported != cached.supported) {
                loaded = cached.loaded;
                supported = cached.supported;
                publish();
            }
            return;
        }
        final String uri = currentUri;
        final int requestGeneration = ++generation;
        pendingUri = uri;
        try {
            ClassLoader loader = repository.getClass().getClassLoader();
            // This is the original repository path, including its cache/online decision and the
            // gea0/vja0 service's original android-libs-singalong remote-config query value.
            Object single = repository.getClass().getMethod("b", String.class, String.class)
                    .invoke(repository, uri, null);
            Class<?> singleType = Class.forName("io.reactivex.rxjava3.core.Single", false, loader);
            if (!singleType.isInstance(single)) throw new IllegalStateException("Native lyrics result type changed");
            Class<?> schedulerType = Class.forName("io.reactivex.rxjava3.core.Scheduler", false, loader);
            Object ioScheduler = Class.forName("io.reactivex.rxjava3.schedulers.Schedulers", true, loader)
                    .getField("c").get(null);
            single = singleType.getMethod("subscribeOn", schedulerType).invoke(single, ioScheduler);
            single = singleType.getMethod("timeout", long.class, TimeUnit.class)
                    .invoke(single, TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Class<?> consumer = Class.forName("io.reactivex.rxjava3.functions.Consumer", false, loader);
            Object success = Proxy.newProxyInstance(loader, new Class<?>[]{consumer}, (proxy, method, args) -> {
                if (method.getName().equals("accept")) {
                    Object response = args[0];
                    MAIN.post(() -> consume(requestGeneration, uri, response));
                    return null;
                }
                return objectMethod(proxy, method, args);
            });
            Object failure = Proxy.newProxyInstance(loader, new Class<?>[]{consumer}, (proxy, method, args) -> {
                if (method.getName().equals("accept")) {
                    // Never log the native Throwable, network response, or credentials.
                    MAIN.post(() -> finish(requestGeneration, uri, false, false));
                    return null;
                }
                return objectMethod(proxy, method, args);
            });
            subscription = singleType.getMethod("subscribe", consumer, consumer).invoke(single, success, failure);
            MAIN.postDelayed(() -> finish(requestGeneration, uri, false, false), TIMEOUT_MS);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "Native karaoke support request unavailable: " + error.getClass().getSimpleName());
            finish(requestGeneration, uri, false, false);
        }
    }

    private static void consume(int expectedGeneration, String uri, Object response) {
        if (!isCurrent(expectedGeneration, uri)) return;
        try {
            if (response == null || !response.getClass().getName().equals("p.eve")) {
                throw new IllegalStateException("Native lyrics response shape changed");
            }
            Object lyrics = field(response, "a");
            if (!lyrics.getClass().getName().equals("p.q4a0")) throw new IllegalStateException("Native lyrics shape changed");
            Object vocalRemoval = field(lyrics, "g");
            if (!vocalRemoval.getClass().getName().equals("p.p4a0")) throw new IllegalStateException("Native support shape changed");
            finish(expectedGeneration, uri, true, (Boolean) field(vocalRemoval, "a"));
        } catch (ReflectiveOperationException | RuntimeException error) {
            finish(expectedGeneration, uri, false, false);
        }
    }

    private static boolean isCurrent(int expectedGeneration, String uri) {
        return listener != null && generation == expectedGeneration && uri.equals(currentUri) && uri.equals(pendingUri);
    }

    private static void finish(int expectedGeneration, String uri, boolean resultLoaded, boolean resultSupported) {
        if (!isCurrent(expectedGeneration, uri)) return;
        loaded = resultLoaded;
        supported = resultLoaded && resultSupported;
        CACHE.put(uri, new Cached(loaded, supported));
        cancel();
        publish();
    }

    private static void cancel() {
        generation++;
        pendingUri = "";
        Object current = subscription;
        subscription = null;
        if (current == null) return;
        try {
            Class.forName("io.reactivex.rxjava3.disposables.Disposable", false, current.getClass().getClassLoader())
                    .getMethod("dispose").invoke(current);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static void publish() {
        Listener current = listener;
        if (current != null) {
            try { current.onEligibility(currentUri, loaded, supported); }
            catch (RuntimeException error) {
                Log.w(TAG, "Native karaoke eligibility listener failed: " + error.getClass().getSimpleName());
            }
        }
    }

    private static void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else MAIN.post(action);
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        return field.get(owner);
    }

    private static String canonicalUri(String value) {
        return value != null && TRACK_URI.matcher(value).matches() ? value : "";
    }

    private static ProviderBinding providerBindingFor(Class<?> type) throws ReflectiveOperationException {
        ProviderBinding cached = providerBinding;
        if (cached == null || cached.providerType != type) {
            synchronized (SpotifyKaraokeEligibility.class) {
                cached = providerBinding;
                if (cached == null || cached.providerType != type) {
                    cached = new ProviderBinding(type);
                    providerDependencies = null;
                    providerBinding = cached;
                }
            }
        }
        return cached;
    }

    /** One validated provider is retained; no card, ContextTrack or Activity is cached. */
    private static final class ProviderDependencies {
        final Object provider, mapper, repository, pool, source;

        ProviderDependencies(Object provider, Object mapper, Object repository, Object pool, Object source) {
            this.provider = provider;
            this.mapper = mapper;
            this.repository = repository;
            this.pool = pool;
            this.source = source;
        }
    }

    private static final class ProviderBinding {
        final Class<?> providerType, contextTrack, mapperType, repositoryType, flowable;
        final Field providerMapper, providerPool, mapperBranch, mapperRepository, repositoryService;
        final Method trackUri;
        Method poolState, stateFlow;

        ProviderBinding(Class<?> type) throws ReflectiveOperationException {
            providerType = type;
            ClassLoader loader = type.getClassLoader();
            contextTrack = Class.forName("com.spotify.player.model.ContextTrack", false, loader);
            trackUri = contextTrack.getMethod("uri");
            mapperType = Class.forName("p.yh", false, loader);
            repositoryType = Class.forName("p.eda0", false, loader);
            flowable = Class.forName("io.reactivex.rxjava3.core.Flowable", false, loader);
            providerMapper = finalField(type, "c");
            providerPool = finalField(type, "a");
            mapperBranch = finalField(mapperType, "a");
            mapperRepository = mapperType.getField("c");
            repositoryService = finalField(repositoryType, "d");
            try {
                poolState = Class.forName("p.fzi0", false, loader).getMethod("c");
                stateFlow = Class.forName("p.o131", false, loader).getMethod("e");
            } catch (ReflectiveOperationException ignored) {
                // Optional player-source compatibility cannot block support lookup.
            }
        }

        ProviderDependencies capture(Object provider) throws ReflectiveOperationException {
            Object mapper = providerMapper.get(provider);
            if (!mapperType.isInstance(mapper) || mapperBranch.getInt(mapper) != 26) return null;
            Object repository = mapperRepository.get(mapper);
            if (!repositoryType.isInstance(repository)) return null;
            Object service = repositoryService.get(repository);
            if (service == null) return null;
            String name = service.getClass().getName();
            if (!name.equals("p.gea0") && !name.equals("p.vja0")) return null;
            return new ProviderDependencies(provider, mapper, repository, providerPool.get(provider), null);
        }

        ProviderDependencies withPlayerSource(ProviderDependencies value) {
            Object source = null;
            try {
                if (value.pool == playerSourceOwner) source = playerSource;
                else if (poolState != null && stateFlow != null) source = stateFlow.invoke(poolState.invoke(value.pool));
                if (!flowable.isInstance(source)) source = null;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Retry a not-yet-ready pool on the next provider event.
            }
            return source == null ? value : new ProviderDependencies(value.provider, value.mapper,
                    value.repository, value.pool, source);
        }

        private static Field finalField(Class<?> type, String name) throws ReflectiveOperationException {
            Field field = type.getField(name);
            if (!Modifier.isFinal(field.getModifiers())) throw new NoSuchFieldException("Mutable provider dependency");
            return field;
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
        if (method.getName().equals("equals")) return proxy == args[0];
        if (method.getName().equals("toString")) return "ivLyrics native karaoke support callback";
        throw new UnsupportedOperationException(method.getName());
    }

    private static final class Cached {
        final boolean loaded;
        final boolean supported;
        final long at = SystemClock.elapsedRealtime();

        Cached(boolean loaded, boolean supported) {
            this.loaded = loaded;
            this.supported = supported;
        }
    }
}
