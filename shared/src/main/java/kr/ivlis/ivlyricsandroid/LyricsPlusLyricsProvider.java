package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
    private static final int REQUEST_TIMEOUT_MS = 5_000;
    private static final int STAGE_TIMEOUT_MS = 10_000;
    private static final int MIRROR_HEDGE_DELAY_MS = 750;
    private static final int MIRROR_COOLDOWN_MS = 60_000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int SYLLABLE_TIMING_TOLERANCE_MS = 1_500;
    private static final double LONG_LINE_TRIGGER_WIDTH = 22.0;
    private static final double LONG_LINE_HARD_WIDTH = 26.0;
    private static final double LONG_LINE_MIN_WIDTH = 6.0;
    private static final long LONG_LINE_MIN_DURATION_MS = 500L;
    private static final int LONG_LINE_MAX_SEGMENTS = 4;
    private static final AtomicInteger PREFERRED_MIRROR = new AtomicInteger();
    private static final Map<String, Long> MIRROR_UNAVAILABLE_UNTIL = new ConcurrentHashMap<>();
    private static final ExecutorService REQUESTS = Executors.newFixedThreadPool(2, work -> {
        Thread thread = new Thread(work, "ivLyrics-LyricsPlus");
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(work -> {
        Thread thread = new Thread(work, "ivLyrics-LyricsPlus-deadline");
        thread.setDaemon(true);
        return thread;
    });
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

        List<LyricsLine> karaokeLines = new ArrayList<>();
        if (completeWordTiming) {
            // A timing overlap does not imply background vocals or a shared sentence.
            // Keep each provider row and the explicit background syllables parsed inside it.
            for (ParsedLine line : timed) karaokeLines.add(line.toKaraokeLine());
            karaokeLines = splitLongSoloLines(karaokeLines);
        }
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
        RequestCancellation.check();
        RequestCancellation request = RequestCancellation.current();
        long stageDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STAGE_TIMEOUT_MS);
        long hedgeAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MIRROR_HEDGE_DELAY_MS);
        int start = Math.floorMod(PREFERRED_MIRROR.get(), bases.size());
        Exception lastError = null;
        boolean allNotFound = true;
        List<Integer> available = new ArrayList<>();
        for (int offset = 0; offset < bases.size(); offset++) {
            int mirror = (start + offset) % bases.size();
            Long unavailableUntil = MIRROR_UNAVAILABLE_UNTIL.get(bases.get(mirror));
            if (unavailableUntil != null && unavailableUntil - System.nanoTime() > 0L) {
                allNotFound = false;
                lastError = new IOException("LyricsPlus mirror #" + (mirror + 1) + " temporarily unavailable");
            } else {
                available.add(mirror);
            }
        }
        ExecutorCompletionService<MirrorResponse> completed = new ExecutorCompletionService<>(REQUESTS);
        List<MirrorAttempt> attempts = new ArrayList<>();
        int next = 0;
        int pending = 0;
        try {
            while (next < available.size() || pending > 0) {
                RequestCancellation.check();
                long remaining = stageDeadline - System.nanoTime();
                if (remaining <= 0) throw new SocketTimeoutException("LyricsPlus playback deadline reached");
                if (next < available.size() && (pending == 0 || System.nanoTime() >= hedgeAt)) {
                    int mirror = available.get(next++);
                    RequestCancellation attempt = new RequestCancellation();
                    String requestUrl = buildLyricsUrl(bases.get(mirror), track, isrc);
                    Future<MirrorResponse> future = completed.submit(() -> {
                        try {
                            return new MirrorResponse(mirror, get(requestUrl, request, attempt, stageDeadline), null);
                        } catch (Exception error) {
                            return new MirrorResponse(mirror, null, error);
                        }
                    });
                    attempts.add(new MirrorAttempt(attempt, future));
                    pending++;
                }
                long wait = Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100));
                if (next < available.size()) wait = Math.min(wait, Math.max(1L, hedgeAt - System.nanoTime()));
                Future<MirrorResponse> future = completed.poll(wait, TimeUnit.NANOSECONDS);
                if (future == null) continue;
                pending--;
                MirrorResponse result = future.get();
                RequestCancellation.check();
                String base = bases.get(result.mirror);
                if (result.error != null) {
                    allNotFound = false;
                    lastError = result.error;
                    MIRROR_UNAVAILABLE_UNTIL.put(base,
                            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MIRROR_COOLDOWN_MS));
                    continue;
                }
                HttpResult response = result.response;
                JSONObject body = parseJson(response.body);
                if (response.status >= 200 && response.status < 300 && isUsablePayload(body)) {
                    PREFERRED_MIRROR.set(result.mirror);
                    MIRROR_UNAVAILABLE_UNTIL.remove(base);
                    logger.write("lyricsplus: mirror #" + (result.mirror + 1) + " matched");
                    return body;
                }
                boolean notFound = response.status == 404
                        || (response.status >= 200 && response.status < 300 && body != null
                        && !isTruthyJsonValue(body.opt("error")) && body.optJSONArray("lyrics") != null);
                allNotFound &= notFound;
                if (notFound) {
                    MIRROR_UNAVAILABLE_UNTIL.remove(base);
                } else {
                    lastError = new IOException("LyricsPlus mirror #" + (result.mirror + 1) + " response " + response.status);
                    MIRROR_UNAVAILABLE_UNTIL.put(base,
                            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MIRROR_COOLDOWN_MS));
                }
            }
        } finally {
            // Future interruption alone does not reliably release HttpURLConnection reads.
            for (MirrorAttempt attempt : attempts) {
                attempt.request.cancel();
                attempt.future.cancel(true);
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

    private static HttpResult get(
            String requestUrl, RequestCancellation request, RequestCancellation attempt, long stageDeadline
    ) throws Exception {
        long deadline = Math.min(stageDeadline,
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REQUEST_TIMEOUT_MS));
        checkRequest(request, attempt, deadline);
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        ScheduledFuture<?> timeout = DEADLINES.schedule(attempt::cancel, remainingMs(deadline), TimeUnit.MILLISECONDS);
        try (RequestCancellation.AutoCloseableConnection registration = RequestCancellation.register(request, connection);
             RequestCancellation.AutoCloseableConnection mirrorRegistration = RequestCancellation.register(attempt, connection)) {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(remainingMs(deadline));
            connection.setReadTimeout(remainingMs(deadline));
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            checkRequest(request, attempt, deadline);
            // A slow error page cannot delay a fallback once the status already proves failure.
            if (status < 200 || status >= 300) return new HttpResult(status, "");
            InputStream stream = connection.getInputStream();
            return new HttpResult(status, readUtf8(stream, connection, request, attempt, deadline));
        } finally {
            timeout.cancel(false);
        }
    }

    private static int remainingMs(long deadline) throws SocketTimeoutException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new SocketTimeoutException("LyricsPlus request deadline reached");
        return (int) Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
    }

    private static void checkRequest(RequestCancellation request, RequestCancellation attempt, long deadline)
            throws SocketTimeoutException {
        if (request != null) request.throwIfCancelled();
        remainingMs(deadline);
        attempt.throwIfCancelled();
    }

    private static String readUtf8(InputStream stream, HttpURLConnection connection,
                                   RequestCancellation request, RequestCancellation attempt, long deadline) throws Exception {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            while (true) {
                checkRequest(request, attempt, deadline);
                connection.setReadTimeout(remainingMs(deadline));
                int read = input.read(buffer);
                if (read < 0) break;
                if (output.size() + read > MAX_RESPONSE_BYTES) throw new IOException("LyricsPlus response too large");
                output.write(buffer, 0, read);
            }
            checkRequest(request, attempt, deadline);
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
        String text = value == null ? "" : value;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
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
        if (value == null) return -1;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)
                    && Character.getType(codePoint) != Character.NON_SPACING_MARK) {
                return codePoint;
            }
            offset += Character.charCount(codePoint);
        }
        return -1;
    }

    private static int lastMeaningfulCodePoint(String value) {
        if (value == null) return -1;
        for (int offset = value.length(); offset > 0; ) {
            int codePoint = value.codePointBefore(offset);
            if (!Character.isWhitespace(codePoint)
                    && Character.getType(codePoint) != Character.NON_SPACING_MARK) {
                return codePoint;
            }
            offset -= Character.charCount(codePoint);
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

    private static final class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }

    private static final class MirrorAttempt {
        final RequestCancellation request;
        final Future<MirrorResponse> future;

        MirrorAttempt(RequestCancellation request, Future<MirrorResponse> future) {
            this.request = request;
            this.future = future;
        }
    }

    private static final class MirrorResponse {
        final int mirror;
        final HttpResult response;
        final Exception error;

        MirrorResponse(int mirror, HttpResult response, Exception error) {
            this.mirror = mirror;
            this.response = response;
            this.error = error;
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
