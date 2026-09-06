# ivLyrics for Spotify

This module replaces Spotify's player lyrics card and full lyrics page using
the same `:shared` library as the standalone Android app. The original Spotify
APK, login and package are not changed by this module.

For music tracks the ivLyrics card is included even when Spotify reports no lyrics
or omits the lyrics section from its server-selected Now Playing cards. Lyrics are
resolved by ivLyrics independently; episodes and podcast-embedded tracks remain
excluded, and an existing lyrics card is never duplicated.

Build from the repository root with `./build.sh :spotify-module:assembleRelease`.
Output: `spotify-module/build/outputs/apk/release/spotify-module-release.apk`.
GitHub [Releases](https://github.com/ivLis-Studio/ivLyrics-Android/releases) also
attach this module as `ivLyrics-LSPatch-${tag}.apk`, alongside the separate
standalone Android app. This APK contains the ivLyrics module, not Spotify or a
prepatched Spotify APK.

Release builds use the existing `IVLYRICS_RELEASE_*` signing configuration when
provided. The publishing workflow requires it; a local build without it keeps
using the Android debug signing key. Keep the same key when installing updates.
A published module signed with the release key may not update an earlier locally
debug-signed installation. See [build and signing configuration](../ARCHITECTURE.md#build-and-verify).

Install the module, enable **ivLyrics for Spotify** in LSPosed and select Spotify
(`com.spotify.music`) as the scope. Force-stop Spotify before updating the module
APK, then reopen Spotify. The current physical-device setup uses rooted LSPosed.
LSPatch is a compatible legacy loader path but requires patching the host; this
repository migration does not require changing the installed Spotify app.

The version guard recognizes Spotify **9.1.80.2221** and **9.1.42.2058**. Other
versions are left untouched and logged as unsupported. Recognition alone does
not establish playback validation on every Android version or loader.

On Spotify **9.1.80.2221**, the module also connects to Spotify's native SingAlong
service. A single microphone button at the lower right of the full lyrics page,
beside the provider credits, cycles **OFF → LOW → HIGH → OFF**. It appears on page
entry or any screen touch and hides after three seconds without interaction.
It stays hidden while track support is being checked and for tracks without native
vocal-removal support. Activation retains Spotify's support, online-mode,
local-playback and audio-quality requirements; LOW-quality playback is excluded. The module does
not change Spotify's feature flags or make unsupported tracks available. This
control belongs only to the Spotify module, not the standalone app.

See [architecture](../ARCHITECTURE.md) for the host interfaces, resource context,
WebKit isolation, build outputs and editing boundaries. No source-copy or
source-replacement build step is used.
