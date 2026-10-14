package kr.ivlis.ivlyricsandroid;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in host check: runs the app's Local AI path (LocalAiRuntime, LocalAiTranslator, LocalAiNative and the
 * JNI wrapper) on macOS arm64 against llama.cpp b11364 and a downloaded Light model.
 * Arguments: LIBRARY_DIR MODEL_DIR [DOWNLOAD_SCRATCH_DIR]
 */
public final class LocalAiHostCheck {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        String.format("%.2f", 0.0); // Load the formatter first so row timestamps measure the engine.
        File libraries = new File(args[0]);
        File models = new File(args[1]);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "local-ai-idle");
            thread.setDaemon(true);
            return thread;
        });
        LocalAiRuntime.Timer timer = (task, delay) -> {
            ScheduledFuture<?> scheduled = scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
            return () -> scheduled.cancel(false);
        };
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        LocalAiRuntime runtime = new LocalAiRuntime(models,
                new LocalAiNative(() -> libraries, Collections.singletonList("libivlyrics_localai.dylib"), false),
                timer, task -> {
                    Thread thread = new Thread(task, "ivLyrics Local AI");
                    thread.setDaemon(true);
                    return thread;
                }, threads, null, null);
        runtime.configure(LocalAiModels.LIGHT, 10);
        LocalAiRuntime.Messages messages = new LocalAiRuntime.Messages() {
            @Override public String notReady() { return "The local model is not ready yet."; }
            @Override public String downloadBusy() { return "Wait for the model download to finish, then try again."; }
            @Override public String unsupportedLanguage(String code) { return "The local model cannot translate into " + code + "."; }
            @Override public String unsupportedDevice(String reason) { return reason; }
        };
        System.out.println("JDK " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + " "
                + System.getProperty("os.arch") + ", " + threads + " threads, CPU only (n_gpu_layers 0, no op offload)");
        check(runtime.status().phase == LocalAiRuntime.Phase.DOWNLOADED, "the Light model is present before loading");

        List<String> rows = Arrays.asList(
                "君の声が聞こえる", "", "[Instrumental]", "夜明けまで / 走り続けよう", "涙の跡をたどって",
                "君の声が聞こえる", "もう一度だけ笑ってみせて", "星降る夜に願いをかけた");
        for (int run = 1; run <= 2; run++) {
            long started = System.nanoTime();
            List<String> reported = Collections.synchronizedList(new ArrayList<>());
            List<String> result = runtime.translate(rows, "ko", messages, (row, text) -> reported.add(
                    String.format("    +%.2fs row %d", (System.nanoTime() - started) / 1e9, row)));
            System.out.printf("Run %d (%s): %.2f s%n", run, run == 1 ? "cold: engine init, model load, translation" : "warm", (System.nanoTime() - started) / 1e9);
            for (String line : reported) System.out.println(line);
            for (int i = 0; i < rows.size(); i++) System.out.println("    " + rows.get(i) + "  =>  " + result.get(i));
            check(result.size() == rows.size(), "one translation per row");
            check(result.get(1).isEmpty() && result.get(2).equals("[Instrumental]"), "blank and marker rows are kept");
            check(result.get(0).equals(result.get(5)), "a repeated line is translated once");
            check(result.get(3).contains(" / "), "simultaneous vocal parts keep their separator");
            for (int i : new int[]{0, 3, 4, 6, 7}) {
                check(result.get(i).codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3), "row " + i + " is Korean");
            }
            check(reported.size() == rows.size(), "every row is reported once");
            check(runtime.status().phase == LocalAiRuntime.Phase.READY && "CPU".equals(runtime.status().device), "ready on the CPU");
        }
        LocalAiRuntime.TestResult test = runtime.testTranslation("ko", messages).get(60, TimeUnit.SECONDS);
        System.out.printf("Test translation: ✓ %s → %s (%.1fs)%n", test.sample, test.line, test.seconds);
        check(!test.line.isEmpty(), "the test translation returns a line");
        try {
            runtime.translate(Collections.singletonList("君の声"), "sv", messages, null);
            check(false, "Swedish must be refused");
        } catch (LocalAiRuntime.LocalAiException expected) {
            System.out.println("Unsupported language: " + expected.getMessage());
            checks++;
        }
        System.out.println("ggml: " + LocalAiNative.systemInfo());
        runtime.unload();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (runtime.status().phase != LocalAiRuntime.Phase.DOWNLOADED && System.nanoTime() < deadline) Thread.sleep(10);
        check(runtime.status().phase == LocalAiRuntime.Phase.DOWNLOADED, "Free memory returns to Downloaded");
        if (args.length > 2) checkResume(new File(args[2]), new File(models, LocalAiModels.LIGHT.fileName));
        System.out.println("LOCAL_AI_HOST_CHECK_PASSED checks=" + checks);
    }

    /** Downloads the start of the Light model from Hugging Face twice, resuming the partial file. */
    private static void checkResume(File directory, File reference) throws Exception {
        File target = new File(directory, LocalAiModels.LIGHT.fileName);
        File partial = LocalAiDownloader.partialFile(target);
        for (int attempt = 0; attempt < 2; attempt++) {
            long before = partial.isFile() ? partial.length() : 0L;
            long stop = before + 3_000_000L;
            LocalAiDownloader downloader = new LocalAiDownloader(null);
            try {
                downloader.download(LocalAiModels.LIGHT.url(), target, LocalAiModels.LIGHT.sizeBytes, (loaded, total) -> {
                    if (loaded >= stop) downloader.cancel();
                });
                check(false, "the download was meant to be cancelled");
            } catch (CancellationException cancelled) {
                byte[] kept = Files.readAllBytes(partial.toPath());
                byte[] expected = new byte[kept.length];
                try (RandomAccessFile file = new RandomAccessFile(reference, "r")) {
                    file.readFully(expected);
                }
                System.out.println("Hugging Face download cancelled at " + kept.length + " bytes; partial matches the model: "
                        + Arrays.equals(kept, expected));
                check(Arrays.equals(kept, expected) && kept.length >= stop && kept.length > before,
                        "the partial file grew from " + before + " bytes and matches the model");
            }
        }
        check(partial.delete(), "the scratch partial file is removed");
    }
}
