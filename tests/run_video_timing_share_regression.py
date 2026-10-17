#!/usr/bin/env python3
"""Run production explicit-share control/auth code with frozen models and no external requests."""
from pathlib import Path
import subprocess
from regression_runtime import SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar


def declaration(source, signature):
    assert source.count(signature) == 1, signature
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


activity = (SHARED / "BaseLyricsActivity.java").read_text()
repository = (SHARED / "CreatorPrivacyRepository.java").read_text()
view = (SHARED / "YouTubeBackgroundView.java").read_text()
popup = declaration(activity, "private LinearLayout buildVideoSyncSettingsContent()")
assert 'videoTimingShareButton.setOnClickListener(view -> shareCurrentVideoTiming())' in popup
assert activity.count("shareCurrentVideoTiming()") == 2  # Definition and one explicit click only.
capture = declaration(activity, "private VideoTimingSubmission captureCurrentVideoTiming()")
assert "youtubeBackgroundView.appliedVideoInfo()" in capture
assert "applied != currentYouTubeBackgroundInfo" in capture
assert "isCurrentVideoSelectionTrack(currentLyricsKey)" in capture
assert "firstLyricMs, currentVideoSyncOffsetMs" in capture
for signature in ("private void setCurrentVideoSyncOffset(", "private void applySelectedVideo(",
                  "private void adjustCurrentVideoSyncOffset(", "private void materializeEditedVideoTiming("):
    method = declaration(activity, signature)
    assert "shareCurrentVideoTiming" not in method and "shareVideoTiming" not in method
assert activity.count("creatorPrivacyRepository.shareVideoTiming(") == 1
work = REPORTS / "video-timing-share"
work.mkdir(parents=True, exist_ok=True)
fixture = Path(__file__).with_name("VideoTimingShareRegression.java.in").read_text()
fixture = fixture.replace("// SHARE", declaration(activity, "private void shareCurrentVideoTiming()"))
fixture = fixture.replace("// AUTH", declaration(repository, "void shareVideoTiming("))
fixture = fixture.replace("// APPLIED", "\n".join(declaration(view, signature) for signature in (
    "YouTubeBackgroundRepository.VideoInfo appliedVideoInfo()", "void loadVideo(", "void clearVideo()",
    "private final class Bridge {",
)))
fixture = fixture.replace("// EDIT", "\n".join(declaration(activity, signature) for signature in (
    "private void materializeEditedVideoTiming(", "private void setCurrentVideoSyncOffset(",
)))
target = work / "VideoTimingShareRegression.java"
target.write_text(fixture)
dependencies = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
sources = [target, SHARED / "VideoTimingSubmission.java", SHARED / "YouTubeBackgroundRepository.java"]
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-cp", dependencies,
                "-d", str(work), *map(str, sources)], check=True)
result = subprocess.run([java_tool("java"), "-cp", dependencies,
                         "kr.ivlis.ivlyricsandroid.VideoTimingShareRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
