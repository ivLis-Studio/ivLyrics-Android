package kr.ivlis.ivlyricsandroid;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Run on JVM with compiled app models, android.jar and org.json; no account or device access. */
public final class NativeMetadataRegression {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        byte[] gid = HexFormat.of().parseHex("65812b432675448b8e9762cfbf92cb21");
        check(NativeTrackIdentity.base62(new byte[16]).equals("0000000000000000000000"), "GID leading zeroes");
        String uri = "spotify:track:35xfPPSPHG42a8MSoHBymZ";
        check(NativeTrackIdentity.base62(gid).equals("35xfPPSPHG42a8MSoHBymZ"), "known Spotify GID/base62 conversion");
        Proto proto = new Proto(gid, List.of(new Ext("JPP302400443", "isrc")));
        NativeTrackIdentity parsed = NativeTrackIdentity.fromProto(proto);
        check(parsed != null && parsed.uri.equals(uri) && parsed.isrc.equals("JPP302400443"), "protobuf GID and external id");
        check(NativeTrackIdentity.fromProto(new Proto(gid, List.of(new Ext("JPP302400443", "upc")))) == null, "wrong external id type rejected");
        check(NativeTrackIdentity.fromProto(new Proto(new byte[15], List.of(new Ext("JPP302400443", "isrc")))) == null, "malformed GID rejected");
        V4 nativeTrack = new V4("35xfPPSPHG42a8MSoHBymZ", List.of(new Id("jp-p30-24-00443", "ISRC")));
        parsed = NativeTrackIdentity.fromTrackV4(nativeTrack);
        check(parsed.uri.equals("spotify:track:35xfPPSPHG42a8MSoHBymZ") && parsed.isrc.equals("JPP302400443"), "TrackV4 URI and normalized ISRC");
        check(NativeTrackIdentity.canonicalUri("spotify:episode:35xfPPSPHG42a8MSoHBymZ").isEmpty(), "episode not queried as song");
        check(NativeTrackIdentity.normalizedIsrc("JPP302400443 unexpected").isEmpty(), "untrusted external id rejected");
        LyricsProviderSettings.Snapshot settings = new LyricsProviderSettings(null).snapshot();
        LyricsResult cached = new LyricsResult(List.of(new LyricsLine(0, 1000, "test", List.of())), "Paxsenix", "", true)
            .withSelection("paxsenix", settings.cacheKeyForProvider("paxsenix"));
        check(!LyricsProviderSelectionPlan.shouldRecheckCachedResult(cached, settings, ""), "no repeated lookup before ISRC");
        check(LyricsProviderSelectionPlan.shouldRecheckCachedResult(cached, settings, "JPP302400443"), "late ISRC rechecks cached karaoke");
        check(!LyricsProviderSelectionPlan.shouldRecheckCachedResult(cached.withMetadata("JPP302400443", "35xfPPSPHG42a8MSoHBymZ"), settings, "JPP302400443"), "checked identity does not repeatedly recheck");
        check(!LyricsProviderSelectionPlan.shouldRecheckCachedResult(cached.withSelection("paxsenix", "manual"), settings, "JPP302400443"), "manual selection preserved");
        // An asynchronous lookup for A must not revert a newer committed song B.
        java.lang.reflect.Field latest = NowPlayingService.class.getDeclaredField("latestSnapshot");
        latest.setAccessible(true);
        TrackSnapshot a = snapshot("spotify:track:35xfPPSPHG42a8MSoHBymZ", 1000);
        TrackSnapshot b = snapshot("spotify:track:5NxmDq0yXBYGfCbMqvIXuv", 2000);
        latest.set(null, a);
        android.os.Looper.testMainThread(false);
        NowPlayingService.enrichEmbeddedIsrc(a.mediaId, "JPP302400443");
        latest.set(null, b);
        android.os.Handler.drain();
        check(NowPlayingService.getLatestSnapshot() == b, "late enrichment cannot overwrite different current URI");
        latest.set(null, a);
        android.os.Looper.testMainThread(false);
        NowPlayingService.enrichEmbeddedIsrc(a.mediaId, "JPP302400443");
        TrackSnapshot newerA = snapshot(a.mediaId, 9000);
        latest.set(null, newerA);
        android.os.Handler.drain();
        check(NowPlayingService.getLatestSnapshot().positionMs == 9000, "enrichment preserves latest playback position");
        check(NowPlayingService.getLatestSnapshot().lastPositionUpdateElapsedMs == newerA.lastPositionUpdateElapsedMs, "enrichment preserves playback clock");
        check(NowPlayingService.getLatestSnapshot().isrc.equals("JPP302400443"), "matching identity receives ISRC");
        TrackSnapshot queuedA = snapshot(a.mediaId, 10000);
        NowPlayingService.publishEmbeddedSnapshot(queuedA);
        android.os.Handler.drain();
        check(NowPlayingService.getLatestSnapshot().isrc.equals("JPP302400443"), "queued same-ID snapshot preserves resolved ISRC");
        check(NowPlayingService.getLatestSnapshot().positionMs == queuedA.positionMs
            && NowPlayingService.getLatestSnapshot().lastPositionUpdateElapsedMs == queuedA.lastPositionUpdateElapsedMs,
            "queued same-ID snapshot keeps incoming position and clock");
        NowPlayingService.publishEmbeddedSnapshot(b);
        android.os.Handler.drain();
        check(NowPlayingService.getLatestSnapshot() == b && b.isrc.isEmpty(), "different ID does not inherit ISRC despite same title/artist/album");
        latest.set(null, a.withIsrc("JPP302400443"));
        TrackSnapshot unknown = snapshot("", 11000);
        NowPlayingService.publishEmbeddedSnapshot(unknown);
        android.os.Handler.drain();
        check(NowPlayingService.getLatestSnapshot() == unknown && unknown.isrc.isEmpty(), "missing ID never inherits ISRC by matching title/artist/album");
        if (args.length == 2) {
            JSONObject response = new JSONObject(Files.readString(Path.of(args[0]))).getJSONObject("data");
            JSONObject base = new JSONObject(Files.readString(Path.of(args[1])));
            List<LyricsLine> lines = new ArrayList<>();
            for (String line : base.getString("plainLyrics").split("\n")) lines.add(new LyricsLine(0, 0, line, List.of()));
            TrackSnapshot track = new TrackSnapshot("UNDEAD", "YOASOBI", "THE BOOK for,", "com.spotify.music", "spotify:track:35xfPPSPHG42a8MSoHBymZ", "JPP302400443", 182702, 0, 1, 1f, false, null, "");
            SyncDataApplier.ApplyResult result = SyncDataApplier.applyWithDiagnostics(lines, response.getJSONObject("syncData"), track, "lrclib", base.getLong("id"));
            check(result.lines.size() == 104, "UNDEAD exact source timing application: " + result.diagnostics);
            long syllables = result.lines.stream().mapToLong(line -> line.syllables.size()).sum();
            check(syllables > 800, "UNDEAD timed syllables populated");
            System.out.println("PUBLIC UNDEAD APPLY PASS: 104 lines / " + syllables + " timed syllables / " + response.getInt("syncPoints") + " sync points");
        }
        System.out.println("NATIVE IDENTITY + LATE ISRC CACHE REGRESSIONS PASS");
    }
    static TrackSnapshot snapshot(String uri, long position) {
        return new TrackSnapshot("UNDEAD", "YOASOBI", "THE BOOK for,", "com.spotify.music", uri,
                "", 182702, position, position + 1, 1f, false, null, "");
    }
    public static final class Bytes { final byte[] data; Bytes(byte[] value) { data = value; } public byte[] s() { return data; } }
    static final class Proto { private final Bytes gid_; private final List<Ext> externalId_; Proto(byte[] gid, List<Ext> ext) { gid_ = new Bytes(gid); externalId_ = ext; } }
    static final class Ext { private final String id_, type_; Ext(String id, String type) { id_ = id; type_ = type; } }
    static final class V4 { public final String a; public final List<Id> j; V4(String uri, List<Id> ids) { a = uri; j = ids; } }
    static final class Id { public final String a, b; Id(String id, String type) { a = id; b = type; } }
}
