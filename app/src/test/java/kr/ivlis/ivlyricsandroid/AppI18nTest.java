package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppI18nTest {
    @Test
    public void persianLanguageTabHasNoCorruptedSuffix() {
        assertEquals("زبان", AppI18n.t("fa", "lyrics.tab.language"));
    }
}
