# Spotify 9.1.80.2221 inline lyrics mapping

Verified against the locally saved host APK on 2026-09-07. Its manifest contains
version `9.1.80.2221`; SHA-256:
`f4826f9a9148b15d4a7adeb10a01603a6eca2ff29d16c9d59a320439b4ff17da`.
This document records hook contracts, not device playback validation.

| Host mapping | Native behavior | ivLyrics behavior |
| --- | --- | --- |
| `p.jm7.invokeSuspend`, branch `a == 1` | Native lyric result is `p.mx80`, permitted Connect automobile mode (`!b || d`), and audio player (`!c`) must all pass. | Return the player restrictions without requiring native lyric data. |
| `p.pca0.a` | Shares the `jm7` availability with the inline element and context menu. | Both consumers receive the independent availability result. |
| `p.cp5.invokeSuspend`, branch `a == 2` | Combines Canvas (`b`), saved preference (`c`), availability (`d`) and mixing (`e`): `!b && c && d && !e`. | Preserve Canvas, preference and availability. Mixing alone does not hide a song; the shared preview excludes DJ speech. |
| `p.dam0` | Owns `key_lyrics_on_npv_visible`. | Continue observing the host preference; no preference write or default change. |
| `p.ziw0` → `p.m6o0` → `p.b9p0.apply` | Converts `pca0.a` plus the saved preference into an optional boolean, then builds `npv_lyrics_toggle`. | Keep the native menu item and action. |
| `p.d2w0.e(ContextTrack)` inside `p.b9p0.apply` | A second menu guard requires track metadata `has_lyrics` and no `parent_episode_uri`. | Accept a `spotify:track:` without a parent episode. Override this method only within the synchronous menu call, using a per-thread depth that also clears on exceptions. Other consumers retain native lyric eligibility. |
| `p.mk0.invokeSuspend`, branch `a == 12` | Always returns `p.lba0`, including an empty model when native lyrics are absent. | The renderer already handles that empty model; fabricated Spotify lyric data is unnecessary. |
| `p.nba0.c1`, branch `a == 0` | Renders `p.lba0` in the slot above the song title. | Mount the shared ivLyrics preview through the host Compose AndroidView interop. |

`tests/run_inline_hooks_regression.py` compiles the production hook classes with
synthetic host, Android and Xposed surfaces. It covers absent/native lyrics,
the two menu gates, unrelated hook branches, nested/failed menu scopes,
saved preference toggling, mixing, video/audio transitions, host reattachment
and rendering an empty native lyric model. `tests/run_inline_gate_regression.py`
covers the full flag truth table. Neither test starts an emulator or device.
