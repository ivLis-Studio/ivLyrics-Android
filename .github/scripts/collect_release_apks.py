#!/usr/bin/env python3
"""Verify and collect the two signed release APKs as one release asset set."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PRODUCTS = (
    ("standalone", "app", "app-release.apk", "kr.ivlis.ivlyricsandroid",
     {"android.permission.INTERNET", "android.permission.REQUEST_INSTALL_PACKAGES",
      "android.permission.SYSTEM_ALERT_WINDOW"}),
    ("spotify-module", "spotify-module", "spotify-module-release.apk", "dev.ivlyrics.spotify.module", set()),
)


def asset_names(tag):
    if not re.fullmatch(r"v?[0-9][A-Za-z0-9._+-]*", tag):
        raise ValueError("Release tag must be a filename-safe version tag")
    # Older installed apps choose the first APK containing '-release'. Only the
    # standalone file may contain it, including when the tag is a prerelease.
    names = (f"ivLyrics-Android-{tag}-release.apk",
             f"ivLyrics-LSPatch-{tag.replace('-', '_')}.apk")
    if "unsigned" in names[0].lower():
        raise ValueError("Release tag would be rejected by the existing Android updater")
    return names


def release_version(tag, root=ROOT, previous=None):
    asset_names(tag)
    properties = {}
    for line in (root / "gradle.properties").read_text().splitlines():
        match = re.fullmatch(r"\s*(ivLyricsVersionName|ivLyricsVersionCode)\s*=\s*(.*?)\s*", line)
        if match:
            if match[1] in properties:
                raise RuntimeError(f"Duplicate release property: {match[1]}")
            properties[match[1]] = match[2]
    name = properties.get("ivLyricsVersionName", "")
    code = properties.get("ivLyricsVersionCode", "")
    if name != tag.removeprefix("v"):
        raise RuntimeError(f"Release tag {tag} does not match configured versionName {name!r}")
    if not code.isdigit() or not 0 < int(code) <= 2100000000:
        raise RuntimeError("ivLyricsVersionCode must be a positive Android version code")
    version = {"versionName": name, "versionCode": int(code)}
    if previous is not None:
        records = [previous, *previous.get("apks", [])]
        codes = [row.get("versionCode") for row in records]
        if any(not isinstance(value, int) or isinstance(value, bool) or value <= 0 for value in codes):
            raise RuntimeError("Previous release metadata has invalid version codes")
        if previous.get("tag") == tag:
            if any(row.get("versionName") != name or row["versionCode"] != version["versionCode"] for row in records):
                raise RuntimeError("Rebuilding an existing release must preserve its published APK versions")
        elif version["versionCode"] <= max(codes):
            raise RuntimeError("A new release must increase versionCode above every previously published APK")
    return version


def sdk_tool(name):
    roots = [Path(value) for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT")
             if (value := os.environ.get(key))]
    roots += [Path.home() / "Library/Android/sdk", Path.home() / "Android/Sdk"]
    for sdk in roots:
        versions = sorted((sdk / "build-tools").glob("*"),
                          key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)), reverse=True)
        for version in versions:
            tool = version / name
            if tool.is_file():
                return str(tool)
    raise RuntimeError(f"Android SDK tool {name} not found; set ANDROID_HOME")


def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def inspect_apk(path, expected_package, expected_permissions, aapt, apksigner):
    if not path.is_file():
        raise RuntimeError(f"Missing signed release APK: {path}")
    certificates = output(apksigner, "verify", "--print-certs", str(path))
    # Build Tools 37 labels scheme signers as 'V2 Signer:'; earlier tools use
    # 'Signer #1'. Keep the certificate comparison independent of that label.
    signers = set(re.findall(r"(?:Signer #\d+|V\d+ Signer:) certificate SHA-256 digest: ([0-9a-fA-F]+)", certificates))
    if not signers:
        raise RuntimeError(f"No verified signing certificate: {path}")
    badging = output(aapt, "dump", "badging", str(path))
    package_line = next((line for line in badging.splitlines() if line.startswith("package:")), "")
    info = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    if info.get("name") != expected_package:
        raise RuntimeError(f"Unexpected APK package: {path}")
    if "application-debuggable" in badging:
        raise RuntimeError(f"Debuggable APK cannot be published as a release: {path}")
    permission_dump = output(aapt, "dump", "permissions", str(path))
    permissions = set(re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", permission_dump, re.M))
    if permissions != expected_permissions:
        raise RuntimeError(f"Unexpected permission profile: {path}: {sorted(permissions)}")
    return {"packageName": info["name"], "versionName": info.get("versionName", ""),
            "versionCode": int(info["versionCode"]), "signers": sorted(signers)}


def collect(tag, destination, root=ROOT, aapt=None, apksigner=None):
    names = asset_names(tag)
    aapt, apksigner = aapt or sdk_tool("aapt"), apksigner or sdk_tool("apksigner")
    verified = []
    for product, project, filename, package, permissions in PRODUCTS:
        source = root / project / "build/outputs/apk/release" / filename
        info = inspect_apk(source, package, permissions, aapt, apksigner)
        verified.append((product, source, info))
    if verified[0][2]["signers"] != verified[1][2]["signers"]:
        raise RuntimeError("Both release APKs must use the configured stable release signing key")
    expected = release_version(tag, root)
    for product, source, info in verified:
        if any(info.get(key) != value for key, value in expected.items()):
            raise RuntimeError(
                f"{product} APK version {info.get('versionName')} ({info.get('versionCode')}) "
                f"does not match release {expected['versionName']} ({expected['versionCode']})"
            )
    # Verify both before creating any output; never publish a module-only set.
    destination.mkdir(parents=True, exist_ok=True)
    if list(destination.glob("*.apk")):
        raise RuntimeError(f"Release output already contains APKs: {destination}")
    report = []
    for name, (product, source, info) in zip(names, verified):
        target = destination / name
        shutil.copy2(source, target)
        report.append({"product": product, "name": name, **info,
                       "size": target.stat().st_size,
                       "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
        print(target)
    (destination / "packaging-report.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", type=Path, default=Path("release-apks"))
    parser.add_argument("--verify-version-only", action="store_true")
    parser.add_argument("--previous-version-file", type=Path)
    args = parser.parse_args()
    if args.verify_version_only:
        previous = json.loads(args.previous_version_file.read_text()) if args.previous_version_file else None
        print(json.dumps(release_version(args.tag, previous=previous)))
        return
    collect(args.tag, args.output)


if __name__ == "__main__":
    main()
