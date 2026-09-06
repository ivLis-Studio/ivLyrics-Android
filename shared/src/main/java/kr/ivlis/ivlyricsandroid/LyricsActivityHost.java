package kr.ivlis.ivlyricsandroid;

import android.view.View;

/** Optional window and gesture behavior supplied by the app hosting the shared lyrics UI. */
public interface LyricsActivityHost {
    void attachRoot(View root, boolean animate);
    void interruptDrag();
    void dragTo(float translationY);
    void settle(float velocityY, Runnable finishImmediately);
    void close(Runnable finishImmediately);
    void destroy();
}
