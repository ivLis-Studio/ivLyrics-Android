package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Android side of the Local AI provider, used by the standalone app and the Spotify module alike.
 * Models live in the active context's no-backup storage. The native engine ships as assets
 * (localai/arm64-v8a) and is extracted beside them on first use, so a host that cannot load it,
 * such as Spotify through LSPatch, reports an error and the next provider translates.
 */
final class LocalAiProvider {
    static final String ASSET_DIRECTORY = "localai/arm64-v8a";
    /** The APK would store "*.so" assets uncompressed; the suffix keeps them deflated until extraction. */
    static final String ASSET_SUFFIX = ".bin";
    private static final List<String> LOAD_ORDER =
            Arrays.asList("libggml-base.so", "libggml.so", "libllama.so", "libivlyrics_localai.so");
    private static LocalAiRuntime runtime;

    private LocalAiProvider() {
    }

    static synchronized LocalAiRuntime runtime(Context context) {
        if (runtime == null) {
            // Inside Spotify the module's application context reads the module's assets and Spotify's files.
            Context application = context.getApplicationContext() == null ? context : context.getApplicationContext();
            File root = new File(application.getNoBackupFilesDir(), "local-ai");
            ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, threads("ivLyrics Local AI idle", false));
            timer.setRemoveOnCancelPolicy(true);
            runtime = new LocalAiRuntime(
                    new File(root, "models"),
                    new LocalAiNative(() -> installNativeLibraries(application, new File(root, "native")), LOAD_ORDER, true),
                    (task, delayMillis) -> {
                        ScheduledFuture<?> scheduled = timer.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
                        return () -> scheduled.cancel(false);
                    },
                    threads("ivLyrics Local AI", true),
                    Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())),
                    unsupportedReason(),
                    null);
        }
        return runtime;
    }

    /** Translates lyric rows with Local AI; failures let the repository try the next enabled provider. */
    static List<String> translate(Context context, AiLyricsSettings.Snapshot settings, List<String> rows,
            String targetLanguage, LocalAiTranslator.LineListener listener) throws Exception {
        if (context == null) {
            throw new IOException("Local AI is not available here.");
        }
        LocalAiRuntime engine = runtime(context);
        engine.configure(LocalAiModels.tier(settings.localAiTier), settings.localAiIdleMinutes);
        return engine.translate(rows, targetLanguage, messages(settings.uiLang), listener);
    }

    /** A newer request replaced this one; its track is gone, so no other provider should run for it. */
    static boolean isSuperseded(Throwable error) {
        return error instanceof LocalAiRuntime.SupersededException;
    }

    /**
     * The UI text for a Local AI failure. Requests the provider rejects are worded by their messages
     * already; engine, model and download failures carry an i18n key. Their detail stays for the logs.
     */
    static String userMessage(Throwable failure, String uiLang) {
        if (failure instanceof LocalAiRuntime.LocalAiException) {
            return failure.getMessage();
        }
        if (failure instanceof LocalAiRuntime.Failure) {
            LocalAiRuntime.Failure known = (LocalAiRuntime.Failure) failure;
            return failureText(known.key, known.httpStatus, uiLang);
        }
        return failureText(LocalAiModels.ERROR_TRANSLATE, 0, uiLang);
    }

    /** Words a failure key of the runtime in the UI language; "{status}" takes the HTTP status. */
    static String failureText(String key, int httpStatus, String uiLang) {
        return LocalAiModels.format(AppI18n.t(uiLang, key), "status", String.valueOf(httpStatus));
    }

    static LocalAiRuntime.Messages messages(String uiLang) {
        return new LocalAiRuntime.Messages() {
            @Override
            public String notReady() {
                return AppI18n.t(uiLang, "local_ai.not_ready");
            }

            @Override
            public String downloadBusy() {
                return AppI18n.t(uiLang, "local_ai.download_busy");
            }

            @Override
            public String unsupportedLanguage(String languageCode) {
                return LocalAiModels.format(AppI18n.t(uiLang, "local_ai.unsupported_language"),
                        "language", languageLabel(languageCode, uiLang));
            }

            @Override
            public String unsupportedDevice(String reason) {
                return AppI18n.t(uiLang, reason);
            }
        };
    }

    static String languageLabel(String languageCode, String uiLang) {
        String tag = languageCode == null ? "" : languageCode.trim().replace('_', '-');
        if (tag.isEmpty()) {
            return "";
        }
        String label = Locale.forLanguageTag(tag)
                .getDisplayName(Locale.forLanguageTag(AiLyricsSettings.normalizeLanguageCode(uiLang)));
        return label == null || label.trim().isEmpty() || label.equalsIgnoreCase(tag) ? tag : label;
    }

    /** Null when this device can run the engine; otherwise the i18n key of why not. */
    static String unsupportedReason() {
        // The b11364 Android libraries use Android 9 relocation packing; older linkers would load them broken.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return LocalAiModels.NEEDS_ANDROID_9;
        }
        if (!Process.is64Bit() || !Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a")) {
            return LocalAiModels.NEEDS_ARM64;
        }
        return null;
    }

    private static ThreadFactory threads(String name, boolean inference) {
        return task -> {
            Thread thread = new Thread(() -> {
                if (inference) {
                    // Below the UI and audio, but outside the background group that would confine it to small cores.
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE);
                }
                task.run();
            }, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Extracts and verifies the engine libraries once per version; returns their directory. */
    private static File installNativeLibraries(Context context, File base) throws IOException {
        AssetManager assets = context.getAssets();
        String manifest;
        try (InputStream input = assets.open(ASSET_DIRECTORY + "/manifest.txt")) {
            manifest = new String(readAll(input), StandardCharsets.UTF_8);
        }
        List<String[]> entries = new ArrayList<>();
        for (String line : manifest.split("\n")) {
            String[] fields = line.trim().split(" ");
            if (fields.length == 3 && fields[2].matches("[A-Za-z0-9._-]+\\.so")) {
                entries.add(fields);
            }
        }
        if (entries.isEmpty()) {
            throw new IOException("The Local AI engine is missing from this app.");
        }
        File directory = new File(base, hex(sha256(manifest.getBytes(StandardCharsets.UTF_8))).substring(0, 16));
        File installed = new File(directory, "installed");
        if (!installed.isFile()) {
            deleteTree(directory);
            if (!directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("Could not install the Local AI engine.");
            }
            for (String[] entry : entries) {
                File target = new File(directory, entry[2]);
                File temporary = new File(directory, entry[2] + ".tmp");
                MessageDigest digest = sha256Digest();
                long size = 0;
                try (InputStream input = assets.open(ASSET_DIRECTORY + "/" + entry[2] + ASSET_SUFFIX);
                        OutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                        size += count;
                    }
                }
                if (size != Long.parseLong(entry[1]) || !hex(digest.digest()).equals(entry[0])) {
                    throw new IOException("The Local AI engine in this app is damaged.");
                }
                if (!temporary.renameTo(target)) {
                    throw new IOException("Could not install the Local AI engine.");
                }
                // Android 14+ refuses dynamically loaded code that its app can still write.
                if (!target.setReadOnly()) {
                    throw new IOException("Could not install the Local AI engine.");
                }
            }
            if (!installed.createNewFile() && !installed.isFile()) {
                throw new IOException("Could not finish installing the Local AI engine.");
            }
        }
        File[] versions = base.listFiles();
        if (versions != null) {
            for (File version : versions) {
                if (!version.equals(directory)) {
                    deleteTree(version);
                }
            }
        }
        return directory;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IOException(missing);
        }
    }

    private static byte[] sha256(byte[] data) throws IOException {
        return sha256Digest().digest(data);
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private static void deleteTree(File file) {
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
