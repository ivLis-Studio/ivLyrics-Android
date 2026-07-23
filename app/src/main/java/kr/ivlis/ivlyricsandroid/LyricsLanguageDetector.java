package kr.ivlis.ivlyricsandroid;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LyricsLanguageDetector {
    private static final double NON_LATIN_LINE_SHARE = 0.15;
    private static final int NON_LATIN_MIN_LINES = 3;
    private static final Pattern LATIN_WORD_PATTERN =
            Pattern.compile("\\p{IsLatin}+(?:['’]\\p{IsLatin}+)?");

    private static final String SIMPLIFIED_HINTS =
            "这为国们会来时说对过还后个无爱声体见长门马鸟鱼龙云当开摇请听";
    private static final String TRADITIONAL_HINTS =
            "這為國們會來時說對過還後個無愛聲體見長門馬鳥魚龍雲當開搖請聽";
    private static final String PERSIAN_UNIQUE = "پچژگکی";

    private static final String VIETNAMESE =
            "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";
    private static final String VIETNAMESE_UNIQUE =
            "đươăạảắằẳẵặấầẩẫậếềểễệịỉĩọỏộốồổỗớờởỡợụủứừửữựỳỵỷỹ";
    private static final String CZECH = "áčďéěíňóřšťúůýž";
    private static final String CZECH_UNIQUE = "ěřů";
    private static final String TURKISH = "çğıöşü";
    private static final String TURKISH_UNIQUE = "ğıış";
    private static final String SWEDISH = "åäö";
    private static final String SWEDISH_UNIQUE = "å";
    private static final String GERMAN = "äöüß";
    private static final String GERMAN_UNIQUE = "üß";
    private static final String SPANISH = "áéíóúüñ¿¡";
    private static final String FRENCH = "àâæçéèêëïîôùûüÿœ";
    private static final String FRENCH_UNIQUE = "æœçëïÿ";
    private static final String PORTUGUESE = "ãõáàâéêíóôúüç";
    private static final String POLISH = "ąćęłńóśźż";

    private static final List<LanguageHints> LATIN_HINTS = List.of(
            hints(
                    "de",
                    "ich du nicht kein keine der die das den dem ein eine einen einem bin bist ist sind war waren werde wird werden mein meine dein deine mir dir mich dich für über schön liebe nacht herz",
                    "und oder aber mit auf im in zu zum zur nur noch schon wie was wenn dann doch alles immer"
            ),
            hints(
                    "en",
                    "i you the and that with not for this your my me we are am is be was were have has do does don't can't love night heart",
                    "to in on of it all so no yes but if when now here there"
            ),
            hints(
                    "fr",
                    "je tu nous vous pas ne est suis es sommes avec pour dans mon ma mes ton ta tes que qui sur plus amour coeur",
                    "le la les un une des du de et ou mais ce ces en"
            ),
            hints(
                    "es",
                    "yo tú tu usted nosotros vosotros soy eres estoy estás no con para por mi mis tus quiero amor corazón",
                    "el la los las un una de y o pero que en es como"
            ),
            hints(
                    "it",
                    "io tu noi voi sono sei non con per mio mia tuo tua amore cuore notte",
                    "il lo la gli le un una di e o ma che in come"
            ),
            hints(
                    "pt",
                    "eu você voce nós nos sou és esta está não nao com para por meu minha teu tua amor coração coracao",
                    "o a os as um uma de e ou mas que em como"
            ),
            hints(
                    "sv",
                    "jag du vi ni inte är var med för min mitt din ditt kärlek hjärta natt",
                    "och eller men det den en ett i på som om allt"
            ),
            hints(
                    "tr",
                    "ben sen biz siz değil degil için icin çok cok gibi beni seni aşk ask kalp gece",
                    "ve bir bu o da de mi ne ile ama her"
            ),
            hints(
                    "cs",
                    "já ty jsme jste není nejsem jsem jsi můj moje tvůj tvoje láska srdce noc tebe tobě chci mám",
                    "a ale nebo že se si do na pro s z když jen už jak"
            ),
            hints(
                    "pl",
                    "ja ty my wy nie jest są sa dla przez mój moj moja twój twoj twoja miłość milosc serce noc",
                    "i lub ale to ten ta te w na z do jak"
            ),
            hints(
                    "nl",
                    "ik jij je wij niet ben bent is zijn met voor mijn jouw liefde hart nacht",
                    "de het een en of maar dat dit in op als"
            ),
            hints(
                    "id",
                    "aku kamu kau tidak tak bisa ingin karena denganmu bersamamu dirimu cinta hati hatiku rindu malam sendiri selalu pernah",
                    "yang dan di ke dari untuk dengan ini itu ada akan bukan hanya jangan semua tanpa membuat percaya"
            ),
            hints(
                    "ms",
                    "aku saya awak kau tidak tak mahu boleh kerana denganmu bersamamu dirimu cinta hati hatiku rindu malam sendiri selalu pernah",
                    "yang dan di ke dari untuk dengan ini itu ada akan bukan hanya jangan semua tanpa percaya"
            ),
            hints(
                    "vi",
                    "anh em tôi không của yêu đêm một những người biết quên được thương nhớ lòng đời mãi",
                    "và cho với này khi rồi vẫn chỉ đã sẽ lại thêm"
            )
    );

    private static final Map<String, String> DIACRITIC_BEARING = Map.ofEntries(
            Map.entry("es", "áéíóúüñ¿¡"),
            Map.entry("cs", CZECH),
            Map.entry("fr", FRENCH),
            Map.entry("it", "àèéìòù"),
            Map.entry("pl", POLISH),
            Map.entry("pt", PORTUGUESE),
            Map.entry("tr", TURKISH),
            Map.entry("de", GERMAN),
            Map.entry("sv", SWEDISH),
            Map.entry("vi", "đươăạảấầẩẫậắằẳẵặếềểễệốồổỗộớờởỡợụủứừửữựỳỵỷỹ")
    );

    private LyricsLanguageDetector() {}

    static String detect(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        LinkedHashMap<Script, Integer> lineVotes = new LinkedHashMap<>();
        for (String line : normalized.split("\\R", -1)) {
            Script script = scriptOfLine(line);
            if (script != null) {
                lineVotes.put(script, lineVotes.getOrDefault(script, 0) + 1);
            }
        }

        int totalVotes = 0;
        Script dominantNonLatin = null;
        int dominantVotes = 0;
        for (Map.Entry<Script, Integer> entry : lineVotes.entrySet()) {
            totalVotes += entry.getValue();
            if (entry.getKey() != Script.LATIN && entry.getValue() > dominantVotes) {
                dominantNonLatin = entry.getKey();
                dominantVotes = entry.getValue();
            }
        }

        boolean hookFloorApplies = totalVotes >= 4;
        if (dominantNonLatin != null
                && (!hookFloorApplies || dominantVotes >= NON_LATIN_MIN_LINES)
                && ((double) dominantVotes / Math.max(1, totalVotes)) >= NON_LATIN_LINE_SHARE) {
            return resolveNonLatin(dominantNonLatin, normalized);
        }

        String latinLanguage = detectLatinLanguage(normalized);
        if (latinLanguage != null) {
            return latinLanguage;
        }

        return countLatin(normalized) > 2 ? "en" : null;
    }

    private static Script scriptOfLine(String line) {
        int[] counts = new int[Script.values().length];
        for (int offset = 0; offset < line.length(); ) {
            int cp = line.codePointAt(offset);
            offset += Character.charCount(cp);
            Script script = scriptOf(cp);
            if (script != null) {
                counts[script.ordinal()]++;
            }
        }

        Script best = null;
        int bestCount = 0;
        for (Script script : Script.values()) {
            int count = counts[script.ordinal()];
            if (count > bestCount) {
                best = script;
                bestCount = count;
            }
        }
        return best;
    }

    private static Script scriptOf(int cp) {
        if (cp >= 0x0980 && cp <= 0x09ff) {
            return Script.BN;
        }
        if (cp >= 0x0900 && cp <= 0x097f) {
            return Script.HI;
        }
        if (cp >= 0x0e00 && cp <= 0x0e7f) {
            return Script.TH;
        }
        if (cp >= 0x0600 && cp <= 0x06ff) {
            return Script.AR;
        }
        if (cp >= 0x0400 && cp <= 0x04ff) {
            return Script.RU;
        }

        Character.UnicodeScript unicodeScript = Character.UnicodeScript.of(cp);
        if (unicodeScript == Character.UnicodeScript.HANGUL
                || unicodeScript == Character.UnicodeScript.HIRAGANA
                || unicodeScript == Character.UnicodeScript.KATAKANA
                || unicodeScript == Character.UnicodeScript.HAN
                || (cp >= 0xff66 && cp <= 0xff9f)) {
            return Script.CJK;
        }
        if (unicodeScript == Character.UnicodeScript.LATIN && Character.isLetter(cp)) {
            return Script.LATIN;
        }
        return null;
    }

    private static String resolveNonLatin(Script script, String text) {
        switch (script) {
            case CJK:
                return resolveCjk(text);
            case AR:
                int arabic = countRange(text, 0x0600, 0x06ff);
                int persian = countCharacters(text, PERSIAN_UNIQUE);
                return arabic > 0 && ((double) persian / arabic) >= 0.07 ? "fa" : "ar";
            case RU:
                return "ru";
            case TH:
                return "th";
            case HI:
                return "hi";
            case BN:
                return "bn";
            default:
                return null;
        }
    }

    private static String resolveCjk(String text) {
        int hangul = 0;
        int kana = 0;
        List<Integer> han = new ArrayList<>();
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HANGUL) {
                hangul++;
            } else if (script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || (cp >= 0xff66 && cp <= 0xff9f)) {
                kana++;
            } else if (script == Character.UnicodeScript.HAN) {
                han.add(cp);
            }
        }

        int total = hangul + kana + han.size();
        if (total == 0) {
            return null;
        }
        if ((double) hangul / total >= 0.2) {
            return "ko";
        }

        double kanaShare = (double) kana / total;
        double hanShare = (double) han.size() / total;
        if (((kanaShare - hanShare + 1.0) / 2.0) * 100.0 >= 40.0) {
            return "ja";
        }

        int simplified = 0;
        int traditional = 0;
        for (int cp : han) {
            boolean isSimplified = SIMPLIFIED_HINTS.indexOf(cp) >= 0;
            boolean isTraditional = TRADITIONAL_HINTS.indexOf(cp) >= 0;
            if (isSimplified && !isTraditional) {
                simplified++;
            } else if (isTraditional && !isSimplified) {
                traditional++;
            }
        }
        int distinguishing = simplified + traditional;
        if (distinguishing == 0) {
            return "zh-CN";
        }
        double score = (((double) simplified / distinguishing)
                - ((double) traditional / distinguishing) + 1.0) / 2.0 * 100.0;
        return score >= 40.0 ? "zh-CN" : "zh-TW";
    }

    private static String detectLatinLanguage(String text) {
        String lower = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        List<String> words = latinWords(lower);
        if (words.isEmpty()) {
            return null;
        }

        if (words.size() < 4) {
            if (containsAnyPhrase(lower, "aku cinta kamu", "aku sayang kamu", "cinta kamu")) {
                return "id";
            }
            if (containsAnyPhrase(lower, "aku sayang awak", "saya sayang awak", "cinta awak")) {
                return "ms";
            }
        }

        LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        for (LanguageHints hints : LATIN_HINTS) {
            int score = 0;
            for (String word : words) {
                if (hints.strong.contains(word)) {
                    score += 2;
                } else if (hints.weak.contains(word)) {
                    score++;
                }
            }
            scores.put(hints.language, score);
        }

        add(scores, "de", countCharacters(lower, "ß") * 5
                + countCharacters(lower, "ä") * 3
                + countCharacters(lower, "öü"));
        add(scores, "tr", countCharacters(lower, "ğıış") * 5
                + countCharacters(lower, "ç") * 2);
        add(scores, "cs", countCharacters(lower, CZECH_UNIQUE) * 5
                + countCharacters(lower, "čďňšťž") * 2);
        add(scores, "sv", countCharacters(lower, SWEDISH_UNIQUE) * 5
                + countCharacters(lower, "äö"));

        int vietnameseSignal = countCharacters(lower, VIETNAMESE_UNIQUE);
        add(scores, "vi", vietnameseSignal * 3);
        add(scores, "fr", countCharacters(lower, FRENCH_UNIQUE) * 3
                + (vietnameseSignal > 0 ? 0 : countCharacters(lower, "êèùû")));
        add(scores, "es", countCharacters(lower, "ñ¿¡") * 5
                + countCharacters(lower, "áéíóú"));
        add(scores, "pt", countCharacters(lower, "ãõ") * 5
                + countCharacters(lower, "ç"));
        add(scores, "pl", countCharacters(lower, POLISH) * 5);

        if (containsAnyPhrase(lower, "ich bin", "du bist", "ich hab", "ich habe",
                "du hast", "wir sind", "es ist", "nicht mehr", "für dich", "mit dir")) {
            add(scores, "de", 4);
        }
        if (containsAnyPhrase(lower, "i am", "you are", "don't", "can't",
                "with you", "for you", "my heart")) {
            add(scores, "en", 4);
        }
        if (containsAnyPhrase(lower, "je suis", "tu es", "avec toi",
                "mon coeur", "mon cœur", "pour toi")) {
            add(scores, "fr", 4);
        }
        if (containsAnyPhrase(lower, "yo soy", "estoy aquí", "estoy aqui",
                "contigo", "mi corazón", "mi corazon")) {
            add(scores, "es", 4);
        }
        if (containsAnyPhrase(lower, "aku ingin", "aku bisa", "aku tak bisa",
                "aku tidak bisa", "kau dan aku", "karena aku", "karena kamu",
                "bersamamu", "denganmu", "cinta ini")) {
            add(scores, "id", 4);
        }
        if (containsAnyPhrase(lower, "aku mahu", "aku boleh", "kau dan aku",
                "kerana aku", "kerana awak", "kerana kau", "bersamamu",
                "denganmu", "cinta ini")) {
            add(scores, "ms", 4);
        }

        int nonAscii = 0;
        for (int offset = 0; offset < lower.length(); ) {
            int cp = lower.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp > 0x7f) {
                nonAscii++;
            }
        }
        add(scores, "en", -Math.min(nonAscii, 8));

        int leading = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double density = (double) leading / Math.max(1, words.size());
        if (words.size() >= 8 && density < 0.2) {
            for (Map.Entry<String, String> entry : DIACRITIC_BEARING.entrySet()) {
                int score = scores.getOrDefault(entry.getKey(), 0);
                if (score > 0 && countCharacters(lower, entry.getValue()) == 0) {
                    scores.put(entry.getKey(), score / 2);
                }
            }
        }

        String bestLanguage = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestLanguage = entry.getKey();
                bestScore = entry.getValue();
            }
        }
        int minScore = words.size() < 4 ? 2 : (words.size() < 8 ? 4 : 5);
        if (bestScore >= minScore) {
            return bestLanguage;
        }

        return detectByDiacritics(lower);
    }

    private static String detectByDiacritics(String text) {
        LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        scores.put("vi", countCharacters(text, VIETNAMESE_UNIQUE) * 3
                + countCharacters(text, VIETNAMESE));
        scores.put("cs", countCharacters(text, CZECH_UNIQUE) * 3
                + countCharacters(text, CZECH));
        scores.put("tr", countCharacters(text, TURKISH_UNIQUE) * 3
                + countCharacters(text, TURKISH));
        scores.put("sv", countCharacters(text, SWEDISH_UNIQUE) * 3
                + countCharacters(text, SWEDISH));
        scores.put("de", countCharacters(text, GERMAN_UNIQUE) * 3
                + countCharacters(text, GERMAN));
        scores.put("fr", countCharacters(text, FRENCH_UNIQUE) * 3
                + countCharacters(text, FRENCH));
        scores.put("pl", countCharacters(text, POLISH) * 2);
        scores.put("pt", countCharacters(text, PORTUGUESE));
        scores.put("es", countCharacters(text, SPANISH));

        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                best = entry.getKey();
                bestScore = entry.getValue();
            }
        }
        return bestScore >= 4 ? best : null;
    }

    private static List<String> latinWords(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = LATIN_WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    private static int countLatin(String text) {
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN
                    && Character.isLetter(cp)) {
                count++;
            }
        }
        return count;
    }

    private static int countRange(String text, int start, int end) {
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp >= start && cp <= end) {
                count++;
            }
        }
        return count;
    }

    private static int countCharacters(String text, String candidates) {
        int count = 0;
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (candidates.indexOf(cp) >= 0) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsAnyPhrase(String text, String... phrases) {
        String searchable = " " + text.replaceAll("[^\\p{L}'’]+", " ").trim() + " ";
        for (String phrase : phrases) {
            if (searchable.contains(" " + phrase + " ")) {
                return true;
            }
        }
        return false;
    }

    private static void add(Map<String, Integer> scores, String language, int delta) {
        scores.put(language, scores.getOrDefault(language, 0) + delta);
    }

    private static LanguageHints hints(String language, String strong, String weak) {
        return new LanguageHints(language, words(strong), words(weak));
    }

    private static Set<String> words(String text) {
        Set<String> result = new HashSet<>();
        for (String word : text.split(" ")) {
            if (!word.isEmpty()) {
                result.add(word);
            }
        }
        return result;
    }

    private enum Script {
        LATIN,
        CJK,
        RU,
        AR,
        TH,
        HI,
        BN
    }

    private static final class LanguageHints {
        final String language;
        final Set<String> strong;
        final Set<String> weak;

        LanguageHints(String language, Set<String> strong, Set<String> weak) {
            this.language = language;
            this.strong = strong;
            this.weak = weak;
        }
    }
}
