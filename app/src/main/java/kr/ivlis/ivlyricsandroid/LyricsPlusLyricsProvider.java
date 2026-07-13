package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

final class LyricsPlusLyricsProvider {
    static final String PROVIDER_ID = "lyricsplus";
    static final String AUTHOR = "default";
    static final String PROJECT_URL = "https://github.com/ibratabian17/lyricsplus";

    private static final String[] ENCODED_API_BASES = new String[]{
            "YUhSMGNITTZMeTlzZVhKcFkzTndiSFZ6TG5CeWFtdDBiR0V1YlhrdWFXUT0=",
            "YUhSMGNITTZMeTlzZVhKcFkzTXVaMlZsYTJWa0xuZDBaZz09"
    };
    private static final String API_PATH = "/v2/lyrics/get";
    private static final String ATTRIBUTION = "Lyrics from LyricsPlus · " + PROJECT_URL;
    private static final int REQUEST_TIMEOUT_MS = 10_000;
    private static final int SYLLABLE_TIMING_TOLERANCE_MS = 1_500;
    private static final int MAX_PARALLEL_SOURCE_LINES = 4;
    private static final long MAX_PARALLEL_SEGMENT_DELAY_MS = 16L;
    private static final double LONG_LINE_TRIGGER_WIDTH = 22.0;
    private static final double LONG_LINE_HARD_WIDTH = 26.0;
    private static final double LONG_LINE_MIN_WIDTH = 6.0;
    private static final long LONG_LINE_MIN_DURATION_MS = 500L;
    private static final int LONG_LINE_MAX_SEGMENTS = 4;
    private static final AtomicInteger NEXT_MIRROR = new AtomicInteger();
    private static final Pattern BACKGROUND_PARENTHESES_PATTERN = Pattern.compile("[()（）]");
    private static final Pattern INLINE_WHITESPACE_PATTERN = Pattern.compile("[\\r\\n\\t\\f\\u000B ]+");

    private static final SpeakerPresentation[] SPEAKER_PALETTE = new SpeakerPresentation[]{
            new SpeakerPresentation("CUSTOM", "#a8ccff", "MALE 1"),
            new SpeakerPresentation("CUSTOM", "#ffb8c7", "FEMALE 1"),
            new SpeakerPresentation("CUSTOM", "#e4d8ff", "DUET 1"),
            new SpeakerPresentation("CUSTOM", "#9ae8d4", "MALE 2"),
            new SpeakerPresentation("CUSTOM", "#ffd6b3", "FEMALE 2"),
            new SpeakerPresentation("CUSTOM", "#d6e4ff", "DUET 2"),
            new SpeakerPresentation("CUSTOM", "#bfe8ff", "MALE 3"),
            new SpeakerPresentation("CUSTOM", "#f6c8ff", "FEMALE 3"),
            new SpeakerPresentation("CUSTOM", "#ffddf2", "DUET 3")
    };

    interface Logger {
        void write(String message);
    }

    private LyricsPlusLyricsProvider() {
    }

    static LyricsResult fetch(
            TrackSnapshot track,
            String isrc,
            String spotifyTrackId,
            Logger logger
    ) throws Exception {
        Variants variants = fetchVariants(track, isrc, spotifyTrackId, logger);
        return variants == null ? null : variants.best();
    }

    static Variants fetchVariants(
            TrackSnapshot track,
            String isrc,
            String spotifyTrackId,
            Logger logger
    ) throws Exception {
        if (track == null || !track.hasUsableMetadata()) {
            return null;
        }
        JSONObject payload = fetchPayload(track, isrc, logger);
        if (payload == null) {
            logger.write("lyricsplus: no lyrics found");
            return null;
        }
        Variants variants = parsePayloadVariants(payload, track.durationMs, isrc, spotifyTrackId);
        if (variants == null || variants.best() == null) {
            logger.write("lyricsplus: response has no renderable lyrics");
            return null;
        }
        LyricsResult best = variants.best();
        logger.write("lyricsplus loaded: variants=" + variants.availableTypes()
                + " / bestLines=" + best.lines.size()
                + " / vocalParts=" + countVocalParts(best.lines));
        return variants;
    }

    static LyricsResult parsePayload(JSONObject payload, long durationMs, String isrc, String spotifyTrackId) {
        Variants variants = parsePayloadVariants(payload, durationMs, isrc, spotifyTrackId);
        return variants == null ? null : variants.best();
    }

    static Variants parsePayloadVariants(JSONObject payload, long durationMs, String isrc, String spotifyTrackId) {
        if (!isUsablePayload(payload)) return null;
        JSONArray sourceLyrics = payload.optJSONArray("lyrics");
        JSONObject metadata = payload.optJSONObject("metadata");
        JSONObject agents = metadata == null ? null : metadata.optJSONObject("agents");
        Map<String, Integer> singerOrder = new LinkedHashMap<>();
        List<ParsedLine> parsed = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < sourceLyrics.length(); sourceIndex++) {
            ParsedLine line = parseLine(sourceLyrics.optJSONObject(sourceIndex), sourceIndex, singerOrder, agents);
            if (line != null) parsed.add(line);
        }
        if (parsed.isEmpty()) return null;

        List<ParsedLine> timed = new ArrayList<>();
        for (ParsedLine line : parsed) if (line.startTimeMs >= 0L) timed.add(line);
        timed.sort((left, right) -> {
            int byTime = Long.compare(left.startTimeMs, right.startTimeMs);
            return byTime != 0 ? byTime : Integer.compare(left.sourceIndex, right.sourceIndex);
        });
        for (int index = 0; index < timed.size(); index++) {
            ParsedLine line = timed.get(index);
            if (line.endTimeMs <= line.startTimeMs) {
                long nextStart = index + 1 < timed.size() ? timed.get(index + 1).startTimeMs : -1L;
                line.endTimeMs = nextStart > line.startTimeMs
                        ? nextStart
                        : Math.max(line.startTimeMs + 1L,
                        durationMs > line.startTimeMs ? durationMs : line.startTimeMs + 3_000L);
            }
        }

        String payloadType = normalizeDisplayText(payload.optString("type", "")).toLowerCase(Locale.ROOT);
        boolean wordType = "word".equals(payloadType);
        boolean lineType = "line".equals(payloadType);
        boolean plainType = "none".equals(payloadType)
                || "plain".equals(payloadType)
                || "unsynced".equals(payloadType);
        boolean inferType = !wordType && !lineType && !plainType;
        boolean completeTiming = timed.size() == parsed.size();
        boolean completeWordTiming = completeTiming && (wordType || inferType);
        if (completeWordTiming) {
            for (ParsedLine line : timed) {
                if (!line.hasWordTiming) {
                    completeWordTiming = false;
                    break;
                }
            }
        }

        List<LyricsLine> karaokeLines = completeWordTiming
                ? splitLongSoloLines(groupParallelLines(timed))
                : Collections.emptyList();
        List<LyricsLine> syncedLines = new ArrayList<>();
        if (completeTiming && !plainType) {
            for (ParsedLine line : timed) syncedLines.add(line.toSyncedLine());
        }
        List<ParsedLine> sourceOrder = new ArrayList<>(parsed);
        sourceOrder.sort((left, right) -> Integer.compare(left.sourceIndex, right.sourceIndex));
        List<LyricsLine> plainLines = new ArrayList<>();
        for (ParsedLine line : sourceOrder) {
            plainLines.add(new LyricsLine(0L, 0L, line.text, Collections.emptyList(), line.presentation.speaker,
                    line.presentation.color, line.presentation.fallback, "vocal", Collections.emptyList()));
        }

        LyricsResult karaoke = karaokeLines.isEmpty() ? null : new LyricsResult(
                karaokeLines, "LyricsPlus karaoke", ATTRIBUTION, true, isrc, spotifyTrackId
        );
        LyricsResult synced = syncedLines.isEmpty() ? null : new LyricsResult(
                syncedLines, "LyricsPlus synced", ATTRIBUTION, false, isrc, spotifyTrackId
        );
        LyricsResult plain = plainLines.isEmpty() ? null : new LyricsResult(
                plainLines, "LyricsPlus plain", ATTRIBUTION, false, isrc, spotifyTrackId
        );
        Variants variants = new Variants(karaoke, synced, plain);
        return variants.best() == null ? null : variants;
    }

    private static JSONObject fetchPayload(TrackSnapshot track, String isrc, Logger logger) throws Exception {
        List<String> bases = decodedApiBases();
        if (bases.isEmpty()) return null;
        int start = Math.floorMod(NEXT_MIRROR.getAndIncrement(), bases.size());
        Exception lastError = null;
        boolean allNotFound = true;
        for (int offset = 0; offset < bases.size(); offset++) {
            int mirror = (start + offset) % bases.size();
            String requestUrl = buildLyricsUrl(bases.get(mirror), track, isrc);
            try {
                HttpResult response = get(requestUrl);
                JSONObject body = parseJson(response.body);
                if (response.status >= 200 && response.status < 300 && isUsablePayload(body)) {
                    logger.write("lyricsplus: mirror #" + (mirror + 1) + " matched");
                    return body;
                }
                boolean notFound = response.status == 404
                        || (response.status >= 200 && response.status < 300 && body != null && body.optJSONArray("lyrics") != null);
                allNotFound &= notFound;
                lastError = new IllegalStateException("mirror #" + (mirror + 1) + " response " + response.status);
            } catch (Exception error) {
                allNotFound = false;
                lastError = error;
            }
        }
        if (allNotFound) return null;
        throw lastError == null ? new IllegalStateException("LyricsPlus request failed") : lastError;
    }

    private static List<String> decodedApiBases() {
        List<String> result = new ArrayList<>();
        for (String encoded : ENCODED_API_BASES) {
            try {
                byte[] once = Base64.getDecoder().decode(encoded);
                byte[] twice = Base64.getDecoder().decode(new String(once, StandardCharsets.UTF_8));
                String decoded = new String(twice, StandardCharsets.UTF_8).trim();
                if (!decoded.isEmpty()) result.add(decoded);
            } catch (Exception ignored) {
                // Invalid bundled endpoint is ignored so the other mirror can still work.
            }
        }
        return result;
    }

    private static String buildLyricsUrl(String base, TrackSnapshot track, String isrc) {
        Map<String, String> params = new LinkedHashMap<>();
        String normalizedIsrc = TrackSnapshot.normalizeIsrc(isrc);
        if (!normalizedIsrc.isEmpty()) {
            params.put("isrc", normalizedIsrc);
        } else {
            params.put("title", track.title);
            params.put("artist", track.artist);
            if (!track.album.isEmpty() && !"undefined".equalsIgnoreCase(track.album)) params.put("album", track.album);
            if (track.durationMs > 0L) params.put("duration", String.format(Locale.ROOT, "%.3f", track.durationMs / 1000.0));
        }
        StringBuilder url = new StringBuilder(base);
        if (url.length() > 0 && url.charAt(url.length() - 1) == '/') url.setLength(url.length() - 1);
        url.append(API_PATH).append('?');
        int index = 0;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (index++ > 0) url.append('&');
            url.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return url.toString();
    }

    private static HttpResult get(String requestUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
        connection.setReadTimeout(REQUEST_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
        try {
            return new HttpResult(status, readUtf8(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static String readUtf8(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static JSONObject parseJson(String body) {
        try {
            return new JSONObject(body == null ? "" : body.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isUsablePayload(JSONObject payload) {
        if (payload == null || isTruthyJsonValue(payload.opt("error"))) return false;
        JSONArray lyrics = payload.optJSONArray("lyrics");
        if (lyrics == null || lyrics.length() == 0) return false;
        for (int index = 0; index < lyrics.length(); index++) {
            JSONObject line = lyrics.optJSONObject(index);
            if (line == null) continue;
            if (!normalizeDisplayText(line.optString("text", "")).isEmpty()) return true;
            JSONArray syllables = line.optJSONArray("syllabus");
            if (syllables == null) continue;
            for (int syllableIndex = 0; syllableIndex < syllables.length(); syllableIndex++) {
                JSONObject syllable = syllables.optJSONObject(syllableIndex);
                if (syllable != null && !normalizeInlineText(syllable.optString("text", "")).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTruthyJsonValue(Object value) {
        if (value == null || value == JSONObject.NULL) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0.0;
        if (value instanceof String) return !((String) value).trim().isEmpty();
        return true;
    }

    private static ParsedLine parseLine(
            JSONObject source,
            int sourceIndex,
            Map<String, Integer> singerOrder,
            JSONObject agents
    ) {
        if (source == null) return null;
        Long rawStart = finiteMs(source.opt("time"));
        Long rawDuration = positiveMs(source.opt("duration"));
        long rawEnd = rawStart != null && rawDuration != null ? rawStart + rawDuration : -1L;
        List<ParsedSyllable> rawSyllables = new ArrayList<>();
        JSONArray syllabus = source.optJSONArray("syllabus");
        if (syllabus != null) {
            for (int index = 0; index < syllabus.length(); index++) {
                ParsedSyllable syllable = parseSyllable(syllabus.optJSONObject(index));
                if (syllable != null && withinLine(syllable, rawStart, rawEnd)) rawSyllables.add(syllable);
            }
        }
        List<LyricsLine.Syllable> leadSyllables = new ArrayList<>();
        List<LyricsLine.Syllable> backgroundSyllables = new ArrayList<>();
        for (ParsedSyllable syllable : rawSyllables) {
            (syllable.background ? backgroundSyllables : leadSyllables).add(syllable.value);
        }
        String sourceText = normalizeDisplayText(source.optString("text", ""));
        String leadText = joinSyllables(leadSyllables);
        String backgroundText = stripBackgroundParentheses(joinSyllables(backgroundSyllables));
        String text = sourceText.isEmpty() ? joinSyllables(toSyllables(rawSyllables)) : sourceText;
        if (text.isEmpty()) return null;

        JSONObject element = source.optJSONObject("element");
        String lineKey = element == null ? "line-" + (sourceIndex + 1) : element.optString("key", "line-" + (sourceIndex + 1));
        String singer = element == null ? "" : element.optString("singer", "").trim();
        SpeakerPresentation presentation = speakerPresentation(singer, singerOrder, agents);

        long start = rawStart == null ? -1L : rawStart;
        long end = rawEnd;
        for (ParsedSyllable syllable : rawSyllables) {
            start = start < 0L ? syllable.value.startTimeMs : Math.min(start, syllable.value.startTimeMs);
            end = Math.max(end, syllable.value.endTimeMs);
        }

        List<LyricsLine.VocalPart> inlineParts = new ArrayList<>();
        if (!leadSyllables.isEmpty() && !backgroundSyllables.isEmpty()) {
            inlineParts.add(vocalPart(lineKey + "-lead", "lead", singer, presentation, leadText, leadSyllables));
            inlineParts.add(vocalPart(lineKey + "-background-1", "background", singer, presentation,
                    backgroundText, stripBackgroundSyllableParentheses(backgroundSyllables)));
        }
        return new ParsedLine(sourceIndex, lineKey, singer, Math.max(-1L, start), Math.max(-1L, end), text,
                leadSyllables.isEmpty() ? toSyllables(rawSyllables) : leadSyllables,
                inlineParts, presentation, !rawSyllables.isEmpty());
    }

    private static List<LyricsLine> groupParallelLines(List<ParsedLine> lines) {
        int count = lines.size();
        int[] parents = new int[count];
        boolean[] parallelSeed = new boolean[count];
        for (int index = 0; index < count; index++) parents[index] = index;
        for (int leftIndex = 0; leftIndex < count; leftIndex++) {
            ParsedLine left = lines.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < count; rightIndex++) {
                ParsedLine right = lines.get(rightIndex);
                if (right.startTimeMs >= left.endTimeMs) break;
                long overlap = Math.min(left.endTimeMs, right.endTimeMs) - Math.max(left.startTimeMs, right.startTimeMs);
                if (overlap <= 0L) continue;
                union(parents, leftIndex, rightIndex);
                if (overlap >= 30L
                        && !left.singer.isEmpty()
                        && !right.singer.isEmpty()
                        && !left.singer.equals(right.singer)) {
                    parallelSeed[leftIndex] = true;
                    parallelSeed[rightIndex] = true;
                }
            }
        }

        Set<Integer> parallelRoots = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            if (parallelSeed[index]) parallelRoots.add(find(parents, index));
        }
        Map<Integer, List<ParsedLine>> components = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            int root = find(parents, index);
            components.computeIfAbsent(root, ignored -> new ArrayList<>()).add(lines.get(index));
        }

        List<LyricsLine> result = new ArrayList<>();
        Set<Integer> emitted = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            int root = find(parents, index);
            if (!parallelRoots.contains(root)) {
                result.add(lines.get(index).toKaraokeLine());
                continue;
            }
            if (!emitted.add(root)) continue;
            List<ParsedLine> component = components.get(root);
            component.sort((left, right) -> {
                int byTime = Long.compare(left.startTimeMs, right.startTimeMs);
                return byTime != 0 ? byTime : Integer.compare(left.sourceIndex, right.sourceIndex);
            });
            result.addAll(createParallelSegments(component));
        }
        result.sort((left, right) -> Long.compare(left.startTimeMs, right.startTimeMs));
        return result;
    }

    private static List<LyricsLine> createParallelSegments(List<ParsedLine> sourceLines) {
        List<ParsedLine> remaining = new ArrayList<>(sourceLines);
        List<LyricsLine> result = new ArrayList<>();
        String preferredSinger = preferredLeadSinger(sourceLines);
        long forcedStart = -1L;
        int guard = 0;
        while (sourceLineCount(remaining) > MAX_PARALLEL_SOURCE_LINES && guard++ < sourceLines.size() * 2) {
            ParallelSplit split = findParallelSplit(remaining, forcedStart);
            if (split == null) {
                // Keeping every timed syllable is safer than cutting a vocal at an unsafe boundary.
                return Collections.singletonList(createParallelLine(sourceLines, preferredSinger, -1L, -1L));
            }
            result.add(createParallelLine(split.left, preferredSinger, forcedStart, split.leftEndTime));
            remaining = split.right;
            forcedStart = split.nextStartTime;
        }
        if (!remaining.isEmpty()) {
            result.add(createParallelLine(remaining, preferredSinger, forcedStart, -1L));
        }
        return result;
    }

    private static ParallelSplit findParallelSplit(List<ParsedLine> lines, long forcedStart) {
        List<ParsedLine> ordered = new ArrayList<>(lines);
        ordered.sort((left, right) -> {
            int byTime = Long.compare(left.startTimeMs, right.startTimeMs);
            return byTime != 0 ? byTime : Integer.compare(left.sourceIndex, right.sourceIndex);
        });
        List<String> sourceKeys = new ArrayList<>();
        long nominalBoundary = -1L;
        for (ParsedLine line : ordered) {
            if (!sourceKeys.contains(line.lineKey)) {
                sourceKeys.add(line.lineKey);
                if (sourceKeys.size() == MAX_PARALLEL_SOURCE_LINES + 1) {
                    nominalBoundary = line.startTimeMs;
                    break;
                }
            }
        }
        if (nominalBoundary < 0L) return null;

        Set<Long> candidateSet = new LinkedHashSet<>();
        candidateSet.add(nominalBoundary);
        Set<String> firstSourceKeys = new LinkedHashSet<>(sourceKeys.subList(0, MAX_PARALLEL_SOURCE_LINES));
        for (ParsedLine line : ordered) {
            if (!firstSourceKeys.contains(line.lineKey)) continue;
            for (LyricsLine.Syllable syllable : lineSyllables(line)) {
                if (syllable.startTimeMs > forcedStart && syllable.startTimeMs <= nominalBoundary) {
                    candidateSet.add(syllable.startTimeMs);
                }
                if (syllable.endTimeMs > forcedStart && syllable.endTimeMs <= nominalBoundary) {
                    candidateSet.add(syllable.endTimeMs);
                }
            }
        }
        List<Long> candidates = new ArrayList<>(candidateSet);
        final long targetBoundary = nominalBoundary;
        candidates.sort((left, right) -> {
            int byDistance = Long.compare(Math.abs(left - targetBoundary), Math.abs(right - targetBoundary));
            return byDistance != 0 ? byDistance : Long.compare(right, left);
        });

        ParallelSplit best = null;
        for (long candidate : candidates) {
            if (candidate <= forcedStart) continue;
            List<ParsedLine> left = new ArrayList<>();
            List<ParsedLine> right = new ArrayList<>();
            for (ParsedLine line : ordered) {
                ParsedLine leftFragment = sliceParsedLine(line, candidate, true);
                ParsedLine rightFragment = sliceParsedLine(line, candidate, false);
                if (leftFragment != null) left.add(leftFragment);
                if (rightFragment != null) right.add(rightFragment);
            }
            int leftSourceCount = sourceLineCount(left);
            if (leftSourceCount < 2
                    || leftSourceCount > MAX_PARALLEL_SOURCE_LINES
                    || right.isEmpty()
                    || singerCount(left) < 2) {
                continue;
            }
            long leftEnd = maximumEndTime(left);
            long rightStart = minimumStartTime(right);
            long nextStart = Math.max(leftEnd, rightStart);
            long maximumDelay = 0L;
            boolean losesSyllable = false;
            for (ParsedLine line : right) {
                for (LyricsLine.Syllable syllable : lineSyllables(line)) {
                    if (syllable.startTimeMs < nextStart) {
                        if (syllable.endTimeMs <= nextStart) {
                            losesSyllable = true;
                            break;
                        }
                        maximumDelay = Math.max(maximumDelay, nextStart - syllable.startTimeMs);
                    }
                }
                if (losesSyllable) break;
            }
            if (losesSyllable || maximumDelay > MAX_PARALLEL_SEGMENT_DELAY_MS) continue;

            ParallelSplit split = new ParallelSplit(
                    left,
                    right,
                    leftEnd,
                    nextStart,
                    leftSourceCount,
                    maximumDelay,
                    Math.abs(candidate - nominalBoundary)
            );
            if (best == null || split.betterThan(best)) best = split;
        }
        return best;
    }

    private static ParsedLine sliceParsedLine(ParsedLine source, long boundary, boolean takeLeft) {
        List<LyricsLine.Syllable> slicedLead = sliceSyllables(source.syllables, boundary, takeLeft);
        List<LyricsLine.VocalPart> slicedParts = new ArrayList<>();
        for (LyricsLine.VocalPart part : source.inlineVocalParts) {
            LyricsLine.VocalPart sliced = sliceVocalPart(part, boundary, takeLeft);
            if (sliced != null) slicedParts.add(sliced);
        }
        if (slicedParts.isEmpty() && slicedLead.isEmpty()) return null;

        if (!slicedParts.isEmpty()) {
            int leadIndex = -1;
            for (int index = 0; index < slicedParts.size(); index++) {
                if ("lead".equals(slicedParts.get(index).role)) {
                    leadIndex = index;
                    break;
                }
            }
            if (leadIndex < 0) {
                slicedParts.set(0, withVocalRole(slicedParts.get(0), "lead"));
                leadIndex = 0;
            }
            slicedLead = new ArrayList<>(slicedParts.get(leadIndex).syllables);
            if (slicedParts.size() == 1) slicedParts.clear();
        }
        if (slicedLead.isEmpty()) return null;

        List<LyricsLine.Syllable> all = slicedParts.isEmpty() ? slicedLead : partSyllables(slicedParts);
        long start = minimumSyllableStart(all);
        long end = maximumSyllableEnd(all);
        String text;
        if (slicedParts.isEmpty()) {
            text = joinSyllables(slicedLead);
        } else {
            StringBuilder joined = new StringBuilder();
            for (LyricsLine.VocalPart part : slicedParts) {
                if (joined.length() > 0) joined.append(' ');
                joined.append(part.text);
            }
            text = normalizeDisplayText(joined.toString());
        }
        return new ParsedLine(
                source.sourceIndex,
                source.lineKey,
                source.singer,
                start,
                end,
                text,
                slicedLead,
                slicedParts,
                source.presentation,
                source.hasWordTiming
        );
    }

    private static List<LyricsLine.Syllable> sliceSyllables(
            List<LyricsLine.Syllable> syllables,
            long boundary,
            boolean takeLeft
    ) {
        List<LyricsLine.Syllable> result = new ArrayList<>();
        for (LyricsLine.Syllable syllable : syllables) {
            long midpoint = syllable.startTimeMs + ((syllable.endTimeMs - syllable.startTimeMs) / 2L);
            if ((midpoint <= boundary) == takeLeft) result.add(syllable);
        }
        return result;
    }

    private static LyricsLine.VocalPart sliceVocalPart(
            LyricsLine.VocalPart source,
            long boundary,
            boolean takeLeft
    ) {
        List<LyricsLine.Syllable> syllables = sliceSyllables(source.syllables, boundary, takeLeft);
        if (syllables.isEmpty()) return null;
        return new LyricsLine.VocalPart(
                source.id,
                source.role,
                source.speaker,
                source.speakerColor,
                source.speakerFallback,
                source.kind,
                joinSyllables(syllables),
                syllables
        );
    }

    private static LyricsLine createParallelLine(
            List<ParsedLine> sourceLines,
            String preferredSinger,
            long forcedStart,
            long forcedEnd
    ) {
        List<ParallelPart> leadEntries = new ArrayList<>();
        List<ParallelPart> explicitBackgroundEntries = new ArrayList<>();
        for (ParsedLine line : sourceLines) {
            if (!line.inlineVocalParts.isEmpty()) {
                for (LyricsLine.VocalPart part : line.inlineVocalParts) {
                    ParallelPart entry = new ParallelPart(part, line);
                    if ("lead".equals(part.role)) leadEntries.add(entry);
                    else explicitBackgroundEntries.add(entry);
                }
            } else {
                leadEntries.add(new ParallelPart(vocalPart(
                        line.lineKey + "-lead",
                        "lead",
                        line.singer,
                        line.presentation,
                        line.text,
                        line.syllables
                ), line));
            }
        }
        leadEntries.sort((left, right) -> {
            int byTime = Long.compare(left.part.startTimeMs, right.part.startTimeMs);
            return byTime != 0 ? byTime : Integer.compare(left.source.sourceIndex, right.source.sourceIndex);
        });

        Map<String, List<ParallelLane>> singerLanes = new LinkedHashMap<>();
        List<ParallelLane> lanes = new ArrayList<>();
        for (ParallelPart entry : leadEntries) {
            String singerKey = entry.source.singer.isEmpty()
                    ? "line:" + entry.source.lineKey
                    : entry.source.singer;
            List<ParallelLane> candidates = singerLanes.computeIfAbsent(singerKey, ignored -> new ArrayList<>());
            ParallelLane lane = null;
            for (ParallelLane candidate : candidates) {
                if (candidate.endTime <= entry.part.startTimeMs) {
                    lane = candidate;
                    break;
                }
            }
            if (lane == null) {
                lane = new ParallelLane(singerKey, entry.source.singer);
                candidates.add(lane);
                lanes.add(lane);
            }
            lane.add(entry);
        }

        ParallelLane strongest = strongestLane(lanes, "");
        ParallelLane preferred = strongestLane(lanes, preferredSinger);
        ParallelLane leadLane = strongest;
        if (preferred != null && strongest != null && preferred.duration * 2L >= strongest.duration) {
            leadLane = preferred;
        }
        if (leadLane == null) {
            ParsedLine fallback = sourceLines.get(0);
            return fallback.toKaraokeLine();
        }

        List<ParallelLane> backgroundLanes = new ArrayList<>();
        for (ParallelLane lane : lanes) if (lane != leadLane) backgroundLanes.add(lane);
        backgroundLanes.sort((left, right) -> {
            int byTime = Long.compare(left.startTime, right.startTime);
            return byTime != 0 ? byTime : Integer.compare(left.firstSourceIndex(), right.firstSourceIndex());
        });

        List<LyricsLine.VocalPart> parts = new ArrayList<>();
        parts.add(leadLane.toVocalPart("lead"));
        for (ParallelLane lane : backgroundLanes) parts.add(lane.toVocalPart("background"));
        explicitBackgroundEntries.sort((left, right) -> Long.compare(
                left.part.startTimeMs,
                right.part.startTimeMs
        ));
        for (ParallelPart entry : explicitBackgroundEntries) {
            parts.add(withVocalRole(entry.part, "background"));
        }

        StringBuilder text = new StringBuilder();
        long start = Long.MAX_VALUE;
        long end = 0L;
        for (LyricsLine.VocalPart part : parts) {
            if (text.length() > 0) text.append(" / ");
            text.append(part.text);
            start = Math.min(start, part.startTimeMs);
            end = Math.max(end, part.endTimeMs);
        }
        if (forcedStart >= 0L) start = forcedStart;
        if (forcedEnd >= start) end = forcedEnd;
        SpeakerPresentation presentation = leadLane.entries.get(0).source.presentation;
        return new LyricsLine(start, Math.max(start + 1L, end), text.toString(), Collections.emptyList(),
                presentation.speaker, presentation.color, presentation.fallback, "vocal", parts);
    }

    private static ParallelLane strongestLane(List<ParallelLane> lanes, String singer) {
        ParallelLane best = null;
        for (ParallelLane lane : lanes) {
            if (singer != null && !singer.isEmpty() && !singer.equals(lane.singer)) continue;
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

    private static LyricsLine.VocalPart withVocalRole(LyricsLine.VocalPart source, String role) {
        List<LyricsLine.Syllable> syllables = source.syllables;
        String text = source.text;
        if ("background".equals(role)) {
            syllables = stripBackgroundSyllableParentheses(syllables);
            text = stripBackgroundParentheses(text);
        }
        return new LyricsLine.VocalPart(
                source.id,
                role,
                source.speaker,
                source.speakerColor,
                source.speakerFallback,
                source.kind,
                text,
                syllables
        );
    }

    private static String preferredLeadSinger(List<ParsedLine> lines) {
        Map<String, Long> durations = new LinkedHashMap<>();
        for (ParsedLine line : lines) {
            if (line.singer.isEmpty()) continue;
            long duration = Math.max(0L, line.endTimeMs - line.startTimeMs);
            durations.put(line.singer, durations.getOrDefault(line.singer, 0L) + duration);
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

    private static int sourceLineCount(List<ParsedLine> lines) {
        Set<String> keys = new LinkedHashSet<>();
        for (ParsedLine line : lines) keys.add(line.lineKey);
        return keys.size();
    }

    private static int singerCount(List<ParsedLine> lines) {
        Set<String> singers = new LinkedHashSet<>();
        for (ParsedLine line : lines) if (!line.singer.isEmpty()) singers.add(line.singer);
        return singers.size();
    }

    private static List<LyricsLine.Syllable> lineSyllables(ParsedLine line) {
        return line.inlineVocalParts.isEmpty() ? line.syllables : partSyllables(line.inlineVocalParts);
    }

    private static List<LyricsLine.Syllable> partSyllables(List<LyricsLine.VocalPart> parts) {
        List<LyricsLine.Syllable> result = new ArrayList<>();
        for (LyricsLine.VocalPart part : parts) result.addAll(part.syllables);
        return result;
    }

    private static long minimumStartTime(List<ParsedLine> lines) {
        long result = Long.MAX_VALUE;
        for (ParsedLine line : lines) result = Math.min(result, line.startTimeMs);
        return result;
    }

    private static long maximumEndTime(List<ParsedLine> lines) {
        long result = 0L;
        for (ParsedLine line : lines) result = Math.max(result, line.endTimeMs);
        return result;
    }

    private static long minimumSyllableStart(List<LyricsLine.Syllable> syllables) {
        long result = Long.MAX_VALUE;
        for (LyricsLine.Syllable syllable : syllables) result = Math.min(result, syllable.startTimeMs);
        return result;
    }

    private static long maximumSyllableEnd(List<LyricsLine.Syllable> syllables) {
        long result = 0L;
        for (LyricsLine.Syllable syllable : syllables) result = Math.max(result, syllable.endTimeMs);
        return result;
    }

    private static List<LyricsLine> splitLongSoloLines(List<LyricsLine> lines) {
        List<LyricsLine> result = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            LyricsLine line = lines.get(lineIndex);
            LyricsLine previousLine = lineIndex > 0 ? lines.get(lineIndex - 1) : null;
            LyricsLine nextLine = lineIndex + 1 < lines.size() ? lines.get(lineIndex + 1) : null;
            if (line == null || !line.vocalParts.isEmpty() || line.syllables.size() < 2
                    || !canSplitLongSoloLine(line, previousLine, nextLine)) {
                result.add(line);
                continue;
            }
            Map<Integer, BoundaryInfo> boundaries = longLineBoundaries(line.syllables);
            if (boundaries.isEmpty()) {
                result.add(line);
                continue;
            }
            List<Integer> cuts = chooseLongLineCuts(line.syllables, boundaries);
            if (cuts.isEmpty()) {
                result.add(line);
                continue;
            }
            int start = 0;
            for (int cut : cuts) {
                result.add(lineFragment(line, start, cut, start == 0, false));
                start = cut;
            }
            result.add(lineFragment(line, start, line.syllables.size(), start == 0, true));
        }
        return result;
    }

    private static boolean canSplitLongSoloLine(
            LyricsLine line,
            LyricsLine previousLine,
            LyricsLine nextLine
    ) {
        String joined = joinSyllables(line.syllables);
        double totalWidth = measureDisplayWidth(line.text);
        if (line.text.isEmpty() || !line.text.equals(joined) || totalWidth <= LONG_LINE_TRIGGER_WIDTH) return false;
        LyricsLine.Syllable previous = null;
        for (LyricsLine.Syllable syllable : line.syllables) {
            if (normalizeInlineText(syllable.text).isEmpty()
                    || syllable.endTimeMs < syllable.startTimeMs
                    || measureDisplayWidth(syllable.text) > LONG_LINE_HARD_WIDTH
                    || (previous != null && (syllable.startTimeMs <= previous.startTimeMs
                    || syllable.startTimeMs < previous.endTimeMs))) {
                return false;
            }
            previous = syllable;
        }
        long firstStart = line.syllables.get(0).startTimeMs;
        long lastEnd = line.syllables.get(line.syllables.size() - 1).endTimeMs;
        return (previousLine == null || previousLine.endTimeMs <= firstStart)
                && (nextLine == null || nextLine.startTimeMs >= lastEnd);
    }

    private static List<Integer> chooseLongLineCuts(
            List<LyricsLine.Syllable> syllables,
            Map<Integer, BoundaryInfo> boundaries
    ) {
        double totalWidth = measureDisplayWidth(joinSyllables(syllables));
        int minimumSegments = Math.max(2, (int) Math.ceil(totalWidth / LONG_LINE_HARD_WIDTH));
        int maximumSegments = Math.min(
                LONG_LINE_MAX_SEGMENTS,
                Math.min(boundaries.size() + 1, syllables.size())
        );
        for (int segmentCount = minimumSegments; segmentCount <= maximumSegments; segmentCount++) {
            SplitPlan plan = findLongLineSplitPlan(
                    syllables,
                    boundaries,
                    0,
                    segmentCount,
                    totalWidth / segmentCount,
                    new HashMap<>()
            );
            if (plan != null) return plan.cuts;
        }
        return Collections.emptyList();
    }

    private static SplitPlan findLongLineSplitPlan(
            List<LyricsLine.Syllable> syllables,
            Map<Integer, BoundaryInfo> boundaries,
            int start,
            int remainingSegments,
            double targetWidth,
            Map<String, SplitPlan> memo
    ) {
        String memoKey = start + ":" + remainingSegments;
        if (memo.containsKey(memoKey)) return memo.get(memoKey);
        if (remainingSegments == 1) {
            SplitPlan tail = validLongLineFragment(syllables, start, syllables.size(), targetWidth)
                    ? new SplitPlan(square(measureDisplayWidth(joinSyllables(
                    syllables.subList(start, syllables.size())
            )) - targetWidth), Collections.emptyList())
                    : null;
            memo.put(memoKey, tail);
            return tail;
        }

        SplitPlan best = null;
        int maximumEnd = syllables.size() - (remainingSegments - 1);
        for (int end = start + 1; end <= maximumEnd; end++) {
            BoundaryInfo boundary = boundaries.get(end);
            if (boundary == null || !validLongLineFragment(syllables, start, end, targetWidth)) continue;
            SplitPlan remaining = findLongLineSplitPlan(
                    syllables,
                    boundaries,
                    end,
                    remainingSegments - 1,
                    targetWidth,
                    memo
            );
            if (remaining == null) continue;
            double width = measureDisplayWidth(joinSyllables(syllables.subList(start, end)));
            double cost = square(width - targetWidth)
                    + boundary.penalty * 4.0
                    - Math.min(boundary.gapMs / 200.0, 1.0)
                    + remaining.cost;
            if (best == null || cost < best.cost) {
                List<Integer> cuts = new ArrayList<>();
                cuts.add(end);
                cuts.addAll(remaining.cuts);
                best = new SplitPlan(cost, cuts);
            }
        }
        memo.put(memoKey, best);
        return best;
    }

    private static boolean validLongLineFragment(
            List<LyricsLine.Syllable> syllables,
            int start,
            int end,
            double ignoredTargetWidth
    ) {
        if (start < 0 || end <= start || end > syllables.size()) return false;
        List<LyricsLine.Syllable> fragment = syllables.subList(start, end);
        double width = measureDisplayWidth(joinSyllables(fragment));
        long duration = fragment.get(fragment.size() - 1).endTimeMs - fragment.get(0).startTimeMs;
        return width >= LONG_LINE_MIN_WIDTH
                && width <= LONG_LINE_HARD_WIDTH
                && duration >= LONG_LINE_MIN_DURATION_MS;
    }

    private static double square(double value) {
        return value * value;
    }

    private static Map<Integer, BoundaryInfo> longLineBoundaries(List<LyricsLine.Syllable> syllables) {
        Map<Integer, BoundaryInfo> result = new LinkedHashMap<>();
        for (int index = 1; index < syllables.size(); index++) {
            LyricsLine.Syllable left = syllables.get(index - 1);
            LyricsLine.Syllable right = syllables.get(index);
            BoundaryInfo boundary = safeBoundary(left, right);
            if (boundary != null) result.put(index, boundary);
        }
        return result;
    }

    private static BoundaryInfo safeBoundary(LyricsLine.Syllable left, LyricsLine.Syllable right) {
        if (left.endTimeMs > right.startTimeMs) return null;
        String leftText = left.text == null ? "" : left.text;
        String rightText = right.text == null ? "" : right.text;
        int leftChar = lastMeaningfulCodePoint(leftText);
        int rightChar = firstMeaningfulCodePoint(rightText);
        if (leftChar < 0 || rightChar < 0 || isOpeningPunctuation(leftChar)
                || isClosingPunctuation(rightChar) || isJapaneseContinuation(rightChar)) return null;
        boolean whitespace = endsWithWhitespace(leftText) || startsWithWhitespace(rightText);
        boolean punctuation = isBoundaryPunctuation(leftChar);
        boolean scriptChange = (isCjk(leftChar) && isLatinOrNumber(rightChar))
                || (isLatinOrNumber(leftChar) && isCjk(rightChar));
        boolean noSpaceScriptBoundary = LyricsWrapPolicy.isSafeNoSpaceCjkBoundary(leftChar, rightChar);
        if (isLatinOrNumber(leftChar) && isLatinOrNumber(rightChar) && !whitespace && !punctuation) return null;
        if (!whitespace && !punctuation && !scriptChange && !noSpaceScriptBoundary) return null;
        return new BoundaryInfo(
                whitespace ? 0.0 : (punctuation ? 0.25 : (noSpaceScriptBoundary ? 1.0 : 2.5)),
                Math.max(0L, right.startTimeMs - left.endTimeMs)
        );
    }

    private static LyricsLine lineFragment(
            LyricsLine source,
            int start,
            int end,
            boolean firstFragment,
            boolean lastFragment
    ) {
        List<LyricsLine.Syllable> syllables = new ArrayList<>(source.syllables.subList(start, end));
        long fragmentStart = firstFragment
                ? Math.min(source.startTimeMs, syllables.get(0).startTimeMs)
                : syllables.get(0).startTimeMs;
        long fragmentEnd = lastFragment
                ? Math.max(source.endTimeMs, syllables.get(syllables.size() - 1).endTimeMs)
                : syllables.get(syllables.size() - 1).endTimeMs;
        return new LyricsLine(
                fragmentStart,
                fragmentEnd,
                joinSyllables(syllables),
                syllables,
                source.speaker,
                source.speakerColor,
                source.speakerFallback,
                source.kind,
                Collections.emptyList()
        );
    }

    private static ParsedSyllable parseSyllable(JSONObject source) {
        if (source == null) return null;
        Long start = finiteMs(source.opt("time"));
        if (start == null) return null;
        Long duration = positiveMs(source.opt("duration"));
        String text = normalizeInlineText(source.optString("text", ""));
        if (text.isEmpty()) return null;
        return new ParsedSyllable(new LyricsLine.Syllable(text, start, start + (duration == null ? 1L : duration)),
                source.optBoolean("isBackground", false));
    }

    private static boolean withinLine(ParsedSyllable syllable, Long start, long end) {
        if (start == null || end < 0L) return true;
        return syllable.value.startTimeMs >= start - SYLLABLE_TIMING_TOLERANCE_MS
                && syllable.value.endTimeMs <= end + SYLLABLE_TIMING_TOLERANCE_MS;
    }

    private static SpeakerPresentation speakerPresentation(
            String singer,
            Map<String, Integer> singerOrder,
            JSONObject agents
    ) {
        if (singer == null || singer.trim().isEmpty()) return SpeakerPresentation.NORMAL;
        String id = singer.trim();
        if (!singerOrder.containsKey(id)) singerOrder.put(id, singerOrder.size());
        int index = singerOrder.get(id);
        if (index == 0) return SpeakerPresentation.NORMAL;
        JSONObject agent = agents == null ? null : agents.optJSONObject(id);
        if (agent != null && "group".equalsIgnoreCase(agent.optString("type", ""))) {
            SpeakerPresentation[] groups = new SpeakerPresentation[]{SPEAKER_PALETTE[2], SPEAKER_PALETTE[5], SPEAKER_PALETTE[8]};
            return groups[(index - 1) % groups.length];
        }
        return SPEAKER_PALETTE[(index - 1) % SPEAKER_PALETTE.length];
    }

    private static LyricsLine.VocalPart vocalPart(
            String id,
            String role,
            String singer,
            SpeakerPresentation presentation,
            String text,
            List<LyricsLine.Syllable> syllables
    ) {
        return new LyricsLine.VocalPart(id, role, presentation.speaker, presentation.color,
                presentation.fallback, "vocal", normalizeDisplayText(text), syllables);
    }

    private static List<LyricsLine.Syllable> stripBackgroundSyllableParentheses(List<LyricsLine.Syllable> source) {
        if (source.isEmpty()) return source;
        List<LyricsLine.Syllable> result = new ArrayList<>();
        for (LyricsLine.Syllable syllable : source) {
            String text = BACKGROUND_PARENTHESES_PATTERN.matcher(syllable.text).replaceAll("");
            if (!text.isEmpty()) {
                result.add(new LyricsLine.Syllable(text, syllable.startTimeMs, syllable.endTimeMs));
            }
        }
        return result;
    }

    private static String stripBackgroundParentheses(String text) {
        return normalizeDisplayText(BACKGROUND_PARENTHESES_PATTERN.matcher(text).replaceAll(""));
    }

    private static List<LyricsLine.Syllable> toSyllables(List<ParsedSyllable> source) {
        List<LyricsLine.Syllable> result = new ArrayList<>();
        for (ParsedSyllable syllable : source) result.add(syllable.value);
        return result;
    }

    private static String joinSyllables(List<LyricsLine.Syllable> syllables) {
        StringBuilder builder = new StringBuilder();
        for (LyricsLine.Syllable syllable : syllables) builder.append(syllable.text);
        return normalizeDisplayText(builder.toString());
    }

    private static boolean hasTimedLines(List<LyricsLine> lines) {
        for (LyricsLine line : lines) if (line.isTimed()) return true;
        return false;
    }

    private static int countVocalParts(List<LyricsLine> lines) {
        int result = 0;
        for (LyricsLine line : lines) result += line.vocalParts.size();
        return result;
    }

    private static int find(int[] parents, int index) {
        while (parents[index] != index) {
            parents[index] = parents[parents[index]];
            index = parents[index];
        }
        return index;
    }

    private static void union(int[] parents, int left, int right) {
        int leftRoot = find(parents, left);
        int rightRoot = find(parents, right);
        if (leftRoot != rightRoot) parents[rightRoot] = leftRoot;
    }

    private static Long finiteMs(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        try {
            double number = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
            return Double.isFinite(number) && number >= 0.0 ? Math.round(number) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long positiveMs(Object value) {
        Long result = finiteMs(value);
        return result != null && result > 0L ? result : null;
    }

    private static String normalizeInlineText(String value) {
        return INLINE_WHITESPACE_PATTERN.matcher(value == null ? "" : value).replaceAll(" ");
    }

    private static String normalizeDisplayText(String value) {
        return normalizeInlineText(value).trim();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static double measureDisplayWidth(String value) {
        double width = 0.0;
        int[] codePoints = (value == null ? "" : value).codePoints().toArray();
        for (int codePoint : codePoints) {
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) continue;
            if (Character.isWhitespace(codePoint)) width += 0.33;
            else if (isCjk(codePoint) || isEmoji(codePoint)) width += 1.0;
            else if (codePoint >= 'A' && codePoint <= 'Z') width += 0.72;
            else if (codePoint >= 'a' && codePoint <= 'z') width += 0.58;
            else if (Character.isDigit(codePoint)) width += 0.62;
            else if (".,'’!?;:()-".indexOf(codePoint) >= 0) width += 0.38;
            else width += 0.8;
        }
        return width;
    }

    private static int firstMeaningfulCodePoint(String value) {
        return value == null ? -1 : value.codePoints().filter(cp -> !Character.isWhitespace(cp)
                && Character.getType(cp) != Character.NON_SPACING_MARK).findFirst().orElse(-1);
    }

    private static int lastMeaningfulCodePoint(String value) {
        if (value == null) return -1;
        int[] points = value.codePoints().toArray();
        for (int index = points.length - 1; index >= 0; index--) {
            if (!Character.isWhitespace(points[index]) && Character.getType(points[index]) != Character.NON_SPACING_MARK) return points[index];
        }
        return -1;
    }

    private static boolean startsWithWhitespace(String value) {
        return value != null && !value.isEmpty() && Character.isWhitespace(value.codePointAt(0));
    }

    private static boolean endsWithWhitespace(String value) {
        if (value == null || value.isEmpty()) return false;
        int point = value.codePointBefore(value.length());
        return Character.isWhitespace(point);
    }

    private static boolean isLatinOrNumber(int point) {
        return point >= 0 && (Character.isDigit(point) || Character.UnicodeScript.of(point) == Character.UnicodeScript.LATIN);
    }

    private static boolean isCjk(int point) {
        if (point < 0) return false;
        Character.UnicodeScript script = Character.UnicodeScript.of(point);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
    }

    private static boolean isEmoji(int point) {
        return point >= 0x1F000 && point <= 0x1FAFF;
    }

    private static boolean isOpeningPunctuation(int point) {
        return "([{（「『【〈《".indexOf(point) >= 0;
    }

    private static boolean isClosingPunctuation(int point) {
        return ")] }）」』】〉》、。，．！？?!".replace(" ", "").indexOf(point) >= 0;
    }

    private static boolean isJapaneseContinuation(int point) {
        return "ゃゅょっぁぃぅぇぉゎャュョッァィゥェォヮー々".indexOf(point) >= 0;
    }

    private static boolean isBoundaryPunctuation(int point) {
        return "。！？?!…；;：:、，,.".indexOf(point) >= 0;
    }

    static final class Variants {
        final LyricsResult karaoke;
        final LyricsResult synced;
        final LyricsResult plain;

        Variants(LyricsResult karaoke, LyricsResult synced, LyricsResult plain) {
            this.karaoke = karaoke;
            this.synced = synced;
            this.plain = plain;
        }

        LyricsResult best() {
            if (karaoke != null && !karaoke.lines.isEmpty()) return karaoke;
            if (synced != null && !synced.lines.isEmpty()) return synced;
            return plain != null && !plain.lines.isEmpty() ? plain : null;
        }

        String availableTypes() {
            List<String> result = new ArrayList<>();
            if (karaoke != null && !karaoke.lines.isEmpty()) result.add("karaoke");
            if (synced != null && !synced.lines.isEmpty()) result.add("synced");
            if (plain != null && !plain.lines.isEmpty()) result.add("plain");
            return result.toString();
        }
    }

    private static final class BoundaryInfo {
        final double penalty;
        final long gapMs;

        BoundaryInfo(double penalty, long gapMs) {
            this.penalty = penalty;
            this.gapMs = gapMs;
        }
    }

    private static final class SplitPlan {
        final double cost;
        final List<Integer> cuts;

        SplitPlan(double cost, List<Integer> cuts) {
            this.cost = cost;
            this.cuts = Collections.unmodifiableList(new ArrayList<>(cuts));
        }
    }

    private static final class ParallelSplit {
        final List<ParsedLine> left;
        final List<ParsedLine> right;
        final long leftEndTime;
        final long nextStartTime;
        final int leftSourceCount;
        final long maximumDelay;
        final long distance;

        ParallelSplit(
                List<ParsedLine> left,
                List<ParsedLine> right,
                long leftEndTime,
                long nextStartTime,
                int leftSourceCount,
                long maximumDelay,
                long distance
        ) {
            this.left = left;
            this.right = right;
            this.leftEndTime = leftEndTime;
            this.nextStartTime = nextStartTime;
            this.leftSourceCount = leftSourceCount;
            this.maximumDelay = maximumDelay;
            this.distance = distance;
        }

        boolean betterThan(ParallelSplit other) {
            if (leftSourceCount != other.leftSourceCount) return leftSourceCount > other.leftSourceCount;
            if (maximumDelay != other.maximumDelay) return maximumDelay < other.maximumDelay;
            if (distance != other.distance) return distance < other.distance;
            return leftEndTime > other.leftEndTime;
        }
    }

    private static final class ParallelPart {
        final LyricsLine.VocalPart part;
        final ParsedLine source;

        ParallelPart(LyricsLine.VocalPart part, ParsedLine source) {
            this.part = part;
            this.source = source;
        }
    }

    private static final class ParallelLane {
        final String key;
        final String singer;
        final List<ParallelPart> entries = new ArrayList<>();
        long startTime = Long.MAX_VALUE;
        long endTime;
        long duration;

        ParallelLane(String key, String singer) {
            this.key = key;
            this.singer = singer == null ? "" : singer;
        }

        void add(ParallelPart entry) {
            entries.add(entry);
            startTime = Math.min(startTime, entry.part.startTimeMs);
            endTime = Math.max(endTime, entry.part.endTimeMs);
            duration += Math.max(0L, entry.part.endTimeMs - entry.part.startTimeMs);
        }

        int firstSourceIndex() {
            int result = Integer.MAX_VALUE;
            for (ParallelPart entry : entries) result = Math.min(result, entry.source.sourceIndex);
            return result;
        }

        LyricsLine.VocalPart toVocalPart(String role) {
            List<ParallelPart> ordered = new ArrayList<>(entries);
            ordered.sort((left, right) -> {
                int byTime = Long.compare(left.part.startTimeMs, right.part.startTimeMs);
                return byTime != 0 ? byTime : Integer.compare(left.source.sourceIndex, right.source.sourceIndex);
            });
            List<LyricsLine.Syllable> syllables = new ArrayList<>();
            StringBuilder id = new StringBuilder();
            StringBuilder text = new StringBuilder();
            for (ParallelPart entry : ordered) {
                LyricsLine.VocalPart part = withVocalRole(entry.part, role);
                if (id.length() > 0) id.append('+');
                id.append(part.id);
                if (text.length() > 0) text.append(" / ");
                text.append(part.text);
                if (!syllables.isEmpty() && !part.syllables.isEmpty()) {
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

    private static final class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }

    private static final class ParsedSyllable {
        final LyricsLine.Syllable value;
        final boolean background;

        ParsedSyllable(LyricsLine.Syllable value, boolean background) {
            this.value = value;
            this.background = background;
        }
    }

    private static final class SpeakerPresentation {
        static final SpeakerPresentation NORMAL = new SpeakerPresentation("NORMAL", "", "");

        final String speaker;
        final String color;
        final String fallback;

        SpeakerPresentation(String speaker, String color, String fallback) {
            this.speaker = speaker;
            this.color = color;
            this.fallback = fallback;
        }
    }

    private static final class ParsedLine {
        final int sourceIndex;
        final String lineKey;
        final String singer;
        final long startTimeMs;
        long endTimeMs;
        final String text;
        final List<LyricsLine.Syllable> syllables;
        final List<LyricsLine.VocalPart> inlineVocalParts;
        final SpeakerPresentation presentation;
        final boolean hasWordTiming;

        ParsedLine(
                int sourceIndex,
                String lineKey,
                String singer,
                long startTimeMs,
                long endTimeMs,
                String text,
                List<LyricsLine.Syllable> syllables,
                List<LyricsLine.VocalPart> inlineVocalParts,
                SpeakerPresentation presentation,
                boolean hasWordTiming
        ) {
            this.sourceIndex = sourceIndex;
            this.lineKey = lineKey;
            this.singer = singer;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.text = text;
            this.syllables = syllables;
            this.inlineVocalParts = inlineVocalParts;
            this.presentation = presentation;
            this.hasWordTiming = hasWordTiming;
        }

        LyricsLine toKaraokeLine() {
            List<LyricsLine.Syllable> safeSyllables = syllables.isEmpty()
                    ? Collections.singletonList(new LyricsLine.Syllable(text, startTimeMs, endTimeMs))
                    : syllables;
            return new LyricsLine(startTimeMs, endTimeMs, text,
                    inlineVocalParts.isEmpty() ? safeSyllables : Collections.emptyList(),
                    presentation.speaker, presentation.color, presentation.fallback, "vocal", inlineVocalParts);
        }

        LyricsLine toSyncedLine() {
            return new LyricsLine(startTimeMs, endTimeMs, text, Collections.emptyList(), presentation.speaker,
                    presentation.color, presentation.fallback, "vocal", Collections.emptyList());
        }
    }
}
