package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Shared preview row policy, matching the standalone player including merged vocals and interludes. */
final class InlineLyricPreviewModel {
    private static final long PREVIEW_INTERLUDE_MIN_DURATION_MS = 500L;
    private static final long PREVIEW_TRAILING_INTERLUDE_DELAY_MS = 3_500L;
    private final LyricsResult currentLyricsResult;
    private final AiLyricsSettings.Snapshot settings;
    private final long trackDurationMs;
    private final boolean pronunciationLoading;
    private final boolean translationLoading;
    private final boolean pronunciationEnabled;
    private final boolean translationEnabled;
    private final long[] boundaries;
    private final Map<LyricsLine, List<MainLyricPreviewView.PreviewLine>> rowCache = new IdentityHashMap<>();
    private int cachedBucket = Integer.MIN_VALUE;
    private PreviewEntry cachedEntry;
    private PreviewEntry cachedRowsEntry;
    private List<MainLyricPreviewView.PreviewLine> cachedRows;

    InlineLyricPreviewModel(LyricsResult result, AiLyricsSettings.Snapshot settings, long durationMs,
            boolean pronunciationLoading, boolean translationLoading, String sourceLanguage) {
        currentLyricsResult = result;
        this.settings = settings;
        trackDurationMs = durationMs;
        this.pronunciationLoading = pronunciationLoading;
        this.translationLoading = translationLoading;
        pronunciationEnabled = settings.ruleForSource(sourceLanguage).pronunciationEnabled;
        translationEnabled = settings.ruleForSource(sourceLanguage).translationEnabled
                && !settings.shouldSkipTranslation(sourceLanguage, settings.resolveTargetLanguage(sourceLanguage));
        TreeSet<Long> changes = new TreeSet<>();
        changes.add(0L);
        changes.add(Math.max(0L, durationMs));
        for (LyricsLine line : result.lines) {
            if (line == null) continue;
            changes.add(line.startTimeMs);
            changes.add(line.endTimeMs);
            long lastEnd = previewLastLyricEndTime(line);
            if (lastEnd >= 0L) changes.add(lastEnd + PREVIEW_TRAILING_INTERLUDE_DELAY_MS);
        }
        boundaries = new long[changes.size()];
        int index = 0;
        for (Long change : changes) boundaries[index++] = change;
    }

    PreviewEntry at(long positionMs) {
        int bucket = Arrays.binarySearch(boundaries, positionMs);
        if (bucket < 0) bucket = -bucket - 2;
        if (bucket != cachedBucket) {
            cachedBucket = bucket;
            cachedEntry = previewEntryAt(positionMs);
        }
        return cachedEntry;
    }

    List<MainLyricPreviewView.PreviewLine> rows(PreviewEntry entry) {
        if (settings.previewItems == AiLyricsSettings.PREVIEW_ITEM_NONE) return Collections.emptyList();
        if (entry == null) return Collections.singletonList(
                new MainLyricPreviewView.PreviewLine(ui("status.lyrics_waiting"), true));
        if (entry.isInterlude()) return Collections.singletonList(
                MainLyricPreviewView.PreviewLine.interlude(interludePreviewLabel(entry.interludeKind)));
        if (cachedRowsEntry == entry) return cachedRows;
        List<MainLyricPreviewView.PreviewLine> rows = new ArrayList<>();
        for (LyricsLine line : entry.lines) rows.addAll(rowsForLine(line));
        cachedRowsEntry = entry;
        cachedRows = rows;
        return rows;
    }

    private List<MainLyricPreviewView.PreviewLine> rowsForLine(LyricsLine line) {
        List<MainLyricPreviewView.PreviewLine> cached = rowCache.get(line);
        if (cached != null) return cached;
        List<MainLyricPreviewView.PreviewLine> rows = new ArrayList<>();
        PreviewText original = originalPreviewText(line);
        int items = settings.previewItems;
        // Inline lyrics always retain the sung words. Supplement selection never
        // removes the source row, and enabling translation needs no preview-setting migration.
        addPreviewRow(rows, original.text, original.rubyText, original.syllables, original.kind,
                AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL);
        if (pronunciationEnabled
                && AiLyricsSettings.previewItemEnabled(items, AiLyricsSettings.PREVIEW_ITEM_PRONUNCIATION)) {
            addSupplementPreviewRow(rows, line.pronunciationText, ui("loading.pronunciation"),
                    original.text, original.rubyText, original.syllables, original.kind,
                    pronunciationLoading, AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION);
        }
        if (translationEnabled && (!line.translationText.trim().isEmpty()
                || (translationLoading && AiLyricsSettings.previewItemEnabled(items, AiLyricsSettings.PREVIEW_ITEM_TRANSLATION)))) {
            addSupplementPreviewRow(rows, line.translationText, ui("loading.translation"),
                    original.text, original.rubyText, original.syllables, original.kind,
                    translationLoading, AiLyricsSettings.TYPO_MAIN_PREVIEW_TRANSLATION);
        }
        if (rows.isEmpty()) addPreviewRow(rows, original.text, original.rubyText, original.syllables,
                original.kind, AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL);
        for (int index = 0; index < rows.size(); index++) {
            rows.set(index, rows.get(index).withPlaybackWindow(line.startTimeMs, line.endTimeMs));
        }
        rowCache.put(line, rows);
        return rows;
    }

    private String ui(String key) { return AppI18n.t(settings.uiLang, key); }

    private PreviewEntry previewEntryAt(long positionMs) {
        List<LyricsLine> lines = currentLyricsResult.lines;
        int lineCount = lines.size();
        LyricsLine firstUntimedLine = null;
        List<LyricsLine> matchingTimedLines = new ArrayList<>();
        PreviewEntry matchingMarker = null;
        LyricsLine fallbackLine = null;
        for (int index = 0; index < lineCount; index++) {
            LyricsLine line = lines.get(index);
            if (line == null) {
                continue;
            }

            boolean timed = line.isTimed();
            if (!timed) {
                if (firstUntimedLine == null
                        && !isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
                    firstUntimedLine = line;
                }
                continue;
            }

            boolean interludeMarker = isPreviewInterludeMarkerText(previewInterludeCandidateText(line));
            if (interludeMarker) {
                PreviewEntry markerEntry = markerInterludeEntry(line, index, lineCount);
                if (markerEntry != null && markerEntry.contains(positionMs)) {
                    matchingMarker = markerEntry;
                }
                continue;
            }

            if (positionMs >= line.startTimeMs && positionMs < line.endTimeMs) {
                matchingTimedLines.add(line);
            }
            if (positionMs >= line.startTimeMs) {
                fallbackLine = line;
            }
        }

        if (firstUntimedLine != null) {
            return PreviewEntry.line(firstUntimedLine);
        }

        if (!matchingTimedLines.isEmpty()) {
            return PreviewEntry.lines(matchingTimedLines);
        }
        // A marker in another stream must not hide vocals that are still singing.
        if (matchingMarker != null) return matchingMarker;

        PreviewEntry prelude = preludeEntry(positionMs);
        if (prelude != null) {
            return prelude;
        }

        PreviewEntry trailingInterlude = trailingInterludeEntry(positionMs);
        if (trailingInterlude != null) {
            return trailingInterlude;
        }

        return fallbackLine == null ? null : PreviewEntry.line(fallbackLine);
    }

    private PreviewEntry markerInterludeEntry(LyricsLine line, int lineIndex, int lineCount) {
        long endTimeMs = Math.max(line.endTimeMs, nextPreviewRenderableLineStartAfter(lineIndex));
        long durationMs = endTimeMs > line.startTimeMs ? endTimeMs - line.startTimeMs : 0L;
        long minimumDurationMs = isPreviewMusicNoteInterludeMarkerText(previewInterludeCandidateText(line))
                ? 0L
                : PREVIEW_INTERLUDE_MIN_DURATION_MS;
        if (durationMs <= minimumDurationMs) {
            return null;
        }
        return PreviewEntry.interlude(line.startTimeMs, endTimeMs, previewInstrumentalKind(lineIndex, lineCount));
    }

    private PreviewEntry preludeEntry(long positionMs) {
        int firstIndex = firstPreviewRenderableLineIndex();
        if (firstIndex < 0) {
            return null;
        }
        LyricsLine firstLine = currentLyricsResult.lines.get(firstIndex);
        if (firstLine == null || !firstLine.isTimed() || positionMs >= firstLine.startTimeMs) {
            return null;
        }
        long startTimeMs = 0L;
        long endTimeMs = firstLine.startTimeMs;
        if (endTimeMs - startTimeMs <= PREVIEW_INTERLUDE_MIN_DURATION_MS) {
            return null;
        }
        return PreviewEntry.interlude(startTimeMs, endTimeMs, "prelude");
    }

    private PreviewEntry trailingInterludeEntry(long positionMs) {
        if (!previewAutoInstrumentalBreakEnabled()) {
            return null;
        }
        List<LyricsLine> lines = currentLyricsResult.lines;
        int lineCount = lines.size();
        long latestLyricEnd = -1L;
        for (int index = 0; index < lineCount; index++) {
            LyricsLine line = lines.get(index);
            if (line == null || !line.isTimed() || isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
                continue;
            }
            latestLyricEnd = Math.max(latestLyricEnd, previewLastLyricEndTime(line));
            long lyricEndTime = latestLyricEnd;
            if (lyricEndTime < 0L) {
                continue;
            }
            long startTimeMs = lyricEndTime + PREVIEW_TRAILING_INTERLUDE_DELAY_MS;
            long nextLyricStartTime = nextPreviewRenderableLineStartAfter(index);
            long endTimeMs = nextLyricStartTime > startTimeMs
                    ? nextLyricStartTime
                    : (index >= Math.max(0, lineCount - 1) ? previewTrackDurationMs() : 0L);
            long durationMs = endTimeMs > startTimeMs ? endTimeMs - startTimeMs : 0L;
            if (durationMs <= PREVIEW_INTERLUDE_MIN_DURATION_MS) {
                continue;
            }
            if (positionMs >= startTimeMs && positionMs < endTimeMs) {
                return PreviewEntry.interlude(startTimeMs, endTimeMs, nextLyricStartTime > 0L ? "break" : "postlude");
            }
        }
        return null;
    }

    private int firstPreviewRenderableLineIndex() {
        List<LyricsLine> lines = currentLyricsResult.lines;
        for (int index = 0; index < lines.size(); index++) {
            LyricsLine line = lines.get(index);
            if (line == null || !line.isTimed()) {
                continue;
            }
            if (!isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
                return index;
            }
        }
        return -1;
    }

    private long nextPreviewRenderableLineStartAfter(int lineIndex) {
        List<LyricsLine> lines = currentLyricsResult.lines;
        for (int index = Math.max(0, lineIndex + 1); index < lines.size(); index++) {
            LyricsLine candidate = lines.get(index);
            if (candidate == null || !candidate.isTimed()) {
                continue;
            }
            if (isPreviewInterludeMarkerText(previewInterludeCandidateText(candidate))) {
                continue;
            }
            return candidate.startTimeMs;
        }
        return 0L;
    }

    private long previewLastLyricEndTime(LyricsLine line) {
        if (line == null) {
            return -1L;
        }
        long lastEnd = previewMaxSyllableEnd(line.syllables, line.endTimeMs);
        if (line.vocalParts != null) {
            for (LyricsLine.VocalPart part : line.vocalParts) {
                lastEnd = Math.max(lastEnd, previewMaxSyllableEnd(part.syllables, line.endTimeMs));
            }
        }
        if (lastEnd >= 0L) {
            return lastEnd;
        }
        return line.endTimeMs > line.startTimeMs ? line.endTimeMs : -1L;
    }

    private long previewMaxSyllableEnd(List<LyricsLine.Syllable> syllables, long fallbackLineEndMs) {
        if (syllables == null || syllables.isEmpty()) {
            return -1L;
        }
        long lastEnd = -1L;
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null) {
                continue;
            }
            long endTime = syllable.endTimeMs > syllable.startTimeMs ? syllable.endTimeMs : fallbackLineEndMs;
            if (endTime >= syllable.startTimeMs) {
                lastEnd = Math.max(lastEnd, endTime);
            }
        }
        return lastEnd;
    }

    private String previewInstrumentalKind(int lineIndex, int lineCount) {
        if (lineIndex == 0) {
            return "prelude";
        }
        if (lineIndex == Math.max(0, lineCount - 1)) {
            return "postlude";
        }
        return "break";
    }

    private String interludePreviewLabel(String kind) {
        if (!interludeLabelsEnabled()) {
            return "";
        }
        if ("prelude".equals(kind)) {
            return ui("interlude.prelude");
        }
        if ("postlude".equals(kind)) {
            return ui("interlude.postlude");
        }
        return ui("interlude.break");
    }

    private boolean interludeLabelsEnabled() {
        return settings.interludeLabelsEnabled;
    }

    private String previewInterludeCandidateText(LyricsLine line) {
        if (line == null) {
            return "";
        }
        String text = line.text == null ? "" : line.text;
        if (!text.trim().isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        if (line.vocalParts != null) {
            for (LyricsLine.VocalPart part : line.vocalParts) {
                if (part != null && part.text != null) {
                    builder.append(part.text);
                }
            }
        }
        return builder.toString();
    }

    private boolean isPreviewInterludeMarkerText(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }

        int start = 0;
        int end = text.length();
        while (start < end) {
            if (text.charAt(start) <= ' ') {
                start++;
            } else if (isPreviewHtmlSpaceEntity(text, start, end)) {
                start += 6;
            } else {
                break;
            }
        }
        while (end > start) {
            if (text.charAt(end - 1) <= ' ') {
                end--;
            } else if (isPreviewHtmlSpaceEntityEndingAt(text, start, end)) {
                end -= 6;
            } else {
                break;
            }
        }

        for (int offset = start; offset < end; ) {
            if (isPreviewHtmlSpaceEntity(text, offset, end)) {
                offset += 6;
                continue;
            }
            int codePoint = text.codePointAt(offset);
            if (!isPreviewInterludeMarkerCodePoint(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private boolean isPreviewMusicNoteInterludeMarkerText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        boolean containsMusicNote = false;
        for (int offset = 0; offset < text.length(); ) {
            if (isPreviewHtmlSpaceEntity(text, offset, text.length())) {
                offset += 6;
                continue;
            }
            int codePoint = text.codePointAt(offset);
            if (!isPreviewInterludeMarkerCodePoint(codePoint)) {
                return false;
            }
            containsMusicNote |= isPreviewMusicNoteCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        return containsMusicNote;
    }

    private boolean isPreviewMusicNoteCodePoint(int codePoint) {
        return codePoint >= 0x2669 && codePoint <= 0x266C;
    }

    private boolean isPreviewHtmlSpaceEntity(String text, int offset, int end) {
        return end - offset >= 6
                && (text.startsWith("&nbsp;", offset) || text.startsWith("&NBSP;", offset));
    }

    private boolean isPreviewHtmlSpaceEntityEndingAt(String text, int start, int end) {
        return end - start >= 6 && isPreviewHtmlSpaceEntity(text, end - 6, end);
    }

    private boolean isPreviewInterludeMarkerCodePoint(int codePoint) {
        return Character.isWhitespace(codePoint)
                || codePoint == 0x00A0
                || (codePoint >= 0x200B && codePoint <= 0x200D)
                || codePoint == 0xFEFF
                || (codePoint >= 0x2669 && codePoint <= 0x266C);
    }

    private boolean previewAutoInstrumentalBreakEnabled() {
        return settings.autoInstrumentalBreakEnabled;
    }

    private long previewTrackDurationMs() {
        return trackDurationMs;
    }

    private void addSupplementPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            String generatingText,
            String fallback,
            String fallbackRubyText,
            List<LyricsLine.Syllable> fallbackSyllables,
            String fallbackKind,
            boolean generating,
            String slotId
    ) {
        String value = text == null ? "" : text.trim();
        String rubyText = "";
        List<LyricsLine.Syllable> syllables = Collections.emptyList();
        String kind = "vocal";
        if (value.isEmpty()) {
            if (generating) {
                value = generatingText;
            } else {
                value = fallback;
                rubyText = fallbackRubyText == null ? "" : fallbackRubyText.trim();
                syllables = fallbackSyllables == null ? Collections.emptyList() : fallbackSyllables;
                kind = fallbackKind;
            }
        }
        if (samePreviewTextAlreadyShown(rows, value)) {
            return;
        }
        addPreviewRow(rows, value, rubyText, syllables, kind, slotId);
    }

    private void addPreviewRow(List<MainLyricPreviewView.PreviewLine> rows, String text) {
        addPreviewRow(rows, text, Collections.emptyList(), "vocal");
    }

    private void addPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            List<LyricsLine.Syllable> syllables
    ) {
        addPreviewRow(rows, text, syllables, "vocal");
    }

    private void addPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            List<LyricsLine.Syllable> syllables,
            String kind
    ) {
        addPreviewRow(rows, text, syllables, kind, rows.isEmpty()
                ? AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL
                : AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION);
    }

    private void addPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            List<LyricsLine.Syllable> syllables,
            String kind,
            String slotId
    ) {
        addPreviewRow(rows, text, "", syllables, kind, slotId);
    }

    private void addPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            String rubyText,
            List<LyricsLine.Syllable> syllables,
            String kind,
            String slotId
    ) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        rows.add(new MainLyricPreviewView.PreviewLine(value, rubyText, rows.isEmpty(), syllables, kind, slotId));
    }

    private boolean samePreviewTextAlreadyShown(List<MainLyricPreviewView.PreviewLine> rows, String text) {
        String value = text == null ? "" : text.trim();
        for (MainLyricPreviewView.PreviewLine row : rows) {
            if (row.text.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private PreviewText originalPreviewText(LyricsLine line) {
        if (!hasMultiplePreviewVocalParts(line) && line.text != null && !line.text.trim().isEmpty()) {
            String text = line.text.trim();
            return new PreviewText(text, previewLineRubyText(line), karaokeSyllablesForText(text, line.syllables), line.kind);
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder rubyBuilder = new StringBuilder();
        List<LyricsLine.Syllable> syllables = new ArrayList<>();
        boolean syllablesUsable = true;
        for (LyricsLine.VocalPart part : line.vocalParts) {
            if (part.text == null || part.text.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
                rubyBuilder.append(' ');
                syllables.add(spaceSyllable(syllables, part));
            }
            String partText = part.text.trim();
            builder.append(partText);
            rubyBuilder.append(previewPartRubyText(part, partText));
            List<LyricsLine.Syllable> partSyllables = karaokeSyllablesForText(partText, part.syllables);
            if (partSyllables.isEmpty()) {
                syllablesUsable = false;
            }
            syllables.addAll(partSyllables);
        }
        if (builder.length() == 0) {
            return new PreviewText("♪", "", Collections.emptyList(), line.kind);
        }
        return new PreviewText(
                builder.toString(),
                rubyBuilder.toString(),
                syllablesUsable ? syllables : Collections.emptyList(),
                line.kind
        );
    }

    private String previewLineRubyText(LyricsLine line) {
        if (!previewJapaneseFuriganaEnabled() || line == null) {
            return "";
        }
        return line.furiganaText == null ? "" : line.furiganaText.trim();
    }

    private String previewPartRubyText(LyricsLine.VocalPart part, String fallbackText) {
        if (!previewJapaneseFuriganaEnabled() || part == null) {
            return fallbackText == null ? "" : fallbackText;
        }
        String rubyText = part.furiganaText == null ? "" : part.furiganaText.trim();
        return rubyText.isEmpty() ? (fallbackText == null ? "" : fallbackText) : rubyText;
    }

    private boolean previewJapaneseFuriganaEnabled() {
        return settings.japaneseFuriganaEnabled;
    }

    private boolean hasMultiplePreviewVocalParts(LyricsLine line) {
        if (line == null || line.vocalParts == null) {
            return false;
        }
        int count = 0;
        for (LyricsLine.VocalPart part : line.vocalParts) {
            if (part != null && part.text != null && !part.text.trim().isEmpty()) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<LyricsLine.Syllable> karaokeSyllablesForText(String text, List<LyricsLine.Syllable> syllables) {
        if (text == null || syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder builder = new StringBuilder();
        List<LyricsLine.Syllable> usable = new ArrayList<>();
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null || syllable.text == null || syllable.text.isEmpty()) {
                continue;
            }
            builder.append(syllable.text);
            usable.add(syllable);
        }
        return builder.toString().trim().equals(value) ? trimPreviewSyllables(usable) : Collections.emptyList();
    }

    private List<LyricsLine.Syllable> trimPreviewSyllables(List<LyricsLine.Syllable> syllables) {
        if (syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }
        int start = 0;
        int end = syllables.size() - 1;
        while (start <= end && isWhitespaceSyllable(syllables.get(start))) {
            start++;
        }
        while (end >= start && isWhitespaceSyllable(syllables.get(end))) {
            end--;
        }
        if (start > end) {
            return Collections.emptyList();
        }
        return new ArrayList<>(syllables.subList(start, end + 1));
    }

    private boolean isWhitespaceSyllable(LyricsLine.Syllable syllable) {
        if (syllable == null || syllable.text == null || syllable.text.isEmpty()) {
            return true;
        }
        String value = syllable.text;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private LyricsLine.Syllable spaceSyllable(List<LyricsLine.Syllable> previous, LyricsLine.VocalPart nextPart) {
        long start = previous == null || previous.isEmpty()
                ? (nextPart == null ? 0L : nextPart.startTimeMs)
                : previous.get(previous.size() - 1).endTimeMs;
        long end = nextPart == null ? start : Math.max(start, nextPart.startTimeMs);
        return new LyricsLine.Syllable(" ", start, end);
    }

    private static final class PreviewText {
        final String text;
        final String rubyText;
        final List<LyricsLine.Syllable> syllables;
        final String kind;

        PreviewText(String text, String rubyText, List<LyricsLine.Syllable> syllables, String kind) {
            this.text = text == null ? "" : text;
            this.rubyText = rubyText == null ? "" : rubyText;
            this.syllables = syllables == null ? Collections.emptyList() : new ArrayList<>(syllables);
            this.kind = kind == null || kind.trim().isEmpty() ? "vocal" : kind.trim();
        }
    }

    static final class PreviewEntry {
        final LyricsLine line;
        final List<LyricsLine> lines;
        final long startTimeMs;
        final long endTimeMs;
        final String interludeKind;

        private PreviewEntry(List<LyricsLine> lines, long startTimeMs, long endTimeMs, String interludeKind) {
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
            this.line = lines.isEmpty() ? null : lines.get(0);
            this.startTimeMs = Math.max(0L, startTimeMs);
            this.endTimeMs = Math.max(this.startTimeMs, endTimeMs);
            this.interludeKind = interludeKind == null ? "" : interludeKind;
        }

        static PreviewEntry line(LyricsLine line) {
            return lines(line == null ? Collections.emptyList() : Collections.singletonList(line));
        }

        static PreviewEntry lines(List<LyricsLine> lines) {
            long start = Long.MAX_VALUE;
            long end = 0L;
            for (LyricsLine line : lines) {
                start = Math.min(start, line.startTimeMs);
                end = Math.max(end, line.endTimeMs);
            }
            return new PreviewEntry(lines, start == Long.MAX_VALUE ? 0L : start, end, "");
        }

        static PreviewEntry interlude(long startTimeMs, long endTimeMs, String kind) {
            return new PreviewEntry(Collections.emptyList(), startTimeMs, endTimeMs, kind);
        }

        boolean isInterlude() {
            return line == null && !interludeKind.isEmpty();
        }

        boolean contains(long positionMs) {
            return positionMs >= startTimeMs && positionMs < endTimeMs;
        }
    }
}
