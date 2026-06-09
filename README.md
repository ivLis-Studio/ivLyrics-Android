# ivLyrics Android

Android companion app prototype for ivLyrics karaoke lyrics.

## Scope

- Reads the currently active media session through Android notification listener access.
- Uses the playing track title, artist, album, and duration to search Spotify Web API.
- Reads `external_ids.isrc` from ranked Spotify search candidates.
- Requests ivLyrics community `sync-data` by ISRC.
- If `sync-data.source.lrclibId` exists, loads that LRCLIB entry directly.
- Otherwise searches LRCLIB and ranks candidates against the current track and sync-data line shape.
- Applies the ivLyrics karaoke sync-data format, including multi-vocal `parallel.parts`, `ranges`, `join`, `hiddenRanges`, `speaker`, and `kind` effects.
- Falls back to LRCLIB line-synced lyrics when `sync-data` cannot be used.

## Runtime notes

The app does not use Spotify Android SDK/App Remote. Android media sessions do not expose ISRC, so the app resolves ISRC by searching Spotify Web API from the currently playing title and artist. The in-app log panel shows the token/search flow, Spotify candidates, selected ISRC, sync-data response, LRCLIB direct load/search result, and fallback reasons.

For sync-data requests, the app keeps the Spotify desktop client style request headers currently used by the prototype:

- `Origin: https://xpui.app.spotify.com`
- `Referer: https://xpui.app.spotify.com/`
- `X-ivLyrics-Client: android`

## Build

Open this folder in Android Studio and let Gradle sync the project. The project targets the Android 16 QPR2 SDK package currently installed as `platforms/android-36.1`, so it uses the Android Gradle Plugin 8.13+ minor API `compileSdk` syntax.

### Release signing

Release signing is optional for local builds and required for GitHub release builds.

Create a release keystore outside Git:

```bash
keytool -genkeypair \
  -v \
  -keystore ivlyrics-release.jks \
  -alias ivlyrics \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

For local signed release builds, add these ignored values to `local.properties`:

```properties
IVLYRICS_RELEASE_STORE_FILE=/absolute/path/ivlyrics-release.jks
IVLYRICS_RELEASE_STORE_PASSWORD=...
IVLYRICS_RELEASE_KEY_ALIAS=ivlyrics
IVLYRICS_RELEASE_KEY_PASSWORD=...
```

GitHub Actions release builds require these repository secrets:

- `ANDROID_KEYSTORE_BASE64`: `base64 -i ivlyrics-release.jks`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep the keystore and passwords backed up. Losing the keystore prevents installing signed updates over earlier signed APKs.
