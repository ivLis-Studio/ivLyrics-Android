package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class AiLyricsRepositoryCacheTest {
    @Test
    public void boundedCacheEvictsLeastRecentlyUsedEntry() {
        Map<String, Integer> cache = AiLyricsRepository.newBoundedCache(2);
        cache.put("first", 1);
        cache.put("second", 2);
        assertEquals(Integer.valueOf(1), cache.get("first"));
        cache.put("third", 3);

        assertTrue(cache.containsKey("first"));
        assertFalse(cache.containsKey("second"));
        assertTrue(cache.containsKey("third"));
        assertEquals(2, cache.size());
    }
}
