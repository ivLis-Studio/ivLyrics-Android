package kr.ivlis.ivlyricsandroid;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class DiskCachePolicy {
    static final long MAX_AGE_MS = 365L * 24L * 60L * 60L * 1_000L;
    static final long MAX_TOTAL_BYTES = 10L * 1024L * 1024L * 1024L;
    private static final ScheduledExecutorService MAINTENANCE = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ivlyrics-cache-prune");
        thread.setDaemon(true);
        return thread;
    });
    private static final CachePruneScheduler PRUNER = new CachePruneScheduler(
            work -> MAINTENANCE.schedule(work, 1L, TimeUnit.SECONDS), DiskCachePolicy::pruneToSize);

    private DiskCachePolicy() {
    }

    static void schedulePrune(File root) {
        PRUNER.request(root);
    }

    static void pruneToSize(File root) {
        pruneToSize(root, MAX_TOTAL_BYTES);
    }

    static void pruneToSize(File root, long maxBytes) {
        if (root == null || !root.isDirectory() || maxBytes < 0L) {
            return;
        }
        List<File> files = new ArrayList<>();
        collectCacheFiles(root, files);
        long totalBytes = 0L;
        for (File file : files) {
            totalBytes += Math.max(0L, file.length());
        }
        if (totalBytes <= maxBytes) {
            return;
        }
        files.sort(Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            long length = Math.max(0L, file.length());
            if (file.delete()) {
                totalBytes -= length;
            }
            if (totalBytes <= maxBytes) {
                break;
            }
        }
    }

    private static void collectCacheFiles(File directory, List<File> output) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectCacheFiles(child, output);
            } else if (child.isFile() && !child.getName().endsWith(".tmp")) {
                output.add(child);
            }
        }
    }
}
