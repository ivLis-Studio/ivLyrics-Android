#!/usr/bin/env python3
"""Compare real sync GET/parsing against the pre-optimization source using local HTTP fixtures."""
import hashlib
import json
from pathlib import Path
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

BASELINE = "18880143025174e996660f1a92277b5863a1c08b"
work = REPORTS / "sync-metadata-reporting"
work.mkdir(parents=True, exist_ok=True)
production = SHARED / "LyricsRepository.java"
baseline = subprocess.check_output(["git", "show", f"{BASELINE}:shared/src/main/java/kr/ivlis/ivlyricsandroid/LyricsRepository.java"], cwd=ROOT, text=True)
results = {}
reports = []
for name, source in (("baseline", baseline), ("candidate", production.read_text())):
    directory = work / name
    directory.mkdir(parents=True, exist_ok=True)
    repository = directory / "LyricsRepository.java"
    repository.write_text(source)
    fixture = directory / "SyncMetadataReportingRegression.java"
    fixture.write_text((ROOT / "tests/SyncMetadataReportingRegression.java.in").read_text())
    files = [repository, SHARED / "SyncMetadataReportScope.java", SHARED / "TrackSnapshot.java", fixture,
             ROOT / "tests/android/os/Handler.java", ROOT / "tests/android/os/Looper.java"]
    cp = classpath(directory, json_jar(), compiled_classes("shared"), android_jar())
    subprocess.run([java_tool("javac"), "-cp", cp, "-d", str(directory), *map(str, files)], check=True)
    result = subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.SyncMetadataReportingRegression", name],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
    if result.returncode:
        print(result.stdout)
        raise SystemExit(result.returncode)
    (directory / "result.txt").write_text(result.stdout)
    reports.append(f"{name} LyricsRepository.java SHA256 {hashlib.sha256(source.encode()).hexdigest()}\n{result.stdout}")
    results[name] = json.loads(next(line[len("RESULT_JSON "):] for line in result.stdout.splitlines() if line.startswith("RESULT_JSON ")))
assert results["baseline"]["outputs"] == results["candidate"]["outputs"], "Changed sync result payload/selection semantics"
assert results["baseline"]["getCount"] == results["candidate"]["getCount"], "Privacy/lyrics GET count must remain unchanged"
summary = {"baselineCommit": BASELINE, "baseline": results["baseline"], "candidate": results["candidate"], "outputEquivalent": True,
           "method": "Full production fetchSyncData, HTTP transport and JSON parser; synthetic URL handler and operation scope, no external network/device."}
(work / "result.json").write_text(json.dumps(summary, indent=2))
report = "\n".join(reports) + "PASS frozen-baseline output equivalence and unchanged privacy/lyrics GET count.\n"
(work / "result.txt").write_text(report)
print("\n".join(line for line in report.splitlines() if not line.startswith("RESULT_JSON ")))
