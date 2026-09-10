#!/usr/bin/env python3
"""Exercise production overlap activation and interlude display logic with synthetic rows."""
from pathlib import Path
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, compiled_classes, classpath, java_tool

source = (SHARED / "LyricsView.java").read_text()

def declaration(signature):
    assert source.count(signature) == 1, signature
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]

production = "\n".join(declaration(signature) for signature in (
    "private List<DisplayLine> buildDisplayLines()",
    "private LineVisualState lineVisualState(",
    "private long displayLineContentEndTime(",
    "private InterludeInfo interludeInfoForLine(",
    "private InterludeInfo trailingInterludeInfo(",
    "private long nextRenderableLineStartAfter(",
    "private boolean hasRenderableInterludeMarkerBeforeNextRenderableLine(",
    "private long lastLyricEndTime(",
    "private long maxSyllableEnd(",
    "private boolean isPositionInside(",
    "private String instrumentalKind(",
    "private long cacheIntervalStart(",
    "private long cacheIntervalEnd(",
    "private boolean hasVisibleInterludeOverlap(",
    "private boolean interludesOverlap(",
    "private static final class DisplayLine {",
    "private static final class LineVisualState {",
    "private static final class InterludeInfo {",
))
work = REPORTS / "renderer-overlap"
work.mkdir(parents=True, exist_ok=True)
test = work / "RendererOverlapRegression.java"
test.write_text(Path(__file__).with_name("RendererOverlapRegression.java.in").read_text()
                .replace("// INSERT_PRODUCTION_DECLARATIONS", production))
dependencies = classpath(work, compiled_classes("shared"))
subprocess.run([java_tool("javac"), "-cp", dependencies, "-d", str(work),
                str(SHARED / "LyricsLine.java"), str(test)], check=True)
result = subprocess.run([java_tool("java"), "-cp", dependencies,
                         "kr.ivlis.ivlyricsandroid.RendererOverlapRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
