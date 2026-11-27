# ivLyrics for Android: license notices

Copyright (c) 2026 ivLis Studio and contributors.

Unless this notice says otherwise, the files in this repository are licensed under the ivLyrics Source-Available License 1.0 in [LICENSE](LICENSE).

## Generated motion lyrics page

`shared/src/main/assets/motion/MotionLyricsPage.html` is generated from [ivLyrics for PC](https://github.com/ivLis-Studio/ivLyrics). Do not edit it directly. It contains:

- Code from the PC files `LyricsWordSegmenter.js` and `style.css`, which are licensed under the GNU Lesser General Public License v2.1 as listed in the PC repository's [NOTICE.md](https://github.com/ivLis-Studio/ivLyrics/blob/main/NOTICE.md). The full text is in [LICENSES/LGPL-2.1.txt](LICENSES/LGPL-2.1.txt). You may use, modify, and redistribute that code under the LGPL.
- TinySegmenter (BSD-3-Clause) and Preact (MIT). Their notices are kept inside the file.

## Generated Pronunciation Lab page

`shared/src/main/assets/pronunciation-lab/PronunciationLabPage.html` is generated from [ivLyrics for PC](https://github.com/ivLis-Studio/ivLyrics) by `.github/scripts/build_pronunciation_lab_mobile.mjs`. Do not edit it directly. It contains only ivLyrics code. The [pronunciation-lab](https://github.com/ivLis-Studio/pronunciation-lab) library it uses (GPL-3.0; its dictionaries and data under their sources' licenses) is not included in this repository or the apps: when the Pronunciation Lab provider writes pronunciation, the page downloads the newest release and the language data it needs from jsDelivr at run time.

## Third-party components

| Component | License | Where |
|---|---|---|
| [Pretendard](https://github.com/orioncactus/pretendard) by Kil Hyung-jin | SIL Open Font License 1.1 | `shared/src/main/assets/fonts/`, notice in [OFL.txt](shared/src/main/assets/fonts/OFL.txt). `ivLyricsBrand-Variable.ttf` is a renamed subset; see [BRAND-FONT.md](shared/src/main/assets/fonts/BRAND-FONT.md). |
| [TinySegmenter](http://chasen.org/~taku/software/TinySegmenter/) by Taku Kudo, Java port | BSD-3-Clause | Notice in [TinySegmenter.LICENSE.txt](shared/src/main/assets/TinySegmenter.LICENSE.txt) |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT | Native libraries added at build time by `shared/tools/prepare_local_ai.py`, which also copies its license |

Builds also include libraries declared in the Gradle build files, under their own licenses.

## Lyrics and other content

The repository does not contain lyrics, recordings, artwork, or videos, and no license here covers them. ivLyrics retrieves lyrics and other content from third-party services at the user's request; that content belongs to its rights holders. Section 5 of [LICENSE](LICENSE) has the full terms. Rights holders can request removal of content stored on ivLyrics servers at me@ivl.is.

ivLyrics is not affiliated with Spotify. Spotify is a trademark of Spotify AB.
