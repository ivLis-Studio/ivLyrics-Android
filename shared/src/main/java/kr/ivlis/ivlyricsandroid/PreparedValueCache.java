package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded completed work and one in-flight preparation per key. */
final class PreparedValueCache<K, V> {
    private final int capacity;
    private final Executor worker, delivery;
    private final Map<K, V> cache = new LinkedHashMap<>(9, .75f, true);
    private final Map<K, Pending> pending = new HashMap<>();
    private final class Pending {
        Supplier<V> prepare;
        final List<Consumer<V>> listeners = new ArrayList<>();
        Runnable task;
    }

    PreparedValueCache(int capacity, Executor worker, Executor delivery) {
        this.capacity = capacity; this.worker = worker; this.delivery = delivery;
    }

    Runnable request(K key, Supplier<V> prepare, Consumer<V> callback) {
        V hit;
        Pending request;
        boolean start = false;
        synchronized (this) {
            hit = cache.get(key);
            request = pending.get(key);
            if (hit == null) {
                if (request == null) {
                    request = new Pending();
                    request.prepare = prepare;
                    pending.put(key, request);
                    start = true;
                }
                request.listeners.add(callback);
            }
        }
        if (hit != null) { callback.accept(hit); return () -> {}; }
        Pending entry = request;
        if (start) {
            entry.task = () -> {
                Supplier<V> supplier;
                synchronized (this) { supplier = entry.prepare; entry.prepare = null; }
                if (supplier == null) return;
                V prepared = null;
                try { prepared = supplier.get(); } catch (RuntimeException ignored) { }
                V result = prepared;
                delivery.execute(() -> {
                    List<Consumer<V>> listeners;
                    synchronized (this) {
                        if (pending.get(key) != entry) return;
                        pending.remove(key);
                        listeners = new ArrayList<>(entry.listeners);
                        if (result != null) {
                            cache.put(key, result);
                            while (cache.size() > capacity) cache.remove(cache.keySet().iterator().next());
                        }
                    }
                    for (Consumer<V> listener : listeners) listener.accept(result);
                });
            };
            worker.execute(entry.task);
        }
        return () -> {
            synchronized (this) {
                entry.listeners.remove(callback);
                if (entry.listeners.isEmpty() && pending.get(key) == entry) {
                    pending.remove(key);
                    entry.prepare = null;
                    if (worker instanceof ThreadPoolExecutor) ((ThreadPoolExecutor) worker).remove(entry.task);
                }
            }
        };
    }
}
