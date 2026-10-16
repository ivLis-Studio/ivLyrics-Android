package kr.ivlis.ivlyricsandroid;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Isolated, single-request playback transport. Never consults process cookies, authentication,
 * proxies or connection pools. Explicitly configured HTTP is allowed even inside a cleartext-denying
 * host process; HTTPS uses its normal default TLS factory and HTTPS endpoint identification.
 * No manifest, trust manager, hostname verifier or global network setting is changed.
 */
final class LrclibPlaybackConnection extends HttpURLConnection {
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final ThreadPoolExecutor DNS = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2), task -> {
                Thread thread = new Thread(task, "ivLyrics-LRCLIB-DNS");
                thread.setDaemon(true);
                return thread;
            });
    private final URI target;
    private final long deadlineNanos;
    private final SSLSocketFactory tlsFactory;
    private volatile Socket plainSocket;
    private volatile Socket activeSocket;
    private volatile Future<InetAddress[]> resolving;
    private volatile boolean closed;
    private BufferedInputStream input;
    private InputStream body;
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private int headerBytes;

    LrclibPlaybackConnection(URI target, long deadlineNanos) throws IOException {
        this(target, deadlineNanos, HttpsURLConnection.getDefaultSSLSocketFactory());
    }

    LrclibPlaybackConnection(URI target, long deadlineNanos, SSLSocketFactory tlsFactory) throws IOException {
        super(target.toURL());
        if ((!"http".equals(target.getScheme()) && !"https".equals(target.getScheme()))
                || target.getHost() == null || target.getRawUserInfo() != null || target.getRawFragment() != null) {
            throw new IOException("Invalid LRCLIB request URL");
        }
        this.target = target;
        this.deadlineNanos = deadlineNanos;
        this.tlsFactory = tlsFactory;
    }

    private int remainingMs() throws IOException {
        RequestCancellation.check();
        if (closed) throw new IOException("LRCLIB connection closed");
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) throw new SocketTimeoutException("LRCLIB playback deadline reached");
        return (int) Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
    }

    @Override public void connect() throws IOException {
        if (connected) return;
        if (!"GET".equals(method) || getDoOutput()) throw new IOException("LRCLIB playback is GET only");
        String host = target.getHost();
        int port = target.getPort() < 0 ? ("https".equals(target.getScheme()) ? 443 : 80) : target.getPort();
        InetAddress[] addresses;
        try {
            resolving = DNS.submit(() -> InetAddress.getAllByName(host));
            addresses = resolving.get(remainingMs(), TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            RequestCancellation.check();
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("LRCLIB hostname lookup failed", error);
        } finally {
            if (resolving != null) resolving.cancel(true);
            DNS.purge();
        }
        if (addresses.length == 0) throw new IOException("LRCLIB hostname has no address");
        IOException connectFailure = null;
        for (int index = 0; index < Math.min(addresses.length, 8); index++) {
            plainSocket = new Socket();
            activeSocket = plainSocket;
            try {
                plainSocket.connect(new InetSocketAddress(addresses[index], port), remainingMs());
                connectFailure = null;
                break;
            } catch (IOException error) {
                connectFailure = error;
                try { plainSocket.close(); } catch (IOException ignored) { }
                remainingMs();
            }
        }
        if (connectFailure != null) throw connectFailure;
        plainSocket.setSoTimeout(remainingMs());
        if ("https".equals(target.getScheme())) {
            SSLSocket tls = (SSLSocket) tlsFactory.createSocket(plainSocket, host, port, true);
            activeSocket = tls;
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(parameters);
            tls.setSoTimeout(remainingMs());
            tls.startHandshake(); // Trust failures never retry as HTTP or with weaker verification.
        }
        String path = target.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (target.getRawQuery() != null) path += "?" + target.getRawQuery();
        if (path.length() > 16 * 1024) throw new IOException("LRCLIB request target too long");
        String request = "GET " + path + " HTTP/1.1\r\nHost: " + target.getRawAuthority()
                + "\r\nAccept: application/json\r\nUser-Agent: ivLyrics-Android/0.1"
                + "\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
        remainingMs();
        activeSocket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        activeSocket.getOutputStream().flush();
        input = new BufferedInputStream(activeSocket.getInputStream());
        connected = true;
    }

    private String line() throws IOException {
        activeSocket.setSoTimeout(remainingMs());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            remainingMs();
            int value = input.read();
            if (value < 0) throw new EOFException("Truncated LRCLIB headers");
            if (++headerBytes > MAX_HEADER_BYTES || bytes.size() >= 8192) throw new IOException("LRCLIB headers too large");
            if (value == '\r') {
                if (input.read() != '\n') throw new IOException("Malformed LRCLIB header line");
                headerBytes++;
                return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
            }
            if (value < 32 && value != '\t' || value > 126) throw new IOException("Malformed LRCLIB header character");
            bytes.write(value);
        }
    }

    @Override public int getResponseCode() throws IOException {
        if (responseCode >= 0) return responseCode;
        connect();
        String status = line();
        if (!status.matches("HTTP/1\\.[01] [1-5][0-9]{2}(?: .*)?")) throw new IOException("Malformed LRCLIB status line");
        responseCode = Integer.parseInt(status.substring(9, 12));
        for (String header = line(); !header.isEmpty(); header = line()) {
            int colon = header.indexOf(':');
            if (colon <= 0 || !header.substring(0, colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw new IOException("Malformed LRCLIB header");
            String key = header.substring(0, colon);
            String value = header.substring(colon + 1).trim();
            if (headers.containsKey(key) && (key.equalsIgnoreCase("Content-Length") || key.equalsIgnoreCase("Transfer-Encoding"))) {
                throw new IOException("Ambiguous LRCLIB response framing");
            }
            headers.put(key, value);
        }
        return responseCode;
    }

    @Override public InputStream getInputStream() throws IOException {
        getResponseCode();
        if (body != null) return body;
        String encoding = headers.get("Content-Encoding");
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw new IOException("Unsupported LRCLIB content encoding");
        String transfer = headers.get("Transfer-Encoding");
        if (transfer != null && (!transfer.equalsIgnoreCase("chunked") || headers.containsKey("Content-Length"))) {
            throw new IOException("Unsupported LRCLIB response framing");
        }
        long length = -1L;
        if (headers.containsKey("Content-Length")) {
            try {
                String raw = headers.get("Content-Length");
                if (!raw.matches("[0-9]+")) throw new NumberFormatException();
                length = Long.parseLong(raw);
                if (length > MAX_BODY_BYTES) throw new IOException("LRCLIB response too large");
            } catch (NumberFormatException error) { throw new IOException("Invalid LRCLIB content length", error); }
        }
        body = new FramedBody(transfer != null, length);
        return body;
    }

    private final class FramedBody extends InputStream {
        private final boolean chunked;
        private long remaining;
        private int total;
        private boolean needsChunkTerminator;
        private boolean ended;
        FramedBody(boolean chunked, long length) { this.chunked = chunked; remaining = chunked ? 0 : length; }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
        }
        @Override public int read(byte[] bytes, int offset, int count) throws IOException {
            if (count == 0) return 0;
            if (ended) return -1;
            activeSocket.setSoTimeout(remainingMs());
            if (chunked && remaining == 0) {
                if (needsChunkTerminator && !line().isEmpty()) throw new IOException("Invalid LRCLIB chunk terminator");
                String size = line().split(";", 2)[0];
                try {
                    if (!size.matches("[0-9a-fA-F]+")) throw new NumberFormatException();
                    remaining = Long.parseLong(size, 16);
                } catch (NumberFormatException error) { throw new IOException("Invalid LRCLIB chunk size", error); }
                if (remaining > MAX_BODY_BYTES - total) throw new IOException("LRCLIB response too large");
                if (remaining == 0) {
                    while (!line().isEmpty()) { /* Bounded trailers are ignored; never acted upon. */ }
                    ended = true;
                    return -1;
                }
                needsChunkTerminator = true;
            }
            if (remaining == 0) { ended = true; return -1; }
            int read = input.read(bytes, offset, remaining < 0 ? count : (int) Math.min(count, remaining));
            if (read < 0) {
                if (remaining > 0) throw new EOFException("Truncated LRCLIB body");
                ended = true;
                return -1;
            }
            total += read;
            if (total > MAX_BODY_BYTES) throw new IOException("LRCLIB response too large");
            if (remaining > 0) remaining -= read;
            return read;
        }
        @Override public void close() { disconnect(); }
    }

    @Override public String getHeaderField(String name) { return headers.get(name); }
    @Override public boolean usingProxy() { return false; }
    @Override public void disconnect() {
        closed = true;
        Future<InetAddress[]> pending = resolving;
        if (pending != null) pending.cancel(true);
        Socket active = activeSocket, plain = plainSocket;
        if (active != null) try { active.close(); } catch (IOException ignored) { }
        if (plain != null) try { plain.close(); } catch (IOException ignored) { }
    }
}
