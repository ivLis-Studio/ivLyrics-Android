package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

public final class AppI18nInitializationTest {
    @Test
    public void everyLanguageTableInitializes() throws Exception {
        Class.forName("kr.ivlis.ivlyricsandroid.AppI18n", true, AppI18n.class.getClassLoader());
    }
}
