package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lyrics provider backed by the public Lyrically service. */
final class PaxsenixLyricsProvider {
    static final String PROJECT_URL = decode("aHR0cHM6Ly9seXJpY3MucGF4c2VuaXgub3Jn");

    private static final String CATALOG_SEARCH_URL = decode("aHR0cHM6Ly9pdHVuZXMuYXBwbGUuY29tL3NlYXJjaA==");
    private static final String STRUCTURED_SEARCH_URL = decode("aHR0cHM6Ly9seXJpY3MucGF4c2VuaXgub3JnL2t1Z291L3NlYXJjaA==");
    private static final String STRUCTURED_LYRICS_URL = decode("aHR0cHM6Ly9seXJpY3MucGF4c2VuaXgub3JnL2t1Z291L2x5cmljcw==");
    private static final String CATALOG_LYRICS_URL = decode("aHR0cHM6Ly9seXJpY3MucGF4c2VuaXgub3JnL2FwcGxlLW11c2ljL2x5cmljcw==");
    private static final String STRUCTURED_PROVIDER_ID = decode("a3Vnb3U=");
    private static final String ATTRIBUTION = "Lyrics via Lyrically API (" + PROJECT_URL + ").";
    private static final int REQUEST_TIMEOUT_MS = 9_000;
    private static final Pattern REFERENCE_LINE_PATTERN = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern REFERENCE_TOKEN_PATTERN = Pattern.compile("<\\d+,\\d+,\\d+>");
    private static final Pattern BRACKETED_TITLE_PART_PATTERN = Pattern.compile("\\([^)]*\\)|\\[[^]]*]");
    private static final Pattern TITLE_SUFFIX_PATTERN = Pattern.compile(
            "\\s+-\\s+(?:remaster(?:ed)?|live|version|edit|mix).*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final SpeakerPresentation[] SPEAKER_PALETTE = new SpeakerPresentation[]{
            new SpeakerPresentation("CUSTOM", "#a8ccff", "MALE 1"),
            new SpeakerPresentation("CUSTOM", "#ffb8c7", "FEMALE 1"),
            new SpeakerPresentation("CUSTOM", "#e4d8ff", "DUET 1"),
            new SpeakerPresentation("CUSTOM", "#9ae8d4", "MALE 2"),
            new SpeakerPresentation("CUSTOM", "#ffd6b3", "FEMALE 2"),
            new SpeakerPresentation("CUSTOM", "#d6e4ff", "DUET 2")
    };

    private static final Set<String> CREDIT_LABELS = setOf(
            "词", "詞", "作词", "作詞", "填词", "填詞", "词曲", "詞曲",
            "曲", "作曲", "编曲", "編曲", "弦编曲", "弦編曲", "弦乐编曲", "弦樂編曲",
            "lyrics", "lyric", "lyricsby", "lyricby", "lyricist",
            "composedby", "composer", "musicby", "arrangedby", "arranger", "stringsarrangedby",
            "producedby", "producer", "制作", "製作", "制作人", "製作人",
            "翻译", "翻譯", "translatedby", "歌手", "演唱", "原唱", "原曲", "录音", "錄音",
            "混音", "和声", "和聲", "vocal", "vocals", "vocalby", "vocalsby",
            "mixby", "mixedby", "mixingby", "masteredby", "masteringby"
    );

    private static final Set<String> CREDIT_NAME_CONNECTORS = setOf(
            "and", "de", "del", "der", "di", "du", "la", "le", "of", "the", "van", "von", "y"
    );

    private static final Set<String> NO_LYRICS_PLACEHOLDERS = setOf(
            "纯音乐请欣赏", "纯音乐请您欣赏", "纯音乐敬请欣赏",
            "純音樂請欣賞", "純音樂請您欣賞",
            "此歌曲为没有填词的纯音乐请您欣赏", "此歌曲為沒有填詞的純音樂請您欣賞",
            "该歌曲为纯音乐请您欣赏", "該歌曲為純音樂請您欣賞",
            "暂无歌词", "暫無歌詞", "没有歌词", "沒有歌詞"
    );

    interface Logger {
        void write(String message);
    }

    private PaxsenixLyricsProvider() {
    }

    static Variants fetchVariants(
            TrackSnapshot track,
            String isrc,
            String spotifyTrackId,
            Logger logger
    ) throws Exception {
        if (track == null || !track.hasUsableMetadata()) return null;

        CompletableFuture<Candidate> catalogFuture = CompletableFuture.supplyAsync(
                () -> fetchCandidateSafely(false, track)
        );
        CompletableFuture<Candidate> structuredFuture = CompletableFuture.supplyAsync(
                () -> fetchCandidateSafely(true, track)
        );
        CompletableFuture.allOf(catalogFuture, structuredFuture).join();

        List<Candidate> candidates = new ArrayList<>();
        Candidate catalog = catalogFuture.getNow(null);
        Candidate structured = structuredFuture.getNow(null);
        if (catalog != null) candidates.add(catalog);
        if (structured != null) candidates.add(structured);

        for (Candidate candidate : candidates) {
            candidate.variants = parsePayloadVariants(
                    candidate.payload,
                    track.durationMs,
                    isrc,
                    spotifyTrackId,
                    track.title,
                    track.artist
            );
        }
        candidates.removeIf(candidate -> quality(candidate) <= 0d);
        candidates.sort(Comparator.comparingDouble(PaxsenixLyricsProvider::quality).reversed());
        if (candidates.isEmpty()) {
            logger.write("paxsenix: no lyrics found");
            return null;
        }

        Candidate selected = candidates.get(0);
        LyricsResult best = selected.variants.best();
        logger.write("paxsenix loaded: source=" + selected.source
                + " / variants=" + selected.variants.availableTypes()
                + " / bestLines=" + (best == null ? 0 : best.lines.size()));
        return selected.variants;
    }

    private static Candidate fetchCandidateSafely(boolean structured, TrackSnapshot track) {
        try {
            return structured ? fetchStructuredCandidate(track) : fetchCatalogCandidate(track);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Candidate fetchStructuredCandidate(TrackSnapshot track) throws Exception {
        String searchUrl = STRUCTURED_SEARCH_URL + "?q=" + encode(searchTerm(track));
        HttpResult search = get(searchUrl);
        if (search.status < 200 || search.status >= 300) return null;
        Object parsedSearch = parseJson(search.body);
        if (!(parsedSearch instanceof JSONArray)) return null;

        JSONArray results = (JSONArray) parsedSearch;
        List<MatchCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < results.length(); index++) {
            JSONObject item = results.optJSONObject(index);
            if (item == null) continue;
            candidates.add(new MatchCandidate(
                    item.optString("hash", ""),
                    item.optString("title", ""),
                    item.optString("artist", ""),
                    item.optString("album", ""),
                    item.optDouble("duration", 0d)
            ));
        }
        ScoredCandidate match = selectBestCandidate(candidates, track);
        if (match == null) return null;

        for (boolean detailed : new boolean[]{false, true}) {
            String lyricsUrl = STRUCTURED_LYRICS_URL
                    + "?id=" + encode(match.candidate.id)
                    + "&word=" + detailed
                    + "&v=2";
            HttpResult response = get(lyricsUrl);
            Object body = parseJson(response.body);
            if (response.status >= 200 && response.status < 300 && body instanceof JSONObject) {
                JSONObject payload = (JSONObject) body;
                JSONArray lyrics = payload.optJSONArray("lyrics");
                if (lyrics != null && lyrics.length() > 0) {
                    return new Candidate("structured_api", payload, match.score);
                }
            }
        }
        return null;
    }

    private static Candidate fetchCatalogCandidate(TrackSnapshot track) throws Exception {
        String searchUrl = CATALOG_SEARCH_URL
                + "?term=" + encode(searchTerm(track))
                + "&entity=song&limit=25";
        HttpResult search = get(searchUrl);
        Object parsedSearch = parseJson(search.body);
        if (search.status < 200 || search.status >= 300 || !(parsedSearch instanceof JSONObject)) return null;
        JSONArray results = ((JSONObject) parsedSearch).optJSONArray("results");
        if (results == null) return null;

        List<MatchCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < results.length(); index++) {
            JSONObject item = results.optJSONObject(index);
            if (item == null) continue;
            candidates.add(new MatchCandidate(
                    String.valueOf(item.optLong("trackId", 0L)),
                    item.optString("trackName", ""),
                    item.optString("artistName", ""),
                    item.optString("collectionName", ""),
                    item.optDouble("trackTimeMillis", 0d) / 1000d
            ));
        }
        ScoredCandidate match = selectBestCandidate(candidates, track);
        if (match == null) return null;

        String lyricsUrl = CATALOG_LYRICS_URL + "?id=" + encode(match.candidate.id) + "&v=2";
        HttpResult response = get(lyricsUrl);
        Object body = parseJson(response.body);
        if (response.status < 200 || response.status >= 300 || !(body instanceof JSONObject)) return null;
        JSONObject payload = (JSONObject) body;
        JSONArray lyrics = payload.optJSONArray("lyrics");
        if (lyrics == null || lyrics.length() == 0) return null;
        return new Candidate("catalog_api", payload, match.score);
    }

    static Variants parsePayloadVariants(
            JSONObject payload,
            long durationMs,
            String isrc,
            String spotifyTrackId,
            String title,
            String artist
    ) {
        if (payload == null) return null;
        Variants structured = parseStructuredLyrics(payload, durationMs, isrc, spotifyTrackId, title, artist);
        if (structured != null) return structured;

        String ttml = payload.optString("ttmlContent", "");
        if (!ttml.isEmpty()) {
            try {
                UnisonLyricsProvider.ParsedLyrics parsed = UnisonLyricsProvider.parseTtmlLyrics(ttml, durationMs);
                return variantsFromParsed(parsed, isrc, spotifyTrackId);
            } catch (Exception ignored) {
                return null;
            }
        }

        String lrc = payload.optString("lrc", "");
        if (!lrc.isEmpty()) {
            List<LyricsLine> syncedLines = LrcParser.parseSynced(lrc, durationMs);
            if (!syncedLines.isEmpty()) {
                return buildVariants(null, syncedLines, toPlainLines(syncedLines), isrc, spotifyTrackId);
            }
        }
        if (isTargetStructuredPayload(payload) && payload.optJSONArray("lyrics") != null) return null;

        String plain = payload.optString("plain", "");
        if (plain.isEmpty() && payload.opt("lyrics") instanceof String) {
            plain = payload.optString("lyrics", "");
        }
        List<LyricsLine> plainLines = LrcParser.parsePlain(plain);
        return plainLines.isEmpty() ? null : buildVariants(null, null, plainLines, isrc, spotifyTrackId);
    }

    static Variants parseStructuredLyrics(
            JSONObject payload,
            long requestedDurationMs,
            String isrc,
            String spotifyTrackId,
            String title,
            String artist
    ) {
        JSONArray allLines = payload.optJSONArray("lyrics");
        if (allLines == null || allLines.length() == 0) return null;

        Map<Long, String> referenceLines = parseReferenceLines(payload);
        Set<Integer> metadataIndexes = leadingMetadataIndexes(payload, title, artist, referenceLines);
        List<JSONObject> rawLines = new ArrayList<>();
        for (int index = 0; index < allLines.length(); index++) {
            JSONObject line = allLines.optJSONObject(index);
            if (line != null && !metadataIndexes.contains(index)) rawLines.add(line);
        }
        if (rawLines.isEmpty()) return null;
        if (rawLines.size() <= 3 && allPlaceholderLines(rawLines, referenceLines)) return null;

        JSONObject metadata = payload.optJSONObject("metadata");
        long metadataDuration = toMilliseconds(metadata == null ? null : metadata.opt("duration"), 0L);
        long durationMs = requestedDurationMs > 0L ? requestedDurationMs : metadataDuration;
        Map<String, Integer> agentOrder = new LinkedHashMap<>();
        JSONArray agents = metadata == null ? null : metadata.optJSONArray("agents");
        if (agents != null) {
            for (int index = 0; index < agents.length(); index++) {
                JSONObject agent = agents.optJSONObject(index);
                String id = agent == null ? "" : agent.optString("id", "").trim();
                if (!id.isEmpty() && !agentOrder.containsKey(id)) agentOrder.put(id, agentOrder.size());
            }
        }
        for (JSONObject line : rawLines) {
            String agent = line.optString("agent", "").trim();
            if (!agent.isEmpty() && !agentOrder.containsKey(agent)) agentOrder.put(agent, agentOrder.size());
        }

        List<Long> starts = new ArrayList<>();
        for (JSONObject line : rawLines) starts.add(lineStart(line));
        boolean syllableSync = "syllable".equalsIgnoreCase(payload.optString("syncType", ""));
        List<ParsedRow> rows = new ArrayList<>();
        for (int index = 0; index < rawLines.size(); index++) {
            JSONObject source = rawLines.get(index);
            long start = starts.get(index);
            long nextStart = index + 1 < starts.size() ? starts.get(index + 1) : -1L;
            JSONArray leadSource = source.optJSONArray("text");
            JSONArray backgroundSource = source.optJSONArray("backgroundText");
            long end = toMilliseconds(source.opt("endtime"), -1L);
            if (end < 0L && leadSource != null && leadSource.length() > 0) {
                JSONObject last = leadSource.optJSONObject(leadSource.length() - 1);
                end = toMilliseconds(last == null ? null : last.opt("endtime"), -1L);
            }
            if (end < 0L) end = nextStart > start ? nextStart : start + 3_000L;
            if (durationMs > 0L) end = Math.min(end, durationMs);
            if (nextStart >= 0L && end > nextStart + 15_000L) end = nextStart;
            end = Math.max(start + 1L, end);

            SpeakerPresentation presentation = speakerPresentation(source, agentOrder);
            List<LyricsLine.Syllable> lead = parseTimedTokens(
                    leadSource,
                    start,
                    end,
                    referenceLines.get(start)
            );
            List<LyricsLine.Syllable> background = parseTimedTokens(backgroundSource, start, end, null);
            lead = capSyllables(lead, end);
            background = capSyllables(background, end);
            String leadText = joinSyllables(lead);
            if (leadText.isEmpty() && !(source.opt("text") instanceof JSONArray)) {
                leadText = source.optString("text", "").trim();
            }
            String backgroundText = joinSyllables(background);
            if (leadText.isEmpty() && backgroundText.isEmpty()) continue;

            String key = source.optString("key", "").trim();
            if (key.isEmpty()) key = "line-" + (index + 1);
            String agent = source.optString("agent", "").trim();
            LyricsLine line;
            if (syllableSync && !lead.isEmpty()) {
                LyricsLine.VocalPart leadPart = vocalPart(key + "-lead", "lead", leadText, lead, presentation);
                if (!background.isEmpty()) {
                    LyricsLine.VocalPart backgroundPart = vocalPart(
                            key + "-background-1", "background", backgroundText, background, presentation
                    );
                    List<LyricsLine.VocalPart> parts = new ArrayList<>();
                    parts.add(leadPart);
                    parts.add(backgroundPart);
                    long lineStart = Math.min(leadPart.startTimeMs, backgroundPart.startTimeMs);
                    long lineEnd = Math.max(leadPart.endTimeMs, backgroundPart.endTimeMs);
                    line = new LyricsLine(
                            lineStart,
                            lineEnd,
                            leadText + (backgroundText.isEmpty() ? "" : " " + backgroundText),
                            Collections.emptyList(),
                            presentation.speaker,
                            presentation.color,
                            presentation.fallback,
                            "vocal",
                            parts
                    );
                } else {
                    line = new LyricsLine(
                            start, end, leadText, lead,
                            presentation.speaker, presentation.color, presentation.fallback,
                            "vocal", Collections.emptyList()
                    );
                }
            } else if (syllableSync && !background.isEmpty()) {
                line = new LyricsLine(
                        start, end, backgroundText, background,
                        presentation.speaker, presentation.color, presentation.fallback,
                        "vocal", Collections.emptyList()
                );
            } else {
                line = new LyricsLine(
                        start, end,
                        leadText + (backgroundText.isEmpty() ? "" : " " + backgroundText),
                        Collections.emptyList(), presentation.speaker, presentation.color,
                        presentation.fallback, "vocal", Collections.emptyList()
                );
            }
            rows.add(new ParsedRow(index, key, agent, line));
        }
        if (rows.isEmpty()) return null;
        rows.sort((left, right) -> {
            int byTime = Long.compare(left.line.startTimeMs, right.line.startTimeMs);
            return byTime != 0 ? byTime : Integer.compare(left.sourceIndex, right.sourceIndex);
        });

        List<LyricsLine> karaoke = new ArrayList<>();
        if (syllableSync) {
            List<ParallelVocalLineMerger.SourceLine> sources = new ArrayList<>();
            for (ParsedRow row : rows) {
                if (row.line.syllables.isEmpty() && row.line.vocalParts.isEmpty()) continue;
                sources.add(ParallelVocalLineMerger.source(
                        row.sourceIndex,
                        row.key,
                        row.agent,
                        row.line
                ));
            }
            karaoke = ParallelVocalLineMerger.mergeOverlaps(sources);
        }

        List<LyricsLine> synced = new ArrayList<>();
        if (!"none".equalsIgnoreCase(payload.optString("syncType", ""))) {
            for (ParsedRow row : rows) {
                LyricsLine line = row.line;
                synced.add(new LyricsLine(
                        line.startTimeMs, line.endTimeMs, line.text, Collections.emptyList(),
                        line.speaker, line.speakerColor, line.speakerFallback,
                        line.kind, Collections.emptyList()
                ));
            }
        }
        List<LyricsLine> plain = new ArrayList<>();
        for (ParsedRow row : rows) {
            plain.add(new LyricsLine(0L, 0L, row.line.text, Collections.emptyList()));
        }
        return buildVariants(karaoke, synced, plain, isrc, spotifyTrackId);
    }

    static List<LyricsLine.Syllable> parseTimedTokens(
            JSONArray items,
            long fallbackStart,
            long fallbackEnd,
            String referenceText
    ) {
        if (items == null || items.length() == 0) return Collections.emptyList();
        Set<Integer> boundaries = referenceWhitespaceBoundaries(items, referenceText);
        int consumedCharacters = 0;
        List<LyricsLine.Syllable> result = new ArrayList<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) continue;
            String text = item.optString("text", "");
            if (text.isEmpty()) continue;
            JSONObject next = items.optJSONObject(index + 1);
            if (boundaries != null) {
                text = text.trim();
                consumedCharacters += codePointCount(normalizeSpacingCharacters(text));
                if (next != null && boundaries.contains(consumedCharacters)) text += " ";
            } else if (shouldAppendBoundary(item, next, text)) {
                text += " ";
            }
            long start = toMilliseconds(item.opt("timestamp"), fallbackStart);
            long nextStart = next == null ? -1L : toMilliseconds(next.opt("timestamp"), -1L);
            long end = toMilliseconds(item.opt("endtime"), nextStart >= 0L ? nextStart : fallbackEnd);
            result.add(new LyricsLine.Syllable(text, start, Math.max(start + 1L, end)));
        }
        return result;
    }

    private static Set<Integer> referenceWhitespaceBoundaries(JSONArray items, String referenceText) {
        if (referenceText == null || referenceText.isEmpty()) return null;
        StringBuilder tokenText = new StringBuilder();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            String compact = normalizeSpacingCharacters(item == null ? "" : item.optString("text", ""));
            if (compact.isEmpty()) return null;
            tokenText.append(compact);
        }
        if (!tokenText.toString().equals(normalizeSpacingCharacters(referenceText))) return null;

        Set<Integer> boundaries = new HashSet<>();
        int count = 0;
        String[] segments = referenceText.split("(?<=\\s)|(?=\\s)");
        for (String segment : segments) {
            if (segment.trim().isEmpty()) {
                if (count > 0) boundaries.add(count);
            } else {
                count += codePointCount(normalizeSpacingCharacters(segment));
            }
        }
        return boundaries;
    }

    private static boolean shouldAppendBoundary(JSONObject item, JSONObject next, String text) {
        if (next == null || item.optBoolean("part", true) || Character.isWhitespace(text.charAt(text.length() - 1))) {
            return false;
        }
        String nextText = next.optString("text", "");
        if (nextText.isEmpty() || Character.isWhitespace(nextText.charAt(0))) return false;
        int first = nextText.codePointAt(0);
        if (",.;:!?%)]}".indexOf(first) >= 0) return false;
        int last = text.codePointBefore(text.length());
        return "-‐‑‒–—'’".indexOf(last) < 0;
    }

    private static Map<Long, String> parseReferenceLines(JSONObject payload) {
        JSONObject metadata = payload.optJSONObject("metadata");
        JSONObject rawData = metadata == null ? null : metadata.optJSONObject("rawData");
        if (rawData == null) rawData = payload.optJSONObject("rawData");
        String rawText = rawData == null ? "" : rawData.optString("lyrics_text", "");
        Map<Long, String> result = new LinkedHashMap<>();
        for (String line : rawText.split("\\r?\\n")) {
            Matcher match = REFERENCE_LINE_PATTERN.matcher(line);
            if (!match.matches()) continue;
            try {
                long timestamp = Long.parseLong(match.group(1));
                result.put(timestamp, REFERENCE_TOKEN_PATTERN.matcher(match.group(3)).replaceAll(""));
            } catch (NumberFormatException ignored) {
                // Ignore malformed reference timestamps.
            }
        }
        return result;
    }

    private static Set<Integer> leadingMetadataIndexes(
            JSONObject payload,
            String title,
            String artist,
            Map<Long, String> referenceLines
    ) {
        Set<Integer> result = new HashSet<>();
        JSONArray lines = payload.optJSONArray("lyrics");
        if (!isTargetStructuredPayload(payload) || lines == null) return result;
        int creditAnchorIndex = leadingCreditAnchorIndex(lines, referenceLines, title);
        boolean anchored = false;
        boolean creditContinuationAllowed = false;
        int limit = Math.min(lines.length(), 24);
        for (int index = 0; index < limit; index++) {
            JSONObject line = lines.optJSONObject(index);
            if (line == null) break;
            long start = lineStart(line);
            if (index == 0 && start >= 30_000L) break;
            String text = structuredLineText(line, referenceLines.get(start));
            boolean creditAnchoredPrefix = index <= creditAnchorIndex;
            boolean titleHeader = index == 0 && isTitleArtistHeader(text, title, artist);
            boolean credit = isCreditMetadata(text);
            boolean creditContinuation = creditContinuationAllowed && isCreditContinuationMetadata(text);
            boolean copyright = anchored && isCopyrightMetadata(text);
            if (!creditAnchoredPrefix && !titleHeader && !credit && !creditContinuation && !copyright) break;
            if (creditAnchoredPrefix || titleHeader || credit) anchored = true;
            creditContinuationAllowed = credit || creditContinuation;
            result.add(index);
        }
        return result;
    }

    private static int leadingCreditAnchorIndex(
            JSONArray lines,
            Map<Long, String> referenceLines,
            String title
    ) {
        int limit = Math.min(lines.length(), 4);
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            JSONObject line = lines.optJSONObject(index);
            if (line == null) return -1;
            long start = lineStart(line);
            if (start >= 30_000L) return -1;
            entries.add(structuredLineText(line, referenceLines.get(start)));
        }

        int firstCreditIndex = -1;
        for (int index = 0; index < entries.size(); index++) {
            if (isDefiniteCreditMetadata(entries.get(index))) {
                firstCreditIndex = index;
                break;
            }
        }
        if (firstCreditIndex <= 0) return firstCreditIndex;

        StringBuilder leadingText = new StringBuilder();
        for (int index = 0; index < firstCreditIndex; index++) {
            if (leadingText.length() > 0) leadingText.append(' ');
            leadingText.append(entries.get(index));
        }
        String expectedTitle = metadataIdentity(title);
        String combinedLeading = metadataIdentity(leadingText.toString());
        if (metadataIdentitiesOverlap(expectedTitle, combinedLeading)) return firstCreditIndex;

        int consecutiveStrongCredits = 0;
        for (int index = firstCreditIndex;
             index < entries.size() && isDefiniteCreditMetadata(entries.get(index));
             index++) {
            consecutiveStrongCredits++;
        }
        return consecutiveStrongCredits >= 2
                && isTitleArtistSeparatorHeader(entries.get(firstCreditIndex - 1))
                ? firstCreditIndex
                : -1;
    }

    private static boolean metadataIdentitiesOverlap(String expected, String actual) {
        if (expected.isEmpty() || actual.isEmpty()) return false;
        if (expected.equals(actual)) return true;
        return Math.min(expected.length(), actual.length()) >= 4
                && (expected.contains(actual) || actual.contains(expected));
    }

    private static boolean isTitleArtistSeparatorHeader(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty() || normalized.length() > 220
                || normalized.indexOf(':') >= 0 || normalized.indexOf('：') >= 0) {
            return false;
        }
        for (int offset = 0; offset < normalized.length(); ) {
            int point = normalized.codePointAt(offset);
            int width = Character.charCount(point);
            if ("-‐‑‒–—".indexOf(point) >= 0
                    && offset > 0 && offset + width < normalized.length()
                    && Character.isWhitespace(normalized.codePointBefore(offset))
                    && Character.isWhitespace(normalized.codePointAt(offset + width))
                    && !normalized.substring(0, offset).trim().isEmpty()
                    && !normalized.substring(offset + width).trim().isEmpty()) {
                return true;
            }
            offset += width;
        }
        return false;
    }

    static boolean isTitleArtistHeader(String text, String title, String artist) {
        String expectedTitle = metadataIdentity(title);
        if (expectedTitle.isEmpty()) return false;
        String expectedArtist = metadataIdentity(artist);
        String value = text == null ? "" : text;
        for (int offset = 0; offset < value.length(); ) {
            int point = value.codePointAt(offset);
            int width = Character.charCount(point);
            if ("-‐‑‒–—".indexOf(point) >= 0) {
                String left = metadataIdentity(value.substring(0, offset));
                String right = metadataIdentity(value.substring(offset + width));
                if (!left.isEmpty() && !right.isEmpty()) {
                    if (left.equals(expectedTitle)) return true;
                    boolean artistMatches = expectedArtist.isEmpty()
                            || left.equals(expectedArtist)
                            || left.contains(expectedArtist)
                            || expectedArtist.contains(left);
                    if (right.equals(expectedTitle) && artistMatches) return true;
                }
            }
            offset += width;
        }
        return false;
    }

    static boolean isCreditMetadata(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).trim();
        int ascii = normalized.indexOf(':');
        int fullWidth = normalized.indexOf('：');
        int separator = ascii < 0 ? fullWidth : (fullWidth < 0 ? ascii : Math.min(ascii, fullWidth));
        if (separator <= 0 || normalized.substring(separator + 1).trim().isEmpty()) return false;
        String label = normalized.substring(0, separator)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s._-]+", "");
        return CREDIT_LABELS.contains(label);
    }

    private static boolean isDefiniteCreditMetadata(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).trim();
        if (!isCreditMetadata(normalized)) return false;
        int separator = normalized.indexOf(':');
        if (separator < 0) separator = normalized.indexOf('：');
        String value = separator < 0 ? "" : normalized.substring(separator + 1).trim();
        return isContributorNameList(value);
    }

    private static boolean isContributorNameList(String text) {
        return isContributorNameList(text, false);
    }

    private static boolean isContributorNameList(String text, boolean requireEveryWord) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty() || normalized.length() > 240) return false;
        String[] names = normalized.split("[/／⁄,，、]", -1);
        if (names.length == 0) return false;
        for (String name : names) {
            if (!isContributorNameSegment(name, requireEveryWord)) return false;
        }
        return true;
    }

    private static boolean isContributorNameSegment(String text, boolean requireEveryWord) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty() || normalized.length() > 64
                || !normalized.matches(".*[\\p{L}\\p{N}].*")
                || !normalized.matches("[\\p{L}\\p{M}\\p{N}\\s.'’‘`´&+()\\-·・]+")) {
            return false;
        }
        if (containsEastAsianScript(normalized)) return true;

        Matcher words = Pattern.compile(
                "[\\p{L}\\p{M}\\p{N}]+(?:[.'’‘`´-][\\p{L}\\p{M}\\p{N}]+)*"
        ).matcher(normalized);
        int wordCount = 0;
        boolean hasSignificantWord = false;
        boolean hasNameWord = false;
        boolean everySignificantWordIsName = true;
        while (words.find()) {
            if (++wordCount > 6) return false;
            String word = words.group();
            if (CREDIT_NAME_CONNECTORS.contains(word.toLowerCase(Locale.ROOT))) continue;
            hasSignificantWord = true;
            boolean nameWord = isCapitalizedNameWord(word);
            if (nameWord) hasNameWord = true;
            else everySignificantWordIsName = false;
        }
        return wordCount > 0 && hasSignificantWord
                && (requireEveryWord ? everySignificantWordIsName : hasNameWord);
    }

    private static boolean containsEastAsianScript(String text) {
        for (int offset = 0; offset < text.length(); ) {
            int point = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(point);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
            offset += Character.charCount(point);
        }
        return false;
    }

    private static boolean isCapitalizedNameWord(String word) {
        for (int offset = 0; offset < word.length(); ) {
            int point = word.codePointAt(offset);
            if (Character.isLetter(point)) {
                return Character.isUpperCase(point) || Character.isTitleCase(point);
            }
            offset += Character.charCount(point);
        }
        return word.matches("\\d+");
    }

    static boolean isCreditContinuationMetadata(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).trim();
        if (normalized.length() < 3 || normalized.length() > 180
                || !normalized.matches(".*[\\p{L}\\p{N}]\\s*[/／⁄]\\s*[\\p{L}\\p{N}].*")) {
            return false;
        }
        if (normalized.indexOf(':') >= 0 || normalized.indexOf('：') >= 0
                || normalized.matches(".*[!?。！？].*")) return false;
        return isContributorNameList(normalized, true);
    }

    static boolean isNoLyricsPlaceholder(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
        return NO_LYRICS_PLACEHOLDERS.contains(normalized);
    }

    private static boolean allPlaceholderLines(List<JSONObject> lines, Map<Long, String> references) {
        for (JSONObject line : lines) {
            JSONArray background = line.optJSONArray("backgroundText");
            if (background != null && background.length() > 0 && !joinJsonText(background).trim().isEmpty()) return false;
            long start = lineStart(line);
            if (!isNoLyricsPlaceholder(structuredLineText(line, references.get(start)))) return false;
        }
        return true;
    }

    private static boolean isTargetStructuredPayload(JSONObject payload) {
        if (STRUCTURED_PROVIDER_ID.equalsIgnoreCase(payload.optString("provider", ""))) return true;
        JSONObject metadata = payload.optJSONObject("metadata");
        JSONObject rawData = metadata == null ? null : metadata.optJSONObject("rawData");
        if (rawData == null) rawData = payload.optJSONObject("rawData");
        String format = rawData == null ? "" : rawData.optString("format", "");
        if (format.isEmpty() && metadata != null) format = metadata.optString("format", "");
        return "krc".equalsIgnoreCase(format);
    }

    private static boolean isCopyrightMetadata(String text) {
        String value = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).trim();
        return value.matches("(?iu)^(?:©|℗|ⓒ|\\(c\\)|\\(p\\)|copyright\\b).*");
    }

    private static String structuredLineText(JSONObject line, String referenceText) {
        if (referenceText != null && !referenceText.trim().isEmpty()) return referenceText.trim();
        Object text = line.opt("text");
        if (text instanceof JSONArray) return joinJsonText((JSONArray) text).trim();
        return text == null ? "" : String.valueOf(text).trim();
    }

    private static String joinJsonText(JSONArray values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.optJSONObject(index);
            if (item != null) builder.append(item.optString("text", ""));
        }
        return builder.toString();
    }

    private static long lineStart(JSONObject line) {
        long start = toMilliseconds(line.opt("timestamp"), -1L);
        if (start >= 0L) return start;
        JSONArray text = line.optJSONArray("text");
        JSONObject first = text == null ? null : text.optJSONObject(0);
        return Math.max(0L, toMilliseconds(first == null ? null : first.opt("timestamp"), 0L));
    }

    private static SpeakerPresentation speakerPresentation(JSONObject line, Map<String, Integer> agentOrder) {
        String agent = line.optString("agent", "").trim();
        Integer index = agent.isEmpty() ? null : agentOrder.get(agent);
        if (index == null) index = line.optBoolean("oppositeTurn", false) ? 1 : 0;
        if (index <= 0) return SpeakerPresentation.NORMAL;
        return SPEAKER_PALETTE[(index - 1) % SPEAKER_PALETTE.length];
    }

    private static LyricsLine.VocalPart vocalPart(
            String id,
            String role,
            String text,
            List<LyricsLine.Syllable> syllables,
            SpeakerPresentation presentation
    ) {
        return new LyricsLine.VocalPart(
                id, role, presentation.speaker, presentation.color, presentation.fallback,
                "vocal", text, syllables
        );
    }

    private static List<LyricsLine.Syllable> capSyllables(List<LyricsLine.Syllable> source, long end) {
        if (source.isEmpty()) return source;
        List<LyricsLine.Syllable> result = new ArrayList<>();
        for (LyricsLine.Syllable syllable : source) {
            long cappedEnd = Math.max(syllable.startTimeMs + 1L, Math.min(syllable.endTimeMs, end));
            result.add(new LyricsLine.Syllable(syllable.text, syllable.startTimeMs, cappedEnd));
        }
        return result;
    }

    private static String joinSyllables(List<LyricsLine.Syllable> syllables) {
        StringBuilder builder = new StringBuilder();
        for (LyricsLine.Syllable syllable : syllables) builder.append(syllable.text);
        return builder.toString().trim();
    }

    private static Variants variantsFromParsed(
            UnisonLyricsProvider.ParsedLyrics parsed,
            String isrc,
            String spotifyTrackId
    ) {
        if (parsed == null || parsed.lines.isEmpty()) return null;
        List<LyricsLine> karaoke = parsed.karaoke ? parsed.lines : null;
        List<LyricsLine> synced = parsed.synced ? toSyncedLines(parsed.lines) : null;
        List<LyricsLine> plain = toPlainLines(parsed.lines);
        return buildVariants(karaoke, synced, plain, isrc, spotifyTrackId);
    }

    private static Variants buildVariants(
            List<LyricsLine> karaokeLines,
            List<LyricsLine> syncedLines,
            List<LyricsLine> plainLines,
            String isrc,
            String spotifyTrackId
    ) {
        LyricsResult karaoke = karaokeLines == null || karaokeLines.isEmpty() ? null : new LyricsResult(
                karaokeLines, "Lyrically (Paxsenix) karaoke", ATTRIBUTION, true, isrc, spotifyTrackId
        );
        LyricsResult synced = syncedLines == null || syncedLines.isEmpty() ? null : new LyricsResult(
                syncedLines, "Lyrically (Paxsenix) synced", ATTRIBUTION, false, isrc, spotifyTrackId
        );
        LyricsResult plain = plainLines == null || plainLines.isEmpty() ? null : new LyricsResult(
                plainLines, "Lyrically (Paxsenix) plain", ATTRIBUTION, false, isrc, spotifyTrackId
        );
        Variants variants = new Variants(karaoke, synced, plain);
        return variants.best() == null ? null : variants;
    }

    private static List<LyricsLine> toSyncedLines(List<LyricsLine> source) {
        List<LyricsLine> result = new ArrayList<>();
        for (LyricsLine line : source) {
            if (!line.isTimed()) continue;
            result.add(new LyricsLine(
                    line.startTimeMs, line.endTimeMs, line.text, Collections.emptyList(),
                    line.speaker, line.speakerColor, line.speakerFallback,
                    line.kind, Collections.emptyList()
            ));
        }
        return result;
    }

    private static List<LyricsLine> toPlainLines(List<LyricsLine> source) {
        List<LyricsLine> result = new ArrayList<>();
        if (source == null) return result;
        for (LyricsLine line : source) {
            if (!line.text.trim().isEmpty()) {
                result.add(new LyricsLine(0L, 0L, line.text, Collections.emptyList()));
            }
        }
        return result;
    }

    private static double quality(Candidate candidate) {
        if (candidate == null || candidate.variants == null) return 0d;
        double sourceBonus = "catalog_api".equals(candidate.source) ? 2d : 0d;
        if (candidate.variants.karaoke != null) {
            int backgroundCount = 0;
            for (LyricsLine line : candidate.variants.karaoke.lines) {
                for (LyricsLine.VocalPart part : line.vocalParts) {
                    if ("background".equals(part.role)) backgroundCount++;
                }
            }
            return 3_000d + backgroundCount * 10d + sourceBonus
                    + candidate.variants.karaoke.lines.size() / 1_000d;
        }
        if (candidate.variants.synced != null) {
            return 2_000d + sourceBonus + candidate.variants.synced.lines.size() / 1_000d;
        }
        return candidate.variants.plain == null
                ? 0d
                : 1_000d + sourceBonus + candidate.variants.plain.lines.size() / 1_000d;
    }

    static ScoredCandidate selectBestCandidate(List<MatchCandidate> candidates, TrackSnapshot track) {
        ScoredCandidate best = null;
        for (MatchCandidate candidate : candidates) {
            if (candidate == null || candidate.id.isEmpty()) continue;
            double score = scoreCandidate(candidate, track);
            if (best == null || score > best.score) best = new ScoredCandidate(candidate, score);
        }
        return best != null && best.score >= 45d ? best : null;
    }

    private static double scoreCandidate(MatchCandidate candidate, TrackSnapshot track) {
        double artistScore = scoreText(track.artist, candidate.artist, 30d, false);
        double albumScore = scoreText(track.album, candidate.album, 30d, false);
        double score = scoreText(track.title, candidate.title, 70d, true);
        if (normalizeComparable(track.title).equals(normalizeComparable(candidate.title))) score += 18d;
        score += artistScore + albumScore;
        if (!track.artist.isEmpty() && !candidate.artist.isEmpty() && artistScore == 0d && albumScore == 0d) {
            score -= 72d;
        }
        double expectedDuration = track.durationMs / 1000d;
        if (expectedDuration > 0d && candidate.durationSeconds > 0d) {
            double difference = Math.abs(expectedDuration - candidate.durationSeconds);
            if (difference <= 2d) score += 24d;
            else if (difference <= 5d) score += 18d;
            else if (difference <= 15d) score += 8d;
            else if (difference > 60d) score -= 20d;
        }
        return score;
    }

    private static double scoreText(String expected, String actual, double weight, boolean titleCore) {
        String left = titleCore ? normalizeTitleCore(expected) : normalizeComparable(expected);
        String right = titleCore ? normalizeTitleCore(actual) : normalizeComparable(actual);
        if (left.isEmpty() || right.isEmpty()) return 0d;
        if (left.equals(right)) return weight;
        if (left.contains(right) || right.contains(left)) return weight * 0.78d;
        return weight * 0.62d * tokenOverlap(left, right);
    }

    private static double tokenOverlap(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0d;
        int matches = 0;
        for (String token : leftTokens) if (rightTokens.contains(token)) matches++;
        return matches / (double) Math.max(1, Math.min(leftTokens.size(), rightTokens.size()));
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        for (String token : value.split(" ")) if (token.length() > 1) result.add(token);
        return result;
    }

    private static String normalizeComparable(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[’‘`´]", "'")
                .replaceAll("(?i)\\b(feat(?:uring)?|ft)\\.?\\b", " ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String normalizeTitleCore(String value) {
        String withoutBrackets = BRACKETED_TITLE_PART_PATTERN.matcher(value == null ? "" : value).replaceAll(" ");
        return normalizeComparable(TITLE_SUFFIX_PATTERN.matcher(withoutBrackets).replaceAll(" "));
    }

    private static String metadataIdentity(String value) {
        return normalizeTitleCore(value).replace(" ", "");
    }

    private static String normalizeSpacingCharacters(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replace('“', '"').replace('”', '"').replace('„', '"').replace('‟', '"')
                .replace('‘', '\'').replace('’', '\'').replace('‚', '\'').replace('‛', '\'')
                .replaceAll("\\s+", "");
    }

    private static String searchTerm(TrackSnapshot track) {
        return (track.title + " " + track.artist).trim();
    }

    private static HttpResult get(String endpoint) throws Exception {
        URL url = URI.create(endpoint).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
        connection.setReadTimeout(REQUEST_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/1");
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            return new HttpResult(status, stream == null ? "" : readUtf8(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static Object parseJson(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            return new JSONTokener(body).nextValue();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readUtf8(InputStream stream) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static long toMilliseconds(Object value, long fallback) {
        if (value == null || JSONObject.NULL.equals(value)) return fallback;
        try {
            double number = value instanceof Number
                    ? ((Number) value).doubleValue()
                    : Double.parseDouble(String.valueOf(value));
            return Double.isFinite(number) ? Math.max(0L, Math.round(number)) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int codePointCount(String value) {
        return value.codePointCount(0, value.length());
    }

    private static Set<String> setOf(String... values) {
        Set<String> result = new HashSet<>();
        Collections.addAll(result, values);
        return Collections.unmodifiableSet(result);
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
            List<String> values = new ArrayList<>();
            if (karaoke != null) values.add("karaoke");
            if (synced != null) values.add("synced");
            if (plain != null) values.add("plain");
            return values.toString();
        }
    }

    static final class MatchCandidate {
        final String id;
        final String title;
        final String artist;
        final String album;
        final double durationSeconds;

        MatchCandidate(String id, String title, String artist, String album, double durationSeconds) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.album = album == null ? "" : album;
            this.durationSeconds = durationSeconds;
        }
    }

    static final class ScoredCandidate {
        final MatchCandidate candidate;
        final double score;

        ScoredCandidate(MatchCandidate candidate, double score) {
            this.candidate = candidate;
            this.score = score;
        }
    }

    private static final class Candidate {
        final String source;
        final JSONObject payload;
        final double matchScore;
        Variants variants;

        Candidate(String source, JSONObject payload, double matchScore) {
            this.source = source;
            this.payload = payload;
            this.matchScore = matchScore;
        }
    }

    private static final class ParsedRow {
        final int sourceIndex;
        final String key;
        final String agent;
        final LyricsLine line;

        ParsedRow(int sourceIndex, String key, String agent, LyricsLine line) {
            this.sourceIndex = sourceIndex;
            this.key = key;
            this.agent = agent;
            this.line = line;
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

    private static final class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
