package dev.ivlyrics.spotify;

import android.content.Context;
import android.view.View;

import kr.ivlis.ivlyricsandroid.BaseLyricsActivity;
import kr.ivlis.ivlyricsandroid.LyricsActivityHost;
import kr.ivlis.ivlyricsandroid.R;

/** Hosts the shared lyrics page in Spotify's process with module resources and sheet motion. */
public final class SpotifyLyricsActivity extends BaseLyricsActivity {
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
