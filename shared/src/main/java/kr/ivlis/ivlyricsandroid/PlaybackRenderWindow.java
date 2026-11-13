package kr.ivlis.ivlyricsandroid;

/**
 * The interval of playback positions that paint the same frame. Built from the actual displayed
 * rows after a draw, not from a second lyric selector. Boundaries work in both seek directions.
 * Any continuous fill, fade or wall-clock motion makes the interval unusable.
 */
final class PlaybackRenderWindow {
    private double position, start, end;
    private boolean valid;

    void begin(double position) {
        this.position = position;
        start = Double.NEGATIVE_INFINITY;
        end = Double.POSITIVE_INFINITY;
        valid = true;
    }

    void boundary(double time) {
        if (time <= position) start = Math.max(start, time);
        else end = Math.min(end, time);
    }

    void motion(double from, double until) {
        boundary(from);
        boundary(until);
        if (position >= from && position < until) invalidate();
    }

    void invalidate() { valid = false; }

    boolean contains(double next) {
        return valid && Double.isFinite(next) && next >= start && next < end;
    }
}
