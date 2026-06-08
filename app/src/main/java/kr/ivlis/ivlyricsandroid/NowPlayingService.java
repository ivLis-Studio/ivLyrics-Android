package kr.ivlis.ivlyricsandroid;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NowPlayingService extends NotificationListenerService {
    private static final Pattern ISRC_PATTERN = Pattern.compile("[A-Z]{2}[A-Z0-9]{3}\\d{7}", Pattern.CASE_INSENSITIVE);
    private static final String SPOTIFY_PACKAGE_PREFIX = "com.spotify.";
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WeakReference<NowPlayingService> instance = new WeakReference<>(null);
    private static volatile TrackSnapshot latestSnapshot;
    private static volatile MediaController activeController;
    private static volatile MediaController.Callback activeCallback;

    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> artworkCache = new ConcurrentHashMap<>();
    private final Set<String> artworkLoading = ConcurrentHashMap.newKeySet();

    interface Listener {
        void onNowPlayingChanged(TrackSnapshot snapshot);
    }

    static void register(Listener listener) {
        LISTENERS.add(listener);
        TrackSnapshot snapshot = latestSnapshot;
        if (snapshot != null) {
            MAIN.post(() -> listener.onNowPlayingChanged(snapshot));
        }
    }

    static void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    static TrackSnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    static void requestRefresh(Context context) {
        NowPlayingService service = instance.get();
        if (service != null) {
            service.refreshSessions();
        }
    }

    static boolean isNotificationAccessEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners"
        );
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }

        ComponentName expected = new ComponentName(context, NowPlayingService.class);
        String[] flattenedNames = enabled.split(":");
        for (String flattenedName : flattenedNames) {
            ComponentName componentName = ComponentName.unflattenFromString(flattenedName);
            if (expected.equals(componentName)) {
                return true;
            }
        }
        return enabled.toLowerCase(Locale.ROOT).contains(context.getPackageName().toLowerCase(Locale.ROOT));
    }

    static boolean togglePlayback() {
        MediaController controller = activeController;
        if (controller == null) return false;
        PlaybackState state = controller.getPlaybackState();
        int playbackState = state == null ? PlaybackState.STATE_NONE : state.getState();
        if (playbackState == PlaybackState.STATE_PLAYING || playbackState == PlaybackState.STATE_BUFFERING) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
        return true;
    }

    static boolean skipToNext() {
        MediaController controller = activeController;
        if (controller == null) return false;
        controller.getTransportControls().skipToNext();
        return true;
    }

    static boolean skipToPrevious() {
        MediaController controller = activeController;
        if (controller == null) return false;
        controller.getTransportControls().skipToPrevious();
        return true;
    }

    static boolean seekTo(long positionMs) {
        MediaController controller = activeController;
        if (controller == null) return false;
        controller.getTransportControls().seekTo(Math.max(0L, positionMs));
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = new WeakReference<>(this);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = new WeakReference<>(this);
        refreshSessions();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        refreshSessions();
        scheduleRefreshBurst();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        refreshSessions();
    }

    @Override
    public void onDestroy() {
        unregisterActiveCallback();
        artworkExecutor.shutdownNow();
        if (instance.get() == this) {
            instance = new WeakReference<>(null);
        }
        super.onDestroy();
    }

    private void refreshSessions() {
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;

            ComponentName componentName = new ComponentName(this, NowPlayingService.class);
            List<MediaController> controllers = manager.getActiveSessions(componentName);
            MediaController selected = selectController(controllers);
            if (selected == null) {
                publish(null);
                return;
            }

            registerActiveCallback(selected);
            publish(buildSnapshot(selected));
        } catch (SecurityException ignored) {
            publish(null);
        }
    }

    private MediaController selectController(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) {
            return null;
        }

        MediaController pausedCandidate = null;
        for (MediaController controller : controllers) {
            if (controller == null || controller.getMetadata() == null) {
                continue;
            }
            if (!isSpotifyPackage(controller.getPackageName())) {
                continue;
            }
            PlaybackState state = controller.getPlaybackState();
            int playbackState = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (playbackState == PlaybackState.STATE_PLAYING || playbackState == PlaybackState.STATE_BUFFERING) {
                return controller;
            }
            if (pausedCandidate == null && playbackState == PlaybackState.STATE_PAUSED) {
                pausedCandidate = controller;
            }
        }
        return pausedCandidate;
    }

    private static boolean isSpotifyPackage(String packageName) {
        String value = packageName == null ? "" : packageName.trim().toLowerCase(Locale.ROOT);
        return value.startsWith(SPOTIFY_PACKAGE_PREFIX);
    }

    private void registerActiveCallback(MediaController controller) {
        if (activeController == controller && activeCallback != null) {
            return;
        }

        unregisterActiveCallback();
        activeController = controller;
        activeCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                publish(buildSnapshot(controller));
                scheduleRefreshBurst();
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                publish(buildSnapshot(controller));
                scheduleRefreshBurst();
            }

            @Override
            public void onSessionDestroyed() {
                refreshSessions();
            }
        };
        controller.registerCallback(activeCallback);
    }

    private void unregisterActiveCallback() {
        MediaController controller = activeController;
        MediaController.Callback callback = activeCallback;
        if (controller != null && callback != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Exception ignored) {
            }
        }
        activeController = null;
        activeCallback = null;
    }

    private void scheduleRefreshBurst() {
        MAIN.postDelayed(this::refreshSessions, 80L);
        MAIN.postDelayed(this::refreshSessions, 240L);
        MAIN.postDelayed(this::refreshSessions, 560L);
    }

    private TrackSnapshot buildSnapshot(MediaController controller) {
        MediaMetadata metadata = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();
        boolean playing = state != null && (
                state.getState() == PlaybackState.STATE_PLAYING
                        || state.getState() == PlaybackState.STATE_BUFFERING
        );
        long position = state == null ? 0L : Math.max(0L, state.getPosition());
        long updateTime = state == null || state.getLastPositionUpdateTime() <= 0L
                ? SystemClock.elapsedRealtime()
                : state.getLastPositionUpdateTime();
        float speed = state == null || state.getPlaybackSpeed() <= 0f ? 1f : state.getPlaybackSpeed();

        String title = firstNonEmpty(
                metadataString(metadata, MediaMetadata.METADATA_KEY_TITLE),
                metadataString(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        );
        String artist = firstNonEmpty(
                metadataString(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                firstNonEmpty(
                        metadataString(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                        metadataString(metadata, MediaMetadata.METADATA_KEY_AUTHOR)
                )
        );
        String album = metadataString(metadata, MediaMetadata.METADATA_KEY_ALBUM);
        String mediaId = firstNonEmpty(
                metadataString(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID),
                metadataString(metadata, MediaMetadata.METADATA_KEY_MEDIA_URI)
        );
        String isrc = findIsrc(metadata);
        long duration = metadata == null ? 0L : Math.max(0L, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        Bitmap artwork = firstNonNull(
                metadataBitmap(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART),
                firstNonNull(
                        metadataBitmap(metadata, MediaMetadata.METADATA_KEY_ART),
                        firstNonNull(
                                metadataBitmap(metadata, MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                                descriptionIconBitmap(metadata)
                        )
                )
        );
        String artworkUri = firstNonEmpty(
                metadataString(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                firstNonEmpty(
                        metadataString(metadata, MediaMetadata.METADATA_KEY_ART_URI),
                        firstNonEmpty(
                                metadataString(metadata, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
                                descriptionIconUri(metadata)
                        )
                )
        );
        if (artwork == null && !artworkUri.isEmpty()) {
            artwork = artworkCache.get(artworkUri);
            if (artwork == null) {
                requestArtworkLoad(controller, artworkUri);
            }
        }

        return new TrackSnapshot(
                title,
                artist,
                album,
                controller.getPackageName(),
                mediaId,
                isrc,
                duration,
                position,
                updateTime,
                speed,
                playing,
                artwork,
                artworkUri
        );
    }

    private void publish(TrackSnapshot snapshot) {
        latestSnapshot = snapshot;
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) {
                listener.onNowPlayingChanged(snapshot);
            }
        });
    }

    private void requestArtworkLoad(MediaController controller, String artworkUri) {
        if (controller == null || artworkUri == null || artworkUri.trim().isEmpty() || artworkExecutor.isShutdown()) {
            return;
        }
        String cacheKey = artworkUri.trim();
        if (!artworkLoading.add(cacheKey)) {
            return;
        }

        try {
            artworkExecutor.execute(() -> {
                Bitmap bitmap = loadArtworkBitmap(cacheKey);
                if (bitmap != null && !bitmap.isRecycled()) {
                    if (artworkCache.size() > 24) {
                        artworkCache.clear();
                    }
                    artworkCache.put(cacheKey, bitmap);
                }
                artworkLoading.remove(cacheKey);
                if (bitmap != null && activeController == controller) {
                    publish(buildSnapshot(controller));
                }
            });
        } catch (RuntimeException ignored) {
            artworkLoading.remove(cacheKey);
        }
    }

    private Bitmap loadArtworkBitmap(String artworkUri) {
        String safeUri = artworkUri == null ? "" : artworkUri.trim();
        if (safeUri.isEmpty()) {
            return null;
        }
        if (safeUri.startsWith("spotify:image:")) {
            String imageId = safeUri.substring("spotify:image:".length()).trim();
            if (!imageId.isEmpty()) {
                return loadHttpBitmap("https://i.scdn.co/image/" + imageId);
            }
        }

        Uri uri;
        try {
            uri = Uri.parse(safeUri);
        } catch (Exception ignored) {
            return null;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) || "https".equals(scheme)) {
            return loadHttpBitmap(safeUri);
        }

        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            return stream == null ? null : BitmapFactory.decodeStream(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap loadHttpBitmap(String urlValue) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlValue);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "ivLyrics-Android/0.1");
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String findIsrc(MediaMetadata metadata) {
        if (metadata == null) {
            return "";
        }

        for (String key : metadata.keySet()) {
            String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
            String value = "";
            if (normalizedKey.contains("isrc")) {
                value = metadataString(metadata, key);
                String normalized = TrackSnapshot.normalizeIsrc(value);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
            if (value.isEmpty() && isStringMetadataKey(key)) {
                value = metadataString(metadata, key);
            }
            if (value != null) {
                Matcher matcher = ISRC_PATTERN.matcher(value);
                if (matcher.find()) {
                    String normalized = TrackSnapshot.normalizeIsrc(matcher.group());
                    if (!normalized.isEmpty()) {
                        return normalized;
                    }
                }
            }
        }
        return "";
    }

    private String metadataString(MediaMetadata metadata, String key) {
        if (metadata == null || key == null) {
            return "";
        }
        try {
            CharSequence value = metadata.getText(key);
            return value == null ? "" : value.toString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isStringMetadataKey(String key) {
        if (key == null) {
            return false;
        }
        switch (key) {
            case MediaMetadata.METADATA_KEY_TITLE:
            case MediaMetadata.METADATA_KEY_ARTIST:
            case MediaMetadata.METADATA_KEY_ALBUM:
            case MediaMetadata.METADATA_KEY_ALBUM_ARTIST:
            case MediaMetadata.METADATA_KEY_AUTHOR:
            case MediaMetadata.METADATA_KEY_WRITER:
            case MediaMetadata.METADATA_KEY_COMPOSER:
            case MediaMetadata.METADATA_KEY_COMPILATION:
            case MediaMetadata.METADATA_KEY_DATE:
            case MediaMetadata.METADATA_KEY_GENRE:
            case MediaMetadata.METADATA_KEY_MEDIA_ID:
            case MediaMetadata.METADATA_KEY_MEDIA_URI:
            case MediaMetadata.METADATA_KEY_DISPLAY_TITLE:
            case MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE:
            case MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION:
                return true;
            default:
                return false;
        }
    }

    private Bitmap metadataBitmap(MediaMetadata metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        try {
            return metadata.getBitmap(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap descriptionIconBitmap(MediaMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            MediaDescription description = metadata.getDescription();
            return description == null ? null : description.getIconBitmap();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String descriptionIconUri(MediaMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        try {
            MediaDescription description = metadata.getDescription();
            Uri uri = description == null ? null : description.getIconUri();
            return uri == null ? "" : uri.toString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}
