#!/usr/bin/env python3
"""Check native motion against captured desktop production output and renderer gates.

JDK only. This checks timing/motion math and production gate/split declarations;
it does not claim Android rasterization, display pacing, or live-song acceptance.
"""
import hashlib
import json
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, java_tool


def declaration(source, signature):
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


fixture_path = ROOT / "tests/fixtures/karaoke-desktop-6.6.8.json"
fixture = json.loads(fixture_path.read_text())
source_path = SHARED / "KaraokeMotion.java"
view_source = (SHARED / "LyricsView.java").read_text()
production = "\n".join(declaration(view_source, signature) for signature in (
    "private KaraokeMotion.Values karaokeBounce(",
    "private KaraokeMotion.Plan prepareMotionPlan(",
    "private TextSegment createSplitSegment(",
))
cases = []
for index, record in enumerate(fixture["records"]):
    units = ",".join(f"new LyricsLine.Syllable({json.dumps(text, ensure_ascii=False)},{start}L,{end}L)"
                     for text, start, end in record["units"])
    segments = []
    for segment in record["segments"]:
        profile = ",".join(map(repr, segment["profile"]))
        samples = ",".join("{" + ",".join(map(repr, sample)) + "}" for sample in segment["samples"])
        segments.append(f"new Fixture({segment['offset']},{segment['length']},{segment['fillStart']}L,"
                        f"{segment['fillEnd']}L,new double[]{{{profile}}},new double[][]{{{samples}}})")
    cases.append(f"static void case{index}() {{ checkCase({json.dumps(record['name'])},"
                 f"List.of({units}),new Fixture[]{{{','.join(segments)}}}); }}")

work = REPORTS / "karaoke-motion"
work.mkdir(parents=True, exist_ok=True)
java_file = work / "KaraokeMotionRegression.java"
java_file.write_text(r'''
package kr.ivlis.ivlyricsandroid;
import java.util.*;
public final class KaraokeMotionRegression {
    static int assertions, profiles, samples;
    static double maxDesktopOffsetError, maxDesktopScaleError;
    static void check(String label, boolean valid) {
        assertions++;
        if (!valid) throw new AssertionError(label);
    }
    static void close(String label, double expected, double actual, double tolerance) {
        check(label + " expected " + expected + " got " + actual, Math.abs(expected - actual) <= tolerance);
    }
    record Fixture(int offset, int length, long start, long end, double[] profile, double[][] samples) { }
    static void checkCase(String name, List<LyricsLine.Syllable> units, Fixture[] fixtures) {
        KaraokeMotion.Plan plan = KaraokeMotion.prepare(units);
        KaraokeMotion.Values result = new KaraokeMotion.Values();
        for (Fixture fixture : fixtures) {
            KaraokeMotion.Profile profile = plan.profile(fixture.offset, fixture.length, fixture.start, fixture.end);
            check(name + " profile exists", profile != null);
            double[] actual = {profile.startTime, profile.endTime, profile.riseDuration, profile.releaseDuration,
                    profile.amplitude, profile.scaleAmount};
            for (int index = 0; index < actual.length; index++) {
                close(name + " profile field " + index, fixture.profile[index], actual[index], .000001);
            }
            profiles++;
            for (double[] sample : fixture.samples) {
                long time = (long) sample[0];
                check("Evaluation reuses output", KaraokeMotion.evaluate(profile, time, 44f, result) == result);
                maxDesktopOffsetError = Math.max(maxDesktopOffsetError, Math.abs(sample[1] - result.offsetY));
                maxDesktopScaleError = Math.max(maxDesktopScaleError, Math.abs(sample[2] - result.scale));
                // Desktop rounds targets to .25px/.002 then CSS interpolates them for75ms.
                // Native Canvas samples the continuous source envelope on display frames.
                close(name + " desktop lift", sample[1], result.offsetY, .125001);
                close(name + " desktop scale", sample[2], result.scale, .001001);
                float originalY = result.offsetY, originalScale = result.scale;
                for (float size : new float[]{12f, 25f, 50f, 88f}) {
                    KaraokeMotion.evaluate(profile, time, size, result);
                    close(name + " font-relative lift", originalY * size / 44, result.offsetY, .00001);
                    close(name + " font-independent scale", originalScale, result.scale, .000001);
                }
                // A seek or a different simultaneous vocal cannot pollute this profile's result.
                KaraokeMotion.evaluate(profile, time + 30000, 44, result);
                KaraokeMotion.evaluate(profile, time, 44, result);
                close(name + " deterministic after seek", originalY, result.offsetY, .000001);
                samples++;
            }
            KaraokeMotion.evaluate(profile, (long) Math.ceil(profile.endTime + profile.releaseDuration), 44, result);
            check(name + " complete release is exactly idle", !result.active && result.offsetY == 0 && result.scale == 1);
        }
    }

    private boolean frameAnimationsEnabled = true, lineMode, karaokeBounceEffectEnabled = true;
    private long positionMs;
    private final KaraokeMotion.Values karaokeBounceResult = new KaraokeMotion.Values();
    private boolean isLineDisplayGranularity() { return lineMode; }
    private static final class DrawGroup { float textSize = 25f; }
    private static final class TextSegment {
        String text, rubyText = "";
        int sourceIndex, sourceLength;
        long startTimeMs, endTimeMs, fillStartTimeMs, fillEndTimeMs;
        KaraokeMotion.Profile motionProfile;
        TextSegment(String text, int offset, int length, long start, long end) {
            this.text = text; sourceIndex = offset; sourceLength = length;
            startTimeMs = fillStartTimeMs = start; endTimeMs = fillEndTimeMs = end;
        }
        TextSegment withFillTiming(long start, long end) { fillStartTimeMs = start; fillEndTimeMs = end; return this; }
        LyricsLine.Syllable styleSyllable() { return new LyricsLine.Syllable(text, startTimeMs, endTimeMs); }
    }
    private TextSegment createMeasuredSegment(String text, long start, long end, int offset,
                                              int length, String ruby, LyricsLine.Syllable style) {
        return new TextSegment(text, offset, length, start, end);
    }
    private String rubyForSplitSegment(TextSegment source, int offset, int length) { return ""; }
''' + production + r'''
    void renderContracts() {
        List<LyricsLine.Syllable> sources = List.of(new LyricsLine.Syllable("아", 1000, 2800));
        TextSegment segment = new TextSegment("아", 0, 1, 1000, 2800);
        segment.motionProfile = prepareMotionPlan(List.of(segment), sources).profile(0, 1, 1000, 2800);
        DrawGroup lead = new DrawGroup(), secondVoice = new DrawGroup();
        positionMs = 2000;
        float lifted = karaokeBounce(segment, lead).offsetY;
        check("Held source remains lifted through its own end", lifted < -2f);
        close("Multivocal strength is equal", lifted, karaokeBounce(segment, secondVoice).offsetY, .000001);
        lineMode = true;
        check("Line mode has no timed motion", !karaokeBounce(segment, lead).active);
        lineMode = false; karaokeBounceEffectEnabled = false;
        check("User-disabled bounce has no motion", !karaokeBounce(segment, lead).active);
        karaokeBounceEffectEnabled = true; frameAnimationsEnabled = false;
        check("Reduced motion has no timed movement", !karaokeBounce(segment, lead).active);
        frameAnimationsEnabled = true; positionMs = 900;
        check("Backward seek before onset is idle", !karaokeBounce(segment, lead).active);
        positionMs = 2000;
        close("Seek returns to original source envelope", lifted, karaokeBounce(segment, lead).offsetY, .000001);
        TextSegment word = new TextSegment("abcdefgh", 7, 8, 1000, 2600);
        TextSegment wrapped = createSplitSegment(word, List.of("e", "f", "g", "h"), 4);
        check("Wrapped piece keeps absolute source offset", wrapped.sourceIndex == 11 && wrapped.sourceLength == 4);
        check("Wrapped piece preserves interpolated source/fill timing", wrapped.startTimeMs == 1800
                && wrapped.endTimeMs == 2600 && wrapped.fillStartTimeMs == 1800 && wrapped.fillEndTimeMs == 2600);
        check("Split metadata does not mutate original timing", word.sourceIndex == 7 && word.startTimeMs == 1000
                && word.fillStartTimeMs == 1000 && word.endTimeMs == 2600);
        KaraokeMotion.Plan synthetic = prepareMotionPlan(List.of(new TextSegment("가", 0, 1, 0, 80),
                new TextSegment("나", 1, 1, 80, 160)), Collections.emptyList());
        check("Synthetic rapid characters retain low cadence", synthetic.profile(0, 1, 0, 80).amplitude < 1.2);
    }

    static void effectContracts() {
        close("Desktop wave upward keyframe", -.11 * 44, KaraokeMotion.waveOffset(322, 0, 44), .0001);
        close("Desktop wave downward keyframe", .03 * 44, KaraokeMotion.waveOffset(644, 0, 44), .0001);
        close("Desktop wave even-child delay", KaraokeMotion.waveOffset(120, 0, 44),
                KaraokeMotion.waveOffset(0, 1, 44), .000001);
        close("Desktop wave third-child delay wins", KaraokeMotion.waveOffset(240, 0, 44),
                KaraokeMotion.waveOffset(0, 5, 44), .000001);
        close("Desktop adlib peak at half-cycle", -1.5, KaraokeMotion.adlibOffset(525, 44), .000001);
        close("Adlib normalized to native text size", -1.5 * 25 / 44, KaraokeMotion.adlibOffset(525, 25), .000001);
        check("Adlib never adds downward movement", KaraokeMotion.adlibOffset(800, 44) <= 0);
        close("Desktop pulse keyframe", 1.025, KaraokeMotion.pulseScale(423), .00001);
        close("Desktop sway left keyframe", -1, KaraokeMotion.swayStrength(405), .00001);
        close("Desktop sway right keyframe", 1, KaraokeMotion.swayStrength(945), .00001);
        close("Desktop float half-cycle", 1, KaraokeMotion.floatStrength(825), .000001);
        for (int time = 0; time < 1080; time++) {
            float scale = KaraokeMotion.popScale(time);
            check("Pop remains within desktop scale range", scale >= .99599f && scale <= 1.03501f);
            check("Pop has continuous keyframe transitions", Math.abs(KaraokeMotion.popScale(time + 1) - scale) < .002f);
        }
        for (int time = 0; time < 920; time++) {
            float y = KaraokeMotion.waveOffset(time, 0, 44);
            check("Wave bounded by desktop em amplitudes", y >= -.11f * 44 - .0001f && y <= .03f * 44 + .0001f);
        }
        float before = KaraokeMotion.bounceOffset(779, 44), after = KaraokeMotion.bounceOffset(780, 44);
        check("Bounce wraps continuously", Math.abs(before - after) < .001f);
    }
''' + "\n".join(cases) + r'''
    public static void main(String[] args) {
''' + "\n".join(f"        case{index}();" for index in range(len(cases))) + r'''
        new KaraokeMotionRegression().renderContracts();
        effectContracts();
        System.out.println("PASS desktop source fixtures: " + profiles + " profiles, " + samples + " sampled positions");
        System.out.println("Maximum desktop quantization difference: " + maxDesktopOffsetError + " CSS px / "
                + maxDesktopScaleError + " scale");
        System.out.println("PASS font normalization, held syllables, rapid phrases, multi-vocal equality, seek, display/reduced-motion gates");
        System.out.println("PASS wrapped source offsets and fill timing; desktop wave/adlib/pulse/sway/float/pop/bounce envelopes");
        System.out.println("KARAOKE_MOTION_REGRESSION_PASSED assertions=" + assertions);
    }
}
''')
stub = work / "LyricsLine.java"
stub.write_text("""package kr.ivlis.ivlyricsandroid;
final class LyricsLine { static final class Syllable {
    final String text; final long startTimeMs, endTimeMs;
    Syllable(String text, long start, long end) { this.text=text; startTimeMs=start; endTimeMs=end; }
} }
""")
subprocess.run([java_tool("javac"), "--release", "17", "-d", str(work), str(source_path),
                str(stub), str(java_file)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "kr.ivlis.ivlyricsandroid.KaraokeMotionRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = ("Scope: production motion helper and renderer gates; no Android rasterization/device assertions.\n"
          f"Desktop reference SHA256: {fixture['sourceSha256']}\n"
          f"KaraokeMotion.java SHA256: {hashlib.sha256(source_path.read_bytes()).hexdigest()}\n"
          f"LyricsView declarations SHA256: {hashlib.sha256(production.encode()).hexdigest()}\n"
          + result.stdout)
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
