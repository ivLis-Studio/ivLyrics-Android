package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class DiskCachePolicyTest {
    @Test
    public void policyKeepsOneYearAndCapsAtTenGibibytes() {
        assertEquals(365L * 24L * 60L * 60L * 1_000L, DiskCachePolicy.MAX_AGE_MS);
        assertEquals(10L * 1024L * 1024L * 1024L, DiskCachePolicy.MAX_TOTAL_BYTES);
    }

    @Test
    public void sizePruningRemovesOldestFilesFirst() throws Exception {
        File root = java.nio.file.Files.createTempDirectory("ivlyrics-cache-test").toFile();
        File older = write(root, "older.json", "1234567890");
        File newer = write(root, "newer.json", "abcdefghij");
        older.setLastModified(1_000L);
        newer.setLastModified(2_000L);

        DiskCachePolicy.pruneToSize(root, 10L);

        assertFalse(older.exists());
        assertTrue(newer.exists());
        newer.delete();
        root.delete();
    }

    private static File write(File root, String name, String value) throws Exception {
        File file = new File(root, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
