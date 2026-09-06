package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import static org.junit.Assert.*;

public class CachePruneSchedulerTest {
    @Test public void batchesNamespacesAtTheSameRootWithoutPostponingMaintenance() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        List<File> scans = new ArrayList<>();
        CachePruneScheduler scheduler = new CachePruneScheduler(scheduled::add, scans::add);
        File root = new File("cache-a");
        for (int i = 0; i < 100; i++) scheduler.request(root);
        scheduler.request(new File("cache-b"));
        assertEquals(2, scheduled.size());
        scheduled.remove().run(); scheduled.remove().run();
        assertEquals(List.of(root.getAbsoluteFile(), new File("cache-b").getAbsoluteFile()), scans);
        scheduler.request(root);
        assertEquals(1, scheduled.size());
    }

    @Test public void writesDuringScanScheduleExactlyOneFollowUp() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        CachePruneScheduler[] owner = new CachePruneScheduler[1];
        int[] scans = {0};
        File root = new File("cache");
        owner[0] = new CachePruneScheduler(scheduled::add, directory -> {
            if (++scans[0] == 1) for (int i = 0; i < 100; i++) owner[0].request(root);
        });
        owner[0].request(root); scheduled.remove().run();
        assertEquals(1, scheduled.size());
        scheduled.remove().run();
        assertEquals(2, scans[0]); assertTrue(scheduled.isEmpty());
    }

    @Test public void failureDoesNotLeaveRootPermanentlyPending() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        CachePruneScheduler scheduler = new CachePruneScheduler(scheduled::add, root -> { throw new IllegalStateException(); });
        File root = new File("cache");
        scheduler.request(root);
        assertThrows(IllegalStateException.class, () -> scheduled.remove().run());
        scheduler.request(root); assertEquals(1, scheduled.size());
    }

    @Test public void keepsExistingLruLimitAndNeverDeletesInProgressTemporaryFiles() throws Exception {
        Path root = Files.createTempDirectory("ivlyrics-prune-test");
        try {
            Path older = Files.createDirectories(root.resolve("raw")).resolve("old.json");
            Path newer = Files.createDirectories(root.resolve("lyrics")).resolve("new.json");
            Path temp = root.resolve("raw/writing.tmp");
            Files.write(older, new byte[6]); Files.write(newer, new byte[6]); Files.write(temp, new byte[20]);
            assertTrue(older.toFile().setLastModified(1_000L)); assertTrue(newer.toFile().setLastModified(2_000L));
            DiskCachePolicy.pruneToSize(root.toFile(), 12L);
            assertTrue(Files.exists(older)); assertTrue(Files.exists(newer));
            DiskCachePolicy.pruneToSize(root.toFile(), 6L);
            assertFalse(Files.exists(older)); assertTrue(Files.exists(newer)); assertTrue(Files.exists(temp));
            DiskCachePolicy.pruneToSize(root.toFile(), 0L);
            assertFalse(Files.exists(newer)); assertTrue(Files.exists(temp));
        } finally {
            try (var paths = Files.walk(root)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete()); }
        }
    }
}
