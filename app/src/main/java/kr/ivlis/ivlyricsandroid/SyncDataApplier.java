package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SyncDataApplier {
    private static final long DURATION_OFFSET_MIN_DIFF_MS = 500L;
    private static final double DURATION_FRONT_OFFSET_RATIO = 0.3;

    private SyncDataApplier() {
    }

    static List<LyricsLine> apply(List<LyricsLine> baseLyrics, JSONObject syncBody, TrackSnapshot track) {
        return applyWithDiagnostics(baseLyrics, syncBody, track).lines;
    }

    static ApplyResult applyWithDiagnostics(List<LyricsLine> baseLyrics, JSONObject syncBody, TrackSnapshot track) {
        List<String> diagnostics = new ArrayList<>();
        if (baseLyrics == null || baseLyrics.isEmpty() || syncBody == null) {
            return ApplyResult.empty("missing base lyrics or sync body");
        }

        JSONArray rawLines = syncBody.optJSONArray("lines");
        if (rawLines == null || rawLines.length() == 0) {
            return ApplyResult.empty("sync body has no lines");
        }

        JSONObject source = syncBody.optJSONObject("source");
        boolean hasSourceLineShape = source != null
                && source.optJSONArray("lineCharCounts") != null
                && source.optJSONArray("lineCharCounts").length() > 0;
        boolean normalizeParentheticalLines = syncBody.optInt("version", 1) >= 2 || hasSourceLineShape;
        List<String> baseLines = getBaseLyricLines(baseLyrics, normalizeParentheticalLines);
        if (baseLines.isEmpty()) {
            return ApplyResult.empty("base lyrics became empty after normalization");
        }
        diagnostics.add("shape: baseLines=" + baseLines.size()
                + " / rawSyncLines=" + rawLines.length()
                + " / sourceLineShape=" + hasSourceLineShape
                + " / version=" + syncBody.optInt("version", 1));

        List<SyncLine> syncLines = parseSyncLines(rawLines);
        if (syncLines.isEmpty()) {
            return ApplyResult.empty("no valid sync lines parsed from JSON");
        }

        int sourcePrefix = resolveSourcePrefix(source, baseLines);
        if (sourcePrefix < 0) {
            List<Integer> expectedCounts = source == null
                    ? Collections.emptyList()
                    : readIntArray(source.optJSONArray("lineCharCounts"));
            return ApplyResult.empty("source line shape mismatch: expected="
                    + previewIntegers(expectedCounts)
                    + " actual=" + previewIntegers(lineCharCounts(baseLines)));
        }
        if (sourcePrefix > 0) {
            int charOffset = leadingCharOffset(readIntArray(source.optJSONArray("lineCharCounts")), sourcePrefix);
            syncLines = shiftSyncLines(syncLines, charOffset);
            diagnostics.add("source prefix trimmed: lines=" + sourcePrefix + " / charOffset=" + charOffset);
        } else if (source != null) {
            String expectedFingerprint = source.optString("lyricsFingerprint", "");
            if (!expectedFingerprint.isEmpty()) {
                String actualFingerprint = lyricsFingerprint(joinLinesForFingerprint(baseLines));
                if (!expectedFingerprint.equals(actualFingerprint)) {
                    return ApplyResult.empty("source fingerprint mismatch: expected="
                            + expectedFingerprint
                            + " actual=" + actualFingerprint);
                }
                diagnostics.add("source fingerprint matched: " + actualFingerprint);
            }
        }

        DurationAdjustment durationAdjustment = computeDurationAdjustment(syncBody, track);
        if (durationAdjustment.offsetMs != 0L) {
            syncLines = shiftSyncTimes(syncLines, durationAdjustment.offsetMs / 1000.0);
            diagnostics.add("duration offset applied: frontOffset="
                    + durationAdjustment.offsetMs
                    + "ms / registered="
                    + durationAdjustment.registeredDurationMs
                    + "ms / current="
                    + durationAdjustment.currentDurationMs
                    + "ms / diff="
                    + durationAdjustment.diffMs
                    + "ms / rearRemainder="
                    + (durationAdjustment.diffMs - durationAdjustment.offsetMs)
                    + "ms / frontRatio="
                    + DURATION_FRONT_OFFSET_RATIO);
        } else if (durationAdjustment.registeredDurationMs > 0L
                && durationAdjustment.currentDurationMs > 0L
                && durationAdjustment.diffMs != 0L) {
            diagnostics.add("duration offset skipped: registered="
                    + durationAdjustment.registeredDurationMs
                    + "ms / current="
                    + durationAdjustment.currentDurationMs
                    + "ms / diff="
                    + durationAdjustment.diffMs
                    + "ms");
        }

        List<String> fullChars = splitChars(joinLines(baseLines));
        syncLines = normalizeParallelParts(syncLines, fullChars);
        diagnostics.add("char map: fullChars=" + fullChars.size());

        List<LyricsLine> result = new ArrayList<>();
        int skippedLines = 0;
        for (int index = 0; index < syncLines.size(); index++) {
            SyncLine line = syncLines.get(index);
            if (!line.isUsable(fullChars.size())) {
                skippedLines++;
                continue;
            }

            SyncLine nextLine = nextUsableLine(syncLines, index + 1, fullChars.size());
            String lineText = joinChars(fullChars, line.start, line.end);
            long lineStartMs = secondsToMs(line.chars.get(0));
            long lineEndMs = nextLine != null && !nextLine.chars.isEmpty()
                    ? secondsToMs(nextLine.chars.get(0))
                    : secondsToMs(line.chars.get(line.chars.size() - 1)) + 2_000L;

            double lineDurationSec = (lineEndMs - lineStartMs) / 1000.0;
            double avgCharDuration = Math.max(0.2, lineDurationSec / Math.max(1, line.chars.size()));
            double lastCharMaxDuration = Math.max(0.5, Math.min(1.5, avgCharDuration * 2.5));

            TimedSyllables timedLine = buildLineSyllables(line, lineText, lineEndMs, lastCharMaxDuration);
            if (timedLine.syllables.isEmpty()) {
                continue;
            }
            lineEndMs = timedLine.endTimeMs;

            List<LyricsLine.VocalPart> vocalParts = buildVocalParts(line, fullChars, lineEndMs, lastCharMaxDuration);
            if (vocalParts.size() > 1) {
                LyricsLine.VocalPart leadPart = findLeadPart(vocalParts);
                long startMs = lineStartMs;
                long endMs = lineEndMs;
                for (LyricsLine.VocalPart part : vocalParts) {
                    startMs = Math.min(startMs, part.startTimeMs);
                    endMs = Math.max(endMs, part.endTimeMs);
                }
                result.add(new LyricsLine(
                        startMs,
                        endMs,
                        lineText,
                        timedLine.syllables,
                        firstNonEmpty(line.speaker, leadPart.speaker),
                        firstNonEmpty(line.speakerColor, leadPart.speakerColor),
                        firstNonEmpty(line.kind, leadPart.kind),
                        vocalParts
                ));
            } else if (vocalParts.size() == 1) {
                LyricsLine.VocalPart part = vocalParts.get(0);
                result.add(new LyricsLine(
                        lineStartMs,
                        lineEndMs,
                        lineText,
                        timedLine.syllables,
                        firstNonEmpty(line.speaker, part.speaker),
                        firstNonEmpty(line.speakerColor, part.speakerColor),
                        firstNonEmpty(line.kind, part.kind),
                        Collections.emptyList()
                ));
            } else {
                result.add(new LyricsLine(
                        lineStartMs,
                        lineEndMs,
                        lineText,
                        timedLine.syllables,
                        line.speaker,
                        line.speakerColor,
                        line.kind,
                        Collections.emptyList()
                ));
            }
        }

        if (result.isEmpty()) {
            return ApplyResult.empty("rendered 0 karaoke lines; skippedSyncLines="
                    + skippedLines
                    + " / fullChars=" + fullChars.size());
        }
        if (skippedLines > 0) {
            diagnostics.add("skipped unusable sync lines=" + skippedLines);
        }
        return new ApplyResult(result, diagnostics);
    }

    private static TimedSyllables buildLineSyllables(
            SyncLine line,
            String lineText,
            long lineEndMs,
            double lastCharMaxDuration
    ) {
        List<String> chars = splitChars(lineText);
        int charCount = Math.min(line.chars.size(), chars.size());
        if (charCount == 0) {
            return new TimedSyllables(Collections.emptyList(), lineEndMs);
        }

        List<LyricsLine.Syllable> syllables = new ArrayList<>();
        long adjustedEndMs = lineEndMs;
        for (int charIndex = 0; charIndex < charCount; charIndex++) {
            long charStartMs = secondsToMs(line.chars.get(charIndex));
            long charEndMs;
            if (charIndex < charCount - 1) {
                charEndMs = secondsToMs(line.chars.get(charIndex + 1));
            } else {
                long naturalEndMs = secondsToMs(line.chars.get(charIndex) + lastCharMaxDuration);
                charEndMs = Math.min(lineEndMs, naturalEndMs);
                adjustedEndMs = charEndMs;
            }
            syllables.add(new LyricsLine.Syllable(chars.get(charIndex), charStartMs, charEndMs));
        }
        return new TimedSyllables(syllables, adjustedEndMs);
    }

    private static List<LyricsLine.VocalPart> buildVocalParts(
            SyncLine line,
            List<String> fullChars,
            long fallbackEndMs,
            double lastCharMaxDuration
    ) {
        if (line.parts.isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricsLine.VocalPart> result = new ArrayList<>();
        for (ParallelPart part : line.parts) {
            LyricsLine.VocalPart vocalPart = buildVocalPart(part, line, fullChars, fallbackEndMs, lastCharMaxDuration);
            if (vocalPart != null) {
                result.add(vocalPart);
            }
        }
        return result;
    }

    private static LyricsLine.VocalPart buildVocalPart(
            ParallelPart part,
            SyncLine line,
            List<String> fullChars,
            long fallbackEndMs,
            double lastCharMaxDuration
    ) {
        if (part == null || part.ranges.isEmpty() || part.chars.isEmpty()) {
            return null;
        }

        List<LyricsLine.Syllable> syllables = new ArrayList<>();
        int partCharIndex = 0;
        for (int rangeIndex = 0; rangeIndex < part.ranges.size(); rangeIndex++) {
            Range range = part.ranges.get(rangeIndex);
            if (rangeIndex > 0) {
                int joinMode = rangeIndex - 1 < part.join.size() ? part.join.get(rangeIndex - 1) : 1;
                if (joinMode == 1 || joinMode == 2) {
                    long previousTime = syllables.isEmpty()
                            ? secondsToMs(part.chars.get(Math.max(0, Math.min(partCharIndex, part.chars.size() - 1))))
                            : syllables.get(syllables.size() - 1).endTimeMs;
                    syllables.add(new LyricsLine.Syllable(" ", previousTime, previousTime));
                }
            }

            for (int sourceIndex = range.start; sourceIndex <= range.end; sourceIndex++) {
                if (sourceIndex < 0 || sourceIndex >= fullChars.size()) {
                    partCharIndex++;
                    continue;
                }
                double startSeconds = partCharIndex < part.chars.size()
                        ? part.chars.get(partCharIndex)
                        : (line.chars.isEmpty() ? 0.0 : line.chars.get(0));
                long charStartMs = secondsToMs(startSeconds);
                long charEndMs;
                if (partCharIndex + 1 < part.chars.size()) {
                    charEndMs = secondsToMs(part.chars.get(partCharIndex + 1));
                } else {
                    charEndMs = Math.min(fallbackEndMs, charStartMs + Math.round(lastCharMaxDuration * 1000.0));
                }
                syllables.add(new LyricsLine.Syllable(fullChars.get(sourceIndex), charStartMs, charEndMs));
                partCharIndex++;
            }
        }

        List<LyricsLine.Syllable> trimmed = trimWhitespaceSyllables(syllables);
        if (trimmed.isEmpty()) {
            return null;
        }

        StringBuilder text = new StringBuilder();
        for (LyricsLine.Syllable syllable : trimmed) {
            text.append(syllable.text);
        }
        return new LyricsLine.VocalPart(
                part.id,
                part.role,
                part.speaker,
                part.speakerColor,
                part.kind,
                text.toString(),
                trimmed
        );
    }

    private static List<LyricsLine.Syllable> trimWhitespaceSyllables(List<LyricsLine.Syllable> syllables) {
        int start = 0;
        int end = syllables == null ? 0 : syllables.size();
        while (start < end && isWhitespace(syllables.get(start).text)) {
            start++;
        }
        while (end > start && isWhitespace(syllables.get(end - 1).text)) {
            end--;
        }
        if (start >= end) {
            return Collections.emptyList();
        }
        return new ArrayList<>(syllables.subList(start, end));
    }

    private static LyricsLine.VocalPart findLeadPart(List<LyricsLine.VocalPart> parts) {
        for (LyricsLine.VocalPart part : parts) {
            if ("lead".equals(part.role)) {
                return part;
            }
        }
        return parts.get(0);
    }

    private static List<String> getBaseLyricLines(List<LyricsLine> lines, boolean normalizeParentheticalLines) {
        List<String> result = new ArrayList<>();
        for (LyricsLine line : lines) {
            String text = normalize(line.text).trim();
            if (!text.isEmpty()) {
                result.add(normalizeParentheticalLines ? stripStandaloneParentheticalLine(text) : text);
            }
        }

        if (normalizeParentheticalLines) {
            result = normalizeStandaloneParentheticalBlocks(result);
        }

        List<String> filtered = new ArrayList<>();
        for (String line : result) {
            String text = normalize(line).trim();
            if (!text.isEmpty()) {
                filtered.add(text);
            }
        }
        return filtered;
    }

    private static List<SyncLine> parseSyncLines(JSONArray rawLines) {
        List<SyncLine> result = new ArrayList<>();
        for (int index = 0; index < rawLines.length(); index++) {
            JSONObject rawLine = rawLines.optJSONObject(index);
            if (rawLine == null) continue;

            int start = rawLine.optInt("start", -1);
            int end = rawLine.optInt("end", -1);
            List<Double> chars = readDoubleArray(rawLine.optJSONArray("chars"));
            Parallel parallel = parseParallel(rawLine.optJSONObject("parallel"));
            result.add(new SyncLine(
                    start,
                    end,
                    chars,
                    rawLine.optString("speaker", ""),
                    rawLine.optString("speaker-color", ""),
                    rawLine.optString("kind", "vocal"),
                    parallel.parts,
                    parallel.hiddenRanges
            ));
        }
        return result;
    }

    private static Parallel parseParallel(JSONObject object) {
        if (object == null) {
            return new Parallel(Collections.emptyList(), Collections.emptyList());
        }

        List<Range> hiddenRanges = readRanges(object.optJSONArray("hiddenRanges"));
        JSONArray rawParts = object.optJSONArray("parts");
        if (rawParts == null || rawParts.length() == 0) {
            return new Parallel(Collections.emptyList(), hiddenRanges);
        }

        List<ParallelPart> parts = new ArrayList<>();
        for (int index = 0; index < rawParts.length(); index++) {
            JSONObject rawPart = rawParts.optJSONObject(index);
            if (rawPart == null) continue;

            List<Range> ranges = readRanges(rawPart.optJSONArray("ranges"));
            List<Double> chars = readDoubleArray(rawPart.optJSONArray("chars"));
            if (ranges.isEmpty() || chars.isEmpty()) {
                continue;
            }
            parts.add(new ParallelPart(
                    rawPart.optString("id", ""),
                    rawPart.optString("role", ""),
                    rawPart.optString("speaker", ""),
                    rawPart.optString("speaker-color", ""),
                    rawPart.optString("kind", "vocal"),
                    ranges,
                    readIntArray(rawPart.optJSONArray("join")),
                    chars
            ));
        }
        return new Parallel(parts, hiddenRanges);
    }

    private static List<SyncLine> normalizeParallelParts(List<SyncLine> lines, List<String> fullChars) {
        if (lines.isEmpty() || fullChars.isEmpty()) {
            return lines;
        }

        List<SyncLine> normalized = new ArrayList<>();
        for (SyncLine line : lines) {
            if (line.parts.isEmpty()) {
                normalized.add(line);
                continue;
            }

            List<ParallelPart> parts = new ArrayList<>();
            for (ParallelPart part : line.parts) {
                parts.add(stripParentheticalPartRange(part, fullChars));
            }
            parts = splitHiddenDelimitedParallelParts(parts, line.hiddenRanges);
            normalized.add(line.withParts(parts));
        }
        return normalized;
    }

    private static ParallelPart stripParentheticalPartRange(ParallelPart part, List<String> fullChars) {
        if (part.ranges.size() != 1) {
            return part;
        }

        Range range = part.ranges.get(0);
        if (range.start < 0 || range.end >= fullChars.size() || part.chars.size() != range.count()) {
            return part;
        }

        StripResult stripped = stripStandaloneParentheticalCharRange(fullChars, range.start, range.end);
        if (!stripped.changed || stripped.start > stripped.end) {
            return part;
        }

        int charOffset = stripped.start - range.start;
        int charEnd = charOffset + (stripped.end - stripped.start + 1);
        if (charOffset < 0 || charEnd > part.chars.size()) {
            return part;
        }
        List<Double> chars = new ArrayList<>(part.chars.subList(charOffset, charEnd));
        return part.withRangesAndChars(
                Collections.singletonList(new Range(stripped.start, stripped.end)),
                chars
        );
    }

    private static List<ParallelPart> splitHiddenDelimitedParallelParts(List<ParallelPart> parts, List<Range> hiddenRanges) {
        if (hiddenRanges.isEmpty()) {
            return parts;
        }

        Set<String> usedIds = new HashSet<>();
        for (ParallelPart part : parts) {
            if (!part.id.isEmpty()) {
                usedIds.add(part.id);
            }
        }

        List<ParallelPart> split = new ArrayList<>();
        boolean changed = false;
        for (ParallelPart part : parts) {
            List<ParallelPart> splitPart = splitHiddenDelimitedParallelPart(part, hiddenRanges, usedIds);
            if (splitPart == null) {
                split.add(part);
            } else {
                changed = true;
                split.addAll(splitPart);
            }
        }
        return changed && split.size() <= 16 ? split : parts;
    }

    private static List<ParallelPart> splitHiddenDelimitedParallelPart(
            ParallelPart part,
            List<Range> hiddenRanges,
            Set<String> usedIds
    ) {
        if (!"background".equals(part.role)
                || part.id.isEmpty()
                || part.ranges.size() < 2
                || part.join.size() != part.ranges.size() - 1
                || part.chars.size() != countRangeChars(part.ranges)) {
            return null;
        }
        for (int joinMode : part.join) {
            if (joinMode < 0 || joinMode > 2 || joinMode == 2) {
                return null;
            }
        }
        for (int index = 0; index < part.ranges.size(); index++) {
            Range range = part.ranges.get(index);
            if (index > 0) {
                Range previous = part.ranges.get(index - 1);
                if (range.start <= previous.end
                        || !isRangeGapFullyHidden(hiddenRanges, previous.end + 1, range.start - 1)) {
                    return null;
                }
            }
        }

        List<ParallelPart> splitParts = new ArrayList<>();
        int charOffset = 0;
        for (int index = 0; index < part.ranges.size(); index++) {
            Range range = part.ranges.get(index);
            int charCount = range.count();
            String id = index == 0 ? part.id : getNextPartId(usedIds);
            if (id == null) {
                return null;
            }
            splitParts.add(new ParallelPart(
                    id,
                    part.role,
                    part.speaker,
                    part.speakerColor,
                    part.kind,
                    Collections.singletonList(range),
                    Collections.emptyList(),
                    new ArrayList<>(part.chars.subList(charOffset, charOffset + charCount))
            ));
            charOffset += charCount;
        }
        return splitParts;
    }

    private static String getNextPartId(Set<String> usedIds) {
        for (char label = 'a'; label <= 'z'; label++) {
            String id = String.valueOf(label);
            if (!usedIds.contains(id)) {
                usedIds.add(id);
                return id;
            }
        }
        for (int index = 1; index <= 16; index++) {
            String id = "p" + index;
            if (!usedIds.contains(id)) {
                usedIds.add(id);
                return id;
            }
        }
        return null;
    }

    private static boolean isRangeGapFullyHidden(List<Range> hiddenRanges, int gapStart, int gapEnd) {
        if (gapStart > gapEnd || hiddenRanges.isEmpty()) {
            return false;
        }

        int cursor = gapStart;
        for (Range range : hiddenRanges) {
            if (range.end < cursor) {
                continue;
            }
            if (range.start > cursor) {
                return false;
            }
            cursor = Math.max(cursor, range.end + 1);
            if (cursor > gapEnd) {
                return true;
            }
        }
        return false;
    }

    private static int countRangeChars(List<Range> ranges) {
        int total = 0;
        for (Range range : ranges) {
            total += range.count();
        }
        return total;
    }

    private static SyncLine nextUsableLine(List<SyncLine> lines, int startIndex, int fullCharCount) {
        for (int index = startIndex; index < lines.size(); index++) {
            SyncLine line = lines.get(index);
            if (line.isUsable(fullCharCount)) {
                return line;
            }
        }
        return null;
    }

    private static int resolveSourcePrefix(JSONObject source, List<String> baseLines) {
        if (source == null) {
            return 0;
        }

        List<Integer> expectedCounts = readIntArray(source.optJSONArray("lineCharCounts"));
        if (expectedCounts.isEmpty()) {
            return 0;
        }

        List<Integer> actualCounts = new ArrayList<>();
        for (String line : baseLines) {
            actualCounts.add(splitChars(line).size());
        }

        if (sameShape(expectedCounts, actualCounts, 0)) {
            return 0;
        }
        if (expectedCounts.size() <= actualCounts.size()) {
            return -1;
        }

        int maxPrefix = expectedCounts.size() - actualCounts.size();
        for (int prefix = 1; prefix <= maxPrefix; prefix++) {
            if (sameShape(expectedCounts, actualCounts, prefix)) {
                return prefix;
            }
        }
        return -1;
    }

    private static boolean sameShape(List<Integer> expected, List<Integer> actual, int expectedOffset) {
        if (expected.size() - expectedOffset != actual.size()) {
            return false;
        }
        for (int index = 0; index < actual.size(); index++) {
            if (!expected.get(expectedOffset + index).equals(actual.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static int leadingCharOffset(List<Integer> lineCounts, int prefixLength) {
        int total = 0;
        for (int index = 0; index < Math.min(prefixLength, lineCounts.size()); index++) {
            total += Math.max(0, lineCounts.get(index));
        }
        return total;
    }

    private static List<SyncLine> shiftSyncLines(List<SyncLine> lines, int charOffset) {
        if (charOffset <= 0) {
            return lines;
        }

        List<SyncLine> shifted = new ArrayList<>();
        for (SyncLine line : lines) {
            if (line.end < charOffset) {
                continue;
            }
            List<ParallelPart> parts = new ArrayList<>();
            for (ParallelPart part : line.parts) {
                ParallelPart shiftedPart = shiftPartRanges(part, charOffset);
                if (shiftedPart != null) {
                    parts.add(shiftedPart);
                }
            }
            shifted.add(new SyncLine(
                    Math.max(0, line.start - charOffset),
                    Math.max(0, line.end - charOffset),
                    line.chars,
                    line.speaker,
                    line.speakerColor,
                    line.kind,
                    parts,
                    shiftRanges(line.hiddenRanges, charOffset).ranges
            ));
        }
        return shifted;
    }

    private static ParallelPart shiftPartRanges(ParallelPart part, int charOffset) {
        ShiftedRanges shifted = shiftRanges(part.ranges, charOffset);
        if (shifted.ranges.isEmpty()) {
            return null;
        }

        int nextCharCount = countRangeChars(shifted.ranges);
        int from = Math.min(Math.max(0, shifted.removedLeadingChars), part.chars.size());
        int to = Math.min(part.chars.size(), from + nextCharCount);
        if (to - from != nextCharCount) {
            return null;
        }
        return part.withRangesAndChars(shifted.ranges, new ArrayList<>(part.chars.subList(from, to)));
    }

    private static ShiftedRanges shiftRanges(List<Range> ranges, int charOffset) {
        List<Range> shifted = new ArrayList<>();
        int removedLeadingChars = 0;
        for (Range range : ranges) {
            if (range.end < charOffset) {
                removedLeadingChars += range.count();
                continue;
            }
            if (range.start < charOffset) {
                removedLeadingChars += charOffset - range.start;
            }
            shifted.add(new Range(
                    Math.max(0, range.start - charOffset),
                    Math.max(0, range.end - charOffset)
            ));
        }
        return new ShiftedRanges(shifted, removedLeadingChars);
    }

    private static List<SyncLine> shiftSyncTimes(List<SyncLine> lines, double offsetSeconds) {
        List<SyncLine> shifted = new ArrayList<>();
        for (SyncLine line : lines) {
            List<Double> chars = shiftTimes(line.chars, offsetSeconds);
            List<ParallelPart> parts = new ArrayList<>();
            for (ParallelPart part : line.parts) {
                parts.add(part.withChars(shiftTimes(part.chars, offsetSeconds)));
            }
            shifted.add(new SyncLine(
                    line.start,
                    line.end,
                    chars,
                    line.speaker,
                    line.speakerColor,
                    line.kind,
                    parts,
                    line.hiddenRanges
            ));
        }
        return shifted;
    }

    private static List<Double> shiftTimes(List<Double> values, double offsetSeconds) {
        List<Double> shifted = new ArrayList<>();
        for (double value : values) {
            shifted.add(Math.max(0.0, round3(value + offsetSeconds)));
        }
        return shifted;
    }

    private static DurationAdjustment computeDurationAdjustment(JSONObject syncBody, TrackSnapshot track) {
        long currentDurationMs = track == null ? 0L : track.durationMs;
        if (currentDurationMs <= 0L) {
            return new DurationAdjustment(0L, currentDurationMs, 0L, 0L);
        }
        long registeredDurationMs = normalizeSyncDataDurationMs(
                syncBody == null ? null : syncBody.opt("trackDurationMs"),
                syncBody == null ? null : syncBody.opt("durationMs"),
                syncBody == null ? null : syncBody.opt("duration_ms")
        );
        if (registeredDurationMs <= 0L) {
            return new DurationAdjustment(0L, currentDurationMs, 0L, 0L);
        }
        long diffMs = currentDurationMs - registeredDurationMs;
        if (Math.abs(diffMs) < DURATION_OFFSET_MIN_DIFF_MS) {
            return new DurationAdjustment(registeredDurationMs, currentDurationMs, diffMs, 0L);
        }
        return new DurationAdjustment(
                registeredDurationMs,
                currentDurationMs,
                diffMs,
                Math.round(diffMs * DURATION_FRONT_OFFSET_RATIO)
        );
    }

    private static long normalizeSyncDataDurationMs(Object... values) {
        if (values == null) {
            return 0L;
        }
        for (Object value : values) {
            if (value == null || JSONObject.NULL.equals(value)) {
                continue;
            }
            double numeric;
            if (value instanceof Number) {
                numeric = ((Number) value).doubleValue();
            } else {
                String text = String.valueOf(value).trim();
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    numeric = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
            if (Double.isFinite(numeric) && numeric > 0.0 && numeric <= 86_400_000.0) {
                return Math.round(numeric);
            }
        }
        return 0L;
    }

    private static List<Range> readRanges(JSONArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<Range> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject rawRange = array.optJSONObject(index);
            if (rawRange == null) {
                continue;
            }
            int start = rawRange.optInt("start", -1);
            int end = rawRange.optInt("end", -1);
            if (start >= 0 && end >= start) {
                result.add(new Range(start, end));
            }
        }
        return result;
    }

    private static List<Integer> readIntArray(JSONArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            result.add(array.optInt(index, 0));
        }
        return result;
    }

    private static List<Double> readDoubleArray(JSONArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<Double> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            double value = array.optDouble(index, Double.NaN);
            if (Double.isFinite(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<String> normalizeStandaloneParentheticalBlocks(List<String> lines) {
        List<String> normalizedLines = new ArrayList<>(lines);
        for (int index = 0; index < normalizedLines.size(); index++) {
            String trimmed = normalize(normalizedLines.get(index)).trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String openChar = firstChar(trimmed);
            String closeChar = getParenthesisClose(openChar);
            if (closeChar.isEmpty() || trimmed.contains(closeChar)) {
                continue;
            }

            int closeLineIndex = -1;
            for (int candidate = index + 1; candidate < normalizedLines.size(); candidate++) {
                String candidateTrimmed = normalize(normalizedLines.get(candidate)).trim();
                if (!candidateTrimmed.isEmpty() && candidateTrimmed.endsWith(closeChar)) {
                    closeLineIndex = candidate;
                    break;
                }
            }
            if (closeLineIndex < 0) {
                continue;
            }

            normalizedLines.set(index, stripLeadingParenthesis(normalizedLines.get(index)).trim());
            normalizedLines.set(closeLineIndex, stripTrailingParenthesis(normalizedLines.get(closeLineIndex), closeChar).trim());
        }
        return normalizedLines;
    }

    private static StripResult stripStandaloneParentheticalCharRange(List<String> chars, int start, int end) {
        int nextStart = start;
        int nextEnd = end;
        boolean changed = false;

        Range trimmed = trimWhitespaceRange(chars, nextStart, nextEnd);
        nextStart = trimmed.start;
        nextEnd = trimmed.end;
        while (nextStart < nextEnd && isStandaloneParentheticalLine(joinChars(chars, nextStart, nextEnd))) {
            nextStart++;
            nextEnd--;
            changed = true;
            trimmed = trimWhitespaceRange(chars, nextStart, nextEnd);
            nextStart = trimmed.start;
            nextEnd = trimmed.end;
        }

        return new StripResult(nextStart, nextEnd, changed);
    }

    private static Range trimWhitespaceRange(List<String> chars, int start, int end) {
        int nextStart = start;
        int nextEnd = end;
        while (nextStart <= nextEnd && nextStart < chars.size() && isWhitespace(chars.get(nextStart))) {
            nextStart++;
        }
        while (nextEnd >= nextStart && nextEnd >= 0 && isWhitespace(chars.get(nextEnd))) {
            nextEnd--;
        }
        return new Range(nextStart, nextEnd);
    }

    private static String stripStandaloneParentheticalLine(String text) {
        String value = normalize(text).trim();
        while (isStandaloneParentheticalLine(value)) {
            List<String> chars = splitChars(value);
            value = joinChars(chars, 1, chars.size() - 2).trim();
        }
        return value;
    }

    private static boolean isStandaloneParentheticalLine(String text) {
        String value = normalize(text).trim();
        List<String> chars = splitChars(value);
        if (chars.size() < 2) {
            return false;
        }
        String closeChar = getParenthesisClose(chars.get(0));
        return !closeChar.isEmpty() && closeChar.equals(chars.get(chars.size() - 1));
    }

    private static String stripLeadingParenthesis(String line) {
        String value = normalize(line);
        List<String> chars = splitChars(value);
        for (int index = 0; index < chars.size(); index++) {
            String closeChar = getParenthesisClose(chars.get(index));
            if (isWhitespace(chars.get(index))) {
                continue;
            }
            if (!closeChar.isEmpty()) {
                chars.remove(index);
            }
            break;
        }
        return joinChars(chars, 0, chars.size() - 1);
    }

    private static String stripTrailingParenthesis(String line, String closeChar) {
        String value = normalize(line);
        List<String> chars = splitChars(value);
        for (int index = chars.size() - 1; index >= 0; index--) {
            if (isWhitespace(chars.get(index))) {
                continue;
            }
            if (chars.get(index).equals(closeChar)) {
                chars.remove(index);
            }
            break;
        }
        return joinChars(chars, 0, chars.size() - 1);
    }

    private static String getParenthesisClose(String openChar) {
        if ("(".equals(openChar)) return ")";
        if ("（".equals(openChar)) return "）";
        return "";
    }

    private static String firstChar(String value) {
        List<String> chars = splitChars(value);
        return chars.isEmpty() ? "" : chars.get(0);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
    }

    private static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line);
        }
        return builder.toString();
    }

    private static String joinLinesForFingerprint(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }

    private static List<Integer> lineCharCounts(List<String> lines) {
        List<Integer> counts = new ArrayList<>();
        for (String line : lines) {
            counts.add(splitChars(line).size());
        }
        return counts;
    }

    private static String previewIntegers(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(12, values.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append(values.get(index));
        }
        if (values.size() > limit) {
            builder.append(",...");
        }
        builder.append("] len=").append(values.size());
        return builder.toString();
    }

    private static List<String> splitChars(String value) {
        String normalized = normalize(value);
        List<String> chars = new ArrayList<>();
        normalized.codePoints().forEach(codePoint -> chars.add(new String(Character.toChars(codePoint))));
        return chars;
    }

    private static String joinChars(List<String> chars, int start, int end) {
        StringBuilder builder = new StringBuilder();
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(chars.size() - 1, end);
        for (int index = safeStart; index <= safeEnd; index++) {
            builder.append(chars.get(index));
        }
        return builder.toString();
    }

    private static boolean isWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return value.codePoints().allMatch(Character::isWhitespace);
    }

    private static long secondsToMs(double seconds) {
        return Math.round(Math.max(0.0, seconds) * 1000.0);
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private static String lyricsFingerprint(String text) {
        long hash = 2_166_136_261L;
        List<String> chars = splitChars(text);
        for (String character : chars) {
            int codePoint = character.codePointAt(0);
            hash ^= codePoint;
            hash = (hash * 16_777_619L) & 0xffff_ffffL;
        }
        return String.format(Locale.ROOT, "lrclib-%s-%s",
                Long.toString(hash, 36),
                Long.toString(chars.size(), 36));
    }

    static final class ApplyResult {
        final List<LyricsLine> lines;
        final List<String> diagnostics;

        ApplyResult(List<LyricsLine> lines, List<String> diagnostics) {
            this.lines = lines == null ? Collections.emptyList() : lines;
            this.diagnostics = diagnostics == null ? Collections.emptyList() : diagnostics;
        }

        static ApplyResult empty(String reason) {
            List<String> diagnostics = new ArrayList<>();
            if (reason != null && !reason.trim().isEmpty()) {
                diagnostics.add(reason);
            }
            return new ApplyResult(Collections.emptyList(), diagnostics);
        }
    }

    private static final class TimedSyllables {
        final List<LyricsLine.Syllable> syllables;
        final long endTimeMs;

        TimedSyllables(List<LyricsLine.Syllable> syllables, long endTimeMs) {
            this.syllables = syllables;
            this.endTimeMs = endTimeMs;
        }
    }

    private static final class Parallel {
        final List<ParallelPart> parts;
        final List<Range> hiddenRanges;

        Parallel(List<ParallelPart> parts, List<Range> hiddenRanges) {
            this.parts = parts == null ? Collections.emptyList() : new ArrayList<>(parts);
            this.hiddenRanges = hiddenRanges == null ? Collections.emptyList() : new ArrayList<>(hiddenRanges);
        }
    }

    private static final class SyncLine {
        final int start;
        final int end;
        final List<Double> chars;
        final String speaker;
        final String speakerColor;
        final String kind;
        final List<ParallelPart> parts;
        final List<Range> hiddenRanges;

        SyncLine(
                int start,
                int end,
                List<Double> chars,
                String speaker,
                String speakerColor,
                String kind,
                List<ParallelPart> parts,
                List<Range> hiddenRanges
        ) {
            this.start = start;
            this.end = end;
            this.chars = chars == null ? Collections.emptyList() : new ArrayList<>(chars);
            this.speaker = speaker == null ? "" : speaker;
            this.speakerColor = speakerColor == null ? "" : speakerColor;
            this.kind = kind == null || kind.trim().isEmpty() ? "vocal" : kind.trim();
            this.parts = parts == null ? Collections.emptyList() : new ArrayList<>(parts);
            this.hiddenRanges = hiddenRanges == null ? Collections.emptyList() : new ArrayList<>(hiddenRanges);
        }

        SyncLine withParts(List<ParallelPart> nextParts) {
            return new SyncLine(start, end, chars, speaker, speakerColor, kind, nextParts, hiddenRanges);
        }

        boolean isUsable(int fullCharCount) {
            int expected = end - start + 1;
            return start >= 0
                    && end >= start
                    && end < fullCharCount
                    && expected > 0
                    && chars.size() == expected;
        }
    }

    private static final class ParallelPart {
        final String id;
        final String role;
        final String speaker;
        final String speakerColor;
        final String kind;
        final List<Range> ranges;
        final List<Integer> join;
        final List<Double> chars;

        ParallelPart(
                String id,
                String role,
                String speaker,
                String speakerColor,
                String kind,
                List<Range> ranges,
                List<Integer> join,
                List<Double> chars
        ) {
            this.id = id == null ? "" : id;
            this.role = role == null ? "" : role;
            this.speaker = speaker == null ? "" : speaker;
            this.speakerColor = speakerColor == null ? "" : speakerColor;
            this.kind = kind == null || kind.trim().isEmpty() ? "vocal" : kind.trim();
            this.ranges = ranges == null ? Collections.emptyList() : new ArrayList<>(ranges);
            this.join = join == null ? Collections.emptyList() : new ArrayList<>(join);
            this.chars = chars == null ? Collections.emptyList() : new ArrayList<>(chars);
        }

        ParallelPart withRangesAndChars(List<Range> nextRanges, List<Double> nextChars) {
            return new ParallelPart(id, role, speaker, speakerColor, kind, nextRanges, join, nextChars);
        }

        ParallelPart withChars(List<Double> nextChars) {
            return new ParallelPart(id, role, speaker, speakerColor, kind, ranges, join, nextChars);
        }
    }

    private static final class Range {
        final int start;
        final int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }

        int count() {
            return Math.max(0, end - start + 1);
        }
    }

    private static final class ShiftedRanges {
        final List<Range> ranges;
        final int removedLeadingChars;

        ShiftedRanges(List<Range> ranges, int removedLeadingChars) {
            this.ranges = ranges;
            this.removedLeadingChars = removedLeadingChars;
        }
    }

    private static final class DurationAdjustment {
        final long registeredDurationMs;
        final long currentDurationMs;
        final long diffMs;
        final long offsetMs;

        DurationAdjustment(long registeredDurationMs, long currentDurationMs, long diffMs, long offsetMs) {
            this.registeredDurationMs = Math.max(0L, registeredDurationMs);
            this.currentDurationMs = Math.max(0L, currentDurationMs);
            this.diffMs = diffMs;
            this.offsetMs = offsetMs;
        }
    }

    private static final class StripResult {
        final int start;
        final int end;
        final boolean changed;

        StripResult(int start, int end, boolean changed) {
            this.start = start;
            this.end = end;
            this.changed = changed;
        }
    }
}
