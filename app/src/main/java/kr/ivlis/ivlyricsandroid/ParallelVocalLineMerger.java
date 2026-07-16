package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes overlapping provider rows into one top-level karaoke row.
 *
 * <p>The lyric renderers activate only the latest top-level start time. Keeping every concurrently
 * timed stream in {@link LyricsLine#vocalParts} prevents a background vocal from disappearing when
 * the following lead starts. Inputs are immutable and are never modified.</p>
 */
final class ParallelVocalLineMerger {
    static final long MIN_COMPONENT_OVERLAP_MS = 30L;

    private ParallelVocalLineMerger() {
    }

    static SourceLine source(
            int sourceIndex,
            String sourceKey,
            String laneKey,
            LyricsLine line
    ) {
        return new SourceLine(sourceIndex, sourceKey, laneKey, line);
    }

    static long overlapMs(SourceLine left, SourceLine right) {
        return overlapMs(effectiveParts(left), effectiveParts(right));
    }

    private static long overlapMs(List<PartEntry> leftParts, List<PartEntry> rightParts) {
        long overlap = 0L;
        for (PartEntry leftPart : leftParts) {
            for (PartEntry rightPart : rightParts) {
                overlap = Math.max(overlap, overlapMs(leftPart.part, rightPart.part));
            }
        }
        return overlap;
    }

    /**
     * Groups overlap components without imposing a vocal-lane cap.
     *
     * <p>Every component edge must overlap by at least 30 ms. Shorter incidental timing noise stays
     * separate even when it is adjacent to an otherwise valid overlap component.</p>
     */
    static List<LyricsLine> mergeOverlaps(List<SourceLine> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();

        List<SourceLine> lines = new ArrayList<>();
        for (SourceLine source : input) {
            if (source != null && source.line != null) lines.add(source);
        }
        lines.sort(SOURCE_ORDER);
        if (lines.size() < 2) {
            return lines.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(lines.get(0).line);
        }

        int count = lines.size();
        int[] parents = new int[count];
        boolean[] seeds = new boolean[count];
        List<List<PartEntry>> preparedParts = new ArrayList<>(count);
        long[] minimumPartStarts = new long[count];
        long[] maximumPartEnds = new long[count];
        for (int index = 0; index < count; index++) parents[index] = index;
        for (int index = 0; index < count; index++) {
            SourceLine source = lines.get(index);
            List<PartEntry> parts = effectiveParts(source);
            preparedParts.add(parts);
            long start = Long.MAX_VALUE;
            long end = 0L;
            for (PartEntry entry : parts) {
                start = Math.min(start, entry.part.startTimeMs);
                end = Math.max(end, entry.part.endTimeMs);
            }
            minimumPartStarts[index] = start == Long.MAX_VALUE ? source.line.startTimeMs : start;
            maximumPartEnds[index] = end == 0L ? source.line.endTimeMs : end;
        }

        for (int leftIndex = 0; leftIndex < count; leftIndex++) {
            List<PartEntry> leftParts = preparedParts.get(leftIndex);
            long leftEnd = maximumPartEnds[leftIndex];
            for (int rightIndex = leftIndex + 1; rightIndex < count; rightIndex++) {
                if (minimumPartStarts[rightIndex] >= leftEnd) break;
                long overlap = overlapMs(leftParts, preparedParts.get(rightIndex));
                if (overlap < MIN_COMPONENT_OVERLAP_MS) continue;
                union(parents, leftIndex, rightIndex);
                seeds[leftIndex] = true;
                seeds[rightIndex] = true;
            }
        }

        Set<Integer> seededRoots = new LinkedHashSet<>();
        Map<Integer, List<SourceLine>> components = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            int root = find(parents, index);
            if (seeds[index]) seededRoots.add(root);
            components.computeIfAbsent(root, ignored -> new ArrayList<>()).add(lines.get(index));
        }

        List<OutputLine> output = new ArrayList<>();
        Set<Integer> emitted = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            int root = find(parents, index);
            SourceLine source = lines.get(index);
            if (!seededRoots.contains(root)) {
                output.add(new OutputLine(source.sourceIndex, source.line));
                continue;
            }
            if (!emitted.add(root)) continue;
            List<SourceLine> component = components.get(root);
            output.add(new OutputLine(
                    minimumSourceIndex(component),
                    mergeComponent(component, preferredLaneKey(component), -1L, -1L)
            ));
        }
        output.sort((left, right) -> {
            int byTime = Long.compare(left.line.startTimeMs, right.line.startTimeMs);
            return byTime != 0 ? byTime : Integer.compare(left.sourceIndex, right.sourceIndex);
        });

        List<LyricsLine> result = new ArrayList<>(output.size());
        for (OutputLine line : output) result.add(line.line);
        return result;
    }

    static LyricsLine mergeComponent(
            List<SourceLine> input,
            String preferredLaneKey,
            long forcedStart,
            long forcedEnd
    ) {
        List<SourceLine> sources = new ArrayList<>();
        if (input != null) {
            for (SourceLine source : input) {
                if (source != null && source.line != null) sources.add(source);
            }
        }
        sources.sort(SOURCE_ORDER);
        if (sources.isEmpty()) {
            return new LyricsLine(0L, 1L, "", Collections.emptyList());
        }

        List<PartEntry> entries = new ArrayList<>();
        for (SourceLine source : sources) entries.addAll(effectiveParts(source));
        entries.sort(PART_ORDER);
        if (entries.isEmpty()) return sources.get(0).line;

        Map<String, List<Lane>> lanesByKey = new LinkedHashMap<>();
        List<Lane> lanes = new ArrayList<>();
        for (PartEntry entry : entries) {
            List<Lane> candidates = lanesByKey.computeIfAbsent(entry.laneKey, ignored -> new ArrayList<>());
            Lane lane = null;
            for (Lane candidate : candidates) {
                if (candidate.endTime <= entry.part.startTimeMs) {
                    lane = candidate;
                    break;
                }
            }
            if (lane == null) {
                lane = new Lane(entry.laneKey);
                candidates.add(lane);
                lanes.add(lane);
            }
            lane.add(entry);
        }

        Lane strongest = strongestLane(lanes, "", true);
        if (strongest == null) strongest = strongestLane(lanes, "", false);
        String normalizedPreferredLaneKey = preferredLaneKey == null || preferredLaneKey.isEmpty()
                ? ""
                : (preferredLaneKey.startsWith("source:") || preferredLaneKey.startsWith("speaker:")
                ? preferredLaneKey
                : "source:" + preferredLaneKey);
        Lane preferred = strongestLane(lanes, normalizedPreferredLaneKey, true);
        Lane leadLane = strongest;
        if (preferred != null && strongest != null && preferred.duration * 2L >= strongest.duration) {
            leadLane = preferred;
        }
        if (leadLane == null) return sources.get(0).line;

        List<Lane> backgroundLanes = new ArrayList<>();
        for (Lane lane : lanes) if (lane != leadLane) backgroundLanes.add(lane);
        backgroundLanes.sort((left, right) -> {
            int byTime = Long.compare(left.startTime, right.startTime);
            return byTime != 0 ? byTime : Integer.compare(left.firstSourceIndex(), right.firstSourceIndex());
        });

        List<LyricsLine.VocalPart> vocalParts = new ArrayList<>();
        vocalParts.add(leadLane.toVocalPart("lead"));
        for (Lane lane : backgroundLanes) vocalParts.add(lane.toVocalPart("background"));

        long start = Long.MAX_VALUE;
        long end = 0L;
        StringBuilder text = new StringBuilder();
        for (LyricsLine.VocalPart part : vocalParts) {
            start = Math.min(start, part.startTimeMs);
            end = Math.max(end, part.endTimeMs);
            if (text.length() > 0) text.append(" / ");
            text.append(part.text);
        }
        if (forcedStart >= 0L) start = forcedStart;
        if (forcedEnd >= start) end = forcedEnd;
        LyricsLine.VocalPart lead = vocalParts.get(0);
        return new LyricsLine(
                start,
                Math.max(start + 1L, end),
                text.toString(),
                Collections.emptyList(),
                lead.speaker,
                lead.speakerColor,
                lead.speakerFallback,
                lead.kind,
                vocalParts
        );
    }

    private static List<PartEntry> effectiveParts(SourceLine source) {
        List<PartEntry> result = new ArrayList<>();
        LyricsLine line = source.line;
        if (!line.vocalParts.isEmpty()) {
            LyricsLine.VocalPart declaredLead = null;
            for (LyricsLine.VocalPart part : line.vocalParts) {
                if ("lead".equals(part.role)) {
                    declaredLead = part;
                    break;
                }
            }
            for (int index = 0; index < line.vocalParts.size(); index++) {
                LyricsLine.VocalPart part = line.vocalParts.get(index);
                if (part == null || part.syllables.isEmpty() || part.endTimeMs <= part.startTimeMs) continue;
                result.add(new PartEntry(
                        source,
                        part,
                        index,
                        partLaneKey(source, part, declaredLead)
                ));
            }
            if (!result.isEmpty()) return result;
        }

        List<LyricsLine.Syllable> syllables = line.syllables;
        if (syllables.isEmpty() && line.endTimeMs > line.startTimeMs && !line.text.isEmpty()) {
            syllables = Collections.singletonList(new LyricsLine.Syllable(
                    line.text,
                    line.startTimeMs,
                    line.endTimeMs
            ));
        }
        if (syllables.isEmpty()) return result;
        LyricsLine.VocalPart lead = new LyricsLine.VocalPart(
                source.sourceKey + "-lead",
                "lead",
                line.speaker,
                line.speakerColor,
                line.speakerFallback,
                line.kind,
                line.text,
                syllables
        );
        result.add(new PartEntry(source, lead, 0, sourceLaneKey(source, lead)));
        return result;
    }

    private static String partLaneKey(
            SourceLine source,
            LyricsLine.VocalPart part,
            LyricsLine.VocalPart declaredLead
    ) {
        if ("lead".equals(part.role)) {
            return sourceLaneKey(source, part);
        }
        String backgroundKey = declaredLead == null || samePresentation(part, declaredLead)
                ? sourceLaneKey(source, part)
                : "speaker:" + presentationKey(part);
        return "background:" + backgroundKey;
    }

    private static String sourceLaneKey(SourceLine source, LyricsLine.VocalPart part) {
        if (!source.laneKey.isEmpty()) return "source:" + source.laneKey;
        String presentation = presentationKey(part);
        return presentation.isEmpty() ? "line:" + source.sourceKey : "speaker:" + presentation;
    }

    private static String presentationKey(LyricsLine.VocalPart part) {
        return part.speaker + '|' + part.speakerColor + '|' + part.speakerFallback;
    }

    private static boolean samePresentation(LyricsLine.VocalPart left, LyricsLine.VocalPart right) {
        return left.speaker.equals(right.speaker)
                && left.speakerColor.equals(right.speakerColor)
                && left.speakerFallback.equals(right.speakerFallback);
    }

    private static long overlapMs(LyricsLine.VocalPart left, LyricsLine.VocalPart right) {
        return Math.max(0L,
                Math.min(left.endTimeMs, right.endTimeMs)
                        - Math.max(left.startTimeMs, right.startTimeMs));
    }

    private static long minimumPartStart(SourceLine source) {
        long start = Long.MAX_VALUE;
        for (PartEntry entry : effectiveParts(source)) start = Math.min(start, entry.part.startTimeMs);
        return start == Long.MAX_VALUE ? source.line.startTimeMs : start;
    }

    private static long maximumPartEnd(SourceLine source) {
        long end = 0L;
        for (PartEntry entry : effectiveParts(source)) end = Math.max(end, entry.part.endTimeMs);
        return end == 0L ? source.line.endTimeMs : end;
    }

    private static String preferredLaneKey(List<SourceLine> sources) {
        Map<String, Long> durations = new LinkedHashMap<>();
        for (SourceLine source : sources) {
            String key = source.laneKey;
            if (key.isEmpty()) continue;
            long duration = Math.max(0L, maximumPartEnd(source) - minimumPartStart(source));
            String normalized = "source:" + key;
            durations.put(normalized, durations.getOrDefault(normalized, 0L) + duration);
        }
        String best = "";
        long bestDuration = -1L;
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            if (entry.getValue() > bestDuration) {
                best = entry.getKey();
                bestDuration = entry.getValue();
            }
        }
        return best;
    }

    private static Lane strongestLane(List<Lane> lanes, String key, boolean requireDeclaredLead) {
        Lane best = null;
        for (Lane lane : lanes) {
            if (key != null && !key.isEmpty() && !key.equals(lane.key)) continue;
            if (requireDeclaredLead && !lane.hasDeclaredLead) continue;
            if (best == null
                    || lane.duration > best.duration
                    || (lane.duration == best.duration && lane.entries.size() > best.entries.size())
                    || (lane.duration == best.duration && lane.entries.size() == best.entries.size()
                    && lane.startTime < best.startTime)
                    || (lane.duration == best.duration && lane.entries.size() == best.entries.size()
                    && lane.startTime == best.startTime && lane.firstSourceIndex() < best.firstSourceIndex())) {
                best = lane;
            }
        }
        return best;
    }

    private static LyricsLine.VocalPart copyWithRole(LyricsLine.VocalPart source, String role) {
        String text = source.text;
        List<LyricsLine.Syllable> syllables = source.syllables;
        if ("background".equals(role)) {
            text = stripParenthesisCharacters(text).trim();
            List<LyricsLine.Syllable> stripped = new ArrayList<>();
            for (LyricsLine.Syllable syllable : syllables) {
                String value = stripParenthesisCharacters(syllable.text);
                if (!value.isEmpty()) {
                    stripped.add(new LyricsLine.Syllable(value, syllable.startTimeMs, syllable.endTimeMs));
                }
            }
            syllables = stripped;
        }
        return new LyricsLine.VocalPart(
                source.id,
                role,
                source.speaker,
                source.speakerColor,
                source.speakerFallback,
                source.kind,
                text,
                syllables,
                source.pronunciationText,
                source.translationText,
                source.furiganaText
        );
    }

    private static String stripParenthesisCharacters(String value) {
        return value == null ? "" : value.replace("(", "").replace(")", "")
                .replace("（", "").replace("）", "");
    }

    private static boolean endsWithWhitespace(String value) {
        return value != null && !value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1));
    }

    private static boolean startsWithWhitespace(String value) {
        return value != null && !value.isEmpty() && Character.isWhitespace(value.charAt(0));
    }

    private static int minimumSourceIndex(List<SourceLine> sources) {
        int result = Integer.MAX_VALUE;
        for (SourceLine source : sources) result = Math.min(result, source.sourceIndex);
        return result;
    }

    private static int find(int[] parents, int index) {
        int root = index;
        while (parents[root] != root) root = parents[root];
        while (parents[index] != index) {
            int parent = parents[index];
            parents[index] = root;
            index = parent;
        }
        return root;
    }

    private static void union(int[] parents, int left, int right) {
        int leftRoot = find(parents, left);
        int rightRoot = find(parents, right);
        if (leftRoot != rightRoot) parents[rightRoot] = leftRoot;
    }

    static final class SourceLine {
        final int sourceIndex;
        final String sourceKey;
        final String laneKey;
        final LyricsLine line;

        SourceLine(int sourceIndex, String sourceKey, String laneKey, LyricsLine line) {
            this.sourceIndex = sourceIndex;
            this.sourceKey = sourceKey == null || sourceKey.isEmpty()
                    ? "line-" + (sourceIndex + 1)
                    : sourceKey;
            this.laneKey = laneKey == null ? "" : laneKey;
            this.line = line;
        }
    }

    private static final class OutputLine {
        final int sourceIndex;
        final LyricsLine line;

        OutputLine(int sourceIndex, LyricsLine line) {
            this.sourceIndex = sourceIndex;
            this.line = line;
        }
    }

    private static final class PartEntry {
        final SourceLine source;
        final LyricsLine.VocalPart part;
        final int partIndex;
        final String laneKey;

        PartEntry(SourceLine source, LyricsLine.VocalPart part, int partIndex, String laneKey) {
            this.source = source;
            this.part = part;
            this.partIndex = partIndex;
            this.laneKey = laneKey;
        }
    }

    private static final class Lane {
        final String key;
        final List<PartEntry> entries = new ArrayList<>();
        long startTime = Long.MAX_VALUE;
        long endTime;
        long duration;
        boolean hasDeclaredLead;

        Lane(String key) {
            this.key = key;
        }

        void add(PartEntry entry) {
            entries.add(entry);
            startTime = Math.min(startTime, entry.part.startTimeMs);
            endTime = Math.max(endTime, entry.part.endTimeMs);
            duration += Math.max(0L, entry.part.endTimeMs - entry.part.startTimeMs);
            hasDeclaredLead = hasDeclaredLead || "lead".equals(entry.part.role);
        }

        int firstSourceIndex() {
            int result = Integer.MAX_VALUE;
            for (PartEntry entry : entries) result = Math.min(result, entry.source.sourceIndex);
            return result;
        }

        LyricsLine.VocalPart toVocalPart(String role) {
            List<PartEntry> ordered = new ArrayList<>(entries);
            ordered.sort(PART_ORDER);
            List<LyricsLine.Syllable> syllables = new ArrayList<>();
            StringBuilder id = new StringBuilder();
            StringBuilder text = new StringBuilder();
            for (PartEntry entry : ordered) {
                LyricsLine.VocalPart part = copyWithRole(entry.part, role);
                if (part.syllables.isEmpty() || part.text.isEmpty()) continue;
                if (id.length() > 0) id.append('+');
                id.append(part.id);
                if (text.length() > 0) text.append(" / ");
                text.append(part.text);
                if (!syllables.isEmpty()) {
                    LyricsLine.Syllable previous = syllables.get(syllables.size() - 1);
                    LyricsLine.Syllable next = part.syllables.get(0);
                    if (!endsWithWhitespace(previous.text) && !startsWithWhitespace(next.text)) {
                        syllables.add(new LyricsLine.Syllable(" ", next.startTimeMs, next.startTimeMs));
                    }
                }
                syllables.addAll(part.syllables);
            }
            LyricsLine.VocalPart first = ordered.get(0).part;
            return new LyricsLine.VocalPart(
                    id.toString(),
                    role,
                    first.speaker,
                    first.speakerColor,
                    first.speakerFallback,
                    first.kind,
                    text.toString(),
                    syllables
            );
        }
    }

    private static final Comparator<SourceLine> SOURCE_ORDER = (left, right) -> {
        int byTime = Long.compare(left.line.startTimeMs, right.line.startTimeMs);
        return byTime != 0 ? byTime : Integer.compare(left.sourceIndex, right.sourceIndex);
    };

    private static final Comparator<PartEntry> PART_ORDER = (left, right) -> {
        int byTime = Long.compare(left.part.startTimeMs, right.part.startTimeMs);
        if (byTime != 0) return byTime;
        int bySource = Integer.compare(left.source.sourceIndex, right.source.sourceIndex);
        return bySource != 0 ? bySource : Integer.compare(left.partIndex, right.partIndex);
    };
}
