#!/usr/bin/env python3
"""Exercise furigana cache invalidation and loading completion without WebView/devices."""
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "furigana-loading"
work.mkdir(parents=True, exist_ok=True)


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


activity = SHARED / "BaseLyricsActivity.java"
source = activity.read_text()
test_file = work / "FuriganaLoadingRegression.java"
test_file.write_text((ROOT / "tests/FuriganaLoadingRegression.java.in").read_text()
                     .replace("// INSERT_SUPPLEMENT_LOADING_TEXT", declaration(source, "private String supplementLoadingText()"))
                     .replace("// INSERT_VINYL_LOADING_TEXT", declaration(source, "private String vinylLoadingText()")))
files = [SHARED / name for name in ("FuriganaRepository.java", "LyricsLine.java", "LyricsResult.java", "LyricsDiskCache.java")]
files += [ROOT / "tests/android/os/Handler.java", ROOT / "tests/android/os/Looper.java", test_file]
test_classpath = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", test_classpath, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", test_classpath,
                         "kr.ivlis.ivlyricsandroid.FuriganaLoadingRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
report = "Production furigana callbacks and loading labels; synthetic cache/results, controlled timeout queue, no WebView/network/device.\n"
for file in files[:4] + [activity]:
    report += f"{file.name} SHA256 {hashlib.sha256(file.read_bytes()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
