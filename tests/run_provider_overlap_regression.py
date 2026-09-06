#!/usr/bin/env python3
"""Run real provider parsers with synthetic overlap fixtures, optionally checking a local report TTML."""
import subprocess
import sys
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "provider-overlap"
work.mkdir(parents=True, exist_ok=True)
sources = [SHARED / name for name in (
    "LyricsLine.java", "UnisonLyricsProvider.java", "LyricsPlusLyricsProvider.java", "PaxsenixLyricsProvider.java")]
sources.append(ROOT / "tests/kr/ivlis/ivlyricsandroid/ProviderOverlapRegression.java")
dependencies = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", dependencies, "-d", str(work), *map(str, sources)], check=True)
result = subprocess.run([java_tool("java"), "-cp", dependencies,
                         "kr.ivlis.ivlyricsandroid.ProviderOverlapRegression", *sys.argv[1:]],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
