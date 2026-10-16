#!/usr/bin/env python3
"""Manually regenerate the tiny variable brand font; not an Android build dependency.

Requires fonttools==4.61.1 and the upstream Pretendard v1.3.9 variable TTF.
"""
import argparse
import hashlib
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

SOURCE_SHA256 = "3090ccde0442bb347aa7685d9ba8b17436a60682df6e8f92a9a670de14056e22"
OUTPUT = Path(__file__).resolve().parents[1] / "src/main/assets/fonts/ivLyricsBrand-Variable.ttf"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    if hashlib.sha256(args.source.read_bytes()).hexdigest() != SOURCE_SHA256:
        parser.error("Expected the pinned upstream Pretendard v1.3.9 variable TTF")
    font = TTFont(args.source, recalcTimestamp=False)
    options = subset.Options()
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    worker = subset.Subsetter(options=options)
    worker.populate(text="ivLyrics")
    worker.subset(font)
    # OFL reserves the upstream family name for unmodified fonts. Keep its
    # copyright/license records and rename all family/PostScript/instance names.
    for record in font["name"].names:
        if record.nameID in (0, 13, 14):
            continue
        value = record.toUnicode()
        if "Pretendard" in value:
            value = value.replace("Pretendard", "ivLyricsBrand")
            record.string = value.encode(record.getEncoding())
    font.save(OUTPUT)
    axis = font["fvar"].axes[0]
    print(f"{OUTPUT.name}: {OUTPUT.stat().st_size} bytes, wght {axis.minValue:g}-{axis.maxValue:g}, ivLyrics glyphs")


if __name__ == "__main__":
    main()
