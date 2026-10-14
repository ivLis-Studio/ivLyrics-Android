package kr.ivlis.ivlyricsandroid;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalAiDownloaderTest {
    private static final String URL = "https://models.example/resolve/model.gguf";
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    private final byte[] model = new byte[300_000];
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private boolean ignoreRange;

    public LocalAiDownloaderTest() {
        new Random(7).nextBytes(model);
    }

    /** A resolve URL redirecting to a CDN file that honours "Range: bytes=N-", like Hugging Face. */
    private HttpURLConnection open(URL url) {
        return new HttpURLConnection(url) {
            private final Map<String, String> request = new HashMap<>();
            private final Map<String, String> response = new HashMap<>();
            private int code;
            private byte[] body = new byte[0];

            @Override public void setRequestProperty(String key, String value) { request.put(key, value); }

            @Override public int getResponseCode() {
                if (code != 0) return code;
                String range = request.get("Range");
                requests.add(url.getPath() + " " + range);
                if (url.getPath().startsWith("/resolve/")) {
                    response.put("Location", "/cdn/model.gguf?signed=1");
                    return code = 302;
                }
                int start = range == null || ignoreRange ? 0 : Integer.parseInt(range.replace("bytes=", "").replace("-", ""));
                body = Arrays.copyOfRange(model, start, model.length);
                response.put("Content-Length", String.valueOf(body.length));
                if (start > 0) {
                    response.put("Content-Range", "bytes " + start + "-" + (model.length - 1) + "/" + model.length);
                    return code = 206;
                }
                return code = 200;
            }

            @Override public String getHeaderField(String name) {
                getResponseCode();
                return response.get(name);
            }

            @Override public InputStream getInputStream() {
                getResponseCode();
                return new ByteArrayInputStream(body);
            }

            @Override public void disconnect() { }
            @Override public boolean usingProxy() { return false; }
            @Override public void connect() { }
        };
    }

    private File target() {
        return new File(folder.getRoot(), "models/model.gguf");
    }

    @Test public void downloadsThroughARedirectAndVerifiesTheSize() throws Exception {
        List<Long> progress = new ArrayList<>();
        new LocalAiDownloader(this::open).download(URL, target(), model.length, (loaded, total) -> {
            progress.add(loaded);
            assertEquals(model.length, total);
        });
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
        assertFalse(LocalAiDownloader.partialFile(target()).exists());
        assertEquals(Long.valueOf(model.length), progress.get(progress.size() - 1));
        assertEquals(Arrays.asList("/resolve/model.gguf null", "/cdn/model.gguf null"), requests);
    }

    @Test public void aPartialFileResumesWithARangeRequestThroughTheRedirect() throws Exception {
        assertTrue(target().getParentFile().mkdirs());
        Files.write(LocalAiDownloader.partialFile(target()).toPath(), Arrays.copyOf(model, 100_000));
        new LocalAiDownloader(this::open).download(URL, target(), model.length, null);
        assertEquals(Arrays.asList("/resolve/model.gguf bytes=100000-", "/cdn/model.gguf bytes=100000-"), requests);
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
    }

    @Test public void aServerThatIgnoresTheRangeRestartsFromTheBeginning() throws Exception {
        ignoreRange = true;
        assertTrue(target().getParentFile().mkdirs());
        Files.write(LocalAiDownloader.partialFile(target()).toPath(), Arrays.copyOf(model, 50_000));
        new LocalAiDownloader(this::open).download(URL, target(), model.length, null);
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
    }

    @Test public void cancellingKeepsThePartialFileForTheNextDownload() throws Exception {
        LocalAiDownloader downloader = new LocalAiDownloader(this::open);
        try {
            downloader.download(URL, target(), model.length, (loaded, total) -> {
                if (loaded > 0) downloader.cancel();
            });
            fail("The download was cancelled");
        } catch (CancellationException expected) {
            byte[] kept = Files.readAllBytes(LocalAiDownloader.partialFile(target()).toPath());
            assertTrue(kept.length > 0 && kept.length < model.length);
            assertArrayEquals(Arrays.copyOf(model, kept.length), kept);
            assertFalse(target().exists());
        }
        new LocalAiDownloader(this::open).download(URL, target(), model.length, null);
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
    }

    @Test public void aFileOfTheWrongSizeIsNeverMarkedReady() throws Exception {
        try {
            new LocalAiDownloader(this::open).download(URL, target(), model.length + 1, null);
            fail("The server's file is smaller than expected");
        } catch (IOException expected) {
            assertFalse(target().exists());
        }
    }

    @Test public void aCompletePartialFileIsUsedWithoutAnotherRequest() throws Exception {
        assertTrue(target().getParentFile().mkdirs());
        Files.write(LocalAiDownloader.partialFile(target()).toPath(), model);
        new LocalAiDownloader(url -> { throw new AssertionError("no request expected"); })
                .download(URL, target(), model.length, null);
        assertArrayEquals(model, Files.readAllBytes(target().toPath()));
    }
}
