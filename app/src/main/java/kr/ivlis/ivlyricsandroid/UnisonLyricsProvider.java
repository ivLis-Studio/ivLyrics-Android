package kr.ivlis.ivlyricsandroid;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

final class UnisonLyricsProvider {
    private static final String API_BASE = "https://unison.boidu.dev";
    private static final String ATTRIBUTION = "Lyrics from Unison (https://unison.boidu.dev).";
    private static final int REQUEST_TIMEOUT_MS = 10_000;
    private static final Pattern ROOT_TT_PATTERN = Pattern.compile("<tt\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECLARED_PREFIX_PATTERN = Pattern.compile("xmlns:([A-Za-z][\\w.-]*)\\s*=");
    private static final Pattern ELEMENT_PREFIX_PATTERN = Pattern.compile("</?([A-Za-z][\\w.-]*):");
    private static final Pattern ATTRIBUTE_PREFIX_PATTERN = Pattern.compile("\\s([A-Za-z][\\w.-]*):[\\w.-]+\\s*=");
    private static final Pattern LRC_TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern LRC_OFFSET_PATTERN = Pattern.compile("^\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LRC_METADATA_PATTERN = Pattern.compile("^\\[(?:ar|al|ti|by|re|ve|length):", Pattern.CASE_INSENSITIVE);

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

    private UnisonLyricsProvider() {
    }

    static LyricsResult fetch(
            TrackSnapshot track,
            String isrc,
            String spotifyTrackId,
            Logger logger
    ) throws Exception {
        if (track == null || !track.hasUsableMetadata()) {
            return null;
        }

        ResponseData data = fetchLyricsData(track, logger);
        if (data == null) {
            logger.write("unison: no lyrics found");
            return null;
        }

        ParsedLyrics parsed = parseResponseLyrics(data, track.durationMs);
        if (parsed.lines.isEmpty()) {
            logger.write("unison: response has no renderable lyrics");
            return null;
        }

        String type = parsed.karaoke ? "karaoke" : (parsed.synced ? "synced" : "plain");
        logger.write("unison selected: format=" + data.format
                + " / type=" + type
                + " / lines=" + parsed.lines.size()
                + " / vocalParts=" + countVocalParts(parsed.lines));
        return new LyricsResult(
                parsed.lines,
                "Unison " + type,
                ATTRIBUTION,
                parsed.karaoke,
                isrc,
                spotifyTrackId,
                Collections.emptyList()
        );
    }

    static boolean isUnisonResult(LyricsResult result) {
        return result != null && result.providerLabel.startsWith("Unison ");
    }

    private static ResponseData fetchLyricsData(TrackSnapshot track, Logger logger) throws Exception {
        List<String> artists = artistCandidates(track.artist);
        List<RequestAttempt> attempts = new ArrayList<>();
        boolean hasAlbum = !track.album.isEmpty() && !"undefined".equalsIgnoreCase(track.album);
        boolean[] albumOptions = hasAlbum ? new boolean[]{true, false} : new boolean[]{false};
        for (boolean includeAlbum : albumOptions) {
            for (String artist : artists) {
                attempts.add(new RequestAttempt(buildLyricsUrl(track, artist, includeAlbum, true), false));
            }
        }
        if (track.durationMs > 0) {
            for (String artist : artists) {
                attempts.add(new RequestAttempt(buildLyricsUrl(track, artist, false, false), true));
            }
        }

        Map<String, RequestAttempt> unique = new LinkedHashMap<>();
        for (RequestAttempt attempt : attempts) {
            unique.put(attempt.url, attempt);
        }
        int requestIndex = 0;
        for (RequestAttempt attempt : unique.values()) {
            requestIndex++;
            HttpResult response = get(attempt.url);
            JSONObject body = parseJsonObject(response.body);
            if (response.status >= 200 && response.status < 300
                    && body != null
                    && body.optBoolean("success", true)
                    && body.optJSONObject("data") != null) {
                ResponseData data = ResponseData.from(body.optJSONObject("data"));
                if (!data.lyrics.isEmpty()
                        && (!attempt.exactMetadataRequired || isExactMetadataMatch(data, track))) {
                    logger.write("unison: request #" + requestIndex + " matched"
                            + (attempt.exactMetadataRequired ? " / exact metadata verified" : ""));
                    return data;
                }
                if (attempt.exactMetadataRequired && !data.lyrics.isEmpty()) {
                    logger.write("unison: request #" + requestIndex + " rejected by exact metadata check");
                }
                continue;
            }
            if (response.status != 404) {
                String message = body == null ? "" : body.optString("error", "");
                throw new IllegalStateException(message.isEmpty()
                        ? "Unison request failed (" + response.status + ")"
                        : message);
            }
        }
        return null;
    }

    private static JSONObject parseJsonObject(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String buildLyricsUrl(
            TrackSnapshot track,
            String artist,
            boolean includeAlbum,
            boolean includeDuration
    ) {
        List<String> query = new ArrayList<>();
        query.add("song=" + urlEncode(track.title));
        query.add("artist=" + urlEncode(artist));
        if (includeDuration && track.durationMs > 0) {
            query.add("duration=" + Math.round(track.durationMs / 1000.0));
        }
        if (includeAlbum && !track.album.isEmpty() && !"undefined".equalsIgnoreCase(track.album)) {
            query.add("album=" + urlEncode(track.album));
        }
        return API_BASE + "/lyrics?" + String.join("&", query);
    }

    private static HttpResult get(String endpoint) throws Exception {
        URL url = URI.create(endpoint).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
        connection.setReadTimeout(REQUEST_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/0.1");
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            return new HttpResult(status, stream == null ? "" : readBody(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(InputStream stream) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static ParsedLyrics parseResponseLyrics(ResponseData data, long durationMs) throws Exception {
        switch (data.format.toLowerCase(Locale.ROOT)) {
            case "ttml":
                return parseTtmlLyrics(data.lyrics, durationMs);
            case "lrc":
                return parseLrcLyrics(data.lyrics, durationMs);
            case "plain":
                return parsePlainLyrics(data.lyrics);
            default:
                throw new IllegalArgumentException("Unsupported Unison lyrics format: "
                        + (data.format.isEmpty() ? "unknown" : data.format));
        }
    }

    private static ParsedLyrics parseTtmlLyrics(String ttml, long durationMs) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        setXmlFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setXmlFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setXmlFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(declareMissingNamespaces(ttml)))
        );

        Map<String, Integer> agentOrder = new LinkedHashMap<>();
        List<Element> agentElements = elementsByLocalName(document, "agent");
        for (Element agent : agentElements) {
            String id = attribute(agent, "id");
            if (!id.isEmpty() && !agentOrder.containsKey(id)) {
                agentOrder.put(id, agentOrder.size());
            }
        }

        List<ParsedLine> parsedLines = new ArrayList<>();
        List<Element> paragraphs = elementsByLocalName(document, "p");
        for (int lineIndex = 0; lineIndex < paragraphs.size(); lineIndex++) {
            Element paragraph = paragraphs.get(lineIndex);
            Long parsedStart = parseTimeMs(attribute(paragraph, "begin"));
            long startTime = parsedStart == null ? 0L : parsedStart;
            Long explicitEnd = parseTimeMs(attribute(paragraph, "end"));
            Long paragraphDuration = parseTimeMs(attribute(paragraph, "dur"));
            long endTime = explicitEnd != null
                    ? explicitEnd
                    : (paragraphDuration != null ? startTime + paragraphDuration : startTime + 2500L);
            String lineKey = firstNonEmpty(attribute(paragraph, "key"), attribute(paragraph, "id"));
            if (lineKey.isEmpty()) {
                lineKey = "line-" + (lineIndex + 1);
            }
            String lineAgent = attribute(paragraph, "agent");
            addAgentIfNeeded(agentOrder, lineAgent);
            SpeakerPresentation lineSpeaker = speakerPresentation(lineAgent, agentOrder);

            TimedPart lead = parseTimedNodes(paragraph.getChildNodes(), startTime, endTime, true);
            List<LyricsLine.VocalPart> backgrounds = new ArrayList<>();
            for (Element child : childElements(paragraph.getChildNodes())) {
                if (!"x-bg".equalsIgnoreCase(attribute(child, "role"))) {
                    continue;
                }
                String backgroundAgent = firstNonEmpty(attribute(child, "agent"), lineAgent);
                addAgentIfNeeded(agentOrder, backgroundAgent);
                Long parsedBackgroundStart = parseTimeMs(attribute(child, "begin"));
                Long parsedBackgroundEnd = parseTimeMs(attribute(child, "end"));
                TimedPart parsedBackground = stripBackgroundParentheses(parseTimedNodes(
                        child.getChildNodes(),
                        parsedBackgroundStart == null ? startTime : parsedBackgroundStart,
                        parsedBackgroundEnd == null ? endTime : parsedBackgroundEnd,
                        false
                ));
                LyricsLine.VocalPart part = createVocalPart(
                        lineKey + "-background-" + (backgrounds.size() + 1),
                        "background",
                        parsedBackground,
                        speakerPresentation(backgroundAgent, agentOrder)
                );
                if (part != null) {
                    backgrounds.add(part);
                }
            }

            List<String> backgroundTexts = new ArrayList<>();
            for (LyricsLine.VocalPart background : backgrounds) {
                backgroundTexts.add(background.text);
            }
            LyricsLine.VocalPart leadPart = createVocalPart(
                    lineKey + "-lead", "lead", lead, lineSpeaker
            );
            if (leadPart == null && !backgrounds.isEmpty() && !lead.text.isEmpty()) {
                leadPart = createVocalPart(
                        lineKey + "-lead",
                        "lead",
                        new TimedPart(lead.text, Collections.singletonList(
                                new LyricsLine.Syllable(lead.text, startTime, endTime)
                        ), true),
                        lineSpeaker
                );
            }
            if (leadPart == null && !backgrounds.isEmpty()) {
                LyricsLine.VocalPart promoted = backgrounds.remove(0);
                leadPart = new LyricsLine.VocalPart(
                        lineKey + "-lead",
                        "lead",
                        promoted.speaker,
                        promoted.speakerColor,
                        promoted.speakerFallback,
                        promoted.kind,
                        promoted.text,
                        promoted.syllables
                );
            }

            String displayText;
            if (!backgroundTexts.isEmpty()) {
                List<String> displayParts = new ArrayList<>();
                if (!lead.text.isEmpty()) {
                    displayParts.add(lead.text);
                }
                displayParts.addAll(backgroundTexts);
                displayText = normalizeDisplayText(String.join(" ", displayParts));
            } else {
                displayText = firstNonEmpty(normalizeDisplayText(paragraph.getTextContent()), lead.text);
            }
            if (displayText.isEmpty()) {
                continue;
            }

            List<LyricsLine.VocalPart> allParts = new ArrayList<>();
            if (leadPart != null) {
                allParts.add(leadPart);
            }
            allParts.addAll(backgrounds);
            long resolvedStart = startTime;
            long resolvedEnd = endTime;
            for (LyricsLine.VocalPart part : allParts) {
                resolvedStart = Math.min(resolvedStart, part.startTimeMs);
                resolvedEnd = Math.max(resolvedEnd, part.endTimeMs);
            }
            resolvedEnd = Math.max(resolvedStart + 1L, resolvedEnd);

            List<LyricsLine.Syllable> lineSyllables = Collections.emptyList();
            List<LyricsLine.VocalPart> vocalParts = Collections.emptyList();
            boolean hasWordTiming;
            if (!backgrounds.isEmpty() && leadPart != null) {
                vocalParts = allParts;
                hasWordTiming = true;
            } else if (leadPart != null && !backgroundTexts.isEmpty()) {
                lineSyllables = leadPart.syllables;
                hasWordTiming = true;
            } else if (!lead.syllables.isEmpty()) {
                lineSyllables = lead.syllables;
                hasWordTiming = lead.hasTimedText;
            } else {
                hasWordTiming = false;
            }

            parsedLines.add(new ParsedLine(
                    resolvedStart,
                    resolvedEnd,
                    displayText,
                    lineSyllables,
                    lineSpeaker,
                    vocalParts,
                    hasWordTiming
            ));
        }

        parsedLines.sort((left, right) -> Long.compare(left.startTimeMs, right.startTimeMs));
        boolean karaoke = false;
        for (ParsedLine line : parsedLines) {
            karaoke = karaoke || line.hasWordTiming;
        }
        List<LyricsLine> lines = new ArrayList<>();
        for (ParsedLine line : parsedLines) {
            List<LyricsLine.Syllable> syllables = line.syllables;
            if (karaoke && syllables.isEmpty() && line.vocalParts.isEmpty()) {
                syllables = Collections.singletonList(new LyricsLine.Syllable(
                        line.text, line.startTimeMs, line.endTimeMs
                ));
            }
            lines.add(new LyricsLine(
                    line.startTimeMs,
                    line.endTimeMs,
                    line.text,
                    karaoke ? syllables : Collections.emptyList(),
                    line.speaker.speaker,
                    line.speaker.color,
                    line.speaker.fallback,
                    "vocal",
                    karaoke ? line.vocalParts : Collections.emptyList()
            ));
        }
        return new ParsedLyrics(lines, karaoke, !lines.isEmpty());
    }

    private static TimedPart parseTimedNodes(
            NodeList nodes,
            long fallbackStart,
            long fallbackEnd,
            boolean excludeBackground
    ) {
        TimedPartBuilder state = new TimedPartBuilder(fallbackStart, fallbackEnd);
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                String rawText = node.getNodeValue() == null ? "" : node.getNodeValue();
                if (rawText.trim().isEmpty()) {
                    if (state.text.length() > 0
                            && state.text.charAt(state.text.length() - 1) != ' '
                            && hasContentAfter(nodes, index)) {
                        long boundary = state.syllables.isEmpty()
                                ? state.fallbackStart
                                : state.syllables.get(state.syllables.size() - 1).endTimeMs;
                        appendText(state, " ", boundary, boundary, state.hasTimedText);
                    }
                    continue;
                }
                appendText(state, rawText, state.fallbackStart, state.fallbackEnd, false);
                continue;
            }
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            if (excludeBackground && "x-bg".equalsIgnoreCase(attribute(element, "role"))) {
                continue;
            }
            if ("br".equals(localName(element))) {
                appendText(state, " ", state.fallbackStart, state.fallbackStart, state.hasTimedText);
                continue;
            }

            Long elementStart = parseTimeMs(attribute(element, "begin"));
            Long explicitEnd = parseTimeMs(attribute(element, "end"));
            Long duration = parseTimeMs(attribute(element, "dur"));
            long start = elementStart == null ? state.fallbackStart : elementStart;
            long end = explicitEnd != null
                    ? explicitEnd
                    : (duration != null ? start + duration : state.fallbackEnd);
            if (!childElements(element.getChildNodes()).isEmpty()) {
                TimedPart nested = parseTimedNodes(element.getChildNodes(), start, end, excludeBackground);
                state.text.append(nested.text);
                state.syllables.addAll(nested.syllables);
                state.hasTimedText = state.hasTimedText || nested.hasTimedText;
                continue;
            }
            boolean hasExplicitTiming = elementStart != null || explicitEnd != null || duration != null;
            appendText(state, element.getTextContent(), start, end, hasExplicitTiming);
        }

        String text = state.text.toString().trim();
        trimEmptySyllables(state.syllables);
        return new TimedPart(text, state.syllables, state.hasTimedText);
    }

    private static void appendText(
            TimedPartBuilder state,
            String rawText,
            long startTime,
            long endTime,
            boolean timed
    ) {
        String text = normalizeInlineText(rawText);
        if (text.isEmpty()) {
            return;
        }
        if (state.text.length() == 0) {
            text = trimStart(text);
        }
        if (state.text.length() > 0
                && state.text.charAt(state.text.length() - 1) == ' '
                && text.startsWith(" ")) {
            text = text.substring(1);
        }
        if (text.isEmpty()) {
            return;
        }
        state.text.append(text);
        if (!timed) {
            return;
        }
        long start = Math.max(0L, startTime);
        long end = Math.max(start + 1L, endTime >= start ? endTime : state.fallbackEnd);
        state.syllables.add(new LyricsLine.Syllable(text, start, end));
        state.hasTimedText = true;
    }

    private static TimedPart stripBackgroundParentheses(TimedPart part) {
        List<LyricsLine.Syllable> syllables = new ArrayList<>();
        for (LyricsLine.Syllable syllable : part.syllables) {
            String text = syllable.text.replaceAll("[()（）]", "");
            if (!text.isEmpty()) {
                syllables.add(new LyricsLine.Syllable(text, syllable.startTimeMs, syllable.endTimeMs));
            }
        }
        trimEmptySyllables(syllables);
        return new TimedPart(
                normalizeDisplayText(part.text.replaceAll("[()（）]", "")),
                syllables,
                part.hasTimedText
        );
    }

    private static LyricsLine.VocalPart createVocalPart(
            String id,
            String role,
            TimedPart part,
            SpeakerPresentation speaker
    ) {
        if (part == null || part.text.isEmpty() || part.syllables.isEmpty()) {
            return null;
        }
        return new LyricsLine.VocalPart(
                id,
                role,
                speaker.speaker,
                speaker.color,
                speaker.fallback,
                "vocal",
                part.text,
                part.syllables
        );
    }

    private static ParsedLyrics parseLrcLyrics(String lrc, long durationMs) {
        String clean = stripBom(lrc);
        String[] rawLines = clean.split("\\r?\\n", -1);
        long offset = 0L;
        List<LrcLine> synced = new ArrayList<>();
        for (String rawLine : rawLines) {
            Matcher offsetMatcher = LRC_OFFSET_PATTERN.matcher(rawLine);
            if (offsetMatcher.find()) {
                offset = parseLong(offsetMatcher.group(1), 0L);
                continue;
            }
            if (LRC_METADATA_PATTERN.matcher(rawLine).find()) {
                continue;
            }
            Matcher matcher = LRC_TIMESTAMP_PATTERN.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            while (matcher.find()) {
                timestamps.add(parseLrcTimestamp(matcher.group(1), matcher.group(2), matcher.group(3)));
            }
            if (timestamps.isEmpty()) {
                continue;
            }
            String text = LRC_TIMESTAMP_PATTERN.matcher(rawLine).replaceAll("").trim();
            if (text.isEmpty()) {
                continue;
            }
            for (long timestamp : timestamps) {
                synced.add(new LrcLine(Math.max(0L, timestamp + offset), text));
            }
        }
        synced.sort((left, right) -> Long.compare(left.startTimeMs, right.startTimeMs));
        if (synced.isEmpty()) {
            return parsePlainLyrics(lrc);
        }
        List<LyricsLine> lines = new ArrayList<>();
        for (int index = 0; index < synced.size(); index++) {
            LrcLine line = synced.get(index);
            long end = index + 1 < synced.size()
                    ? synced.get(index + 1).startTimeMs
                    : (durationMs > 0 ? durationMs : line.startTimeMs + 3000L);
            lines.add(new LyricsLine(line.startTimeMs, Math.max(line.startTimeMs + 1L, end), line.text, Collections.emptyList()));
        }
        return new ParsedLyrics(lines, false, true);
    }

    private static ParsedLyrics parsePlainLyrics(String plain) {
        List<LyricsLine> lines = new ArrayList<>();
        for (String rawLine : stripBom(plain).split("\\r?\\n", -1)) {
            String text = rawLine.trim();
            if (!text.isEmpty()) {
                lines.add(new LyricsLine(0L, 0L, text, Collections.emptyList()));
            }
        }
        return new ParsedLyrics(lines, false, false);
    }

    private static Long parseTimeMs(String value) {
        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) {
            return null;
        }
        Matcher offset = Pattern.compile("^([+-]?[\\d.]+)(ms|h|m|s)$", Pattern.CASE_INSENSITIVE).matcher(input);
        if (offset.matches()) {
            try {
                double amount = Double.parseDouble(offset.group(1));
                String unit = offset.group(2).toLowerCase(Locale.ROOT);
                double multiplier = "h".equals(unit)
                        ? 3_600_000.0
                        : ("m".equals(unit) ? 60_000.0 : ("s".equals(unit) ? 1000.0 : 1.0));
                return Math.round(amount * multiplier);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        String[] parts = input.split(":", -1);
        if (parts.length < 1 || parts.length > 3) {
            return null;
        }
        try {
            double seconds;
            if (parts.length == 3) {
                seconds = Double.parseDouble(parts[0]) * 3600.0
                        + Double.parseDouble(parts[1]) * 60.0
                        + Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                seconds = Double.parseDouble(parts[0]) * 60.0 + Double.parseDouble(parts[1]);
            } else {
                seconds = Double.parseDouble(parts[0]);
            }
            return Math.round(seconds * 1000.0);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long parseLrcTimestamp(String minutes, String seconds, String fraction) {
        long fractionMs = 0L;
        if (fraction != null && !fraction.isEmpty()) {
            if (fraction.length() == 1) {
                fractionMs = parseLong(fraction, 0L) * 100L;
            } else if (fraction.length() == 2) {
                fractionMs = parseLong(fraction, 0L) * 10L;
            } else {
                fractionMs = parseLong(fraction.substring(0, Math.min(3, fraction.length())), 0L);
            }
        }
        return parseLong(minutes, 0L) * 60_000L + parseLong(seconds, 0L) * 1000L + fractionMs;
    }

    private static SpeakerPresentation speakerPresentation(String agentId, Map<String, Integer> agentOrder) {
        if (agentId == null || agentId.isEmpty()) {
            return SpeakerPresentation.EMPTY;
        }
        int index = Math.max(0, agentOrder.getOrDefault(agentId, 0));
        if (index == 0) {
            return new SpeakerPresentation("NORMAL", "", "");
        }
        return SPEAKER_PALETTE[(index - 1) % SPEAKER_PALETTE.length];
    }

    private static void addAgentIfNeeded(Map<String, Integer> agentOrder, String agentId) {
        if (agentId != null && !agentId.isEmpty() && !agentOrder.containsKey(agentId)) {
            agentOrder.put(agentId, agentOrder.size());
        }
    }

    private static String declareMissingNamespaces(String xml) {
        String input = xml == null ? "" : xml;
        Matcher rootMatcher = ROOT_TT_PATTERN.matcher(input);
        if (!rootMatcher.find()) {
            return input;
        }
        String rootTag = rootMatcher.group();
        Set<String> declared = new HashSet<>();
        declared.add("xml");
        declared.add("xmlns");
        Matcher declaredMatcher = DECLARED_PREFIX_PATTERN.matcher(rootTag);
        while (declaredMatcher.find()) {
            declared.add(declaredMatcher.group(1));
        }
        Set<String> used = new HashSet<>();
        Matcher elementMatcher = ELEMENT_PREFIX_PATTERN.matcher(input);
        while (elementMatcher.find()) {
            used.add(elementMatcher.group(1));
        }
        Matcher attributeMatcher = ATTRIBUTE_PREFIX_PATTERN.matcher(input);
        while (attributeMatcher.find()) {
            used.add(attributeMatcher.group(1));
        }
        used.removeAll(declared);
        if (used.isEmpty()) {
            return input;
        }
        StringBuilder declarations = new StringBuilder();
        for (String prefix : used) {
            declarations.append(" xmlns:")
                    .append(prefix)
                    .append("=\"urn:ivlyrics:unison:")
                    .append(prefix)
                    .append("\"");
        }
        String replacement = rootTag.substring(0, rootTag.length() - 1) + declarations + ">";
        return input.substring(0, rootMatcher.start()) + replacement + input.substring(rootMatcher.end());
    }

    private static void setXmlFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (Exception ignored) {
            // Android XML implementations differ by API level.
        }
    }

    private static List<Element> elementsByLocalName(Document document, String name) {
        List<Element> result = new ArrayList<>();
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Node node = elements.item(index);
            if (node instanceof Element && name.equals(localName(node))) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static List<Element> childElements(NodeList nodes) {
        List<Element> result = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String localName(Node node) {
        String local = node.getLocalName();
        if (local != null && !local.isEmpty()) {
            return local;
        }
        String name = node.getNodeName();
        int separator = name.indexOf(':');
        return separator >= 0 ? name.substring(separator + 1) : name;
    }

    private static String attribute(Element element, String localName) {
        String direct = element.getAttribute(localName);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (localName.equals(localName(attribute)) || localName.equals(attribute.getNodeName())) {
                return attribute.getNodeValue() == null ? "" : attribute.getNodeValue();
            }
        }
        return "";
    }

    private static boolean hasContentAfter(NodeList nodes, int index) {
        for (int next = index + 1; next < nodes.getLength(); next++) {
            Node node = nodes.item(next);
            if ((node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE)
                    && node.getNodeValue() != null
                    && !node.getNodeValue().trim().isEmpty()) {
                return true;
            }
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && !normalizeDisplayText(node.getTextContent()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeInlineText(String value) {
        return (value == null ? "" : value).replaceAll("[\\r\\n\\t\\f\\u000B ]+", " ");
    }

    private static String normalizeDisplayText(String value) {
        return normalizeInlineText(value).trim();
    }

    private static String trimStart(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return value.substring(index);
    }

    private static void trimEmptySyllables(List<LyricsLine.Syllable> syllables) {
        while (!syllables.isEmpty() && syllables.get(0).text.trim().isEmpty()) {
            syllables.remove(0);
        }
        while (!syllables.isEmpty() && syllables.get(syllables.size() - 1).text.trim().isEmpty()) {
            syllables.remove(syllables.size() - 1);
        }
    }

    private static List<String> artistCandidates(String artistInput) {
        String artist = artistInput == null ? "" : artistInput.trim();
        if (artist.isEmpty()) {
            return Collections.emptyList();
        }
        String[] split = artist.split("(?i)\\s*(?:,|;|\\bfeat\\.?\\b|\\bfeaturing\\b|\\s&\\s)\\s*", 2);
        List<String> result = new ArrayList<>();
        result.add(artist);
        if (split.length > 0 && !split[0].trim().isEmpty() && !artist.equals(split[0].trim())) {
            result.add(split[0].trim());
        }
        return result;
    }

    private static boolean isExactMetadataMatch(ResponseData data, TrackSnapshot track) {
        if (!normalizeMetadata(data.song).equals(normalizeMetadata(track.title))) {
            return false;
        }
        String actualArtist = normalizeMetadata(data.artist);
        for (String candidate : artistCandidates(track.artist)) {
            if (!actualArtist.isEmpty() && actualArtist.equals(normalizeMetadata(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeMetadata(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String stripBom(String value) {
        String input = value == null ? "" : value;
        return input.startsWith("\uFEFF") ? input.substring(1) : input;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int countVocalParts(List<LyricsLine> lines) {
        int count = 0;
        for (LyricsLine line : lines) {
            count += line.vocalParts.size();
        }
        return count;
    }

    private static final class RequestAttempt {
        final String url;
        final boolean exactMetadataRequired;

        RequestAttempt(String url, boolean exactMetadataRequired) {
            this.url = url;
            this.exactMetadataRequired = exactMetadataRequired;
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

    private static final class ResponseData {
        final String lyrics;
        final String format;
        final String song;
        final String artist;

        ResponseData(String lyrics, String format, String song, String artist) {
            this.lyrics = lyrics;
            this.format = format;
            this.song = song;
            this.artist = artist;
        }

        static ResponseData from(JSONObject data) {
            return new ResponseData(
                    data == null ? "" : data.optString("lyrics", ""),
                    data == null ? "" : data.optString("format", ""),
                    data == null ? "" : data.optString("song", ""),
                    data == null ? "" : data.optString("artist", "")
            );
        }
    }

    private static final class SpeakerPresentation {
        static final SpeakerPresentation EMPTY = new SpeakerPresentation("", "", "");

        final String speaker;
        final String color;
        final String fallback;

        SpeakerPresentation(String speaker, String color, String fallback) {
            this.speaker = speaker;
            this.color = color;
            this.fallback = fallback;
        }
    }

    private static final class TimedPartBuilder {
        final StringBuilder text = new StringBuilder();
        final List<LyricsLine.Syllable> syllables = new ArrayList<>();
        final long fallbackStart;
        final long fallbackEnd;
        boolean hasTimedText;

        TimedPartBuilder(long fallbackStart, long fallbackEnd) {
            this.fallbackStart = Math.max(0L, fallbackStart);
            this.fallbackEnd = Math.max(this.fallbackStart + 1L, fallbackEnd);
        }
    }

    private static final class TimedPart {
        final String text;
        final List<LyricsLine.Syllable> syllables;
        final boolean hasTimedText;

        TimedPart(String text, List<LyricsLine.Syllable> syllables, boolean hasTimedText) {
            this.text = text == null ? "" : text;
            this.syllables = syllables == null ? Collections.emptyList() : syllables;
            this.hasTimedText = hasTimedText;
        }
    }

    private static final class ParsedLine {
        final long startTimeMs;
        final long endTimeMs;
        final String text;
        final List<LyricsLine.Syllable> syllables;
        final SpeakerPresentation speaker;
        final List<LyricsLine.VocalPart> vocalParts;
        final boolean hasWordTiming;

        ParsedLine(
                long startTimeMs,
                long endTimeMs,
                String text,
                List<LyricsLine.Syllable> syllables,
                SpeakerPresentation speaker,
                List<LyricsLine.VocalPart> vocalParts,
                boolean hasWordTiming
        ) {
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.text = text;
            this.syllables = syllables;
            this.speaker = speaker;
            this.vocalParts = vocalParts;
            this.hasWordTiming = hasWordTiming;
        }
    }

    private static final class ParsedLyrics {
        final List<LyricsLine> lines;
        final boolean karaoke;
        final boolean synced;

        ParsedLyrics(List<LyricsLine> lines, boolean karaoke, boolean synced) {
            this.lines = lines;
            this.karaoke = karaoke;
            this.synced = synced;
        }
    }

    private static final class LrcLine {
        final long startTimeMs;
        final String text;

        LrcLine(long startTimeMs, String text) {
            this.startTimeMs = startTimeMs;
            this.text = text;
        }
    }
}
