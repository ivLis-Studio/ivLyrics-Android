#!/usr/bin/env python3
"""Motion lyrics wiring: the page asset, the messages around it and the standalone-only entry.

The presentation itself is the desktop one (the page is built and tested in the ivLyrics
repository); MotionLyricsSongTest covers the song the page is given. This checks that the page,
its messages, its strings and the mode's entry and exit paths are in place.
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

# Standalone only, for songs that can play; a phone is held in landscape while it shows.
show = declaration(activity, "private void showMotionLyricsMode(boolean show)")
assert show.index("IvLyricsBridge.isEmbedded(this)") < show.index("MotionLyricsSong.canPlay(currentLyricsResult)") \
    < show.index("motionModeVisible = true") < show.index("holdLandscapeForMotionLyrics(true)")
assert 'ui("motion.unavailable")' in show
closing = show[show.index("motionModeVisible = false"):]
assert "holdLandscapeForMotionLyrics(false)" in closing and "applyKeepScreenOnSetting(" in closing
assert "mainPage.setVisibility(View.VISIBLE)" in closing and "refreshMainPagePlayback()" in closing
hold = declaration(activity, "private void holdLandscapeForMotionLyrics(boolean hold)")
assert hold.index("smallestScreenWidthDp >= 600") < hold.index("SCREEN_ORIENTATION_SENSOR_LANDSCAPE")
assert "setRequestedOrientation(orientationBeforeMotionMode)" in hold
assert "|| motionModeVisible" in declaration(activity, "private void applyKeepScreenOnSetting(")

# The pages rebuilt by the turn stay hidden under the mode; Back and a lost track close it.
rebuild = declaration(activity, "private void rebuildOrientationSensitivePages()")
assert "if (motionModeVisible) {\n            mainPage.setVisibility(View.INVISIBLE);" in rebuild
back = declaration(activity, "public void onBackPressed()")
assert back.index("showMotionLyricsMode(false)") < back.index("showVinylMode(false)")
assert "showMotionLyricsMode(false)" in declaration(activity, "private MotionLyricsModeView buildMotionLyricsModeView()")
lost = activity[activity.index("if (snapshot == null || !snapshot.hasUsableMetadata()) {"):]
assert "showMotionLyricsMode(false)" in lost[:lost.index('titleView.setText("ivLyrics")')]
content = declaration(activity, "private View buildContentView()")
embedded = content[content.index("if (!IvLyricsBridge.isEmbedded(this)) {"):]
assert "motionLyricsModeView = buildMotionLyricsModeView()" in embedded
assert "detachFromParent(motionLyricsModeView)" in embedded, "the page survives a content rebuild"

# The entry sits with LP mode in the current-track menu, and is left out when animations are off.
entry = activity[activity.index('settingsAction(modes, ui("vinyl.mode")'):activity.index("registerSetting(currentTrackSettingsSummary")]
assert entry.index("MotionPreferences.animationsEnabled(this)") < entry.index('settingsAction(modes, ui("motion.mode")')
assert "showMotionLyricsMode(true)" in entry

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
