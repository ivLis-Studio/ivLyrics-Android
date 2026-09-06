package kr.ivlis.ivlyricsandroid;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Request-scoped cancellation shared by all synchronous provider adapters. */
final class RequestCancellation {
    private static final ThreadLocal<RequestCancellation> CURRENT = new ThreadLocal<>();
    private static final Executor DISCONNECT = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ivlyrics-http-cancel");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<HttpURLConnection> connections = new HashSet<>();
    private volatile boolean cancelled;

    static RequestCancellation current() { return CURRENT.get(); }
    boolean isCancelled() { return cancelled; }
    void throwIfCancelled() { if (cancelled) throw new CancellationException(); }
    static void check() { RequestCancellation request = CURRENT.get(); if (request != null) request.throwIfCancelled(); }

    static Runnable guard(Runnable delivery) { return guard(CURRENT.get(), delivery); }

    static Runnable guard(RequestCancellation request, Runnable delivery) {
        return () -> { if (request == null || !request.isCancelled()) delivery.run(); };
    }

    void run(Runnable work) {
        RequestCancellation previous = CURRENT.get();
        CURRENT.set(this);
        try { throwIfCancelled(); work.run(); }
        catch (CancellationException ignored) { }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    void cancel() {
        Set<HttpURLConnection> pending;
        synchronized (this) {
            cancelled = true;
            pending = new HashSet<>(connections);
            connections.clear();
        }
        // Some platform implementations may wait on an I/O lock in disconnect.
        // Never hold the UI thread while releasing an obsolete socket.
        for (HttpURLConnection connection : pending) DISCONNECT.execute(connection::disconnect);
    }

    static AutoCloseableConnection register(HttpURLConnection connection) {
        RequestCancellation request = CURRENT.get();
        if (request != null) synchronized (request) {
            request.throwIfCancelled();
            request.connections.add(connection);
        }
        return new AutoCloseableConnection(request, connection);
    }

    static final class AutoCloseableConnection implements AutoCloseable {
        private final RequestCancellation request;
        private final HttpURLConnection connection;
        AutoCloseableConnection(RequestCancellation request, HttpURLConnection connection) {
            this.request = request; this.connection = connection;
        }
        @Override public void close() {
            if (request != null) synchronized (request) { request.connections.remove(connection); }
            connection.disconnect();
        }
    }
}
