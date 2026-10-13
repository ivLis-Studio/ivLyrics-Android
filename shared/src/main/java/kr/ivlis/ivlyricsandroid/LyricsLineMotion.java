package kr.ivlis.ivlyricsandroid;

/**
 * Row travel shared with the desktop renderer: a critically damped spring and a
 * cascade. Only the rows' travel uses it; row presentation (colors, opacity) keeps
 * its own transitions.
 *
 * <p>Each change of the resting layout adds one {@link Shift}: the pixel distance the
 * rows still have to cover, decaying to zero. Active shifts add up, so a row that is
 * retargeted while moving keeps its velocity (the earlier shift keeps decaying while
 * the new one leaves from rest) and successive advances never overshoot.
 */
final class LyricsLineMotion {
    /** Stiffness is omega * duration; less than 1% of the travel remains after 470 ms. */
    static final double DURATION_MS = 560d;
    static final double STIFFNESS = 7.5d;
    static final double STAGGER_MS = 50d;
    static final double STAGGER_DECAY = 1d / 1.05d;
    static final double MAX_STAGGER_MS = 260d;
    static final double SETTLE_RESERVE_MS = 24d;
    static final double MIN_TOTAL_MS = 80d;
    private static final double TAIL = Math.exp(-STIFFNESS);
    private static final double NORMALIZER = 1d - (1d + STIFFNESS) * TAIL;

    private LyricsLineMotion() { }

    /**
     * Critically damped spring leaving rest, normalized to land exactly at u = 1:
     * p(u) = (1 - (1 + ku)e^(-ku)) / (1 - (1 + k)e^(-k)). Its velocity is zero at u = 0.
     */
    static double progress(double u) {
        double t = Math.max(0d, Math.min(1d, u));
        return (1d - (1d + STIFFNESS * t) * Math.exp(-STIFFNESS * t)) / NORMALIZER;
    }

    /**
     * A rapid passage can advance again before a travel and its cascade settle.
     * The whole budget scales so every row arrives just before the next change.
     * A non-positive window uses the full budget.
     */
    static Timing timing(double transitionWindowMs) {
        if (!(transitionWindowMs > 0d) || Double.isInfinite(transitionWindowMs)) {
            return new Timing(DURATION_MS, STAGGER_MS, MAX_STAGGER_MS);
        }
        double available = Math.max(MIN_TOTAL_MS, transitionWindowMs - SETTLE_RESERVE_MS);
        double scale = Math.min(1d, available / (DURATION_MS + MAX_STAGGER_MS));
        double duration = Math.max(1d, Math.round(DURATION_MS * scale));
        // Rounding the travel must not push the last row past the window.
        double maxStagger = Math.max(0d, Math.min(MAX_STAGGER_MS * scale, Math.floor(available) - duration));
        return new Timing(duration, STAGGER_MS * scale, maxStagger);
    }

    /** Rows at or above the anchor start together; each row below trails by a shrinking step. */
    static double delay(int animationIndex, Timing timing) {
        if (animationIndex <= 0 || !(timing.staggerMs > 0d)) return 0d;
        return Math.min(timing.maxStaggerMs,
                timing.staggerMs * (1d - Math.pow(STAGGER_DECAY, animationIndex)) / (1d - STAGGER_DECAY));
    }

    static final class Timing {
        final double durationMs;
        final double staggerMs;
        final double maxStaggerMs;

        Timing(double durationMs, double staggerMs, double maxStaggerMs) {
            this.durationMs = durationMs;
            this.staggerMs = staggerMs;
            this.maxStaggerMs = maxStaggerMs;
        }
    }

    /**
     * Rows' remaining distance after one change of the resting layout. Pixels on the
     * rows' axis; times on the Choreographer frame clock (System.nanoTime()).
     */
    static final class Shift {
        final float offsetPx;
        final long startNanos;
        final Timing timing;
        final boolean cascade;
        /** Display index the cascade counts from; remapped when display rows change. */
        float anchorIndex;

        Shift(float offsetPx, long startNanos, Timing timing, boolean cascade, float anchorIndex) {
            this.offsetPx = offsetPx;
            this.startNanos = startNanos;
            this.timing = timing;
            this.cascade = cascade;
            this.anchorIndex = anchorIndex;
        }

        double delayMs(int rowIndex) {
            return cascade ? delay(rowIndex - Math.round(anchorIndex), timing) : 0d;
        }

        /** Offset still to travel for the display row at {@code rowIndex}. */
        float offsetAt(int rowIndex, long nanos) {
            double u = ((nanos - startNanos) / 1_000_000d - delayMs(rowIndex)) / timing.durationMs;
            if (u <= 0d) return offsetPx;
            if (u >= 1d) return 0f;
            return (float) (offsetPx * (1d - progress(u)));
        }

        /** True once the deepest cascaded row has arrived. */
        boolean finishedAt(long nanos) {
            double lastDelay = cascade ? timing.maxStaggerMs : 0d;
            return (nanos - startNanos) / 1_000_000d >= lastDelay + timing.durationMs;
        }
    }
}
