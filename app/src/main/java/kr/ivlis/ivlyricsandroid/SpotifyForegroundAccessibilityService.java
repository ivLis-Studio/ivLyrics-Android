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
        String packageValue = packageName.toString();
        if (isIgnoredPackage(packageValue)) {
            return;
        }
        SpotifyShortcutOverlayController.setSpotifyForeground(isSpotifyPackage(packageValue));
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

    private boolean isIgnoredPackage(String packageName) {
        String value = packageName == null ? "" : packageName.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return true;
        }
        String ownPackage = getPackageName() == null ? "" : getPackageName().toLowerCase(Locale.ROOT);
        return value.equals(ownPackage)
                || value.equals("android")
                || value.startsWith("android.")
                || value.equals("com.android.systemui")
                || value.startsWith("com.android.systemui.");
    }
}
