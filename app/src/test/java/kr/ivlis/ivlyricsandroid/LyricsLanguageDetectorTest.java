package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.text.Normalizer;
import java.util.List;

import org.junit.Test;

public class LyricsLanguageDetectorTest {
    private static final List<Sample> CORPUS = List.of(
            sample("en", """
                    I remember when the summer ended
                    you said my name like it was ours
                    and I keep it in my pocket now
                    oh oh oh, I keep it now
                    """),
            sample("ko", """
                    너의 이름을 부르면
                    내 마음이 자꾸 흔들려
                    밤이 오면 더 선명해져
                    그대로 있어줘
                    """),
            sample("ja", """
                    君の名前を呼んだら
                    心が揺れてしまうよ
                    夜が来ればもっと鮮明に
                    そのままでいて
                    """),
            sample("zh-CN", """
                    当我叫你的名字
                    我的心又开始摇晃
                    夜来了就更清楚
                    请你留在这里
                    """),
            sample("zh-TW", """
                    當我叫你的名字
                    我的心又開始搖晃
                    夜來了就更清楚
                    請你留在這裡
                    """),
            sample("es", """
                    cuando llega la noche
                    te busco en la ciudad vacía
                    no sé cómo olvidarte
                    quédate un poco más
                    """),
            sample("fr", """
                    quand la nuit tombe enfin
                    je te cherche dans les rues vides
                    je ne sais pas t'oublier
                    reste encore un peu
                    """),
            sample("de", """
                    wenn die Nacht endlich fällt
                    suche ich dich in leeren Straßen
                    ich weiß nicht wie ich dich vergesse
                    bleib noch ein bisschen hier
                    """),
            sample("pt", """
                    quando a noite finalmente cai
                    procuro você nas ruas vazias
                    não sei como te esquecer
                    fica mais um pouco
                    """),
            sample("it", """
                    quando la notte finalmente scende
                    ti cerco nelle strade vuote
                    non so più come dimenticarti
                    resta ancora un po' con me perché
                    è così che finisce sempre
                    """),
            sample("ru", """
                    когда наступает ночь
                    я ищу тебя на пустых улицах
                    я не знаю как забыть тебя
                    останься еще немного
                    """),
            sample("ar", """
                    عندما يأتي الليل
                    أبحث عنك في الشوارع الفارغة
                    لا أعرف كيف أنساك
                    ابق قليلا بعد
                    """),
            sample("th", """
                    เมื่อค่ำคืนมาถึง
                    ฉันตามหาเธอบนถนนที่ว่างเปล่า
                    ฉันไม่รู้ว่าจะลืมเธอยังไง
                    อยู่ต่ออีกสักหน่อย
                    """),
            sample("hi", """
                    जब रात आती है
                    मैं तुम्हें खाली सड़कों पर ढूंढता हूँ
                    मुझे नहीं पता तुम्हें कैसे भूलूँ
                    थोड़ा और रुक जाओ
                    """),
            sample("vi", """
                    khi màn đêm buông xuống
                    anh tìm em trên những con phố vắng
                    anh không biết làm sao quên em
                    ở lại thêm một chút nữa
                    """),
            sample("tr", """
                    gece sonunda çöktüğünde
                    seni boş sokaklarda arıyorum
                    seni nasıl unutacağımı bilmiyorum
                    biraz daha kal yanımda
                    """),
            sample("sv", """
                    när natten äntligen faller
                    söker jag dig på tomma gator
                    jag vet inte hur jag glömmer dig
                    stanna kvar en liten stund
                    """),
            sample("pl", """
                    kiedy noc wreszcie zapada
                    szukam cię na pustych ulicach
                    nie wiem jak cię zapomnieć
                    zostań jeszcze chwilę
                    """),
            sample("cs", """
                    když konečně padne noc
                    hledám tě v prázdných ulicích
                    nevím jak na tebe zapomenout
                    zůstaň ještě chvíli
                    """),
            sample("nl", """
                    als de nacht eindelijk valt
                    zoek ik je in lege straten
                    ik weet niet hoe ik je vergeet
                    blijf nog even hier
                    """),
            sample("id", """
                    ketika malam akhirnya tiba
                    aku ingin mencarimu di jalan yang kosong
                    aku tidak bisa melupakanmu
                    karena cinta ini masih ada
                    """),
            sample("ms", """
                    apabila malam akhirnya tiba
                    aku mencari awak di jalan yang kosong
                    aku tidak tahu cara melupakan awak
                    tinggallah sebentar lagi
                    """),
            sample("fa", """
                    وقتی شب می‌رسد
                    تو را در خیابان‌های خالی می‌جویم
                    نمی‌دانم چگونه فراموشت کنم
                    کمی بیشتر بمان
                    """),
            sample("bn", """
                    যখন রাত নেমে আসে
                    আমি তোমাকে খালি রাস্তায় খুঁজি
                    আমি জানি না কীভাবে তোমাকে ভুলব
                    আরও কিছুক্ষণ থাকো
                    """)
    );

    @Test
    public void detectsSupportedLanguagesFromLyricLengthInput() {
        for (Sample sample : CORPUS) {
            assertEquals(
                    "corpus " + sample.expected,
                    sample.expected,
                    LyricsLanguageDetector.detect(sample.text)
            );
        }
    }

    @Test
    public void foreignHookDoesNotCaptureLongerLyrics() {
        assertEquals("en", LyricsLanguageDetector.detect("""
                I remember when the summer ended
                you said my name like it was ours
                and I keep it in my pocket now
                oh oh oh, I keep it now
                যখন রাত নেমে আসে
                """));
        assertEquals("en", LyricsLanguageDetector.detect("""
                I remember when the summer ended
                you said my name like it was ours
                and I keep it in my pocket now
                I keep it, I keep it now
                사랑해
                and I keep it now
                """));
    }

    @Test
    public void kpopCanRemainKoreanWithMoreEnglishLines() {
        assertEquals("ko", LyricsLanguageDetector.detect("""
                너의 이름을 부르면
                I remember when the summer ended
                내 마음이 자꾸 흔들려
                you said my name like it was ours
                밤이 오면 더 선명해져
                and I keep it in my pocket now
                그대로 있어줘
                oh oh oh, I keep it now
                """));
    }

    @Test
    public void oneOrTwoForeignLinesDoNotCaptureLyricsOverTwentyLines() {
        StringBuilder lyrics = new StringBuilder();
        for (int i = 0; i < 22; i++) {
            lyrics.append("I remember you and my heart in the night\n");
        }
        lyrics.append("사랑해\n");
        lyrics.append("너를 기다려\n");
        assertEquals("en", LyricsLanguageDetector.detect(lyrics.toString()));
    }

    @Test
    public void majorityLatinLanguageSurvivesAnotherLanguageHook() {
        assertEquals("fr", LyricsLanguageDetector.detect("""
                quand la nuit tombe enfin
                je te cherche dans les rues vides
                je ne sais pas t'oublier
                reste encore un peu
                wenn die Nacht endlich fällt
                """));
    }

    @Test
    public void traditionalChineseUsesOnlyDistinguishingCharacters() {
        assertEquals("zh-TW", LyricsLanguageDetector.detect("聽見你的聲音"));
    }

    @Test
    public void shortFragmentsStillResolve() {
        assertEquals("ko", LyricsLanguageDetector.detect("사랑해"));
        assertEquals("ja", LyricsLanguageDetector.detect("愛してる"));
        assertEquals("es", LyricsLanguageDetector.detect("te quiero"));
        assertEquals("en", LyricsLanguageDetector.detect("oh oh oh\nyeah"));
    }

    @Test
    public void noEvidenceReturnsNull() {
        assertNull(LyricsLanguageDetector.detect(null));
        assertNull(LyricsLanguageDetector.detect(""));
        assertNull(LyricsLanguageDetector.detect("   "));
        assertNull(LyricsLanguageDetector.detect("♪ ♪ ♪"));
    }

    @Test
    public void romanizedLyricsStayLatin() {
        assertEquals("en", LyricsLanguageDetector.detect("""
                kimi no namae wo yondara
                kokoro ga yurete shimau yo
                yoru ga kureba motto senmei ni
                """));
    }

    @Test
    public void normalizationDoesNotChangeTheVerdict() {
        String source = """
                cuando llega la noche
                te busco en la ciudad vacía
                no sé cómo olvidarte
                quédate un poco más
                """;
        assertEquals(
                LyricsLanguageDetector.detect(Normalizer.normalize(source, Normalizer.Form.NFC)),
                LyricsLanguageDetector.detect(Normalizer.normalize(source, Normalizer.Form.NFD))
        );
    }

    private static Sample sample(String expected, String text) {
        return new Sample(expected, text);
    }

    private static final class Sample {
        final String expected;
        final String text;

        Sample(String expected, String text) {
            this.expected = expected;
            this.text = text;
        }
    }
}
