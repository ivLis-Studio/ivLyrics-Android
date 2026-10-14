#!/usr/bin/env python3
"""Opt-in host check of the Local AI provider on macOS arm64 (not part of CI).

Builds the JNI wrapper (shared/src/main/cpp/localai) with clang++ against the SHA-256 pinned
llama.cpp b11364 macOS release and headers from shared/tools/local_ai_native.lock.json, then runs
the app's Java path (LocalAiRuntime -> LocalAiTranslator -> LocalAiNative) on a downloaded Light
model. Usage: run_local_ai_host_check.py MODEL_GGUF [--download-check]
"""
import importlib.util
import os
from pathlib import Path
import platform
import subprocess
import sys
import tarfile

sys.dont_write_bytecode = True  # shared/tools is a Gradle task input; keep it free of caches.
from regression_runtime import ROOT, SHARED, REPORTS, java_tool

LIGHT_FILE, LIGHT_SIZE = "Hy-MT2-1.8B-Q4_K_M.gguf", 1_133_080_448
ARCHIVE = "llama-b11364-bin-macos-arm64.tar.gz"

if sys.platform != "darwin" or platform.machine() != "arm64":
    raise SystemExit("The Local AI host check runs on macOS arm64.")
arguments = [value for value in sys.argv[1:] if not value.startswith("--")]
if len(arguments) != 1:
    raise SystemExit(__doc__)
model = Path(arguments[0]).resolve()
if model.name != LIGHT_FILE or model.stat().st_size != LIGHT_SIZE:
    raise SystemExit(f"Expected the Light model {LIGHT_FILE} ({LIGHT_SIZE} bytes).")

spec = importlib.util.spec_from_file_location("prepare_local_ai", ROOT / "shared/tools/prepare_local_ai.py")
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
lock = __import__("json").loads((ROOT / "shared/tools/local_ai_native.lock.json").read_text())
cache = ROOT / ".gradle/ivlyrics-dependencies/llama.cpp-b11364"
cache.mkdir(parents=True, exist_ok=True)
archive = prepare.fetch(ARCHIVE, lock[ARCHIVE], cache)

work = REPORTS / "local-ai-host"
include, libraries, classes = work / "include", work / "lib", work / "classes"
for directory in (include, libraries, classes):
    directory.mkdir(parents=True, exist_ok=True)
for header in prepare.HEADERS:
    (include / header).write_bytes(prepare.fetch(header, lock[header], cache).read_bytes())
with tarfile.open(archive, "r:gz") as source:
    for member in source.getmembers():
        name = Path(member.name).name
        if name.startswith(("libllama.", "libggml")) and name.endswith(".dylib"):
            target = libraries / name
            if target.is_symlink() or target.exists():
                target.unlink()
            if member.issym():
                target.symlink_to(Path(member.linkname).name)
            elif member.isfile():
                with source.extractfile(member) as data:
                    target.write_bytes(data.read())

java_home = Path(java_tool("javac")).resolve().parents[1]
subprocess.run(["clang++", "-std=c++17", "-O2", "-Wall", "-Wextra", "-dynamiclib",
                str(ROOT / "shared/src/main/cpp/localai/ivlyrics_localai.cpp"),
                f"-I{include}", f"-I{java_home / 'include'}", f"-I{java_home / 'include/darwin'}",
                f"-L{libraries}", "-lllama", "-lggml", "-lggml-base", f"-Wl,-rpath,{libraries}",
                "-o", str(libraries / "libivlyrics_localai.dylib")], check=True)
sources = [SHARED / f"{name}.java" for name in
           ("LocalAiModels", "LocalAiTranslator", "LocalAiDownloader", "LocalAiNative", "LocalAiRuntime")]
sources.append(ROOT / "tests/kr/ivlis/ivlyricsandroid/LocalAiHostCheck.java")
subprocess.run([java_tool("javac"), "--release", "17", "-d", str(classes), *map(str, sources)], check=True)
command = [java_tool("java"), "--enable-native-access=ALL-UNNAMED", "-cp", str(classes),
           "kr.ivlis.ivlyricsandroid.LocalAiHostCheck", str(libraries), str(model.parent)]
if "--download-check" in sys.argv:
    scratch = work / "download"
    scratch.mkdir(exist_ok=True)
    command.append(str(scratch))
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
