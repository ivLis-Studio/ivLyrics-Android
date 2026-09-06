package kr.ivlis.ivlyricsandroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public final class PictureInPictureActionReceiver extends BroadcastReceiver {
    static final String ACTION_PREVIOUS = "kr.ivlis.ivlyricsandroid.pip.PREVIOUS";
    static final String ACTION_TOGGLE_PLAYBACK = "kr.ivlis.ivlyricsandroid.pip.TOGGLE_PLAYBACK";
    static final String ACTION_NEXT = "kr.ivlis.ivlyricsandroid.pip.NEXT";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (ACTION_PREVIOUS.equals(action)) {
            NowPlayingService.skipToPrevious();
        } else if (ACTION_TOGGLE_PLAYBACK.equals(action)) {
            NowPlayingService.togglePlayback();
        } else if (ACTION_NEXT.equals(action)) {
            NowPlayingService.skipToNext();
        } else {
            return;
        }
        Context appContext = context.getApplicationContext();
        NowPlayingService.requestRefresh(appContext);
        MAIN.postDelayed(() -> NowPlayingService.requestRefresh(appContext), 240L);
    }
}
