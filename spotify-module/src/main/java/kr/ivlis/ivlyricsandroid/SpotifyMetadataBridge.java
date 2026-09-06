package kr.ivlis.ivlyricsandroid;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Observes already-decoded native track metadata; never requests or reads credentials. */
public final class SpotifyMetadataBridge {
    private static final String TAG = "ivLyricsMetadata";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> ISRC = new LinkedHashMap<String, String>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> entry) { return size() > 256; }
    };
    private static final Map<MediaController, MediaController.Callback> CONTROLLERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean observingPlayer;

    private SpotifyMetadataBridge() {}

    public static void onMetadataClient(Object client) {
        SpotifyNativeMetadataClient.attach(client);
        observePlayer();
    }

    private static void observePlayer() {
        MAIN.post(() -> {
            if (observingPlayer) return;
            observingPlayer = true;
            NowPlayingService.register(snapshot -> mergeCurrent());
        });
    }

    public static void onMediaSession(MediaSession session) {
        if (session == null) return;
        observePlayer();
        MediaController controller = session.getController();
        // Source bridge attachment is queued on MAIN. Register after it, and let its snapshot publication settle.
        MAIN.post(() -> {
            synchronized (CONTROLLERS) {
                if (CONTROLLERS.containsKey(controller)) return;
                MediaController.Callback callback = new MediaController.Callback() {
                    @Override public void onMetadataChanged(MediaMetadata metadata) { MAIN.post(() -> MAIN.post(SpotifyMetadataBridge::mergeCurrent)); }
                    @Override public void onSessionDestroyed() { CONTROLLERS.remove(controller); }
                };
                CONTROLLERS.put(controller, callback);
                controller.registerCallback(callback, MAIN);
            }
            MAIN.post(() -> MAIN.post(SpotifyMetadataBridge::mergeCurrent));
        });
    }

    public static void onTrackMetadata(Object track) {
        if (track == null) return;
        try {
            cache(NativeTrackIdentity.fromProto(track), "");
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "Public track identifier parse failed: " + error.getClass().getSimpleName());
        }
    }

    /** Nested protobuf messages are decoded without invoking Metadata.Track's static parser. */
    public static void onMetadataBatch(Object response) {
        try {
            for (Object item : (Iterable<?>) NativeTrackIdentity.field(response, "items_")) {
                if (((Integer) NativeTrackIdentity.field(item, "itemCase_")) == 4) {
                    onTrackMetadata(NativeTrackIdentity.field(item, "item_"));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "Public metadata batch parse failed: " + error.getClass().getSimpleName());
        }
    }

    static boolean onRequestedTrackV4(String requestUri, Object track) throws ReflectiveOperationException {
        NativeTrackIdentity identity = NativeTrackIdentity.fromTrackV4(track);
        if (identity == null) return false;
        // The native response envelope was matched to requestUri before accepting a relinked GID.
        cache(identity, requestUri);
        return true;
    }

    private static void cache(NativeTrackIdentity identity, String requestedUri) {
        if (identity == null) return;
        synchronized (ISRC) {
            ISRC.put(identity.uri, identity.isrc);
            if (!NativeTrackIdentity.canonicalUri(requestedUri).isEmpty()) ISRC.put(requestedUri, identity.isrc);
        }
        Log.i(TAG, "Native public ISRC cached for a track");
        MAIN.post(SpotifyMetadataBridge::mergeCurrent);
    }

    public static void onContextTrack(Object track) {
        if (track == null) return;
        try {
            String uri = NativeTrackIdentity.canonicalUri((String) track.getClass().getMethod("uri").invoke(track));
            if (uri.isEmpty()) return;
            @SuppressWarnings("unchecked") Map<String, String> metadata = (Map<String, String>) track.getClass().getMethod("metadata").invoke(track);
            MAIN.post(() -> {
                TrackSnapshot current = NowPlayingService.getLatestSnapshot();
                if (current == null) return;
                String currentUri = NativeTrackIdentity.canonicalUri(current.mediaId);
                // A matching title/artist does not prove the same recording or album edition.
                if (!uri.equals(currentUri)) return;
                String isrc = NativeTrackIdentity.normalizedIsrc(metadata.get("isrc"));
                if (isrc.isEmpty()) isrc = current.isrc;
                NowPlayingService.enrichEmbeddedIsrc(current.mediaId, isrc);
                SpotifyNativeMetadataClient.request(uri, !isrc.isEmpty());
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "Public current-track identity failed: " + error.getClass().getSimpleName());
        }
    }

    private static void mergeCurrent() {
        TrackSnapshot current = NowPlayingService.getLatestSnapshot();
        if (current == null) { SpotifyNativeMetadataClient.request("", false); return; }
        String uri = NativeTrackIdentity.canonicalUri(current.mediaId);
        if (uri.isEmpty()) { SpotifyNativeMetadataClient.request("", false); return; }
        String isrc;
        synchronized (ISRC) { isrc = ISRC.get(uri); }
        SpotifyNativeMetadataClient.request(uri, !current.isrc.isEmpty() || isrc != null);
        if (isrc == null || isrc.equals(current.isrc)) return;
        NowPlayingService.enrichEmbeddedIsrc(current.mediaId, isrc);
        Log.i(TAG, "Matched native ISRC to the current Spotify track");
    }
}
