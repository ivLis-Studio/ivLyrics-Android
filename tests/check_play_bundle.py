#!/usr/bin/env python3
"""Verify the Google Play build: its merged manifest, its app bundle, and that the GitHub build kept its features.

Run after ./gradlew :app:bundlePlay :app:assembleRelease (signed or not). The Play build must target the
API level Google Play requires, leave out the GitHub APK updater (REQUEST_INSTALL_PACKAGES and its result
receiver) and the Spotify shortcut (its accessibility service and SYSTEM_ALERT_WINDOW), and tell the shared
code it is the Play build (DistributionChannel). The GitHub release build must keep all of them.
"""
import argparse
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile
from regression_runtime import ROOT, REPORTS

ANDROID = "{http://schemas.android.com/apk/res/android}"
PLAY_TARGET_SDK = 36
UPDATER_PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"
OVERLAY_PERMISSION = "android.permission.SYSTEM_ALERT_WINDOW"
UPDATE_RECEIVER = "kr.ivlis.ivlyricsandroid.UpdateInstallResultReceiver"
SHORTCUT_SERVICE = "kr.ivlis.ivlyricsandroid.SpotifyForegroundAccessibilityService"
NOW_PLAYING_SERVICE = "kr.ivlis.ivlyricsandroid.NowPlayingService"
DISTRIBUTION_KEY = "kr.ivlis.ivlyricsandroid.DISTRIBUTION"

parser = argparse.ArgumentParser()
parser.add_argument("--bundle", type=Path, help="Play app bundle to inspect; defaults to the one under app/build")
args = parser.parse_args()
checks = []


def require(condition, description):
    if not condition:
        raise AssertionError(description)
    checks.append(description)


def merged_manifest(variant):
    manifests = sorted((ROOT / "app/build/intermediates/merged_manifests" / variant).glob("*/AndroidManifest.xml"))
    require(len(manifests) == 1, f"Exactly one app {variant} merged manifest is available after the build")
    return ET.parse(manifests[0]).getroot()


def names(root, path):
    return {node.get(ANDROID + "name", "") for node in root.findall(path)}


def meta_data(root):
    return {node.get(ANDROID + "name", ""): node.get(ANDROID + "value", "") for node in root.findall("application/meta-data")}


play = merged_manifest("play")
require(int(play.find("uses-sdk").get(ANDROID + "targetSdkVersion", "0")) >= PLAY_TARGET_SDK,
        f"Play build targets API {PLAY_TARGET_SDK} or higher, as Google Play requires")
play_permissions = names(play, "uses-permission")
require(UPDATER_PERMISSION not in play_permissions, "Play build does not request REQUEST_INSTALL_PACKAGES")
require(OVERLAY_PERMISSION not in play_permissions, "Play build does not request SYSTEM_ALERT_WINDOW")
require(UPDATE_RECEIVER not in names(play, "application/receiver"), "Play build has no GitHub update install receiver")
services = names(play, "application/service")
require(SHORTCUT_SERVICE not in services, "Play build has no Spotify shortcut accessibility service")
require(not any(node.get(ANDROID + "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE"
                for node in play.findall("application/service")),
        "Play build declares no accessibility service at all")
require(NOW_PLAYING_SERVICE in services, "Play build keeps the now-playing notification listener")
require(meta_data(play).get(DISTRIBUTION_KEY) == "play", "Play build tells the shared code it is the Google Play build")
require(play.find("application").get(ANDROID + "enableOnBackInvokedCallback") == "false",
        "Play build keeps onBackPressed handling on Android 16 (predictive back opt-out)")

github = merged_manifest("release")
github_permissions = names(github, "uses-permission")
require({UPDATER_PERMISSION, OVERLAY_PERMISSION}.issubset(github_permissions),
        "GitHub release build keeps the updater and overlay permissions")
require(UPDATE_RECEIVER in names(github, "application/receiver")
        and SHORTCUT_SERVICE in names(github, "application/service"),
        "GitHub release build keeps the update receiver and the Spotify shortcut service")
require(DISTRIBUTION_KEY not in meta_data(github), "GitHub release build is not marked as the Play build")

bundle = args.bundle
if bundle is None:
    bundles = sorted((ROOT / "app/build/outputs/bundle/play").glob("*.aab"))
    require(len(bundles) == 1, "Exactly one Play app bundle is available after :app:bundlePlay")
    bundle = bundles[0]
with zipfile.ZipFile(bundle) as aab:
    entries = set(aab.namelist())
    require("base/manifest/AndroidManifest.xml" in entries, "Play bundle has a base module manifest")
    require(not any(name.startswith("base/lib/") for name in entries),
            "Play bundle has no lib/ native libraries (Local AI ships as assets)")
    require("base/assets/localai/arm64-v8a/manifest.txt" in entries, "Play bundle carries the Local AI engine")
    dex = [name for name in entries if re.fullmatch(r"base/dex/classes(?:[0-9]+)?[.]dex", name)]
    require(bool(dex), "Play bundle has executable DEX files")
    harness_markers = (b"SettingsUiHarnessActivity", b"StandaloneSmokeActivity", b"SettingsUiRegressionTest")
    require(not any(marker in aab.read(name) for name in dex for marker in harness_markers),
            "Play bundle excludes debug harnesses and tests")
    require(any(b"DistributionChannel" in aab.read(name) for name in dex),
            "Play bundle contains the distribution check the manifest drives")

report = "\n".join("PASS " + check for check in checks)
report += f"\nPLAY_BUNDLE_PASSED assertions={len(checks)}\n"
directory = REPORTS / "play-bundle"
directory.mkdir(parents=True, exist_ok=True)
(directory / "result.txt").write_text(report)
print(report, end="")
