package kr.ivlis.ivlyricsandroid;

import android.os.Handler;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Generates lrc.red word timing for line-timed lrc.red lyrics that were chosen because no
 * enabled source had karaoke lyrics. The lyrics stay displayed; only their timing is added.
 *
 * One generation runs at a time and at most four wait. A track is not retried for ten minutes
 * after a failure (an hour after success), and the server's 429/503 cooldown is respected.
 * Callbacks run on the main thread.
 */
final class LrcRedWordSync {
    /** What the scheduler needs from the lyrics repository. Called off the main thread. */
    interface Host {
        /** Whether automatic word timing applies to this track's provider settings. */
        boolean enabled(String trackKey);

        /** Stores generated timing where the same line-timed lyrics are still cached. */
        void persist(String trackKey, LyricsResult upgraded);

        void log(String trackKey, LyricsRepository.Callback callback, String message);
    }

    private static final int MAX_PENDING = 4;
    private static final int MAX_REMEMBERED_JOBS = 32;
    private static final int MAX_REMEMBERED_RECORDINGS = 64;
    private static final long FAILURE_RETRY_MS = 10L * 60L * 1000L;
    private static final long SUCCESS_RETRY_MS = 60L * 60L * 1000L;

    private final Host host;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ivlyrics-word-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final Object lock = new Object();
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    // The lrc.red recording each track's lyrics came from, so generation skips a new lookup.
    private final Map<String, String> recordings = new LinkedHashMap<String, String>(MAX_REMEMBERED_RECORDINGS + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_REMEMBERED_RECORDINGS;
        }
    };
    private int pending;
    private long unavailableUntilMs;
    private volatile boolean closed;

    LrcRedWordSync(Host host, Handler mainHandler) {
        this.host = host;
        this.mainHandler = mainHandler;
    }

    void rememberRecording(String trackKey, String recordIsrc) {
        synchronized (lock) {
            recordings.put(trackKey, recordIsrc);
        }
    }

    /** Whether lrc.red is currently generating word timing for this track. */
    boolean isGenerating(String trackKey) {
        synchronized (lock) {
            Job job = jobs.get(trackKey);
            return job != null && !job.finished && job.generating;
        }
    }

    /**
     * Requests word timing for the displayed result. A generation already under way for the
     * track reports to the latest callback instead. checkFresh looks the recording up again,
     * since cached line timing may predate a generation by another client.
     */
    void request(TrackSnapshot track, String key, LyricsResult result, LyricsRepository.Callback callback, boolean checkFresh) {
        if (closed || track == null || callback == null || key == null || key.isEmpty()
                || !LyricsTimingUpgrade.canUpgrade(result) || !host.enabled(key)) {
            return;
        }
        Job job;
        synchronized (lock) {
            long now = System.currentTimeMillis();
            Job existing = jobs.get(key);
            if (existing != null && (!existing.finished || now < existing.retryAtMs)) {
                // A reopened page receives the result of the generation already under way.
                existing.callback = callback;
                if (!existing.finished && existing.generating) {
                    post(() -> callback.onLyricsWordSyncStarted(key));
                }
                return;
            }
            if (now < unavailableUntilMs || pending >= MAX_PENDING) return;
            String recordIsrc = checkFresh ? "" : recordings.getOrDefault(key, "");
            job = new Job(key, track, result, recordIsrc, checkFresh, callback);
            jobs.remove(key);
            jobs.put(key, job);
            Iterator<Map.Entry<String, Job>> entries = jobs.entrySet().iterator();
            while (jobs.size() > MAX_REMEMBERED_JOBS && entries.hasNext()) {
                if (entries.next().getValue().finished) entries.remove();
            }
            pending++;
        }
        try {
            executor.execute(() -> run(job));
        } catch (RejectedExecutionException rejected) {
            synchronized (lock) {
                job.finished = true;
                pending--;
            }
        }
    }

    /** Stops accepting work. A running generation still finishes and updates the cache. */
    void close() {
        closed = true;
        executor.shutdown();
    }

    private void run(Job job) {
        LrcRedLyricsProvider.Logger log = message -> {
            LyricsRepository.Callback callback = job.callback;
            if (!closed && callback != null) host.log(job.key, callback, "word sync: " + message);
        };
        LyricsResult upgraded = null;
        try {
            if (System.currentTimeMillis() < unavailableUntilMs) throw new IllegalStateException("server cooling down");
            String recordIsrc = job.recordIsrc;
            List<LyricsLine> timedLines = null;
            if (job.checkFresh || recordIsrc.isEmpty()) {
                LrcRedLyricsProvider.Fetched fresh = LrcRedLyricsProvider.fetch(job.track, job.base.isrc, job.base.spotifyTrackId, log);
                if (fresh != null) {
                    recordIsrc = fresh.recordIsrc;
                    if (fresh.result.karaoke) timedLines = fresh.result.lines;
                }
            }
            if (timedLines == null) {
                if (recordIsrc.isEmpty()) throw new IllegalStateException("no lrc.red recording");
                synchronized (lock) {
                    job.generating = true;
                }
                LyricsRepository.Callback callback = job.callback;
                if (callback != null) post(() -> callback.onLyricsWordSyncStarted(job.key));
                log.write("generating for " + recordIsrc);
                timedLines = LrcRedLyricsProvider.requestWordSync(recordIsrc);
            }
            upgraded = LyricsTimingUpgrade.upgradedBase(job.base, timedLines);
            if (upgraded == null) throw new IllegalStateException("generated text differs from the displayed lyrics");
            host.persist(job.key, upgraded);
            log.write("word timing applied: lines=" + upgraded.lines.size());
        } catch (LrcRedLyricsProvider.HttpStatusException error) {
            if (error.status == 429 || error.status == 503) {
                synchronized (lock) {
                    unavailableUntilMs = System.currentTimeMillis() + Math.max(FAILURE_RETRY_MS, error.retryAfterMs);
                }
            }
            log.write("unavailable: " + error.getMessage());
        } catch (Exception error) {
            log.write("unavailable: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        } finally {
            finish(job, upgraded);
        }
    }

    private void finish(Job job, LyricsResult upgraded) {
        boolean announced;
        LyricsRepository.Callback callback;
        synchronized (lock) {
            announced = job.generating;
            job.finished = true;
            job.retryAtMs = System.currentTimeMillis() + (upgraded != null ? SUCCESS_RETRY_MS : FAILURE_RETRY_MS);
            pending--;
            callback = job.callback;
        }
        // A failure is reported only for a generation the page was told about.
        if (callback != null && (announced || upgraded != null)) {
            post(() -> callback.onLyricsWordSyncFinished(job.key, upgraded));
        }
    }

    private void post(Runnable action) {
        mainHandler.post(() -> {
            if (!closed) action.run();
        });
    }

    private static final class Job {
        final String key;
        final TrackSnapshot track;
        final LyricsResult base;
        final String recordIsrc;
        final boolean checkFresh;
        volatile LyricsRepository.Callback callback;
        boolean generating;
        boolean finished;
        long retryAtMs = Long.MAX_VALUE;

        Job(String key, TrackSnapshot track, LyricsResult base, String recordIsrc, boolean checkFresh,
            LyricsRepository.Callback callback) {
            this.key = key;
            this.track = track;
            this.base = base;
            this.recordIsrc = recordIsrc == null ? "" : recordIsrc;
            this.checkFresh = checkFresh;
            this.callback = callback;
        }
    }
}

