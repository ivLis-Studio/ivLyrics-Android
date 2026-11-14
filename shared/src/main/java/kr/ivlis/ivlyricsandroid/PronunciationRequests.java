package kr.ivlis.ivlyricsandroid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * A song's pronunciation as small AI requests side by side, the same as the desktop app's
 * runParallelBatches (AIAddonManager.js) and the iOS app.
 *
 * <p>Reasoning models think longer than linearly with the lines of a request (gpt-6-luna: one
 * line 6 s, two 14 s, three up to 29 s, seven up to 60 s), so the opening line goes out alone and
 * the rest as two-line requests, up to twelve at a time. The lines a request answered wrongly are
 * asked again as soon as it returns. A rate-limited request halves the requests in flight and is
 * sent again after a pause, so a provider that refuses parallel requests still gets the song one
 * request at a time.
 */
final class PronunciationRequests {
    static final int PARALLEL_REQUESTS = 12;
    static final int BATCH_LINES = 2;
    static final int BATCH_UNITS = 60;
    static final int RATE_LIMIT_RETRIES = 3;
    static final long RATE_LIMIT_PAUSE_MS = 1500L;

    interface Perform<J, A> {
        A run(J job) throws Exception;
    }

    /** Gets each answer on the coordinating thread, one at a time; returns follow-up jobs. */
    interface Handle<J, A> {
        List<J> handle(J job, A answer);
    }

    interface Pause {
        void sleep(int attempt) throws InterruptedException;
    }

    /** 1.5 s, 3 s, 6 s. */
    static final Pause DEFAULT_PAUSE = attempt -> Thread.sleep(RATE_LIMIT_PAUSE_MS << Math.max(0, attempt));

    private PronunciationRequests() {
    }

    /** The opening item alone, then {@code chunk} of the rest. */
    static <T> List<List<T>> batches(List<T> items, Function<List<T>, List<List<T>>> chunk) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<List<T>> batches = new ArrayList<>();
        batches.add(Collections.singletonList(items.get(0)));
        batches.addAll(chunk.apply(new ArrayList<>(items.subList(1, items.size()))));
        return batches;
    }

    static boolean isRateLimited(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (message.contains("429") || message.contains("rate limit") || message.contains("rate_limit")
                    || message.contains("too many requests") || message.contains("resource_exhausted")
                    || message.contains("resource exhausted")) {
                return true;
            }
        }
        return false;
    }

    private static final class Entry<J> {
        final J job;
        final int attempt;

        Entry(J job, int attempt) {
            this.job = job;
            this.attempt = attempt;
        }
    }

    private static final class Outcome<J, A> {
        final Entry<J> entry;
        final A answer;
        final Exception error;

        Outcome(Entry<J> entry, A answer, Exception error) {
            this.entry = entry;
            this.answer = answer;
            this.error = error;
        }
    }

    static <J, A> List<Exception> run(List<J> jobs, ExecutorService executor, Perform<J, A> perform, Handle<J, A> handle)
            throws InterruptedException {
        return run(jobs, executor, PARALLEL_REQUESTS, RATE_LIMIT_RETRIES, DEFAULT_PAUSE, perform, handle);
    }

    /**
     * Runs jobs in order on {@code executor}, at most {@code limit} at a time, and returns the
     * errors of the jobs that failed for good. Interrupting the calling thread cancels the jobs.
     */
    static <J, A> List<Exception> run(
            List<J> jobs,
            ExecutorService executor,
            int limit,
            int retries,
            Pause pause,
            Perform<J, A> perform,
            Handle<J, A> handle
    ) throws InterruptedException {
        ArrayDeque<Entry<J>> queue = new ArrayDeque<>();
        for (J job : jobs) queue.addLast(new Entry<>(job, 0));
        List<Exception> errors = new ArrayList<>();
        CompletionService<Outcome<J, A>> completion = new ExecutorCompletionService<>(executor);
        List<Future<Outcome<J, A>>> launched = new ArrayList<>();
        int allowed = Math.max(1, limit);
        int active = 0;
        try {
            while (true) {
                while (active < allowed && !queue.isEmpty()) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    Entry<J> next = queue.pollFirst();
                    active++;
                    launched.add(completion.submit(() -> {
                        try {
                            // A retried request waits first, keeping its place while it does.
                            if (next.attempt > 0) pause.sleep(next.attempt - 1);
                            return new Outcome<>(next, perform.run(next.job), null);
                        } catch (Exception error) {
                            return new Outcome<>(next, null, error);
                        }
                    }));
                }
                if (active == 0) break;
                Outcome<J, A> outcome;
                try {
                    outcome = completion.take().get();
                } catch (ExecutionException failure) {
                    active--;
                    errors.add(new Exception(failure.getCause()));
                    continue;
                }
                active--;
                if (outcome.error == null) {
                    List<J> more = handle.handle(outcome.entry.job, outcome.answer);
                    if (more != null) {
                        for (int index = more.size() - 1; index >= 0; index--) queue.addFirst(new Entry<>(more.get(index), 0));
                    }
                } else if (!(outcome.error instanceof InterruptedException)
                        && outcome.entry.attempt < retries && isRateLimited(outcome.error)) {
                    allowed = Math.min(allowed, Math.max(1, (active + 1) / 2));
                    queue.addFirst(new Entry<>(outcome.entry.job, outcome.entry.attempt + 1));
                } else {
                    errors.add(outcome.error);
                }
            }
        } catch (InterruptedException interrupted) {
            for (Future<?> future : launched) future.cancel(true);
            throw interrupted;
        }
        return errors;
    }
}
