#!/usr/bin/env python3
"""Compare real renderer metadata against cf69ad2, without Android rasterization.

Only the two counters are injected into verbatim production declarations. The
immutable lyric model and pronunciation comparison execute directly from source.
"""
from pathlib import Path
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, java_tool

BASELINE = "cf69ad2a3183b7d8a67f4549d159a128d0e6a044"
source = (SHARED / "LyricsView.java").read_text()
baseline = subprocess.check_output([
    "git", "show", f"{BASELINE}:shared/src/main/java/kr/ivlis/ivlyricsandroid/LyricsView.java"
], cwd=ROOT, text=True)
work = REPORTS / "lyrics-metadata-cache"
work.mkdir(parents=True, exist_ok=True)


def declaration(value, signature):
    assert value.count(signature) == 1, signature
    start = value.index(signature)
    cursor = value.index("{", start) + 1
    depth = 1
    while depth:
        depth += (value[cursor] == "{") - (value[cursor] == "}")
        cursor += 1
    return value[start:cursor]


def renderer(value, name):
    declarations = "\n".join(declaration(value, signature) for signature in (
        "private String accessibilityLabel(",
        "private void addAccessibilityText(",
        "private String distinctPronunciation(",
        "private long displayLineContentEndTime(",
        "private long lastLyricEndTime(",
        "private long maxSyllableEnd(",
        "private LineVisualState lineVisualState(",
        "private static final class DisplayLine {",
        "private static final class InterludeInfo {",
        "private static final class LineVisualState {",
    ))
    declarations = declarations.replace(
        "LinkedHashSet<String> originals =", "labelBuilds++;\n        LinkedHashSet<String> originals =")
    declarations = declarations.replace(
        "for (LyricsLine.Syllable syllable : syllables) {",
        "for (LyricsLine.Syllable syllable : syllables) {\n            syllableVisits++;")
    return "static final class " + name + " {\n" + COMMON + declarations + "\n}"


COMMON = """
long positionMs, labelBuilds, syllableVisits;
String preludeLabel = "Prelude", breakLabel = "Break", postludeLabel = "Postlude";
List<DisplayLine> display = new ArrayList<>();
static final long KARAOKE_RELEASE_WINDOW_MS = 820L, KARAOKE_COMPLETED_COLOR_FADE_MS = 520L;
static final class RowReflow {}
float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
int findActiveDisplayIndex(List<DisplayLine> values) {
    int active = 0;
    for (int index = 0; index < values.size(); index++) if (values.get(index).startTimeMs() <= positionMs) active = index;
    return active;
}
void load(List<LyricsLine> lines) {
    display = new ArrayList<>();
    for (int index = 0; index < lines.size(); index++) {
        LyricsLine line = lines.get(index);
        display.add(DisplayLine.real(line, index, index, InterludeInfo.none()));
    }
}
void addInterlude(String kind, boolean virtual) {
    InterludeInfo info = new InterludeInfo(true, 9000, 12000, kind, virtual);
    display.add(virtual ? DisplayLine.virtual(0, display.size(), info)
            : DisplayLine.real(new LyricsLine(9000,12000,"♪",List.of()),0,display.size(),info));
}
String label(int index) { return accessibilityLabel(display.get(index)); }
long end(int index) { return displayLineContentEndTime(display.get(index)); }
String visual(int index, long position) {
    positionMs = position;
    LineVisualState state = lineVisualState(display,index);
    return state.highlighted + "|" + state.animating + "|" + Float.floatToIntBits(state.completedColorOpacity);
}
"""

template = Path(__file__).with_name("LyricsMetadataCacheRegression.java.in").read_text()
generated = template.replace("// INSERT_BASELINE", renderer(baseline, "Baseline"))
generated = generated.replace("// INSERT_CANDIDATE", renderer(source, "Candidate"))
java_file = work / "LyricsMetadataCacheRegression.java"
java_file.write_text(generated)
textutils = work / "android/text/TextUtils.java"
textutils.parent.mkdir(parents=True, exist_ok=True)
textutils.write_text('''package android.text;
public final class TextUtils {
    public static String join(CharSequence delimiter, Iterable<?> values) {
        StringBuilder result = new StringBuilder(); boolean first = true;
        for (Object value : values) { if (!first) result.append(delimiter); result.append(value); first = false; }
        return result.toString();
    }
}
''')
subprocess.run([java_tool("javac"), "-d", str(work), str(textutils),
                str(SHARED / "LyricsLine.java"), str(SHARED / "LyricsTextComparison.java"), str(java_file)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work),
                         "kr.ivlis.ivlyricsandroid.LyricsMetadataCacheRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
report = (f"Baseline: {BASELINE}\n"
          f"Candidate LyricsView SHA256: {hashlib.sha256(source.encode()).hexdigest()}\n"
          "Scope: production immutable model, label normalization, content bounds and visual state; no Android Canvas/device.\n"
          + result.stdout)
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
