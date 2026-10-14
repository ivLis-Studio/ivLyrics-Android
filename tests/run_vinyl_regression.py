#!/usr/bin/env python3
"""Replay production LP drawing bounds, metadata and spin wiring using synthetic Android collaborators."""
from pathlib import Path
import subprocess
from regression_runtime import SHARED, REPORTS, java_tool

source = (SHARED / "VinylPlayerModeView.java").read_text()
def declaration(signature):
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]
work = REPORTS / "vinyl"
work.mkdir(parents=True, exist_ok=True)
production = "\n".join(declaration(signature) for signature in (
    "private void drawSafeRecordText(", "private void updateSpin()", "        void setDisplayMetadata("))
target = work / "VinylRendererRegression.java"
target.write_text(Path(__file__).with_name("VinylRendererRegression.java.in").read_text().replace("// PRODUCTION", production))
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-d", str(work),
                str(SHARED / "RecordRotation.java"), str(target)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "kr.ivlis.ivlyricsandroid.VinylRendererRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)

