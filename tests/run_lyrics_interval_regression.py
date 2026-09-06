#!/usr/bin/env python3
"""Exercise production playback/look-ahead interval caching against an uncached timeline oracle."""
from pathlib import Path
import hashlib
import subprocess
from regression_runtime import SHARED, REPORTS, java_tool

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

production = "\n".join(declaration(signature) for signature in [
    "private int findActiveDisplayIndexAt(",
    "private int findVisualCenterDisplayIndex(",
    "private void invalidateDisplayLineCache()",
])
work = REPORTS / "lyrics-interval"
work.mkdir(parents=True, exist_ok=True)
java_file = work / "LyricsIntervalRegression.java"
java_file.write_text(Path(__file__).with_name("LyricsIntervalRegression.java.in").read_text().replace("// INSERT_PRODUCTION_DECLARATIONS", production))
subprocess.run([java_tool("javac"), "-d", str(work), str(java_file)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "LyricsIntervalRegression"], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
report = f"Production declarations SHA256: {hashlib.sha256(production.encode()).hexdigest()}\n" + result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
