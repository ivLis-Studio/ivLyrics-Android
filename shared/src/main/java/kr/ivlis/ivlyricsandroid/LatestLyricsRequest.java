package kr.ivlis.ivlyricsandroid;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/** Replaces only automatic lyrics work, leaving manual searches and settings jobs intact. */
final class LatestLyricsRequest {
    private final ThreadPoolExecutor executor;
    private RequestCancellation current;
    private Future<?> pending;
    LatestLyricsRequest(ThreadPoolExecutor executor) { this.executor = executor; }

    synchronized RequestCancellation begin() {
        cancel();
        return current = new RequestCancellation();
    }
    synchronized void submit(RequestCancellation request, Runnable work) {
        if (request != current || request.isCancelled() || executor.isShutdown()) return;
        pending = executor.submit(() -> request.run(work));
    }
    synchronized RequestCancellation current() { return current; }
    synchronized void cancel() {
        if (current != null) current.cancel();
        if (pending != null) pending.cancel(true);
        pending = null;
        executor.purge();
    }
}
