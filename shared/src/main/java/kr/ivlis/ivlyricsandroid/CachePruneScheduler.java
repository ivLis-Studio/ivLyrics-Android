package kr.ivlis.ivlyricsandroid;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** One maintenance scan per root and write burst, with a follow-up for writes during a scan. */
final class CachePruneScheduler {
    private final Consumer<Runnable> schedule;
    private final Consumer<File> prune;
    private final Map<File, Entry> pending = new HashMap<>();

    CachePruneScheduler(Consumer<Runnable> schedule, Consumer<File> prune) {
        this.schedule = schedule;
        this.prune = prune;
    }

    synchronized void request(File root) {
        if (root == null) return;
        File key = root.getAbsoluteFile();
        Entry entry = pending.get(key);
        if (entry != null) {
            if (entry.running) entry.dirty = true;
            return;
        }
        entry = new Entry();
        pending.put(key, entry);
        Entry scheduledEntry = entry;
        schedule.accept(() -> run(key, scheduledEntry));
    }

    private void run(File root, Entry entry) {
        synchronized (this) {
            entry.running = true;
            entry.dirty = false;
        }
        try {
            prune.accept(root);
        } finally {
            synchronized (this) {
                entry.running = false;
                if (entry.dirty) {
                    schedule.accept(() -> run(root, entry));
                } else {
                    pending.remove(root);
                }
            }
        }
    }

    private static final class Entry {
        boolean running;
        boolean dirty;
    }
}
