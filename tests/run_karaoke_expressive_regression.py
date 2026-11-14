#!/usr/bin/env python3
"""Check native expressive karaoke motion against values captured from the desktop renderer.

JDK only. The fixture comes from tests/capture_expressive_fixture.mjs run against the desktop
Pages.js. This checks the motion math, unit classification and the renderer's motion gates; it
does not claim Android rasterization, display pacing, or live-song acceptance.
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


fixture_path = ROOT / "tests/fixtures/karaoke-expressive-desktop.json"
fixture = json.loads(fixture_path.read_text())
engine_path = SHARED / "KaraokeExpressiveMotion.java"
view_source = (SHARED / "LyricsView.java").read_text()
production = "\n".join(declaration(view_source, signature) for signature in (
    "private KaraokeMotion.Values karaokeBounce(",
    "private boolean expressiveMotionActive(",
    "private KaraokeMotion.Values karaokeMotion(",
))

trajectories = []
for trajectory in fixture["trajectories"]:
    samples = ",".join("{" + ",".join(map(repr, sample)) + "}" for sample in trajectory["samples"])
    trajectories.append(f"checkTrajectory({trajectory['duration']},{str(trajectory['emphasis']).lower()},"
                        f"new double[][]{{{samples}}});")
classifications = []
for line in fixture["classifications"]:
    texts = ",".join(json.dumps(char[0], ensure_ascii=False) for char in line["chars"])
    starts = ",".join(repr(float(char[1])) for char in line["chars"])
    ends = ",".join(repr(float(char[2])) for char in line["chars"])
    windows = ",".join("null" if window is None else "new double[]{" + ",".join(map(repr, window)) + "}"
                       for window in line["windows"])
    classifications.append(f"checkLine({json.dumps(line['name'])},List.of({texts}),new double[]{{{starts}}},"
                           f"new double[]{{{ends}}},new double[][]{{{windows}}});")

work = REPORTS / "karaoke-expressive"
work.mkdir(parents=True, exist_ok=True)
java_file = work / "KaraokeExpressiveRegression.java"
java_file.write_text(r'''
package kr.ivlis.ivlyricsandroid;
import java.util.*;
public final class KaraokeExpressiveRegression {
    static int assertions, samples, units;
    static double worstScale, worstOffset, worstGlow;
    static void check(String label, boolean valid) {
        assertions++;
        if (!valid) throw new AssertionError(label);
    }
    static void checkTrajectory(double duration, boolean emphasis, double[][] expected) {
        KaraokeExpressiveMotion.Unit unit = new KaraokeExpressiveMotion.Unit(5000, 5000 + duration, emphasis);
        KaraokeMotion.Values result = new KaraokeMotion.Values();
        for (double[] sample : expected) {
            KaraokeExpressiveMotion.evaluate(unit, 5000 + sample[0], 0, 1f, result);
            String label = duration + "/" + emphasis + " at " + sample[0];
            if (sample[4] == 0) {
                check(label + " settles idle", !result.active && result.scale == 1f && result.offsetY == 0f && result.glow == 0f);
                continue;
            }
            // Desktop rounds scale to 0.001, offset to 0.0005 em and glow to 0.01.
            worstScale = Math.max(worstScale, Math.abs(result.scale - sample[1]));
            worstOffset = Math.max(worstOffset, Math.abs(result.offsetY - sample[2]));
            worstGlow = Math.max(worstGlow, Math.abs(result.glow - sample[3]));
            check(label + " scale " + result.scale + " vs " + sample[1], Math.abs(result.scale - sample[1]) <= .0011);
            check(label + " offset " + result.offsetY + " vs " + sample[2], Math.abs(result.offsetY - sample[2]) <= .0006);
            check(label + " glow " + result.glow + " vs " + sample[3], Math.abs(result.glow - sample[3]) <= .0101);
            samples++;
        }
    }
    static void checkLine(String name, List<String> texts, double[] starts, double[] ends, double[][] windows) {
        KaraokeExpressiveMotion.Unit[] actual = KaraokeExpressiveMotion.units(texts, starts, ends, false);
        for (int index = 0; index < windows.length; index++) {
            if (windows[index] == null) {
                check(name + "[" + index + "] does not move", actual[index] == null);
                continue;
            }
            check(name + "[" + index + "] moves", actual[index] != null);
            check(name + "[" + index + "] window", Math.abs(actual[index].start - windows[index][0]) < .001
                    && Math.abs(actual[index].end - windows[index][1]) < .001);
            check(name + "[" + index + "] emphasis", actual[index].emphasis == (windows[index][2] == 1));
            units++;
        }
    }

    private boolean frameAnimationsEnabled = true, lineMode, karaokeBounceEffectEnabled = true, karaokeExpressiveMotion = true;
    private double framePositionMs;
    private final KaraokeMotion.Values karaokeBounceResult = new KaraokeMotion.Values();
    private boolean isLineDisplayGranularity() { return lineMode; }
    private static final class DrawGroup {
        float textSize = 40f; boolean supplement, active = true; long lineStartTimeMs = 1000, lineEndTimeMs = 3000;
    }
    private static final class TextSegment {
        KaraokeMotion.Profile motionProfile;
        KaraokeExpressiveMotion.Unit expressiveUnit;
    }
''' + production + r'''
    void renderContracts() {
        TextSegment segment = new TextSegment();
        segment.expressiveUnit = new KaraokeExpressiveMotion.Unit(1500, 1900, false);
        DrawGroup lead = new DrawGroup();
        framePositionMs = 760;
        check("No pose before the ease", !karaokeMotion(segment, lead).active);
        framePositionMs = 880;
        KaraokeMotion.Values half = karaokeMotion(segment, lead);
        check("Pose eases in before the line", half.active && Math.abs(half.scale - .975f) < 1e-5
                && Math.abs(half.offsetY - .006f * 40f) < 1e-4);
        framePositionMs = 1200;
        KaraokeMotion.Values pending = karaokeMotion(segment, lead);
        check("Pending units rest smaller and lower", Math.abs(pending.scale - .95f) < 1e-6
                && Math.abs(pending.offsetY - .012f * 40f) < 1e-4 && pending.glow == 0f);
        DrawGroup other = new DrawGroup();
        other.active = false;
        check("Lines not being sung keep pending units idle", !karaokeMotion(segment, other).active);
        framePositionMs = 1960;
        KaraokeMotion.Values moving = karaokeMotion(segment, lead);
        check("A sung unit swells, lifts and glows", moving.scale > 1f && moving.offsetY < 0f && moving.glow > .2f);
        check("Every vocal row moves the same", Math.abs(moving.offsetY - karaokeMotion(segment, other).offsetY) < 1e-6);
        framePositionMs = 3000;
        check("Completed line drops the pose", !karaokeMotion(segment, lead).active);
        framePositionMs = 1960;
        DrawGroup pronunciation = new DrawGroup();
        pronunciation.supplement = true;
        check("Pronunciation and translation rows do not move", !karaokeMotion(segment, pronunciation).active);
        lineMode = true;
        check("Line display has no timed motion", !karaokeMotion(segment, lead).active);
        lineMode = false; karaokeBounceEffectEnabled = false;
        check("Expressive motion needs the bounce", !karaokeMotion(segment, lead).active);
        karaokeBounceEffectEnabled = true; frameAnimationsEnabled = false;
        check("Reduced motion has no expressive movement", !karaokeMotion(segment, lead).active);
        frameAnimationsEnabled = true; karaokeExpressiveMotion = false;
        check("Classic style keeps the classic bounce", !karaokeMotion(segment, lead).active);
        karaokeExpressiveMotion = true;
        framePositionMs = 9000;
        karaokeMotion(segment, lead);
        framePositionMs = 1960;
        KaraokeMotion.Values again = karaokeMotion(segment, lead);
        check("A seek returns to the same pose", Math.abs(again.offsetY - moving.offsetY) < 1e-6
                && Math.abs(again.scale - moving.scale) < 1e-6);
        KaraokeExpressiveMotion.Unit[] words = KaraokeExpressiveMotion.units(List.of("wonderfully", " ", "bright"),
                new double[]{0, 2000, 2100}, new double[]{2000, 2100, 2400}, true);
        check("Word display moves per word", words[0] != null && !words[0].emphasis && words[1] == null
                && words[2].start == 2100);
        KaraokeExpressiveMotion.Unit[] untimed = KaraokeExpressiveMotion.units(List.of("a"),
                new double[]{Double.NaN}, new double[]{Double.NaN}, false);
        check("Untimed text does not move", untimed[0] == null);
    }

    public static void main(String[] args) {
''' + "\n".join("        " + line for line in trajectories + classifications) + r'''
        new KaraokeExpressiveRegression().renderContracts();
        System.out.println("PASS desktop expressive fixtures: " + samples + " sampled positions, " + units + " classified glyphs");
        System.out.println("Maximum desktop difference: scale " + worstScale + ", offset " + worstOffset + " em, glow " + worstGlow);
        System.out.println("PASS pending pose, vocal rows, seeks and display/bounce/reduced-motion/supplement gates");
        System.out.println("KARAOKE_EXPRESSIVE_REGRESSION_PASSED assertions=" + assertions);
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
subprocess.run([java_tool("javac"), "--release", "17", "-encoding", "UTF-8", "-d", str(work), str(engine_path),
                str(SHARED / "KaraokeMotion.java"), str(stub), str(java_file)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "kr.ivlis.ivlyricsandroid.KaraokeExpressiveRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = ("Scope: production expressive motion helper and renderer gates; no Android rasterization/device assertions.\n"
          f"Desktop reference SHA256: {fixture['sourceSha256']}\n"
          f"KaraokeExpressiveMotion.java SHA256: {hashlib.sha256(engine_path.read_bytes()).hexdigest()}\n"
          f"LyricsView declarations SHA256: {hashlib.sha256(production.encode()).hexdigest()}\n"
          + result.stdout)
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
