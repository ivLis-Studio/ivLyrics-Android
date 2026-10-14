#!/usr/bin/env python3
"""Validate shared-source ownership and module component isolation, including merged XML."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile
from regression_runtime import ROOT, REPORTS

parser = argparse.ArgumentParser()
parser.add_argument("--built", action="store_true", help="Also verify an assembled module's merged manifest")
parser.add_argument("--variant", choices=("debug", "release"), help="Module variant; otherwise prefer an assembled debug APK, then release")
args = parser.parse_args()
checks = []


def require(condition, description):
    if not condition:
        raise AssertionError(description)
    checks.append(description)


settings = (ROOT / "settings.gradle").read_text()
for project in ("app", "shared", "spotify-module"):
    require(re.search(r"['\"]:" + re.escape(project) + r"['\"]", settings), f"Gradle includes :{project}")
for project in ("app", "spotify-module"):
    gradle = (ROOT / project / "build.gradle").read_text()
    require(re.search(r"implementation\s*\(?\s*project\s*\(?\s*['\"]:shared['\"]", gradle),
            f":{project} depends on :shared")
    require(not re.search(r"prepare_sources\.py|generated/ivlyrics|generatedRoot", gradle),
            f":{project} does not generate or copy the shared Java source")
shared_gradle = (ROOT / "shared/build.gradle").read_text()
require("com.android.library" in shared_gradle, ":shared is an Android library")
require("androidx.webkit:" not in shared_gradle,
        ":shared does not add the application's WebKit dependency")
# Check actual Java references; comments and string literals are not compile-time dependencies.
java_noncode = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
shared_java = [(path, java_noncode.sub(" ", path.read_text()))
               for path in (ROOT / "shared/src/main/java").rglob("*.java")]
for package, pattern in (("androidx.webkit", r"\bandroidx\s*\.\s*webkit\b"),
                         ("dev.ivlyrics.spotify", r"\bdev\s*\.\s*ivlyrics\s*\.\s*spotify\b")):
    references = [str(path.relative_to(ROOT)) for path, code in shared_java if re.search(pattern, code)]
    require(not references, f"Shared Java has no direct {package} imports or references (found: {references})")
for name in ("LyricsView.java", "AiLyricsRepository.java", "BaseLyricsActivity.java"):
    require((ROOT / "shared/src/main/java/kr/ivlis/ivlyricsandroid" / name).is_file(), f"{name} exists in :shared")
    duplicates = [str(path.relative_to(ROOT)) for project in ("app", "spotify-module")
                  for path in (ROOT / project / "src").rglob(name)]
    require(not duplicates, f"{name} has one shared implementation (duplicates: {duplicates})")

# Local AI: one native wrapper in :shared, shipped to both APKs as assets rather than lib/ entries.
require((ROOT / "shared/src/main/cpp/localai/ivlyrics_localai.cpp").is_file(),
        "Local AI JNI wrapper source lives in :shared")
for project in ("app", "spotify-module"):
    gradle = (ROOT / project / "build.gradle").read_text()
    native_sources = [str(path.relative_to(ROOT)) for path in (ROOT / project / "src").rglob("*")
                      if path.suffix in (".cpp", ".c", ".so") or path.name == "CMakeLists.txt"]
    require(not native_sources and not re.search(r"externalNativeBuild|jniLibs", gradle),
            f":{project} adds no native build or native libraries of its own (found: {native_sources})")
native_lock = json.loads((ROOT / "shared/tools/local_ai_native.lock.json").read_text())
require(native_lock and all(spec["url"].startswith("https://") and re.fullmatch(r"[0-9a-f]{64}", spec["sha256"])
                            for spec in native_lock.values()),
        f"All {len(native_lock)} Local AI native downloads are SHA-256 pinned")
require(all("/46ca246de9bb1c35269722a6240d37d9dfd79cad/" in spec["url"] or "/download/b11364/" in spec["url"]
            for spec in native_lock.values()),
        "Local AI native downloads come from llama.cpp b11364 (46ca246)")

component_tags = {"activity", "activity-alias", "service", "receiver", "provider"}
shared_manifest = ET.parse(ROOT / "shared/src/main/AndroidManifest.xml").getroot()
require(not any(node.tag in component_tags for node in shared_manifest.iter()),
        "Shared manifest does not register standalone app components")
module_manifest = ET.parse(ROOT / "spotify-module/src/main/AndroidManifest.xml").getroot()
require(not any(node.tag in component_tags for node in module_manifest.iter()),
        "Module source manifest does not register standalone app components")
require(not module_manifest.findall("uses-permission"),
        "Module source manifest does not inherit standalone installer/overlay permissions")

if args.built:
    variant = args.variant
    if variant is None:
        variant = next((candidate for candidate in ("debug", "release")
                        if list((ROOT / "spotify-module/build/outputs/apk" / candidate).glob("*.apk"))), None)
    require(variant is not None, "An assembled module debug or release APK is available")
    merged_root = ROOT / "spotify-module/build/intermediates/merged_manifests" / variant
    manifests = list(merged_root.glob("*/AndroidManifest.xml"))
    require(len(manifests) == 1, f"Exactly one module {variant} merged manifest is available after assembly")
    merged = ET.parse(manifests[0]).getroot()
    require(not any(node.tag in component_tags for node in merged.iter()),
            "Assembled module manifest has no leaked standalone Activity/service/receiver/provider")
    android_name = "{http://schemas.android.com/apk/res/android}name"
    permissions = {node.get(android_name, "") for node in merged.findall("uses-permission")}
    require(not permissions.intersection({"android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.SYSTEM_ALERT_WINDOW"}),
            "Assembled module does not request standalone updater or overlay access")
    metadata = {node.get(android_name) for node in merged.findall("application/meta-data")}
    require({"xposedmodule", "xposedminversion", "xposedscope"}.issubset(metadata),
            "Assembled module retains its LSPosed registration metadata")
    apks = {"standalone app": sorted((ROOT / "app/build/outputs/apk" / variant).glob("*.apk")),
            "Spotify module": sorted((ROOT / "spotify-module/build/outputs/apk" / variant).glob("*.apk"))}
    if variant == "debug":
        apks["standalone QA app"] = sorted((ROOT / "app/build/outputs/apk/qa").glob("*.apk"))
    payloads = {}
    for product, paths in apks.items():
        require(len(paths) == 1, f"Exactly one {variant} {product} APK is available after assembly")
        with zipfile.ZipFile(paths[0]) as apk:
            entries = set(apk.namelist())
            require(not any(name.startswith("lib/") for name in entries),
                    f"{product} APK has no lib/ native libraries, so its installable ABIs are unchanged")
            manifest_name = "assets/localai/arm64-v8a/manifest.txt"
            require(manifest_name in entries, f"{product} APK carries the Local AI engine manifest")
            manifest = apk.read(manifest_name).decode()
            listed = [line.split(" ", 2) for line in manifest.splitlines() if line.strip()]
            verified = [name for digest, size, name in listed
                        if f"assets/localai/arm64-v8a/{name}" in entries
                        and hashlib.sha256(apk.read(f"assets/localai/arm64-v8a/{name}")).hexdigest() == digest
                        and apk.getinfo(f"assets/localai/arm64-v8a/{name}").file_size == int(size)]
            names = [name for _, _, name in listed]
            require(len(verified) == len(listed) and "libivlyrics_localai.so" in names and "libllama.so" in names
                    and any(name.startswith("libggml-cpu-android_armv8.0") for name in names),
                    f"{product} APK carries all {len(listed)} Local AI engine libraries with matching SHA-256")
            payloads[product] = manifest
    require(len(set(payloads.values())) == 1, "Every APK carries the same Local AI engine build")

webkit_jar = ROOT / "spotify-module/build/generated/private-webkit/webkit-private.jar"
if args.built:
    require(webkit_jar.is_file(), "Assembled module has its isolated WebKit dependency artifact")
if webkit_jar.is_file():
    with zipfile.ZipFile(webkit_jar) as artifact:
        classes = {name for name in artifact.namelist() if name.endswith(".class")}
    private_root = "kr/ivlis/ivlyricsandroid/privateapi/"
    public_boundary = "org/chromium/support_lib_boundary/"
    require(private_root + "androidx/webkit/WebViewCompat.class" in classes
            and private_root + "androidx/webkit/WebViewFeature.class" in classes,
            "WebKit artifact contains the private AndroidX WebView classes")
    require(not any(name.startswith("androidx/") for name in classes),
            "WebKit artifact contains no original AndroidX class definitions")
    require(public_boundary + "WebViewProviderFactoryBoundaryInterface.class" in classes,
            "WebView provider boundary interface retains its public protocol FQCN")
    wrongly_relocated = [name for name in classes if name.startswith(private_root + public_boundary)
                         and not name.startswith(private_root + public_boundary + "util/")]
    require(not wrongly_relocated,
            "Chromium protocol interfaces are not relocated into the private namespace")
    original_jar = webkit_jar.with_name("webkit-unrelocated.jar")
    if original_jar.is_file():
        with zipfile.ZipFile(original_jar) as original:
            protocol_classes = {name for name in original.namelist()
                                if name.startswith(public_boundary) and name.endswith(".class")
                                and not name.startswith(public_boundary + "util/")}
        require(bool(protocol_classes) and protocol_classes.issubset(classes),
                f"All {len(protocol_classes)} original Chromium protocol class names remain available")

report = "\n".join("PASS " + check for check in checks)
report += f"\nMODULE_BOUNDARIES_PASSED assertions={len(checks)}\n"
directory = REPORTS / "module-boundaries"
directory.mkdir(parents=True, exist_ok=True)
(directory / "result.txt").write_text(report)
print(report, end="")
