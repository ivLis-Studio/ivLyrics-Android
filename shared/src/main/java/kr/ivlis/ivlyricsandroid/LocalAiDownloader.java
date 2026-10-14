package kr.ivlis.ivlyricsandroid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads one model file into "{target}.part", resuming with an HTTP Range request when a partial
 * file exists, and renames it only after its size equals the expected byte count.
 */
final class LocalAiDownloader {
    interface Progress {
        void onProgress(long loadedBytes, long totalBytes);
    }

    interface Connector {
        HttpURLConnection open(URL url) throws IOException;
    }

    private static final int MAX_REDIRECTS = 8;
    private static final long PROGRESS_INTERVAL_MS = 250L;
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)");

    private final Connector connector;
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;

    LocalAiDownloader(Connector connector) {
        this.connector = connector == null ? url -> (HttpURLConnection) url.openConnection() : connector;
    }

    static File partialFile(File target) {
        return new File(target.getPath() + ".part");
    }

    /** Stops the download; the partial file stays so the next download resumes it. */
    void cancel() {
        cancelled = true;
        HttpURLConnection connection = active;
        if (connection != null) {
            connection.disconnect();
        }
    }

    void download(String url, File target, long expectedBytes, Progress progress) throws IOException {
        if (target.isFile() && target.length() == expectedBytes) {
            return;
        }
        File directory = target.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create the model folder.");
        }
        File partial = partialFile(target);
        long existing = partial.isFile() ? partial.length() : 0L;
        if (existing > expectedBytes) {
            deleteOrThrow(partial);
            existing = 0L;
        }
        if (existing < expectedBytes) {
            transfer(new URL(url), partial, existing, expectedBytes, progress);
        }
        long size = partial.length();
        if (size != expectedBytes) {
            if (size > expectedBytes) {
                deleteOrThrow(partial);
            }
            throw new IOException("The downloaded file has " + size + " bytes instead of " + expectedBytes + ".");
        }
        if (target.exists()) {
            deleteOrThrow(target);
        }
        if (!partial.renameTo(target)) {
            throw new IOException("Could not move the downloaded model into place.");
        }
    }

    private void transfer(URL start, File partial, long existing, long expectedBytes, Progress progress)
            throws IOException {
        URL url = start;
        boolean restarted = false;
        for (int redirects = 0; ; ) {
            throwIfCancelled();
            HttpURLConnection connection = connector.open(url);
            active = connection;
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(20_000);
                connection.setReadTimeout(30_000);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (existing > 0) {
                    connection.setRequestProperty("Range", "bytes=" + existing + "-");
                }
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || ++redirects > MAX_REDIRECTS) {
                        throw new IOException("HTTP " + code + " without a usable redirect");
                    }
                    url = new URL(url, location);
                    continue;
                }
                if (code == 416 && existing > 0 && !restarted) {
                    // The partial file no longer matches the server's file: start again.
                    deleteOrThrow(partial);
                    existing = 0;
                    restarted = true;
                    url = start;
                    continue;
                }
                boolean append;
                if (code == 206 && existing > 0) {
                    Matcher range = CONTENT_RANGE.matcher(String.valueOf(connection.getHeaderField("Content-Range")));
                    if (!range.find() || Long.parseLong(range.group(1)) != existing
                            || (!"*".equals(range.group(3)) && Long.parseLong(range.group(3)) != expectedBytes)) {
                        throw new IOException("The server resumed the download at an unexpected position.");
                    }
                    append = true;
                } else if (code == 200) {
                    long length = contentLength(connection);
                    if (length >= 0 && length != expectedBytes) {
                        throw new IOException("The server reported " + length + " bytes instead of " + expectedBytes + ".");
                    }
                    append = false;
                    existing = 0;
                } else {
                    throw new IOException("HTTP " + code);
                }
                copy(connection, partial, append, existing, expectedBytes, progress);
                return;
            } catch (IOException error) {
                throwIfCancelled();
                throw error;
            } finally {
                active = null;
                connection.disconnect();
            }
        }
    }

    private void copy(HttpURLConnection connection, File partial, boolean append, long existing, long expectedBytes,
            Progress progress) throws IOException {
        long loaded = existing;
        long lastReport = 0L;
        report(progress, loaded, expectedBytes);
        try (InputStream input = connection.getInputStream();
                OutputStream output = new FileOutputStream(partial, append)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                throwIfCancelled();
                if (loaded + count > expectedBytes) {
                    throw new IOException("The server sent more data than expected.");
                }
                output.write(buffer, 0, count);
                loaded += count;
                long now = System.nanoTime() / 1_000_000L;
                if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                    lastReport = now;
                    report(progress, loaded, expectedBytes);
                }
            }
        }
        report(progress, loaded, expectedBytes);
    }

    private static long contentLength(HttpURLConnection connection) {
        String value = connection.getHeaderField("Content-Length");
        try {
            return value == null ? -1 : Long.parseLong(value.trim());
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    private static void report(Progress progress, long loaded, long total) {
        if (progress != null) {
            progress.onProgress(loaded, total);
        }
    }

    private void throwIfCancelled() {
        if (cancelled) {
            throw new CancellationException("The download was cancelled.");
        }
    }

    private static void deleteOrThrow(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not replace the previous download.");
        }
    }
}
