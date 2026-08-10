package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KeylessTranslationProvidersTest {
    @Test
    public void formEncodingUsesApi26CompatibleUtf8Overload() {
        assertEquals("hello%20world%2F%ED%95%9C%EA%B5%AD", KeylessTranslationProviders.encode("hello world/한국"));
    }

    @Test
    public void nullEncodingIsEmpty() {
        assertEquals("", KeylessTranslationProviders.encode(null));
    }
}
