package kr.ivlis.ivlyricsandroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/** One bounded playback host attempt: no shared auth headers, redirects or authoring operations. */
final class LrclibPlaybackStage {
    interface ConnectionFactory {
        HttpURLConnection open(URI uri, long deadlineNanos) throws IOException;
    }
    static final int REQUEST_TIMEOUT_MS = 5_000;
    static final int STAGE_TIMEOUT_MS = 20_000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(work -> {
        Thread thread = new Thread(work, "ivLyrics-LRCLIB-deadline");
        thread.setDaemon(true);
        return thread;
    });
    final String base;
    final boolean custom;
    private final long deadlineNanos;
    private final ConnectionFactory connections;

    LrclibPlaybackStage(String base) {
        this(base, LrclibPlaybackConnection::new);
    }

    LrclibPlaybackStage(String base, ConnectionFactory connections) {
        this.base = base;
        this.connections = connections;
        custom = !LrclibEndpoint.OFFICIAL.equals(base);
        deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STAGE_TIMEOUT_MS);
    }

    String get(String suffix) throws IOException {
        RequestCancellation.check();
        long requestDeadline = Math.min(deadlineNanos,
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REQUEST_TIMEOUT_MS));
        int timeout = remainingMs(requestDeadline);
        HttpURLConnection connection = connections.open(URI.create(base + suffix), requestDeadline);
        ScheduledFuture<?> deadline = DEADLINES.schedule(connection::disconnect, timeout, TimeUnit.MILLISECONDS);
        try (RequestCancellation.AutoCloseableConnection ignored = RequestCancellation.register(connection)) {
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setAllowUserInteraction(false);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ivLyrics-Android/0.1");
            connection.connect();
            connection.setReadTimeout(remainingMs(requestDeadline));
            int status = connection.getResponseCode();
            remainingMs(requestDeadline);
            // Unsupported/missing query variants must not suppress this host's remaining searches.
            if (status == 204 || status == 400 || status == 404 || status == 405 || status == 410 || status == 422) return "";
            // Redirects are host failures, including redirects to the official API.
            if (status < 200 || status >= 300) throw new IOException("LRCLIB HTTP " + status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                while (true) {
                    RequestCancellation.check();
                    connection.setReadTimeout(remainingMs(requestDeadline));
                    int count = input.read(buffer);
                    if (count < 0) break;
                    if (output.size() + count > MAX_RESPONSE_BYTES) throw new IOException("LRCLIB response too large");
                    output.write(buffer, 0, count);
                }
                remainingMs(requestDeadline);
                RequestCancellation.check();
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            deadline.cancel(false);
        }
    }

    private static int remainingMs(long deadline) throws SocketTimeoutException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new SocketTimeoutException("LRCLIB playback deadline reached");
        return (int) Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
    }
}
