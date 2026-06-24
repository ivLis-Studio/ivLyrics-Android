package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class YouTubeBackgroundRepository {
    private static final String PREFS_NAME = "youtube_background_cache";
    private static final String YOUTUBE_ENDPOINT = "https://ivlis.kr/ivLyrics/openvideo/youtube";
    private static final String SPOTIFY_ORIGIN = "https://xpui.app.spotify.com";
    private static final String SPOTIFY_REFERER = "https://xpui.app.spotify.com/";

    private final Context appContext;
    private final SharedPreferences cachePrefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String clientVersion;

    YouTubeBackgroundRepository(Context context) {
        appContext = context.getApplicationContext();
        cachePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        clientVersion = resolveClientVersion(appContext);
    }

    void load(
            String requestKey,
            TrackSnapshot track,
            LyricsResult lyricsResult,
            Callback callback
    ) {
        String safeRequestKey = requestKey == null ? "" : requestKey;
        if (track == null || lyricsResult == null) {
            notifyError(callback, safeRequestKey, "youtube background: missing track metadata");
            return;
        }
        String isrc = firstNonEmpty(lyricsResult.isrc, track.isrc);
        if (isrc.isEmpty()) {
            notifyError(callback, safeRequestKey, "youtube background: missing ISRC");
            return;
        }
        String spotifyTrackId = firstNonEmpty(lyricsResult.spotifyTrackId, track.trackId);
        executor.execute(() -> {
            try {
                VideoInfo cached = readCache(isrc);
                if (cached != null) {
                    notifyLoaded(callback, safeRequestKey, cached, true);
                    return;
                }

                Uri.Builder builder = Uri.parse(YOUTUBE_ENDPOINT).buildUpon()
                        .appendQueryParameter("isrc", isrc)
                        .appendQueryParameter("useCommunity", "true")
                        .appendQueryParameter("client", "ivLyrics")
                        .appendQueryParameter("clientVersion", clientVersion)
                        .appendQueryParameter("requestVersion", "2");
                if (!spotifyTrackId.isEmpty()) {
                    builder.appendQueryParameter("trackId", spotifyTrackId);
                }
                if (!track.title.isEmpty()) {
                    builder.appendQueryParameter("trackName", track.title);
                }
                if (!track.artist.isEmpty()) {
                    builder.appendQueryParameter("trackArtists", track.artist);
                }
                if (!track.album.isEmpty()) {
                    builder.appendQueryParameter("album", track.album);
                }

                String requestUrl = builder.build().toString();
                notifyLog(callback, safeRequestKey, "youtube background request: isrc=" + isrc
                        + (spotifyTrackId.isEmpty() ? "" : " / trackId=" + spotifyTrackId));
                JSONObject response = new JSONObject(get(requestUrl));
                if (!response.optBoolean("success", false)) {
                    notifyError(callback, safeRequestKey, "youtube background: video not found");
                    return;
                }
                JSONObject data = response.optJSONObject("data");
                VideoInfo info = VideoInfo.fromJson(isrc, data);
                if (info == null || info.youtubeVideoId.isEmpty()) {
                    notifyError(callback, safeRequestKey, "youtube background: invalid response");
                    return;
                }
                writeCache(info);
                notifyLoaded(callback, safeRequestKey, info, false);
            } catch (Exception error) {
                notifyError(callback, safeRequestKey, "youtube background failed: " + error.getMessage());
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }

    void clearCache() {
        cachePrefs.edit().clear().apply();
    }

    void clearCacheForIsrc(String isrc) {
        String key = TrackSnapshot.normalizeIsrc(isrc);
        if (!key.isEmpty()) {
            cachePrefs.edit().remove(key).apply();
        }
    }

    private VideoInfo readCache(String isrc) {
        String key = TrackSnapshot.normalizeIsrc(isrc);
        if (key.isEmpty()) {
            return null;
        }
        String raw = cachePrefs.getString(key, "");
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(raw);
            return VideoInfo.fromJson(key, object);
        } catch (Exception ignored) {
            cachePrefs.edit().remove(key).apply();
            return null;
        }
    }

    private void writeCache(VideoInfo info) {
        if (info == null || info.isrc.isEmpty() || info.youtubeVideoId.isEmpty()) {
            return;
        }
        try {
            JSONObject object = info.toJson();
            object.put("cachedAt", System.currentTimeMillis());
            cachePrefs.edit().putString(info.isrc, object.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(16_000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Origin", SPOTIFY_ORIGIN);
        connection.setRequestProperty("Referer", SPOTIFY_REFERER);
        connection.setRequestProperty("X-ivLyrics-Client", "ivLyrics");
        connection.setRequestProperty("X-ivLyrics-Request-Version", "2");
        connection.setRequestProperty("X-ivLyrics-Client-Version", clientVersion);
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + (body.isEmpty() ? "" : ": " + body));
        }
        return body;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String firstNonEmpty(String first, String second) {
        String safeFirst = first == null ? "" : first.trim();
        if (!safeFirst.isEmpty()) {
            return safeFirst;
        }
        return second == null ? "" : second.trim();
    }

    @SuppressWarnings("deprecation")
    private static String resolveClientVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info == null || info.versionName == null || info.versionName.trim().isEmpty()
                    ? "unknown"
                    : info.versionName.trim();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private void notifyLoaded(Callback callback, String requestKey, VideoInfo info, boolean fromCache) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onYouTubeBackgroundLoaded(requestKey, info, fromCache));
    }

    private void notifyError(Callback callback, String requestKey, String message) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onYouTubeBackgroundError(requestKey, message));
    }

    private void notifyLog(Callback callback, String requestKey, String message) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onYouTubeBackgroundLog(requestKey, message));
    }

    interface Callback {
        void onYouTubeBackgroundLoaded(String requestKey, VideoInfo info, boolean fromCache);

        void onYouTubeBackgroundError(String requestKey, String message);

        void onYouTubeBackgroundLog(String requestKey, String message);
    }

    static final class VideoInfo {
        final String isrc;
        final String spotifyTrackId;
        final String youtubeVideoId;
        final String youtubeTitle;
        final boolean hasCaptionStartTime;
        final double captionStartTimeSeconds;
        final boolean autoGenerated;
        final String submitterId;

        private VideoInfo(
                String isrc,
                String spotifyTrackId,
                String youtubeVideoId,
                String youtubeTitle,
                boolean hasCaptionStartTime,
                double captionStartTimeSeconds,
                boolean autoGenerated,
                String submitterId
        ) {
            this.isrc = TrackSnapshot.normalizeIsrc(isrc);
            this.spotifyTrackId = spotifyTrackId == null ? "" : spotifyTrackId.trim();
            this.youtubeVideoId = validYouTubeId(youtubeVideoId) ? youtubeVideoId.trim() : "";
            this.youtubeTitle = youtubeTitle == null ? "" : youtubeTitle.trim();
            this.hasCaptionStartTime = hasCaptionStartTime;
            this.captionStartTimeSeconds = hasCaptionStartTime ? Math.max(0d, captionStartTimeSeconds) : 0d;
            this.autoGenerated = autoGenerated;
            this.submitterId = submitterId == null ? "" : submitterId.trim();
        }

        static VideoInfo fromJson(String fallbackIsrc, JSONObject object) {
            if (object == null) {
                return null;
            }
            boolean hasCaption = object.has("captionStartTime") && !object.isNull("captionStartTime");
            return new VideoInfo(
                    firstNonEmpty(object.optString("isrc", ""), fallbackIsrc),
                    firstNonEmpty(object.optString("spotifyTrackId", ""), object.optString("trackId", "")),
                    firstNonEmpty(object.optString("youtubeVideoId", ""), object.optString("videoId", "")),
                    firstNonEmpty(object.optString("youtubeTitle", ""), object.optString("title", "")),
                    hasCaption,
                    hasCaption ? object.optDouble("captionStartTime", 0d) : 0d,
                    object.optBoolean("isAutoGenerated", false),
                    object.optString("submitterId", "")
            );
        }

        JSONObject toJson() throws Exception {
            JSONObject object = new JSONObject();
            object.put("isrc", isrc);
            object.put("spotifyTrackId", spotifyTrackId);
            object.put("youtubeVideoId", youtubeVideoId);
            object.put("youtubeTitle", youtubeTitle);
            if (hasCaptionStartTime) {
                object.put("captionStartTime", captionStartTimeSeconds);
            }
            object.put("isAutoGenerated", autoGenerated);
            object.put("submitterId", submitterId);
            return object;
        }

        boolean isAutoMatchedUnknownCaptionStart() {
            return autoGenerated && hasCaptionStartTime && Math.abs(captionStartTimeSeconds) < 0.001d;
        }

        private static boolean validYouTubeId(String value) {
            return value != null && value.trim().matches("[A-Za-z0-9_-]{11}");
        }
    }
}
