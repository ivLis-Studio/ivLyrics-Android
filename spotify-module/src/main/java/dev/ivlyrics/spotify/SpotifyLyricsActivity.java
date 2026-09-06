package dev.ivlyrics.spotify;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import kr.ivlis.ivlyricsandroid.BaseLyricsActivity;
import kr.ivlis.ivlyricsandroid.LyricsActivityHost;
import kr.ivlis.ivlyricsandroid.R;

/** Hosts the shared lyrics page in Spotify's process with module resources and sheet motion. */
public final class SpotifyLyricsActivity extends BaseLyricsActivity {
    // The native effect belongs to the player, so failed cleanup survives page recreation.
    private static final SpotifyKaraokeController KARAOKE = new SpotifyKaraokeController(null);
    private static SpotifyLyricsActivity karaokeOwner;
    private SpotifyKaraokeToolbar karaokeToolbar;
    private String karaokeTrack = "";

    @Override protected View createLyricsFooterAccessory() {
        karaokeToolbar = new SpotifyKaraokeToolbar(this, KARAOKE);
        if (karaokeOwner == this) bindKaraokeToolbar();
        return karaokeToolbar;
    }

    @Override protected void onLyricsHostTrackChanged(String trackUri) {
        karaokeTrack = trackUri;
        if (karaokeOwner == this) KARAOKE.trackChanged(trackUri);
    }

    @Override protected void onResume() {
        super.onResume();
        if (karaokeOwner != null && karaokeOwner != this) KARAOKE.pause();
        karaokeOwner = this;
        bindKaraokeToolbar();
        KARAOKE.trackChanged(karaokeTrack);
        KARAOKE.resume();
        if (karaokeToolbar != null) karaokeToolbar.revealTemporarily();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // Dispatch first so a touch on the hidden control only reveals it.
        boolean handled = super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (karaokeToolbar != null && (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)) {
            karaokeToolbar.revealTemporarily();
        }
        return handled;
    }

    @Override protected void onPause() {
        releaseKaraoke();
        super.onPause();
    }

    @Override public void finish() {
        releaseKaraoke();
        super.finish();
    }

    @Override protected void onDestroy() {
        releaseKaraoke();
        super.onDestroy();
    }

    private void bindKaraokeToolbar() {
        KARAOKE.setListener((state, available, reason) -> {
            if (karaokeToolbar != null) karaokeToolbar.render(state, available, reason);
        });
    }

    private void releaseKaraoke() {
        if (karaokeOwner != this) return;
        KARAOKE.pause();
        KARAOKE.setListener(null);
        karaokeOwner = null;
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(ContextBridge.wrap(base));
    }

    @Override public void setTheme(int ignoredHostTheme) {
        super.setTheme(R.style.AppTheme);
    }

    @Override protected LyricsActivityHost createLyricsActivityHost(View root) {
        return new LyricsSheetTransition(this, root);
    }
}
