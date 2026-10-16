package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Request-local statistics, cancellation and every translated result string; no model or network. */
public class LocalAiOutputPerformanceTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    private static final LocalAiRuntime.Messages MESSAGES = new LocalAiRuntime.Messages() {
        @Override public String notReady() { return "not ready"; }
        @Override public String downloadBusy() { return "download busy"; }
        @Override public String unsupportedLanguage(String code) { return "unsupported " + code; }
        @Override public String unsupportedDevice(String reason) { return reason; }
    };

    private static final class Backend implements LocalAiRuntime.Backend {
        volatile LocalAiTranslator.Completion completion = new LocalAiTranslator.Completion("短い 😀", false,
                new LocalAiTranslator.Performance(8, 2));
        volatile boolean aborted;
        volatile CountDownLatch hold;
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        @Override public void initialize() { }
        @Override public long load(File model, int context, int batch, int threads) { return 1; }
        @Override public LocalAiTranslator.Completion complete(long handle, String prompt, int maxTokens) throws Exception {
            started.countDown();
            CountDownLatch pending = hold;
            while (pending != null && !pending.await(5, TimeUnit.MILLISECONDS)) {
                if (aborted) {
                    hold = null;
                    stopped.countDown();
                    throw new CancellationException("stopped");
                }
            }
            return completion;
        }
        @Override public void abort(long handle, boolean abort) { aborted = abort; }
        @Override public void free(long handle) { }
    }

    private LocalAiRuntime runtime(Backend backend) throws IOException {
        File directory = folder.newFolder();
        try (RandomAccessFile file = new RandomAccessFile(new File(directory, LocalAiModels.LIGHT.fileName), "rw")) {
            file.setLength(LocalAiModels.LIGHT.sizeBytes); // sparse fixture, no model data
        }
        LocalAiRuntime runtime = new LocalAiRuntime(directory, backend, (task, delay) -> () -> { }, task -> {
            Thread thread = new Thread(task, "local-ai-performance-test");
            thread.setDaemon(true);
            return thread;
        }, 1, null, url -> { throw new AssertionError("No network in this test"); });
        runtime.configure(LocalAiModels.LIGHT, 0);
        return runtime;
    }

    @Test public void actualTokenCountAndDecodeTimeTravelWithTheTestCompletion() throws Exception {
        Backend backend = new Backend();
        LocalAiRuntime runtime = runtime(backend);
        LocalAiRuntime.TestResult first = runtime.testTranslation("ko", MESSAGES).get(5, TimeUnit.SECONDS);
        assertEquals("短い 😀", first.line);
        assertSame(backend.completion.performance, first.performance);
        assertEquals(8, first.performance.tokens);
        assertEquals(2, first.performance.seconds, 0);
        assertEquals(4, first.performance.tokensPerSecond(), 0);
        assertTrue(first.seconds >= 0);
        backend.completion = new LocalAiTranslator.Completion("next", false, new LocalAiTranslator.Performance(2, 4));
        LocalAiRuntime.TestResult second = runtime.testTranslation("ko", MESSAGES).get(5, TimeUnit.SECONDS);
        assertEquals(0.5, second.performance.tokensPerSecond(), 0);
        assertEquals("the old result is immutable", 4, first.performance.tokensPerSecond(), 0);
        backend.completion = new LocalAiTranslator.Completion("unmeasured", false);
        assertNull(runtime.testTranslation("ko", MESSAGES).get(5, TimeUnit.SECONDS).performance);
        backend.completion = new LocalAiTranslator.Completion(" ", false, first.performance);
        try {
            runtime.testTranslation("ko", MESSAGES).get(5, TimeUnit.SECONDS);
            fail("empty output must not return a successful speed");
        } catch (ExecutionException expected) {
            assertNotNull(expected.getCause());
        }
    }

    @Test public void zeroNegativeAndNonFiniteStatisticsAreUnavailable() {
        for (LocalAiTranslator.Performance invalid : new LocalAiTranslator.Performance[]{
                new LocalAiTranslator.Performance(0, 1), new LocalAiTranslator.Performance(-1, 1),
                new LocalAiTranslator.Performance(8, 0), new LocalAiTranslator.Performance(8, -1),
                new LocalAiTranslator.Performance(8, Double.NaN), new LocalAiTranslator.Performance(8, Double.POSITIVE_INFINITY),
                new LocalAiTranslator.Performance(8, Double.MIN_VALUE)}) {
            assertNull(invalid.tokensPerSecond());
        }
    }

    @Test public void cancellingThePublicTestFutureAbortsGenerationAndCannotPublishStaleStatistics() throws Exception {
        Backend backend = new Backend();
        backend.hold = new CountDownLatch(1);
        LocalAiRuntime runtime = runtime(backend);
        CompletableFuture<LocalAiRuntime.TestResult> old = runtime.testTranslation("ko", MESSAGES);
        assertTrue(backend.started.await(5, TimeUnit.SECONDS));
        assertTrue(old.cancel(true));
        assertTrue("cancel reaches the native backend", backend.stopped.await(5, TimeUnit.SECONDS));
        backend.completion = new LocalAiTranslator.Completion("new", false, new LocalAiTranslator.Performance(3, 6));
        LocalAiRuntime.TestResult next = runtime.testTranslation("ko", MESSAGES).get(5, TimeUnit.SECONDS);
        assertEquals(0.5, next.performance.tokensPerSecond(), 0);
        assertTrue(old.isCancelled());
        assertEquals("new", next.line);
    }

    @Test public void everyLocaleHasTheMetricScopeAndConditionalSmallerPresetHint() {
        assertEquals(22, AppI18n.UI_LANGUAGES.size());
        String[] keys = {"test_total_time", "test_speed", "test_speed_unavailable", "test_speed_scope", "test_smaller_hint"};
        String[][] parameters = {{"seconds"}, {"seconds", "speed", "tokens"}, {}, {}, {"tier"}};
        Pattern placeholder = Pattern.compile("\\{(\\w+)\\}");
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (int i = 0; i < keys.length; i++) {
                String key = "local_ai." + keys[i];
                String text = AppI18n.t(language.code, key);
                assertFalse(language.code + ": " + key, text.equals(key) || text.trim().isEmpty());
                List<String> actual = new ArrayList<>();
                Matcher matcher = placeholder.matcher(text);
                while (matcher.find()) actual.add(matcher.group(1));
                Collections.sort(actual);
                assertEquals(language.code + ": " + key, Arrays.asList(parameters[i]), actual);
                assertFalse(text.matches("(?is).*(Hy-MT|GGUF|Tencent|wllama|https?://).*"));
            }
        }
        assertEquals("Total: {seconds}s", AppI18n.t("en", "local_ai.test_total_time"));
        assertTrue(AppI18n.t("ko", "local_ai.test_smaller_hint").startsWith("느리게 느껴진다면"));
    }
}
