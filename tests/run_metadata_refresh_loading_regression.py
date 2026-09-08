#!/usr/bin/env python3
"""Exercise actual embedded metadata-refresh callbacks and AI generation identity guard."""
import hashlib
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "metadata-refresh-loading"
work.mkdir(parents=True, exist_ok=True)
activity = SHARED / "BaseLyricsActivity.java"
source = activity.read_text()


def declaration(text, signature):
    if text.count(signature) != 1:
        raise ValueError(f"Expected one declaration: {signature}")
    start = text.index(signature)
    cursor = text.index("{", start) + 1
    depth = 1
    while depth:
        depth += (text[cursor] == "{") - (text[cursor] == "}")
        cursor += 1
    return text[start:cursor]


refresh = source[source.index("if (!trackChanged && embeddedMetadataEnriched && lyricsRepository != null)"):]
callback = declaration(refresh, "new LyricsRepository.Callback()")
callback = callback.replace("BaseLyricsActivity.this.", "MetadataRefreshLoadingRegression.this.")
provider_loading = declaration(source, "public void onLyricsProviderLoading(String trackKey, String providerName)")
guard = declaration(source, "private boolean current(String callbackTrackKey)")
test_file = work / "MetadataRefreshLoadingRegression.java"
test_file.write_text((ROOT / "tests/MetadataRefreshLoadingRegression.java.in").read_text()
                     .replace("// INSERT_REFRESH_CALLBACK", callback)
                     .replace("// INSERT_PROVIDER_LOADING", provider_loading)
                     .replace("// INSERT_GENERATION_GUARD", guard))
files = [SHARED / name for name in ("LyricsLine.java", "LyricsResult.java")] + [test_file]
cp = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", cp, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", cp,
                         "kr.ivlis.ivlyricsandroid.MetadataRefreshLoadingRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
report = "Production embedded metadata refresh callbacks/AI identity guard with synthetic lyrics, no network/device.\n"
report += f"BaseLyricsActivity.java SHA256 {hashlib.sha256(activity.read_bytes()).hexdigest()}\n" + result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
