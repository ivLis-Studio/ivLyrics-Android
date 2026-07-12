package kr.ivlis.ivlyricsandroid;

import android.graphics.Bitmap;
import android.os.SystemClock;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TrackSnapshot {
    private static final Pattern SPOTIFY_TRACK_PATTERN =
            Pattern.compile("(?:spotify:track:|open\\.spotify\\.com/track/)([A-Za-z0-9]{22})");
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
        return sameMetadata(artist, "DJ X")
                && (sameMetadata(title, "Welcome") || sameMetadata(title, "Up Next"));
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

    private static boolean sameMetadata(String first, String second) {
        return normalizeForKey(first).equals(normalizeForKey(second));
    }

    static String normalizeIsrc(String value) {
        if (value == null) return "";
        String normalized = ISRC_SEPARATOR_PATTERN.matcher(value.trim()).replaceAll("").toUpperCase(Locale.ROOT);
        return VALID_ISRC_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private static String extractSpotifyTrackId(String value) {
        if (value == null) return "";
        Matcher matcher = SPOTIFY_TRACK_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
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
