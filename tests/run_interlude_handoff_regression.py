#!/usr/bin/env python3
"""Exercise the real full-page and preview interlude-retention methods without Android rasterization."""
from pathlib import Path
import subprocess
from regression_runtime import SHARED, REPORTS, java_tool

def declaration(source, signature):
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]

lyrics = (SHARED / "LyricsView.java").read_text()
preview = (SHARED / "MainLyricPreviewView.java").read_text()
production = "\n".join(declaration(lyrics, signature) for signature in (
    "private void captureExitingInterlude(", "private void drawExitingInterlude(",
    "private static final class InterludeExit {"))
preview_production = "\n".join(declaration(preview, signature) for signature in (
    "private void updatePreviewInterludeExit(", "private static final class PreviewInterlude {"))
work = REPORTS / "interlude-handoff"
work.mkdir(parents=True, exist_ok=True)
fixture = Path(__file__).with_name("InterludeHandoffRegression.java.in").read_text()
fixture = fixture.replace("// LYRICS_PRODUCTION", production).replace("// PREVIEW_PRODUCTION", preview_production)
target = work / "InterludeHandoffRegression.java"
target.write_text(fixture)
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-d", str(work),
                str(SHARED / "InterludeTransition.java"), str(target)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "kr.ivlis.ivlyricsandroid.InterludeHandoffRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)

