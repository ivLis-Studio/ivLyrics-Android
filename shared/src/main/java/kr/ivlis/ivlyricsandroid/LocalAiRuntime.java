package kr.ivlis.ivlyricsandroid;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;

/**
 * The process's single Local AI engine: model files, download, loading, idle release and one worker
 * thread that makes every native call. A test translation runs before lyric work. Lyrics translate
 * one block at a time; an identical request joins the running one and a different request replaces
 * it, so the lyrics on screen never wait behind a track the user already left.
 */
final class LocalAiRuntime {
    static final int CONTEXT_SIZE = 4096;
    static final int BATCH_SIZE = 512;
    static final String DEVICE_LABEL = "CPU";
    static final String TEST_SAMPLE = "今夜はまだ始まったばかり";
    static final String TEST_SAMPLE_FOR_JAPANESE = "The night is still young.";

    enum Phase { MISSING, DOWNLOADING, DOWNLOADED, LOADING, READY, ERROR }

    interface Backend {
        /** Loads the native engine once per process; throws when this host cannot run it. */
        void initialize() throws Exception;

        long load(File model, int contextSize, int batchSize, int threads) throws Exception;

        LocalAiTranslator.Completion complete(long handle, String prompt, int maxTokens) throws Exception;

        void abort(long handle, boolean abort);

        void free(long handle);
    }

    interface Messages {
        String notReady();

        String downloadBusy();

        String unsupportedLanguage(String languageCode);

        /** The text for the unsupported-device reason this runtime was created with. */
        String unsupportedDevice(String reason);
    }

    interface Timer {
        /** Runs the task once after the delay; the returned Runnable cancels it. */
        Runnable schedule(Runnable task, long delayMillis);
    }

    /** A request the provider rejects at once, so the next enabled provider translates instead. */
    static final class LocalAiException extends IOException {
        LocalAiException(String message) {
            super(message);
        }
    }

    /** Ends a request that a newer one replaced; its track is no longer on screen. */
    static final class SupersededException extends CancellationException {
        SupersededException() {
            super("A newer Local AI request replaced this one.");
        }
    }

    static final class Status {
        final Phase phase;
        final LocalAiModels.Tier tier;
        final long loadedBytes;
        final long totalBytes;
        final String device;
        final String error;
        /** Why this device cannot run the engine (an i18n key), or null; the phase is then ERROR. */
        final String unsupportedReason;
        private final Set<String> downloadedTiers;

        Status(Phase phase, LocalAiModels.Tier tier, long loadedBytes, long totalBytes, String device, String error,
                String unsupportedReason, Set<String> downloadedTiers) {
            this.phase = phase;
            this.tier = tier;
            this.loadedBytes = loadedBytes;
            this.totalBytes = totalBytes;
            this.device = device;
            this.error = error;
            this.unsupportedReason = unsupportedReason;
            this.downloadedTiers = Collections.unmodifiableSet(downloadedTiers);
        }

        boolean isDownloaded(LocalAiModels.Tier tier) {
            return downloadedTiers.contains(tier.id);
        }

        boolean isBusy() {
            return phase == Phase.DOWNLOADING || phase == Phase.LOADING;
        }

        boolean hasModel() {
            return phase == Phase.DOWNLOADED || phase == Phase.READY;
        }
    }

    static final class TestResult {
        final String sample;
        final String line;
        final double seconds;

        TestResult(String sample, String line, double seconds) {
            this.sample = sample;
            this.line = line;
            this.seconds = seconds;
        }
    }

    private static final class Job {
        final String key;
        final LocalAiTranslator.Plan plan;
        final List<List<String>> blocks;
        final String language;
        final Messages messages;
        final List<LocalAiTranslator.LineListener> listeners = new ArrayList<>();
        final CompletableFuture<List<String>> result = new CompletableFuture<>();
        int nextBlock;
        int waiters;
        boolean cancelled;
        CancellationException cancellation;

        Job(String key, List<String> rows, String language, Messages messages) {
            this.key = key;
            this.plan = new LocalAiTranslator.Plan(rows);
            this.blocks = LocalAiTranslator.buildBlocks(plan.pendingParts());
            this.language = language;
            this.messages = messages;
        }
    }

    /** Marks a failure of the native engine so the worker releases the model. */
    private static final class NativeFailure extends Exception {
        NativeFailure(Throwable cause) {
            super(cause);
        }
    }

    private final Object lock = new Object();
    private final File modelDirectory;
    private final Backend backend;
    private final Timer timer;
    private final ThreadFactory threadFactory;
    private final int threads;
    private final String unsupportedReason;
    private final LocalAiDownloader.Connector connector;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<Runnable> controls = new ArrayDeque<>();
    private final ArrayDeque<Job> tests = new ArrayDeque<>();

    private LocalAiModels.Tier tier = LocalAiModels.DEFAULT_TIER;
    private int idleMinutes = LocalAiModels.DEFAULT_IDLE_UNLOAD_MINUTES;
    private long handle;
    private LocalAiModels.Tier loadedTier;
    private LocalAiModels.Tier loadingTier;
    private String error;
    private LocalAiModels.Tier downloadingTier;
    private long downloadedBytes;
    private LocalAiDownloader download;
    private Job translation;
    private Job running;
    private Thread worker;
    private Runnable idleCancel;

    /**
     * @param unsupportedReason why this device cannot run the engine, or null when it can: an i18n key, so
     *        requests ({@link Messages#unsupportedDevice}) and the settings card show it in the current UI language
     * @param connector opens download connections; null uses the platform's HttpURLConnection
     */
    LocalAiRuntime(File modelDirectory, Backend backend, Timer timer, ThreadFactory threadFactory, int threads,
            String unsupportedReason, LocalAiDownloader.Connector connector) {
        this.modelDirectory = modelDirectory;
        this.backend = backend;
        this.timer = timer;
        this.threadFactory = threadFactory;
        this.threads = Math.max(1, threads);
        this.unsupportedReason = unsupportedReason;
        this.connector = connector;
    }

    File modelFile(LocalAiModels.Tier tier) {
        return new File(modelDirectory, tier.fileName);
    }

    boolean isDownloaded(LocalAiModels.Tier tier) {
        File file = modelFile(tier);
        return file.isFile() && file.length() == tier.sizeBytes;
    }

    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** Applies the selected tier and idle choice; a different tier releases the loaded model. */
    void configure(LocalAiModels.Tier selected, int idleUnloadMinutes) {
        LocalAiModels.Tier next = selected == null ? LocalAiModels.DEFAULT_TIER : selected;
        int idle = LocalAiModels.idleUnloadMinutes(idleUnloadMinutes);
        boolean changed;
        synchronized (lock) {
            changed = next != tier || idle != idleMinutes;
            if (next != tier) {
                tier = next;
                error = null;
                if (handle != 0 && loadedTier != next) {
                    enqueueControlLocked(this::unloadNow);
                }
            }
            if (idle != idleMinutes) {
                idleMinutes = idle;
                cancelIdleUnloadLocked();
                lock.notifyAll();
            }
        }
        if (changed) {
            notifyListeners();
        }
    }

    /** Translates lyric rows, reporting each finished row; blocks the calling (background) thread. */
    List<String> translate(List<String> rows, String languageCode, Messages messages,
            LocalAiTranslator.LineListener listener) throws Exception {
        String language = LocalAiTranslator.languageName(languageCode);
        if (language == null) {
            throw new LocalAiException(messages.unsupportedLanguage(languageCode));
        }
        Job job;
        List<Integer> replayRows;
        List<String> replayTexts = new ArrayList<>();
        synchronized (lock) {
            requireReadyLocked(messages);
            String key = tier.id + '\u0000' + language + '\u0000' + String.join("\u0001", rows);
            if (translation != null && key.equals(translation.key)) {
                job = translation;
            } else {
                if (translation != null) {
                    cancelLocked(translation, new SupersededException());
                }
                job = new Job(key, rows, language, messages);
                job.plan.takeReadyRows();
                if (job.blocks.isEmpty()) {
                    job.result.complete(job.plan.texts());
                } else {
                    translation = job;
                    ensureWorkerLocked();
                    lock.notifyAll();
                }
            }
            if (listener != null) {
                job.listeners.add(listener);
            }
            job.waiters++;
            replayRows = job.plan.reportedRows();
            for (int row : replayRows) {
                replayTexts.add(job.plan.text(row));
            }
        }
        dispatch(listener == null ? Collections.<LocalAiTranslator.LineListener>emptyList()
                : Collections.singletonList(listener), replayRows, replayTexts);
        try {
            return job.result.get();
        } catch (CancellationException cancelled) {
            // Some runtimes wrap the reason in a new CancellationException; report the job's own reason.
            CancellationException reason;
            synchronized (lock) {
                reason = job.cancellation;
            }
            throw reason != null ? reason : cancelled;
        } catch (ExecutionException failure) {
            throw asException(failure.getCause());
        } catch (InterruptedException interrupted) {
            synchronized (lock) {
                if (--job.waiters == 0) {
                    cancelLocked(job, new CancellationException("The Local AI request was interrupted."));
                }
            }
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    /** Translates the sample line into the target language, ahead of queued lyric blocks. */
    CompletableFuture<TestResult> testTranslation(String languageCode, Messages messages) {
        CompletableFuture<TestResult> result = new CompletableFuture<>();
        String language = LocalAiTranslator.languageName(languageCode);
        if (language == null) {
            result.completeExceptionally(new LocalAiException(messages.unsupportedLanguage(languageCode)));
            return result;
        }
        String code = languageCode == null ? "" : languageCode.trim().toLowerCase(Locale.ROOT);
        String sample = code.startsWith("ja") ? TEST_SAMPLE_FOR_JAPANESE : TEST_SAMPLE;
        long started = System.nanoTime();
        Job job;
        synchronized (lock) {
            try {
                requireReadyLocked(messages);
            } catch (LocalAiException notReady) {
                result.completeExceptionally(notReady);
                return result;
            }
            job = new Job(null, Collections.singletonList(sample), language, messages);
            tests.add(job);
            ensureWorkerLocked();
            lock.notifyAll();
        }
        job.result.whenComplete((rows, failure) -> {
            if (failure != null) {
                result.completeExceptionally(failure);
            } else {
                result.complete(new TestResult(sample, rows.get(0), (System.nanoTime() - started) / 1e9));
            }
        });
        return result;
    }

    /** "Free memory": releases the model after the block that is running, if any. */
    void unload() {
        synchronized (lock) {
            enqueueControlLocked(this::unloadNow);
        }
    }

    /** Deletes a tier's file and any partial download of it. */
    void delete(LocalAiModels.Tier target) {
        cancelDownload();
        synchronized (lock) {
            enqueueControlLocked(() -> {
                LocalAiModels.Tier loaded;
                synchronized (lock) {
                    loaded = loadedTier;
                }
                if (loaded == target) {
                    unloadNow();
                }
                File file = modelFile(target);
                File partial = LocalAiDownloader.partialFile(file);
                boolean deleted = (!file.exists() || file.delete()) && (!partial.exists() || partial.delete());
                synchronized (lock) {
                    error = deleted ? null : "Could not delete the model file.";
                }
            });
        }
    }

    /** Downloads the selected tier; only an explicit user action calls this. */
    void startDownload() {
        LocalAiModels.Tier target;
        LocalAiDownloader downloader;
        synchronized (lock) {
            if (downloadingTier != null || unsupportedReason != null || isDownloaded(tier)) {
                return;
            }
            target = tier;
            downloader = new LocalAiDownloader(connector);
            download = downloader;
            downloadingTier = target;
            File partial = LocalAiDownloader.partialFile(modelFile(target));
            downloadedBytes = partial.isFile() ? Math.min(partial.length(), target.sizeBytes) : 0L;
            error = null;
        }
        notifyListeners();
        Thread thread = threadFactory.newThread(() -> runDownload(target, downloader));
        thread.start();
    }

    void cancelDownload() {
        LocalAiDownloader downloader;
        synchronized (lock) {
            downloader = download;
        }
        if (downloader != null) {
            downloader.cancel();
        }
    }

    Status status() {
        synchronized (lock) {
            Set<String> downloaded = new LinkedHashSet<>();
            for (LocalAiModels.Tier candidate : LocalAiModels.TIERS) {
                if (isDownloaded(candidate)) {
                    downloaded.add(candidate.id);
                }
            }
            Phase phase;
            String message = null;
            String device = null;
            long loaded = 0L;
            if (unsupportedReason != null) {
                phase = Phase.ERROR;
            } else if (downloadingTier == tier) {
                phase = Phase.DOWNLOADING;
                loaded = downloadedBytes;
            } else if (loadingTier == tier) {
                phase = Phase.LOADING;
            } else if (handle != 0 && loadedTier == tier) {
                phase = Phase.READY;
                device = DEVICE_LABEL;
            } else if (error != null) {
                phase = Phase.ERROR;
                message = error;
            } else {
                phase = downloaded.contains(tier.id) ? Phase.DOWNLOADED : Phase.MISSING;
            }
            return new Status(phase, tier, loaded, tier.sizeBytes, device, message, unsupportedReason, downloaded);
        }
    }

    private void requireReadyLocked(Messages messages) throws LocalAiException {
        if (unsupportedReason != null) {
            throw new LocalAiException(messages.unsupportedDevice(unsupportedReason));
        }
        if (downloadingTier == tier) {
            throw new LocalAiException(messages.downloadBusy());
        }
        if (!isDownloaded(tier)) {
            // Never download implicitly: fail at once so the next enabled provider translates.
            throw new LocalAiException(messages.notReady());
        }
    }

    private void runDownload(LocalAiModels.Tier target, LocalAiDownloader downloader) {
        String failure = null;
        try {
            downloader.download(target.url(), modelFile(target), target.sizeBytes, (loaded, total) -> {
                synchronized (lock) {
                    downloadedBytes = loaded;
                }
                notifyListeners();
            });
        } catch (CancellationException cancelled) {
            // The partial file stays so the next download resumes it.
        } catch (Throwable downloadError) {
            failure = describe(downloadError);
        }
        synchronized (lock) {
            if (download == downloader) {
                download = null;
                downloadingTier = null;
            }
            if (failure != null) {
                error = failure;
            }
        }
        notifyListeners();
    }

    private void ensureWorkerLocked() {
        if (worker == null) {
            worker = threadFactory.newThread(this::work);
            worker.start();
        }
    }

    private void enqueueControlLocked(Runnable control) {
        controls.add(control);
        ensureWorkerLocked();
        lock.notifyAll();
    }

    private void work() {
        while (true) {
            Runnable control;
            Job job;
            List<String> block = null;
            synchronized (lock) {
                while (true) {
                    control = controls.poll();
                    job = control == null ? (tests.isEmpty() ? translation : tests.peek()) : null;
                    if (control != null || job != null) {
                        break;
                    }
                    scheduleIdleUnloadLocked();
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                        // The worker serves the process for its whole lifetime.
                    }
                }
                cancelIdleUnloadLocked();
                if (job != null) {
                    block = job.blocks.get(job.nextBlock);
                }
            }
            if (control != null) {
                try {
                    control.run();
                } catch (RuntimeException ignored) {
                    // A failed release or deletion must not stop the worker.
                }
                notifyListeners();
            } else {
                runBlock(job, block);
            }
        }
    }

    private void runBlock(Job job, List<String> block) {
        try {
            long engine = ensureLoaded(job.messages);
            synchronized (lock) {
                if (job.cancelled) {
                    return;
                }
                running = job;
                backend.abort(engine, false);
            }
            List<String> values;
            try {
                values = LocalAiTranslator.translateBlock(
                        (prompt, maxTokens) -> complete(engine, prompt, maxTokens), block, job.language);
            } finally {
                synchronized (lock) {
                    running = null;
                }
            }
            List<Integer> rows;
            List<String> texts = new ArrayList<>();
            List<LocalAiTranslator.LineListener> targets;
            List<String> finished = null;
            synchronized (lock) {
                if (job.cancelled) {
                    return;
                }
                job.plan.put(block, values);
                job.nextBlock++;
                rows = job.plan.takeReadyRows();
                for (int row : rows) {
                    texts.add(job.plan.text(row));
                }
                targets = new ArrayList<>(job.listeners);
                if (job.nextBlock >= job.blocks.size()) {
                    removeLocked(job);
                    finished = job.plan.texts();
                }
            }
            dispatch(targets, rows, texts);
            if (finished != null) {
                job.result.complete(finished);
            }
        } catch (Throwable failure) {
            Throwable reported = failure instanceof NativeFailure ? failure.getCause() : failure;
            boolean release = failure instanceof NativeFailure && !(reported instanceof CancellationException);
            synchronized (lock) {
                removeLocked(job);
                if (release) {
                    error = describe(reported);
                }
            }
            if (release) {
                // Out of memory or a native failure: free the engine; the next request loads it again.
                unloadNow();
            }
            job.result.completeExceptionally(reported);
            notifyListeners();
        }
    }

    private LocalAiTranslator.Completion complete(long engine, String prompt, int maxTokens) throws Exception {
        try {
            return backend.complete(engine, prompt, maxTokens);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Throwable failure) {
            throw new NativeFailure(failure);
        }
    }

    private long ensureLoaded(Messages messages) throws Exception {
        LocalAiModels.Tier wanted;
        synchronized (lock) {
            wanted = tier;
            if (handle != 0 && loadedTier == wanted) {
                return handle;
            }
        }
        unloadNow();
        synchronized (lock) {
            requireReadyLocked(messages);
            loadingTier = wanted;
            error = null;
        }
        notifyListeners();
        try {
            backend.initialize();
            long loaded = backend.load(modelFile(wanted), CONTEXT_SIZE, BATCH_SIZE, threads);
            synchronized (lock) {
                handle = loaded;
                loadedTier = wanted;
                loadingTier = null;
            }
            notifyListeners();
            return loaded;
        } catch (Throwable failure) {
            synchronized (lock) {
                loadingTier = null;
                error = describe(failure);
            }
            notifyListeners();
            throw asException(failure);
        }
    }

    private void unloadNow() {
        long released;
        synchronized (lock) {
            released = handle;
            handle = 0;
            loadedTier = null;
            cancelIdleUnloadLocked();
        }
        if (released != 0) {
            backend.free(released);
        }
    }

    private void unloadIfIdle() {
        synchronized (lock) {
            if (translation != null || !tests.isEmpty()) {
                return;
            }
        }
        unloadNow();
    }

    private void scheduleIdleUnloadLocked() {
        if (idleCancel != null || handle == 0 || idleMinutes <= 0 || timer == null) {
            return;
        }
        idleCancel = timer.schedule(() -> {
            synchronized (lock) {
                enqueueControlLocked(this::unloadIfIdle);
            }
        }, idleMinutes * 60_000L);
    }

    private void cancelIdleUnloadLocked() {
        Runnable cancel = idleCancel;
        idleCancel = null;
        if (cancel != null) {
            cancel.run();
        }
    }

    private void cancelLocked(Job job, CancellationException reason) {
        if (job.cancelled || job.result.isDone()) {
            return;
        }
        job.cancelled = true;
        job.cancellation = reason;
        removeLocked(job);
        job.result.completeExceptionally(reason);
        if (running == job && handle != 0) {
            backend.abort(handle, true);
        }
    }

    private void removeLocked(Job job) {
        if (translation == job) {
            translation = null;
        }
        tests.remove(job);
    }

    private static void dispatch(List<LocalAiTranslator.LineListener> targets, List<Integer> rows, List<String> texts) {
        for (LocalAiTranslator.LineListener target : targets) {
            for (int index = 0; index < rows.size(); index++) {
                try {
                    target.onLine(rows.get(index), texts.get(index));
                } catch (RuntimeException ignored) {
                    // A closed screen must not stop the engine.
                }
            }
        }
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A closed settings card must not break the engine.
            }
        }
    }

    static String describe(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message.trim();
    }

    private static Exception asException(Throwable error) {
        return error instanceof Exception ? (Exception) error : new IOException(describe(error), error);
    }
}
