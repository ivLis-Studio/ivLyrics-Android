#!/usr/bin/env python3
"""Run real WebView + CDN Kuromoji against module-only assets in an isolated QA APK.

This opt-in device test installs/starts/stops only kr.ivlis.ivlyricsandroid.qa.
It never installs, starts, stops, or changes Spotify or its ivLyrics module.
"""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import queue
import subprocess
import threading
import tarfile
import time
import uuid
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "kr.ivlis.ivlyricsandroid.qa"
ACTIVITY = "kr.ivlis.ivlyricsandroid.FuriganaWebViewSmokeActivity"
REPORT = ROOT / "build/reports/regressions/furigana-webview-device"


def run(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True, **kwargs).stdout.strip()


def sdk_tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
    for version in sorted((sdk / "build-tools").glob("*"), reverse=True):
        tool = version / name
        if tool.exists():
            return str(tool)
    raise RuntimeError(f"Android SDK {name} not found")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit adb device serial")
    parser.add_argument("--skip-build", action="store_true", help="Use an already freshly built QA APK")
    parser.add_argument("--cdn-fixture", action="store_true",
                        help="Intercept CDN requests only in QA using registry-verified Kuromoji bytes when device DNS is unavailable")
    args = parser.parse_args()
    adb = ["adb", "-s", args.serial]
    if run(*adb, "get-state") != "device":
        raise RuntimeError("Selected device is unavailable")
    if not args.skip_build:
        result = subprocess.run([str(ROOT / "build.sh"), ":app:assembleQa", "--console=plain"], cwd=ROOT)
        result.check_returncode()
    source = ROOT / "app/build/outputs/apk/qa/app-qa.apk"
    if "name='" + PACKAGE + "'" not in run(sdk_tool("aapt"), "dump", "badging", str(source)).splitlines()[0]:
        raise RuntimeError("Refusing to install a non-QA package")
    REPORT.mkdir(parents=True, exist_ok=True)
    unsigned = REPORT / "furigana-host-unaligned.apk"
    aligned = REPORT / "furigana-host-aligned.apk"
    signed = REPORT / "furigana-host-qa.apk"
    # Keep the production bridge intact in an independent archive. Removing it
    # only from the host APK recreates Spotify's Application asset namespace.
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(unsigned, "w") as host:
        for entry in original.infolist():
            if entry.filename == "assets/furigana/bridge.html" or entry.filename.startswith("META-INF/"):
                continue
            host.writestr(entry, original.read(entry.filename))
        host.writestr("assets/qa_furigana_module_fixture.apk", source.read_bytes(), compress_type=zipfile.ZIP_STORED)
        if args.cdn_fixture:
            metadata = json.load(urllib.request.urlopen("https://registry.npmjs.org/kuromoji/0.1.2", timeout=20))
            distribution = metadata["dist"]
            data = urllib.request.urlopen(distribution["tarball"], timeout=30).read()
            algorithm, expected = distribution["integrity"].split("-", 1)
            if algorithm != "sha512" or base64.b64encode(hashlib.sha512(data).digest()).decode() != expected:
                raise RuntimeError("Kuromoji registry integrity mismatch")
            (REPORT / "kuromoji-integrity.json").write_text(json.dumps(distribution, indent=2) + "\n")
            with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
                for member in archive.getmembers():
                    if member.isfile() and (member.name == "package/build/kuromoji.js"
                                            or member.name.startswith("package/dict/")):
                        host.writestr("assets/qa_kuromoji/" + member.name.removeprefix("package/"),
                                      archive.extractfile(member).read(), compress_type=zipfile.ZIP_STORED)
    run(sdk_tool("zipalign"), "-f", "-p", "4", str(unsigned), str(aligned))
    run(sdk_tool("apksigner"), "sign", "--ks", str(Path.home() / ".android/debug.keystore"),
        "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android", "--key-pass", "pass:android",
        "--out", str(signed), str(aligned))
    run(sdk_tool("apksigner"), "verify", str(signed))
    print(run(*adb, "install", "-r", str(signed)), flush=True)
    run(*adb, "shell", "am", "force-stop", PACKAGE)
    token = uuid.uuid4().hex
    proc = subprocess.Popen([*adb, "logcat", "-T", "1", "-v", "threadtime", "-s",
                             "IvLyricsFuriganaSmoke:I", "AndroidRuntime:E", "*:S"],
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    lines = queue.Queue()
    def read_logs():
        for line in proc.stdout:
            lines.put(line.rstrip())
    threading.Thread(target=read_logs, daemon=True).start()
    collected = []
    started = False
    passed = False
    try:
        print(run(*adb, "shell", "am", "start", "-W", "-n", PACKAGE + "/" + ACTIVITY,
                  "--es", "run_id", token, "--ez", "cdn_fixture", str(args.cdn_fixture).lower()), flush=True)
        deadline = time.monotonic() + 85
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                continue
            if "RUN_STARTED " + token in line:
                started = True
            if not started:
                continue
            collected.append(line)
            print(line, flush=True)
            if "ALL_CHECKS_PASSED" in line:
                passed = True
                break
            if "FIXTURE_FAILED" in line or "FATAL EXCEPTION" in line:
                break
    finally:
        proc.terminate()
        proc.wait(timeout=5)
        # This is the separate, test-only package. User playback processes remain untouched.
        run(*adb, "shell", "am", "force-stop", PACKAGE)
        log_name = "device-cdn-fixture.log" if args.cdn_fixture else "device-live-cdn.log"
        (REPORT / log_name).write_text("\n".join(collected) + "\n")
    evidence = [
        "PASS" if passed else "FAIL",
        "Real Android WebView, production FuriganaRepository and production bridge.",
        "Kuromoji source: " + ("QA-only intercepted library/dictionaries from SHA512-verified npm0.1.2 tarball. Device CDN connectivity is not verified."
                                 if args.cdn_fixture else "device downloads from public Kuromoji CDN"),
        "Synthetic Japanese lyrics; isolated QA host missing bridge; separate module archive supplies it.",
        "This does not exercise Spotify hooks, its actual Activity, account, or installed module.",
        "Device model: " + run(*adb, "shell", "getprop", "ro.product.model"),
        "Android release: " + run(*adb, "shell", "getprop", "ro.build.version.release"),
        "WebView: " + next((line.strip() for line in run(*adb, "shell", "dumpsys", "webviewupdate").splitlines()
                             if "Current WebView package" in line), "unavailable"),
        "QA APK SHA256: " + hashlib.sha256(signed.read_bytes()).hexdigest(),
        "Production Java SHA256: " + hashlib.sha256((ROOT / "shared/src/main/java/kr/ivlis/ivlyricsandroid/FuriganaRepository.java").read_bytes()).hexdigest(),
        "Production bridge SHA256: " + hashlib.sha256((ROOT / "shared/src/main/assets/furigana/bridge.html").read_bytes()).hexdigest(),
    ]
    (REPORT / "result.txt").write_text("\n".join(evidence) + "\n")
    print("\n".join(evidence))
    if not passed:
        raise SystemExit("Real WebView fixture did not pass; inspect " + str(REPORT / log_name))


if __name__ == "__main__":
    main()
