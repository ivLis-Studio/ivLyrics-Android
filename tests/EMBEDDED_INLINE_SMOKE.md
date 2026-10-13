# Mounted inline lyrics smoke test

`EmbeddedSmokeActivity` is included only in the debug and isolated `.qa` app variants.
It mounts the production shared inline surface and card, drives a synthetic
`MediaSession`, and checks the actual `MainLyricPreviewView` rows and drawing origins.

Build/install the QA APK using the normal project workflow. Select the intended
ADB device explicitly, then start the fixture in a fresh QA process:

```sh
adb -s DEVICE_SERIAL shell am force-stop kr.ivlis.ivlyricsandroid.qa
adb -s DEVICE_SERIAL shell am start -W -n kr.ivlis.ivlyricsandroid.qa/kr.ivlis.ivlyricsandroid.EmbeddedSmokeActivity
adb -s DEVICE_SERIAL logcat -d -s IvLyricsSmoke:I '*:S'
```

Success is `ALL_CHECKS_PASSED` after the current launch, with no `CHECK_FAILED`.
Check the current launch's timestamps because `logcat -d` may contain older runs.
The fixture finishes paused at 7400 ms on track two, with the inline original and
translation above the larger card. It checks:

- Canonical synthetic Spotify track IDs pass the shared song guard and the inline
  child is attached, measured, compact, and externally driven.
- Both vocal timelines survive in the displayed original row; a prepared translation
  appears under the default original-only preview preference without changing it.
- Selecting pronunciation adds its row between original and translation.
- Left, right, and center settings update the mounted renderer's drawing origin.
  Overflow retains its start hold and time-dependent horizontal scrolling in all modes.
- A real MediaSession seek changes the displayed inline row and playback position;
  switching tracks replaces the displayed lyrics and retains the new track's timeline.
- Selecting no preview hides and clears the inline surface, and restoring the original
  selection restores the current original and translation.

After success, the inline click route opens the existing full lyrics page:

```sh
adb -s DEVICE_SERIAL shell am start -W -n kr.ivlis.ivlyricsandroid.qa/kr.ivlis.ivlyricsandroid.EmbeddedSmokeActivity --es action inline-click
```

Existing `card-click`, `full`, and `settings` actions remain available. Opening a page
is a manual visual/navigation check, separate from the automated fixture assertions.
The launched full-page Activity uses the QA application's normal settings and storage;
the fixture's isolated alignment/preview preferences are not passed to that Activity.
Use the `.qa` variant for this manual check so the installed release application's
settings remain separate.

Synthetic rows are published before each track reaches the engine, so the fixture
uses the shared in-memory result path without provider requests. A manual cache is
also populated only under `files/embedded_smoke`. Preference names use the
`embedded_smoke_` prefix; the fixture does not modify normal user preferences. The
synthetic IDs are `spotify:track:0000000000000000000001` and
`spotify:track:0000000000000000000002`; they must never be sent to a provider.

This tests the shared Android rendering and media-session path. The module's actual
Spotify Compose mount, touch handoff, and account/provider behavior still require
the separate live Spotify QA run. No device result is implied by a passing host build.

## Lyrics sheet over the fixture (frame measurement)

`SheetSmokeActivity` (debug/QA only) opens the shared page above the fixture, in the
same task and process, as Spotify opens the module's sheet over its player. Its host
is the Spotify module's own `LyricsSheetTransition`, loaded from the installed module
debug APK with the QA application's class loader as parent. Build both APKs from the
same commit, install both, and pass the module's installed path:

```sh
MODULE=$(adb -s DEVICE_SERIAL shell pm path dev.ivlyrics.spotify.module | sed 's/^package://' | tr -d '\r')
adb -s DEVICE_SERIAL shell setprop log.tag.IvLyricsFrame DEBUG   # before the fixture starts
adb -s DEVICE_SERIAL shell am start -n kr.ivlis.ivlyricsandroid.qa/kr.ivlis.ivlyricsandroid.EmbeddedSmokeActivity --es action sheet --es moduleApk "$MODULE"
```

With the frame probe enabled, `SHEET` lines give each drawn sheet frame's time and
translation, and `LV`/`PV` lines name their Activity (`owner=`), so the page and the
player's card and inline preview can be counted separately. While the sheet rests
fully open, the paused card and inline preview log `COVER`, and `UNCOVER` when a
drag, close or pause reveals them again, or as soon as a touch lands on the sheet's
drag handle (`COVER` again when that touch ends without moving the sheet). The
`kr.ivlis.ivlyricsandroid.debug.FIXTURE_PLAYBACK` broadcast plays or pauses the
synthetic session (`--ez play`, `--el position`) or switches to the dense measurement
track (`--es track three`). Launch the fixture with `--ez reduceMotion true` when
paused captures must be compared pixel for pixel.

For drag measurements, `kr.ivlis.ivlyricsandroid.debug.SHEET_TOUCH` plays a synthetic
touch on the sheet through the Activity's own dispatch, with one move per display frame:
`--ef x 640 --ef y 156` (the drag handle strip above the title), `--el hold 80` before
moving, `--ef dy 900 --el ms 280` for the motion. `--ez release false` keeps the pointer
down at the end for a capture, and `--ez up true` lifts it. Each event logs a `TOUCH` line.
