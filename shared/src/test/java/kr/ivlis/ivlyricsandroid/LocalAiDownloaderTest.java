package kr.ivlis.ivlyricsandroid;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalAiDownloaderTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    private final byte[] model = new byte[300_000];
    private final List<String> ranges = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean ignoreRange;
    private HttpServer server;
    private String base;

    @Before public void start() throws IOException {
        new Random(7).nextBytes(model);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/resolve/model.gguf", exchange -> {
            exchange.getResponseHeaders().add("Location", "/cdn/model.gguf?signed=1");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/cdn/model.gguf", this::serve);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After public void stop() {
        server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        String range = exchange.getRequestHeaders().getFirst("Range");
        ranges.add(String.valueOf(range));
        int start = 0;
        if (range != null && !ignoreRange) {
            start = Integer.parseInt(range.replace("bytes=", "").replace("-", ""));
            exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + (model.length - 1) + "/" + model.length);
            exchange.sendResponseHeaders(206, model.length - start);
        } else {
            exchange.sendResponseHeaders(200, model.length);
        }
        try (OutputStream body = exchange.getResponseBody()) {
            for (int offset = start; offset < model.length; offset += 16_384) {
                body.write(model, offset, Math.min(16_384, model.length - offset));
            }
        } catch (IOException closedByClient) {
            // A cancelled download closes the connection.
        }
    }

    private File target() {
        return new File(folder.getRoot(), "models/model.gguf");
    }

    @Test public void downloadsThroughARedirectAndVerifiesTheSize() throws Exception {
        List<Long> progress = new ArrayList<>();
        new LocalAiDownloader(null).download(base + "/resolve/model.gguf", target(), model.length,
                (loaded, total) -> { progress.add(loaded); assertEquals(model.length, total); });
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
        assertFalse(LocalAiDownloader.partialFile(target()).exists());
        assertEquals(Long.valueOf(model.length), progress.get(progress.size() - 1));
        assertEquals(Collections.singletonList("null"), ranges);
    }

    @Test public void aPartialFileResumesWithARangeRequest() throws Exception {
        assertTrue(target().getParentFile().mkdirs());
        Files.write(LocalAiDownloader.partialFile(target()).toPath(), Arrays.copyOf(model, 100_000));
        new LocalAiDownloader(null).download(base + "/resolve/model.gguf", target(), model.length, null);
        assertEquals(Collections.singletonList("bytes=100000-"), ranges);
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
    }

    @Test public void aServerThatIgnoresTheRangeRestartsFromTheBeginning() throws Exception {
        ignoreRange = true;
        assertTrue(target().getParentFile().mkdirs());
        Files.write(LocalAiDownloader.partialFile(target()).toPath(), Arrays.copyOf(model, 50_000));
        new LocalAiDownloader(null).download(base + "/resolve/model.gguf", target(), model.length, null);
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
    }

    @Test public void cancellingKeepsThePartialFileForTheNextDownload() throws Exception {
        LocalAiDownloader downloader = new LocalAiDownloader(null);
        try {
            downloader.download(base + "/resolve/model.gguf", target(), model.length, (loaded, total) -> {
                if (loaded > 0) downloader.cancel();
            });
            fail("The download was cancelled");
        } catch (CancellationException expected) {
            File partial = LocalAiDownloader.partialFile(target());
            byte[] kept = Files.readAllBytes(partial.toPath());
            assertTrue(kept.length > 0 && kept.length < model.length);
            assertArrayEquals(Arrays.copyOf(model, kept.length), kept);
            assertFalse(target().exists());
        }
        new LocalAiDownloader(null).download(base + "/resolve/model.gguf", target(), model.length, null);
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
    }

    @Test public void aFileOfTheWrongSizeIsNeverMarkedReady() throws Exception {
        try {
            new LocalAiDownloader(null).download(base + "/resolve/model.gguf", target(), model.length + 1, null);
            fail("The server's file is smaller than expected");
        } catch (IOException expected) {
            assertFalse(target().exists());
        }
    }

    @Test public void aCompletePartialFileIsUsedWithoutAnotherRequest() throws Exception {
        assertTrue(target().getParentFile().mkdirs());
        Files.write(LocalAiDownloader.partialFile(target()).toPath(), model);
        new LocalAiDownloader(url -> { throw new AssertionError("no request expected"); })
                .download(base + "/resolve/model.gguf", target(), model.length, null);
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
    }
}
