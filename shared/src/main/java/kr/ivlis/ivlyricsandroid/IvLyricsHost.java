package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.Intent;

/** Resource and navigation boundaries provided by an embedding application. */
public interface IvLyricsHost {
    Context wrapContext(Context context);
    Intent createLyricsIntent(Context context);
}
