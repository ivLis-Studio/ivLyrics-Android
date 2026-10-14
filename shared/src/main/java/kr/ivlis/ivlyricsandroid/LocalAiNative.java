package kr.ivlis.ivlyricsandroid;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * JNI bindings of libivlyrics_localai (shared/src/main/cpp/localai) on llama.cpp b11364.
 * Libraries are loaded by absolute path, dependencies first, so each library's DT_NEEDED entries
 * resolve to the copies already loaded. Any failure, including UnsatisfiedLinkError inside a host
 * process such as Spotify, becomes an IOException for the provider to report.
 */
final class LocalAiNative implements LocalAiRuntime.Backend {
    static final float TEMPERATURE = 0.7f;
    static final float TOP_P = 0.6f;
    static final int TOP_K = 20;
    static final float REPEAT_PENALTY = 1.05f;

    private static final Object LOCK = new Object();
    private static boolean initialized;
    private static String permanentFailure;
    private static String systemInfo = "";

    private final Callable<File> libraryDirectory;
    private final List<String> loadOrder;
    private final boolean scanBackends;

    /**
     * @param libraryDirectory supplies the directory holding the libraries (installed on first use)
     * @param loadOrder file names to load, dependencies first; the JNI wrapper comes last
     * @param scanBackends whether ggml should load its CPU backend variants from that directory
     */
    LocalAiNative(Callable<File> libraryDirectory, List<String> loadOrder, boolean scanBackends) {
        this.libraryDirectory = libraryDirectory;
        this.loadOrder = Collections.unmodifiableList(new ArrayList<>(loadOrder));
        this.scanBackends = scanBackends;
    }

    static String systemInfo() {
        synchronized (LOCK) {
            return systemInfo;
        }
    }

    // The libraries come from this APK's own assets, verified against their SHA-256 and read-only in
    // app-private storage; System.loadLibrary cannot reach them because they are not packaged in lib/.
    @Override
    @SuppressWarnings("UnsafeDynamicallyLoadedCode")
    public void initialize() throws IOException {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            if (permanentFailure != null) {
                throw new IOException(permanentFailure);
            }
            File directory;
            try {
                directory = libraryDirectory.call();
            } catch (IOException error) {
                throw error;
            } catch (Exception error) {
                throw new IOException(describe(error), error);
            }
            try {
                for (String library : loadOrder) {
                    System.load(new File(directory, library).getAbsolutePath());
                }
                byte[] info = nativeInit(scanBackends ? directory.getAbsolutePath().getBytes(StandardCharsets.UTF_8) : null);
                systemInfo = info == null ? "" : new String(info, StandardCharsets.UTF_8).trim();
                initialized = true;
            } catch (Throwable error) {
                // A library that failed to link stays unusable in this process; do not retry every request.
                permanentFailure = "Local AI engine unavailable: " + describe(error);
                throw new IOException(permanentFailure, error);
            }
        }
    }

    @Override
    public long load(File model, int contextSize, int batchSize, int threads) throws IOException {
        long handle = nativeLoad(model.getAbsolutePath().getBytes(StandardCharsets.UTF_8), contextSize, batchSize, threads);
        if (handle == 0) {
            throw new IOException("The model could not be loaded.");
        }
        return handle;
    }

    @Override
    public LocalAiTranslator.Completion complete(long handle, String prompt, int maxTokens) throws IOException {
        byte[] reply = nativeComplete(handle, prompt.getBytes(StandardCharsets.UTF_8), maxTokens,
                TEMPERATURE, TOP_P, TOP_K, REPEAT_PENALTY);
        if (reply == null || reply.length == 0) {
            throw new IOException("Local AI returned no reply.");
        }
        // The native side appends one status byte: 0 = end of generation, 1 = token limit.
        boolean stoppedAtTokenLimit = reply[reply.length - 1] != 0;
        return new LocalAiTranslator.Completion(
                new String(reply, 0, reply.length - 1, StandardCharsets.UTF_8), stoppedAtTokenLimit);
    }

    @Override
    public void abort(long handle, boolean abort) {
        if (handle != 0) {
            nativeAbort(handle, abort);
        }
    }

    @Override
    public void free(long handle) {
        if (handle != 0) {
            nativeFree(handle);
        }
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message.trim();
    }

    private static native byte[] nativeInit(byte[] backendDirectory) throws IOException;

    private static native long nativeLoad(byte[] modelPath, int contextSize, int batchSize, int threads)
            throws IOException;

    private static native byte[] nativeComplete(long handle, byte[] prompt, int maxTokens, float temperature,
            float topP, int topK, float repeatPenalty) throws IOException;

    private static native void nativeAbort(long handle, boolean abort);

    private static native void nativeFree(long handle);
}
