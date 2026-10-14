#!/usr/bin/env python3
"""Build the Local AI native assets: pinned llama.cpp b11364 libraries plus the JNI wrapper.

The libraries ship as assets under localai/arm64-v8a rather than lib/, so neither APK becomes
arm64-only at install time; LocalAiProvider extracts and verifies them on first use. Every
download is checked against local_ai_native.lock.json. The JNI wrapper is compiled with CMake and
the Android NDK; the SDK packages are found under the given SDK directory or ANDROID_HOME.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import urllib.request

NDK_VERSION = "29.0.14206865"
CMAKE_VERSION = "3.31.6"
# The b11364 Android libraries use Android 9 relocation packing; the provider refuses older systems.
ANDROID_PLATFORM = "android-28"
ABI = "arm64-v8a"
ARCHIVE = "llama-b11364-bin-android-arm64.tar.gz"
HEADERS = ["llama.h", "ggml.h", "ggml-cpu.h", "ggml-backend.h", "ggml-alloc.h", "ggml-opt.h", "gguf.h"]
# LocalAiProvider loads these in order; ggml loads the best libggml-cpu-android_* variant itself.
LOAD_ORDER = ["libggml-base.so", "libggml.so", "libllama.so", "libivlyrics_localai.so"]
CPU_VARIANT_PREFIX = "libggml-cpu-android_"
PREBUILT = {"libggml-base.so", "libggml.so", "libllama.so", "LICENSE"}


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(name, spec, cache):
    path = cache / name
    if not path.exists():
        with urllib.request.urlopen(spec["url"], timeout=120) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != spec["sha256"]:
            raise RuntimeError("Dependency checksum mismatch: " + name)
        temporary = path.with_suffix(path.suffix + ".download")
        temporary.write_bytes(data)
        temporary.replace(path)
    if sha256(path) != spec["sha256"]:
        raise RuntimeError("Dependency checksum mismatch: " + name)
    return path


def sdk_directory(argument):
    candidates = [argument, os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    properties = Path(__file__).resolve().parents[2] / "local.properties"
    if properties.is_file():
        for line in properties.read_text().splitlines():
            if line.startswith("sdk.dir="):
                candidates.append(line.split("=", 1)[1].strip())
    candidates += [str(Path.home() / "Library/Android/sdk"), str(Path.home() / "Android/Sdk")]
    for candidate in candidates:
        if candidate and Path(candidate).is_dir():
            return Path(candidate)
    raise SystemExit("Android SDK not found; set ANDROID_HOME.")


def version_key(path):
    return tuple(int(part) if part.isdigit() else 0 for part in path.name.split("."))


def ndk_directory(sdk):
    pinned = sdk / "ndk" / NDK_VERSION
    candidates = [pinned] + [Path(os.environ[name]) for name in ("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT")
                             if os.environ.get(name)]
    candidates += sorted((sdk / "ndk").glob("*"), key=version_key, reverse=True) if (sdk / "ndk").is_dir() else []
    for candidate in candidates:
        if (candidate / "build/cmake/android.toolchain.cmake").is_file():
            return candidate
    raise SystemExit(f"Local AI needs the Android NDK: sdkmanager \"ndk;{NDK_VERSION}\"")


def cmake_tools(sdk):
    directories = [sdk / "cmake" / CMAKE_VERSION / "bin"]
    if (sdk / "cmake").is_dir():
        directories += [path / "bin" for path in sorted((sdk / "cmake").glob("*"), key=version_key, reverse=True)]
    for directory in directories:
        if (directory / "cmake").is_file():
            ninja = directory / "ninja"
            return str(directory / "cmake"), str(ninja) if ninja.is_file() else shutil.which("ninja")
    cmake = shutil.which("cmake")
    if not cmake:
        raise SystemExit(f"Local AI needs CMake: sdkmanager \"cmake;{CMAKE_VERSION}\"")
    return cmake, shutil.which("ninja")


def extract_prebuilt(archive, destination):
    destination.mkdir(parents=True)
    names = set()
    with tarfile.open(archive, "r:gz") as source:
        for member in source.getmembers():
            name = Path(member.name).name
            if member.isfile() and (name in PREBUILT or (name.startswith(CPU_VARIANT_PREFIX) and name.endswith(".so"))):
                with source.extractfile(member) as data:
                    (destination / name).write_bytes(data.read())
                names.add(name)
    missing = PREBUILT - names
    variants = sorted(name for name in names if name.startswith(CPU_VARIANT_PREFIX))
    if missing or not any(name.endswith("armv8.0_1.so") for name in variants):
        raise RuntimeError(f"{ARCHIVE} lacks {sorted(missing) or 'the baseline CPU variant'}")
    return variants


def main():
    if len(sys.argv) not in (4, 5):
        raise SystemExit("Usage: prepare_local_ai.py ASSET_DIR WORK_DIR CACHE_DIR [ANDROID_SDK_DIR]")
    tools = Path(__file__).resolve().parent
    assets, work, cache = (Path(value).resolve() for value in sys.argv[1:4])
    sdk = sdk_directory(sys.argv[4] if len(sys.argv) == 5 else None)
    lock = json.loads((tools / "local_ai_native.lock.json").read_text())
    cache = cache / "llama.cpp-b11364"
    cache.mkdir(parents=True, exist_ok=True)
    archive = fetch(ARCHIVE, lock[ARCHIVE], cache)
    headers = [fetch(name, lock[name], cache) for name in HEADERS]

    if work.exists():
        shutil.rmtree(work)
    include, prebuilt, build = work / "include", work / "prebuilt", work / "cmake"
    include.mkdir(parents=True)
    for header in headers:
        shutil.copy2(header, include / header.name)
    variants = extract_prebuilt(archive, prebuilt)

    ndk = ndk_directory(sdk)
    cmake, ninja = cmake_tools(sdk)
    configure = [cmake, "-S", str(tools.parent / "src/main/cpp/localai"), "-B", str(build),
                 f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
                 f"-DANDROID_ABI={ABI}", f"-DANDROID_PLATFORM={ANDROID_PLATFORM}", "-DCMAKE_BUILD_TYPE=Release",
                 f"-DLLAMA_INCLUDE_DIR={include}", f"-DLLAMA_LIBRARY_DIR={prebuilt}"]
    configure += ["-G", "Ninja", f"-DCMAKE_MAKE_PROGRAM={ninja}"] if ninja else ["-G", "Unix Makefiles"]
    subprocess.run(configure, check=True)
    subprocess.run([cmake, "--build", str(build), "--config", "Release"], check=True)
    wrapper = build / "libivlyrics_localai.so"
    if not wrapper.is_file():
        raise RuntimeError("CMake did not produce libivlyrics_localai.so")

    strip = next(iter(sorted((ndk / "toolchains/llvm/prebuilt").glob("*/bin/llvm-strip"))), None)
    if strip is None:
        raise SystemExit(f"llvm-strip not found in {ndk}")
    root = assets / "localai"
    if root.exists():
        shutil.rmtree(root)
    output = root / ABI
    output.mkdir(parents=True)
    sources = {name: prebuilt / name for name in LOAD_ORDER[:-1] + variants}
    sources[LOAD_ORDER[-1]] = wrapper
    lines = []
    for name in LOAD_ORDER + variants:
        subprocess.run([str(strip), "--strip-unneeded", "-o", str(output / name), str(sources[name])], check=True)
        lines.append(f"{sha256(output / name)} {(output / name).stat().st_size} {name}")
    (output / "manifest.txt").write_text("\n".join(lines) + "\n")
    shutil.copy2(prebuilt / "LICENSE", root / "LICENSE-llama.cpp.txt")
    total = sum((output / name).stat().st_size for name in LOAD_ORDER + variants)
    print(f"Local AI native assets: {len(lines)} libraries, {total / 1e6:.1f} MB "
          f"(NDK {ndk.name}, {Path(cmake).parent.parent.name} CMake)")


if __name__ == "__main__":
    main()
