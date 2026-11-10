package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies lrc.red word timing to lyrics that are already displayed.
 *
 * Generated timing never changes the lyric text, so pronunciation, translation and furigana
 * produced for the line-timed lyrics stay valid and are carried over line by line.
 */
final class LyricsTimingUpgrade {
    private LyricsTimingUpgrade() {
    }

    /** The word-timed replacement for a line-timed lrc.red result, or null if it does not apply. */
    static LyricsResult upgradedBase(LyricsResult base, List<LyricsLine> timedLines) {
        if (!canUpgrade(base) || timedLines == null || timedLines.size() != base.lines.size()
                || !LrcRedLyricsProvider.sameLineTexts(base.lines, timedLines)) {
            return null;
        }
        return new LyricsResult(
                new ArrayList<>(timedLines),
                "lrc.red karaoke",
                base.detail,
                true,
                base.isrc,
                base.spotifyTrackId,
                base.contributors,
                base.providerId,
                base.selectionPolicyKey,
                base.syncType,
                base.syncPoints,
                base.pronunciationProviderLabel,
                base.translationProviderLabel
        );
    }

    /** Whether a displayed base may receive generated lrc.red word timing. */
    static boolean canUpgrade(LyricsResult base) {
        return base != null && !base.karaoke && !base.loading && !base.lines.isEmpty()
                && LyricsProviderSettings.PROVIDER_LRC_RED.equals(base.providerId);
    }

    /** True when an upgraded base replaces exactly this line-timed base. */
    static boolean upgrades(LyricsResult source, LyricsResult upgraded) {
        return canUpgrade(source) && upgraded != null && upgraded.karaoke
                && source.providerId.equals(upgraded.providerId)
                && source.selectionPolicyKey.equals(upgraded.selectionPolicyKey)
                && source.lines.size() == upgraded.lines.size()
                && LrcRedLyricsProvider.sameLineTexts(source.lines, upgraded.lines);
    }

    /** Displayed supplements on the upgraded timing; the displayed lines map one to one. */
    static LyricsResult retime(LyricsResult displayed, LyricsResult timed) {
        if (timed == null) return displayed;
        if (displayed == null || displayed.lines.size() != timed.lines.size()) return timed;
        List<LyricsLine> lines = new ArrayList<>();
        for (int index = 0; index < timed.lines.size(); index++) {
            LyricsLine shown = displayed.lines.get(index);
            LyricsLine line = timed.lines.get(index);
            lines.add(line.withSupplements(shown.pronunciationText, shown.translationText, shown.furiganaText));
        }
        return new LyricsResult(
                lines,
                timed.providerLabel,
                displayed.detail,
                timed.karaoke,
                timed.isrc,
                timed.spotifyTrackId,
                displayed.contributors,
                timed.providerId,
                timed.selectionPolicyKey,
                timed.syncType,
                timed.syncPoints,
                displayed.pronunciationProviderLabel,
                displayed.translationProviderLabel
        );
    }
}

