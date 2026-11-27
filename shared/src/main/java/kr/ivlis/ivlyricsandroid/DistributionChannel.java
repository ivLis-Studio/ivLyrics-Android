package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

/**
 * Which channel delivered the standalone app. The GitHub APK keeps every feature. The Google Play
 * build (the app module's {@code play} build type) declares {@link #META_DATA_KEY} = {@code play}
 * in app/src/play/AndroidManifest.xml and, like the iOS App Store build, leaves out the GitHub APK
 * updater and the optional Discord account (creator privacy, cloud settings backup, video timing
 * sharing). That manifest also drops the Spotify shortcut's accessibility service and overlay
 * permission, so its settings section is hidden too. Playback, lyrics, translation and community
 * videos are identical. The Spotify module runs in Spotify's package and is never the Play build.
 */
final class DistributionChannel {
    static final String META_DATA_KEY = "kr.ivlis.ivlyricsandroid.DISTRIBUTION";
    static final String GOOGLE_PLAY = "play";

    /** Fixed for the lifetime of the installed package, so it is read once. */
    private static volatile Boolean googlePlay;

    private DistributionChannel() {}

    /** True only for the Google Play build of the standalone app. */
    static boolean isGooglePlay(Context context) {
        Boolean cached = googlePlay;
        if (cached != null) return cached;
        boolean value = false;
        if (context != null) {
            try {
                Context application = context.getApplicationContext() != null ? context.getApplicationContext() : context;
                value = isGooglePlay(metaData(application));
            } catch (RuntimeException | PackageManager.NameNotFoundException ignored) {
                // Unknown means the full GitHub feature set, as before this channel existed.
            }
        }
        googlePlay = value;
        return value;
    }

    static boolean isGooglePlay(Bundle metaData) {
        String value = metaData == null ? null : metaData.getString(META_DATA_KEY);
        return value != null && GOOGLE_PLAY.equalsIgnoreCase(value.trim());
    }

    @SuppressWarnings("deprecation")
    private static Bundle metaData(Context context) throws PackageManager.NameNotFoundException {
        ApplicationInfo info = context.getPackageManager()
                .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        return info == null ? null : info.metaData;
    }
}
