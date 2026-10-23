#!/usr/bin/env python3
"""Execute the production local-file decoder, LRC parser and override store."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool

work = REPORTS / "local-lyrics"
work.mkdir(parents=True, exist_ok=True)
fixture = work / "LocalLyricsRegression.java"
fixture.write_text((ROOT / "tests/LocalLyricsRegression.java.in").read_text())
cp = classpath(work, compiled_classes("shared"), android_jar())
production = [SHARED / name for name in ("LocalLyricsStore.java", "LrcParser.java", "LyricsLine.java", "LyricsResult.java", "TrackSnapshot.java")]
subprocess.run([java_tool("javac"), "-cp", cp, "-d", str(work), *map(str, production), str(fixture)], check=True)
subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.LocalLyricsRegression"], check=True)
