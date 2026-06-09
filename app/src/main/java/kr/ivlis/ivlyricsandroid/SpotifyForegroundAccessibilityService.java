package kr.ivlis.ivlyricsandroid;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

public final class SpotifyForegroundAccessibilityService extends AccessibilityService {
    private static final String SPOTIFY_PACKAGE = "com.spotify.music";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName == null) {
            return;
        }
        SpotifyShortcutOverlayController.setSpotifyForeground(isSpotifyPackage(packageName.toString()));
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        SpotifyShortcutOverlayController.setSpotifyForeground(false);
        super.onDestroy();
    }

    static boolean isSpotifyPackage(String packageName) {
        String value = packageName == null ? "" : packageName.trim().toLowerCase(Locale.ROOT);
        return SPOTIFY_PACKAGE.equals(value) || value.startsWith("com.spotify.");
    }
}
