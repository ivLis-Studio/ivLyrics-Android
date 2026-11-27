package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** The page contract of PronunciationLabPage.html (pronunciationLabMobileBridge.mjs), without a WebView. */
public final class PronunciationLabProtocolTest {
    private static PronunciationLabProtocol.Request request(String... lines) {
        return new PronunciationLabProtocol.Request(Arrays.asList(lines), "ja", "ko", "translation");
    }

    @Test public void readyResultAndErrorMessagesParse() {
        assertEquals(PronunciationLabProtocol.Kind.READY, PronunciationLabProtocol.parse("{\"type\":\"ready\"}").kind);

        PronunciationLabProtocol.Message result = PronunciationLabProtocol.parse(
                "{\"type\":\"result\",\"id\":\"p7\",\"lines\":[\"유메나라바 도레호도 요캇타데쇼\",\"\",\"♪\"],\"version\":\"1.0.2\"}");
        assertEquals(PronunciationLabProtocol.Kind.RESULT, result.kind);
        assertEquals("p7", result.id);
        assertEquals(Arrays.asList("유메나라바 도레호도 요캇타데쇼", "", "♪"), result.lines);
        assertEquals("1.0.2", result.version);

        PronunciationLabProtocol.Message error = PronunciationLabProtocol.parse(
                "{\"type\":\"error\",\"id\":\"p8\",\"code\":\"PRONUNCIATION_LAB_UNSUPPORTED_SOURCE\","
                        + "\"message\":\"[Pronunciation Lab] Cannot read lyrics in language ru\"}");
        assertEquals(PronunciationLabProtocol.Kind.ERROR, error.kind);
        assertEquals("p8", error.id);
        assertEquals(PronunciationLabProtocol.UNSUPPORTED_SOURCE, error.code);
        assertEquals("[Pronunciation Lab] Cannot read lyrics in language ru", error.text);
    }

    @Test public void lineAndUnknownMessagesCarryNoAnswer() {
        assertEquals(PronunciationLabProtocol.Kind.LINE,
                PronunciationLabProtocol.parse("{\"type\":\"line\",\"id\":\"p1\",\"index\":0,\"text\":\"네코\"}").kind);
        assertEquals(PronunciationLabProtocol.Kind.UNKNOWN,
                PronunciationLabProtocol.parse("{\"type\":\"progress\",\"id\":\"p1\"}").kind);
        assertEquals(PronunciationLabProtocol.Kind.UNKNOWN, PronunciationLabProtocol.parse("{}").kind);
    }

    @Test public void unexpectedErrorCodesBecomeFailed() {
        PronunciationLabProtocol.Message error = PronunciationLabProtocol.parse(
                "{\"type\":\"error\",\"id\":\"p1\",\"code\":\"TypeError\"}");
        assertEquals(PronunciationLabProtocol.FAILED, error.code);
        assertEquals("", error.text);
        assertEquals(PronunciationLabProtocol.FAILED,
                PronunciationLabProtocol.parse("{\"type\":\"error\",\"id\":\"p1\",\"code\":7}").code);
    }

    @Test public void resultLinesKeepEveryRowAndCoerceOddValues() {
        PronunciationLabProtocol.Message result = PronunciationLabProtocol.parse(
                "{\"type\":\"result\",\"id\":\"p1\",\"lines\":[null,\"a\",3]}");
        assertEquals(Arrays.asList("", "a", "3.0"), result.lines);
        assertEquals("", result.version);
        assertEquals(PronunciationLabProtocol.Kind.RESULT,
                PronunciationLabProtocol.parse("{\"type\":\"result\",\"id\":\"p1\",\"lines\":[],\"version\":null}").kind);
    }

    @Test public void escapesAndUnicodeDecode() {
        PronunciationLabProtocol.Message result = PronunciationLabProtocol.parse(
                " {\"type\" : \"result\", \"id\":\"p\\u0031\",\n\"lines\":[\"a\\\"b\\\\c\\/d\\n\\t\\r\\b\\f\","
                        + "\"\\ub124\\ucf54\",\"\\ud83c\\udfb5 \uD83C\uDFB6\",\"夢ならば\"]} ");
        assertEquals("p1", result.id);
        assertEquals("a\"b\\c/d\n\t\r\b\f", result.lines.get(0));
        assertEquals("네코", result.lines.get(1));
        assertEquals("\uD83C\uDFB5 \uD83C\uDFB6", result.lines.get(2));
        assertEquals("夢ならば", result.lines.get(3));
    }

    @Test public void malformedInputIsRejected() {
        String deep = String.join("", Collections.nCopies(40, "[")) + String.join("", Collections.nCopies(40, "]"));
        for (String input : new String[]{null, "", "   ", "ready", "[]", "\"ready\"", "{\"type\":\"ready\"",
                "{\"type\":\"ready\"} x", "{type:\"ready\"}", "{\"type\":'ready'}", "{\"type\":\"ready\",}",
                "{\"type\":\"result\",\"id\":\"p1\"}", "{\"type\":\"result\",\"lines\":[]}",
                "{\"type\":\"result\",\"id\":\"p1\",\"lines\":\"a\"}", "{\"type\":\"error\",\"code\":\"x\"}",
                "{\"type\":\"result\",\"id\":\"p1\",\"lines\":[\"\\x\"]}", "{\"type\":\"result\",\"id\":\"p1\",\"lines\":[\"\\u12\"]}",
                "{\"type\":\"result\",\"id\":\"p1\",\"lines\":[\"\\u+123\"]}", "{\"type\":\"ready\",\"n\":01.}",
                "{\"type\":\"ready\",\"n\":-}", "{\"type\":\"ready\",\"n\":1e}", "{\"a\":" + deep + "}",
                "{\"type\":\"result\",\"id\":\"p1\",\"lines\":[\"raw\ncontrol\"]}", "{\"type\":tru}"}) {
            assertEquals(String.valueOf(input), PronunciationLabProtocol.Kind.MALFORMED, PronunciationLabProtocol.parse(input).kind);
        }
        assertEquals(PronunciationLabProtocol.Kind.READY,
                PronunciationLabProtocol.parse("{\"type\":\"ready\",\"n\":-1.5e+3,\"ok\":true,\"no\":false,\"z\":null,\"o\":{\"a\":[1,{}]}}").kind);
    }

    @Test public void requestScriptsFollowTheBridgeContract() {
        PronunciationLabProtocol.Request request = request("猫", "");
        assertEquals("{\"lines\":[\"猫\",\"\"],\"sourceLang\":\"ja\",\"lang\":\"ko\",\"notation\":\"translation\"}",
                PronunciationLabProtocol.requestJson(request));
        assertEquals("window.ivLyricsPronunciationLab.convert(\"p1\","
                        + "\"{\\\"lines\\\":[\\\"猫\\\",\\\"\\\"],\\\"sourceLang\\\":\\\"ja\\\",\\\"lang\\\":\\\"ko\\\","
                        + "\\\"notation\\\":\\\"translation\\\"}\");void 0;",
                PronunciationLabProtocol.convertScript("p1", request));
        assertEquals("window.ivLyricsPronunciationLab&&window.ivLyricsPronunciationLab.cancel(\"p1\");void 0;",
                PronunciationLabProtocol.cancelScript("p1"));
    }

    @Test public void quotedLyricsSurviveAsJavaScriptAndJson() {
        String lyric = "a\"b\\c\n\r\t\u0001</script>\u2028\u2029 夢 \uD83C\uDFB5 lone\uD800 low\uDC00 end";
        String quoted = PronunciationLabProtocol.quote(lyric);
        for (char forbidden : new char[]{'\n', '\r', '\t', '\u0001', '\u2028', '\u2029', '\uD800', '\uDC00'}) {
            if (forbidden == '\uD800' || forbidden == '\uDC00') {
                // Only the unpaired surrogates are escaped; the paired emoji stays as it is.
                assertTrue(quoted.contains(String.format(Locale.ROOT, "\\u%04x", (int) forbidden)));
            } else {
                assertFalse("raw " + (int) forbidden, quoted.indexOf(forbidden) >= 0);
            }
        }
        assertTrue(quoted.contains("\uD83C\uDFB5"));
        PronunciationLabProtocol.Message echoed = PronunciationLabProtocol.parse(
                "{\"type\":\"result\",\"id\":" + PronunciationLabProtocol.quote("p\"1") + ",\"lines\":[" + quoted + "]}");
        assertEquals("p\"1", echoed.id);
        assertEquals(Collections.singletonList(lyric), echoed.lines);
    }

    @Test public void requestNormalizesItsFields() {
        PronunciationLabProtocol.Request request = new PronunciationLabProtocol.Request(
                Arrays.asList("a", null), "  ", " ko ", "LATIN");
        assertEquals(Arrays.asList("a", ""), request.lines);
        assertEquals("auto", request.sourceLang);
        assertEquals("ko", request.lang);
        assertEquals("latin", request.notation);
        assertEquals(Collections.emptyList(), new PronunciationLabProtocol.Request(null, null, null, null).lines);
        assertEquals("ipa", PronunciationLabProtocol.notation(" ipa "));
        assertEquals("translation", PronunciationLabProtocol.notation(AiLyricsSettings.PRONUNCIATION_NOTATION_TRANSLATION));
        assertEquals("translation", PronunciationLabProtocol.notation("romaji"));
        assertEquals("translation", PronunciationLabProtocol.notation(null));
    }

    @Test public void failuresShowLocalizedMessagesAndKeepTheDetailForLogs() {
        PronunciationLabProtocol.Failure source = new PronunciationLabProtocol.Failure(
                PronunciationLabProtocol.UNSUPPORTED_SOURCE, "[Pronunciation Lab] Cannot read lyrics in language ru");
        assertEquals(PronunciationLabProtocol.UNSUPPORTED_SOURCE, source.code);
        assertEquals("PRONUNCIATION_LAB_UNSUPPORTED_SOURCE: [Pronunciation Lab] Cannot read lyrics in language ru",
                source.getMessage());
        assertEquals("Pronunciation Lab can't read this song's language.", source.userMessage("en"));
        assertEquals("Pronunciation Lab은 이 곡의 언어를 읽을 수 없습니다.", source.userMessage("ko"));
        assertEquals("Pronunciation Lab can't write pronunciation in this language.",
                new PronunciationLabProtocol.Failure(PronunciationLabProtocol.UNSUPPORTED_TARGET, "").userMessage("en"));
        assertEquals("Couldn't download Pronunciation Lab data. Check your connection and try again.",
                new PronunciationLabProtocol.Failure(PronunciationLabProtocol.LOAD_FAILED, "").userMessage("en"));
        for (String code : new String[]{PronunciationLabProtocol.FAILED, PronunciationLabProtocol.BAD_REQUEST,
                PronunciationLabProtocol.ABORTED, PronunciationLabProtocol.TIMEOUT, PronunciationLabProtocol.UNAVAILABLE}) {
            assertEquals("Pronunciation Lab is temporarily unavailable. Try again later.",
                    new PronunciationLabProtocol.Failure(code, "detail").userMessage("en"));
        }
        PronunciationLabProtocol.Failure unknown = new PronunciationLabProtocol.Failure(null, null);
        assertEquals(PronunciationLabProtocol.FAILED, unknown.code);
        assertEquals("PRONUNCIATION_LAB_FAILED: ", unknown.getMessage());
        // The repository shows provider failures through the keyless translators' localization.
        assertTrue(unknown instanceof KeylessTranslationProviders.ProviderException);
    }

    @Test public void everyLanguageHasTheProviderCopy() {
        String[] keys = {"provider.desc.pronunciation-lab", "error.pronunciation_lab_unsupported_source",
                "error.pronunciation_lab_unsupported_target", "error.pronunciation_lab_load_failed"};
        Pattern hangul = Pattern.compile("[\uac00-\ud7a3]");
        assertEquals(22, AppI18n.UI_LANGUAGES.size());
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (String key : keys) {
                String value = AppI18n.t(language.code, key);
                String label = language.code + ":" + key;
                assertNotEquals(label, key, value);
                assertFalse(label, value.trim().isEmpty());
                // Shown through String.format by ProviderException.userMessage.
                assertFalse(label, value.contains("%"));
                if (!key.startsWith("provider.")) assertTrue(label, value.contains("Pronunciation Lab"));
                else assertTrue(label, value.contains("pronunciation-lab") && value.contains("CDN") && value.contains("API"));
                if (!"ko".equals(language.code)) assertFalse(label, hangul.matcher(value).find());
                if (!"en".equals(language.code)) assertNotEquals(label, AppI18n.t("en", key), value);
            }
        }
        assertEquals("규칙·사전 기반 발음 전용 제공자 (AI 아님, API 키 불필요). pronunciation-lab 최신 버전을 CDN에서 불러옵니다",
                AppI18n.t("ko", "provider.desc.pronunciation-lab"));
        assertEquals("Rule- and dictionary-based pronunciation-only provider (not AI, no API key required). "
                + "Loads the latest pronunciation-lab release from a CDN", AppI18n.t("en", "provider.desc.pronunciation-lab"));
        assertEquals("ルールと辞書に基づく発音専用プロバイダー（AI ではなく、API キー不要）。pronunciation-lab の最新版を CDN から読み込みます",
                AppI18n.t("ja", "provider.desc.pronunciation-lab"));
    }

    @Test public void converterContractUsesTheRequestLines() throws Exception {
        PronunciationLabProtocol.Converter echo = request -> request.lines;
        List<String> lines = echo.convert(request("猫"));
        assertEquals(Collections.singletonList("猫"), lines);
    }
}
