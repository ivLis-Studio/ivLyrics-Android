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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

final class LyricsRepository {
    private static final String TAG = "ivLyricsDebug";
    private static final String LRCLIB_BASE = "https://lrclib.net/api";
    private static final String SYNC_DATA_BASE = "https://lyrics.api.ivl.is/lyrics/sync-data";
    private static final String SYNC_DATA_REQUEST_VERSION = "20260701";
    private static final String OPENDB_ORIGIN = "https://ivlis.kr";
    private static final String OPENDB_ROOT = "https://ivlis.kr/ivLyrics/opendb";
    private static final String OPENDB_MANIFEST_URL = OPENDB_ROOT + "/data/manifest.json";
    private static final String SYNC_DATA_SPOTIFY_ORIGIN = "https://xpui.app.spotify.com";
    private static final String SYNC_DATA_SPOTIFY_REFERER = "https://xpui.app.spotify.com/";
    private static final String SYNC_DATA_CACHE_SCHEMA = "sync-data-api-v1";
    private static final String OPENDB_PREFS = "sync_data_opendb";
    private static final String KEY_OPENDB_PROVIDER_MAP = "provider_map";
    private static final String KEY_OPENDB_FETCHED_AT_MS = "fetched_at_ms";
    private static final String KEY_OPENDB_SIGNATURE = "manifest_signature";
    private static final String KEY_OPENDB_BASE_DATE = "base_date";
    private static final String KEY_OPENDB_UNAVAILABLE_UNTIL_MS = "unavailable_until_ms";
    private static final String SPOTIFY_ACCOUNTS_TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";
    private static final String SPOTIFY_SEARCH_BASE = "https://api.spotify.com/v1/search";
    private static final String SPOTIFY_TRACK_BASE = "https://api.spotify.com/v1/tracks/";
    private static final String SPOTIFY_ENGLISH_ACCEPT_LANGUAGE = "en-US,en;q=0.9";
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
    private static final long SYNC_DATA_SERVER_CACHE_BYPASS_MS = 30L * 1_000L;
    private static final long LYRICS_CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1_000L;
    private static final long OPENDB_FRESH_MS = 60L * 1_000L;
    private static final long OPENDB_UNAVAILABLE_RETRY_MS = 5L * 60L * 1_000L;
    private static final double DURATION_TOLERANCE_SECONDS = 15.0;
    private static final double LRCLIB_SYNCED_FALLBACK_SCORE_WINDOW = 0.50;
    private static final double LRCLIB_SYNCED_FALLBACK_MIN_TITLE_SCORE = 0.78;
    private static final double LRCLIB_SYNCED_FALLBACK_MIN_ARTIST_SCORE = 0.45;
    private static final Pattern LRCLIB_METADATA_LINE_PATTERN = Pattern.compile(
            "^\\s*\\[(?:ar|al|ti|au|length|by|offset|re|ve):[^\\]]*\\]\\s*$"
    );
    private static final Pattern COMPARABLE_APOSTROPHE_PATTERN = Pattern.compile("[\\u2018\\u2019]");
    private static final Pattern COMPARABLE_QUOTE_PATTERN = Pattern.compile("[\\u201c\\u201d]");
    private static final Pattern COMPARABLE_BRACKET_PATTERN = Pattern.compile("[()\\[\\]{}]");
    private static final Pattern COMPARABLE_WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, MemoryLyricsCacheEntry> cache = new HashMap<>();
    private final AtomicLong providerPolicyGeneration = new AtomicLong();
    private final SharedPreferences spotifyTokenPrefs;
    private final AiLyricsSettings aiLyricsSettings;
    private final LyricsProviderSettings lyricsProviderSettings;
    private final LyricsDiskCache diskCache;
    private final RawResponseDiskCache syncDataResponseCache;
    private final SharedPreferences openDbPrefs;
    private final Map<String, Long> syncDataServerCacheBypassUntil = new HashMap<>();
    private String spotifyAccessToken = "";
    private String spotifyTokenSourceKey = "";
    private long spotifyTokenIssuedAtMs = 0L;
    private long spotifyTokenExpiresAtMs = 0L;
    private long syncDataServerCacheBypassAllUntilMs = 0L;

    LyricsRepository(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        spotifyTokenPrefs = appContext == null
                ? null
                : appContext.getSharedPreferences(SPOTIFY_TOKEN_PREFS, Context.MODE_PRIVATE);
        aiLyricsSettings = appContext == null ? null : new AiLyricsSettings(appContext);
        lyricsProviderSettings = new LyricsProviderSettings(appContext);
        diskCache = appContext == null
                ? null
                : new LyricsDiskCache(appContext, "base_lyrics", 350, LYRICS_CACHE_MAX_AGE_MS);
        syncDataResponseCache = appContext == null
                ? null
                : new RawResponseDiskCache(appContext, "sync_data_api", 700, LYRICS_CACHE_MAX_AGE_MS);
        openDbPrefs = appContext == null
                ? null
                : appContext.getSharedPreferences(OPENDB_PREFS, Context.MODE_PRIVATE);
        restoreSpotifyToken();
    }

    interface Callback {
        void onLyricsLoaded(String trackKey, LyricsResult result);

        void onLyricsError(String trackKey, String message);

        void onLyricsLog(String trackKey, String message);

        void onLyricsArtworkLoaded(String trackKey, Bitmap artwork, String artworkKey);

        void onLyricsMetadataResolved(String trackKey, String isrc, String spotifyTrackId);
    }

    interface ManualLrclibCallback {
        void onManualLrclibSearchResults(String trackKey, List<ManualLrclibCandidate> candidates);

        void onManualLrclibLyricsLoaded(String trackKey, LyricsResult result);

        void onManualLrclibError(String trackKey, String message);

        void onManualLrclibLog(String trackKey, String message);
    }

    static final class ManualLrclibCandidate {
        final long id;
        final String trackName;
        final String artistName;
        final String albumName;
        final double durationSeconds;
        final boolean synced;
        final boolean plain;
        final boolean instrumental;
        final String isrc;
        final double score;

        ManualLrclibCandidate(
                long id,
                String trackName,
                String artistName,
                String albumName,
                double durationSeconds,
                boolean synced,
                boolean plain,
                boolean instrumental,
                String isrc,
                double score
        ) {
            this.id = id;
            this.trackName = trackName == null ? "" : trackName;
            this.artistName = artistName == null ? "" : artistName;
            this.albumName = albumName == null ? "" : albumName;
            this.durationSeconds = Math.max(0.0, durationSeconds);
            this.synced = synced;
            this.plain = plain;
            this.instrumental = instrumental;
            this.isrc = TrackSnapshot.normalizeIsrc(isrc);
            this.score = score;
        }

        static ManualLrclibCandidate from(LrclibCandidate candidate) {
            return new ManualLrclibCandidate(
                    candidate.id,
                    candidate.trackName,
                    candidate.artistName,
                    candidate.albumName,
                    candidate.durationSeconds,
                    candidate.syncedLyrics != null,
                    candidate.plainLyrics != null,
                    candidate.instrumental,
                    candidate.isrc,
                    candidate.score
            );
        }
    }

    interface SpotifyTokenValidationCallback {
        void onSpotifyTokenValidated(long expiresInSeconds);

        void onSpotifyTokenValidationFailed(String message);

        void onSpotifyTokenValidationLog(String message);
    }

    private interface LogSink {
        void write(String message);
    }

    private interface SpotifyMetadataSink {
        void onResolved(SpotifyTrackMatch match);
    }

    private String ui(String key) {
        String lang = aiLyricsSettings == null ? "en" : aiLyricsSettings.snapshot().uiLang;
        return AppI18n.t(lang, key);
    }

    private synchronized LyricsResult getMemoryCachedLyrics(String key) {
        MemoryLyricsCacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.savedAtMs > LYRICS_CACHE_MAX_AGE_MS) {
            cache.remove(key);
            return null;
        }
        return entry.result;
    }

    private synchronized void putMemoryCachedLyrics(String key, LyricsResult result) {
        if (key == null || key.trim().isEmpty() || result == null || result.lines.isEmpty()) {
            return;
        }
        cache.put(key, new MemoryLyricsCacheEntry(result, System.currentTimeMillis()));
    }

    private synchronized void removeMemoryCachedLyrics(String key) {
        cache.remove(key);
    }

    private synchronized void clearMemoryLyricsCache() {
        cache.clear();
    }

    private boolean isCachedResultReusable(
            LyricsResult result,
            LyricsProviderSettings.Snapshot settings
    ) {
        if (result == null || result.lines.isEmpty()) return false;
        if ("manual".equals(result.selectionPolicyKey)) return true;
        return settings != null && settings.cacheKey().equals(result.selectionPolicyKey);
    }

    private boolean shouldRevalidateCachedResult(
            LyricsResult result,
            LyricsProviderSettings.Snapshot settings,
            TrackSnapshot track
    ) {
        String isrc = firstNonEmpty(result == null ? "" : result.isrc, track == null ? "" : track.isrc);
        return LyricsProviderSelectionPlan.shouldRecheckCachedResult(result, settings, isrc);
    }

    void loadLyrics(TrackSnapshot track, Callback callback) {
        if (track == null || !track.hasUsableMetadata()) {
            callback.onLyricsLoaded("", LyricsResult.empty(ui("repo.metadata_waiting")));
            return;
        }

        String key = track.stableKey();
        long requestGeneration = providerPolicyGeneration.get();
        LyricsProviderSettings.Snapshot providerSettings = lyricsProviderSettings.snapshot();
        LyricsResult cached = getMemoryCachedLyrics(key);
        if (!isCachedResultReusable(cached, providerSettings)) {
            cached = null;
            removeMemoryCachedLyrics(key);
        }
        if (cached != null && !shouldRevalidateCachedResult(cached, providerSettings, track)) {
            emitLog(key, callback, "cache hit: " + track.title + " / " + track.artist);
            callback.onLyricsLoaded(key, cached);
            return;
        }
        if (cached != null) {
            emitLog(key, callback, "cache hit: base lyrics served immediately; rechecking OpenDB sync-data in background");
            callback.onLyricsLoaded(key, cached);
        }

        emitLog(key, callback, "track: \"" + track.title + "\" / \"" + track.artist + "\""
                + (track.album.isEmpty() ? "" : " / album=\"" + track.album + "\"")
                + " / duration=" + track.durationMs + "ms"
                + (track.isrc.isEmpty() ? "" : " / player ISRC=" + track.isrc));

        LyricsResult memoryCached = cached;
        executor.execute(() -> {
            LogSink log = message -> emitLog(key, callback, message);
            LyricsResult reusableCached = memoryCached;
            try {
                if (reusableCached == null) {
                    reusableCached = diskCache == null ? null : diskCache.get(key);
                }
                if (!isCachedResultReusable(reusableCached, providerSettings)) {
                    reusableCached = null;
                    if (diskCache != null) diskCache.remove(key);
                }
                if (reusableCached != null
                        && !shouldRevalidateCachedResult(reusableCached, providerSettings, track)) {
                    putMemoryCachedLyrics(key, reusableCached);
                    log.write("disk cache hit: provider=" + reusableCached.providerId
                            + " / contributors=" + reusableCached.contributors.size());
                    LyricsResult finalCached = reusableCached;
                    postLyricsIfCurrent(requestGeneration, callback, key, finalCached);
                    return;
                }
                if (reusableCached != null) {
                    putMemoryCachedLyrics(key, reusableCached);
                    log.write("base lyrics disk cache hit: served immediately; rechecking OpenDB sync-data in background");
                    if (memoryCached == null) {
                        LyricsResult finalCached = reusableCached;
                        postLyricsIfCurrent(requestGeneration, callback, key, finalCached);
                    }
                }
                LyricsResult result = loadLyricsBlocking(track, key, callback, log, reusableCached);
                if (requestGeneration != providerPolicyGeneration.get()) {
                    log.write("stale provider request discarded after settings change");
                    return;
                }
                if (!UnisonLyricsProvider.isUnisonResult(result)) {
                    putMemoryCachedLyrics(key, result);
                    if (diskCache != null) {
                        diskCache.put(key, result);
                    }
                } else {
                    log.write("unison cache: skipped to match provider policy");
                }
                if (shouldPublishRevalidatedLyrics(reusableCached, result)) {
                    postLyricsIfCurrent(requestGeneration, callback, key, result);
                } else {
                    log.write("background sync-data recheck complete: cached lyrics kept");
                }
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                if (reusableCached != null) {
                    log.write("background sync-data recheck error: " + message + "; cached lyrics kept");
                    return;
                }
                log.write("error: " + message);
                mainHandler.post(() -> {
                    if (requestGeneration == providerPolicyGeneration.get()) {
                        callback.onLyricsError(key, message);
                    }
                });
            }
        });
    }

    private void postLyricsIfCurrent(
            long requestGeneration,
            Callback callback,
            String key,
            LyricsResult result
    ) {
        mainHandler.post(() -> {
            if (requestGeneration == providerPolicyGeneration.get()) {
                callback.onLyricsLoaded(key, result);
            }
        });
    }

    void invalidateProviderSelection() {
        providerPolicyGeneration.incrementAndGet();
    }

    void shutdown() {
        executor.shutdownNow();
    }

    void clearCache() {
        clearMemoryLyricsCache();
        if (diskCache != null) {
            diskCache.clear();
        }
        if (syncDataResponseCache != null) {
            syncDataResponseCache.clear();
        }
        clearOpenDbCache();
        markSyncDataServerCacheBypass("");
    }

    void clearCacheForTrack(String trackKey) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return;
        }
        removeMemoryCachedLyrics(key);
        if (diskCache != null) {
            diskCache.remove(key);
        }
    }

    void clearSyncDataCacheForIsrc(String isrc) {
        if (syncDataResponseCache == null) {
            return;
        }
        String key = syncDataCacheKey(isrc);
        if (!key.isEmpty()) {
            syncDataResponseCache.remove(key);
            clearOpenDbCache();
            markSyncDataServerCacheBypass(TrackSnapshot.normalizeIsrc(isrc));
        }
    }

    private void clearOpenDbCache() {
        if (openDbPrefs == null) {
            return;
        }
        openDbPrefs.edit()
                .remove(KEY_OPENDB_PROVIDER_MAP)
                .remove(KEY_OPENDB_FETCHED_AT_MS)
                .remove(KEY_OPENDB_SIGNATURE)
                .remove(KEY_OPENDB_BASE_DATE)
                .remove(KEY_OPENDB_UNAVAILABLE_UNTIL_MS)
                .apply();
    }

    private synchronized void markSyncDataServerCacheBypass(String normalizedIsrc) {
        long expiresAt = System.currentTimeMillis() + SYNC_DATA_SERVER_CACHE_BYPASS_MS;
        String key = normalizedIsrc == null ? "" : normalizedIsrc.trim();
        if (key.isEmpty()) {
            syncDataServerCacheBypassAllUntilMs = expiresAt;
            syncDataServerCacheBypassUntil.clear();
            return;
        }
        syncDataServerCacheBypassUntil.put(key, expiresAt);
    }

    private synchronized boolean shouldBypassSyncDataServerCache(String normalizedIsrc) {
        long now = System.currentTimeMillis();
        if (syncDataServerCacheBypassAllUntilMs > now) {
            return true;
        }
        if (syncDataServerCacheBypassAllUntilMs > 0L) {
            syncDataServerCacheBypassAllUntilMs = 0L;
        }

        String key = normalizedIsrc == null ? "" : normalizedIsrc.trim();
        if (key.isEmpty()) {
            return false;
        }

        Long expiresAt = syncDataServerCacheBypassUntil.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= now) {
            syncDataServerCacheBypassUntil.remove(key);
            return false;
        }
        return true;
    }

    void clearSpotifyTokenCache() {
        invalidateSpotifyToken();
    }

    void searchManualLrclib(
            TrackSnapshot track,
            String title,
            String artist,
            ManualLrclibCallback callback
    ) {
        String key = manualTrackKey(track);
        String queryTitle = firstNonEmpty(title, track == null ? "" : track.title);
        String queryArtist = firstNonEmpty(artist, track == null ? "" : track.artist);
        if (queryTitle.isEmpty()) {
            postManualError(key, callback, ui("lyrics.lrclib_search.empty_title"));
            return;
        }

        executor.execute(() -> {
            LogSink log = message -> emitManualLog(key, callback, message);
            try {
                log.write("manual lrclib search: title=\"" + queryTitle + "\""
                        + (queryArtist.isEmpty() ? "" : " / artist=\"" + queryArtist + "\""));
                List<LrclibCandidate> candidates = searchManualLrclibCandidates(queryTitle, queryArtist, log);
                TrackSnapshot scoringTrack = manualScoringTrack(track, queryTitle, queryArtist);
                for (LrclibCandidate candidate : candidates) {
                    candidate.score = scoringTrack == null ? 0.0 : scoreCandidate(scoringTrack, candidate, null);
                }
                candidates.sort(this::compareLrclibCandidates);
                List<ManualLrclibCandidate> result = new ArrayList<>();
                for (int index = 0; index < Math.min(14, candidates.size()); index++) {
                    result.add(ManualLrclibCandidate.from(candidates.get(index)));
                }
                log.write("manual lrclib search: results=" + result.size());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onManualLrclibSearchResults(key, result);
                    }
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                log.write("manual lrclib search error: " + message);
                postManualError(key, callback, message);
            }
        });
    }

    void loadManualLrclibCandidate(
            TrackSnapshot track,
            ManualLrclibCandidate selected,
            ManualLrclibCallback callback
    ) {
        String key = manualTrackKey(track);
        if (selected == null || selected.id <= 0L) {
            postManualError(key, callback, ui("repo.lyrics_not_found"));
            return;
        }

        executor.execute(() -> {
            LogSink log = message -> emitManualLog(key, callback, message);
            try {
                log.write("manual lrclib load: id=" + selected.id);
                LrclibCandidate candidate = fetchLrclibCandidateById(selected.id, log);
                if (candidate == null) {
                    throw new IOException(ui("repo.lyrics_not_found"));
                }
                LyricsResult result = lyricsResultFromManualCandidate(candidate, track);
                if (!key.isEmpty()) {
                    putMemoryCachedLyrics(key, result);
                    if (diskCache != null) {
                        diskCache.put(key, result);
                    }
                }
                log.write("manual lrclib applied: " + describeLrclibCandidate(candidate)
                        + (key.isEmpty() ? "" : " / cacheKey=" + key));
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onManualLrclibLyricsLoaded(key, result);
                    }
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                log.write("manual lrclib load error: " + message);
                postManualError(key, callback, message);
            }
        });
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

    private LyricsResult loadLyricsBlocking(
            TrackSnapshot track,
            String trackKey,
            Callback callback,
            LogSink log,
            LyricsResult cachedBase
    ) throws Exception {
        boolean hasCachedIsrc = cachedBase != null && !cachedBase.isrc.isEmpty();
        log.write(hasCachedIsrc
                ? "flow: cached ISRC -> sync-data recheck -> cached LRCLIB lyrics"
                : "flow: Spotify Web API search -> sync-data -> LRCLIB source/search -> Unison fallback");
        SpotifyTrackMatch spotifyMatch = null;
        if (hasCachedIsrc) {
            log.write("spotify lookup skipped: cached ISRC=" + cachedBase.isrc
                    + (cachedBase.spotifyTrackId.isEmpty()
                    ? ""
                    : " / trackId=" + cachedBase.spotifyTrackId));
            publishResolvedMetadata(trackKey, cachedBase.isrc, cachedBase.spotifyTrackId, callback);
        } else {
            spotifyMatch = fetchSpotifyIsrc(
                    track,
                    log,
                    match -> publishResolvedSpotifyMetadata(trackKey, match, callback)
            );
            publishSpotifyArtwork(trackKey, spotifyMatch, callback, log);
        }
        boolean isrcFromSpotify = spotifyMatch != null && !spotifyMatch.isrc.isEmpty();
        String isrc = firstNonEmpty(
                spotifyMatch == null ? "" : spotifyMatch.isrc,
                track.isrc,
                cachedBase == null ? "" : cachedBase.isrc
        );
        String spotifyTrackId = firstNonEmpty(
                spotifyMatch == null ? "" : spotifyMatch.spotifyId,
                track.trackId,
                cachedBase == null ? "" : cachedBase.spotifyTrackId
        );
        boolean isrcFromCache = !isrcFromSpotify
                && track.isrc.isEmpty()
                && cachedBase != null
                && !cachedBase.isrc.isEmpty();
        String isrcSource = isrc.isEmpty()
                ? ""
                : (isrcFromSpotify ? "Spotify Web API" : (isrcFromCache ? "lyrics cache" : "player metadata"));
        log.write(isrc.isEmpty()
                ? "isrc: unavailable after Spotify lookup"
                : "isrc: " + isrc + " (" + isrcSource + ")");
        if (!isrc.isEmpty()) {
            publishResolvedMetadata(trackKey, isrc, spotifyTrackId, callback);
        }

        LyricsProviderSettings.Snapshot providerSettings = lyricsProviderSettings.snapshot();
        if (cachedBase != null) {
            Set<String> syncDataProviders = isrc.isEmpty()
                    ? Collections.emptySet()
                    : availableSyncDataProviders(isrc, log);
            String preferredSyncProvider = providerSettings.preferSyncDataProvider
                    ? LyricsProviderSelectionPlan.preferredIvLyricsSyncProviderId(
                    providerSettings,
                    syncDataProviders
            )
                    : "";
            boolean cachedProviderIsPreferred = !preferredSyncProvider.isEmpty()
                    && preferredSyncProvider.equals(cachedBase.providerId);
            if (!preferredSyncProvider.isEmpty()
                    && (!cachedProviderIsPreferred || !cachedBase.karaoke)) {
                if (cachedProviderIsPreferred
                        && LyricsProviderSelectionPlan.canApplyIvLyricsSyncToCachedResult(
                        cachedBase,
                        providerSettings
                )) {
                    SyncDataResult syncData = fetchSyncData(isrc, track, spotifyMatch, log);
                    return applySyncDataToCachedBase(cachedBase, syncData, track, isrc, spotifyTrackId, log)
                            .withSelection(cachedBase.providerId, providerSettings.cacheKey());
                }
                log.write("cached provider " + cachedBase.providerId
                        + " replaced by OpenDB sync-data provider " + preferredSyncProvider);
                return selectLyricsProvider(track, isrc, spotifyTrackId, spotifyMatch, log);
            }
            if (LyricsProviderSelectionPlan.canApplyIvLyricsSyncToCachedResult(cachedBase, providerSettings)) {
                SyncDataResult syncData = isrc.isEmpty() ? null : fetchSyncData(isrc, track, spotifyMatch, log);
                return applySyncDataToCachedBase(cachedBase, syncData, track, isrc, spotifyTrackId, log)
                        .withSelection(cachedBase.providerId, providerSettings.cacheKey());
            }
            log.write("OpenDB sync-data priority unchanged; cached provider kept: " + cachedBase.providerId);
            return cachedBase;
        }

        return selectLyricsProvider(track, isrc, spotifyTrackId, spotifyMatch, log);
    }

    private LyricsResult selectLyricsProvider(
            TrackSnapshot track,
            String isrc,
            String spotifyTrackId,
            SpotifyTrackMatch spotifyMatch,
            LogSink log
    ) {
        LyricsProviderSettings.Snapshot settings = lyricsProviderSettings.snapshot();
        Set<String> syncDataProviders = availableSyncDataProviders(isrc, log);
        LyricsProviderSelectionPlan plan = LyricsProviderSelectionPlan.create(settings, syncDataProviders);
        log.write("provider policy: " + (settings.typeFirst ? "karaoke -> synced -> plain" : "provider first")
                + " / order=" + providerIds(plan.providers)
                + " / opendb=" + syncDataProviders);

        Map<String, ProviderCandidate> attempts = new HashMap<>();
        for (LyricsProviderSelectionPlan.Attempt attempt : plan.attempts) {
            LyricsProviderSettings.ProviderConfig config = attempt.config;
            ProviderCandidate candidate = loadProviderOnce(
                    attempts, config, track, isrc, spotifyTrackId, spotifyMatch, syncDataProviders, log
            );
            LyricsResult selected = resultForType(candidate, attempt.type);
            if (selected != null) {
                log.write("provider selected: " + config.provider.id + " / type=" + attempt.type);
                return selected.withSelection(config.provider.id, settings.cacheKey());
            }
        }
        return LyricsResult.empty(ui("repo.lyrics_not_found"));
    }

    private ProviderCandidate loadProviderOnce(
            Map<String, ProviderCandidate> attempts,
            LyricsProviderSettings.ProviderConfig config,
            TrackSnapshot track,
            String isrc,
            String spotifyTrackId,
            SpotifyTrackMatch spotifyMatch,
            Set<String> syncDataProviders,
            LogSink log
    ) {
        if (attempts.containsKey(config.provider.id)) return attempts.get(config.provider.id);
        LyricsResult result = null;
        ProviderCandidate candidate = null;
        try {
            if (LyricsProviderSettings.PROVIDER_LYRICS_PLUS.equals(config.provider.id)) {
                LyricsPlusLyricsProvider.Variants variants = LyricsPlusLyricsProvider.fetchVariants(
                        track, isrc, spotifyTrackId, log::write
                );
                if (variants != null) {
                    candidate = new ProviderCandidate(variants.karaoke, variants.synced, variants.plain);
                }
            } else if (LyricsProviderSettings.PROVIDER_UNISON.equals(config.provider.id)) {
                result = UnisonLyricsProvider.fetch(track, isrc, spotifyTrackId, log::write);
            } else if (LyricsProviderSettings.PROVIDER_LRCLIB.equals(config.provider.id)) {
                SyncDataResult syncData = !isrc.isEmpty() && config.karaoke && syncDataProviders.contains(config.provider.id)
                        ? fetchSyncData(isrc, track, spotifyMatch, log)
                        : null;
                result = loadLrclibProvider(track, isrc, spotifyTrackId, spotifyMatch, syncData, log);
            }
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            log.write(config.provider.id + " error: " + message);
        }
        if (candidate == null && result != null && !result.lines.isEmpty()) {
            candidate = candidateFromResult(result);
        }
        attempts.put(config.provider.id, candidate);
        return candidate;
    }

    private ProviderCandidate candidateFromResult(LyricsResult result) {
        LyricsResult karaoke = result.karaoke ? result : null;
        LyricsResult synced = hasTimedLyrics(result.lines) ? asLineSynced(result) : null;
        LyricsResult plain = asPlain(result);
        return new ProviderCandidate(karaoke, synced, plain);
    }

    private LyricsResult resultForType(ProviderCandidate candidate, String type) {
        if (candidate == null) return null;
        if (LyricsProviderSettings.TYPE_KARAOKE.equals(type)) return candidate.karaoke;
        if (LyricsProviderSettings.TYPE_SYNCED.equals(type)) return candidate.synced;
        return candidate.plain;
    }

    private LyricsResult asLineSynced(LyricsResult source) {
        List<LyricsLine> lines = new ArrayList<>();
        for (LyricsLine line : source.lines) {
            if (!line.isTimed()) continue;
            lines.add(new LyricsLine(
                    line.startTimeMs,
                    line.endTimeMs,
                    line.text,
                    Collections.emptyList(),
                    line.speaker,
                    line.speakerColor,
                    line.speakerFallback,
                    line.kind,
                    Collections.emptyList()
            ));
        }
        if (lines.isEmpty()) return null;
        return new LyricsResult(lines, providerBaseLabel(source.providerLabel) + " synced", source.detail,
                false, source.isrc, source.spotifyTrackId, source.contributors);
    }

    private LyricsResult asPlain(LyricsResult source) {
        List<LyricsLine> lines = new ArrayList<>();
        for (LyricsLine line : source.lines) {
            if (line.text == null || line.text.trim().isEmpty()) continue;
            lines.add(new LyricsLine(0L, 0L, line.text, Collections.emptyList(), line.speaker,
                    line.speakerColor, line.speakerFallback, line.kind, Collections.emptyList()));
        }
        if (lines.isEmpty()) return null;
        return new LyricsResult(lines, providerBaseLabel(source.providerLabel) + " plain", source.detail,
                false, source.isrc, source.spotifyTrackId, source.contributors);
    }

    private String providerBaseLabel(String label) {
        String value = label == null ? "" : label.trim();
        for (String suffix : new String[]{" karaoke", " synced", " plain"}) {
            if (value.toLowerCase(Locale.ROOT).endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private boolean hasTimedLyrics(List<LyricsLine> lines) {
        for (LyricsLine line : lines) if (line.isTimed()) return true;
        return false;
    }

    private Set<String> availableSyncDataProviders(String isrc, LogSink log) {
        String normalized = TrackSnapshot.normalizeIsrc(isrc);
        if (normalized.isEmpty()) return Collections.emptySet();
        try {
            JSONObject providerMap = loadOpenDbProviderMap(log);
            if (providerMap == null) return Collections.emptySet();
            Set<String> result = new LinkedHashSet<>();
            Iterator<String> providers = providerMap.keys();
            while (providers.hasNext()) {
                String provider = providers.next().trim().toLowerCase(Locale.ROOT);
                if (jsonArrayContains(providerMap.optJSONArray(provider), normalized)) result.add(provider);
            }
            return result;
        } catch (Exception error) {
            log.write("sync-data opendb error: " + error.getMessage());
            return Collections.emptySet();
        }
    }

    private String providerIds(List<LyricsProviderSettings.ProviderConfig> providers) {
        List<String> ids = new ArrayList<>();
        for (LyricsProviderSettings.ProviderConfig provider : providers) ids.add(provider.provider.id);
        return ids.toString();
    }

    private LyricsResult loadLrclibProvider(
            TrackSnapshot track,
            String isrc,
            String spotifyTrackId,
            SpotifyTrackMatch spotifyMatch,
            SyncDataResult syncData,
            LogSink log
    ) {
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
            try {
                candidate = searchBestCandidate(track, spotifyMatch, syncData, log);
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage();
                log.write("lrclib search error: " + message + "; continuing with Unison");
            }
        }

        if (candidate == null) {
            log.write("result: no LRCLIB candidate selected");
            return null;
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
            return null;
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
            if (!syncData.contributors.isEmpty()) {
                log.write("sync-data contributors ignored: sync-data was not applied");
            }
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
                Collections.emptyList()
        );
    }

    private LyricsResult applySyncDataToCachedBase(
            LyricsResult cachedBase,
            SyncDataResult syncData,
            TrackSnapshot track,
            String isrc,
            String spotifyTrackId,
            LogSink log
    ) {
        List<LyricsLine> baseLines = cachedBase.lines;
        log.write("lyrics base: lines=" + baseLines.size() + " / source=cache");
        if (syncData != null) {
            SyncDataApplier.ApplyResult applied = SyncDataApplier.applyWithDiagnostics(baseLines, syncData.syncBody, track);
            for (String diagnostic : applied.diagnostics) {
                log.write("sync-data apply: " + diagnostic);
            }
            if (!applied.lines.isEmpty()) {
                log.write("sync-data applied to cached lyrics: karaoke lines=" + applied.lines.size()
                        + " / vocalParts=" + countVocalParts(applied.lines));
                return new LyricsResult(
                        applied.lines,
                        "ivLyrics sync-data + LRCLIB",
                        ui("repo.detail.sync_applied_search"),
                        true,
                        isrc,
                        spotifyTrackId,
                        syncData.contributors
                );
            }
            log.write("sync-data apply failed for cached lyrics: keeping LRCLIB line lyrics");
        }

        String detail = isrc.isEmpty()
                ? ui("repo.detail.no_spotify_isrc")
                : (syncData == null ? cachedBase.detail : ui("repo.detail.sync_apply_failed"));
        return new LyricsResult(
                baseLines,
                cachedBase.providerLabel,
                detail,
                false,
                firstNonEmpty(isrc, cachedBase.isrc),
                firstNonEmpty(spotifyTrackId, cachedBase.spotifyTrackId),
                cachedBase.contributors
        );
    }

    private boolean shouldPublishRevalidatedLyrics(LyricsResult cached, LyricsResult refreshed) {
        if (cached == null) {
            return true;
        }
        return refreshed != null
                && !refreshed.lines.isEmpty()
                && ((refreshed.karaoke && !cached.karaoke)
                || !refreshed.providerId.equals(cached.providerId));
    }

    private LyricsResult lyricsResultFromManualCandidate(LrclibCandidate candidate, TrackSnapshot track) {
        boolean lineSynced = candidate.useSyncedLyrics();
        List<LyricsLine> lines = lineSynced
                ? LrcParser.parseSynced(candidate.syncedLyrics, secondsToMs(
                candidate.durationSeconds,
                track == null ? 0L : track.durationMs
        ))
                : LrcParser.parsePlain(candidate.plainLyrics);
        if (lines.isEmpty()) {
            return new LyricsResult(
                    Collections.emptyList(),
                    "LRCLIB",
                    candidate.instrumental ? ui("repo.instrumental") : ui("repo.no_renderable_lyrics"),
                    false,
                    firstNonEmpty(candidate.isrc, track == null ? "" : track.isrc),
                    track == null ? "" : track.trackId,
                    Collections.emptyList()
            );
        }
        return new LyricsResult(
                lines,
                lineSynced ? "LRCLIB synced" : "LRCLIB plain",
                ui("repo.detail.manual_lrclib"),
                false,
                firstNonEmpty(candidate.isrc, track == null ? "" : track.isrc),
                track == null ? "" : track.trackId,
                Collections.emptyList()
        ).withSelection(LyricsProviderSettings.PROVIDER_LRCLIB, "manual");
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

    private void publishResolvedSpotifyMetadata(String trackKey, SpotifyTrackMatch match, Callback callback) {
        if (match == null) {
            return;
        }
        publishResolvedMetadata(trackKey, match.isrc, match.spotifyId, callback);
    }

    private void publishResolvedMetadata(String trackKey, String isrc, String spotifyTrackId, Callback callback) {
        if (callback == null) {
            return;
        }
        String normalizedIsrc = TrackSnapshot.normalizeIsrc(isrc);
        String safeSpotifyTrackId = spotifyTrackId == null ? "" : spotifyTrackId.trim();
        if (normalizedIsrc.isEmpty() && safeSpotifyTrackId.isEmpty()) {
            return;
        }
        mainHandler.post(() -> callback.onLyricsMetadataResolved(trackKey, normalizedIsrc, safeSpotifyTrackId));
    }

    private int countVocalParts(List<LyricsLine> lines) {
        int count = 0;
        for (LyricsLine line : lines) {
            count += line.vocalParts == null ? 0 : line.vocalParts.size();
        }
        return count;
    }

    private LrclibCandidate searchBestCandidate(
            TrackSnapshot track,
            SpotifyTrackMatch spotifyMatch,
            SyncDataResult syncData,
            LogSink log
    ) throws Exception {
        List<LrclibCandidate> candidates = new ArrayList<>();
        TrackSnapshot spotifySearchTrack = buildSpotifyLrclibSearchTrack(track, spotifyMatch, log);
        String spotifySearchLabelPrefix = spotifyMatch != null && spotifyMatch.hasEnglishMetadata() ? "spotify-en" : "spotify";
        appendUniqueCandidates(candidates, searchLrclib(buildStructuredQuery(track), "structured", log));
        appendSpotifyLrclibSearchCandidates(candidates, spotifySearchTrack, spotifySearchLabelPrefix, "structured", log);
        appendSpotifyLrclibSearchCandidates(candidates, spotifySearchTrack, spotifySearchLabelPrefix, "q:title+artist", log);
        appendSpotifyLrclibSearchCandidates(candidates, spotifySearchTrack, spotifySearchLabelPrefix, "q:title", log);

        boolean needsLegacyShapeMatch = needsLegacySyncLineShapeMatch(syncData, candidates);
        if (candidates.isEmpty() || needsLegacyShapeMatch) {
            appendUniqueCandidates(candidates, searchLrclib(Collections.singletonMap("q", track.title + " " + track.artist), "q:title+artist", log));
            needsLegacyShapeMatch = needsLegacySyncLineShapeMatch(syncData, candidates);
        }
        if (candidates.isEmpty() || needsLegacyShapeMatch) {
            appendUniqueCandidates(candidates, searchLrclib(Collections.singletonMap("q", track.title), "q:title", log));
        }
        if (candidates.isEmpty()) {
            log.write("lrclib search: no candidates");
            return null;
        }

        for (LrclibCandidate candidate : candidates) {
            decorateCandidateForSyncData(candidate, syncData);
            candidate.albumMatchScore = 0.0;
            candidate.score = scoreCandidate(track, candidate, syncData);
            if (spotifySearchTrack != null) {
                candidate.score = Math.max(candidate.score, scoreCandidate(spotifySearchTrack, candidate, syncData));
            }
        }
        candidates.sort(this::compareLrclibCandidates);
        if (syncData != null && !syncData.lineCharCounts().isEmpty()) {
            log.write("lrclib sync-data exact line-shape candidates="
                    + countSyncLineExactCandidates(candidates));
        }
        log.write("lrclib ranked candidates:");
        for (int index = 0; index < Math.min(5, candidates.size()); index++) {
            LrclibCandidate candidate = candidates.get(index);
            log.write("  #" + (index + 1)
                    + " score=" + fmt(candidate.score)
                    + " album=" + fmt(candidate.albumMatchScore)
                    + " sourceScore=" + candidate.syncSourceMatchScore
                    + " syncLineExact=" + candidate.syncLineExactMatch
                    + " preferred=" + candidate.preferredLyricsSource
                    + " " + describeLrclibCandidate(candidate));
        }
        if (shouldPreferLegacyExactSyncLineShape(syncData)) {
            LrclibCandidate exact = selectLegacyExactLineShapeCandidate(track, candidates, log);
            if (exact != null) {
                return exact;
            }
            log.write("lrclib legacy sync-data: no exact line-shape candidate found; using ranked fallback");
        }
        LrclibCandidate best = selectSyncedFallbackCandidate(
                track,
                spotifySearchTrack,
                syncData,
                candidates,
                candidates.get(0),
                log
        );
        if (best.score <= 2.2 && best.syncSourceMatchScore <= 0 && !best.syncLineExactMatch) {
            log.write("lrclib selected: rejected top candidate, score below threshold: " + fmt(best.score));
            return null;
        }
        return best;
    }

    private void appendSpotifyLrclibSearchCandidates(
            List<LrclibCandidate> candidates,
            TrackSnapshot spotifySearchTrack,
            String labelPrefix,
            String mode,
            LogSink log
    ) throws Exception {
        if (spotifySearchTrack == null || spotifySearchTrack.title.isEmpty() || spotifySearchTrack.artist.isEmpty()) {
            return;
        }
        String label = (labelPrefix == null || labelPrefix.trim().isEmpty() ? "spotify" : labelPrefix.trim()) + ":" + mode;
        if ("structured".equals(mode)) {
            appendUniqueCandidates(candidates, searchLrclib(buildStructuredQuery(spotifySearchTrack, false), label, log));
            return;
        }
        if ("q:title+artist".equals(mode)) {
            appendUniqueCandidates(candidates, searchLrclib(
                    Collections.singletonMap("q", spotifySearchTrack.title + " " + spotifySearchTrack.artist),
                    label,
                    log
            ));
            return;
        }
        if ("q:title".equals(mode)) {
            appendUniqueCandidates(candidates, searchLrclib(
                    Collections.singletonMap("q", spotifySearchTrack.title),
                    label,
                    log
            ));
        }
    }

    private TrackSnapshot buildSpotifyLrclibSearchTrack(
            TrackSnapshot track,
            SpotifyTrackMatch spotifyMatch,
            LogSink log
    ) {
        if (track == null || spotifyMatch == null) {
            return null;
        }

        String spotifyTitle = firstNonEmpty(spotifyMatch.englishTitle, spotifyMatch.title);
        String spotifyArtist = firstNonEmpty(spotifyMatch.englishArtist, spotifyMatch.artist);
        String spotifyAlbum = firstNonEmpty(spotifyMatch.englishAlbum, spotifyMatch.album);
        if (spotifyTitle.isEmpty() || spotifyArtist.isEmpty()) {
            return null;
        }
        if (sameSearchMetadata(track.title, spotifyTitle)
                && sameSearchMetadata(track.artist, spotifyArtist)
                && (spotifyAlbum.isEmpty() || sameSearchMetadata(track.album, spotifyAlbum))) {
            return null;
        }

        boolean englishMetadata = spotifyMatch.hasEnglishMetadata();
        log.write("lrclib spotify metadata search enabled"
                + " / english=" + englishMetadata
                + " / title=\"" + spotifyTitle + "\""
                + " / artist=\"" + spotifyArtist + "\""
                + (spotifyAlbum.isEmpty() || englishMetadata ? "" : " / album=\"" + spotifyAlbum + "\""));
        return new TrackSnapshot(
                spotifyTitle,
                spotifyArtist,
                englishMetadata ? "" : spotifyAlbum,
                track.packageName,
                track.mediaId,
                firstNonEmpty(spotifyMatch.isrc, track.isrc),
                spotifyMatch.durationMs > 0L ? spotifyMatch.durationMs : track.durationMs,
                track.positionMs,
                track.lastPositionUpdateElapsedMs,
                track.playbackSpeed,
                track.playing,
                track.artwork,
                track.artworkUri
        );
    }

    private List<LrclibCandidate> searchManualLrclibCandidates(String title, String artist, LogSink log) throws Exception {
        List<LrclibCandidate> candidates = new ArrayList<>();
        Map<String, String> structured = new LinkedHashMap<>();
        structured.put("track_name", title);
        if (artist != null && !artist.trim().isEmpty()) {
            structured.put("artist_name", artist.trim());
        }
        appendUniqueCandidates(candidates, searchLrclib(structured, "manual:structured", log));

        if (artist != null && !artist.trim().isEmpty()) {
            appendUniqueCandidates(candidates, searchLrclib(
                    Collections.singletonMap("q", title + " " + artist.trim()),
                    "manual:q:title+artist",
                    log
            ));
        }
        appendUniqueCandidates(candidates, searchLrclib(
                Collections.singletonMap("q", title),
                "manual:q:title",
                log
        ));
        return candidates;
    }

    private TrackSnapshot manualScoringTrack(TrackSnapshot track, String title, String artist) {
        if ((title == null || title.trim().isEmpty()) && (track == null || track.title.isEmpty())) {
            return null;
        }
        String scoreTitle = firstNonEmpty(title, track == null ? "" : track.title);
        String scoreArtist = firstNonEmpty(artist, track == null ? "" : track.artist);
        if (scoreTitle.isEmpty() || scoreArtist.isEmpty()) {
            return null;
        }
        return new TrackSnapshot(
                scoreTitle,
                scoreArtist,
                track == null ? "" : track.album,
                track == null ? "" : track.packageName,
                track == null ? "" : track.mediaId,
                track == null ? "" : track.isrc,
                track == null ? 0L : track.durationMs,
                track == null ? 0L : track.positionMs,
                track == null ? 0L : track.lastPositionUpdateElapsedMs,
                track == null ? 1f : track.playbackSpeed,
                track != null && track.playing,
                track == null ? null : track.artwork,
                track == null ? "" : track.artworkUri
        );
    }

    private boolean needsLegacySyncLineShapeMatch(SyncDataResult syncData, List<LrclibCandidate> candidates) {
        if (!shouldPreferLegacyExactSyncLineShape(syncData)) {
            return false;
        }
        return !hasSyncLineExactCandidate(candidates, syncData);
    }

    private boolean shouldPreferLegacyExactSyncLineShape(SyncDataResult syncData) {
        return syncData != null
                && !syncData.lineCharCounts().isEmpty()
                && syncData.lrclibId() <= 0L
                && syncData.sourceLineCharCounts().isEmpty();
    }

    private LrclibCandidate selectLegacyExactLineShapeCandidate(
            TrackSnapshot track,
            List<LrclibCandidate> candidates,
            LogSink log
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<LrclibCandidate> exactCandidates = new ArrayList<>();
        for (LrclibCandidate candidate : candidates) {
            if (candidate != null && candidate.syncLineExactMatch) {
                exactCandidates.add(candidate);
            }
        }
        if (exactCandidates.isEmpty()) {
            return null;
        }

        exactCandidates.sort((left, right) -> compareLegacyExactLineShapeCandidates(track, left, right));
        LrclibCandidate selected = exactCandidates.get(0);
        log.write("lrclib legacy sync-data: selected exact line-shape candidate"
                + " / group=" + legacyExactLineShapeGroup(selected)
                + " / withinDuration=" + isWithinDurationTolerance(track, selected)
                + " / " + describeLrclibCandidate(selected));
        return selected;
    }

    private int compareLegacyExactLineShapeCandidates(
            TrackSnapshot track,
            LrclibCandidate left,
            LrclibCandidate right
    ) {
        int group = Integer.compare(legacyExactLineShapeGroup(right), legacyExactLineShapeGroup(left));
        if (group != 0) return group;

        int durationWithin = Boolean.compare(
                isWithinDurationTolerance(track, right),
                isWithinDurationTolerance(track, left)
        );
        if (durationWithin != 0) return durationWithin;

        int durationDiff = Double.compare(durationDiffSeconds(track, left), durationDiffSeconds(track, right));
        if (durationDiff != 0) return durationDiff;

        return compareLrclibCandidates(left, right);
    }

    private int legacyExactLineShapeGroup(LrclibCandidate candidate) {
        if (candidate == null) {
            return 0;
        }
        if (candidate.hasOriginalLyricsScript && candidate.exactSyncedLineMatch) {
            return 4;
        }
        if (candidate.hasOriginalLyricsScript && candidate.exactPlainLineMatch) {
            return 3;
        }
        if (candidate.exactSyncedLineMatch) {
            return 2;
        }
        return candidate.exactPlainLineMatch ? 1 : 0;
    }

    private boolean isWithinDurationTolerance(TrackSnapshot track, LrclibCandidate candidate) {
        return durationDiffSeconds(track, candidate) <= DURATION_TOLERANCE_SECONDS;
    }

    private double durationDiffSeconds(TrackSnapshot track, LrclibCandidate candidate) {
        if (track == null || candidate == null || track.durationMs <= 0L || candidate.durationSeconds <= 0.0) {
            return Double.MAX_VALUE;
        }
        return Math.abs((track.durationMs / 1000.0) - candidate.durationSeconds);
    }

    private boolean hasSyncLineExactCandidate(List<LrclibCandidate> candidates, SyncDataResult syncData) {
        if (candidates == null || candidates.isEmpty() || syncData == null) {
            return false;
        }
        for (LrclibCandidate candidate : candidates) {
            decorateCandidateForSyncData(candidate, syncData);
            if (candidate.syncLineExactMatch) {
                return true;
            }
        }
        return false;
    }

    private int countSyncLineExactCandidates(List<LrclibCandidate> candidates) {
        int count = 0;
        if (candidates == null) {
            return count;
        }
        for (LrclibCandidate candidate : candidates) {
            if (candidate != null && candidate.syncLineExactMatch) {
                count++;
            }
        }
        return count;
    }

    private void appendUniqueCandidates(List<LrclibCandidate> target, List<LrclibCandidate> next) {
        if (target == null || next == null || next.isEmpty()) {
            return;
        }
        for (LrclibCandidate candidate : next) {
            if (candidate == null || containsCandidate(target, candidate)) {
                continue;
            }
            target.add(candidate);
        }
    }

    private boolean containsCandidate(List<LrclibCandidate> candidates, LrclibCandidate candidate) {
        for (LrclibCandidate existing : candidates) {
            if (existing == null) {
                continue;
            }
            if (existing.id > 0L && candidate.id > 0L && existing.id == candidate.id) {
                return true;
            }
            if (existing.id <= 0L || candidate.id <= 0L) {
                String existingKey = lrclibCandidateKey(existing);
                String nextKey = lrclibCandidateKey(candidate);
                if (!existingKey.isEmpty() && existingKey.equals(nextKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String lrclibCandidateKey(LrclibCandidate candidate) {
        if (candidate == null) {
            return "";
        }
        return (candidate.trackName + "\n" + candidate.artistName + "\n" + candidate.albumName)
                .toLowerCase(Locale.ROOT)
                .trim();
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

    private LrclibCandidate selectSyncedFallbackCandidate(
            TrackSnapshot track,
            TrackSnapshot spotifySearchTrack,
            SyncDataResult syncData,
            List<LrclibCandidate> candidates,
            LrclibCandidate best,
            LogSink log
    ) {
        if (best == null || candidates == null || candidates.isEmpty() || !shouldPreferSyncedLrclibFallback(syncData)) {
            return best;
        }
        if (best.useSyncedLyrics()) {
            return best;
        }
        if (hasSyncedLyricsPayload(best) && isReasonableSyncedFallbackMatch(track, spotifySearchTrack, best)) {
            best.preferredLyricsSource = "synced";
            log.write("lrclib synced fallback: using synced lyrics from top candidate"
                    + " / score=" + fmt(best.score)
                    + " / " + describeLrclibCandidate(best));
            return best;
        }

        double floor = best.score - LRCLIB_SYNCED_FALLBACK_SCORE_WINDOW;
        for (LrclibCandidate candidate : candidates) {
            if (candidate == null || candidate == best || !hasSyncedLyricsPayload(candidate)) {
                continue;
            }
            if (candidate.score + 0.0001 < floor) {
                continue;
            }
            if (!isReasonableSyncedFallbackMatch(track, spotifySearchTrack, candidate)) {
                continue;
            }
            if (!passesLrclibSelectionThreshold(candidate)) {
                continue;
            }
            candidate.preferredLyricsSource = "synced";
            log.write("lrclib synced fallback: selected synced candidate within score window"
                    + " / bestPlainScore=" + fmt(best.score)
                    + " / syncedScore=" + fmt(candidate.score)
                    + " / window=" + fmt(LRCLIB_SYNCED_FALLBACK_SCORE_WINDOW)
                    + " / " + describeLrclibCandidate(candidate));
            return candidate;
        }
        return best;
    }

    private boolean shouldPreferSyncedLrclibFallback(SyncDataResult syncData) {
        return syncData == null
                || (syncData.lrclibId() <= 0L
                && syncData.lineCharCounts().isEmpty());
    }

    private boolean passesLrclibSelectionThreshold(LrclibCandidate candidate) {
        return candidate != null
                && (candidate.score > 2.2
                || candidate.syncSourceMatchScore > 0
                || candidate.syncLineExactMatch);
    }

    private boolean hasSyncedLyricsPayload(LrclibCandidate candidate) {
        return candidate != null
                && candidate.syncedLyrics != null
                && !candidate.syncedLyrics.trim().isEmpty();
    }

    private boolean isReasonableSyncedFallbackMatch(
            TrackSnapshot track,
            TrackSnapshot spotifySearchTrack,
            LrclibCandidate candidate
    ) {
        if (candidate == null) {
            return false;
        }
        if (!candidate.isrc.isEmpty()) {
            if (track != null && candidate.isrc.equals(track.isrc)) {
                return true;
            }
            if (spotifySearchTrack != null && candidate.isrc.equals(spotifySearchTrack.isrc)) {
                return true;
            }
        }
        return isReasonableSyncedFallbackMatch(track, candidate)
                || isReasonableSyncedFallbackMatch(spotifySearchTrack, candidate);
    }

    private boolean isReasonableSyncedFallbackMatch(TrackSnapshot track, LrclibCandidate candidate) {
        if (track == null || candidate == null) {
            return false;
        }
        double title = titleScore(track.title, candidate.trackName);
        double artist = bestArtistScore(track.artist, candidate.artistName);
        boolean durationOk = track.durationMs <= 0L
                || candidate.durationSeconds <= 0.0
                || isWithinDurationTolerance(track, candidate);
        return title >= LRCLIB_SYNCED_FALLBACK_MIN_TITLE_SCORE
                && artist >= LRCLIB_SYNCED_FALLBACK_MIN_ARTIST_SCORE
                && durationOk;
    }

    private Map<String, String> buildStructuredQuery(TrackSnapshot track) {
        return buildStructuredQuery(track, true);
    }

    private Map<String, String> buildStructuredQuery(TrackSnapshot track, boolean includeAlbum) {
        Map<String, String> params = new HashMap<>();
        params.put("track_name", track.title);
        params.put("artist_name", track.artist);
        if (includeAlbum && !track.album.isEmpty()) {
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
            String normalizedIsrc = TrackSnapshot.normalizeIsrc(isrc);
            if (normalizedIsrc.isEmpty()) {
                return null;
            }
            String cacheKey = syncDataCacheKey(isrc);
            String cachedResponse = syncDataResponseCache == null ? "" : syncDataResponseCache.get(cacheKey);
            if (!cachedResponse.isEmpty()) {
                log.write("sync-data cache hit: isrc=" + normalizedIsrc);
                return parseSyncDataResponse(cachedResponse, log, true);
            }

            boolean bypassServerCache = shouldBypassSyncDataServerCache(normalizedIsrc);
            if (bypassServerCache) {
                if (isOpenDbUnavailable(log)) {
                    log.write("sync-data opendb: unavailable after cache clear, skip direct sync-data request");
                    return null;
                }
            } else if (!shouldRequestSyncDataFromOpenDb(normalizedIsrc, LRCLIB_PROVIDER_ID, log)) {
                return null;
            }

            Map<String, String> params = new HashMap<>();
            params.put("isrc", normalizedIsrc);
            params.put("provider", "lrclib");
            params.put("request-version", SYNC_DATA_REQUEST_VERSION);
            params.put("metadata", "1");
            if (bypassServerCache) {
                params.put("bypassCache", "1");
            }
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
            SyncDataResult result = parseSyncDataResponse(response, log, false);
            if (syncDataResponseCache != null && !cacheKey.isEmpty()) {
                syncDataResponseCache.put(cacheKey, response);
            }
            return result;
        } catch (Exception error) {
            log.write("sync-data error: " + error.getMessage());
            return null;
        }
    }

    private boolean shouldRequestSyncDataFromOpenDb(String isrc, String provider, LogSink log) {
        String normalizedIsrc = TrackSnapshot.normalizeIsrc(isrc);
        String normalizedProvider = provider == null ? "" : provider.trim();
        if (normalizedIsrc.isEmpty() || normalizedProvider.isEmpty()) {
            return false;
        }

        try {
            JSONObject providerMap = loadOpenDbProviderMap(log);
            if (providerMap == null) {
                log.write("sync-data opendb: unavailable, skip direct sync-data request");
                return false;
            }
            JSONArray providerItems = providerMap.optJSONArray(normalizedProvider);
            boolean exists = jsonArrayContains(providerItems, normalizedIsrc);
            if (!exists) {
                log.write("sync-data opendb: not listed, skip direct sync-data request"
                        + " / provider=" + normalizedProvider
                        + " / isrc=" + normalizedIsrc);
            }
            return exists;
        } catch (Exception error) {
            markOpenDbUnavailable();
            log.write("sync-data opendb error: " + error.getMessage());
            return false;
        }
    }

    private boolean isOpenDbUnavailable(LogSink log) {
        try {
            return loadOpenDbProviderMap(log) == null;
        } catch (Exception error) {
            markOpenDbUnavailable();
            log.write("sync-data opendb error: " + error.getMessage());
            return true;
        }
    }

    private JSONObject loadOpenDbProviderMap(LogSink log) throws Exception {
        long now = System.currentTimeMillis();
        if (openDbPrefs == null) {
            return null;
        }

        long unavailableUntil = openDbPrefs.getLong(KEY_OPENDB_UNAVAILABLE_UNTIL_MS, 0L);
        if (unavailableUntil > now) {
            return null;
        }

        String cached = openDbPrefs.getString(KEY_OPENDB_PROVIDER_MAP, "");
        long fetchedAt = openDbPrefs.getLong(KEY_OPENDB_FETCHED_AT_MS, 0L);
        if (cached != null && !cached.isEmpty() && now - fetchedAt < OPENDB_FRESH_MS) {
            return new JSONObject(cached);
        }

        try {
            log.write("sync-data opendb manifest request");
            JSONObject manifest = new JSONObject(get(OPENDB_MANIFEST_URL));
            String signature = getOpenDbManifestSignature(manifest);
            String storedSignature = openDbPrefs.getString(KEY_OPENDB_SIGNATURE, "");
            if (cached != null && !cached.isEmpty() && signature.equals(storedSignature)) {
                JSONObject providerMap = new JSONObject(cached);
                openDbPrefs.edit()
                        .putLong(KEY_OPENDB_FETCHED_AT_MS, now)
                        .putLong(KEY_OPENDB_UNAVAILABLE_UNTIL_MS, 0L)
                        .apply();
                log.write("sync-data opendb manifest unchanged: cached provider map reused");
                return providerMap;
            }

            JSONObject refreshed = refreshOpenDbProviderMap(manifest, log);
            JSONObject base = manifest.optJSONObject("base");
            openDbPrefs.edit()
                    .putString(KEY_OPENDB_PROVIDER_MAP, refreshed.toString())
                    .putLong(KEY_OPENDB_FETCHED_AT_MS, now)
                    .putString(KEY_OPENDB_SIGNATURE, signature)
                    .putString(KEY_OPENDB_BASE_DATE, base == null ? "" : base.optString("date", ""))
                    .putLong(KEY_OPENDB_UNAVAILABLE_UNTIL_MS, 0L)
                    .apply();
            return refreshed;
        } catch (Exception error) {
            openDbPrefs.edit()
                    .putLong(KEY_OPENDB_UNAVAILABLE_UNTIL_MS, now + OPENDB_UNAVAILABLE_RETRY_MS)
                    .apply();
            throw error;
        }
    }

    private String getOpenDbManifestSignature(JSONObject manifest) throws Exception {
        JSONObject signature = new JSONObject();
        signature.put("schema", manifest == null ? 0 : manifest.optInt("schema", 0));

        JSONObject base = manifest == null ? null : manifest.optJSONObject("base");
        signature.put("base", base == null ? "" : firstNonEmpty(
                base.optString("sha256", ""),
                base.optString("url", "")
        ));

        JSONArray deltaSignatures = new JSONArray();
        JSONArray deltas = manifest == null ? null : manifest.optJSONArray("deltas");
        if (deltas != null) {
            for (int index = 0; index < deltas.length(); index++) {
                JSONObject delta = deltas.optJSONObject(index);
                deltaSignatures.put(delta == null ? "" : firstNonEmpty(
                        delta.optString("sha256", ""),
                        delta.optString("url", ""),
                        delta.optString("date", "")
                ));
            }
        }
        signature.put("deltas", deltaSignatures);

        JSONObject current = manifest == null ? null : manifest.optJSONObject("current");
        signature.put("current", current == null ? "" : firstNonEmpty(
                current.optString("sha256", ""),
                current.optString("updatedAt", ""),
                current.optString("url", "")
        ));
        return signature.toString();
    }

    private JSONObject refreshOpenDbProviderMap(JSONObject manifest, LogSink log) throws Exception {
        JSONObject providerMap = new JSONObject();

        JSONObject base = manifest.optJSONObject("base");
        if (base != null) {
            mergeOpenDbProviderFile(providerMap, base.optString("url", ""), false);
        }

        JSONArray deltas = manifest.optJSONArray("deltas");
        if (deltas != null) {
            for (int index = 0; index < deltas.length(); index++) {
                JSONObject delta = deltas.optJSONObject(index);
                if (delta != null) {
                    mergeOpenDbProviderFile(providerMap, delta.optString("url", ""), true);
                }
            }
        }

        JSONObject current = manifest.optJSONObject("current");
        if (current != null) {
            mergeOpenDbProviderFile(providerMap, current.optString("url", ""), true);
        }

        JSONArray lrclib = providerMap.optJSONArray(LRCLIB_PROVIDER_ID);
        log.write("sync-data opendb refreshed: lrclib="
                + (lrclib == null ? 0 : lrclib.length()));
        return providerMap;
    }

    private void mergeOpenDbProviderFile(JSONObject providerMap, String relativeUrl, boolean delta) throws Exception {
        String url = resolveOpenDbUrl(relativeUrl);
        if (url.isEmpty()) {
            return;
        }
        JSONObject file = new JSONObject(get(url));
        if (delta) {
            mergeOpenDbProviderObject(providerMap, file.optJSONObject("add"), true);
            mergeOpenDbProviderObject(providerMap, file.optJSONObject("remove"), false);
            return;
        }
        mergeOpenDbProviderObject(providerMap, file.optJSONObject("items"), true);
    }

    private void mergeOpenDbProviderObject(JSONObject providerMap, JSONObject source, boolean add) throws Exception {
        if (source == null) {
            return;
        }

        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String provider = keys.next();
            JSONArray entries = source.optJSONArray(provider);
            if (entries == null) {
                continue;
            }

            JSONArray target = providerMap.optJSONArray(provider);
            if (target == null) {
                target = new JSONArray();
                providerMap.put(provider, target);
            }

            for (int index = 0; index < entries.length(); index++) {
                String normalizedIsrc = TrackSnapshot.normalizeIsrc(entries.optString(index, ""));
                if (normalizedIsrc.isEmpty()) {
                    continue;
                }
                if (add) {
                    if (!jsonArrayContains(target, normalizedIsrc)) {
                        target.put(normalizedIsrc);
                    }
                } else {
                    removeFromJsonArray(target, normalizedIsrc);
                }
            }
        }
    }

    private String resolveOpenDbUrl(String relativeUrl) {
        String value = relativeUrl == null ? "" : relativeUrl.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("/")) {
            return OPENDB_ORIGIN + value;
        }
        return OPENDB_ROOT + "/" + value;
    }

    private void markOpenDbUnavailable() {
        if (openDbPrefs != null) {
            openDbPrefs.edit()
                    .putLong(KEY_OPENDB_UNAVAILABLE_UNTIL_MS, System.currentTimeMillis() + OPENDB_UNAVAILABLE_RETRY_MS)
                    .apply();
        }
    }

    private static boolean jsonArrayContains(JSONArray array, String value) {
        if (array == null || value == null || value.trim().isEmpty()) {
            return false;
        }
        for (int index = 0; index < array.length(); index++) {
            if (value.equals(array.optString(index, ""))) {
                return true;
            }
        }
        return false;
    }

    private static void removeFromJsonArray(JSONArray array, String value) throws Exception {
        if (array == null || value == null || value.trim().isEmpty()) {
            return;
        }
        for (int index = array.length() - 1; index >= 0; index--) {
            if (value.equals(array.optString(index, ""))) {
                array.remove(index);
            }
        }
    }

    private SyncDataResult parseSyncDataResponse(String response, LogSink log, boolean fromCache) throws Exception {
        String prefix = fromCache ? "sync-data cached response" : "sync-data response";
        JSONObject root = new JSONObject(response);
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            log.write(prefix + ": no data");
            return null;
        }
        JSONObject syncData = data.optJSONObject("syncData");
        if (syncData != null) {
            SyncDataResult result = new SyncDataResult(
                    syncBodyWithDurationFallback(syncData, data),
                    data.optString("provider", "lrclib"),
                    parseSyncContributors(data, syncData)
            );
            log.write(prefix + ": provider=" + result.provider
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
            log.write(prefix + ": legacy body / provider=" + result.provider
                    + " / lines=" + result.lineCharCounts().size()
                    + " / lrclibId=" + result.lrclibId()
                    + " / contributors=" + result.contributors.size()
                    + syncDurationSuffix(result.syncBody));
            return result;
        }
        log.write(prefix + ": data without lines");
        return null;
    }

    private String syncDataCacheKey(String isrc) {
        String normalized = TrackSnapshot.normalizeIsrc(isrc);
        return normalized.isEmpty() ? "" : SYNC_DATA_CACHE_SCHEMA + "|isrc:" + normalized + "|provider:" + LRCLIB_PROVIDER_ID;
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

    private SpotifyTrackMatch fetchSpotifyIsrc(TrackSnapshot track, LogSink log, SpotifyMetadataSink metadataSink) {
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
                            publishSpotifyMetadata(metadataSink, direct);
                            direct = attachSpotifyEnglishMetadata(token, direct, log);
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
                                publishSpotifyMetadata(metadataSink, direct);
                                direct = attachSpotifyEnglishMetadata(token, direct, log);
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

            SpotifyTrackMatch selected = selectSpotifyMatchByApiOrder(track, matches, log);
            publishSpotifyMetadata(metadataSink, selected);
            return attachSpotifyEnglishMetadata(token, selected, log);
        } catch (Exception error) {
            log.write("spotify search error: " + error.getMessage());
            return null;
        }
    }

    private void publishSpotifyMetadata(SpotifyMetadataSink metadataSink, SpotifyTrackMatch match) {
        if (metadataSink != null && match != null && !match.isrc.isEmpty()) {
            metadataSink.onResolved(match);
        }
    }

    private SpotifyTrackMatch fetchSpotifyTrackById(String token, String trackId, LogSink log) throws Exception {
        return fetchSpotifyTrackById(
                token,
                trackId,
                "direct metadata",
                Collections.singletonMap("Authorization", "Bearer " + token),
                true,
                log
        );
    }

    private SpotifyTrackMatch fetchSpotifyTrackById(
            String token,
            String trackId,
            String label,
            Map<String, String> headers,
            boolean requireIsrc,
            LogSink log
    ) throws Exception {
        if (trackId == null || trackId.trim().isEmpty()) {
            return null;
        }
        String safeLabel = label == null || label.trim().isEmpty() ? "metadata" : label.trim();
        log.write("spotify track: " + safeLabel + " lookup id=" + trackId);
        String response = get(
                SPOTIFY_TRACK_BASE + urlEncode(trackId.trim()),
                headers == null ? Collections.emptyMap() : headers
        );
        SpotifyTrackMatch match = SpotifyTrackMatch.fromJson(new JSONObject(response), requireIsrc);
        if (match == null) {
            log.write("spotify track: no usable " + safeLabel);
            return null;
        }
        log.write("spotify track: " + safeLabel + " ready " + describeSpotifyMatch(match));
        return match;
    }

    private SpotifyTrackMatch attachSpotifyEnglishMetadata(String token, SpotifyTrackMatch match, LogSink log) {
        if (match == null || match.spotifyId.isEmpty() || token == null || token.trim().isEmpty()) {
            return match;
        }
        try {
            SpotifyTrackMatch english = fetchSpotifyTrackById(
                    token,
                    match.spotifyId,
                    "english metadata",
                    spotifyEnglishHeaders(token),
                    false,
                    log
            );
            if (english == null || english.title.isEmpty() || english.artist.isEmpty()) {
                return match;
            }
            SpotifyTrackMatch merged = match.withEnglishMetadata(english);
            if (merged.hasEnglishMetadata()) {
                log.write("spotify english metadata: title=\"" + merged.englishTitle
                        + "\" / artist=\"" + merged.englishArtist + "\"");
            } else {
                log.write("spotify english metadata: same as selected metadata");
            }
            return merged;
        } catch (Exception error) {
            log.write("spotify english metadata error: " + error.getMessage());
            return match;
        }
    }

    private Map<String, String> spotifyEnglishHeaders(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Accept-Language", SPOTIFY_ENGLISH_ACCEPT_LANGUAGE);
        return headers;
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
        double albumScore = albumScore(track.album, candidate.albumName);
        double durationScore = durationScore(track.durationMs, candidate.durationSeconds);
        double lyricsScore = candidate.useSyncedLyrics()
                ? 0.8
                : (candidate.plainLyrics != null && !candidate.plainLyrics.trim().isEmpty() ? 0.25 : 0.0);
        candidate.albumMatchScore = Math.max(candidate.albumMatchScore, albumScore);
        double score = (titleScore * 4.0)
                + (artistScore * 3.0)
                + (albumScore * 1.25)
                + (durationScore * 2.0)
                + lyricsScore;

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
        candidate.hasOriginalLyricsScript = false;

        if (syncData == null) {
            return;
        }

        List<Integer> syncLineCounts = syncData.lineCharCounts();
        boolean normalizeParentheticalLines = syncData.shouldNormalizeParentheticalLines();
        List<Integer> candidateSyncedLineCounts = candidateLineCharCounts(
                candidate.syncedLyrics, true, normalizeParentheticalLines
        );
        candidate.exactSyncedLineMatch = hasExactLineShape(
                syncLineCounts,
                candidateSyncedLineCounts
        );
        List<Integer> candidatePlainLineCounts = candidateLineCharCounts(
                candidate.plainLyrics, false, normalizeParentheticalLines
        );
        candidate.exactPlainLineMatch = hasExactLineShape(
                syncLineCounts,
                candidatePlainLineCounts
        );
        candidate.syncLineExactMatch = candidate.exactSyncedLineMatch || candidate.exactPlainLineMatch;
        candidate.preferredLyricsSource = candidate.exactSyncedLineMatch
                ? "synced"
                : (candidate.exactPlainLineMatch ? "plain" : syncData.preferredLyricsSource());
        candidate.hasOriginalLyricsScript = hasOriginalLyricsScript(candidateComparableText(
                candidate,
                candidate.preferredLyricsSource,
                normalizeParentheticalLines
        ));

        if (!syncData.hasLrclibSource()) {
            return;
        }

        long sourceId = syncData.lrclibId();
        candidate.syncSourceIdMatch = sourceId > 0L && candidate.id == sourceId;

        List<Integer> sourceLineCounts = syncData.sourceLineCharCounts();
        boolean sourceSyncedLineMatch = hasExactLineShape(
                sourceLineCounts,
                candidateSyncedLineCounts
        );
        boolean sourcePlainLineMatch = hasExactLineShape(
                sourceLineCounts,
                candidatePlainLineCounts
        );
        if (candidate.preferredLyricsSource.isEmpty()) {
            candidate.preferredLyricsSource = sourceSyncedLineMatch
                    ? "synced"
                    : (sourcePlainLineMatch ? "plain" : syncData.preferredLyricsSource());
        }

        String preferredSource = firstNonEmpty(candidate.preferredLyricsSource, syncData.preferredLyricsSource());
        String candidateText = candidateComparableText(candidate, preferredSource, normalizeParentheticalLines);
        String sourceFingerprint = syncData.sourceLyricsFingerprint();
        candidate.syncSourceTextMatch = !sourceFingerprint.isEmpty()
                && sourceFingerprint.equals(lyricsFingerprint(candidateText));

        candidate.syncSourceLineCountMatch = hasExactLineShape(
                sourceLineCounts,
                lineCharCounts(comparableLyricsLines(candidateText, false, false))
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

    private List<Integer> candidateLineCharCounts(
            String text,
            boolean stripTimestamps,
            boolean normalizeParentheticalLines
    ) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        return lineCharCounts(comparableLyricsLines(text, stripTimestamps, normalizeParentheticalLines));
    }

    private List<Integer> lineCharCounts(List<String> lines) {
        List<Integer> counts = new ArrayList<>();
        for (String line : lines) {
            counts.add(codePointLength(line));
        }
        return counts;
    }

    private List<String> comparableLyricsLines(String text, boolean stripTimestamps) {
        return comparableLyricsLines(text, stripTimestamps, false);
    }

    private List<String> comparableLyricsLines(
            String text,
            boolean stripTimestamps,
            boolean normalizeParentheticalLines
    ) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        String[] rawLines = text.split("\\r?\\n");
        for (String rawLine : rawLines) {
            String line = stripTimestamps ? stripLeadingLrcTimestamp(rawLine) : rawLine.trim();
            line = Normalizer.normalize(line, Normalizer.Form.NFC).trim();
            if (line.isEmpty() || LRCLIB_METADATA_LINE_PATTERN.matcher(line).matches()) {
                continue;
            }
            lines.add(line);
        }
        if (normalizeParentheticalLines) {
            List<String> normalizedLines = normalizeStandaloneParentheticalBlocks(lines);
            List<String> filtered = new ArrayList<>();
            for (String line : normalizedLines) {
                String normalized = Normalizer.normalize(line == null ? "" : line, Normalizer.Form.NFC).trim();
                if (!normalized.isEmpty()) {
                    filtered.add(normalized);
                }
            }
            return filtered;
        }
        return lines;
    }

    private String candidateComparableText(
            LrclibCandidate candidate,
            String preferredSource,
            boolean normalizeParentheticalLines
    ) {
        if (candidate == null) {
            return "";
        }
        boolean useSynced = "synced".equals(preferredSource)
                ? candidate.syncedLyrics != null
                : (!"plain".equals(preferredSource) && candidate.plainLyrics == null && candidate.syncedLyrics != null);
        String text = useSynced
                ? stripLrcTimestamps(candidate.syncedLyrics)
                : firstNonEmpty(candidate.plainLyrics, stripLrcTimestamps(candidate.syncedLyrics));
        List<String> lines = comparableLyricsLines(text, false, normalizeParentheticalLines);
        return joinLinesForFingerprint(lines);
    }

    private List<String> normalizeStandaloneParentheticalBlocks(List<String> lines) {
        List<String> normalizedLines = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                normalizedLines.add(stripStandaloneParentheticalLine(line));
            }
        }

        for (int index = 0; index < normalizedLines.size(); index++) {
            String trimmed = Normalizer.normalize(
                    normalizedLines.get(index) == null ? "" : normalizedLines.get(index),
                    Normalizer.Form.NFC
            ).trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int openCodePoint = firstCodePoint(trimmed);
            String closeChar = getParenthesisClose(openCodePoint);
            if (closeChar.isEmpty() || trimmed.contains(closeChar)) {
                continue;
            }

            int closeLineIndex = -1;
            for (int candidate = index + 1; candidate < normalizedLines.size(); candidate++) {
                String candidateTrimmed = Normalizer.normalize(
                        normalizedLines.get(candidate) == null ? "" : normalizedLines.get(candidate),
                        Normalizer.Form.NFC
                ).trim();
                if (!candidateTrimmed.isEmpty() && candidateTrimmed.endsWith(closeChar)) {
                    closeLineIndex = candidate;
                    break;
                }
            }
            if (closeLineIndex < 0) {
                continue;
            }

            normalizedLines.set(index, stripLeadingParenthesis(normalizedLines.get(index)).trim());
            normalizedLines.set(closeLineIndex, stripTrailingParenthesis(
                    normalizedLines.get(closeLineIndex),
                    closeChar
            ).trim());
        }
        return normalizedLines;
    }

    private String stripStandaloneParentheticalLine(String text) {
        String value = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).trim();
        while (isStandaloneParentheticalLine(value)) {
            List<Integer> codePoints = codePoints(value);
            value = codePointsToString(codePoints, 1, codePoints.size() - 2).trim();
        }
        return value;
    }

    private boolean isStandaloneParentheticalLine(String text) {
        String value = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).trim();
        List<Integer> codePoints = codePoints(value);
        if (codePoints.size() < 2) {
            return false;
        }

        String expectedClose = getParenthesisClose(codePoints.get(0));
        return !expectedClose.isEmpty()
                && expectedClose.equals(codePointsToString(codePoints, codePoints.size() - 1, codePoints.size() - 1));
    }

    private String stripLeadingParenthesis(String text) {
        List<Integer> codePoints = codePoints(text);
        for (int index = 0; index < codePoints.size(); index++) {
            int codePoint = codePoints.get(index);
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (!getParenthesisClose(codePoint).isEmpty()) {
                codePoints.remove(index);
            }
            break;
        }
        return codePointsToString(codePoints, 0, codePoints.size() - 1);
    }

    private String stripTrailingParenthesis(String text, String closeChar) {
        List<Integer> codePoints = codePoints(text);
        for (int index = codePoints.size() - 1; index >= 0; index--) {
            int codePoint = codePoints.get(index);
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (closeChar.equals(codePointsToString(codePoints, index, index))) {
                codePoints.remove(index);
            }
            break;
        }
        return codePointsToString(codePoints, 0, codePoints.size() - 1);
    }

    private String getParenthesisClose(int openCodePoint) {
        if (openCodePoint == '(') return ")";
        if (openCodePoint == '（') return "）";
        return "";
    }

    private int firstCodePoint(String value) {
        List<Integer> codePoints = codePoints(value);
        return codePoints.isEmpty() ? -1 : codePoints.get(0);
    }

    private List<Integer> codePoints(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
        List<Integer> codePoints = new ArrayList<>();
        normalized.codePoints().forEach(codePoints::add);
        return codePoints;
    }

    private String codePointsToString(List<Integer> codePoints, int start, int end) {
        if (codePoints == null || codePoints.isEmpty() || start > end) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(codePoints.size() - 1, end);
        for (int index = safeStart; index <= safeEnd; index++) {
            builder.appendCodePoint(codePoints.get(index));
        }
        return builder.toString();
    }

    private boolean hasOriginalLyricsScript(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        return normalized.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x3040 && codePoint <= 0x30ff)
                        || (codePoint >= 0x3400 && codePoint <= 0x4dbf)
                        || (codePoint >= 0x4e00 && codePoint <= 0x9fff)
                        || (codePoint >= 0xf900 && codePoint <= 0xfaff)
                        || (codePoint >= 0x1100 && codePoint <= 0x11ff)
                        || (codePoint >= 0x3130 && codePoint <= 0x318f)
                        || (codePoint >= 0xac00 && codePoint <= 0xd7af)
        );
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

    private void emitManualLog(String trackKey, ManualLrclibCallback callback, String message) {
        String safeMessage = message == null ? "" : message;
        Log.d(TAG, safeMessage);
        if (callback != null) {
            mainHandler.post(() -> callback.onManualLrclibLog(trackKey, safeMessage));
        }
    }

    private void postManualError(String trackKey, ManualLrclibCallback callback, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onManualLrclibError(trackKey, message == null ? "" : message));
        }
    }

    private String manualTrackKey(TrackSnapshot track) {
        return track == null || !track.hasUsableMetadata() ? "" : track.stableKey();
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

    private double albumScore(String expected, String candidate) {
        String left = normalizeComparable(expected);
        String right = normalizeComparable(candidate);
        if (left.isEmpty() || right.isEmpty() || "null".equals(right)) return 0.0;
        if (left.equals(right)) return 1.0;
        if (isAlbumExpansion(left, right) || isAlbumExpansion(right, left)) return 0.72;

        double similarity = jaroWinkler(left, right);
        if (similarity >= 0.92) return 0.55;
        if (similarity >= 0.84) return 0.25;
        return 0.0;
    }

    private boolean isAlbumExpansion(String base, String expanded) {
        if (base.isEmpty() || expanded.isEmpty() || !expanded.startsWith(base) || expanded.length() == base.length()) {
            return false;
        }
        char next = expanded.charAt(base.length());
        return Character.isWhitespace(next) || next == '-' || next == ':' || next == '(' || next == '[';
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
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
        normalized = COMPARABLE_APOSTROPHE_PATTERN.matcher(normalized).replaceAll("'");
        normalized = COMPARABLE_QUOTE_PATTERN.matcher(normalized).replaceAll("\"");
        normalized = COMPARABLE_BRACKET_PATTERN.matcher(normalized).replaceAll("");
        return COMPARABLE_WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ");
    }

    private static boolean sameSearchMetadata(String left, String right) {
        String normalizedLeft = normalizeComparable(left);
        String normalizedRight = normalizeComparable(right);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
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

    private static final class ProviderCandidate {
        final LyricsResult karaoke;
        final LyricsResult synced;
        final LyricsResult plain;

        ProviderCandidate(LyricsResult karaoke, LyricsResult synced, LyricsResult plain) {
            this.karaoke = karaoke;
            this.synced = synced;
            this.plain = plain;
        }
    }

    private static final class MemoryLyricsCacheEntry {
        final LyricsResult result;
        final long savedAtMs;

        MemoryLyricsCacheEntry(LyricsResult result, long savedAtMs) {
            this.result = result;
            this.savedAtMs = savedAtMs;
        }
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
        boolean hasOriginalLyricsScript;
        double albumMatchScore;

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

        boolean shouldNormalizeParentheticalLines() {
            return syncBody != null
                    && (syncBody.optInt("version", 1) >= 2 || !sourceLineCharCounts().isEmpty());
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
        final String englishTitle;
        final String englishArtist;
        final String englishAlbum;

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
            this(
                    spotifyId,
                    title,
                    artist,
                    album,
                    durationMs,
                    isrc,
                    artworkUrl,
                    artworkWidth,
                    artworkHeight,
                    "",
                    "",
                    ""
            );
        }

        SpotifyTrackMatch(
                String spotifyId,
                String title,
                String artist,
                String album,
                long durationMs,
                String isrc,
                String artworkUrl,
                int artworkWidth,
                int artworkHeight,
                String englishTitle,
                String englishArtist,
                String englishAlbum
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
            this.englishTitle = englishTitle == null ? "" : englishTitle.trim();
            this.englishArtist = englishArtist == null ? "" : englishArtist.trim();
            this.englishAlbum = englishAlbum == null ? "" : englishAlbum.trim();
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

        SpotifyTrackMatch withEnglishMetadata(SpotifyTrackMatch english) {
            if (english == null || english.title.trim().isEmpty() || english.artist.trim().isEmpty()) {
                return this;
            }
            String nextTitle = sameSearchMetadata(title, english.title) ? "" : english.title;
            String nextArtist = sameSearchMetadata(artist, english.artist) ? "" : english.artist;
            String nextAlbum = sameSearchMetadata(album, english.album) ? "" : english.album;
            if (nextTitle.isEmpty() && nextArtist.isEmpty()) {
                return this;
            }
            return new SpotifyTrackMatch(
                    spotifyId,
                    title,
                    artist,
                    album,
                    durationMs,
                    isrc,
                    artworkUrl,
                    artworkWidth,
                    artworkHeight,
                    firstNonEmpty(nextTitle, title),
                    firstNonEmpty(nextArtist, artist),
                    nextAlbum
            );
        }

        boolean hasEnglishMetadata() {
            return (!englishTitle.isEmpty() && !sameSearchMetadata(title, englishTitle))
                    || (!englishArtist.isEmpty() && !sameSearchMetadata(artist, englishArtist));
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
