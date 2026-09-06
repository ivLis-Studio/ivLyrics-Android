#!/usr/bin/env python3
"""Synthetic provider/caching checks against current production Java; no API calls."""
from pathlib import Path
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

source = SHARED
work = REPORTS / "provider-attribution"
work.mkdir(parents=True, exist_ok=True)
compiled = compiled_classes("shared")
json_dependency = json_jar()
android = android_jar()

def declaration(text, signature):
    if text.count(signature) != 1:
        raise ValueError(f"Expected one source declaration: {signature}")
    start = text.index(signature)
    cursor = text.index("{", start) + 1
    depth = 1
    while depth:
        depth += (text[cursor] == "{") - (text[cursor] == "}")
        cursor += 1
    return text[start:cursor]

main = (source / "BaseLyricsActivity.java").read_text()
loading = declaration(main, "private String aiProviderLoadingText(String formatKey, String fallbackKey)")
guard = declaration(main, "private boolean current(String callbackTrackKey)")
template = Path(__file__).with_name("ProviderAttributionRegression.java.in").read_text()
test_file = work / "ProviderAttributionRegression.java"
test_file.write_text(template.replace("// INSERT_LOADING_METHOD", loading).replace("// INSERT_GENERATION_GUARD", guard))
files = [source / name for name in ("LyricsLine.java", "LyricsResult.java", "LyricsDiskCache.java", "AiLyricsRepository.java")]
files += [ROOT / "tests/android/os/Handler.java", ROOT / "tests/android/os/Looper.java", test_file]
test_classpath = classpath(work, json_dependency, compiled, android)
subprocess.run([java_tool("javac"), "-cp", test_classpath, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", test_classpath, "kr.ivlis.ivlyricsandroid.ProviderAttributionRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = "Production Java, synthetic in-memory rows/settings, no network/account/device access.\n"
for file in files[:4] + [source / "BaseLyricsActivity.java"]:
    report += f"{file.name} SHA256 {hashlib.sha256(file.read_bytes()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
