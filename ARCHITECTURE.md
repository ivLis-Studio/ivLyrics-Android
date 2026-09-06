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
standalone app's top-level version. Each `apks` entry includes its product label,
package and individual version alongside the existing name, size and SHA-256.
No Spotify APK is included or repackaged by this release workflow.

## Editing policy

- Rendering, ivSync/provider fixes and common UI: edit `shared` once, verify both APKs.
- Standalone permissions, launcher and initialization: edit `app`.
- Spotify version mappings, native metadata, card placement and host transitions: edit `spotify-module`.
- Do not add Spotify classes or standard AndroidX WebKit imports to `shared`.
- Keep provider fetching/translation contracts independent of the host adapter.

Build success and fixture checks are separate from real playback validation.
Verify the physical device's card, full page, settings, pause/seek, track changes
and video background after host integration changes.
