package kr.ivlis.ivlyricsandroid;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalAiRuntimeTest {
    private static final String BLOCK_MARKER = "retain the exact same number of delimiters";
    private static final LocalAiRuntime.Messages MESSAGES = new LocalAiRuntime.Messages() {
        @Override public String notReady() { return "not ready"; }
        @Override public String downloadBusy() { return "download busy"; }
        @Override public String unsupportedLanguage(String code) { return "cannot translate into " + code; }
    };

    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    private final FakeBackend backend = new FakeBackend();
    private final FakeTimer timer = new FakeTimer();
    private final ExecutorService callers = Executors.newCachedThreadPool();

    private static String sourceOf(String prompt) {
        return prompt.substring(prompt.lastIndexOf("\n\n") + 2);
    }

    private LocalAiRuntime runtime(String unsupportedReason, LocalAiModels.Tier... downloaded) throws IOException {
        File models = folder.newFolder();
        for (LocalAiModels.Tier tier : downloaded) {
            try (RandomAccessFile file = new RandomAccessFile(new File(models, tier.fileName), "rw")) {
                file.setLength(tier.sizeBytes);
            }
        }
        LocalAiRuntime runtime = new LocalAiRuntime(models, backend, timer, task -> {
            Thread thread = new Thread(task, "local-ai-test");
            thread.setDaemon(true);
            return thread;
        }, 4, unsupportedReason, backend::open);
        runtime.configure(LocalAiModels.LIGHT, 10);
        return runtime;
    }

    @Test public void lyricsTranslateInBlocksKeepingBlanksMarkersVocalPartsAndRepeats() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT);
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        List<String> result = runtime.translate(Arrays.asList("君の声", "", "[Instrumental]", "夜明け / まで", "君の声"),
                "ko", MESSAGES, (row, text) -> lines.add(row + "=" + text));
        assertEquals(Arrays.asList("KO:君の声", "", "[Instrumental]", "KO:夜明け / KO:まで", "KO:君の声"), result);
        assertEquals("one block covers every unique part", 1, backend.prompts.size());
        assertTrue(backend.prompts.get(0).contains("into Korean.") && backend.prompts.get(0).contains(BLOCK_MARKER));
        assertEquals(Arrays.asList("君の声", "夜明け", "まで"), Arrays.asList(sourceOf(backend.prompts.get(0)).split("\n")));
        assertEquals(Arrays.asList("1=", "2=[Instrumental]", "0=KO:君の声", "3=KO:夜明け / KO:まで", "4=KO:君の声"), lines);
        assertEquals(Collections.singletonList(LocalAiModels.LIGHT.fileName), backend.loads);
        assertEquals(Collections.singletonList(4), backend.threads);
        LocalAiRuntime.Status status = runtime.status();
        assertEquals(LocalAiRuntime.Phase.READY, status.phase);
        assertEquals("CPU", status.device);
    }

    @Test public void withoutTheSelectedModelARequestFailsAtOnceAndNeverDownloads() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.HIGH);
        assertEquals(LocalAiRuntime.Phase.MISSING, runtime.status().phase);
        try {
            runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null);
            fail("The light model is not downloaded");
        } catch (LocalAiRuntime.LocalAiException expected) {
            assertEquals("not ready", expected.getMessage());
        }
        assertEquals(0, backend.initializations.get());
        assertTrue(backend.loads.isEmpty() && backend.prompts.isEmpty());
        assertEquals("no download is started implicitly", 0, backend.connections.get());
        assertTrue(runtime.status().isDownloaded(LocalAiModels.HIGH));
        assertFalse(runtime.status().isDownloaded(LocalAiModels.LIGHT));
    }

    @Test public void anUnsupportedTargetLanguageFailsSoTheNextProviderTranslates() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT);
        try {
            runtime.translate(Collections.singletonList("君の声"), "sv", MESSAGES, null);
            fail("Swedish is not supported");
        } catch (LocalAiRuntime.LocalAiException expected) {
            assertEquals("cannot translate into sv", expected.getMessage());
        }
        assertTrue(backend.prompts.isEmpty());
        for (String language : new String[]{"zh-TW", "zh-CN", "pt"}) {
            assertEquals(1, runtime.translate(Collections.singletonList("Hello " + language), language, MESSAGES, null).size());
        }
        assertTrue(backend.prompts.get(0).contains("into Traditional Chinese."));
        assertTrue(backend.prompts.get(1).contains("into Chinese."));
        assertTrue(backend.prompts.get(2).contains("into Portuguese."));
    }

    @Test public void aDeviceThatCannotRunTheEngineReportsItWithoutLoadingOrDownloading() throws Exception {
        LocalAiRuntime runtime = runtime("needs arm64", LocalAiModels.LIGHT);
        try {
            runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null);
            fail("The device is unsupported");
        } catch (LocalAiRuntime.LocalAiException expected) {
            assertEquals("needs arm64", expected.getMessage());
        }
        runtime.startDownload();
        assertEquals(LocalAiRuntime.Phase.ERROR, runtime.status().phase);
        assertEquals("needs arm64", runtime.status().error);
        assertEquals(0, backend.initializations.get());
        assertEquals(0, backend.connections.get());
    }

    @Test public void aBlockThatLosesALineIsTranslatedAgainPartByPart() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT);
        backend.reply = prompt -> prompt.contains(BLOCK_MARKER) ? "merged line only" : "EN:" + sourceOf(prompt);
        assertEquals(Arrays.asList("EN:一行目", "EN:二行目", "EN:三行目"),
                runtime.translate(Arrays.asList("一行目", "二行目", "三行目"), "en", MESSAGES, null));
        assertEquals(4, backend.prompts.size());
    }

    @Test public void theModelIsReleasedAfterTheIdleTimeAndReloadedOnTheNextRequest() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT);
        runtime.configure(LocalAiModels.LIGHT, 30);
        runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null);
        await(() -> timer.delays().equals(Collections.singletonList(30 * 60_000L)));
        timer.fire();
        await(() -> backend.frees.get() == 1);
        await(() -> runtime.status().phase == LocalAiRuntime.Phase.DOWNLOADED);
        runtime.translate(Collections.singletonList("夜明け"), "ko", MESSAGES, null);
        assertEquals(2, backend.loads.size());

        runtime.configure(LocalAiModels.LIGHT, 0);
        runtime.translate(Collections.singletonList("まで"), "ko", MESSAGES, null);
        Thread.sleep(150);
        assertTrue("Never keeps the model loaded", timer.delays().isEmpty());
        assertEquals(LocalAiRuntime.Phase.READY, runtime.status().phase);
    }

    @Test public void identicalRequestsShareOneJobAndReplayFinishedRows() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT);
        List<String> rows = Arrays.asList("[Intro]", "君の声", "夜明け");
        backend.holdNextCall();
        Future<List<String>> first = callers.submit(() -> runtime.translate(rows, "ko", MESSAGES, null));
        assertTrue(backend.started.await(5, TimeUnit.SECONDS));
        List<String> joinedLines = Collections.synchronizedList(new ArrayList<>());
        Future<List<String>> joined = callers.submit(() ->
                runtime.translate(rows, "ko", MESSAGES, (row, text) -> joinedLines.add(row + "=" + text)));
        await(() -> joinedLines.contains("0=[Intro]"));
        backend.release();
        List<String> expected = Arrays.asList("[Intro]", "KO:君の声", "KO:夜明け");
        assertEquals(expected, first.get(5, TimeUnit.SECONDS));
        assertEquals(expected, joined.get(5, TimeUnit.SECONDS));
        assertEquals(1, backend.prompts.size());
        assertEquals(Arrays.asList("0=[Intro]", "1=KO:君の声", "2=KO:夜明け"), joinedLines);
    }

    @Test public void aNewerRequestReplacesTheRunningOneWithoutWaitingForIt() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT);
        backend.holdNextCall();
        Future<List<String>> previousTrack = callers.submit(() ->
                runtime.translate(Collections.singletonList("前の曲"), "ko", MESSAGES, null));
        assertTrue(backend.started.await(5, TimeUnit.SECONDS));
        List<String> current = runtime.translate(Collections.singletonList("今の曲"), "ko", MESSAGES, null);
        assertEquals(Collections.singletonList("KO:今の曲"), current);
        assertTrue("the running generation was aborted", backend.abortRequested);
        try {
            previousTrack.get(5, TimeUnit.SECONDS);
            fail("The replaced request must end");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof LocalAiRuntime.SupersededException);
        }
    }

    @Test public void aNativeFailureReleasesTheModelAndReportsTheError() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT);
        backend.failure = new IOException("Out of memory");
        try {
            runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null);
            fail("The native failure must reach the caller");
        } catch (IOException expected) {
            assertEquals("Out of memory", expected.getMessage());
        }
        assertEquals(1, backend.frees.get());
        assertEquals(LocalAiRuntime.Phase.ERROR, runtime.status().phase);
        assertEquals("Out of memory", runtime.status().error);
        assertEquals(Collections.singletonList("KO:君の声"),
                runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null));
        assertEquals(2, backend.loads.size());
        assertEquals(LocalAiRuntime.Phase.READY, runtime.status().phase);
    }

    @Test public void choosingAnotherTierReleasesTheLoadedModel() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT, LocalAiModels.HIGH);
        runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null);
        runtime.configure(LocalAiModels.HIGH, 10);
        await(() -> backend.frees.get() == 1);
        assertEquals(LocalAiRuntime.Phase.DOWNLOADED, runtime.status().phase);
        runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null);
        assertEquals(Arrays.asList(LocalAiModels.LIGHT.fileName, LocalAiModels.HIGH.fileName), backend.loads);
    }

    @Test public void deletingTheModelReleasesItAndRemovesOnlyThatTier() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT, LocalAiModels.MEDIUM);
        runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null);
        runtime.delete(LocalAiModels.LIGHT);
        await(() -> runtime.status().phase == LocalAiRuntime.Phase.MISSING);
        assertEquals(1, backend.frees.get());
        assertFalse(runtime.modelFile(LocalAiModels.LIGHT).exists());
        assertTrue(runtime.status().isDownloaded(LocalAiModels.MEDIUM));
    }

    @Test public void testTranslationUsesTheDesktopSamples() throws Exception {
        LocalAiRuntime runtime = runtime(null, LocalAiModels.LIGHT);
        LocalAiRuntime.TestResult korean = runtime.testTranslation("ko", MESSAGES).get(5, TimeUnit.SECONDS);
        assertEquals("今夜はまだ始まったばかり", korean.sample);
        assertEquals("KO:今夜はまだ始まったばかり", korean.line);
        assertTrue(korean.seconds >= 0);
        assertFalse("one part uses the single-line prompt", backend.prompts.get(0).contains(BLOCK_MARKER));
        assertEquals("The night is still young.", runtime.testTranslation("ja", MESSAGES).get(5, TimeUnit.SECONDS).sample);
        try {
            runtime.testTranslation("sv", MESSAGES).get(5, TimeUnit.SECONDS);
            fail("Swedish is not supported");
        } catch (ExecutionException expected) {
            assertEquals("cannot translate into sv", expected.getCause().getMessage());
        }
    }

    @Test public void whileTheSelectedTierDownloadsRequestsAreToldToWait() throws Exception {
        LocalAiRuntime runtime = runtime(null);
        runtime.startDownload();
        assertTrue(backend.downloadStarted.await(5, TimeUnit.SECONDS));
        assertEquals(LocalAiRuntime.Phase.DOWNLOADING, runtime.status().phase);
        try {
            runtime.translate(Collections.singletonList("君の声"), "ko", MESSAGES, null);
            fail("The model is still downloading");
        } catch (LocalAiRuntime.LocalAiException expected) {
            assertEquals("download busy", expected.getMessage());
        }
        runtime.cancelDownload();
        await(() -> runtime.status().phase == LocalAiRuntime.Phase.MISSING);
        assertEquals(0, backend.initializations.get());
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for the Local AI worker");
            Thread.sleep(5);
        }
    }

    private static final class FakeTimer implements LocalAiRuntime.Timer {
        private final Map<Integer, Runnable> tasks = new ConcurrentHashMap<>();
        private final Map<Integer, Long> delays = new ConcurrentHashMap<>();
        private final AtomicInteger ids = new AtomicInteger();

        @Override public Runnable schedule(Runnable task, long delayMillis) {
            int id = ids.incrementAndGet();
            tasks.put(id, task);
            delays.put(id, delayMillis);
            return () -> { tasks.remove(id); delays.remove(id); };
        }

        List<Long> delays() { return new ArrayList<>(delays.values()); }

        void fire() {
            for (Integer id : new ArrayList<>(tasks.keySet())) {
                Runnable task = tasks.remove(id);
                delays.remove(id);
                if (task != null) task.run();
            }
        }
    }

    private static final class FakeBackend implements LocalAiRuntime.Backend {
        final List<String> prompts = Collections.synchronizedList(new ArrayList<>());
        final List<String> loads = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> threads = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger initializations = new AtomicInteger();
        final AtomicInteger frees = new AtomicInteger();
        final AtomicInteger connections = new AtomicInteger();
        final CountDownLatch downloadStarted = new CountDownLatch(1);
        volatile java.util.function.Function<String, String> reply = prompt -> {
            StringBuilder text = new StringBuilder();
            for (String line : sourceOf(prompt).split("\n")) text.append(text.length() > 0 ? "\n" : "").append("KO:").append(line);
            return text.toString();
        };
        volatile Exception failure;
        volatile boolean abortRequested;
        volatile CountDownLatch started = new CountDownLatch(1);
        private volatile CountDownLatch hold;
        private long handles;

        void holdNextCall() {
            started = new CountDownLatch(1);
            hold = new CountDownLatch(1);
        }

        void release() {
            CountDownLatch latch = hold;
            hold = null;
            if (latch != null) latch.countDown();
        }

        @Override public void initialize() { initializations.incrementAndGet(); }

        @Override public synchronized long load(File model, int contextSize, int batchSize, int threads) {
            assertEquals(4096, contextSize);
            assertEquals(512, batchSize);
            loads.add(model.getName());
            this.threads.add(threads);
            return ++handles;
        }

        @Override public LocalAiTranslator.Completion complete(long handle, String prompt, int maxTokens) throws Exception {
            prompts.add(prompt);
            CountDownLatch latch = hold;
            if (latch != null) {
                started.countDown();
                while (!latch.await(5, TimeUnit.MILLISECONDS)) {
                    if (abortRequested) {
                        hold = null;
                        throw new CancellationException("aborted");
                    }
                }
            }
            Exception error = failure;
            if (error != null) {
                failure = null;
                throw error;
            }
            return new LocalAiTranslator.Completion(reply.apply(prompt), false);
        }

        @Override public void abort(long handle, boolean abort) {
            if (abort) abortRequested = true;
        }

        @Override public void free(long handle) { frees.incrementAndGet(); }

        HttpURLConnection open(URL url) {
            connections.incrementAndGet();
            CountDownLatch disconnected = new CountDownLatch(1);
            return new HttpURLConnection(url) {
                @Override public int getResponseCode() throws IOException {
                    downloadStarted.countDown();
                    try {
                        disconnected.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    throw new IOException("closed");
                }
                @Override public void disconnect() { disconnected.countDown(); }
                @Override public boolean usingProxy() { return false; }
                @Override public void connect() { }
            };
        }
    }
}
