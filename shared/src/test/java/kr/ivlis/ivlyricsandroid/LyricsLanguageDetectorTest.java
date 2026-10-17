package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class LyricsLanguageDetectorTest {
    @Test public void repeatedAndReplacementPayloadsKeepOriginalDetection() throws Exception {
        String korean = "오늘도 너를 기다리고 있어\n함께 걷던 그 길에서";
        String japanese = "君と歩いたこの道で\nいつまでも夢を見ている";
        for (int repeat = 0; repeat < 50; repeat++) {
            assertEquals("ko", LyricsLanguageDetector.detect(korean));
            assertEquals("ja", LyricsLanguageDetector.detect(japanese));
            assertNull(LyricsLanguageDetector.detect("123 !!!"));
        }
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> work = new ArrayList<>();
            for (int i = 0; i < 32; i++) work.add(() -> LyricsLanguageDetector.detect(korean));
            for (var result : executor.invokeAll(work)) assertEquals("ko", result.get());
        } finally {
            executor.shutdownNow();
        }
        assertNull(LyricsLanguageDetector.detect(null));
        assertNull(LyricsLanguageDetector.detect("  \n  "));
    }

    @Test public void cacheBoundsMemoryAndRemembersUndetectableText() throws Exception {
        Field field = LyricsLanguageDetector.class.getDeclaredField("detectionCache");
        field.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) field.get(null);
        synchronized (cache) {
            cache.clear();
            LyricsLanguageDetector.detect("123 !!!");
            assertTrue(cache.containsKey("123 !!!"));
            assertNull(cache.get("123 !!!"));
            for (int i = 0; i < 20; i++) LyricsLanguageDetector.detect("오늘도 너를 기다린다 " + i);
            assertEquals(8, cache.size());
            assertFalse(cache.containsKey("123 !!!"));
            String oversized = "오늘도 너를 기다린다\n".repeat(7_000);
            assertEquals("ko", LyricsLanguageDetector.detect(oversized));
            assertFalse(cache.containsKey(oversized));
        }
    }
}
