# Repository verification

Run these commands from the repository root:

```sh
./gradlew :shared:testDebugUnitTest :shared:lintDebug :app:lintDebug :spotify-module:lintDebug :app:assembleDebug :app:assembleQa :spotify-module:assembleDebug
python3 tests/run_lyrics_center_regression.py
python3 tests/run_metadata_regression.py
python3 tests/run_provider_attribution_regression.py
python3 tests/run_card_presence_regression.py
python3 tests/run_karaoke_controller_regression.py
python3 tests/check_module_boundaries.py --built
python3 -m unittest discover -s .github/scripts -p 'test_release_metadata.py'
```

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
generator. They verify independent product/package/version fields, existing
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
