#!/usr/bin/env python3
"""Pronunciation-first value swap, unit timing split and AI response handling with synthetic lyrics; no network."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "pronunciation-first"
work.mkdir(parents=True, exist_ok=True)
fixture = work / "PronunciationFirstRegression.java"
fixture.write_text((ROOT / "tests/PronunciationFirstRegression.java.in").read_text())
files = [SHARED / name for name in (
    "PronunciationFirst.java", "LyricsTimingUpgrade.java", "LrcRedLyricsProvider.java", "LyricsProviderSettings.java",
    "LyricsProviderSelectionPlan.java", "LyricsResult.java", "LyricsLine.java",
)] + [fixture]
cp = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-nowarn", "-cp", cp, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.PronunciationFirstRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
print(result.stdout, end="")
raise SystemExit(result.returncode)
