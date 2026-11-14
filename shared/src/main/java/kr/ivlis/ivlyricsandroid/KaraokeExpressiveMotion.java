package kr.ivlis.ivlyricsandroid;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Desktop expressive karaoke motion ("karaoke-motion-style": "expressive"). Each timing unit
 * follows keyframe targets through damped springs, so sung units swell, lift and glow with a
 * small overshoot. On the line being sung, units not reached yet rest slightly smaller and lower,
 * and long held notes move letter by letter.
 *
 * <p>Desktop steps its springs every display frame from the unit's onset, starting at the pending
 * pose. The goals depend only on the unit's progress, so the trajectory depends only on its
 * duration: it is simulated once per duration and read by playback position, which keeps
 * previews, seeks and skipped frames deterministic.
 */
final class KaraokeExpressiveMotion {
    static final double SETTLE_MS = 820d;
    static final double EMPHASIS_MIN_MS = 1000d;
    static final double PENDING_SCALE = .95d;
    static final double PENDING_OFFSET_EM = .012d;
    /** Desktop eases the pending pose in over 240 ms when the line becomes the one being sung. */
    static final double POSE_EASE_MS = 240d;

    private static final double STEP_MS = 4d;
    private static final double MAX_DURATION_MS = 30_000d;
    private static final double[][] WORD_SCALE = {{0, PENDING_SCALE}, {.7, 1.05}, {1, 1}};
    private static final double[][] WORD_OFFSET = {{0, PENDING_OFFSET_EM}, {.9, -.03}, {1, 0}};
    private static final double[][] WORD_GLOW = {{0, 0}, {.15, .5}, {.6, .5}, {1, 0}};
    private static final double[][] EMPHASIS_SCALE = {{0, PENDING_SCALE}, {.7, 1.14}, {1, 1}};
    private static final double[][] EMPHASIS_OFFSET = {{0, PENDING_OFFSET_EM}, {.9, -.05}, {1, 0}};
    private static final double[][] EMPHASIS_GLOW = {{0, 0}, {.15, .8}, {.6, .8}, {1, 0}};
    // {frequency in Hz, damping ratio}: underdamped for a visible overshoot, settled within 820 ms.
    private static final double[] SCALE_SPRING = {1.05, .62};
    private static final double[] OFFSET_SPRING = {1.5, .45};
    private static final double[] GLOW_SPRING = {1.2, .6};
    private static final Pattern SYLLABIC_SCRIPT = Pattern.compile(
            "[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}\\p{IsThai}\\p{IsLao}\\p{IsKhmer}\\p{IsMyanmar}]");
    private static final int TRAJECTORY_LIMIT = 96;
    private static final Map<Integer, Trajectory> TRAJECTORIES =
            new LinkedHashMap<Integer, Trajectory>(TRAJECTORY_LIMIT, .75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Trajectory> eldest) {
                    return size() > TRAJECTORY_LIMIT;
                }
            };

    private KaraokeExpressiveMotion() { }

    /** One moving unit: the window it moves in and its curve family. */
    static final class Unit {
        final double start, end;
        final boolean emphasis;

        Unit(double start, double end, boolean emphasis) {
            this.start = start; this.end = end; this.emphasis = emphasis;
        }
    }

    /**
     * Writes the unit's values at {@code positionMs} into {@code result}. {@code pendingPose} (0..1)
     * applies the resting pose to units the line being sung has not reached; elsewhere it is 0 and
     * those units stay idle. The offset is in pixels of {@code textSizePx}.
     */
    static KaraokeMotion.Values evaluate(Unit unit, double positionMs, double pendingPose, float textSizePx,
            KaraokeMotion.Values result) {
        if (unit == null || !Double.isFinite(positionMs) || !Float.isFinite(textSizePx) || textSizePx <= 0f) {
            return result.idle();
        }
        double elapsed = positionMs - unit.start;
        double scale, offset, glow;
        if (elapsed < 0) {
            double pose = Math.max(0, Math.min(1, pendingPose));
            if (pose <= 0) return result.idle();
            scale = 1 - (1 - PENDING_SCALE) * pose;
            offset = PENDING_OFFSET_EM * pose;
            glow = 0;
        } else {
            double duration = Math.max(0, unit.end - unit.start);
            if (elapsed >= duration + SETTLE_MS) return result.idle();
            Trajectory trajectory = trajectory(duration, unit.emphasis);
            double index = elapsed / STEP_MS;
            int low = Math.min(trajectory.scale.length - 1, (int) index);
            int high = Math.min(trajectory.scale.length - 1, low + 1);
            float t = (float) (index - low);
            scale = trajectory.scale[low] + (trajectory.scale[high] - trajectory.scale[low]) * t;
            offset = trajectory.offset[low] + (trajectory.offset[high] - trajectory.offset[low]) * t;
            glow = Math.max(0, trajectory.glow[low] + (trajectory.glow[high] - trajectory.glow[low]) * t);
        }
        result.offsetY = (float) (offset * textSizePx);
        result.scale = (float) scale;
        result.glow = (float) glow;
        result.active = true;
        return result;
    }

    /**
     * Strength of the pending pose for a row: eased in over the 240 ms before the line starts while
     * it is the line being sung, and absent once every unit has been reached.
     */
    static double pendingPose(boolean active, double positionMs, double lineStartMs, double lineEndMs) {
        if (!active || !Double.isFinite(positionMs) || !Double.isFinite(lineStartMs)) return 0;
        if (lineEndMs > lineStartMs && positionMs >= lineEndMs) return 0;
        return KaraokeMotion.smooth((positionMs - (lineStartMs - POSE_EASE_MS)) / POSE_EASE_MS);
    }

    /**
     * The moving unit of each display segment, as desktop classifies a character-timed line:
     * syllabic scripts move per glyph; a space-delimited word moves as one unit unless it is held, or
     * long enough to move letter by letter ("emphasis"). Hyphens and periods end a unit. Rows
     * displayed per word move per word. Whitespace and untimed segments do not move.
     */
    static Unit[] units(List<String> texts, double[] starts, double[] ends, boolean wordGranularity) {
        int count = texts.size();
        Unit[] units = new Unit[count];
        if (wordGranularity) {
            for (int index = 0; index < count; index++) {
                if (isWhitespace(texts.get(index)) || !Double.isFinite(starts[index]) || !Double.isFinite(ends[index])) continue;
                units[index] = new Unit(starts[index], ends[index], false);
            }
            return units;
        }
        for (int[] range : unitRanges(texts)) {
            StringBuilder text = new StringBuilder();
            double start = Double.POSITIVE_INFINITY, end = Double.NEGATIVE_INFINITY;
            for (int index = range[0]; index < range[1]; index++) {
                text.append(texts.get(index));
                if (Double.isFinite(starts[index]) && Double.isFinite(ends[index])) {
                    start = Math.min(start, starts[index]);
                    end = Math.max(end, ends[index]);
                }
            }
            if (!Double.isFinite(start) || !Double.isFinite(end)) continue;
            double duration = end - start;
            List<String> glyphs = graphemes(text.toString());
            int letterCount = glyphs.size();
            int sungLetters = 0;
            // Punctuation is drawn but is not an extra sung letter.
            for (String glyph : glyphs) {
                int point = glyph.codePointAt(0);
                if (Character.isLetter(point) || Character.isDigit(point)
                        || Character.getType(point) == Character.LETTER_NUMBER
                        || Character.getType(point) == Character.OTHER_NUMBER) sungLetters++;
            }
            boolean hasLetterWindow = duration >= letterCount * 45d;
            boolean longerWord = sungLetters >= 7 && duration >= 600d;
            int mode;
            if (SYLLABIC_SCRIPT.matcher(text).find()) {
                mode = 0;
            } else if (letterCount > 1 && letterCount <= 24 && hasLetterWindow
                    && (longerWord || duration >= EMPHASIS_MIN_MS)) {
                mode = 2;
            } else {
                mode = 1;
            }
            for (int index = range[0]; index < range[1]; index++) {
                if (mode == 1) {
                    units[index] = new Unit(start, end, false);
                } else if (Double.isFinite(starts[index]) && Double.isFinite(ends[index])) {
                    units[index] = new Unit(starts[index], ends[index],
                            mode == 2 || ends[index] - starts[index] >= EMPHASIS_MIN_MS);
                }
            }
        }
        return units;
    }

    /** Desktop's unit ranges: split at whitespace and after a separator run, which stays with the
     * piece before it (a leading run stays with the first piece). Never creates an empty unit. */
    private static List<int[]> unitRanges(List<String> texts) {
        List<int[]> ranges = new ArrayList<>();
        int start = 0;
        boolean hasText = false, afterSeparator = false;
        for (int index = 0; index <= texts.size(); index++) {
            boolean end = index == texts.size();
            String text = end ? "" : texts.get(index);
            if (end || isWhitespace(text)) {
                if (index > start) ranges.add(new int[]{start, index});
                start = index + 1;
                hasText = false;
                afterSeparator = false;
                continue;
            }
            boolean separator = isSeparator(text);
            if (afterSeparator && !separator && hasText) {
                ranges.add(new int[]{start, index});
                start = index;
                hasText = false;
            }
            if (!separator) hasText = true;
            afterSeparator = separator;
        }
        return ranges;
    }

    private static boolean isWhitespace(String text) {
        if (text == null || text.isEmpty()) return true;
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset);
            if (!Character.isWhitespace(point) && !Character.isSpaceChar(point)) return false;
            offset += Character.charCount(point);
        }
        return true;
    }

    private static boolean isSeparator(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value != '.' && value != '-' && value != '\u2010' && value != '\u2011') return false;
        }
        return true;
    }

    private static List<String> graphemes(String text) {
        List<String> result = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            result.add(text.substring(start, end));
        }
        return result;
    }

    static double sampleCurve(double[][] points, double progress) {
        if (progress <= points[0][0]) return points[0][1];
        for (int index = 1; index < points.length; index++) {
            if (progress <= points[index][0]) {
                double[] previous = points[index - 1], next = points[index];
                double t = (progress - previous[0]) / Math.max(1e-6, next[0] - previous[0]);
                return previous[1] + (next[1] - previous[1]) * t * t * (3 - 2 * t);
            }
        }
        return points[points.length - 1][1];
    }

    private static final class Trajectory {
        final float[] scale, offset, glow;
        Trajectory(float[] scale, float[] offset, float[] glow) {
            this.scale = scale; this.offset = offset; this.glow = glow;
        }
    }

    private static Trajectory trajectory(double durationMs, boolean emphasis) {
        double duration = Math.min(MAX_DURATION_MS, Math.max(0, Math.rint(durationMs)));
        int key = (int) duration * 2 + (emphasis ? 1 : 0);
        synchronized (TRAJECTORIES) {
            Trajectory cached = TRAJECTORIES.get(key);
            if (cached != null) return cached;
            Trajectory built = simulate(duration, emphasis);
            TRAJECTORIES.put(key, built);
            return built;
        }
    }

    private static Trajectory simulate(double durationMs, boolean emphasis) {
        double[][] scaleCurve = emphasis ? EMPHASIS_SCALE : WORD_SCALE;
        double[][] offsetCurve = emphasis ? EMPHASIS_OFFSET : WORD_OFFSET;
        double[][] glowCurve = emphasis ? EMPHASIS_GLOW : WORD_GLOW;
        int count = (int) Math.ceil((durationMs + SETTLE_MS) / STEP_MS) + 1;
        float[] scales = new float[count], offsets = new float[count], glows = new float[count];
        double[] scale = {PENDING_SCALE, 0}, offset = {PENDING_OFFSET_EM, 0}, glow = {0, 0};
        double seconds = STEP_MS / 1000d;
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                double elapsed = index * STEP_MS;
                double progress = durationMs > 0 ? Math.min(1, elapsed / durationMs) : 1;
                step(scale, sampleCurve(scaleCurve, progress), seconds, SCALE_SPRING);
                step(offset, sampleCurve(offsetCurve, progress), seconds, OFFSET_SPRING);
                step(glow, sampleCurve(glowCurve, progress), seconds, GLOW_SPRING);
            }
            scales[index] = (float) scale[0];
            offsets[index] = (float) offset[0];
            glows[index] = (float) glow[0];
        }
        return new Trajectory(scales, offsets, glows);
    }

    /** Closed-form step of an underdamped spring toward a fixed goal, exact for any step. */
    private static void step(double[] spring, double goal, double seconds, double[] parameters) {
        double omega = 2 * Math.PI * parameters[0];
        double damping = parameters[1];
        double offset = spring[0] - goal;
        double velocity = spring[1];
        double decay = Math.exp(-damping * omega * seconds);
        double dampedOmega = omega * Math.sqrt(1 - damping * damping);
        double cosine = Math.cos(dampedOmega * seconds);
        double sine = Math.sin(dampedOmega * seconds);
        spring[0] = goal + decay * (offset * cosine + ((velocity + damping * omega * offset) / dampedOmega) * sine);
        spring[1] = decay * (velocity * cosine - ((damping * omega * velocity + omega * omega * offset) / dampedOmega) * sine);
    }
}
