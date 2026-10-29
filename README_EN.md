<img width="2208" height="512" alt="ivLyrics English banner" src="https://github.com/ivLis-Studio/ivLyrics/blob/main/preview_readme_en.png?raw=true" /><br>
PLATFORM : <a href="https://github.com/ivLis-Studio/ivLyrics">ivLyrics (PC)</a> | <a href="https://github.com/ivLis-Studio/ivLyrics-Android">Android</a> | <a href="https://github.com/ivLis-Studio/ivLyrics-IOS">iOS</a><br>
MODULE : <a href="https://github.com/ivLis-Studio/ivLyrics-overlay">Overlay</a> | <a href="https://github.com/ivLis-Studio/ivLyrics-helper">Helper</a>

# ivLyrics Android

[한국어](README.md) | English

Follow the music you play in Spotify with lyrics, translations, and pronunciation guides. Use ivLyrics Android as a separate app, or use the module to see lyrics inside Spotify. Spotify handles music playback.

[Download the latest release](https://github.com/ivLis-Studio/ivLyrics-Android/releases/latest) · [Release history](https://github.com/ivLis-Studio/ivLyrics-Android/releases) · [Help on Discord](https://discord.gg/2fu36fUzdE)

## Choose how to use it

GitHub Releases contain **two different APKs**. Matching version numbers do not make them interchangeable. These instructions use direct APK installation.

| Filename | What it installs |
| --- | --- |
| `ivLyrics-Android-v<version>-release.apk` | The **standalone app**, opened separately from Spotify. No root or patching required. Follow the steps below. |
| `ivLyrics-LSPatch-v<version>.apk` | The **Spotify module**. Enable it with LSPosed or apply it to Spotify with LSPatch. Follow the [module guide](spotify-module/README.md#english). |

The module APK does not contain Spotify or a prepatched Spotify app. Installing it alone will not change Spotify's lyrics.

## What can I see?

- Lyrics from LRCLIB, Lyrically (Paxsenix), LyricsPlus, and Unison, with ivLyrics community sync data. Choose providers and their lookup order.
- Plain, line-synced, or word/character-level karaoke lyrics, depending on source data, with adjustable fill effects, vocal colors, and instrumental-break labels.
- Separate translation and pronunciation rules for each song language, plus Japanese furigana.
- In the standalone app: full lyrics, landscape layout, Picture in Picture (PiP), Vinyl mode, and a floating shortcut from Spotify.

Not every song has lyrics or detailed timing. Quality and availability depend on the data, providers, and network connection.

## Install the standalone app

You need **Android 8.0+**, Spotify installed and signed in, internet access, and a **Client ID and Client Secret** from a [Spotify developer app](https://developer.spotify.com/dashboard) you manage. The current first-run setup requires both credentials and **notification access**. Spotify and installation tools may impose additional OS or account requirements.

The current [Spotify Web API development-mode rules](https://developer.spotify.com/documentation/web-api/concepts/quota-modes) require the app owner to have Premium and limit authenticated users. Check eligibility before creating your developer app.

1. Download `ivLyrics-Android-v<version>-release.apk` from the [latest release](https://github.com/ivLis-Studio/ivLyrics-Android/releases/latest).
2. Open the APK to install it. If prompted, allow **Install unknown apps** for the browser or file app opening it.
3. Launch ivLyrics and choose your language and theme.
4. Follow the prompt to enable **notification access** for ivLyrics in Android Settings, then return. This reads the current song and Spotify media controls.
5. Select **Web API** in your Spotify developer app, enter its Client ID and Client Secret, and tap **Save and Start**. Expand the in-app registration guide if needed.

> The in-app Redirect URI guide still shows `https://localhost/`. Spotify's current [redirect rules reject `localhost`](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri). Register a URI that meets the Dashboard's official requirements. The standalone app currently uses Client Credentials, without a browser callback. If the old example prevents registration, report the error on Discord.

### Track updates with Obtainium

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/ivLis-Studio/ivLyrics-Android">
  <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60">
</a>

If you use Obtainium, add this repository with the button above. Because releases contain two APKs, **check the filter selects `ivLyrics-Android-…-release.apk` for the standalone app**. Module users should follow the [module update instructions](spotify-module/README.md#english).

## See lyrics for your first song

1. Play music in Spotify, then return to ivLyrics.
2. Once the title and artist appear, tap the lyric preview for full lyrics, or the album cover for Vinyl mode.
3. Open **Settings → Current track settings** for song language, provider, manual search, timing, and backgrounds. Long-pressing the title/artist area also opens the song menu.
4. Tap a lyric line or drag the progress bar to seek. Available controls depend on Spotify playback state and account restrictions.

The floating shortcut is optional. Under **Settings → Advanced**, allow **display over other apps** and enable the **Spotify screen-detection accessibility service**. The accessibility service detects whether Spotify's now-playing screen is in front. These permissions are separate from notification access and are not needed for basic lyrics. The shortcut only appears on Spotify's now-playing screen.

## Add lyrics from your own LRC file

Play a song, then open **Settings → Current track settings → Lyrics Providers → Import lyrics file** and select a `.lrc` or `.txt` file. Files must be UTF-8 or UTF-16 and no larger than 1 MB. LRC timestamps provide line-by-line synchronization.

The file is saved for the song selected when you opened the picker and takes priority over online lyrics. It survives app restarts and lyric-cache clearing. Use **Remove local lyrics for this track** in the same menu to return to online lyrics. This also works in the LSPatch/LSPosed module; imports are stored separately from the standalone app.

## Translation and pronunciation

Choose display and output languages in Settings, then enable and order services under **AI Providers**. Failed requests try the next enabled provider. Per-language rules let you show translation and pronunciation for Japanese songs but only translation for English songs, for example.

| Feature | Provider setup |
| --- | --- |
| Lyric translation | Bing Translate and Google Translate need no personal API key. DeepL requires an API key. All three are translation-only. |
| AI translation and pronunciation | Configure a supported service such as Gemini, an OpenAI-compatible API, or Claude, including its connection and model. Pronunciation needs a configured AI provider. |
| On-device lyric translation | Enable **Local AI** and download a model. It does not generate pronunciation or translate titles and artists. |
| Readings above Japanese kanji | Enable Japanese furigana separately from translation and AI pronunciation. |

Local AI is off by default and needs **Android 9+ with a 64-bit ARM process**. Choose **Local Light / Local Medium / Local High**, about 1.13 / 1.47 / 1.91 GB per model download. Mobile defaults to Light. Runtime memory is needed too; a powerful device is recommended, so run the provider test first. Inside Spotify, the process and loader may prevent the engine from loading.

### Cost and data use

Online providers receive lyrics and song information needed for the request. API pricing, quotas, and data policies vary by service. Keyless translation still uses external services and may become unavailable. Generated translations and pronunciation can be wrong.

**Enabling Local AI does not give it priority. Move it ahead of online providers** to try it first; providers above it can receive the request first. **For local-only lyric translation, disable all other online translation providers.** Inference runs on-device, but model downloads, lyric lookup, and other online features use the internet. This does not guarantee an offline app or prevent all external data transfers.

Translations and pronunciation are cached. Clear AI cache, current-song lyrics, or all lyric cache in Settings. The standalone app and Spotify module keep separate settings, caches, and downloaded models.

## Make it your own

- Use **Appearance** for fonts, alignment, and backgrounds, and **Player** for landscape, PiP, and Vinyl options. Tapping the title opens Spotify by default; the action is configurable.
- Adjust timing globally, per song, or per Bluetooth device in 10ms, 50ms, or 100ms steps. **Positive values show lyrics earlier; negative values show them later.**
- Monthly supporters signed in with Discord can save Android settings to the cloud under **Advanced**. API keys, account information, per-song offsets, and downloaded models are excluded.

## Updates and troubleshooting

For the standalone app, use **Settings → Advanced → Check for updates**, [GitHub Releases](https://github.com/ivLis-Studio/ivLyrics-Android/releases/latest), or Obtainium. Update with a signed APK for the same product. If signatures differ, check the installation source before uninstalling; removing the app can erase local settings and caches. Module updates need a loader restart or repatching; see the [module guide](spotify-module/README.md#english).

| Problem | What to check |
| --- | --- |
| Setup will not finish | Client ID/Secret, Web API selection, developer-account eligibility, and notification access. If Android blocks access, check its restricted-settings message. |
| No current song | Play music in Spotify, check notification access, and restart playback. |
| Missing lyrics or artwork | Check internet and the Spotify credential-save result. Try another provider or manual LRCLIB search in current-track settings. |
| Wrong lyrics or timing | Try manual search, clearing that song's cache, or song/Bluetooth offsets. Detailed sync data may be unavailable. |
| No floating shortcut | Check overlay and Spotify-detection accessibility permissions, then open Spotify's now-playing screen. |
| Translation without pronunciation | Check AI credentials/model and the pronunciation rule for that song language. Translation-only providers cannot generate pronunciation. |
| Local AI fails, or a service returns 403/429 | Check the local provider test, device requirements, and model download. For online errors, check account access/quotas and wait before retrying. |

Ask on [Discord](https://discord.gg/2fu36fUzdE). Include **standalone app or module**, app and Spotify versions, device/Android version, song link, provider, reproduction steps, and exact error. Redact API keys, Client Secrets, and authentication tokens.

## Credits

Thanks to ivLyrics community sync creators and contributors, [LRCLIB](https://lrclib.net/), Lyrically (Paxsenix), [LyricsPlus](https://github.com/ibratabian17/lyricsplus), [Unison](https://github.com/better-lyrics/unison), and translation providers. Check provider and creator credits on the lyrics screen. Local AI uses Tencent Hy-MT2 and llama.cpp. Existing font and library license notices remain in app resources.

<details>
<summary>Development, builds, and verification</summary>

See [ARCHITECTURE.md](ARCHITECTURE.md) for product structure, builds, and signing; [tests/README.md](tests/README.md) for verification; and the [module guide](spotify-module/README.md#english) for Spotify integration and installation constraints. Shared lyrics, translation, and UI code lives in `shared`. Developer and test documentation is maintained separately.

</details>

## Disclaimer

> **Unofficial project**
>
> This project and its contributors are not affiliated with, authorized by, endorsed by, or officially connected to Spotify, its affiliates, or subsidiaries. ivLyrics Android is an independent, non-profit, unofficial Android app and Spotify module developed by a volunteer team to provide the ivLyrics experience on Android.
>
> **Trademarks**
>
> “Spotify” and related names, marks, emblems, and images are registered trademarks of their respective owners. They are used for identification and reference only and imply no association with their owners. This project does not intend to infringe those trademarks or harm their owners.
>
> **Limitation of liability**
>
> The app and module are provided “as is” and used at your own risk. Developers and contributors are not liable for claims, damages, legal consequences, or other liability arising from their use or related dealings. You are responsible for the consequences of using them.
>
> **Copyright and terms**
>
> This project does not claim ownership of, or grant licenses for, lyrics, translations, videos, or other third-party content. You must check and comply with applicable copyright laws, platform policies, API terms, and local regulations, and are responsible for storage, reproduction, distribution, transmission, or commercial use through this project.
