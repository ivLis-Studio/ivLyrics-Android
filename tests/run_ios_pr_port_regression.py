#!/usr/bin/env python3
"""Execute Android's iOS PR7/8 adaptations with deterministic host collaborators.

Uses production settings/models and verbatim production UI/playback declarations.
No device, account or network is accessed; the pinned JSON jar must already exist.
"""
from pathlib import Path
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, compiled_classes, classpath, java_tool, json_jar


def declaration(source, signature):
    assert source.count(signature) == 1, signature
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


work = REPORTS / "ios-pr-port"
work.mkdir(parents=True, exist_ok=True)
template = Path(__file__).with_name("IOSPrPortRegression.java.in").read_text()
inputs = {
    "ANNOTATIONS": ("LyricsView", (
        "private LineRenderInput lineRenderInput(", "private static final class LineRenderInput {",
        "void setCulturalAnnotations(",
    )),
    "DISPLAY_TEXT": ("AiLyricsRepository", ("static String displayLineText(",)),
    "PIP": ("BaseLyricsActivity", (
        "private void buildPortraitPictureInPictureContent(",
        "private void buildSquarePictureInPictureContent(",
    )),
    "VINYL": ("VinylPlayerModeView", (
        "        void setTrack(", "        void setArtwork(", "private static final class VisualTrack {",
    )),
    "CONTEXT": ("NowPlayingService", (
        "private static SpotifyAutomixMetadata spotifyAutomixMetadata(",
        "private static boolean isEnabledAutomixMode(", "private static boolean isSpotifyDjContext(",
        "private static long namedPlaybackLong(", "private static String firstNamedPlaybackValue(",
        "private static boolean playbackMetadataKeyMatches(", "private static final class SpotifyAutomixMetadata {",
        "static boolean seekTo(String expectedTrackKey,", "static void registerExplicitSeek(", "interface Listener {",
    )),
    "SEEK": ("BaseLyricsActivity", (
        "private void seekPlayerBy(", "private void seekToPosition(", "private void seekPlayerToPosition(",
        "private void seekPlayerToPositionInternal(", "public void onExplicitSeek(",
        "private long currentPlaybackPosition(", "private long lyricsPlaybackPosition(",
        "private long playerPositionForLyricsTime(", "private long lyricsTrackDuration(",
    )),
    "ENGINE_SEEK": ("IvLyricsBridge", ("@Override public void onExplicitSeek(",)),
}
for marker, (name, signatures) in inputs.items():
    source = (SHARED / (name + ".java")).read_text()
    template = template.replace("// " + marker, "\n".join(declaration(source, sig) for sig in signatures))

target = work / "IOSPrPortRegression.java"
target.write_text(template)
stubs = {
    "android/graphics/Bitmap.java": """package android.graphics;
public class Bitmap {
    public int getWidth() { return 32; } public int getHeight() { return 32; }
    public boolean isRecycled() { return false; } public int getPixel(int x, int y) { return 0xffdd4488; }
}
""",
    "android/os/SystemClock.java": """package android.os;
public class SystemClock {
    public static long now = 1000;
    public static long elapsedRealtime() { return now; }
    public static long uptimeMillis() { return now; }
    public static long elapsedRealtimeNanos() { return now * 1000000L; }
}
""",
}
files = []
for name, content in stubs.items():
    stub = work / name
    stub.parent.mkdir(parents=True, exist_ok=True)
    stub.write_text(content)
    files.append(stub)
json = ROOT / "build/test-dependencies/json-20250517.jar"
if not json.is_file():
    raise SystemExit("Populate the pinned JSON test dependency before this offline regression.")
dependencies = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
files += [ROOT / "tests/android/os/Handler.java", ROOT / "tests/android/os/Looper.java"]
files += [SHARED / (name + ".java") for name in (
    "AiLyricsSettings", "LyricsLine", "TrackSnapshot", "CulturalAnnotation", "RecordRotation",
    "SpotifyDjLyricsTimeline", "RequestCancellation",
)]
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-cp", dependencies,
                "-d", str(work), *map(str, files), str(target)], check=True)
result = subprocess.run([java_tool("java"), "-cp", dependencies,
                         "kr.ivlis.ivlyricsandroid.IOSPrPortRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
