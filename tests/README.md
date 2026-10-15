# Repository verification

Run these commands from the repository root:

```sh
./gradlew :shared:testDebugUnitTest :shared:lintDebug :app:lintDebug :spotify-module:lintDebug :app:assembleDebug :app:assembleQa :spotify-module:assembleDebug
python3 tests/run_lyrics_center_regression.py
python3 tests/run_appearance_regression.py
python3 tests/run_first_use_regression.py
python3 tests/run_vinyl_regression.py
python3 tests/run_interlude_handoff_regression.py
python3 tests/run_lyrics_metadata_cache_regression.py
python3 tests/run_metadata_regression.py
python3 tests/run_provider_attribution_regression.py
python3 tests/run_provider_overlap_regression.py
python3 tests/run_renderer_overlap_regression.py
python3 tests/run_frame_layout_pool_regression.py
python3 tests/run_render_idle_regression.py
python3 tests/run_card_presence_regression.py
python3 tests/run_karaoke_controller_regression.py
python3 tests/run_inline_gate_regression.py
python3 tests/run_inline_hooks_regression.py
python3 tests/run_inline_lifecycle_regression.py
python3 tests/run_supplement_loading_regression.py
python3 tests/run_repository_completion_regression.py
python3 tests/run_furigana_loading_regression.py
python3 tests/run_furigana_webview_regression.py
node --test tests/furigana_bridge.test.mjs
python3 tests/run_metadata_refresh_loading_regression.py
python3 tests/check_module_boundaries.py --built
python3 -m unittest discover -s .github/scripts -p 'test_release_metadata.py'
```

The appearance regression executes the real settings persistence/export/import and original-row
filter, plus the emitted video JavaScript's resize/ready/error paths. The LP regression runs the
production label bounds, translated accessibility text and spin clock with synthetic collaborators.
The interlude regression replays a 120 Hz handoff through the actual full-page retention and
preview methods, including seeks, reduced motion and unchanged lyric row/anchor geometry.
These three checks also run before release signing in Android Release.

`shared/src/test` contains the common JUnit checks, including initialization of
every language table. The host-side regressions execute production Java or
production method declarations with synthetic data. They cover renderer movement,
native track identity and late metadata, provider attribution and caches. The
boundary check verifies shared source ownership and confirms that the module's
merged manifest contains no standalone Activity, service, receiver or provider.
It also rejects direct standard WebKit or Spotify module references in shared
Java. When the private WebKit JAR exists, it checks that AndroidX class definitions
are isolated while public Chromium boundary interface names remain intact.
These checks complement physical playback/UI validation.

The first-use regression executes the production permission action, credential validation callbacks,
draft capture/restoration and in-flight button state with a controlled token validator. No Spotify
requests are sent. `FirstUseUiRegressionTest` runs the production onboarding and first-language dialog
in an unexported debug fixture with isolated storage. It covers light/dark, native 48dp switches and
touches, large text/landscape/RTL scrolling, language-rule persistence, Original only/cancel/stale-song
handling, closing uncommitted edits, and unsaved credentials across back/language changes/Activity
recreation. Landscape tests assert the real Activity orientation and native window dimensions.
Native taps wait for an attached, focused, laid-out target and quiet rendering by its ancestor
ScrollViews, not just unchanged bounds: a clamped OverScroller can otherwise consume the first
DOWN. A queued clamped-scroll regression requires the same single pointer tap to advance the
onboarding phase; there are no click retries or programmatic `performClick()` fallbacks.
Its OS window captures are atomically saved under the app's external-files
`first-use-screenshots/` directory, including `failure-<test>.png` captured before fixture cleanup
when a native assertion fails. Run on a disposable emulator with:

```sh
adb shell wm fixed-to-user-rotation enabled
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=kr.ivlis.ivlyricsandroid.FirstUseUiRegressionTest
```

The UI workflow also runs the whole unfiltered native suite and collects first-use PNGs into
`build/reports/settings-ui/first-use/` before UTP uninstalls the fixture app. Changes to
`FirstUseI18n.java`, the host regression, or either native test/fixture trigger that workflow.
For local screenshot retention without UTP cleanup, assemble/install the debug app and its
androidTest APK, run the same class with `adb shell am instrument -w -r -e class
kr.ivlis.ivlyricsandroid.FirstUseUiRegressionTest
kr.ivlis.ivlyricsandroid.test/androidx.test.runner.AndroidJUnitRunner`, then pull
`/sdcard/Android/data/kr.ivlis.ivlyricsandroid/files/first-use-screenshots/`.

The offline UI fixture verifies the notification-settings intent but does not grant access or make
a live Spotify token request; validate those platform/account paths separately on a device.

The `LocalAi*Test` JUnit classes cover the Local AI provider without a model or native
library: tier files and the mobile default, idle choices, block splitting, markers, vocal
parts and repeats, the desktop prompts and fallback, language mapping, immediate failures for
a missing model, an unsupported language or device, request joining and replacement, idle
release, download resume against a local HTTP server, and every language's strings. The
boundary check confirms that the native wrapper and its pinned downloads live in `shared`; with
`--built` it opens each APK, verifies every engine library against the asset manifest's SHA-256
and confirms that no APK gained `lib/` native libraries.

`python3 tests/run_local_ai_host_check.py MODEL_GGUF [--download-check]` is an opt-in check for
macOS arm64 (not run in CI). It builds the same JNI wrapper with `clang++` against the pinned
llama.cpp b11364 macOS release, then translates Japanese lyric lines into Korean through
`LocalAiRuntime` on the CPU with a downloaded Light model (`Hy-MT2-1.8B-Q4_K_M.gguf`), reporting
outputs and timings. `--download-check` also resumes a partial Hugging Face download twice and
compares it with the model file. It does not cover Android's linker, device memory or LSPatch.

The lyrics-metadata-cache regression compares the production accessibility label,
content-end and visual-state methods with frozen commit `cf69ad2`. It runs the
immutable lyric model and pronunciation normalization directly, including word
units, inline styles, ruby, RTL, overlapping vocals, pause/seek, replacement
supplements/timing and live interlude-label changes. Instrumented preparation and
syllable-visit counts show repeated-frame work removed without claiming Android
Canvas pixel equivalence or real-device FPS. The overlap regression also extracts
the production display-row class so its metadata lifecycle stays covered.

The frame-layout-pool regression reproduces the eleventh-row overflow in frozen
commit `1c8314c`, then runs the current production frame bounds and layout pool.
It covers long sparse overlaps, normal/manual scrolling, backward seeks, capacity
growth, object reuse and release of stale lyric references after shrinking or
clearing a frame. It also runs the production `onSizeChanged`: a height-only resize
(the lyrics page's top padding follows a sheet drag) keeps the wrapped rows, whose
cache key carries the width, while a width change still re-wraps them. It requires
only a JDK and does not claim Android pixel or physical-device validation.

The render-idle regression compares production methods with frozen commit
`02ebb13`. It checks immediate offset preference changes and malformed JSON
cleanup, paused seeks/resume and effects, motion-setting changes while paused,
empty/loading states, and loading gradient geometry across size/alpha changes.
It counts JSON parsing, invalidation requests, gradient allocation and preference
reads using synthetic Android collaborators and the pinned JSON dependency.
It also runs the page's per-frame feed of the lyrics renderers against `291b1e6`:
a closed lyrics page keeps receiving each frame's clock while its close animation
slides it away (the frozen feed stopped as soon as the close began), while a hidden
page, a page beneath vinyl mode, landscape and picture-in-picture are fed as before.
The preview's frame-synchronized clock is also covered: a position supplied with a
Choreographer frame time paints exactly that position (even when the drawing time leads
it by one frame), a frame without a host tick continues on the same clock, and a
static pause still avoids redundant redraws.
It does not measure device FPS or Canvas pixels.

The same entry point plays the standalone lyrics page's open, back-close, drag-close and
settle-back slides through the production page and gesture methods (synthetic View,
ViewPropertyAnimator, MotionEvent and a 120 Hz frame clock), with a tap, a jitter, a drag
up or a drag down landing early, midway or late in the slide on the player page's edge,
its lyric preview, the page's drag handle or its title. Against `291b1e6`, which froze
the page wherever such a touch caught it, every run must come to rest fully open or fully
closed. A tap or jitter leaves the slide frame-for-frame as it was, with no tap action.
A drag carries the page from where it was without a translation or top-padding jump. The
player page is shown wherever the page reveals it, and the lyrics are fed exactly while
the page is shown. Touches outside a slide must match the frozen methods frame by frame.
A title tap that moved the page a few pixels now settles it back, and a drag that begins
while the module's sheet is closing leaves the page's top padding alone. Each run's
outcome is written to `build/reports/regressions/render-idle/page-slides-result.txt`.
The in-app browser sheet's open, back-close, handle drag-close and settle-back slides are
played the same way, with each gesture on its handle or its web page. Every run must
come to rest; a tap or jitter leaves the slide as it was, and reaches neither the page
nor the handle's click. Touches outside a slide must match `291b1e6` frame by frame,
including the events the page receives. A handle tap that moved the sheet now settles it
back. A close that ended a Discord login is left to finish, because that login cannot
resume.
This exercises the page logic only, not Android touch dispatch or device timing.

The inline hook regression runs production Xposed callbacks against synthetic
Spotify 9.1.80 class shapes, including both upstream menu availability checks,
DJ/mixing, saved visibility preferences, video restrictions, and menu-scope cleanup.
The inline lifecycle regression executes the production preview render/update methods
against synthetic playback and view collaborators. It checks restoration after DJ
speech, missing metadata, and toggling the preview, including loading and no-lyrics
states, plus removal of completed supplement rows from retained/hidden views.
It also executes the real language detector and bridge language adapter before
repeated renders: missing lyrics and undetectable text retain the default locale,
preserve the existing three-second empty preview, and recover with detected lyrics.
The supplement-loading regression uses the production AI repository with
a controlled callback queue to check streaming/completion races, independent
pronunciation and translation progress, errors, and disabled language rules. A
synthetic SSE connection verifies provider completion without waiting for transport
EOF, including final text, keepalives, nonterminal events, and provider errors.
The repository-completion regression calls the full production `loadSupplements`
entry point through an in-memory provider connection. It checks pronunciation-only,
translation-only and concurrent requests, combined and per-task memory caches,
cached pronunciation with live translation, provider errors and disabled source
rules. Overlapping vocal parts, blank/interlude rows, syllable timing, existing
furigana and provider identity must survive generation and cache restoration, so
the activity's base-result check accepts terminal callbacks.
The furigana-loading regression checks that in-flight requests survive cache resets
and finish through success, error or the existing timeout. It also covers repeated
cold/warm loads, ignored superseded requests, track changes and a distinct furigana
loading label.
The furigana-WebView regression executes the production repository bootstrap with
distinct module and host asset contexts. It verifies module HTML loading, UTF-8
and HTTPS origin, terminal initialization/page failures, clean retry, stale bridge
callbacks, timeout and shutdown. The JavaScript bridge tests exercise library and
dictionary fallback, retry and CDN URL preservation without real network requests.
For opt-in real Android WebView and Kuromoji verification in a separate QA app,
see [the device smoke test](FURIGANA_WEBVIEW_DEVICE_SMOKE.md).
The metadata-refresh-loading regression executes the activity's production refresh
callbacks and provider-loading handler. It checks cached/current lyrics remain visible
with accurate supplement progress while metadata refreshes, and old AI callbacks
cannot restore loading after the activity switches generation or base result.
These checks run on the host and do not start an emulator or operate a device.

The card-presence regression runs the production Spotify module list helper with
synthetic cards. It checks insertion when Spotify supplies no lyrics card, placing
ivLyrics before live events while preserving other cards and their order. Existing
lyrics cards (including subtypes and duplicates), empty and immutable lists, absent
candidates, and repeated mapping are covered. The production adapter method also
checks song eligibility with synthetic reflection bindings: Spotify's `has_lyrics`
flag does not affect song cards, and episode-backed tracks and other non-song URIs
remain excluded. It requires only a JDK and does not simulate Android hooks or
claim that device rendering passed.

The karaoke-controller regression runs the production module controller with
deterministic scheduling and synthetic Spotify collaborators. It checks native
preparation and acknowledgement ordering, volume changes, stale track events,
playback/support gates, timeouts and cleanup on page or track changes. It requires
only a JDK and Python. Real native routing, track support, button placement and
audible vocal reduction require separate physical-device checks.

The release metadata tests use synthetic APK bytes and the real metadata
generator. They verify shared release versions and independent product/package fields, existing
checksums and compatibility fields, bilingual download descriptions, and one
standalone version manifest containing both products. They require only Python;
no GitHub, AI, webhook, Android SDK, or signing key is accessed.

Reports are written under `build/reports/regressions`; normal Gradle test reports
remain in `shared/build/reports/tests`. No test depends on another workspace checkout,
an installed Spotify APK, credentials, an account, or a connected Android device.

Use JDK 17 or newer via `JAVA_HOME` or `PATH`. Set `ANDROID_HOME` (or
`ANDROID_SDK_ROOT`) to an SDK containing `platforms/android-36.1/android.jar`.
The standard macOS and Linux SDK locations are also recognized. An alternate
installed platform can be selected with `IVLYRICS_TEST_ANDROID_PLATFORM`.
By default each project's debug classes are preferred, with release classes used
when debug classes are unavailable. `IVLYRICS_TEST_VARIANT=release` explicitly
selects release classes for every project. The `--built` manifest check likewise
accepts debug or release APKs; use `--built --variant release` to select release
explicitly. The host tests do not start Gradle themselves.

The JSON dependency is downloaded on first use from Maven Central into
`build/test-dependencies` and verified against a pinned SHA-256 digest. Subsequent
runs use that verified local dependency. The renderer test has no downloaded
dependency. The optional metadata test arguments accept two explicitly supplied
public JSON fixtures; the default run uses only synthetic data.

GitHub Actions runs common tests, lint for all three modules, both debug APK
builds, the standalone QA variant and all host regressions for pull requests and
branch pushes. Debug and QA APKs are uploaded with the test/lint reports.
The tag/manual release workflow signs and publishes the standalone app and
Spotify module using the existing release key. Its module asset name excludes
`-release`, preserving older standalone updater selection. Pull-request checks
continue to run without release secrets and produce debug-signed verification
APKs rather than APKs signed with the release key.

Detailed scopes:

- [Lyrics center regression](LYRICS_CENTER_REGRESSION.md)
- [Provider attribution regression](PROVIDER_ATTRIBUTION_REGRESSION.md)

## Sync metadata reporting

`python3 tests/run_sync_metadata_reporting_regression.py` compiles the production sync-data fetch/HTTP/parser and frozen `1888014` baseline against an in-memory URL transport. It compares result payloads and privacy GET counts and verifies acknowledged metadata reuse only within one load, changed fields, cache clearing during a response, cancellation, malformed/error/negative responses, raw caches, and contributor privacy refresh. No real API or device is used. Requires the same JDK, compiled shared classes, Android SDK, and pinned JSON dependency as the other repository regressions. Both CI workflows run it.

The render-idle regression also compares preview ruby draw arguments with frozen 1.3.18 source across font, text size, density, ruby/source replacement, alpha and playback fill changes; it counts text measurement calls without claiming device raster/FPS equivalence.

## Native settings and track-popup UI

`python3 tests/run_settings_ui_regression.py` runs SDK-independent contracts against
the production regeneration-state method and all 22 production locale tables. It
also guards the input-height order, single dropdown chevron and debug-only offline
callback boundaries. This is not a native layout or visual acceptance test.

Run the actual Android widgets on an API 35 emulator with a software keyboard:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=kr.ivlis.ivlyricsandroid.SettingsUiRegressionTest
```

Runner: `androidx.test.runner.AndroidJUnitRunner`. The unexported
`SettingsUiHarnessActivity` is in `app/src/debug` only. It builds the production
`BaseLyricsActivity` settings and track popup, uses isolated fixture preferences,
and intercepts live playback, AI/metadata generation, video selection and LRCLIB
search at debug overrides. No Spotify installation, account, API key or real
repository request is needed. Actual `MotionEvent` taps verify saved switch state,
regeneration loading/duplicate-click protection, and video/search callback wiring.

Five scenarios cover Korean 320×640 dp portrait, Korean 568×320 dp landscape at
1.6× font, English 280 dp width at 1.8×, German landscape at 2× and Arabic RTL at
1.6×. Native view captures named `settings-*`/`track-*` use those deterministic
component dimensions. Separate `window-*` captures are real emulator windows,
including tapped controls and the software keyboard. Files ending in
`-software-root-diagnostic.png` always use `View.draw` on the actual window tree;
they are useful even after an emulator GPU failure but do not replace genuine
window screenshot/visual acceptance. The suite requires genuine captures too.
The keyboard phase restores the real window, taps its native search editor,
waits for a focused/active input connection, and requires actual IME visibility
from the activity's framework window insets (not just a successful show request).
Tests measure 48 dp inputs,
buttons and visible hit rectangles, text clipping, exactly one dropdown chevron,
and the actual rendered switch-thumb aspect and drawable padding (including
disabled-state alpha). Native editor checks also verify that positive/negative
text scrolling does not move the clipped touch frame; pointer coordinates use
that frame, not the RTL editor's scrolled content rectangle. Review the images
before claiming visual acceptance.

Screenshots are PNGs in the target app's external files directory. Pull them
while the emulator and app are still present:

```sh
adb pull /sdcard/Android/data/kr.ivlis.ivlyricsandroid/files/ui-regressions ./ui-regressions
```

AGP's connected-test UTP teardown uninstalls the target and test APKs in the
current CI setup, removing app-specific external files. Collect this directory
while tests are running (before teardown), not just after Gradle returns; use
the CI capture helper for continuous pulls. Capture streams use `*.png.tmp`
and are renamed to `*.png` only after close; collect only completed `*.png`
files to avoid racing partially written evidence. The log records each saved absolute
path. Hardware/GPU screenshots remain distinct from software diagnostics.

The test reports are under `app/build/reports/androidTests/connected/debug` and
`app/build/outputs/androidTest-results/connected/debug`. This harness does not
prove OEM/Samsung rasterization, a real LSPatch host's native loading, Spotify
authentication, or device playback behavior.
