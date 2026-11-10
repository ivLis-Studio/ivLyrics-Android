package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only lrc.red provider and its optional word timing generator.
 *
 * Lookups match the desktop addon: the track ISRC first, otherwise a literal title/artist search
 * that accepts only the same song. A record's TTML is preferred, with its LRC as a fallback.
 * Word timing generation is a separate POST whose response is applied only to the same text.
 */
final class LrcRedLyricsProvider {
    static final String PROJECT_URL = "https://lrc.red";
    static final String ATTRIBUTION = "Lyrics from lrc.red (https://lrc.red).";
    private static final String API_BASE = "https://lrc.red";
    private static final String CLIENT_ID = "ivLyrics-Android (+https://github.com/ivLis-Studio/ivLyrics-Android; mailto:me@ivl.is)";
    private static final int REQUEST_TIMEOUT_MS = 15_000;
    static final int WORD_SYNC_TIMEOUT_MS = 75_000;
    private static final Pattern ISRC_PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{3}\\d{7}$");
    private static final Pattern ENHANCED_LRC_TAG = Pattern.compile("<(?:\\d{1,3}:)?\\d{1,2}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern TITLE_DECORATION = Pattern.compile("\\s*[(\\[][^)\\]]*[)\\]]");
    private static final Pattern TITLE_DASH_SUFFIX = Pattern.compile("\\s+-\\s+.*$");
    private static final Pattern VERSION_LABEL = Pattern.compile(
            "\\b(?:feat(?:uring)?|ft|version|remaster(?:ed)?|remix|mix|edit|edition|live|acoustic|mono|stereo)\\b|\\bfrom the vault\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARENTHESIZED = Pattern.compile("\\(([^()]*)\\)|\\[([^\\[\\]]*)\\]");
    private static final Pattern DASH_SUFFIX_CAPTURE = Pattern.compile("\\s+-\\s+(.+)$");
    private static final Pattern ARTIST_LEAD_SEPARATOR = Pattern.compile(
            "\\s+(?:feat(?:uring)?\\.?|ft\\.?|with|[x×])\\s+|[,;&]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern LATIN_MARKS = Pattern.compile("(\\p{IsLatin})\\p{M}+");
    private static final Pattern APOSTROPHES = Pattern.compile("['’‘`´]");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\p{M}]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    interface Logger {
        void write(String message);
    }

    /** A provider result with the lrc.red recording it came from. */
    static final class Fetched {
        final LyricsResult result;
        final String recordIsrc;

        Fetched(LyricsResult result, String recordIsrc) {
            this.result = result;
            this.recordIsrc = recordIsrc == null ? "" : recordIsrc;
        }
    }

    /** Thrown for an HTTP status so callers can apply the server's cooldown. */
    static final class HttpStatusException extends Exception {
        final int status;
        final long retryAfterMs;

        HttpStatusException(String message, int status, long retryAfterMs) {
            super(message);
            this.status = status;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private LrcRedLyricsProvider() {
    }

    static Fetched fetch(TrackSnapshot track, String isrc, String spotifyTrackId, Logger logger) throws Exception {
        if (track == null || !track.hasUsableMetadata()) return null;
        String title = track.title.trim();
        String artist = track.artist.trim();
        long durationSeconds = track.durationMs > 0 ? Math.round(track.durationMs / 1000.0) : 0L;

        String recordIsrc = "";
        String resolvedIsrc = normalizeIsrc(firstNonEmpty(isrc, track.isrc));
        if (!resolvedIsrc.isEmpty()) {
            try {
                HttpResult response = get(API_BASE + "/s/" + urlEncode(resolvedIsrc) + ".json");
                JSONObject record = response.ok() ? parseObject(response.body) : null;
                if (record != null && (!record.optString("title", "").isEmpty() || !record.optString("isrc", "").isEmpty())) {
                    recordIsrc = firstNonEmpty(normalizeIsrc(record.optString("isrc", "")), resolvedIsrc);
                    logger.write("lrc.red: ISRC match " + recordIsrc);
                }
            } catch (java.util.concurrent.CancellationException cancelled) {
                throw cancelled;
            } catch (Exception error) {
                logger.write("lrc.red: ISRC lookup failed, searching instead: " + error.getMessage());
            }
        }

        if (recordIsrc.isEmpty()) {
            double bestScore = -1.0;
            for (String query : buildSearchQueries(title, artist)) {
                JSONArray hits;
                try {
                    HttpResult response = get(API_BASE + "/search.json?q=" + urlEncode(query));
                    JSONObject body = response.ok() ? parseObject(response.body) : null;
                    hits = body == null ? null : body.optJSONArray("hits");
                } catch (java.util.concurrent.CancellationException cancelled) {
                    throw cancelled;
                } catch (Exception ignored) {
                    hits = null;
                }
                if (hits == null) continue;
                for (int index = 0; index < hits.length(); index++) {
                    JSONObject hit = hits.optJSONObject(index);
                    Double score = scoreSearchHit(hit, title, artist, durationSeconds);
                    if (score != null && score > bestScore) {
                        bestScore = score;
                        recordIsrc = normalizeIsrc(hit.optString("isrc", ""));
                    }
                }
                if (!recordIsrc.isEmpty()) {
                    logger.write("lrc.red: search match " + recordIsrc + " (query=" + query + ")");
                    break;
                }
            }
        }
        if (recordIsrc.isEmpty()) {
            logger.write("lrc.red: no lyrics found");
            return null;
        }

        UnisonLyricsProvider.ParsedLyrics parsed = null;
        try {
            HttpResult ttml = get(API_BASE + "/s/" + urlEncode(recordIsrc) + ".ttml");
            if (!ttml.ok()) throw new IllegalStateException("TTML fetch failed (" + ttml.status + ")");
            parsed = UnisonLyricsProvider.parseTtmlLyrics(ttml.body, track.durationMs);
            if (parsed.lines.isEmpty()) parsed = null;
        } catch (java.util.concurrent.CancellationException cancelled) {
            throw cancelled;
        } catch (Exception ttmlError) {
            logger.write("lrc.red: TTML unavailable, trying LRC: " + ttmlError.getMessage());
        }
        if (parsed == null) {
            try {
                HttpResult lrc = get(API_BASE + "/s/" + urlEncode(recordIsrc) + ".lrc");
                if (lrc.ok()) {
                    parsed = UnisonLyricsProvider.parseLrcLyrics(stripEnhancedLrcTags(lrc.body), track.durationMs);
                    if (parsed.lines.isEmpty()) parsed = null;
                }
            } catch (java.util.concurrent.CancellationException cancelled) {
                throw cancelled;
            } catch (Exception lrcError) {
                logger.write("lrc.red: LRC unavailable: " + lrcError.getMessage());
            }
        }
        if (parsed == null) {
            logger.write("lrc.red: record " + recordIsrc + " has no usable lyrics");
            return null;
        }
        String type = parsed.karaoke ? "karaoke" : (parsed.synced ? "synced" : "plain");
        logger.write("lrc.red selected: isrc=" + recordIsrc + " / type=" + type + " / lines=" + parsed.lines.size());
        return new Fetched(new LyricsResult(
                parsed.lines,
                "lrc.red " + type,
                ATTRIBUTION,
                parsed.karaoke,
                firstNonEmpty(TrackSnapshot.normalizeIsrc(isrc), recordIsrc),
                spotifyTrackId == null ? "" : spotifyTrackId,
                Collections.emptyList()
        ), recordIsrc);
    }

    /**
     * Requests generated word timing. Returns lines whose texts equal the response's line texts;
     * the caller must still confirm they equal the lyrics already displayed.
     */
    static List<LyricsLine> requestWordSync(String recordIsrc) throws Exception {
        String isrc = normalizeIsrc(recordIsrc);
        if (isrc.isEmpty()) throw new IllegalArgumentException("Missing lrc.red recording");
        HttpURLConnection connection = (HttpURLConnection) URI.create(API_BASE + "/s/" + urlEncode(isrc) + "/sync")
                .toURL().openConnection();
        try {
            connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
            connection.setReadTimeout(WORD_SYNC_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(0);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Client-ID", CLIENT_ID);
            connection.setRequestProperty("User-Agent", "ivLyrics-Android");
            connection.getOutputStream().close();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                long retryAfter = parseRetryAfterMs(connection.getHeaderField("Retry-After"));
                throw new HttpStatusException("Word sync failed (" + status + ")", status, retryAfter);
            }
            // The whole body is part of the timed request; its content is the generated timing.
            return parseWordSyncResponse(readBody(connection.getInputStream()), isrc);
        } finally {
            connection.disconnect();
        }
    }

    static List<LyricsLine> parseWordSyncResponse(String body, String expectedIsrc) {
        JSONObject payload = parseObject(body);
        if (payload == null || !normalizeIsrc(payload.optString("id", "")).equals(normalizeIsrc(expectedIsrc))) {
            throw new IllegalStateException("Word sync recording mismatch");
        }
        JSONObject lyrics = payload.optJSONObject("lyrics");
        String timing = lyrics == null ? "" : firstNonEmpty(lyrics.optString("timing", ""), lyrics.optString("sync", ""))
                .toLowerCase(Locale.ROOT);
        JSONArray lines = lyrics == null ? null : lyrics.optJSONArray("lines");
        if (lines == null || (!"word".equals(timing) && !"syllable".equals(timing))) {
            throw new IllegalStateException("Word sync response has no word timing");
        }
        List<LyricsLine> result = new ArrayList<>();
        int timedWords = 0;
        for (int lineIndex = 0; lineIndex < lines.length(); lineIndex++) {
            JSONObject line = lines.optJSONObject(lineIndex);
            if (line == null || !(line.opt("text") instanceof String)) continue;
            String text = line.optString("text", "");
            if (text.trim().isEmpty()) continue;
            List<LyricsLine.Syllable> words = new ArrayList<>();
            List<String> wordTexts = new ArrayList<>();
            List<long[]> wordTimes = new ArrayList<>();
            int cursor = 0;
            JSONArray wordGroups = line.optJSONArray("words");
            for (int wordIndex = 0; wordGroups != null && wordIndex < wordGroups.length(); wordIndex++) {
                JSONArray fragments = wordGroups.optJSONArray(wordIndex);
                if (fragments == null || fragments.length() == 0) throw new IllegalStateException("Invalid word sync fragments");
                StringBuilder word = new StringBuilder();
                for (int fragmentIndex = 0; fragmentIndex < fragments.length(); fragmentIndex++) {
                    JSONObject fragment = fragments.optJSONObject(fragmentIndex);
                    if (fragment == null || !(fragment.opt("text") instanceof String)) {
                        throw new IllegalStateException("Invalid word sync text");
                    }
                    long begin = milliseconds(fragment.opt("begin"));
                    long end = milliseconds(fragment.opt("end"));
                    if (end < begin) throw new IllegalStateException("Invalid word sync range");
                    word.append(fragment.optString("text", ""));
                }
                int whitespaceEnd = cursor;
                while (whitespaceEnd < text.length() && Character.isWhitespace(text.charAt(whitespaceEnd))) whitespaceEnd++;
                String whitespace = text.substring(cursor, whitespaceEnd);
                cursor = whitespaceEnd;
                String wordText = word.toString();
                if (wordText.isEmpty() || !text.startsWith(wordText, cursor)) {
                    throw new IllegalStateException("Word sync text does not match its line");
                }
                // Whitespace belongs to the end of the previous word, as on the desktop.
                if (!wordTexts.isEmpty()) {
                    int last = wordTexts.size() - 1;
                    wordTexts.set(last, wordTexts.get(last) + whitespace);
                    wordTexts.add(wordText);
                } else {
                    wordTexts.add(whitespace + wordText);
                }
                long start = milliseconds(fragments.optJSONObject(0).opt("begin"));
                long end = milliseconds(fragments.optJSONObject(fragments.length() - 1).opt("end"));
                if (end < start) throw new IllegalStateException("Invalid word sync range");
                wordTimes.add(new long[]{start, end});
                cursor += wordText.length();
                timedWords++;
            }
            if (!wordTexts.isEmpty()) {
                String remaining = text.substring(cursor);
                if (!remaining.trim().isEmpty()) throw new IllegalStateException("Word sync response has unmatched text");
                int last = wordTexts.size() - 1;
                wordTexts.set(last, wordTexts.get(last) + remaining);
            }
            long lineStart = milliseconds(line.opt("begin"));
            long lineEnd = milliseconds(line.opt("end"));
            if (lineEnd < lineStart) throw new IllegalStateException("Invalid word sync line range");
            for (int index = 0; index < wordTexts.size(); index++) {
                words.add(new LyricsLine.Syllable(wordTexts.get(index), wordTimes.get(index)[0], wordTimes.get(index)[1], true));
            }
            if (words.isEmpty()) words.add(new LyricsLine.Syllable(text, lineStart, lineEnd, true));
            result.add(new LyricsLine(lineStart, Math.max(lineStart + 1L, lineEnd), text, words));
        }
        if (timedWords == 0) throw new IllegalStateException("Word sync response has no timed words");
        return result;
    }

    /** True when both results show the same non-empty lyric lines, ignoring timing. */
    static boolean sameLineTexts(List<LyricsLine> left, List<LyricsLine> right) {
        List<String> a = lineTexts(left);
        List<String> b = lineTexts(right);
        return !a.isEmpty() && a.equals(b);
    }

    private static List<String> lineTexts(List<LyricsLine> lines) {
        List<String> texts = new ArrayList<>();
        if (lines == null) return texts;
        for (LyricsLine line : lines) {
            String text = line == null || line.text == null ? "" : Normalizer.normalize(line.text, Normalizer.Form.NFC).trim();
            if (!text.isEmpty()) texts.add(text);
        }
        return texts;
    }

    static String stripEnhancedLrcTags(String lrc) {
        return ENHANCED_LRC_TAG.matcher(lrc == null ? "" : lrc).replaceAll("");
    }

    static String normalizeIsrc(String value) {
        String isrc = value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return ISRC_PATTERN.matcher(isrc).matches() ? isrc : "";
    }

    static List<String> buildSearchQueries(String title, String artist) {
        String base = stripTitleDecorations(title);
        if (base.isEmpty()) base = title;
        List<String> queries = new ArrayList<>();
        for (String value : new String[]{title + " " + artist, title, base + " " + artist, base}) {
            String query = SPACES.matcher(value == null ? "" : value).replaceAll(" ").trim();
            if (!query.isEmpty() && !queries.contains(query)) queries.add(query);
        }
        return queries;
    }

    static Double scoreSearchHit(JSONObject hit, String title, String artist, long durationSeconds) {
        if (hit == null) return null;
        double titleScore = titleMatchScore(hit.optString("title", ""), title);
        if (normalizeIsrc(hit.optString("isrc", "")).isEmpty() || titleScore <= 0
                || !sameArtist(hit.optString("artist", ""), artist)) {
            return null;
        }
        double candidateDuration = hit.optDouble("duration", 0.0);
        double durationScore = durationSeconds > 0 && candidateDuration > 0
                ? Math.max(0.0, 1.0 - Math.abs(durationSeconds - candidateDuration) / 30.0)
                : 0.0;
        // Duration ranks only candidates already identified as the same song.
        return titleScore * 0.6 + 0.3 + durationScore * 0.1;
    }

    static double titleMatchScore(String actual, String expected) {
        String wanted = compact(expected);
        String candidate = compact(actual);
        if (wanted.isEmpty() || candidate.isEmpty()) return 0;
        if (candidate.equals(wanted)) return 1;
        // Identity matching may ignore only known version labels, never e.g. "Song (Part II)".
        String wantedCore = compact(withoutVersionLabels(expected));
        String actualCore = compact(withoutVersionLabels(actual));
        return !wantedCore.isEmpty() && actualCore.equals(wantedCore) ? 0.9 : 0;
    }

    static boolean sameArtist(String actual, String expected) {
        String wanted = normalizeString(expected);
        String candidate = normalizeString(actual);
        if (wanted.isEmpty() || candidate.isEmpty()) return false;
        if (candidate.equals(wanted)) return true;
        // A catalog may list featured artists where Spotify supplies only the lead.
        String expectedLead = normalizeString(ARTIST_LEAD_SEPARATOR.split(expected == null ? "" : expected, 2)[0]);
        String actualLead = normalizeString(ARTIST_LEAD_SEPARATOR.split(actual == null ? "" : actual, 2)[0]);
        return !expectedLead.isEmpty() && expectedLead.equals(actualLead);
    }

    static String normalizeString(String value) {
        String text = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        // Fold Latin accents without dropping Japanese voicing or marks in other scripts.
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = LATIN_MARKS.matcher(text).replaceAll("$1");
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        text = APOSTROPHES.matcher(text).replaceAll("");
        text = NON_WORD.matcher(text).replaceAll(" ");
        return SPACES.matcher(text).replaceAll(" ").trim();
    }

    private static String compact(String value) {
        return normalizeString(value).replace(" ", "");
    }

    private static String stripTitleDecorations(String title) {
        String value = TITLE_DECORATION.matcher(title == null ? "" : title).replaceAll(" ");
        value = TITLE_DASH_SUFFIX.matcher(value).replaceAll(" ");
        return SPACES.matcher(value).replaceAll(" ").trim();
    }

    private static String withoutVersionLabels(String title) {
        String value = title == null ? "" : title;
        Matcher matcher = PARENTHESIZED.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String inner = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    VERSION_LABEL.matcher(inner == null ? "" : inner).find() ? " " : matcher.group()));
        }
        matcher.appendTail(buffer);
        value = buffer.toString();
        Matcher suffix = DASH_SUFFIX_CAPTURE.matcher(value);
        if (suffix.find() && VERSION_LABEL.matcher(suffix.group(1)).find()) {
            value = value.substring(0, suffix.start());
        }
        return value;
    }

    private static long milliseconds(Object value) {
        if (!(value instanceof Number)) throw new IllegalStateException("Invalid word sync time");
        double seconds = ((Number) value).doubleValue();
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
            throw new IllegalStateException("Invalid word sync time");
        }
        return Math.round(seconds * 1000.0);
    }

    static long parseRetryAfterMs(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            return Math.max(0L, Math.round(Double.parseDouble(value.trim()) * 1000.0));
        } catch (NumberFormatException notSeconds) {
            try {
                long at = java.time.ZonedDateTime.parse(value.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
                return Math.max(0L, at - System.currentTimeMillis());
            } catch (Exception ignored) {
                return 0L;
            }
        }
    }

    private static HttpResult get(String endpoint) throws Exception {
        RequestCancellation.check();
        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        try (RequestCancellation.AutoCloseableConnection registration = RequestCancellation.register(connection)) {
            connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
            connection.setReadTimeout(REQUEST_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json, application/ttml+xml, text/plain, */*");
            connection.setRequestProperty("X-Client-ID", CLIENT_ID);
            connection.setRequestProperty("User-Agent", "ivLyrics-Android");
            try {
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                return new HttpResult(status, stream == null ? "" : readBody(stream));
            } finally {
                RequestCancellation.check();
            }
        }
    }

    private static String readBody(InputStream stream) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static JSONObject parseObject(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            return new JSONObject(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception error) {
            return "";
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static final class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }

        boolean ok() {
            return status >= 200 && status < 300;
        }
    }
}

