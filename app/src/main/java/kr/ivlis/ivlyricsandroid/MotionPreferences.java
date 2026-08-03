package kr.ivlis.ivlyricsandroid;

import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;

final class MotionPreferences {
    private MotionPreferences() {}

    static boolean animationsEnabled(Context context) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return false;
        }
        try {
            return Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
            ) > 0f;
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}
