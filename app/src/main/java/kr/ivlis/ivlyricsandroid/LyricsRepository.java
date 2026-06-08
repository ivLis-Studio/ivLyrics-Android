package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LyricsRepository {
    private static final String TAG = "ivLyricsDebug";
    private static final String LRCLIB_BASE = "https://lrclib.net/api";
    private static final String SYNC_DATA_BASE = "https://lyrics.api.ivl.is/lyrics/sync-data";
    private static final String SYNC_DATA_SPOTIFY_ORIGIN = "https://xpui.app.spotify.com";
    private static final String SYNC_DATA_SPOTIFY_REFERER = "https://xpui.app.spotify.com/";
    private static final String SPOTIFY_ACCOUNTS_TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";
    private static final String SPOTIFY_SEARCH_BASE = "https://api.spotify.com/v1/search";
    private static final String SPOTIFY_TRACK_BASE = "https://api.spotify.com/v1/tracks/";
    private static final String SPOTIFY_TOKEN_PREFS = "spotify_token_cache";
    private static final String KEY_SPOTIFY_ACCESS_TOKEN = "access_token";
    private static final String KEY_SPOTIFY_TOKEN_ISSUED_AT_MS = "issued_at_ms";
    private static final String KEY_SPOTIFY_TOKEN_EXPIRES_AT_MS = "expires_at_ms";
    private static final String KEY_SPOTIFY_TOKEN_SOURCE = "source_key";
    private static final String LRCLIB_PROVIDER_ID = "lrclib";
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 35_000;
    private static final long SPOTIFY_TOKEN_MAX_AGE_MS = 50L * 60L * 1_000L;
    private static final long SPOTIFY_TOKEN_REFRESH_GRACE_MS = 30_000L;
    private static final double DURATION_TOLERANCE_SECONDS = 15.0;
    private static final String LRCLIB_METADATA_LINE_PATTERN = "^\\s*\\[(?:ar|al|ti|au|length|by|offset|re|ve):[^\\]]*\\]\\s*$";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, LyricsResult> cache = new HashMap<>();
    private final SharedPreferences spotifyTokenPrefs;
    private final AiLyricsSettings aiLyricsSettings;
    private final LyricsDiskCache diskCache;
    private String spotifyAccessToken = "";
    private String spotifyTokenSourceKey = "";
    private long spotifyTokenIssuedAtMs = 0L;
    private long spotifyTokenExpiresAtMs = 0L;

    LyricsRepository(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        spotifyTokenPrefs = appContext == null
                ? null
                : appContext.getSharedPreferences(SPOTIFY_TOKEN_PREFS, Context.MODE_PRIVATE);
        aiLyricsSettings = appContext == null ? null : new AiLyricsSettings(appContext);
        diskCache = appContext == null
                ? null
                : new LyricsDiskCache(appContext, "base_lyrics", 350);
        restoreSpotifyToken();
    }

    interface Callback {
        void onLyricsLoaded(String trackKey, LyricsResult result);

        void onLyricsError(String trackKey, String message);

        void onLyricsLog(String trackKey, String message);

        void onLyricsArtworkLoaded(String trackKey, Bitmap artwork, String artworkKey);
    }

    interface SpotifyTokenValidationCallback {
        void onSpotifyTokenValidated(long expiresInSeconds);

        void onSpotifyTokenValidationFailed(String message);

        void onSpotifyTokenValidationLog(String message);
    }

    private interface LogSink {
        void write(String message);
    }

    private String ui(String key) {
        String lang = aiLyricsSettings == null ? "en" : aiLyricsSettings.snapshot().uiLang;
        return AppI18n.t(lang, key);
    }

    void loadLyrics(TrackSnapshot track, Callback callback) {
        if (track == null || !track.hasUsableMetadata()) {
            callback.onLyricsLoaded("", LyricsResult.empty(ui("repo.metadata_waiting")));
            return;
        }

        String key = track.stableKey();
        LyricsResult cached = cache.get(key);
        if (cached != null) {
            emitLog(key, callback, "cache hit: " + track.title + " / " + track.artist);
            callback.onLyricsLoaded(key, cached);
            return;
        }

        emitLog(key, callback, "track: \"" + track.title + "\" / \"" + track.artist + "\""
                + (track.album.isEmpty() ? "" : " / album=\"" + track.album + "\"")
                + " / duration=" + track.durationMs + "ms"
                + (track.isrc.isEmpty() ? "" : " / player ISRC=" + track.isrc));

        executor.execute(() -> {
            LogSink log = message -> emitLog(key, callback, message);
            try {
                LyricsResult diskCached = diskCache == null ? null : diskCache.get(key);
                if (diskCached != null) {
                    cache.put(key, diskCached);
                    log.write("disk cache hit: sync-data/LRCLIB lyrics"
                            + " / contributors=" + diskCached.contributors.size());
                    mainHandler.post(() -> callback.onLyricsLoaded(key, diskCached));
                    return;
                }
                LyricsResult result = loadLyricsBlocking(track, key, callback, log);
                cache.put(key, result);
                if (diskCache != null) {
                    diskCache.put(key, result);
                }
                mainHandler.post(() -> callback.onLyricsLoaded(key, result));
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                log.write("error: " + message);
                mainHandler.post(() -> callback.onLyricsError(key, message));
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }

    void clearCache() {
        cache.clear();
        if (diskCache != null) {
            diskCache.clear();
        }
    }

    void clearCacheForTrack(String trackKey) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return;
        }
        cache.remove(key);
        if (diskCache != null) {
            diskCache.remove(key);
        }
    }

    void clearSpotifyTokenCache() {
        invalidateSpotifyToken();
    }

    void validateSpotifyCredentials(
            String clientId,
            String clientSecret,
            SpotifyTokenValidationCallback callback
    ) {
        SpotifyCredentials credentials = new SpotifyCredentials(clientId, clientSecret);
        executor.execute(() -> {
            LogSink log = message -> {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSpotifyTokenValidationLog(message));
                }
            };
            try {
                if (!credentials.configured()) {
                    throw new IOException(credentials.partial()
                            ? ui("spotify.error.incomplete_credentials")
                            : ui("spotify.error.credentials_not_configured"));
                }
                SpotifyTokenResponse response = requestSpotifyClientCredentialsToken(credentials, log);
                if (response.accessToken.isEmpty()) {
                    throw new IOException(ui("spotify.error.no_access_token"));
                }
                cacheSpotifyToken(credentials, response, log);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSpotifyTokenValidated(response.expiresInSeconds));
                }
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage();
                log.write("spotify token: validation failed (" + message + ")");
                if (callback != null) {
                    mainHandler.post(() -> callback.onSpotifyTokenValidationFailed(message));
                }
            }
        });
    }

    private LyricsResult loadLyricsBlocking(TrackSnapshot track, String trackKey, Callback callback, LogSink log) throws Exception {
        log.write("flow: Spotify Web API search -> sync-data -> LRCLIB source/search");
        SpotifyTrackMatch spotifyMatch = fetchSpotifyIsrc(track, log);
        publishSpotifyArtwork(trackKey, spotifyMatch, callback, log);
        boolean isrcFromSpotify = spotifyMatch != null && !spotifyMatch.isrc.isEmpty();
        String isrc = firstNonEmpty(
                spotifyMatch == null ? "" : spotifyMatch.isrc,
                track.isrc
        );
        String spotifyTrackId = firstNonEmpty(
                spotifyMatch == null ? "" : spotifyMatch.spotifyId,
                track.trackId
        );
        String isrcSource = isrc.isEmpty()
                ? ""
                : (isrcFromSpotify ? "Spotify Web API" : "player metadata");
        log.write(isrc.isEmpty()
                ? "isrc: unavailable after Spotify lookup"
                : "isrc: " + isrc + " (" + isrcSource + ")");

        SyncDataResult syncData = isrc.isEmpty() ? null : fetchSyncData(isrc, track, spotifyMatch, log);
        LrclibCandidate candidate = null;
        boolean loadedFromSyncSource = false;

        long syncSourceLrclibId = syncData == null ? 0L : syncData.lrclibId();
        if (syncSourceLrclibId > 0L) {
            log.write("sync-data source: lrclibId=" + syncSourceLrclibId + ", direct loading LRCLIB");
            candidate = fetchLrclibCandidateById(syncSourceLrclibId, log);
            decorateCandidateForSyncData(candidate, syncData);
            loadedFromSyncSource = candidate != null;
            if (!loadedFromSyncSource) {
                log.write("lrclib direct load failed; falling back to search");
            }
        } else if (syncData != null) {
            log.write("sync-data source: no lrclibId; falling back to LRCLIB search");
        }

        if (candidate == null) {
            candidate = searchBestCandidate(track, syncData, log);
        }

        if (candidate == null) {
            log.write("result: no LRCLIB candidate selected");
            return LyricsResult.empty(ui("repo.lyrics_not_found"));
        }
        log.write("lrclib selected: " + describeLrclibCandidate(candidate)
                + (loadedFromSyncSource ? " / source=sync-data.lrclibId" : " / source=search"));

        List<LyricsLine> baseLines;
        boolean lineSynced = candidate.useSyncedLyrics();
        if (lineSynced) {
            baseLines = LrcParser.parseSynced(candidate.syncedLyrics, secondsToMs(candidate.durationSeconds, track.durationMs));
        } else {
            baseLines = LrcParser.parsePlain(candidate.plainLyrics);
        }

        if (baseLines.isEmpty()) {
            if (candidate.instrumental) {
                log.write("result: instrumental track");
                return LyricsResult.empty(ui("repo.instrumental"));
            }
            log.write("result: LRCLIB result has no renderable lyrics");
            return LyricsResult.empty(ui("repo.no_renderable_lyrics"));
        }
        log.write("lyrics base: lines=" + baseLines.size()
                + " / " + (lineSynced ? "LRCLIB synced" : "LRCLIB plain"));

        if (syncData != null) {
            SyncDataApplier.ApplyResult applied = SyncDataApplier.applyWithDiagnostics(baseLines, syncData.syncBody, track);
            for (String diagnostic : applied.diagnostics) {
                log.write("sync-data apply: " + diagnostic);
            }
            List<LyricsLine> karaoke = applied.lines;
            if (!karaoke.isEmpty()) {
                log.write("sync-data applied: karaoke lines=" + karaoke.size()
                        + " / vocalParts=" + countVocalParts(karaoke));
                return new LyricsResult(
                        karaoke,
                        "ivLyrics sync-data + LRCLIB",
                        ui(loadedFromSyncSource
                                ? "repo.detail.sync_applied_direct"
                                : "repo.detail.sync_applied_search"),
                        true,
                        isrc,
                        spotifyTrackId,
                        syncData.contributors
                );
            }
            log.write("sync-data apply failed: falling back to LRCLIB line lyrics");
        }

        String detail = isrc.isEmpty()
                ? ui("repo.detail.no_spotify_isrc")
                : (syncData == null
                ? ui("repo.detail.no_sync_data")
                : ui("repo.detail.sync_apply_failed"));
        return new LyricsResult(
                baseLines,
                lineSynced ? "LRCLIB synced" : "LRCLIB plain",
                detail,
                false,
                isrc,
                spotifyTrackId,
                syncData == null ? Collections.emptyList() : syncData.contributors
        );
    }

    private void publishSpotifyArtwork(String trackKey, SpotifyTrackMatch match, Callback callback, LogSink log) {
        if (callback == null || match == null || match.artworkUrl.isEmpty()) {
            return;
        }
        try {
            Bitmap artwork = loadBitmap(match.artworkUrl);
            if (artwork == null) {
                log.write("spotify artwork: load failed, url=" + match.artworkUrl);
                return;
            }
            String artworkKey = "spotify:" + match.spotifyId + ":artwork:" + match.artworkUrl;
            log.write("spotify artwork: loaded "
                    + artwork.getWidth()
                    + "x"
                    + artwork.getHeight()
                    + " / source="
                    + match.artworkWidth
                    + "x"
                    + match.artworkHeight);
            mainHandler.post(() -> callback.onLyricsArtworkLoaded(trackKey, artwork, artworkKey));
        } catch (Exception error) {
            log.write("spotify artwork error: " + error.getMessage());
        }
    }

    private int countVocalParts(List<LyricsLine> lines) {
        int count = 0;
        for (LyricsLine line : lines) {
            count += line.vocalParts == null ? 0 : line.vocalParts.size();
        }
        return count;
    }

    private LrclibCandidate searchBestCandidate(TrackSnapshot track, SyncDataResult syncData, LogSink log) throws Exception {
        List<LrclibCandidate> candidates = new ArrayList<>();
        candidates.addAll(searchLrclib(buildStructuredQuery(track), "structured", log));

        if (candidates.isEmpty()) {
            candidates.addAll(searchLrclib(Collections.singletonMap("q", track.title + " " + track.artist), "q:title+artist", log));
        }
        if (candidates.isEmpty()) {
            candidates.addAll(searchLrclib(Collections.singletonMap("q", track.title), "q:title", log));
        }
        if (candidates.isEmpty()) {
            log.write("lrclib search: no candidates");
            return null;
        }

        for (LrclibCandidate candidate : candidates) {
            decorateCandidateForSyncData(candidate, syncData);
            candidate.score = scoreCandidate(track, candidate, syncData);
        }
        candidates.sort(this::compareLrclibCandidates);
        log.write("lrclib ranked candidates:");
        for (int index = 0; index < Math.min(5, candidates.size()); index++) {
            LrclibCandidate candidate = candidates.get(index);
            log.write("  #" + (index + 1)
                    + " score=" + fmt(candidate.score)
                    + " sourceScore=" + candidate.syncSourceMatchScore
                    + " syncLineExact=" + candidate.syncLineExactMatch
                    + " preferred=" + candidate.preferredLyricsSource
                    + " " + describeLrclibCandidate(candidate));
        }
        LrclibCandidate best = candidates.get(0);
        if (best.score <= 2.2 && best.syncSourceMatchScore <= 0 && !best.syncLineExactMatch) {
            log.write("lrclib selected: rejected top candidate, score below threshold: " + fmt(best.score));
            return null;
        }
        return best;
    }

    private int compareLrclibCandidates(LrclibCandidate left, LrclibCandidate right) {
        int sourceScore = Integer.compare(right.syncSourceMatchScore, left.syncSourceMatchScore);
        if (sourceScore != 0) return sourceScore;
        int syncLine = Boolean.compare(right.syncLineExactMatch, left.syncLineExactMatch);
        if (syncLine != 0) return syncLine;
        int syncedLine = Boolean.compare(right.exactSyncedLineMatch, left.exactSyncedLineMatch);
        if (syncedLine != 0) return syncedLine;
        return Double.compare(right.score, left.score);
    }

    private Map<String, String> buildStructuredQuery(TrackSnapshot track) {
        Map<String, String> params = new HashMap<>();
        params.put("track_name", track.title);
        params.put("artist_name", track.artist);
        if (!track.album.isEmpty()) {
            params.put("album_name", track.album);
        }
        return params;
    }

    private List<LrclibCandidate> searchLrclib(Map<String, String> params, String label, LogSink log) throws Exception {
        log.write("lrclib search [" + label + "]: " + describeParams(params));
        String response = get(LRCLIB_BASE + "/search?" + encodeParams(params));
        JSONArray array = new JSONArray(response);
        List<LrclibCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object != null) {
                LrclibCandidate candidate = LrclibCandidate.fromJson(object);
                if (candidate.hasLyrics()) {
                    candidates.add(candidate);
                }
            }
        }
        log.write("lrclib search [" + label + "]: candidates=" + candidates.size());
        return candidates;
    }

    private LrclibCandidate fetchLrclibCandidateById(long lrclibId, LogSink log) {
        if (lrclibId <= 0L) {
            return null;
        }

        try {
            String response = get(LRCLIB_BASE + "/get/" + lrclibId);
            LrclibCandidate candidate = LrclibCandidate.fromJson(new JSONObject(response));
            if (candidate.hasLyrics()) {
                log.write("lrclib direct: " + describeLrclibCandidate(candidate));
                return candidate;
            }
            log.write("lrclib direct: id=" + lrclibId + " has no lyrics payload");
            return null;
        } catch (Exception error) {
            log.write("lrclib direct error: id=" + lrclibId + " / " + error.getMessage());
            return null;
        }
    }

    private SyncDataResult fetchSyncData(String isrc, TrackSnapshot track, SpotifyTrackMatch spotifyMatch, LogSink log) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("isrc", isrc);
            params.put("provider", "lrclib");
            params.put("metadata", "1");
            params.put("title", firstNonEmpty(spotifyMatch == null ? "" : spotifyMatch.title, track.title));
            params.put("artist", firstNonEmpty(spotifyMatch == null ? "" : spotifyMatch.artist, track.artist));
            params.put("album", firstNonEmpty(spotifyMatch == null ? "" : spotifyMatch.album, track.album));
            String trackId = firstNonEmpty(spotifyMatch == null ? "" : spotifyMatch.spotifyId, track.trackId);
            if (!trackId.isEmpty()) {
                params.put("trackId", trackId);
            }

            log.write("sync-data request: " + describeParams(params));
            Map<String, String> headers = syncDataHeaders();
            log.write("sync-data headers: Origin=" + headers.get("Origin"));
            String response = get(SYNC_DATA_BASE + "?" + encodeParams(params), headers);
            JSONObject root = new JSONObject(response);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                log.write("sync-data response: no data");
                return null;
            }
            JSONObject syncData = data.optJSONObject("syncData");
            if (syncData != null) {
                SyncDataResult result = new SyncDataResult(
                        syncBodyWithDurationFallback(syncData, data),
                        data.optString("provider", "lrclib"),
                        parseSyncContributors(data, syncData)
                );
                log.write("sync-data response: provider=" + result.provider
                        + " / lines=" + result.lineCharCounts().size()
                        + " / lrclibId=" + result.lrclibId()
                        + " / contributors=" + result.contributors.size()
                        + syncDurationSuffix(result.syncBody));
                return result;
            }
            if (data.optJSONArray("lines") != null) {
                SyncDataResult result = new SyncDataResult(
                        syncBodyWithDurationFallback(data, data),
                        data.optString("provider", "lrclib"),
                        parseSyncContributors(data, data)
                );
                log.write("sync-data response: legacy body / provider=" + result.provider
                        + " / lines=" + result.lineCharCounts().size()
                        + " / lrclibId=" + result.lrclibId()
                        + " / contributors=" + result.contributors.size()
                        + syncDurationSuffix(result.syncBody));
                return result;
            }
            log.write("sync-data response: data without lines");
            return null;
        } catch (Exception error) {
            log.write("sync-data error: " + error.getMessage());
            return null;
        }
    }

    private JSONObject syncBodyWithDurationFallback(JSONObject syncBody, JSONObject wrapper) {
        if (syncBody == null) {
            return null;
        }
        long durationMs = normalizeSyncDataDurationMs(
                syncBody.opt("trackDurationMs"),
                wrapper == null ? null : wrapper.opt("trackDurationMs"),
                syncBody.opt("durationMs"),
                wrapper == null ? null : wrapper.opt("durationMs"),
                syncBody.opt("duration_ms"),
                wrapper == null ? null : wrapper.opt("duration_ms")
        );
        if (durationMs <= 0L || normalizeSyncDataDurationMs(syncBody.opt("trackDurationMs")) > 0L) {
            return syncBody;
        }
        try {
            JSONObject copy = new JSONObject(syncBody.toString());
            copy.put("trackDurationMs", durationMs);
            return copy;
        } catch (Exception ignored) {
            try {
                syncBody.put("trackDurationMs", durationMs);
            } catch (Exception ignoredAgain) {
            }
            return syncBody;
        }
    }

    private String syncDurationSuffix(JSONObject syncBody) {
        long durationMs = normalizeSyncDataDurationMs(
                syncBody == null ? null : syncBody.opt("trackDurationMs"),
                syncBody == null ? null : syncBody.opt("durationMs"),
                syncBody == null ? null : syncBody.opt("duration_ms")
        );
        return durationMs <= 0L ? "" : " / durationMs=" + durationMs;
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

    private SpotifyTrackMatch fetchSpotifyIsrc(TrackSnapshot track, LogSink log) {
        if (track == null || track.title.trim().isEmpty() || track.artist.trim().isEmpty()) {
            log.write("spotify search: missing title or artist metadata");
            return null;
        }

        try {
            String token = getSpotifyAccessToken(false, log);
            if (token.isEmpty()) {
                log.write("spotify token: unavailable; configure Spotify API client id/secret in settings");
                return null;
            }

            if (!track.trackId.isEmpty()) {
                try {
                    SpotifyTrackMatch direct = fetchSpotifyTrackById(token, track.trackId, log);
                    if (direct != null && !direct.isrc.isEmpty()) {
                        if (isSpotifyDurationCompatible(track, direct)) {
                            log.write("spotify selected ISRC: direct track metadata " + describeSpotifyMatch(direct));
                            return direct;
                        }
                        log.write("spotify track: direct metadata skipped by duration, "
                                + spotifyDurationNote(track, direct) + " " + describeSpotifyMatch(direct));
                    }
                } catch (HttpStatusException error) {
                    if (!isSpotifyTokenFailure(error)) {
                        throw error;
                    }
                    log.write("spotify token: rejected by direct track request (" + error.getMessage() + "), refreshing");
                    invalidateSpotifyToken();
                    token = getSpotifyAccessToken(true, log);
                    if (!token.isEmpty()) {
                        SpotifyTrackMatch direct = fetchSpotifyTrackById(token, track.trackId, log);
                        if (direct != null && !direct.isrc.isEmpty()) {
                            if (isSpotifyDurationCompatible(track, direct)) {
                                log.write("spotify selected ISRC: direct track metadata after refresh "
                                        + describeSpotifyMatch(direct));
                                return direct;
                            }
                            log.write("spotify track: direct metadata after refresh skipped by duration, "
                                    + spotifyDurationNote(track, direct) + " " + describeSpotifyMatch(direct));
                        }
                    }
                }
            }

            List<SpotifyTrackMatch> matches;
            try {
                matches = searchSpotifyCandidates(track, token, log);
            } catch (HttpStatusException error) {
                if (!isSpotifyTokenFailure(error)) {
                    throw error;
                }
                log.write("spotify token: rejected by Spotify API (" + error.getMessage() + "), refreshing");
                invalidateSpotifyToken();
                token = getSpotifyAccessToken(true, log);
                if (token.isEmpty()) {
                    log.write("spotify token: refresh failed");
                    return null;
                }
                matches = searchSpotifyCandidates(track, token, log);
            }
            if (matches.isEmpty()) {
                log.write("spotify search: no candidates with ISRC");
                return null;
            }

            return selectSpotifyMatchByApiOrder(track, matches, log);
        } catch (Exception error) {
            log.write("spotify search error: " + error.getMessage());
            return null;
        }
    }

    private SpotifyTrackMatch fetchSpotifyTrackById(String token, String trackId, LogSink log) throws Exception {
        if (trackId == null || trackId.trim().isEmpty()) {
            return null;
        }
        log.write("spotify track: direct metadata lookup id=" + trackId);
        String response = get(
                SPOTIFY_TRACK_BASE + urlEncode(trackId.trim()),
                Collections.singletonMap("Authorization", "Bearer " + token)
        );
        SpotifyTrackMatch match = SpotifyTrackMatch.fromJson(new JSONObject(response));
        if (match == null) {
            log.write("spotify track: no ISRC in direct metadata");
            return null;
        }
        log.write("spotify track: direct metadata ready " + describeSpotifyMatch(match));
        return match;
    }

    private List<SpotifyTrackMatch> searchSpotifyCandidates(TrackSnapshot track, String token, LogSink log) throws Exception {
        List<SpotifyTrackMatch> matches = new ArrayList<>();
        matches.addAll(searchSpotifyTracks(token, track.title + " " + track.artist, "plain", log));
        matches.addAll(searchSpotifyTracks(token, buildSpotifyFieldQuery(track), "field", log));
        Map<String, SpotifyTrackMatch> ordered = new LinkedHashMap<>();
        for (SpotifyTrackMatch match : matches) {
            String key = match.spotifyId.isEmpty()
                    ? match.isrc + "|" + match.title + "|" + match.artist + "|" + match.durationMs
                    : match.spotifyId;
            if (!key.trim().isEmpty() && !ordered.containsKey(key)) {
                ordered.put(key, match);
            }
        }
        return new ArrayList<>(ordered.values());
    }

    private SpotifyTrackMatch selectSpotifyMatchByApiOrder(
            TrackSnapshot track,
            List<SpotifyTrackMatch> matches,
            LogSink log
    ) {
        log.write("spotify ordered candidates:");
        for (int index = 0; index < Math.min(8, matches.size()); index++) {
            SpotifyTrackMatch match = matches.get(index);
            log.write("  #" + (index + 1) + " "
                    + spotifyDurationNote(track, match)
                    + " " + describeSpotifyMatch(match));
        }

        for (int index = 0; index < matches.size(); index++) {
            SpotifyTrackMatch match = matches.get(index);
            if (isSpotifyDurationCompatible(track, match)) {
                log.write("spotify selected ISRC: " + match.isrc
                        + " / responseOrder=" + (index + 1)
                        + " / trackId=" + match.spotifyId);
                return match;
            }
        }

        log.write("spotify selected ISRC: rejected all candidates by duration mismatch");
        return null;
    }

    private boolean isSpotifyDurationCompatible(TrackSnapshot track, SpotifyTrackMatch match) {
        if (track == null || match == null || track.durationMs <= 0L || match.durationMs <= 0L) {
            return true;
        }
        return spotifyDurationDiffSeconds(track, match) <= DURATION_TOLERANCE_SECONDS;
    }

    private String spotifyDurationNote(TrackSnapshot track, SpotifyTrackMatch match) {
        if (track == null || match == null || track.durationMs <= 0L || match.durationMs <= 0L) {
            return "duration=unchecked";
        }
        double diffSeconds = spotifyDurationDiffSeconds(track, match);
        return (diffSeconds <= DURATION_TOLERANCE_SECONDS ? "duration=ok" : "duration=skip")
                + " diff=" + fmt(diffSeconds) + "s";
    }

    private double spotifyDurationDiffSeconds(TrackSnapshot track, SpotifyTrackMatch match) {
        return Math.abs((track.durationMs - match.durationMs) / 1000.0);
    }

    private synchronized String getSpotifyAccessToken(boolean forceRefresh, LogSink log) {
        SpotifyCredentials credentials = readSpotifyCredentials();
        if (!credentials.configured()) {
            invalidateSpotifyToken();
            log.write(credentials.partial()
                    ? "spotify token: Spotify API client id/secret is incomplete"
                    : "spotify token: Spotify API credentials not configured");
            return "";
        }
        String tokenSourceKey = credentials.sourceKey();
        long now = System.currentTimeMillis();
        if (!forceRefresh && isSpotifyTokenUsable(now, tokenSourceKey)) {
            log.write("spotify token: cached token reused (" + credentials.sourceLabel() + ")");
            return spotifyAccessToken;
        }
        if (forceRefresh) {
            log.write("spotify token: forced refresh");
        } else if (!spotifyAccessToken.isEmpty() && !tokenSourceKey.equals(spotifyTokenSourceKey)) {
            log.write("spotify token: cached token source changed, refreshing");
        } else if (!spotifyAccessToken.isEmpty()) {
            log.write("spotify token: cached token expired, refreshing");
        }

        try {
            SpotifyTokenResponse response = requestSpotifyClientCredentialsToken(credentials, log);
            if (response.accessToken.isEmpty()) {
                return "";
            }

            cacheSpotifyToken(credentials, response, log);
            return spotifyAccessToken;
        } catch (Exception error) {
            invalidateSpotifyToken();
            log.write("spotify token: refresh error (" + credentials.sourceLabel() + "): " + error.getMessage());
            return "";
        }
    }

    private synchronized void cacheSpotifyToken(
            SpotifyCredentials credentials,
            SpotifyTokenResponse response,
            LogSink log
    ) {
        long issuedAtMs = System.currentTimeMillis();
        long providerTtlMs = Math.max(60L, response.expiresInSeconds) * 1_000L;
        long effectiveTtlMs = Math.min(providerTtlMs, SPOTIFY_TOKEN_MAX_AGE_MS);
        spotifyAccessToken = response.accessToken;
        spotifyTokenSourceKey = credentials.sourceKey();
        spotifyTokenIssuedAtMs = issuedAtMs;
        spotifyTokenExpiresAtMs = issuedAtMs + effectiveTtlMs;
        persistSpotifyToken();
        log.write("spotify token: refreshed and saved (" + credentials.sourceLabel()
                + "), ttl=" + Math.round(effectiveTtlMs / 1000.0) + "s");
    }

    private SpotifyTokenResponse requestSpotifyClientCredentialsToken(SpotifyCredentials credentials, LogSink log) throws Exception {
        log.write("spotify token: requesting with Spotify API credentials");
        Map<String, String> headers = new HashMap<>();
        String basic = Base64.encodeToString(
                (credentials.clientId + ":" + credentials.clientSecret).getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
        );
        headers.put("Authorization", "Basic " + basic);

        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "client_credentials");
        String response = postForm(SPOTIFY_ACCOUNTS_TOKEN_ENDPOINT, params, headers);
        JSONObject root = new JSONObject(response == null ? "" : response.trim());
        return new SpotifyTokenResponse(
                extractSpotifyToken(root),
                Math.max(60L, root.optLong("expires_in", root.optLong("expiresIn", 3_600L)))
        );
    }

    private SpotifyCredentials readSpotifyCredentials() {
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings == null ? null : aiLyricsSettings.snapshot();
        return new SpotifyCredentials(
                snapshot == null ? "" : snapshot.spotifyClientId,
                snapshot == null ? "" : snapshot.spotifyClientSecret
        );
    }

    private boolean isSpotifyTokenUsable(long now, String tokenSourceKey) {
        if (spotifyAccessToken.isEmpty()) {
            return false;
        }
        if (!tokenSourceKey.equals(spotifyTokenSourceKey)) {
            return false;
        }
        if (spotifyTokenIssuedAtMs <= 0L || now - spotifyTokenIssuedAtMs >= SPOTIFY_TOKEN_MAX_AGE_MS) {
            return false;
        }
        return spotifyTokenExpiresAtMs > now + SPOTIFY_TOKEN_REFRESH_GRACE_MS;
    }

    private void restoreSpotifyToken() {
        if (spotifyTokenPrefs == null) {
            return;
        }
        spotifyAccessToken = spotifyTokenPrefs.getString(KEY_SPOTIFY_ACCESS_TOKEN, "");
        spotifyTokenSourceKey = spotifyTokenPrefs.getString(KEY_SPOTIFY_TOKEN_SOURCE, "");
        spotifyTokenIssuedAtMs = spotifyTokenPrefs.getLong(KEY_SPOTIFY_TOKEN_ISSUED_AT_MS, 0L);
        spotifyTokenExpiresAtMs = spotifyTokenPrefs.getLong(KEY_SPOTIFY_TOKEN_EXPIRES_AT_MS, 0L);
        if (!isSpotifyTokenUsable(System.currentTimeMillis(), readSpotifyCredentials().sourceKey())) {
            invalidateSpotifyToken();
        }
    }

    private void persistSpotifyToken() {
        if (spotifyTokenPrefs == null) {
            return;
        }
        spotifyTokenPrefs.edit()
                .putString(KEY_SPOTIFY_ACCESS_TOKEN, spotifyAccessToken)
                .putString(KEY_SPOTIFY_TOKEN_SOURCE, spotifyTokenSourceKey)
                .putLong(KEY_SPOTIFY_TOKEN_ISSUED_AT_MS, spotifyTokenIssuedAtMs)
                .putLong(KEY_SPOTIFY_TOKEN_EXPIRES_AT_MS, spotifyTokenExpiresAtMs)
                .apply();
    }

    private synchronized void invalidateSpotifyToken() {
        spotifyAccessToken = "";
        spotifyTokenSourceKey = "";
        spotifyTokenIssuedAtMs = 0L;
        spotifyTokenExpiresAtMs = 0L;
        if (spotifyTokenPrefs != null) {
            spotifyTokenPrefs.edit().clear().apply();
        }
    }

    private boolean isSpotifyTokenFailure(HttpStatusException error) {
        return error != null && (error.statusCode == 401 || error.statusCode == 403);
    }

    private String extractSpotifyToken(JSONObject object) {
        if (object == null) {
            return "";
        }

        String token = firstNonEmpty(
                object.optString("access_token", ""),
                object.optString("accessToken", "")
        );
        token = firstNonEmpty(token, object.optString("token", ""));
        token = firstNonEmpty(token, object.optString("spotifyAccessToken", ""));
        if (!token.isEmpty()) {
            return stripBearer(token);
        }

        String nestedDataToken = extractSpotifyToken(object.optJSONObject("data"));
        if (!nestedDataToken.isEmpty()) {
            return nestedDataToken;
        }
        return extractSpotifyToken(object.optJSONObject("session"));
    }

    private List<SpotifyTrackMatch> searchSpotifyTracks(String token, String query, String label, LogSink log) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("q", query);
        params.put("type", "track");
        params.put("limit", "10");

        log.write("spotify search [" + label + "]: q=" + query);
        String response = get(
                SPOTIFY_SEARCH_BASE + "?" + encodeParams(params),
                Collections.singletonMap("Authorization", "Bearer " + token)
        );
        JSONObject root = new JSONObject(response);
        JSONObject tracks = root.optJSONObject("tracks");
        JSONArray items = tracks == null ? null : tracks.optJSONArray("items");
        List<SpotifyTrackMatch> matches = new ArrayList<>();
        if (items != null) {
            for (int index = 0; index < items.length(); index++) {
                SpotifyTrackMatch match = SpotifyTrackMatch.fromJson(items.optJSONObject(index));
                if (match != null) {
                    matches.add(match);
                }
            }
        }
        log.write("spotify search [" + label + "]: candidates=" + matches.size());
        return matches;
    }

    private String buildSpotifyFieldQuery(TrackSnapshot track) {
        StringBuilder builder = new StringBuilder();
        builder.append("track:").append(spotifySearchValue(track.title));
        builder.append(" artist:").append(spotifySearchValue(track.artist));
        if (!track.album.isEmpty()) {
            builder.append(" album:").append(spotifySearchValue(track.album));
        }
        return builder.toString();
    }

    private String spotifySearchValue(String value) {
        String normalized = value == null ? "" : value.trim().replace("\"", "");
        if (normalized.indexOf(' ') >= 0) {
            return "\"" + normalized + "\"";
        }
        return normalized;
    }

    private double scoreCandidate(TrackSnapshot track, LrclibCandidate candidate, SyncDataResult syncData) {
        double titleScore = titleScore(track.title, candidate.trackName);
        double artistScore = bestArtistScore(track.artist, candidate.artistName);
        double durationScore = durationScore(track.durationMs, candidate.durationSeconds);
        double lyricsScore = candidate.useSyncedLyrics()
                ? 0.8
                : (candidate.plainLyrics != null && !candidate.plainLyrics.trim().isEmpty() ? 0.25 : 0.0);
        double score = (titleScore * 4.0) + (artistScore * 3.0) + (durationScore * 2.0) + lyricsScore;

        if (track.durationMs > 0 && candidate.durationSeconds > 0) {
            double diff = Math.abs((track.durationMs / 1000.0) - candidate.durationSeconds);
            if (diff > DURATION_TOLERANCE_SECONDS) {
                score -= Math.min(2.5, (diff - DURATION_TOLERANCE_SECONDS) / 15.0);
            }
        }
        if (syncData != null && candidate.syncLineExactMatch) {
            score += 2.5;
        }
        return score;
    }

    private void decorateCandidateForSyncData(LrclibCandidate candidate, SyncDataResult syncData) {
        if (candidate == null) {
            return;
        }
        candidate.preferredLyricsSource = "";
        candidate.syncLineExactMatch = false;
        candidate.exactSyncedLineMatch = false;
        candidate.exactPlainLineMatch = false;
        candidate.syncSourceMatchScore = 0;
        candidate.syncSourceIdMatch = false;
        candidate.syncSourceTextMatch = false;
        candidate.syncSourceLineCountMatch = false;

        if (syncData == null) {
            return;
        }

        List<Integer> syncLineCounts = syncData.lineCharCounts();
        candidate.exactSyncedLineMatch = hasExactLineShape(syncLineCounts, candidateLineCharCounts(candidate.syncedLyrics, true));
        candidate.exactPlainLineMatch = hasExactLineShape(syncLineCounts, candidateLineCharCounts(candidate.plainLyrics, false));
        candidate.syncLineExactMatch = candidate.exactSyncedLineMatch || candidate.exactPlainLineMatch;
        candidate.preferredLyricsSource = candidate.exactSyncedLineMatch
                ? "synced"
                : (candidate.exactPlainLineMatch ? "plain" : syncData.preferredLyricsSource());

        if (!syncData.hasLrclibSource()) {
            return;
        }

        long sourceId = syncData.lrclibId();
        candidate.syncSourceIdMatch = sourceId > 0L && candidate.id == sourceId;

        List<Integer> sourceLineCounts = syncData.sourceLineCharCounts();
        boolean sourceSyncedLineMatch = hasExactLineShape(sourceLineCounts, candidateLineCharCounts(candidate.syncedLyrics, true));
        boolean sourcePlainLineMatch = hasExactLineShape(sourceLineCounts, candidateLineCharCounts(candidate.plainLyrics, false));
        if (candidate.preferredLyricsSource.isEmpty()) {
            candidate.preferredLyricsSource = sourceSyncedLineMatch
                    ? "synced"
                    : (sourcePlainLineMatch ? "plain" : syncData.preferredLyricsSource());
        }

        String preferredSource = firstNonEmpty(candidate.preferredLyricsSource, syncData.preferredLyricsSource());
        String candidateText = candidateComparableText(candidate, preferredSource);
        String sourceFingerprint = syncData.sourceLyricsFingerprint();
        candidate.syncSourceTextMatch = !sourceFingerprint.isEmpty()
                && sourceFingerprint.equals(lyricsFingerprint(candidateText));

        candidate.syncSourceLineCountMatch = hasExactLineShape(
                sourceLineCounts,
                lineCharCounts(comparableLyricsLines(candidateText, false))
        );

        if (candidate.syncSourceIdMatch) {
            candidate.syncSourceMatchScore = 100;
        } else if (candidate.syncSourceTextMatch) {
            candidate.syncSourceMatchScore = 90;
        } else if (candidate.syncSourceLineCountMatch) {
            candidate.syncSourceMatchScore = 60;
        }
    }

    private boolean hasExactLineShape(List<Integer> expectedCounts, List<Integer> actualCounts) {
        if (expectedCounts == null || actualCounts == null || expectedCounts.isEmpty()) {
            return false;
        }
        if (expectedCounts.size() != actualCounts.size()) {
            return false;
        }
        for (int index = 0; index < expectedCounts.size(); index++) {
            if (!expectedCounts.get(index).equals(actualCounts.get(index))) {
                return false;
            }
        }
        return true;
    }

    private List<Integer> candidateLineCharCounts(String text, boolean stripTimestamps) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        return lineCharCounts(comparableLyricsLines(text, stripTimestamps));
    }

    private List<Integer> lineCharCounts(List<String> lines) {
        List<Integer> counts = new ArrayList<>();
        for (String line : lines) {
            counts.add(codePointLength(line));
        }
        return counts;
    }

    private List<String> comparableLyricsLines(String text, boolean stripTimestamps) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        String[] rawLines = text.split("\\r?\\n");
        for (String rawLine : rawLines) {
            String line = stripTimestamps ? stripLeadingLrcTimestamp(rawLine) : rawLine.trim();
            line = Normalizer.normalize(line, Normalizer.Form.NFC).trim();
            if (line.isEmpty() || line.matches(LRCLIB_METADATA_LINE_PATTERN)) {
                continue;
            }
            lines.add(line);
        }
        return lines;
    }

    private String candidateComparableText(LrclibCandidate candidate, String preferredSource) {
        if (candidate == null) {
            return "";
        }
        boolean useSynced = "synced".equals(preferredSource)
                ? candidate.syncedLyrics != null
                : (!"plain".equals(preferredSource) && candidate.plainLyrics == null && candidate.syncedLyrics != null);
        String text = useSynced
                ? stripLrcTimestamps(candidate.syncedLyrics)
                : firstNonEmpty(candidate.plainLyrics, stripLrcTimestamps(candidate.syncedLyrics));
        List<String> lines = comparableLyricsLines(text, false);
        return joinLinesForFingerprint(lines);
    }

    private static String stripLeadingLrcTimestamp(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceFirst("^\\[\\d+:\\d+(?:[.,]\\d+)?\\]\\s*", "");
    }

    private static String stripLrcTimestamps(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?m)^\\[\\d+:\\d+(?:[.,]\\d+)?\\]\\s*", "").trim();
    }

    private static String joinLinesForFingerprint(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }

    private static String lyricsFingerprint(String text) {
        long hash = 2_166_136_261L;
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC);
        List<String> chars = new ArrayList<>();
        normalized.codePoints().forEach(codePoint -> chars.add(new String(Character.toChars(codePoint))));
        for (String character : chars) {
            int codePoint = character.codePointAt(0);
            hash ^= codePoint;
            hash = (hash * 16_777_619L) & 0xffff_ffffL;
        }
        return String.format(Locale.ROOT, "lrclib-%s-%s",
                Long.toString(hash, 36),
                Long.toString(chars.size(), 36));
    }

    private void emitLog(String trackKey, Callback callback, String message) {
        String safeMessage = message == null ? "" : message;
        Log.d(TAG, safeMessage);
        mainHandler.post(() -> callback.onLyricsLog(trackKey, safeMessage));
    }

    private String describeParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }

        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder builder = new StringBuilder("{");
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(key).append("=").append(params.get(key));
        }
        builder.append("}");
        return builder.toString();
    }

    private String describeSpotifyMatch(SpotifyTrackMatch match) {
        if (match == null) {
            return "null";
        }
        return "id=" + match.spotifyId
                + " / isrc=" + match.isrc
                + " / title=\"" + match.title + "\""
                + " / artist=\"" + match.artist + "\""
                + " / album=\"" + match.album + "\""
                + " / duration=" + match.durationMs + "ms"
                + (match.artworkUrl.isEmpty()
                ? ""
                : " / artwork=" + match.artworkWidth + "x" + match.artworkHeight);
    }

    private String describeLrclibCandidate(LrclibCandidate candidate) {
        if (candidate == null) {
            return "null";
        }
        return "id=" + candidate.id
                + " / title=\"" + candidate.trackName + "\""
                + " / artist=\"" + candidate.artistName + "\""
                + " / album=\"" + candidate.albumName + "\""
                + " / duration=" + fmt(candidate.durationSeconds) + "s"
                + " / synced=" + (candidate.syncedLyrics != null)
                + " / plain=" + (candidate.plainLyrics != null)
                + (candidate.syncSourceMatchScore > 0
                ? " / sourceMatch[id=" + candidate.syncSourceIdMatch
                + ",text=" + candidate.syncSourceTextMatch
                + ",shape=" + candidate.syncSourceLineCountMatch
                + "]"
                : "");
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int codePointLength(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
        return normalized.codePointCount(0, normalized.length());
    }

    private double durationScore(long expectedDurationMs, double candidateDurationSeconds) {
        if (expectedDurationMs <= 0L || candidateDurationSeconds <= 0.0) {
            return 0.5;
        }
        double diff = Math.abs((expectedDurationMs / 1000.0) - candidateDurationSeconds);
        return Math.max(0.0, 1.0 - Math.min(1.0, diff / DURATION_TOLERANCE_SECONDS));
    }

    private double titleScore(String expected, String candidate) {
        String left = normalizeComparable(expected);
        String right = normalizeComparable(candidate);
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        if (left.equals(right)) return 1.0;
        if (left.contains(right) || right.contains(left)) return 0.96;
        return jaroWinkler(left, right);
    }

    private double bestArtistScore(String expectedArtists, String candidateArtists) {
        List<String> expected = splitArtists(expectedArtists);
        List<String> candidates = splitArtists(candidateArtists);
        double best = 0.0;
        for (String left : expected) {
            for (String right : candidates) {
                best = Math.max(best, jaroWinkler(left, right));
            }
        }
        return best;
    }

    private List<String> splitArtists(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = value.split("[&,]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String normalized = normalizeComparable(part);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private Map<String, String> syncDataHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", SYNC_DATA_SPOTIFY_ORIGIN);
        headers.put("Referer", SYNC_DATA_SPOTIFY_REFERER);
        headers.put("X-ivLyrics-Client", "android");
        return headers;
    }

    private String get(String url) throws IOException {
        return get(url, Collections.emptyMap());
    }

    private String get(String url, Map<String, String> headers) throws IOException {
        URL parsedUrl = URI.create(url).toURL();
        HttpURLConnection connection = (HttpURLConnection) parsedUrl.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/0.1");
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey() != null && header.getValue() != null) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, httpErrorMessage(status, connection));
        }

        try {
            return readBody(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private String postForm(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        byte[] body = encodeParams(params).getBytes(StandardCharsets.UTF_8);
        URL parsedUrl = URI.create(url).toURL();
        HttpURLConnection connection = (HttpURLConnection) parsedUrl.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/0.1");
        connection.setRequestProperty("Content-Length", String.valueOf(body.length));
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey() != null && header.getValue() != null) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
        }

        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, httpErrorMessage(status, connection));
        }

        try {
            return readBody(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private Bitmap loadBitmap(String url) throws IOException {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        URL parsedUrl = URI.create(url).toURL();
        HttpURLConnection connection = (HttpURLConnection) parsedUrl.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "image/*");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/0.1");

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, httpErrorMessage(status, connection));
        }

        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
            return BitmapFactory.decodeStream(input);
        } finally {
            connection.disconnect();
        }
    }

    private String httpErrorMessage(int status, HttpURLConnection connection) {
        String message = "HTTP " + status;
        try {
            String body = readBody(connection.getErrorStream());
            if (!body.trim().isEmpty()) {
                message += " / " + compactBody(body);
            }
        } catch (Exception ignored) {
        }
        return message;
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedInputStream input = new BufferedInputStream(stream);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String compactBody(String body) {
        String compact = body == null ? "" : body.trim().replaceAll("\\s+", " ");
        return compact.length() <= 300 ? compact : compact.substring(0, 300) + "...";
    }

    private String encodeParams(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                continue;
            }
            if (!first) {
                builder.append("&");
            }
            first = false;
            builder.append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(entry.getValue()));
        }
        return builder.toString();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static long secondsToMs(double seconds, long fallbackDurationMs) {
        if (seconds > 0.0) {
            return Math.round(seconds * 1000.0);
        }
        return Math.max(0L, fallbackDurationMs);
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private static String stripBearer(String value) {
        if (value == null) return "";
        return value.replaceFirst("(?i)^Bearer\\s+", "").trim();
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

    private static List<LyricsResult.SyncContributor> parseSyncContributors(JSONObject data, JSONObject syncData) {
        JSONArray combined = new JSONArray();
        appendContributorEntries(combined, data, "contributors");
        appendContributorEntries(combined, syncData, "contributors");
        appendContributorEntries(combined, data, "creators");
        appendContributorEntries(combined, syncData, "creators");
        appendContributorEntries(combined, data, "authors");
        appendContributorEntries(combined, syncData, "authors");
        appendContributorEntry(combined, data == null ? null : data.optJSONObject("creator"));
        appendContributorEntry(combined, syncData == null ? null : syncData.optJSONObject("creator"));
        return parseSyncContributors(combined);
    }

    private static void appendContributorEntries(JSONArray target, JSONObject object, String key) {
        if (target == null || object == null || key == null) {
            return;
        }
        JSONArray array = object.optJSONArray(key);
        if (array == null) {
            appendContributorEntry(target, object.optJSONObject(key));
            return;
        }
        for (int index = 0; index < array.length(); index++) {
            appendContributorEntry(target, array.opt(index));
        }
    }

    private static void appendContributorEntry(JSONArray target, Object entry) {
        if (target == null || entry == null) {
            return;
        }
        target.put(entry);
    }

    private static List<LyricsResult.SyncContributor> parseSyncContributors(JSONArray array) {
        if (array == null || array.length() == 0) {
            return Collections.emptyList();
        }
        List<LyricsResult.SyncContributor> result = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        boolean anonymousAdded = false;
        for (int index = 0; index < array.length(); index++) {
            Object raw = array.opt(index);
            String name;
            String userHash = "";
            boolean profileAvailable = false;
            if (raw instanceof String) {
                name = ((String) raw).trim();
            } else if (raw instanceof JSONObject) {
                JSONObject object = (JSONObject) raw;
                name = firstNonEmpty(
                        firstNonEmpty(
                                firstNonEmpty(object.optString("name", ""), object.optString("nickname", "")),
                                object.optString("displayName", "")
                        ),
                        firstNonEmpty(object.optString("username", ""), object.optString("spotifyDisplayName", ""))
                );
                userHash = firstNonEmpty(
                        firstNonEmpty(object.optString("userHash", ""), object.optString("hash", "")),
                        object.optString("id", "")
                );
                userHash = userHash == null ? "" : userHash.trim();
                profileAvailable = object.has("profileAvailable")
                        ? object.optBoolean("profileAvailable", false)
                        : !userHash.isEmpty();
            } else {
                continue;
            }
            if (name == null || name.trim().isEmpty()) {
                name = "Anonymous";
            }
            String key = userHash.isEmpty() ? "name:" + name.trim().toLowerCase(Locale.ROOT) : userHash;
            boolean anonymous = "anonymous".equalsIgnoreCase(name.trim()) && userHash.isEmpty();
            if (anonymous) {
                if (anonymousAdded) {
                    continue;
                }
                anonymousAdded = true;
            } else if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            result.add(new LyricsResult.SyncContributor(name, userHash, profileAvailable));
        }
        return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
    }

    private static String normalizeComparable(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[\\u2018\\u2019]", "'")
                .replaceAll("[\\u201c\\u201d]", "\"")
                .replaceAll("[()\\[\\]{}]", "")
                .replaceAll("\\s+", " ");
    }

    private static double jaroWinkler(String rawLeft, String rawRight) {
        String left = normalizeComparable(rawLeft);
        String right = normalizeComparable(rawRight);
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        if (left.equals(right)) return 1.0;

        int leftLength = left.length();
        int rightLength = right.length();
        int matchDistance = Math.max(leftLength, rightLength) / 2 - 1;
        boolean[] leftMatches = new boolean[leftLength];
        boolean[] rightMatches = new boolean[rightLength];
        int matches = 0;

        for (int leftIndex = 0; leftIndex < leftLength; leftIndex++) {
            int start = Math.max(0, leftIndex - matchDistance);
            int end = Math.min(leftIndex + matchDistance + 1, rightLength);
            for (int rightIndex = start; rightIndex < end; rightIndex++) {
                if (rightMatches[rightIndex]) continue;
                if (left.charAt(leftIndex) != right.charAt(rightIndex)) continue;
                leftMatches[leftIndex] = true;
                rightMatches[rightIndex] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        double transpositions = 0.0;
        int rightIndex = 0;
        for (int leftIndex = 0; leftIndex < leftLength; leftIndex++) {
            if (!leftMatches[leftIndex]) continue;
            while (!rightMatches[rightIndex]) {
                rightIndex++;
            }
            if (left.charAt(leftIndex) != right.charAt(rightIndex)) {
                transpositions++;
            }
            rightIndex++;
        }
        transpositions /= 2.0;

        double jaro = ((matches / (double) leftLength)
                + (matches / (double) rightLength)
                + ((matches - transpositions) / matches)) / 3.0;

        int prefix = 0;
        for (int index = 0; index < Math.min(4, Math.min(leftLength, rightLength)); index++) {
            if (left.charAt(index) == right.charAt(index)) {
                prefix++;
            } else {
                break;
            }
        }

        return jaro + prefix * 0.1 * (1.0 - jaro);
    }

    private static final class LrclibCandidate {
        final long id;
        final String trackName;
        final String artistName;
        final String albumName;
        final double durationSeconds;
        final boolean instrumental;
        final String plainLyrics;
        final String syncedLyrics;
        final String isrc;
        double score;
        String preferredLyricsSource = "";
        boolean syncLineExactMatch;
        boolean exactSyncedLineMatch;
        boolean exactPlainLineMatch;
        int syncSourceMatchScore;
        boolean syncSourceIdMatch;
        boolean syncSourceTextMatch;
        boolean syncSourceLineCountMatch;

        LrclibCandidate(
                long id,
                String trackName,
                String artistName,
                String albumName,
                double durationSeconds,
                boolean instrumental,
                String plainLyrics,
                String syncedLyrics,
                String isrc
        ) {
            this.id = id;
            this.trackName = trackName == null ? "" : trackName;
            this.artistName = artistName == null ? "" : artistName;
            this.albumName = albumName == null ? "" : albumName;
            this.durationSeconds = durationSeconds;
            this.instrumental = instrumental;
            this.plainLyrics = emptyToNull(plainLyrics);
            this.syncedLyrics = emptyToNull(syncedLyrics);
            this.isrc = TrackSnapshot.normalizeIsrc(isrc);
        }

        static LrclibCandidate fromJson(JSONObject object) {
            return new LrclibCandidate(
                    object.optLong("id", 0L),
                    firstNonEmpty(object.optString("trackName", ""), object.optString("name", "")),
                    object.optString("artistName", ""),
                    object.optString("albumName", ""),
                    object.optDouble("duration", 0.0),
                    object.optBoolean("instrumental", false),
                    jsonStringOrNull(object, "plainLyrics"),
                    jsonStringOrNull(object, "syncedLyrics"),
                    firstNonEmpty(object.optString("isrc", ""), object.optString("ISRC", ""))
            );
        }

        boolean hasLyrics() {
            return instrumental || plainLyrics != null || syncedLyrics != null;
        }

        boolean useSyncedLyrics() {
            if ("plain".equals(preferredLyricsSource) && plainLyrics != null) {
                return false;
            }
            return syncedLyrics != null && !syncedLyrics.trim().isEmpty();
        }

        private static String emptyToNull(String value) {
            return value == null || value.trim().isEmpty() ? null : value;
        }

        private static String jsonStringOrNull(JSONObject object, String key) {
            if (object == null || object.isNull(key)) {
                return null;
            }
            String value = object.optString(key, "");
            return value.trim().isEmpty() ? null : value;
        }
    }

    private static final class SyncDataResult {
        final JSONObject syncBody;
        final String provider;
        final List<LyricsResult.SyncContributor> contributors;

        SyncDataResult(JSONObject syncBody, String provider) {
            this(syncBody, provider, Collections.emptyList());
        }

        SyncDataResult(JSONObject syncBody, String provider, List<LyricsResult.SyncContributor> contributors) {
            this.syncBody = syncBody;
            this.provider = provider == null ? "" : provider;
            this.contributors = contributors == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(contributors));
        }

        JSONObject source() {
            return syncBody == null ? null : syncBody.optJSONObject("source");
        }

        boolean hasLrclibSource() {
            JSONObject source = source();
            if (source == null) {
                return false;
            }
            String sourceProvider = source.optString("provider", "").trim().toLowerCase(Locale.ROOT);
            return sourceProvider.isEmpty() || LRCLIB_PROVIDER_ID.equals(sourceProvider);
        }

        long lrclibId() {
            JSONObject source = source();
            if (source == null) {
                return 0L;
            }
            Object rawId = source.opt("lrclibId");
            if (rawId instanceof Number) {
                return Math.max(0L, ((Number) rawId).longValue());
            }
            if (rawId instanceof String) {
                try {
                    return Math.max(0L, Long.parseLong(((String) rawId).trim()));
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
            return 0L;
        }

        String preferredLyricsSource() {
            JSONObject source = source();
            if (source == null) {
                return "";
            }
            String value = source.optString("preferredLyricsSource", "").trim();
            return "plain".equals(value) || "synced".equals(value) ? value : "";
        }

        String sourceLyricsFingerprint() {
            JSONObject source = source();
            return source == null ? "" : source.optString("lyricsFingerprint", "").trim();
        }

        List<Integer> sourceLineCharCounts() {
            JSONObject source = source();
            return source == null
                    ? Collections.emptyList()
                    : readIntArray(source.optJSONArray("lineCharCounts"));
        }

        List<Integer> lineCharCounts() {
            JSONArray lines = syncBody == null ? null : syncBody.optJSONArray("lines");
            if (lines == null || lines.length() == 0) {
                return Collections.emptyList();
            }

            List<Integer> counts = new ArrayList<>();
            for (int index = 0; index < lines.length(); index++) {
                JSONObject line = lines.optJSONObject(index);
                JSONArray chars = line == null ? null : line.optJSONArray("chars");
                counts.add(chars == null ? 0 : chars.length());
            }
            return counts;
        }
    }

    private static final class SpotifyTrackMatch {
        final String spotifyId;
        final String title;
        final String artist;
        final String album;
        final long durationMs;
        final String isrc;
        final String artworkUrl;
        final int artworkWidth;
        final int artworkHeight;

        SpotifyTrackMatch(
                String spotifyId,
                String title,
                String artist,
                String album,
                long durationMs,
                String isrc,
                String artworkUrl,
                int artworkWidth,
                int artworkHeight
        ) {
            this.spotifyId = spotifyId == null ? "" : spotifyId;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.album = album == null ? "" : album;
            this.durationMs = Math.max(0L, durationMs);
            this.isrc = TrackSnapshot.normalizeIsrc(isrc);
            this.artworkUrl = artworkUrl == null ? "" : artworkUrl;
            this.artworkWidth = Math.max(0, artworkWidth);
            this.artworkHeight = Math.max(0, artworkHeight);
        }

        static SpotifyTrackMatch fromJson(JSONObject item) {
            return fromJson(item, true);
        }

        static SpotifyTrackMatch fromJson(JSONObject item, boolean requireIsrc) {
            if (item == null) {
                return null;
            }

            JSONObject externalIds = item.optJSONObject("external_ids");
            String isrc = externalIds == null ? "" : externalIds.optString("isrc", "");
            if (requireIsrc && TrackSnapshot.normalizeIsrc(isrc).isEmpty()) {
                return null;
            }

            JSONObject album = item.optJSONObject("album");
            AlbumArtwork artwork = bestAlbumArtwork(album);
            return new SpotifyTrackMatch(
                    item.optString("id", ""),
                    item.optString("name", ""),
                    artistText(item.optJSONArray("artists")),
                    albumName(album),
                    item.optLong("duration_ms", 0L),
                    isrc,
                    artwork.url,
                    artwork.width,
                    artwork.height
            );
        }

        private static String artistText(JSONArray artists) {
            if (artists == null || artists.length() == 0) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < artists.length(); index++) {
                JSONObject artist = artists.optJSONObject(index);
                String name = artist == null ? "" : artist.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(name);
            }
            return builder.toString();
        }

        private static String albumName(JSONObject album) {
            return album == null ? "" : album.optString("name", "");
        }

        private static AlbumArtwork bestAlbumArtwork(JSONObject album) {
            JSONArray images = album == null ? null : album.optJSONArray("images");
            if (images == null || images.length() == 0) {
                return AlbumArtwork.empty();
            }

            AlbumArtwork best = AlbumArtwork.empty();
            long bestArea = -1L;
            for (int index = 0; index < images.length(); index++) {
                JSONObject image = images.optJSONObject(index);
                if (image == null) {
                    continue;
                }
                String url = image.optString("url", "").trim();
                if (url.isEmpty()) {
                    continue;
                }
                int width = Math.max(0, image.optInt("width", 0));
                int height = Math.max(0, image.optInt("height", 0));
                long area = width > 0 && height > 0 ? (long) width * height : 0L;
                if (best.url.isEmpty() || area > bestArea) {
                    best = new AlbumArtwork(url, width, height);
                    bestArea = area;
                }
            }
            return best;
        }

        private static final class AlbumArtwork {
            final String url;
            final int width;
            final int height;

            AlbumArtwork(String url, int width, int height) {
                this.url = url == null ? "" : url;
                this.width = Math.max(0, width);
                this.height = Math.max(0, height);
            }

            static AlbumArtwork empty() {
                return new AlbumArtwork("", 0, 0);
            }
        }
    }

    private static final class SpotifyTokenResponse {
        final String accessToken;
        final long expiresInSeconds;

        SpotifyTokenResponse(String accessToken, long expiresInSeconds) {
            this.accessToken = stripBearer(accessToken);
            this.expiresInSeconds = Math.max(60L, expiresInSeconds);
        }
    }

    private static final class SpotifyCredentials {
        final String clientId;
        final String clientSecret;

        SpotifyCredentials(String clientId, String clientSecret) {
            this.clientId = clientId == null ? "" : clientId.trim();
            this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        }

        boolean configured() {
            return !clientId.isEmpty() && !clientSecret.isEmpty();
        }

        boolean partial() {
            return clientId.isEmpty() != clientSecret.isEmpty();
        }

        String sourceKey() {
            if (!configured()) {
                return "spotify-client:missing";
            }
            String secretHash = Integer.toHexString((clientId + "\n" + clientSecret).hashCode());
            return "spotify-client:" + clientId + ":" + secretHash;
        }

        String sourceLabel() {
            return "Spotify API credentials";
        }
    }

    private static final class HttpStatusException extends IOException {
        final int statusCode;

        HttpStatusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
