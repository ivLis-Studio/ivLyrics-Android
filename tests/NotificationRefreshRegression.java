package kr.ivlis.ivlyricsandroid;

import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.service.notification.StatusBarNotification;

/** Session refreshes per notification: Spotify's as before, every other app's none. */
public final class NotificationRefreshRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        NowPlayingService service = new NowPlayingService();
        MediaSessionManager sessions = service.sessions;
        for (String other : new String[]{"com.whatsapp", "com.android.providers.downloads", "kr.ivlis.ivlyricsandroid",
                "com.spotifyfake.player", ""}) {
            service.onNotificationPosted(new StatusBarNotification(other));
            service.onNotificationRemoved(new StatusBarNotification(other));
            Handler.drain();
        }
        check(sessions.lookups == 0, "another app's notification does not re-read the media sessions");
        service.onNotificationPosted(new StatusBarNotification("com.spotify.music"));
        check(sessions.lookups == 1, "a Spotify notification re-reads the sessions at once");
        Handler.drain();
        check(sessions.lookups == 4, "and three more times in the follow-up burst, as before");
        service.onNotificationPosted(new StatusBarNotification("com.spotify.lite"));
        Handler.drain();
        check(sessions.lookups == 8, "any com.spotify. package counts, as the session selection does");
        service.onNotificationRemoved(new StatusBarNotification("com.spotify.music"));
        check(sessions.lookups == 9, "a removed Spotify notification re-reads the sessions once");
        Handler.drain();
        check(sessions.lookups == 9, "with no burst, as before");
        service.onNotificationPosted(null);
        Handler.drain();
        check(sessions.lookups == 13, "a notification of unknown origin still refreshes");
        System.out.println("PASS " + checks + " checks: session refresh for Spotify's notifications only");
    }
}

