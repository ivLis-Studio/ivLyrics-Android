#!/usr/bin/env python3
"""Compile/run bounded public-metadata regressions after a module build.
Optional args: downloaded sync-data API response JSON and its source LRCLIB JSON.
No account, Android device, or app-private files are read.
"""
from pathlib import Path
import hashlib
import subprocess
import sys
from regression_runtime import ROOT, SHARED, MODULE, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "native-metadata"
classes = work / "classes"
classes.mkdir(parents=True, exist_ok=True)
test_classpath = classpath(classes, json_jar(), compiled_classes("spotify-module"),
                           compiled_classes("shared"), android_jar())
files = [SHARED / name for name in ["NowPlayingService.java", "TrackSnapshot.java", "LyricsProviderSelectionPlan.java", "LyricsResult.java", "SyncDataApplier.java", "LyricsRepository.java"]]
files += [MODULE / name for name in ["NativeTrackIdentity.java", "SpotifyMetadataBridge.java", "SpotifyNativeMetadataClient.java"]]
files += [ROOT / "tests/android/os/Handler.java", ROOT / "tests/android/os/Looper.java",
          ROOT / "tests/kr/ivlis/ivlyricsandroid/NativeMetadataRegression.java"]
subprocess.run([java_tool("javac"), "-cp", test_classpath, "-d", str(classes), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", test_classpath,
                         "kr.ivlis.ivlyricsandroid.NativeMetadataRegression", *sys.argv[1:]],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = "Production native metadata code; synthetic in-memory fixtures.\n"
for file in files[:9]:
    report += f"{file.relative_to(ROOT)} SHA256 {hashlib.sha256(file.read_bytes()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
