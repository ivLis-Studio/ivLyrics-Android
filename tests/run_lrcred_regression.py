#!/usr/bin/env python3
"""Production lrc.red parsing, provider defaults and timing carry-over with synthetic input; no network."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "lrcred"
work.mkdir(parents=True, exist_ok=True)
fixture = work / "LrcRedRegression.java"
fixture.write_text((ROOT / "tests/LrcRedRegression.java.in").read_text())
files = [SHARED / name for name in (
    "LrcRedLyricsProvider.java", "LyricsTimingUpgrade.java", "LyricsProviderSettings.java",
    "LyricsProviderSelectionPlan.java", "LyricsResult.java", "LyricsLine.java",
)] + [fixture]
cp = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-nowarn", "-cp", cp, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.LrcRedRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
print(result.stdout, end="")
raise SystemExit(result.returncode)

