#!/usr/bin/env python3
"""Motion lyrics wiring: the page asset, the messages around it and the mode's entry and exit.

The presentation itself is the desktop one (the page is built and tested in the ivLyrics
repository); MotionLyricsSongTest covers the song the page is given. This checks that the page,
its messages, its strings and the mode's entry and exit paths are in place, in the standalone
app and in the Spotify module's sheet.
"""
from regression_runtime import ROOT, SHARED


def declaration(source, signature):
    assert source.count(signature) == 1, signature
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


view = (SHARED / "MotionLyricsModeView.java").read_text()
activity = (SHARED / "BaseLyricsActivity.java").read_text()
repository = (SHARED / "AiLyricsRepository.java").read_text()
i18n = (SHARED / "AppI18n.java").read_text()
page = (ROOT / "shared/src/main/assets/motion/MotionLyricsPage.html").read_text()

# The page and the calls into it.
assert page.startswith('<!doctype html>\n<html lang="en" data-motion-build="'), "the asset is the built page"
assert 'PAGE_ASSET = "motion/MotionLyricsPage.html"' in view
for call in ("configure", "setTrack", "setPlayback", "resolve"):
    assert "ivLyricsMotionHost." + call + "(" in view and ("\t\t" + call + ",") in page, call
assert 'BRIDGE_NAME = "ivLyricsMotionNative"' in view and "root.ivLyricsMotionNative?.postMessage" in page
for message in ("ready", "eligible", "background", "direct", "cacheGet", "cacheSet", "error"):
    assert 'case "' + message + '"' in view and 'type: "' + message + '"' in page, message
# The system font scale must not move the page's own sizes; touches belong to the mode's controls.
assert "settings.setTextZoom(100)" in view
assert "touchLayer.setOnClickListener(view -> showControls())" in view
# A closed mode stops the page's clock and no longer turns the player's background.
assert "playing && shown" in declaration(view, "private void flush()")
hide = declaration(view, "void hide(boolean animate, Runnable afterwards)")
assert "webView.onPause()" in hide and "listener.onBackgroundFrame(0f, 1f, 0f, 0f)" in hide

# For songs that can play, in both products; a phone shows it in landscape.
show = declaration(activity, "private void showMotionLyricsMode(boolean show)")
assert show.index("motionLyricsUnavailableMessage()") < show.index("ensureMotionLyricsModeView()") \
    < show.index("motionModeVisible = true") < show.index("holdLandscapeForMotionLyrics(true)")
unavailable = declaration(activity, "private String motionLyricsUnavailableMessage()")
assert "MotionLyricsSong.canPlay(currentLyricsResult)" in unavailable and 'ui("motion.unavailable")' in unavailable
# The sheet's only page is the lyrics page: closing it there would finish the sheet.
assert "if (!embedded) {\n                showLyricsPage(false);" in show
assert "finish();" in declaration(activity, "private void showLyricsPage(boolean show)").split("return;")[0]
closing = show[show.index("motionModeVisible = false"):]
assert "holdLandscapeForMotionLyrics(false)" in closing and "applyKeepScreenOnSetting(" in closing
assert "mainPage.setVisibility(View.VISIBLE)" in closing and "refreshMainPagePlayback()" in closing
sheet = closing[closing.index("if (embedded) {"):closing.index("mainPage.setVisibility(View.VISIBLE)")]
assert sheet.index("motionLyricsModeView.hide(true") < sheet.index("holdLandscapeForMotionLyrics(false)") \
    < sheet.index("lyricsPage.setVisibility(View.VISIBLE)"), "the stage stays turned while it fades"
assert "reopenLyricsPageAfterMotionMode()" in closing
hold = declaration(activity, "private void holdLandscapeForMotionLyrics(boolean hold)")
assert hold.index("smallestScreenWidthDp < 600") < hold.index("turnMotionStage(hold && phone)") \
    < hold.index("SCREEN_ORIENTATION_SENSOR_LANDSCAPE")
assert "setRequestedOrientation(orientationBeforeMotionMode)" in hold
assert "|| motionModeVisible" in declaration(activity, "private void applyKeepScreenOnSetting(")

# The module's sheet cannot turn its window (Spotify's Activity would be recreated), so the stage
# and the background are laid out as a wide screen a quarter turn round, and the window is held.
turn = declaration(activity, "private void turnMotionStage(boolean turn)")
assert "rootView.getHeight() > rootView.getWidth()" in turn
assert turn.index("SCREEN_ORIENTATION_LOCKED") < turn.index("requestOrientationQuietly(orientationBeforeMotionMode)")
assert "watchMotionStageDirection(wanted != 0)" in turn and "applySystemBarsForOrientation()" in turn
layout = declaration(activity, "private void layoutMotionStageTurn()")
assert "motionLyricsModeView.setQuarterTurn(motionStageTurn, width, height)" in layout
for background in ("backgroundView", "youtubeBackgroundView"):
    assert "layoutForMotionStage(" + background + ", width, height)" in layout, background
camera = declaration(activity, "private void applyMotionCamera()")
assert "motionStageTurn > 0 ? -y : motionStageTurn < 0 ? y : x" in camera
assert "motionStageTurn > 0 ? x : motionStageTurn < 0 ? -x : y" in camera
assert "motionStageTurn + motionCameraRotate" in camera
direction = declaration(activity, "private void watchMotionStageDirection(boolean watch)")
assert "degrees >= 240 && degrees <= 300 ? 90" in direction and "degrees >= 60 && degrees <= 120 ? -90" in direction
assert "isLandscapeLayout() || motionStageTurn != 0" in declaration(activity, "private void applySystemBarsForOrientation()")
quarter = declaration(view, "void setQuarterTurn(int degrees, int windowWidth, int windowHeight)")
assert "degrees == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : windowHeight" in quarter and "setRotation(degrees)" in quarter
# A turned stage has the window's top inset at one of its sides.
assert "quarterTurn > 0 ? insetTop : quarterTurn < 0 ? insetBottom : insetLeft" in declaration(view, "private int stageInsetLeft()")
assert "quarterTurn > 0 ? insetRight : quarterTurn < 0 ? insetLeft : insetTop" in declaration(view, "private int stageInsetTop()")
options = declaration(view, "private String optionsJson()")
assert "stageInsetTop() / density" in options and "insetTop / density" not in options.replace("stageInsetTop()", "")

# The pages rebuilt by the turn stay hidden under the mode; Back and a lost track close it.
rebuild = declaration(activity, "private void rebuildOrientationSensitivePages()")
assert "if (motionModeVisible) {\n            mainPage.setVisibility(View.INVISIBLE);" in rebuild
back = declaration(activity, "public void onBackPressed()")
assert back.index("showMotionLyricsMode(false)") < back.index("showVinylMode(false)")
assert "showMotionLyricsMode(false)" in declaration(activity, "private MotionLyricsModeView buildMotionLyricsModeView()")
lost = activity[activity.index("if (snapshot == null || !snapshot.hasUsableMetadata()) {"):]
assert "showMotionLyricsMode(false)" in lost[:lost.index('titleView.setText("ivLyrics")')]
# The page (a web view with the whole presentation) is built when the mode first opens, in both
# products, and a content rebuild keeps the one it has.
ensure = declaration(activity, "private MotionLyricsModeView ensureMotionLyricsModeView()")
assert "motionLyricsModeView = buildMotionLyricsModeView()" in ensure and "rootView.addView(motionLyricsModeView" in ensure
content = declaration(activity, "private View buildContentView()")
assert "buildMotionLyricsModeView()" not in content
kept = content[content.index("if (motionLyricsModeView != null) {"):]
assert "detachFromParent(motionLyricsModeView)" in kept[:kept.index("debugPanel = buildDebugPanel()")], \
    "the page survives a content rebuild"

# The entry heads the track panel with PiP and LP mode, so it is in the menu a long press on the
# title opens and in the current-track settings; the module's sheet has it too.
panel = declaration(activity, "private LinearLayout buildLyricsLanguageSettingsPanel()")
assert panel.index("panel.addView(buildPlayerModeRow(), matchWrap())") < panel.index("lyricsPopupTabButtonsContainer = new LinearLayout(this)")
assert "lyricsMetaMenuScrollView.addView(lyricsLanguageSettingsPanel" in declaration(activity, "private void showLyricsMetaMenuPopup(View anchor)")
row = declaration(activity, "private LinearLayout buildPlayerModeRow()")
standalone = row[row.index("if (!embedded) {"):row.index("motionModeTile = playerModeTile(")]
assert "enterLyricsPictureInPicture()" in standalone and "openPlayerMode(false)" in standalone
assert "openPlayerMode(true)" in row[row.index("motionModeTile = playerModeTile("):]
opened = declaration(activity, "private void openPlayerMode(boolean motion)")
assert opened.index("motionLyricsUnavailableMessage()") < opened.index("dismissLyricsMetaMenuPopup()") \
    < opened.index("showSettingsPanel(false)") < opened.index("showMotionLyricsMode(true)")
# Left out when animations are off; dimmed for a song that cannot play.
modes = declaration(activity, "private void updatePlayerModeRow()")
assert "MotionPreferences.animationsEnabled(this)" in modes and "motionLyricsUnavailableMessage() == null" in modes
assert "updatePlayerModeRow();" in declaration(activity, "private void setLyricsResultOnViews(LyricsResult result)")
menu = declaration(activity, "private void buildCurrentTrackSettingsMenu(LinearLayout content)")
assert "settingsAction(" not in menu and 'sections.add(ui("motion.mode"))' in menu and "attachTrackPanelToSettings()" in menu

# Opening the mode from settings closes them as the window turns: the rebuild must not bring them
# back over the mode, and it hands the settings the rebuilt lyrics page's track panel.
settings = declaration(activity, "private void showSettingsPanel(boolean show, boolean startAtHome)")
assert "settingsPanelClosing = !show;" in settings
assert "settingsPanelClosing = false;\n                        settingsPanel.setVisibility(View.GONE);" in settings
rotated = declaration(activity, "private void rebuildContentViewAfterConfigurationChange()")
assert "isSettingsPanelVisible() && !settingsPanelClosing" in rotated
assert rotated.index("rebuildOrientationSensitivePages()") < rotated.index("attachTrackPanelToSettings()")
assert "reopenLyricsPageAfterMotionMode()" in rotated
attach = declaration(activity, "private void attachTrackPanelToSettings()")
assert "detachFromParent(settingsTrackPanel)" in attach and "settingsTrackPanel = lyricsLanguageSettingsPanel" in attach

# The page's position reports are fresh readings, and the song is resent only when it changed.
sync = declaration(activity, "private void syncMotionLyricsMode()")
assert "currentLyricsResult != motionSongLyrics" in sync and "currentLyricsPlaybackPosition(track)" in sync
assert "!settings.provider.translationOnly() && settings.hasApiKey() && settings.hasModel()" in sync
assert 'new RawResponseDiskCache(context.getApplicationContext(), "motion_direction", 500)' in repository
assert "motionDirectionDiskCache.clear()" in declaration(repository, "void clearCache()")

strings = declaration(i18n, "private static void addMotionLyricsStrings(")
assert '{"motion.mode", "motion.unavailable", "motion.directing", "motion.direct_failed"}' in strings
assert strings.count('\n                {"') == 22, "one row per UI language"
assert i18n.count("addMotionLyricsStrings(languages);") == 1
print("Motion lyrics wiring regression passed")
