#!/usr/bin/env python3
"""Run production LyricsView remapping/geometry code without Android or a device.

Only source declarations are extracted; no remapping, easing or row-distance
algorithm is reimplemented. Fixed-height rows isolate scroll coordinates from
font rasterization. Generated files stay under build/reports/regressions.
"""
from pathlib import Path
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, java_tool

SOURCE = SHARED / "LyricsView.java"
WORK = REPORTS / "lyrics-center"
TEMPLATE = Path(__file__).with_name("LyricsCenterRegression.java.in")


def declaration(source, signature):
    if source.count(signature) != 1:
        raise RuntimeError(f"Expected one production declaration: {signature}")
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    cursor = opening + 1
    while depth:
        if cursor >= len(source):
            raise RuntimeError(f"Unclosed declaration: {signature}")
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


source = SOURCE.read_text()
signatures = [
    "static final class DisplayIndexMapping {",
    "static final class RowReflow {",
    "private void remapDisplayCenter(List<DisplayLine> previous, List<DisplayLine> next)",
    "private void scheduleRowReflow(List<DisplayLine> previous, List<DisplayLine> next,",
    "private void prepareRowReflow()",
    "private float rowReflowRemaining()",
    "private void clearRowReflow()",
    "void setPlaybackPosition(long positionMs)",
    "void setResult(LyricsResult result)",
    "protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight)",
    "private void invalidateFrameGroupCache()",
    "private static boolean sameDisplayIdentity(DisplayLine first, DisplayLine second)",
    "private void updateAnimatedCenter(int activeIndex, long durationMs)",
    "private float lyricsCenteringProgress(float progress)",
    "private float cubicBezierCoordinate(float t, float first, float second)",
    "private float cubicBezierDerivative(float t, float first, float second)",
    "private float clamp(float value)",
    "private LineLayout layoutAt(List<LineLayout> layouts, int index)",
    "private void prepareAnchorOffsets(List<LineLayout> layouts, int anchorIndex, float blockGap)",
    "private float distanceBetween(LineLayout previous, LineLayout next, float blockGap)",
]
production = "\n\n".join(declaration(source, signature) for signature in signatures)
baseline = subprocess.check_output(["git", "show", "3f238d1a503b2b400cbebd369857428ab837ae83:shared/src/main/java/kr/ivlis/ivlyricsandroid/LyricsView.java"], cwd=ROOT, text=True)
production += "\n" + declaration(baseline, "private float offsetFromAnchor(List<LineLayout> layouts, int anchorIndex, int targetIndex, float blockGap)")
production = production.replace("return previous.height * 0.5f + blockGap + next.height * 0.5f;", "distanceCalls++; return previous.height * 0.5f + blockGap + next.height * 0.5f;")
template = TEMPLATE.read_text()
if template.count("// INSERT_PRODUCTION_DECLARATIONS") != 1:
    raise RuntimeError("Missing production insertion point")
generated = template.replace("// INSERT_PRODUCTION_DECLARATIONS", production)
WORK.mkdir(parents=True, exist_ok=True)
java_file = WORK / "LyricsCenterRegression.java"
java_file.write_text(generated)
subprocess.run([java_tool("javac"), "-d", str(WORK), str(java_file)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(WORK), "LyricsCenterRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
report = (f"Source: {SOURCE}\n"
          f"Source SHA256: {hashlib.sha256(source.encode()).hexdigest()}\n"
          f"Production declarations SHA256: {hashlib.sha256(production.encode()).hexdigest()}\n"
          "Scope: production remap/reflow/easing/row-distance/cleanup functions; fixed block heights, no Android rasterization.\n"
          + result.stdout)
(WORK / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
