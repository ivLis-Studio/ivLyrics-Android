# ivLyrics Android

[한국어](README.md) | English

ivLyrics Android detects the song currently playing in Spotify and displays karaoke-style lyrics on Android using ivLyrics community sync data and LRCLIB lyrics.

The app reads the current title, artist, album, and duration from Android media notifications, then uses the Spotify Web API to fetch the ISRC and high-resolution artwork. It matches that metadata with ivLyrics sync data and LRCLIB lyrics to render character-level karaoke timing, multi-vocal colors, translations, pronunciation, and Japanese furigana.

## Features

- Detects the currently playing Spotify track
- Fetches ISRC, track metadata, and high-resolution artwork through the Spotify Web API
- Karaoke lyrics powered by ivLyrics community sync data
- Direct LRCLIB loading and LRCLIB search fallback
- Character-level fill, bounce animation, and multi-vocal colors
- Original lyrics, pronunciation, translation, and Japanese furigana
- Per-song-language translation and pronunciation rules
- Main player and full lyrics page
- Landscape player with a split lyrics layout
- Floating shortcut from Spotify to ivLyrics
- Clear lyric cache for the current track or all tracks

## Requirements

To use ivLyrics Android, you need:

- An Android 8.0 or newer device
- The Spotify app
- Internet access
- A Spotify Developer account
- Your own Spotify Client ID and Client Secret
- Android notification access permission
- Display-over-other-apps permission if you want the Spotify floating shortcut

Spotify API credentials are stored only inside the app. ivLyrics Android does not use a shared public token server. It requests Spotify tokens with the Client ID and Client Secret from the Spotify Developer app you create yourself.

## Installation

1. Download the latest APK from GitHub Releases.
2. Install the APK on your Android device.
3. If Android shows a security prompt, allow "Install unknown apps" for the app you used to download the APK.
4. Open ivLyrics Android and complete the first-run setup.

You can download the latest APK from [Releases](https://github.com/ivLis-Studio/ivLyrics-Android/releases).

## First Setup

The app guides you through the required setup when you open it for the first time.

### 1. Choose App Language

Choose the language used by the app. You can change it later in Settings.

### 2. Allow Media Detection

ivLyrics Android detects the current Spotify track through Android media notifications.

When the permission screen opens, enable notification access for ivLyrics Android. If this permission is off, the app cannot read the currently playing song.

### 3. Register Spotify API

ivLyrics Android does not ask you to log in with Spotify. Instead, it uses the Client ID and Client Secret from your own Spotify Developer app.

Follow these steps in the Spotify Developer Dashboard.

1. Open the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Sign in with your Spotify account.
3. Press `Create app`.
4. Enter the following values.

| Field | Value |
| --- | --- |
| App name | `trackinfo` |
| App description | `trackinfo` |
| Redirect URIs | `https://localhost/` |
| APIs used | `Web API` |

5. Check the agreement box.
6. Press `Save`.
7. Copy the `Client ID` from the app page.
8. Press `View client secret` and copy the `Client Secret`.
9. Paste both values into ivLyrics Android and continue.

Do not name the Spotify app `ivLyrics` or `ivlyrics`. This is your own personal metadata lookup app, so using the example name `trackinfo` helps avoid confusion.

ivLyrics Android verifies that the credentials can issue a Spotify token before continuing. If the values are wrong, the app will ask you to enter them again.

## How to Use

1. Play music in Spotify.
2. Open ivLyrics Android.
3. When the track is detected, the app shows artwork, title, artist, and lyrics.
4. Swipe up from the lyric preview at the bottom of the main player to open the full lyrics page.
5. Drag the full lyrics page down to return to the main player.

The full lyrics page keeps the active line near the center and fills characters in real time when karaoke sync data is available. If character-level sync is not available, the app falls back to LRCLIB line-synced lyrics.

## Lyrics Page Tips

- Tap the title or artist once to open Spotify.
- Triple-tap the title/artist area on the lyrics page to open lyric settings.
- Tap a lyric line to jump to that position.
- Drag the progress bar to seek.
- If the timing feels off, adjust the sync offset from the lyric settings menu.
- If LRCLIB selected the wrong result, use manual LRCLIB search from the same menu.

## Translation, Pronunciation, and Furigana

ivLyrics Android detects the song language automatically and stores translation and pronunciation settings separately for each language.

For example, you can enable both translation and pronunciation for Japanese songs, enable only translation for English songs, and disable both for Spanish songs. If the detected language is wrong, you can override it from the lyric settings menu.

Japanese songs can also show furigana above kanji when the option is enabled.

Translation and pronunciation data is cached. Once generated, it stays available after restarting the app. You can clear the cache for the current track or for all tracks from Settings.

## Settings

You can customize:

- App language
- Pronunciation/translation output language
- Translation, pronunciation, and furigana rules per song language
- Lyric preview combinations on the main player
- Text size, weight, and font display options
- Vocal part colors
- Background effect
- Fake karaoke effect
- Character bounce animation
- Intro, instrumental, and outro display
- Keep screen on
- Spotify floating shortcut
- Current track cache
- All lyric cache

## Troubleshooting

### The track is not detected

- Make sure Spotify is actually playing music.
- Make sure notification access is enabled for ivLyrics Android.
- If the Spotify notification is not visible, open Spotify again and restart playback.

### Lyrics or artwork do not load

- Check that your Spotify Client ID and Client Secret are correct.
- Make sure Web API is selected in the Spotify Developer Dashboard.
- Check your internet connection.
- Try saving your Spotify API credentials again from Settings.

### The wrong lyrics were selected

- Triple-tap the title/artist area on the lyrics page to open the menu.
- Run manual LRCLIB search.
- Choose the correct lyric result.
- If needed, clear the current track cache and load the track again.

### The lyrics are slightly out of sync

- Adjust sync offset from the lyric settings menu.
- You can fine-tune timing in 10ms, 50ms, and 100ms steps.

### The floating shortcut does not appear

- Make sure display-over-other-apps permission is allowed.
- The shortcut appears only on Spotify's now playing screen.
- It may not appear while Spotify is in the background or on another Spotify screen.

## Disclaimer

> ⚠️ Disclaimer
>
> **Unofficial Project Notice**
>
> This project and its contributors are not affiliated with, authorized by, endorsed by, or officially connected to Spotify, its affiliates, or its subsidiaries. **This project is an independent, non-profit, unofficial extension developed by a volunteer team to provide a desktop experience.**
>
> **Trademark Notice**
>
> The name "Spotify" and all related names, marks, emblems, and images are registered trademarks of their respective owners. These trademarks are used only for identification and reference, and their use does not imply any association with the trademark owner. This project does not intend to infringe those trademarks or harm their owners.
>
> **Limitation of Liability**
>
> This application (extension) is provided "as is" and is used entirely at your own risk. The developers and contributors are not liable for any claims, damages, legal consequences, or other liability arising from the use of this software or related dealings. You are solely responsible for all consequences resulting from your use of this software.
>
> **Copyright and Terms Compliance**
>
> This project does not claim ownership of, or grant licenses for, lyrics, translations, videos, or any other third-party content. You are responsible for checking and complying with applicable copyright laws, platform policies, API terms of service, and local regulations. You are solely responsible for any storage, reproduction, distribution, transmission, or commercial use made through this project.

## Credits

- ivLyrics sync-data contributors
- LRCLIB
- Spotify Web API
- Pretendard

When contributor metadata is available, the sync creator is shown quietly near the top of the lyrics page. Tapping the creator name opens their profile inside the app.
