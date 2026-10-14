package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalAiTranslatorTest {
    private static final String BLOCK_MARKER = "retain the exact same number of delimiters";

    private static String sourceOf(String prompt) {
        return prompt.substring(prompt.lastIndexOf("\n\n") + 2);
    }

    @Test public void targetLanguagesMapToTheModelsEnglishNames() {
        assertEquals("Korean", LocalAiTranslator.languageName("ko"));
        assertEquals("Japanese", LocalAiTranslator.languageName("ja"));
        assertEquals("English", LocalAiTranslator.languageName(null));
        for (String code : new String[]{"zh", "zh-CN", "zh_Hans", "zh-SG"}) {
            assertEquals(code, "Chinese", LocalAiTranslator.languageName(code));
        }
        for (String code : new String[]{"zh-TW", "zh-HK", "zh-Hant", "ZH_tw"}) {
            assertEquals(code, "Traditional Chinese", LocalAiTranslator.languageName(code));
        }
        assertEquals("Portuguese", LocalAiTranslator.languageName("pt-BR"));
        assertEquals("Filipino", LocalAiTranslator.languageName("fil"));
        assertEquals("Filipino", LocalAiTranslator.languageName("tl"));
        assertEquals("Cantonese", LocalAiTranslator.languageName("yue"));
        assertEquals("Persian", LocalAiTranslator.languageName("fa"));
        for (String code : new String[]{"sv", "sv-SE", "no", "xx"}) {
            assertNull(code, LocalAiTranslator.languageName(code));
        }
    }

    @Test public void blocksHoldEightPartsAndFourHundredEightyCharacters() {
        List<String> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) nine.add("p" + i);
        assertEquals(Arrays.asList(8, 1), sizes(LocalAiTranslator.buildBlocks(nine)));
        String hundred = repeat('a', 100);
        assertEquals(Arrays.asList(4, 1), sizes(LocalAiTranslator.buildBlocks(Collections.nCopies(5, hundred))));
        assertEquals(Arrays.asList(2), sizes(LocalAiTranslator.buildBlocks(Arrays.asList(repeat('b', 239), repeat('c', 240)))));
        assertEquals(Arrays.asList(1, 1), sizes(LocalAiTranslator.buildBlocks(Arrays.asList(repeat('d', 600), "e"))));
    }

    @Test public void planKeepsBlanksMarkersAndVocalPartsAndTranslatesRepeatsOnce() {
        LocalAiTranslator.Plan plan = new LocalAiTranslator.Plan(Arrays.asList(
                "君の声", "", "[Instrumental]", "夜明け / まで", " 君の声 ", "\u3000", " [Chorus] ", "君の声 / [Ad-lib]"));
        assertEquals(Arrays.asList("君の声", "夜明け", "まで"), plan.pendingParts());
        assertEquals(Arrays.asList(1, 2, 5, 6), plan.takeReadyRows());
        plan.put(Arrays.asList("君の声", "夜明け", "まで"), Arrays.asList("KO1", "KO2", "KO3"));
        assertEquals(Arrays.asList(0, 3, 4, 7), plan.takeReadyRows());
        assertEquals(Collections.emptyList(), plan.takeReadyRows());
        assertEquals(Arrays.asList("KO1", "", "[Instrumental]", "KO2 / KO3", "KO1", "", " [Chorus] ", "KO1 / [Ad-lib]"),
                plan.texts());
    }

    @Test public void promptsAndBudgetsMatchTheDesktopProvider() {
        assertEquals("Please accurately translate the following text into Korean.\n"
                + "You must retain the exact same number of delimiters in the translation. Strictly do not omit, escape, "
                + "or translate these symbols, and pay close attention to their placement.\n\n一\n二",
                LocalAiTranslator.blockPrompt(Arrays.asList("一", "二"), "Korean"));
        assertEquals("Translate the following text into Korean. Note that you should only output the translated "
                + "result without any additional explanation:\n\n一", LocalAiTranslator.linePrompt("一", "Korean"));
        assertEquals(57, LocalAiTranslator.outputBudget("abc"));
        assertEquals(1536, LocalAiTranslator.outputBudget(repeat('x', 600)));
    }

    @Test public void aBlockReplyIsAcceptedOnlyWhenEveryLineSurvives() throws Exception {
        List<String> prompts = new ArrayList<>();
        List<Integer> budgets = new ArrayList<>();
        LocalAiTranslator.Engine echo = (prompt, maxTokens) -> {
            prompts.add(prompt);
            budgets.add(maxTokens);
            StringBuilder reply = new StringBuilder();
            for (String line : sourceOf(prompt).split("\n")) reply.append(reply.length() > 0 ? "\n" : "").append(" EN:").append(line);
            return new LocalAiTranslator.Completion(reply + "\n", false);
        };
        assertEquals(Arrays.asList("EN:一行目", "EN:二行目"),
                LocalAiTranslator.translateBlock(echo, Arrays.asList("一行目", "二行目"), "English"));
        assertEquals(1, prompts.size());
        assertTrue(prompts.get(0).contains(BLOCK_MARKER));
        assertEquals(Collections.singletonList(48 + 3 * 7), budgets);

        for (LocalAiTranslator.Completion broken : Arrays.asList(
                new LocalAiTranslator.Completion("merged line only", false),
                new LocalAiTranslator.Completion("EN:一行目\n \nEN:二行目", false),
                new LocalAiTranslator.Completion("EN:一行目\n", false),
                new LocalAiTranslator.Completion("EN:一行目\nEN:二行目", true))) {
            prompts.clear();
            LocalAiTranslator.Engine engine = (prompt, maxTokens) -> {
                prompts.add(prompt);
                return prompt.contains(BLOCK_MARKER) ? broken : new LocalAiTranslator.Completion("EN:" + sourceOf(prompt), false);
            };
            assertEquals(Arrays.asList("EN:一行目", "EN:二行目"),
                    LocalAiTranslator.translateBlock(engine, Arrays.asList("一行目", "二行目"), "English"));
            assertEquals("one block prompt, then one prompt per part", 3, prompts.size());
        }
    }

    @Test public void aSinglePartReplyIsCollapsedAndMustNotBeEmpty() throws Exception {
        LocalAiTranslator.Engine multiline = (prompt, maxTokens) -> {
            assertTrue(!prompt.contains(BLOCK_MARKER));
            return new LocalAiTranslator.Completion("  first\r\n\n second \n", false);
        };
        assertEquals(Collections.singletonList("first second"),
                LocalAiTranslator.translateBlock(multiline, Collections.singletonList("一"), "English"));
        try {
            LocalAiTranslator.translateBlock((prompt, maxTokens) -> new LocalAiTranslator.Completion(" \n ", false),
                    Collections.singletonList("一"), "English");
            fail("An empty reply must fail");
        } catch (IOException expected) {
            assertEquals("[Local AI] The model returned an empty line.", expected.getMessage());
        }
    }

    private static List<Integer> sizes(List<List<String>> blocks) {
        List<Integer> sizes = new ArrayList<>();
        for (List<String> block : blocks) sizes.add(block.size());
        return sizes;
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
