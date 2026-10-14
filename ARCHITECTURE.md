# Android and Spotify module development

Both Android products are built by this repository's Gradle wrapper. A lyrics,
ivSync, translation, cache, icon, or rendering change belongs in `shared` and is
compiled into both APKs. Do not copy application Java into another project or
rewrite source text during a build.

| Module | Responsibility | Install package |
| --- | --- | --- |
| `shared` | Lyrics engine, providers, ivSync, shared UI/settings, fonts and resources | Android library |
| `app` | Standalone launcher, application initialization, permissions and system components | `kr.ivlis.ivlyricsandroid` |
| `spotify-module` | Xposed hooks, native metadata, resource context, Spotify navigation and vertical sheet | `dev.ivlyrics.spotify.module` |

`BaseLyricsActivity` owns the shared page. The original standalone
`kr.ivlis.ivlyricsandroid.MainActivity` is a thin subclass, preserving the
installed component name. `SpotifyLyricsActivity` provides the module resource
context and a `LyricsActivityHost` implementation for Spotify's window and sheet
gestures. `SpotifyLyricsHost` explicitly registers context and navigation through
`IvLyricsHost` before the shared engine initializes. Embedded mode follows this
registration; a debug-only Activity intent also supports synthetic UI fixtures.

Spotify's player stays drawn beneath the translucent sheet. Once the sheet has drawn
a frame fully open and at rest, `LyricsSheetTransition` pauses the player's ivLyrics
card and inline preview through `IvLyricsBridge.coverPlayerSurfaces`. Any drag,
close, page rebuild or Activity pause uncovers them first; they repaint the current
playback state for the frame in which the sheet starts to move.
A touch on one of the page's drag handles already resumes them while the sheet still
hides them, so a drag after a long cover starts from a player that is drawing again; a
touch that never moves the sheet covers them again when it ends.

In the standalone app the full lyrics page slides over the player page, which stays
laid out beneath it behind an empty reveal clip. Once the lyrics page rests fully open,
the player page is hidden as in embedded mode, so its progress bar and lyric preview
stop drawing. A drag, close or rebuild that reveals any of it shows it again and
repaints the current playback state in that same frame.
A touch that lands while the lyrics page slides open, closed or back does not stop the
slide. If it moves like a drag (12 dp) before the slide ends, the page follows it from
where it is and settles by the usual rule on release; otherwise the slide finishes as
it would have, and the touch triggers nothing else.
The in-app browser sheet follows the same rule, using its handle's and page's own drag
thresholds. A touch on a sliding sheet never reaches the web page. A close that ended a
Discord login always finishes, since that login cannot resume.

Standalone system components are registered only in the app manifest. The shared
manifest is empty. Some standalone feature implementation classes remain in the
library because the common settings/activity currently call them; Spotify's
embedded feature gates keep those settings and actions unavailable. Further
internal UI splitting can happen without making another copy of the renderer.

The shared library has its own `kr.ivlis.ivlyricsandroid.R`. Each APK has a separate
namespace and receives the library's resources through normal Gradle packaging.
Spotify still needs `ContextBridge`: it obtains resources/assets from the module
APK while preserving Spotify's package identity, preferences, files and playback.
An AAR alone cannot replace this runtime context boundary.

`WebViewSupport` is the common WebView interface. The standalone Application
registers standard AndroidX WebKit; Spotify registers its private backend before
creating lyrics views. `spotify-module/tools/prepare_webkit.py` relocates only
third-party dependency bytecode. Dependencies are SHA-256 pinned and cached under
the repository's `.gradle` directory. Chromium's public boundary interface names
remain unchanged for WebView interoperability.

## Build and verify

Use JDK 21, Python 3 and Android SDK platform 36.1. `build.sh` finds standard macOS
JDK/SDK locations when environment variables are absent; Linux/CI should provide
`JAVA_HOME` and `ANDROID_HOME` (or SDK configuration in `local.properties`).

```sh
./build.sh
# app/build/outputs/apk/debug/app-debug.apk
# spotify-module/build/outputs/apk/release/spotify-module-release.apk

./build.sh :app:assembleRelease
# Requires the existing IVLYRICS_RELEASE_* configuration for a signed release.
# Without it, app-release-unsigned.apk is produced.

./build.sh :app:assembleRelease :spotify-module:assembleRelease -PivLyricsRequireReleaseSigning=true
# Build both release APKs and fail if release signing is not configured.

./build.sh :app:assembleQa
# app/build/outputs/apk/qa/app-qa.apk, installed as kr.ivlis.ivlyricsandroid.qa
# Coexists with the production app and keeps separate data.
```

App release IDs and signing configuration names are unchanged. Both release
variants read the shared `gradle/release-signing.gradle` configuration:
`IVLYRICS_RELEASE_STORE_FILE`, `IVLYRICS_RELEASE_STORE_PASSWORD`,
`IVLYRICS_RELEASE_KEY_ALIAS`, and `IVLYRICS_RELEASE_KEY_PASSWORD`. The tag/manual
release workflow restores the existing repository signing key and requires this
configuration with `-PivLyricsRequireReleaseSigning=true`.

Without release signing configuration, a local module release build continues to
use the existing Android debug key; a standalone release remains unsigned.
Keep the same signing key when installing updates. A published module signed with
the release key may not update an earlier locally debug-signed installation.
The QA variant is debug signed and includes `EmbeddedSmokeActivity` for
synthetic metadata/pause/seek/track-switch and shared-result checks. It does not
replace the user's production app or its data.

See [tests/README.md](tests/README.md) for common unit tests, portable regression
fixtures and merged-manifest checks. PR/branch CI builds both products. Tag/manual
releases sign and publish both APKs:

- `ivLyrics-Android-${tag}-release.apk`: standalone Android app.
- `ivLyrics-LSPatch-${tag}.apk`: Spotify module for LSPosed/LSPatch.

The module filename deliberately has no `-release` suffix. Hyphens within its tag
are replaced with underscores so older standalone updaters continue selecting
the Android APK. The single `ivLyrics-Android-${tag}-version.json` retains the
standalone app's top-level version. Both products take their version name and
code from `ivLyricsVersionName` and `ivLyricsVersionCode` in `gradle.properties`.
Before publishing a new tag, update both properties: the version name must equal
the tag without `v`, and the code must exceed both APKs in the previous release.
The release workflow checks this before building and verifies the actual signed
APK versions again before collecting either file. Changing only the tag is rejected.
Each `apks` entry includes its product label, package and version alongside the
existing name, size and SHA-256. A manual release run with `publish=false` builds
and uploads signed verification artifacts without changing a GitHub Release.
No Spotify APK is included or repackaged by this release workflow.

## Local AI translation provider

`local-ai` ("Local AI") translates lyrics on the device with Tencent Hy-MT2 1.8B through
llama.cpp b11364, matching the desktop provider (`Addon_AI_LocalModel.js`). It lives in `shared`,
so both APKs carry it. It is translation-only (never titles, artists, pronunciation or research),
off by default and appended to the end of the unified provider order; while it is off, provider
cache keys are unchanged.

- `LocalAiTranslator` is the desktop algorithm (vocal parts, markers, repeats, 8-part/480-character
  blocks, line-by-line fallback). `LocalAiRuntime` owns the single engine: one worker thread, a test
  translation ahead of lyrics, newest lyrics request first (an identical request joins it), idle
  release and release after a native failure. Both are plain Java and covered by JVM tests.
- Model files (Light/Medium/High; mobile defaults to Light) are downloaded only from the provider
  card, resumable with HTTP Range and checked by size, into the active context's no-backup directory.
  Inside Spotify that is Spotify's own storage.
- The engine ships as assets under `localai/arm64-v8a`, not `lib/`, so neither APK's installable ABIs
  change. `shared/tools/prepare_local_ai.py` (Gradle task `prepareLocalAiNative`) verifies the SHA-256
  pinned release libraries and headers in `shared/tools/local_ai_native.lock.json`, builds the JNI
  wrapper in `shared/src/main/cpp/localai` with CMake and the NDK, strips everything and writes a
  SHA-256 manifest. Builds therefore need the SDK packages `ndk;29.0.14206865` and `cmake;3.31.6`.
- `LocalAiProvider` extracts and verifies the libraries on first use and loads them by path. The
  release libraries require Android 9 and arm64-v8a; elsewhere, or when a host process cannot load
  them (LSPatch), the provider reports an error and the next enabled provider translates.

## Editing policy

- Rendering, ivSync/provider fixes and common UI: edit `shared` once, verify both APKs.
- Standalone permissions, launcher and initialization: edit `app`.
- Spotify version mappings, native metadata, card placement and host transitions: edit `spotify-module`.
- Do not add Spotify classes or standard AndroidX WebKit imports to `shared`.
- Keep provider fetching/translation contracts independent of the host adapter.

Build success and fixture checks are separate from real playback validation.
Verify the physical device's card, full page, settings, pause/seek, track changes
and video background after host integration changes.
