#!/usr/bin/env python3
"""Run production local-override bridge invalidation and engine routing with controlled collaborators."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, java_tool

source = (SHARED / "IvLyricsBridge.java").read_text()

def declaration(signature, start=0):
    begin = source.index(signature, start)
    cursor = source.index("{", begin) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[begin:cursor]

engine = source.index("private static final class Engine")
methods = {
    "OBSERVER": declaration("private static void observeLocalLyrics(Context context)"),
    "CHANGE": declaration("private static void onLocalLyricsChanged(String changedKey)"),
    "PUBLISH": declaration("static void publishLyrics(TrackSnapshot track, LyricsResult result, String sourceLanguage)"),
    "CACHED": declaration("static LyricsResult cachedLyrics(TrackSnapshot track)"),
    "MATCHES": declaration("private static boolean metadataMatches(TrackSnapshot track, LyricsResult result)"),
    "ENGINE_CHANGE": declaration("void onLocalLyricsChanged(String changedKey)", engine),
    "PLAYBACK": declaration("@Override public void onNowPlayingChanged(TrackSnapshot next)", engine),
    "ACCEPT": declaration("void acceptShared(TrackSnapshot shared, LyricsResult value, String language)", engine),
    "VALID": declaration("boolean valid(long request, String callbackKey)", engine),
    "RENDER": declaration("void renderCards()", engine),
}
fixture = (ROOT / "tests/LocalLyricsBridgeRegression.java.in").read_text()
for name, body in methods.items():
    fixture = fixture.replace("// INSERT_" + name, body)
work = REPORTS / "local-lyrics-bridge"
work.mkdir(parents=True, exist_ok=True)
test = work / "LocalLyricsBridgeRegression.java"
test.write_text(fixture)
subprocess.run([java_tool("javac"), "-d", str(work), str(test)], check=True)
subprocess.run([java_tool("java"), "-cp", str(work), "LocalLyricsBridgeRegression"], check=True, timeout=20)
