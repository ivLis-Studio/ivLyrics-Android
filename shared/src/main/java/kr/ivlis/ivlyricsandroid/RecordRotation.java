package kr.ivlis.ivlyricsandroid;

/** Phase-continuous LP clock. The historical 100% speed is one revolution per 40 seconds. */
final class RecordRotation {
    static final String SPEED_KEY = "vinyl_rotation_speed_percent";
    static final String CHANGE_KEY = "vinyl_track_change_animations";
    static final String SIMPLE_TITLE_KEY = "vinyl_simplified_title";
    private long lastTimeMs = -1L;
    private double degrees;
    private int speedPercent = 100;

    static int speed(int value) { return Math.max(25, Math.min(200, value)); }

    void setSpeed(int percent) { speedPercent = speed(percent); }

    double advance(long nowMs, boolean spinning) {
        if (lastTimeMs >= 0 && spinning) {
            degrees = (degrees + Math.max(0L, nowMs - lastTimeMs) * 0.009 * speedPercent / 100.0) % 360.0;
        }
        lastTimeMs = nowMs;
        return degrees;
    }

    static boolean animateTrackChange(boolean master, boolean changes, boolean motion, boolean requested, boolean shown) {
        return master && changes && motion && requested && shown;
    }

    static String displayTitle(String source, boolean simplified) {
        String title = source == null ? "" : source.trim();
        if (!simplified) return title;
        // Remove release/credit qualifiers only; meaningful parenthesized title text stays intact.
        String simplifiedTitle = title.replaceAll("(?i)\\s*[\\[(](?:feat\\.?|ft\\.?|with|remaster(?:ed)?|live|radio edit|.*?version|.*?remix)\\b[^\\])]*[\\])]", "")
                .replaceAll("(?i)\\s+-\\s+(?:\\d{4}\\s+)?(?:remaster(?:ed)?|live|radio edit|.*?version|.*?remix)\\b.*$", "")
                .trim();
        return simplifiedTitle.isEmpty() ? title : simplifiedTitle;
    }
}
