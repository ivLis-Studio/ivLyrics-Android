package kr.ivlis.ivlyricsandroid;

import android.graphics.Bitmap;
import android.os.SystemClock;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class TrackSnapshot {
    private static final String SPOTIFY_TRACK_URI_PREFIX = "spotify:track:";
    private static final String SPOTIFY_TRACK_URL_PREFIX = "open.spotify.com/track/";
    private static final int SPOTIFY_TRACK_ID_LENGTH = 22;
    private static final Pattern ISRC_SEPARATOR_PATTERN = Pattern.compile("[\\s-]");
    private static final Pattern VALID_ISRC_PATTERN = Pattern.compile("[A-Z]{2}[A-Z0-9]{3}\\d{7}");

    final String title;
    final String artist;
    final String album;
    final String packageName;
    final String mediaId;
    final String trackId;
    final String isrc;
    final long durationMs;
    final long positionMs;
    final long lastPositionUpdateElapsedMs;
    final float playbackSpeed;
    final boolean playing;
    final Bitmap artwork;
    final String artworkUri;

    TrackSnapshot(
            String title,
            String artist,
            String album,
            String packageName,
            String mediaId,
            String isrc,
            long durationMs,
            long positionMs,
            long lastPositionUpdateElapsedMs,
            float playbackSpeed,
            boolean playing,
            Bitmap artwork,
            String artworkUri
    ) {
        this.title = clean(title);
        this.artist = clean(artist);
        this.album = clean(album);
        this.packageName = clean(packageName);
        this.mediaId = clean(mediaId);
        this.trackId = extractSpotifyTrackId(this.mediaId);
        this.isrc = normalizeIsrc(isrc);
        this.durationMs = Math.max(0L, durationMs);
        this.positionMs = Math.max(0L, positionMs);
        this.lastPositionUpdateElapsedMs = lastPositionUpdateElapsedMs > 0
                ? lastPositionUpdateElapsedMs
                : SystemClock.elapsedRealtime();
        this.playbackSpeed = playbackSpeed > 0f ? playbackSpeed : 1f;
        this.playing = playing;
        this.artwork = artwork;
        this.artworkUri = clean(artworkUri);
    }

    long positionNow() {
        if (!playing) {
            return clampPosition(positionMs);
        }
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - lastPositionUpdateElapsedMs);
        return clampPosition(positionMs + Math.round(elapsed * playbackSpeed));
    }

    boolean hasUsableMetadata() {
        return !title.isEmpty() && !artist.isEmpty();
    }

    boolean isSpotifyDjSegment() {
        String normalizedArtist = normalizeForKey(artist);
        if (!normalizedArtist.equals("dj x")) {
            return false;
        }
        String normalizedTitle = normalizeForKey(title);
        return normalizedTitle.equals("welcome") || normalizedTitle.equals("up next");
    }

    String stableKey() {
        if (!trackId.isEmpty()) {
            return "spotify:" + trackId;
        }
        return normalizeForKey(title) + "|" + normalizeForKey(artist) + "|" + durationMs;
    }

    String artworkKey() {
        String source = artworkUri.isEmpty() ? stableKey() : artworkUri;
        if (artwork == null) {
            return source + "|missing";
        }
        return source
                + "|bitmap:"
                + artwork.getWidth()
                + "x"
                + artwork.getHeight();
    }

    private long clampPosition(long value) {
        if (durationMs > 0) {
            return Math.max(0L, Math.min(durationMs, value));
        }
        return Math.max(0L, value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeForKey(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static String normalizeIsrc(String value) {
        if (value == null) return "";
        String normalized = ISRC_SEPARATOR_PATTERN.matcher(value.trim()).replaceAll("").toUpperCase(Locale.ROOT);
        return VALID_ISRC_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private static String extractSpotifyTrackId(String value) {
        if (value == null) return "";
        int spotifyMatchStart = value.indexOf(SPOTIFY_TRACK_URI_PREFIX);
        int openMatchStart = value.indexOf(SPOTIFY_TRACK_URL_PREFIX);
        while (spotifyMatchStart >= 0 || openMatchStart >= 0) {
            boolean useSpotifyPrefix = openMatchStart < 0
                    || (spotifyMatchStart >= 0 && spotifyMatchStart < openMatchStart);
            int matchStart = useSpotifyPrefix ? spotifyMatchStart : openMatchStart;
            String prefix = useSpotifyPrefix ? SPOTIFY_TRACK_URI_PREFIX : SPOTIFY_TRACK_URL_PREFIX;
            int idStart = matchStart + prefix.length();
            if (hasAsciiSpotifyTrackId(value, idStart)) {
                return value.substring(idStart, idStart + SPOTIFY_TRACK_ID_LENGTH);
            }
            if (useSpotifyPrefix) {
                spotifyMatchStart = value.indexOf(SPOTIFY_TRACK_URI_PREFIX, matchStart + 1);
            } else {
                openMatchStart = value.indexOf(SPOTIFY_TRACK_URL_PREFIX, matchStart + 1);
            }
        }
        return "";
    }

    private static boolean hasAsciiSpotifyTrackId(String value, int start) {
        if (start < 0 || value.length() - start < SPOTIFY_TRACK_ID_LENGTH) {
            return false;
        }
        int end = start + SPOTIFY_TRACK_ID_LENGTH;
        for (int index = start; index < end; index++) {
            char character = value.charAt(index);
            if ((character < '0' || character > '9')
                    && (character < 'A' || character > 'Z')
                    && (character < 'a' || character > 'z')) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TrackSnapshot)) return false;
        TrackSnapshot that = (TrackSnapshot) other;
        return durationMs == that.durationMs
                && playing == that.playing
                && Objects.equals(title, that.title)
                && Objects.equals(artist, that.artist)
                && Objects.equals(album, that.album)
                && Objects.equals(packageName, that.packageName)
                && Objects.equals(mediaId, that.mediaId)
                && Objects.equals(artworkUri, that.artworkUri)
                && (artwork != null) == (that.artwork != null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, artist, album, packageName, mediaId, durationMs, playing, artworkUri, artwork != null);
    }
}
