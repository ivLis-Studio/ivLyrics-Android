package dev.ivlyrics.spotify;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Module-only access to Spotify's existing KaraokeService; it changes no availability flags.
 * Calls and subscription setup run serially off the UI thread. Callbacks run on the host Rx
 * emission thread (or our worker for setup failures), so callers must marshal UI updates.
 * A completed command is only an acknowledgement: enabling still requires MASK_READY.
 */
public final class SpotifyKaraokeTransport {
    public interface Result {
        void onComplete();
        void onError(Throwable error);
    }

    public interface Events {
        void onEvent(int id, String trackUri, String errorMessage, int errorCode);
        void onError(Throwable error);
    }

    public interface Subscription { void dispose(); }

    private static final String SERVICE = "spotify.karaoke_esperanto.proto.KaraokeService";
    private static final String TRANSPORT = "com.spotify.esperanto.esperantocosmos.CosmosTransport";
    private static final byte[] EMPTY = new byte[0];
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ivLyrics-karaoke");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Binding binding;

    private SpotifyKaraokeTransport() { }

    /** Best-effort installation; a Spotify compatibility failure cannot abort lyrics UI hooks. */
    public static synchronized void install(ClassLoader loader) {
        if (binding != null) return;
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            Binding candidate = new Binding(loader);
            XC_MethodHook capture = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) candidate.transport = param.thisObject;
                }
            };
            hooks.addAll(XposedBridge.hookAllConstructors(candidate.transportClass, capture));
            // Calls also reveal an instance whose constructor ran before Application.attach.
            hooks.add(XposedBridge.hookMethod(candidate.callSingle, capture));
            hooks.add(XposedBridge.hookMethod(candidate.callStream, capture));
            binding = candidate;
        } catch (Throwable ignored) {
            for (XC_MethodHook.Unhook hook : hooks) {
                try { hook.unhook(); } catch (Throwable ignoredUnhook) { }
            }
        }
    }

    /** Transport presence only, not account/track eligibility or native mask readiness. */
    public static boolean isReady() {
        Binding current = binding;
        return current != null && current.transport != null;
    }

    public static Subscription subscribeEvents(Events events) {
        Objects.requireNonNull(events, "events");
        return start("SubscribeToEvents", EMPTY, null, events);
    }

    public static Subscription postStatus(boolean enabled, Result result) {
        Objects.requireNonNull(result, "result");
        // Proto3 enum field 1: ENABLED=0 (the default), DISABLED=1.
        return start("PostStatus", enabled ? EMPTY : new byte[]{8, 1}, result, null);
    }

    public static Subscription setVolume(float volume, Result result) {
        Objects.requireNonNull(result, "result");
        if (!Float.isFinite(volume) || volume < 0f || volume > 1f) {
            Pending pending = new Pending(result, null);
            WORKER.execute(() -> pending.fail("Invalid karaoke vocal volume"));
            return pending;
        }
        int bits = Float.floatToIntBits(volume);
        // Protobuf float field 1, fixed32 in little-endian order.
        byte[] payload = new byte[]{13, (byte) bits, (byte) (bits >>> 8),
                (byte) (bits >>> 16), (byte) (bits >>> 24)};
        return start("PostVocalVolume", payload, result, null);
    }

    private static Subscription start(String method, byte[] payload, Result result, Events events) {
        Pending pending = new Pending(result, events);
        WORKER.execute(() -> {
            if (pending.closed.get()) return;
            Binding current = binding;
            Object transport = current == null ? null : current.transport;
            if (transport == null) {
                pending.fail("Spotify karaoke transport is not ready");
                return;
            }
            try {
                pending.owner = current;
                Object source = (events == null ? current.callSingle : current.callStream)
                        .invoke(transport, SERVICE, method, payload);
                if (pending.closed.get()) return;
                Object success = consumer(current, value -> pending.accept(value));
                Object failure = consumer(current, value -> pending.fail("Spotify karaoke service request failed"));
                Object disposable = (events == null ? current.subscribeSingle : current.subscribeStream)
                        .invoke(source, success, failure);
                pending.attach(disposable);
            } catch (Throwable ignored) {
                // Host exception messages/causes can contain request details. Do not propagate them.
                pending.fail("Spotify karaoke service is unavailable");
            }
        });
        return pending;
    }

    private interface Receiver { void accept(Object value); }

    private static Object consumer(Binding current, Receiver receiver) {
        return Proxy.newProxyInstance(current.consumer.getClassLoader(), new Class<?>[]{current.consumer},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        return "ivLyrics karaoke callback";
                    }
                    if (method.getName().equals("accept") && args != null && args.length == 1) {
                        // Never let module callbacks escape into Spotify's Rx global error handler.
                        try { receiver.accept(args[0]); } catch (Throwable ignored) { }
                    }
                    return null;
                });
    }

    private static final class Binding {
        final Class<?> transportClass;
        final Class<?> consumer;
        final Method callSingle;
        final Method callStream;
        final Method subscribeSingle;
        final Method subscribeStream;
        final Method dispose;
        volatile Object transport;

        Binding(ClassLoader loader) throws ReflectiveOperationException {
            transportClass = Class.forName(TRANSPORT, false, loader);
            consumer = Class.forName("io.reactivex.rxjava3.functions.Consumer", false, loader);
            Class<?> single = Class.forName("io.reactivex.rxjava3.core.Single", false, loader);
            Class<?> observable = Class.forName("io.reactivex.rxjava3.core.Observable", false, loader);
            Class<?> disposable = Class.forName("io.reactivex.rxjava3.disposables.Disposable", false, loader);
            callSingle = transportClass.getMethod("callSingle", String.class, String.class, byte[].class);
            callStream = transportClass.getMethod("callStream", String.class, String.class, byte[].class);
            if (!single.isAssignableFrom(callSingle.getReturnType())
                    || !observable.isAssignableFrom(callStream.getReturnType()) || !consumer.isInterface()) {
                throw new NoSuchMethodException("Unsupported Spotify reactive transport");
            }
            subscribeSingle = single.getMethod("subscribe", consumer, consumer);
            subscribeStream = observable.getMethod("subscribe", consumer, consumer);
            dispose = disposable.getMethod("dispose");
        }
    }

    private static final class Pending implements Subscription {
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicReference<Object> disposable = new AtomicReference<>();
        final Result result;
        final Events events;
        volatile Binding owner;

        Pending(Result result, Events events) {
            this.result = result;
            this.events = events;
        }

        void attach(Object value) {
            disposable.set(value);
            // Handles disposal or a synchronous terminal callback before subscribe() returns.
            if (closed.get()) release();
        }

        @Override public void dispose() {
            closed.set(true);
            release();
        }

        void release() {
            Object value = disposable.getAndSet(null);
            Binding current = owner;
            if (value != null && current != null) {
                WORKER.execute(() -> {
                    try { current.dispose.invoke(value); } catch (Throwable ignored) { }
                });
            }
        }

        void accept(Object value) {
            if (closed.get()) return;
            if (!(value instanceof byte[])) {
                fail("Invalid Spotify karaoke response");
                return;
            }
            if (events != null) {
                Event event;
                try { event = Event.parse((byte[]) value); }
                catch (RuntimeException ignored) {
                    fail("Invalid Spotify karaoke event");
                    return;
                }
                if (!closed.get()) {
                    try { events.onEvent(event.id, event.trackUri, event.errorMessage, event.errorCode); }
                    catch (Throwable ignored) { }
                }
            } else if (closed.compareAndSet(false, true)) {
                release();
                // Match Spotify's Completable conversion; MASK_READY determines usable activation.
                try { result.onComplete(); } catch (Throwable ignored) { }
            }
        }

        void fail(String message) {
            if (!closed.compareAndSet(false, true)) return;
            release();
            IllegalStateException error = new IllegalStateException(message);
            try {
                if (events != null) events.onError(error);
                else result.onError(error);
            } catch (Throwable ignored) { }
        }
    }

    /** Small bounded protobuf reader avoids linking a second protobuf runtime into Spotify. */
    private static final class Event {
        int id;
        String trackUri = "";
        String errorMessage = "";
        int errorCode;

        static Event parse(byte[] bytes) {
            if (bytes.length > 65536) throw new IllegalArgumentException();
            Cursor input = new Cursor(bytes);
            Event event = new Event();
            while (input.offset < bytes.length) {
                long tag = input.varint();
                if (tag < 8 || tag > 0xffffffffL) throw new IllegalArgumentException();
                if (tag == 8) event.id = (int) input.varint();
                else if (tag == 18) event.trackUri = input.string();
                else if (tag == 26) event.errorMessage = input.string();
                else if (tag == 32) event.errorCode = (int) input.varint();
                else {
                    switch ((int) (tag & 7)) {
                        case 0: input.varint(); break;
                        case 1: input.advance(8); break;
                        case 2: input.advance(input.length()); break;
                        case 5: input.advance(4); break;
                        default: throw new IllegalArgumentException();
                    }
                }
            }
            return event;
        }
    }

    private static final class Cursor {
        final byte[] bytes;
        int offset;

        Cursor(byte[] bytes) { this.bytes = bytes; }

        long varint() {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (offset == bytes.length) throw new IllegalArgumentException();
                int next = bytes[offset++] & 255;
                if (shift == 63 && (next & 254) != 0) throw new IllegalArgumentException();
                value |= (long) (next & 127) << shift;
                if ((next & 128) == 0) return value;
            }
            throw new IllegalArgumentException();
        }

        int length() {
            long value = varint();
            if (value < 0 || value > bytes.length - offset) throw new IllegalArgumentException();
            return (int) value;
        }

        void advance(int count) {
            if (count < 0 || count > bytes.length - offset) throw new IllegalArgumentException();
            offset += count;
        }

        String string() {
            int size = length();
            String value = new String(bytes, offset, size, StandardCharsets.UTF_8);
            advance(size);
            return value;
        }
    }
}
