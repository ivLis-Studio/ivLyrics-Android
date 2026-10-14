package kr.ivlis.ivlyricsandroid;

/** Visual-only handoff; neither an interlude's interval nor the following lyric timing changes. */
final class InterludeTransition {
    static final long ENTER_MS = 180L;
    static final long EXIT_MS = 320L;

    private InterludeTransition() {}

    static boolean canExit(long endMs, long positionMs, boolean motion) {
        return motion && positionMs >= endMs && positionMs - endMs <= 400L;
    }

    static float enter(long startMs, double positionMs, boolean motion) {
        return motion ? smooth((float) ((positionMs - startMs) / ENTER_MS)) : 1f;
    }

    static float exit(long elapsedMs, boolean motion) {
        return motion ? 1f - smooth(elapsedMs / (float) EXIT_MS) : 0f;
    }

    static float scale(float opacity) { return 0.94f + 0.06f * Math.max(0f, Math.min(1f, opacity)); }

    private static float smooth(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }
}
