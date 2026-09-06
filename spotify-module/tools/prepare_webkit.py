#!/usr/bin/env python3
"""Build an isolated WebKit dependency without reading or rewriting app sources."""
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import zipfile


def java_tool(name):
    java_home = os.environ.get("JAVA_HOME")
    candidate = Path(java_home) / "bin" / name if java_home else None
    resolved = str(candidate) if candidate and candidate.is_file() else shutil.which(name)
    if not resolved:
        raise RuntimeError(f"{name} not found; set JAVA_HOME to a JDK installation")
    return resolved


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: prepare_webkit.py OUTPUT_DIR CACHE_DIR")
    tools_dir = Path(__file__).resolve().parent
    output, cache = (Path(value).resolve() for value in sys.argv[1:])
    output.mkdir(parents=True, exist_ok=True)
    cache.mkdir(parents=True, exist_ok=True)
    dependencies = json.loads((tools_dir / "dependencies.lock.json").read_text())
    for name, spec in dependencies.items():
        path = cache / name
        if not path.exists():
            with urllib.request.urlopen(spec["url"], timeout=60) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != spec["sha256"]:
                raise RuntimeError("Dependency checksum mismatch: " + name)
            temporary = path.with_suffix(path.suffix + ".download")
            temporary.write_bytes(data)
            temporary.replace(path)
        if hashlib.sha256(path.read_bytes()).hexdigest() != spec["sha256"]:
            raise RuntimeError("Dependency checksum mismatch: " + name)

    unrelocated = output / "webkit-unrelocated.jar"
    with zipfile.ZipFile(unrelocated, "w") as result:
        for name in ["webkit-1.9.0.aar", "core-1.1.0.aar"]:
            with zipfile.ZipFile(cache / name) as aar:
                with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as classes:
                    for entry in sorted(classes.namelist()):
                        if entry.endswith(".class") and (not name.startswith("core-")
                                or entry == "androidx/core/util/Pair.class"):
                            info = zipfile.ZipInfo(entry, date_time=(1980, 1, 1, 0, 0, 0))
                            result.writestr(info, classes.read(entry))
    classpath = os.pathsep.join(str(cache / name) for name in ["asm-9.8.jar", "asm-commons-9.8.jar"])
    relocator = output / "relocator"
    relocator.mkdir(exist_ok=True)
    subprocess.run([java_tool("javac"), "-cp", classpath, "-d", str(relocator),
                    str(tools_dir / "RelocateJar.java")], check=True)
    subprocess.run([java_tool("java"), "-cp", str(relocator) + os.pathsep + classpath,
                    "RelocateJar", str(unrelocated), str(output / "webkit-private.jar")], check=True)


if __name__ == "__main__":
    main()
