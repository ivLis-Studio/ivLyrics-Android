package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public final class InlineLyricPreviewModelTest {
    @Test public void mergedVocalsKeepBothTimelinesAndReusePreparedRows() throws Exception {
        LyricsLine.VocalPart lead = new LyricsLine.VocalPart("lead", "lead", "", "vocal", "hello",
                Collections.singletonList(new LyricsLine.Syllable("hello", 1000, 2500)));
        LyricsLine.VocalPart backing = new LyricsLine.VocalPart("backing", "background", "", "vocal", "world",
                Collections.singletonList(new LyricsLine.Syllable("world", 1800, 3000)));
        LyricsLine line = new LyricsLine(1000, 3000, "hello", Collections.emptyList(), "", "vocal",
                Arrays.asList(lead, backing));
        InlineLyricPreviewModel model = model(Collections.singletonList(line),
                AiLyricsSettings.PREVIEW_ITEM_ORIGINAL | AiLyricsSettings.PREVIEW_ITEM_TRANSLATION);
        InlineLyricPreviewModel.PreviewEntry entry = model.at(1100);
        List<MainLyricPreviewView.PreviewLine> rows = model.rows(entry);
        assertEquals(1, rows.size()); // Missing translation falls back without repeating original.
        assertEquals("hello world", rows.get(0).text);
        assertEquals(1000, rows.get(0).syllables.get(0).startTimeMs);
        assertEquals(1800, rows.get(0).syllables.get(2).startTimeMs);
        assertSame(entry, model.at(2100));
        assertSame(rows, model.rows(model.at(2200)));
    }

    @Test public void previewSelectionRetainsSupplementsAndTracksSeekAcrossInterlude() throws Exception {
        LyricsLine first = new LyricsLine(1000, 2000, "first", Collections.emptyList())
                .withSupplements("reading", "meaning");
        LyricsLine second = new LyricsLine(8000, 9000, "second", Collections.emptyList());
        InlineLyricPreviewModel model = model(Arrays.asList(first, second),
                AiLyricsSettings.PREVIEW_ITEM_PRONUNCIATION | AiLyricsSettings.PREVIEW_ITEM_TRANSLATION);
        assertEquals("prelude", model.at(100).interludeKind);
        List<MainLyricPreviewView.PreviewLine> rows = model.rows(model.at(1200));
        assertEquals(3, rows.size());
        assertEquals("first", rows.get(0).text);
        assertEquals("reading", rows.get(1).text);
        assertEquals("meaning", rows.get(2).text);
        assertSame(first, model.at(5200).line); // Existing 3500ms trailing delay.
        assertEquals("break", model.at(5600).interludeKind);
        assertSame(second, model.at(8500).line);
        assertSame(first, model.at(1500).line); // A backward seek invalidates the cached bucket.
    }

    @Test public void musicNoteMarkerHasItsOwnShortWindow() throws Exception {
        LyricsLine marker = new LyricsLine(2000, 2200, "♪", Collections.emptyList());
        LyricsLine after = new LyricsLine(2200, 4000, "after", Collections.emptyList());
        InlineLyricPreviewModel model = model(Arrays.asList(marker, after), AiLyricsSettings.PREVIEW_ITEM_ORIGINAL);
        assertTrue(model.at(2100).isInterlude());
        assertSame(after, model.at(2200).line);
    }

    @Test public void defaultOriginalSelectionAddsAvailableTranslationWithoutChangingSettings() throws Exception {
        LyricsLine line = new LyricsLine(1000, 3000, "original", Collections.emptyList())
                .withSupplements("reading", "translation");
        AiLyricsSettings.Snapshot settings = settings(AiLyricsSettings.PREVIEW_ITEM_ORIGINAL, true,
                Collections.emptyMap());
        InlineLyricPreviewModel model = model(Collections.singletonList(line), settings, "en");
        List<MainLyricPreviewView.PreviewLine> rows = model.rows(model.at(1500));
        assertEquals(2, rows.size());
        assertEquals("original", rows.get(0).text);
        assertEquals("translation", rows.get(1).text);
        assertEquals(AiLyricsSettings.TYPO_MAIN_PREVIEW_TRANSLATION, rows.get(1).slotId);
        assertEquals(AiLyricsSettings.PREVIEW_ITEM_ORIGINAL, settings.previewItems);
    }

    @Test public void disabledOrSameLanguageTranslationDoesNotExposeCachedSupplement() throws Exception {
        LyricsLine line = new LyricsLine(1000, 3000, "original", Collections.emptyList())
                .withSupplements("", "cached translation");
        int selection = AiLyricsSettings.PREVIEW_ITEM_ORIGINAL | AiLyricsSettings.PREVIEW_ITEM_TRANSLATION;
        assertEquals(1, model(Collections.singletonList(line), settings(selection, false,
                Collections.emptyMap()), "en").rows(modelEntry(line)).size());
        assertEquals(1, model(Collections.singletonList(line), settings(selection, true,
                Collections.emptyMap()), "ko").rows(modelEntry(line)).size());
        AiLyricsSettings.LanguageRule off = new AiLyricsSettings.LanguageRule("en", false, false, "ko");
        assertEquals(1, model(Collections.singletonList(line), settings(selection, true,
                Collections.singletonMap("en", off)), "en-US").rows(modelEntry(line)).size());
    }

    @Test public void noneRemainsHiddenAndMissingTranslationDoesNotDuplicateOriginal() throws Exception {
        LyricsLine line = new LyricsLine(1000, 3000, "original", Collections.emptyList());
        InlineLyricPreviewModel hidden = model(Collections.singletonList(line), AiLyricsSettings.PREVIEW_ITEM_NONE);
        assertTrue(hidden.rows(hidden.at(1500)).isEmpty());
        assertTrue(hidden.rows(hidden.at(100)).isEmpty());
        InlineLyricPreviewModel original = model(Collections.singletonList(line), AiLyricsSettings.PREVIEW_ITEM_ORIGINAL);
        assertEquals(1, original.rows(original.at(1500)).size());
    }

    private static InlineLyricPreviewModel.PreviewEntry modelEntry(LyricsLine line) throws Exception {
        return model(Collections.singletonList(line), AiLyricsSettings.PREVIEW_ITEM_ORIGINAL).at(1500);
    }

    private static InlineLyricPreviewModel model(List<LyricsLine> lines, int items) throws Exception {
        return model(lines, settings(items, true, Collections.emptyMap()), "en");
    }

    private static InlineLyricPreviewModel model(List<LyricsLine> lines, AiLyricsSettings.Snapshot settings,
            String sourceLanguage) {
        return new InlineLyricPreviewModel(new LyricsResult(lines, "fixture", "", true),
                settings, 12000L, false, false, sourceLanguage);
    }

    private static AiLyricsSettings.Snapshot settings(int items, boolean translate,
            Map<String, AiLyricsSettings.LanguageRule> rules) throws Exception {
        Constructor<?> constructor = AiLyricsSettings.Snapshot.class.getDeclaredConstructors()[0];
        Class<?>[] types = constructor.getParameterTypes();
        Object[] arguments = new Object[types.length];
        for (int index = 0; index < types.length; index++) {
            Class<?> type = types[index];
            if (type == String.class) arguments[index] = "ko";
            else if (type == boolean.class) arguments[index] = true;
            else if (type == int.class) arguments[index] = 0;
            else if (type == float.class) arguments[index] = 0f;
            else if (Map.class.isAssignableFrom(type)) arguments[index] = Collections.emptyMap();
            else if (List.class.isAssignableFrom(type)) arguments[index] = Collections.emptyList();
        }
        arguments[13] = items;
        arguments[4] = new AiLyricsSettings.LanguageRule("default", translate, true, "ko");
        arguments[5] = rules;
        return (AiLyricsSettings.Snapshot) constructor.newInstance(arguments);
    }
}
