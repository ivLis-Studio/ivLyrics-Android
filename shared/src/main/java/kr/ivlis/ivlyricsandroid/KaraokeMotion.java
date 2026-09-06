package kr.ivlis.ivlyricsandroid;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Desktop 6.6.8 source-timed motion, prepared with text layout rather than per frame. */
final class KaraokeMotion {
    // Desktop's default original lyric font is 44 CSS px. Keep its displacement
    // relative to the actual mobile glyph size, including accessibility font scaling.
    static final float DESKTOP_TEXT_SIZE = 44f;
    private static final float[] TRIANGLE_TIMES = {0f, .5f, 1f};
    private static final float[] TRIANGLE_VALUES = {0f, 1f, 0f};
    private static final float[] WAVE_TIMES = {0f, .35f, .70f, 1f};
    private static final float[] WAVE_VALUES = {0f, -.11f, .03f, 0f};
    private static final float[] BOUNCE_TIMES = {0f, .32f, .58f, .76f, 1f};
    private static final float[] BOUNCE_VALUES = {0f, -.16f, .035f, -.045f, 0f};
    private static final float[] POP_TIMES = {0f, .18f, .34f, 1f};
    private static final float[] POP_VALUES = {1f, 1.035f, .996f, 1f};
    private static final float[] PULSE_TIMES = {0f, .45f, 1f};
    private static final float[] PULSE_VALUES = {1f, 1.025f, 1f};
    private static final float[] SWAY_TIMES = {0f, .30f, .70f, 1f};
    private static final float[] SWAY_VALUES = {0f, -1f, 1f, 0f};

    private KaraokeMotion() { }

    static final class Profile {
        final double startTime, endTime, riseDuration, releaseDuration, amplitude, scaleAmount;

        Profile(double start, double end, double cadence, double gap, double holdEnd) {
            double calm = smooth((cadence - 90) / 230);
            double sustained = smooth((holdEnd - start - 700) / 900);
            startTime = start;
            endTime = holdEnd;
            riseDuration = Math.max(1, Math.min(Math.min(holdEnd - start, 220), 45 + cadence * .45));
            releaseDuration = Math.min(700, 110 + 220 * calm + 100 * sustained
                    + Math.min(800, Math.max(0, gap)) * .25 * calm);
            amplitude = 1.1 + 3.9 * calm + .6 * sustained;
            scaleAmount = .006 + .024 * calm;
        }
    }

    static final class Values {
        float offsetY;
        float scale = 1f;
        boolean active;

        Values idle() { offsetY = 0f; scale = 1f; active = false; return this; }
    }

    /** Allocation-free continuous Canvas values; desktop's quantized targets use CSS interpolation. */
    static Values evaluate(Profile profile, long positionMs, float textSizePx, Values result) {
        if (profile == null || !Float.isFinite(textSizePx) || textSizePx <= 0f
                || positionMs < profile.startTime || positionMs >= profile.endTime + profile.releaseDuration) {
            return result.idle();
        }
        double strength = positionMs < profile.endTime
                ? smooth((positionMs - profile.startTime) / profile.riseDuration)
                : 1 - smooth((positionMs - profile.endTime) / profile.releaseDuration);
        result.offsetY = (float) (-profile.amplitude * strength * textSizePx / DESKTOP_TEXT_SIZE);
        result.scale = (float) (1 + profile.scaleAmount * strength);
        result.active = result.offsetY != 0f || result.scale != 1f;
        return result;
    }

    static double smooth(double value) {
        double x = Math.max(0, Math.min(1, value));
        return x * x * (3 - 2 * x);
    }

    static float waveOffset(long timeMs, int segmentIndex, float textSizePx) {
        int child = segmentIndex + 1;
        long delay = child % 3 == 0 ? 240 : child % 2 == 0 ? 120 : 0;
        return keyframe(timeMs + delay, 920, WAVE_TIMES, WAVE_VALUES, 0) * textSizePx;
    }

    static float bounceOffset(long timeMs, float textSizePx) {
        return keyframe(timeMs, 780, BOUNCE_TIMES, BOUNCE_VALUES, 1) * textSizePx;
    }

    static float adlibOffset(long timeMs, float textSizePx) {
        return -1.5f * keyframe(timeMs, 1050, TRIANGLE_TIMES, TRIANGLE_VALUES, 0)
                * textSizePx / DESKTOP_TEXT_SIZE;
    }

    static float pulseScale(long timeMs) {
        return keyframe(timeMs, 940, PULSE_TIMES, PULSE_VALUES, 0);
    }

    static float popScale(long timeMs) {
        return keyframe(timeMs, 1080, POP_TIMES, POP_VALUES, 2);
    }

    static float swayStrength(long timeMs) {
        return keyframe(timeMs, 1350, SWAY_TIMES, SWAY_VALUES, 0);
    }

    static float floatStrength(long timeMs) {
        return keyframe(timeMs, 1650, TRIANGLE_TIMES, TRIANGLE_VALUES, 0);
    }

    private static float keyframe(long timeMs, long periodMs, float[] times, float[] values, int easing) {
        double phase = Math.floorMod(timeMs, periodMs) / (double) periodMs;
        int index = 1;
        while (index < times.length - 1 && phase > times[index]) index++;
        double progress = (phase - times[index - 1]) / (times[index] - times[index - 1]);
        double eased = easing == 1 ? bezier(progress, .2, .85, .24, 1)
                : easing == 2 ? bezier(progress, .18, .9, .36, 1)
                : bezier(progress, .42, 0, .58, 1);
        return (float) (values[index - 1] + (values[index] - values[index - 1]) * eased);
    }

    private static double bezier(double progress, double x1, double y1, double x2, double y2) {
        double x = Math.max(0, Math.min(1, progress));
        double low = 0, high = 1, t = x;
        for (int iteration = 0; iteration < 12; iteration++) {
            double inverse = 1 - t;
            double value = 3 * inverse * inverse * t * x1 + 3 * inverse * t * t * x2 + t * t * t;
            double error = value - x;
            if (Math.abs(error) < .00001) break;
            if (error > 0) high = t; else low = t;
            double derivative = 3 * inverse * inverse * x1 + 6 * inverse * t * (x2 - x1) + 3 * t * t * (1 - x2);
            double next = derivative > .00001 ? t - error / derivative : Double.NaN;
            t = Double.isFinite(next) && next > low && next < high ? next : (low + high) * .5;
        }
        double inverse = 1 - t;
        return 3 * inverse * inverse * t * y1 + 3 * inverse * t * t * y2 + t * t * t;
    }

    static Plan prepare(List<LyricsLine.Syllable> sources) {
        return new Plan(sources);
    }

    static final class Plan {
        private final List<Glyph> glyphs;
        private final Unit[] byGlyph;
        private final int[] byCodePoint;

        Plan(List<LyricsLine.Syllable> sources) {
            glyphs = sourceGlyphs(sources);
            byGlyph = new Unit[glyphs.size()];
            int length = glyphs.isEmpty() ? 0 : glyphs.get(glyphs.size() - 1).endOffset;
            byCodePoint = new int[length];
            Map<Integer, Bounds> bounds = new HashMap<>();
            for (int index = 0; index < glyphs.size(); index++) {
                Glyph glyph = glyphs.get(index);
                for (int offset = glyph.offset; offset < glyph.endOffset; offset++) byCodePoint[offset] = index;
                Bounds source = bounds.get(glyph.key);
                if (source == null) { source = new Bounds(); bounds.put(glyph.key, source); }
                source.start = Math.min(source.start, glyph.start);
                source.end = Math.max(source.end, glyph.end);
            }
            List<Unit> units = new ArrayList<>();
            Unit current = null;
            for (int index = 0; index < glyphs.size(); index++) {
                Glyph glyph = glyphs.get(index);
                if (glyph.whitespace) { current = null; continue; }
                if (glyph.end <= glyph.start) continue;
                if (current == null || current.key != glyph.key) {
                    current = new Unit(glyph.key, glyph.start, glyph.end);
                    units.add(current);
                    bounds.get(glyph.key).groups++;
                }
                current.start = Math.min(current.start, glyph.start);
                current.end = Math.max(current.end, glyph.end);
                current.count++;
                byGlyph[index] = current;
            }
            for (Unit unit : units) {
                Bounds source = bounds.get(unit.key);
                // A trailing space sustains one syllable; phrase-wide source units
                // containing separate words must not hold all earlier words aloft.
                if (source.groups == 1) { unit.start = source.start; unit.end = source.end; }
                unit.cadence = (unit.end - unit.start) / Math.sqrt(unit.count);
            }
            for (int index = 0; index < units.size(); index++) {
                Unit unit = units.get(index);
                double sum = 0, lastStart = Double.NaN;
                int count = 0;
                for (int neighbor = Math.max(0, index - 2); neighbor <= Math.min(units.size() - 1, index + 2); neighbor++) {
                    Unit nearby = units.get(neighbor);
                    if (nearby.start == lastStart) continue;
                    lastStart = nearby.start;
                    sum += Math.min(600, nearby.cadence);
                    count++;
                }
                unit.localCadence = unit.cadence * .75 + sum / Math.max(1, count) * .25;
                unit.gap = Math.max(0, (index + 1 < units.size() ? units.get(index + 1).start : unit.end) - unit.end);
            }
        }

        /** Offsets refer to the source text, so wrapping and ruby never restart the motion clock. */
        Profile profile(int codePointOffset, int codePointLength, long fillStartMs, long fillEndMs) {
            if (codePointOffset < 0 || codePointOffset >= byCodePoint.length || codePointLength <= 0
                    || fillEndMs <= fillStartMs) return null;
            int first = byCodePoint[codePointOffset];
            int last = byCodePoint[(int) Math.min(byCodePoint.length - 1L,
                    (long) codePointOffset + codePointLength - 1)];
            Unit unit = byGlyph[first];
            if (unit == null) return null;
            double cadence = 0;
            int count = 0;
            for (int index = first; index <= last; index++) {
                if (byGlyph[index] != null) { cadence += byGlyph[index].localCadence; count++; }
            }
            int charCount = last - first + 1;
            double sustain = charCount == 1 && unit.count <= 24
                    ? smooth((unit.end - unit.start - 750) / 650) * smooth((unit.cadence - 170) / 200)
                    : 0;
            Unit lastUnit = byGlyph[last] == null ? unit : byGlyph[last];
            boolean wholeUnit = unit == lastUnit && charCount >= unit.count;
            double holdEnd = wholeUnit ? Math.max(fillEndMs, unit.end)
                    : fillEndMs + Math.max(0, unit.end - fillEndMs) * sustain;
            double gap = holdEnd >= lastUnit.end ? lastUnit.gap : 0;
            return new Profile(fillStartMs, fillEndMs, cadence / Math.max(1, count), gap, holdEnd);
        }
    }

    private static List<Glyph> sourceGlyphs(List<LyricsLine.Syllable> sources) {
        List<Glyph> pieces = new ArrayList<>();
        if (sources == null || sources.isEmpty()) return pieces;
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        StringBuilder fullText = new StringBuilder();
        int offset = 0;
        for (int key = 0; key < sources.size(); key++) {
            LyricsLine.Syllable source = sources.get(key);
            if (source == null || source.text == null || source.text.isEmpty()) continue;
            List<String> chars = graphemes(source.text, iterator);
            double duration = Math.max(1, (source.endTimeMs - source.startTimeMs) / (double) chars.size());
            for (int index = 0; index < chars.size(); index++) {
                String text = chars.get(index);
                int length = text.codePointCount(0, text.length());
                pieces.add(new Glyph(offset, offset + length, source.startTimeMs + index * duration,
                        source.startTimeMs + (index + 1) * duration, key, whitespace(text)));
                offset += length;
            }
            fullText.append(source.text);
        }
        // Source boundaries may split combining marks. Coalesce timing metadata
        // across the complete text without changing the renderer's glyph shaping.
        List<Glyph> result = new ArrayList<>();
        int pieceIndex = 0;
        offset = 0;
        for (String text : graphemes(fullText.toString(), iterator)) {
            int end = offset + text.codePointCount(0, text.length());
            while (pieceIndex < pieces.size() && pieces.get(pieceIndex).endOffset <= offset) pieceIndex++;
            if (pieceIndex >= pieces.size()) break;
            Glyph first = pieces.get(pieceIndex);
            double startTime = first.start, endTime = first.end;
            for (int index = pieceIndex + 1; index < pieces.size() && pieces.get(index).offset < end; index++) {
                startTime = Math.min(startTime, pieces.get(index).start);
                endTime = Math.max(endTime, pieces.get(index).end);
            }
            result.add(new Glyph(offset, end, startTime, endTime, first.key, whitespace(text)));
            offset = end;
        }
        return result;
    }

    private static List<String> graphemes(String text, BreakIterator iterator) {
        List<String> result = new ArrayList<>();
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String part = text.substring(start, end);
            String previous = result.isEmpty() ? "" : result.get(result.size() - 1);
            int point = part.codePointAt(0);
            int type = Character.getType(point);
            boolean joinsPrevious = !previous.isEmpty() && (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK
                    || point == 0x200d || previous.endsWith("\u200d")
                    || (point >= 0x1f3fb && point <= 0x1f3ff)
                    || (point >= 0xfe00 && point <= 0xfe0f) || (point >= 0xe0100 && point <= 0xe01ef));
            if (joinsPrevious) result.set(result.size() - 1, previous + part);
            else result.add(part);
        }
        return result;
    }

    private static boolean whitespace(String text) {
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset);
            if (!Character.isWhitespace(point) && !Character.isSpaceChar(point)) return false;
            offset += Character.charCount(point);
        }
        return true;
    }

    private static final class Glyph {
        final int offset, endOffset, key;
        final double start, end;
        final boolean whitespace;
        Glyph(int offset, int endOffset, double start, double end, int key, boolean whitespace) {
            this.offset = offset; this.endOffset = endOffset; this.start = start; this.end = end;
            this.key = key; this.whitespace = whitespace;
        }
    }

    private static final class Bounds {
        double start = Double.POSITIVE_INFINITY, end = Double.NEGATIVE_INFINITY;
        int groups;
    }

    private static final class Unit {
        final int key;
        double start, end, cadence, localCadence, gap;
        int count;
        Unit(int key, double start, double end) { this.key = key; this.start = start; this.end = end; }
    }
}
