# Settings home brand font

`ivLyricsBrand-Variable.ttf` is a glyph subset of the existing Pretendard family's
variable font, used only for the single `ivLyrics` settings home heading. It
retains the real `wght` axis from 45 to 930; Android's API-26 `Typeface.Builder`
selects 800 for `iv` and 250 for `Lyrics`. Ordinary settings fonts do not change.

Source: [Pretendard v1.3.9](https://github.com/orioncactus/pretendard/blob/v1.3.9/packages/pretendard/dist/public/variable/PretendardVariable.ttf)
by Kil Hyung-jin, under the accompanying `OFL.txt`. The derivative family is
renamed `ivLyricsBrand` to respect the reserved font name. Copyright and license
metadata are also retained in the TTF.

Upstream SHA-256: `3090ccde0442bb347aa7685d9ba8b17436a60682df6e8f92a9a670de14056e22`.

To regenerate with `fonttools==4.61.1` installed, download the pinned source and run:

```sh
python3 shared/tools/prepare_brand_font.py /path/to/PretendardVariable.ttf
```
