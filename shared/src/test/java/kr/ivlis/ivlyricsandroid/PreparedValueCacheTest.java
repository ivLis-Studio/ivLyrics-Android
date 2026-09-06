package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import static org.junit.Assert.*;

public class PreparedValueCacheTest {
    @Test public void sharesPreparationAndDeliversOnlyAfterWorkerCompletion() {
        Queue<Runnable> work = new ArrayDeque<>(), delivery = new ArrayDeque<>();
        PreparedValueCache<String, String> cache = new PreparedValueCache<>(2, work::add, delivery::add);
        List<String> results = new ArrayList<>();
        cache.request("album:30", () -> "prepared", results::add);
        cache.request("album:30", () -> { throw new AssertionError("duplicate preparation"); }, results::add);
        assertEquals(1, work.size()); assertTrue(results.isEmpty());
        work.remove().run(); assertTrue(results.isEmpty());
        delivery.remove().run(); assertEquals(List.of("prepared", "prepared"), results);
        cache.request("album:30", () -> { throw new AssertionError("cache miss"); }, results::add);
        assertEquals(3, results.size()); assertTrue(work.isEmpty());
    }
    @Test public void dropsObsoletePreparationButKeepsOtherViewsSubscribed() {
        Queue<Runnable> work = new ArrayDeque<>(), delivery = new ArrayDeque<>();
        PreparedValueCache<String, String> cache = new PreparedValueCache<>(2, work::add, delivery::add);
        List<String> results = new ArrayList<>();
        Runnable first = cache.request("shared", () -> "ready", results::add);
        cache.request("shared", () -> { throw new AssertionError(); }, value -> results.add("second:" + value));
        first.run();
        work.remove().run(); delivery.remove().run();
        assertEquals(List.of("second:ready"), results);
        for (int i = 0; i < 100; i++) {
            Runnable cancel = cache.request("blur:" + i, () -> { throw new AssertionError("obsolete work ran"); }, results::add);
            cancel.run();
        }
        while (!work.isEmpty()) work.remove().run();
        assertTrue(delivery.isEmpty());
    }
    @Test public void boundedCacheSeparatesBlurSettingsAndRetriesFailures() {
        PreparedValueCache<String, String> cache = new PreparedValueCache<>(1, Runnable::run, Runnable::run);
        List<String> results = new ArrayList<>();
        cache.request("album:30", () -> "30", results::add);
        cache.request("album:40", () -> "40", results::add);
        cache.request("album:30", () -> "30-again", results::add);
        cache.request("broken", () -> { throw new IllegalStateException(); }, results::add);
        cache.request("broken", () -> "retry", results::add);
        assertEquals(java.util.Arrays.asList("30", "40", "30-again", null, "retry"), results);
    }
}
