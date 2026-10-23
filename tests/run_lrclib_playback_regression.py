#!/usr/bin/env python3
"""Run the production Java LRCLIB lookup, transport, settings and caches with mocked HTTP."""
import hashlib
import subprocess
import uuid

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "lrclib-playback"
work.mkdir(parents=True, exist_ok=True)
fixture = work / "LrclibPlaybackRegression.java"
fixture.write_text((ROOT / "tests/LrclibPlaybackRegression.java.in").read_text())
wire_fixture = work / "LrclibWireRegression.java"
wire_fixture.write_text((ROOT / "tests/LrclibWireRegression.java.in").read_text())
production = [SHARED / name for name in (
    "LyricsRepository.java", "LrclibEndpoint.java", "LrclibPlaybackStage.java", "LrclibPlaybackConnection.java", "LyricsProviderSettings.java",
    "LyricsProviderSelectionPlan.java", "LyricsDiskCache.java", "LrcParser.java", "SyncDataApplier.java",
    "LyricsResult.java", "LyricsLine.java", "TrackSnapshot.java", "RequestCancellation.java",
    "LatestLyricsRequest.java", "AppI18n.java", "LocalLyricsStore.java", "LocalLyricsI18n.java",
)]
files = production + [ROOT / "tests/android/os/Handler.java", ROOT / "tests/android/os/Looper.java", fixture, wire_fixture]
cp = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", cp, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.LrclibPlaybackRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=45)
report = "Production Java playback entry/selection/HTTP/settings/disk cache; synthetic responses, no real endpoint/device.\n"
report += "\n".join(f"{file.name} SHA256 {hashlib.sha256(file.read_bytes()).hexdigest()}" for file in production)
report += "\n" + result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
if result.returncode:
    raise SystemExit(result.returncode)
# A fresh, local fixture certificate: only the wire-test's explicit trust store trusts it.
keystore = work / f"wire-{uuid.uuid4()}.p12"
subprocess.run([java_tool("keytool"), "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1", "-storetype", "PKCS12",
                "-storepass", "fixture-only", "-keypass", "fixture-only", "-keystore", str(keystore)],
               check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
wire = subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.LrclibWireRegression", str(keystore)],
                      stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=45)
(work / "wire-result.txt").write_text(wire.stdout)
print(wire.stdout, end="")
raise SystemExit(wire.returncode)
