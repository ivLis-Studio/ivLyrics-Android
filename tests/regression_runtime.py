"""Portable paths and pinned dependencies for repository-local Java regressions."""
from pathlib import Path
import hashlib
import os
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "shared/src/main/java/kr/ivlis/ivlyricsandroid"
MODULE = ROOT / "spotify-module/src/main/java/kr/ivlis/ivlyricsandroid"
REPORTS = ROOT / "build/reports/regressions"
JSON_VERSION = "20250517"
JSON_SHA256 = "3ea61b2a06e31edf1c91134fe9106b0ebb16628be169f3db75bc7a2b06b45796"


def java_tool(name):
    executable = name + (".exe" if os.name == "nt" else "")
    java_home = os.environ.get("JAVA_HOME")
    candidate = Path(java_home) / "bin" / executable if java_home else None
    if candidate is not None and candidate.is_file():
        return str(candidate)
    found = shutil.which(executable)
    if found:
        return found
    raise SystemExit("A JDK 17 or newer is required; set JAVA_HOME or add its bin directory to PATH.")


def android_jar():
    candidates = [Path(value) for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT")
                  if (value := os.environ.get(name))]
    candidates += [Path.home() / "Library/Android/sdk", Path.home() / "Android/Sdk"]
    platform = os.environ.get("IVLYRICS_TEST_ANDROID_PLATFORM", "android-36.1")
    for sdk in candidates:
        jar = sdk / "platforms" / platform / "android.jar"
        if jar.is_file():
            return jar
    raise SystemExit(f"Android SDK platform {platform} is required; set ANDROID_HOME.")


def compiled_classes(project):
    requested = os.environ.get("IVLYRICS_TEST_VARIANT")
    for variant in ([requested] if requested else ["debug", "release"]):
        task_variant = variant[0].upper() + variant[1:]
        classes = ROOT / project / "build/intermediates/javac" / variant / f"compile{task_variant}JavaWithJavac/classes"
        if classes.is_dir():
            return classes
    raise SystemExit(f"Missing {project} classes. Run ./gradlew :app:assembleDebug "
                     ":spotify-module:assembleDebug first, or select built release classes with IVLYRICS_TEST_VARIANT.")


def json_jar():
    directory = ROOT / "build/test-dependencies"
    directory.mkdir(parents=True, exist_ok=True)
    jar = directory / f"json-{JSON_VERSION}.jar"
    if not jar.is_file():
        url = f"https://repo.maven.apache.org/maven2/org/json/json/{JSON_VERSION}/json-{JSON_VERSION}.jar"
        data = urllib.request.urlopen(url, timeout=30).read()
        if hashlib.sha256(data).hexdigest() != JSON_SHA256:
            raise SystemExit("Downloaded JSON test dependency failed SHA-256 verification.")
        temporary = jar.with_suffix(".download")
        temporary.write_bytes(data)
        temporary.replace(jar)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != JSON_SHA256:
        raise SystemExit(f"JSON test dependency failed SHA-256 verification: {jar}")
    return jar


def classpath(*entries):
    return os.pathsep.join(map(str, entries))
