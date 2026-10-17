#!/usr/bin/env python3
"""Exercise production artwork gestures with host collaborators; check settings ownership/routes."""
from pathlib import Path
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, java_tool


def declaration(source, signature):
    assert source.count(signature) == 1, signature
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


source = (SHARED / "BaseLyricsActivity.java").read_text()
settings = (SHARED / "AiLyricsSettings.java").read_text()
menu = declaration(source, "private void buildPlayerSettingsMenu(")
assert source.count('settingsToggle(group, ui("setting.album_art_open_lp")') == 1
assert menu.index("albumArtOpenLpSwitch =") < menu.index("if (IvLyricsBridge.isEmbedded(this)) return;")
assert "aiLyricsSettings.setAlbumArtOpenLp(checked)" in menu
assert "updateArtworkTapHint(artworkView)" in menu
assert 'showSavedToast(ui("toast.settings_saved"))' in menu
toggle = declaration(source, "private Switch settingsToggle(")
assert "registerSetting(toggle, title, description, null)" in toggle
populate = declaration(source, "private void populateAiSettingsUi()")
assert "albumArtOpenLpSwitch.setChecked(aiLyricsSettings.albumArtOpenLp())" in populate
assert "updateArtworkTapHint(artworkView)" in populate
assert "suppressSettingsEvents = previousSuppression;" in populate
assert "attachArtworkSwipe(artworkView)" in declaration(source, "private FrameLayout buildMainPage()")
assert "attachArtworkSwipe(artworkView)" in declaration(source, "private FrameLayout buildLandscapeMainPage()")
# Only the artwork callback owns the gate; the explicit menu enters the normal guarded mode function.
current_track_menu = declaration(source, "private void buildCurrentTrackSettingsMenu(")
assert 'settingsAction(settingsGroup(page), ui("vinyl.mode")' in current_track_menu
assert "showVinylMode(true)" in current_track_menu
assert "albumArtOpenLp" not in current_track_menu
mode = declaration(source, "private void showVinylMode(")
assert "albumArtOpenLp" not in mode
assert "currentTrack == null || !currentTrack.hasUsableMetadata()" in mode
assert "if (IvLyricsBridge.isEmbedded(this)) return;" in mode
assert "showVinylMode(false)" in declaration(source, "private VinylPlayerModeView buildVinylPlayerModeView()")
assert "albumArtOpenLp" not in declaration(source, "private void scheduleArtworkLongPress(")
for signature in ("private FrameLayout buildPictureInPicturePage()", "private void openSpotifyDestination(",
                  "private void openSpotifyForCurrentTrack()"):
    assert "albumArtOpenLp" not in declaration(source, signature)
for path in (ROOT / "app/build.gradle", ROOT / "spotify-module/build.gradle"):
    assert 'implementation project(":shared")' in path.read_text()

work = REPORTS / "album-art-lp"
work.mkdir(parents=True, exist_ok=True)
fixture = Path(__file__).with_name("AlbumArtLpRegression.java.in").read_text()
fixture = fixture.replace("// PRODUCTION_GESTURES", "\n".join(declaration(source, signature) for signature in (
    "private void attachArtworkSwipe(", "private void updateArtworkTapHint(",
    "private void scheduleArtworkLongPress(", "private void cancelArtworkLongPress(",
    "private void settleArtworkSwipe(", "private float artworkVelocityX()",
    "private void recycleArtworkVelocityTracker()",
)))
fixture = fixture.replace("// PRODUCTION_SETTINGS", "\n".join(declaration(settings, signature) for signature in (
    "boolean albumArtOpenLp()", "void setAlbumArtOpenLp(",
)))
fixture = fixture.replace("// PRODUCTION_MENU", "\n".join(declaration(source, signature) for signature in (
    "private void buildCurrentTrackSettingsMenu(", "private Button settingsAction(", "private void showVinylMode(",
)))
target = work / "AlbumArtLpRegression.java"
target.write_text(fixture)
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-d", str(work), str(target)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "AlbumArtLpRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
