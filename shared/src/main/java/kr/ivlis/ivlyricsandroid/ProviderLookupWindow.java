package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Independent provider lookups for one lyrics request. Lookups start in the configured order,
 * at most {@code limit} at a time. Callers await them in that order, so a faster
 * lower-priority source never replaces a higher one. Each lookup runs within the request's
 * cancellation scope.
 */
final class ProviderLookupWindow<T> {
    interface Lookup<T> {
        T load(LyricsProviderSettings.ProviderConfig config) throws Exception;
    }

    interface Closed {
        boolean isClosed();
    }

    private final List<LyricsProviderSettings.ProviderConfig> waiting;
    private final Map<String, Future<T>> started = new HashMap<>();
    private final RequestCancellation request = RequestCancellation.current();
    private final ExecutorService executor;
    private final int limit;
    private final Closed closed;
    private final Lookup<T> lookup;

    ProviderLookupWindow(
            List<LyricsProviderSettings.ProviderConfig> providers,
            ExecutorService executor,
            int limit,
            Closed closed,
            Lookup<T> lookup
    ) {
        this.waiting = new ArrayList<>(providers);
        this.executor = executor;
        this.limit = limit;
        this.closed = closed;
        this.lookup = lookup;
    }

    /** Starts this source if it is still waiting, then waits for its result. */
    T await(LyricsProviderSettings.ProviderConfig config) throws Exception {
        Future<T> future = start(config);
        fill();
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IllegalStateException(cause);
        } finally {
            fill();
        }
    }

    /** Starts waiting sources in order until {@code limit} are running. */
    void fill() {
        int running = 0;
        for (Future<T> future : started.values()) {
            if (!future.isDone()) running++;
        }
        while (running < limit && !waiting.isEmpty() && !closed.isClosed()) {
            start(waiting.get(0));
            running++;
        }
    }

    void cancel() {
        waiting.clear();
        for (Future<T> future : started.values()) {
            if (!future.isDone()) future.cancel(true);
        }
    }

    private Future<T> start(LyricsProviderSettings.ProviderConfig config) {
        Future<T> future = started.get(config.provider.id);
        if (future != null) return future;
        waiting.remove(config);
        future = executor.submit(() -> request == null
                ? lookup.load(config)
                : request.call(() -> lookup.load(config)));
        started.put(config.provider.id, future);
        return future;
    }
}

