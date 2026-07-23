package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;

import org.junit.Test;

public class CulturalAnnotationTest {
    @Test
    public void numberingRestartsForEachLyricLine() {
        List<CulturalAnnotation> annotations = List.of(
                new CulturalAnnotation(0, "缶蹴り", "깡통을 이용한 일본의 술래잡기다."),
                new CulturalAnnotation(0, "ケイドロ", "경찰과 도둑 편으로 나뉘는 일본의 놀이다."),
                new CulturalAnnotation(1, "夕焼け小焼け", "일부 지역의 저녁 귀가 방송에 쓰이는 일본 동요다.")
        );

        List<CulturalAnnotation> firstLine = CulturalAnnotation.forLine(
                annotations,
                0,
                "缶蹴り ケイドロ"
        );
        List<CulturalAnnotation> secondLine = CulturalAnnotation.forLine(
                annotations,
                1,
                "夕焼け小焼けが流れる"
        );

        assertEquals("缶蹴り[1] ケイドロ[2]", CulturalAnnotation.annotateText("缶蹴り ケイドロ", firstLine));
        assertEquals("夕焼け小焼け[1]が流れる", CulturalAnnotation.annotateText("夕焼け小焼けが流れる", secondLine));
    }

    @Test
    public void markerKeepsOriginalSyllableTiming() {
        List<LyricsLine.Syllable> syllables = List.of(
                new LyricsLine.Syllable("夕焼け", 100L, 500L),
                new LyricsLine.Syllable("小焼け", 500L, 900L)
        );
        List<CulturalAnnotation> annotations = List.of(
                new CulturalAnnotation(0, "夕焼け小焼け", "저녁 귀가 방송에 쓰이는 일본 동요다.")
        );

        List<LyricsLine.Syllable> result = CulturalAnnotation.annotateSyllables(
                "夕焼け小焼け",
                syllables,
                annotations
        );

        assertEquals("夕焼け", result.get(0).text);
        assertEquals("小焼け[1]", result.get(1).text);
        assertEquals(500L, result.get(1).startTimeMs);
        assertEquals(900L, result.get(1).endTimeMs);
    }

    @Test
    public void noteIsReducedToOneShortSentence() {
        assertEquals(
                "일본의 어린이 놀이다.",
                CulturalAnnotation.compactNote("  일본의 어린이 놀이다.  두 번째 문장은 제거한다. ")
        );
    }

    @Test
    public void culturalSettingsAreTranslatedForEveryUiLanguage() {
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            String loading = AppI18n.t(language.code, "loading.cultural_annotations");
            String description = AppI18n.t(language.code, "setting.cultural_annotations_desc");
            assertFalse(language.code, loading.isBlank());
            assertFalse(language.code, loading.equals("loading.cultural_annotations"));
            assertFalse(language.code, description.equals("setting.cultural_annotations_desc"));
        }
    }
}
