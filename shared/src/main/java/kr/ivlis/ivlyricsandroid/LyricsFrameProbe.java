package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

/**
 * Debug-only per-frame renderer log for measuring lyric motion on a device.
 *
 * <p>It is inert unless the installed app is debuggable and the tag was enabled
 * before the view attached: {@code adb shell setprop log.tag.IvLyricsFrame DEBUG}.
 * Release builds and the Spotify module (Spotify's non-debuggable process) only
 * keep one cached boolean per view.
 */
final class LyricsFrameProbe {
    static final String TAG = "IvLyricsFrame";

    private LyricsFrameProbe() { }

    static boolean enabled(Context context) {
        if (context == null) return false;
        ApplicationInfo info = context.getApplicationInfo();
        return info != null && (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && Log.isLoggable(TAG, Log.DEBUG);
    }

    static StringBuilder appendFixed(StringBuilder line, double value, int decimals) {
        if (!Double.isFinite(value)) return line.append(value);
        long scale = 1L;
        for (int index = 0; index < decimals; index++) scale *= 10L;
        long scaled = Math.round(Math.abs(value) * scale);
        if (value < 0 && scaled != 0L) line.append('-');
        line.append(scaled / scale);
        if (decimals > 0) {
            line.append('.');
            String fraction = Long.toString(scaled % scale);
            for (int index = fraction.length(); index < decimals; index++) line.append('0');
            line.append(fraction);
        }
        return line;
    }

    static void log(StringBuilder line) {
        Log.d(TAG, line.toString());
    }
}
