package kr.ivlis.ivlyricsandroid;

/**
 * Colour tokens of the redesigned settings screen (ivLyrics settings lab): neutral surfaces and
 * one blue that is only used for state. Both themes are chosen manually; there is no automatic mode.
 */
final class SettingsPalette {
    static final String THEME_DARK = "dark";
    static final String THEME_LIGHT = "light";

    final boolean dark;
    final int bg;
    final int surface;
    final int surface2;
    final int surface3;
    final int surface4;
    final int raise;
    final int line;
    final int line2;
    final int line3;
    final int text;
    final int text2;
    final int text3;
    final int text4;
    final int accent;
    final int accentText;
    final int accentSoft;
    final int accentLine;
    final int accentInk;
    final int ink;
    final int inkText;
    final int ok;
    final int okSoft;
    final int warn;
    final int warnSoft;
    final int danger;
    final int dangerSoft;
    final int info;
    final int infoSoft;
    final int switchOff;
    final int ripple;
    final int layerOriginal;
    final int layerPhonetic;
    final int layerTranslation;

    static final SettingsPalette DARK = new SettingsPalette(true,
            0xFF0B0B0C, 0xFF141416, 0xFF19191C, 0xFF212125, 0xFF2C2C31, 0xFF2A2A2F,
            0x11FFFFFF, 0x1AFFFFFF, 0x2BFFFFFF,
            0xFFEDEDEF, 0xFFA9A9B1, 0xFF8B8B94, 0xFF58585F,
            0xFF2563EB, 0xFF7AADFF, 0x243B82F6, 0x8C3B82F6, 0xFFFFFFFF,
            0xFFEDEDEF, 0xFF0B0B0C,
            0xFF3FB950, 0x1F3FB950, 0xFFD6A33A, 0x1FD6A33A, 0xFFF2554E, 0x1FF2554E,
            0xFF7AADFF, 0x1A3B82F6, 0xFF3C3C43, 0x24FFFFFF,
            0xFFD4D4D8, 0xFFE9A23B, 0xFF3FBF8A);

    static final SettingsPalette LIGHT = new SettingsPalette(false,
            0xFFF6F6F7, 0xFFFFFFFF, 0xFFF6F6F7, 0xFFEEEEF0, 0xFFE1E1E5, 0xFFFFFFFF,
            0x12000000, 0x1C000000, 0x2E000000,
            0xFF18181B, 0xFF52525B, 0xFF64646D, 0xFFA1A1AA,
            0xFF2563EB, 0xFF1D4ED8, 0x142563EB, 0x732563EB, 0xFFFFFFFF,
            0xFF18181B, 0xFFFFFFFF,
            0xFF1A7F37, 0x171A7F37, 0xFF9A6700, 0x1A9A6700, 0xFFCF222E, 0x14CF222E,
            0xFF1D4ED8, 0x122563EB, 0xFFD4D4D9, 0x1A000000,
            0xFF3F3F46, 0xFFC27612, 0xFF15845A);

    private SettingsPalette(
            boolean dark,
            int bg, int surface, int surface2, int surface3, int surface4, int raise,
            int line, int line2, int line3,
            int text, int text2, int text3, int text4,
            int accent, int accentText, int accentSoft, int accentLine, int accentInk,
            int ink, int inkText,
            int ok, int okSoft, int warn, int warnSoft, int danger, int dangerSoft,
            int info, int infoSoft, int switchOff, int ripple,
            int layerOriginal, int layerPhonetic, int layerTranslation
    ) {
        this.dark = dark;
        this.bg = bg;
        this.surface = surface;
        this.surface2 = surface2;
        this.surface3 = surface3;
        this.surface4 = surface4;
        this.raise = raise;
        this.line = line;
        this.line2 = line2;
        this.line3 = line3;
        this.text = text;
        this.text2 = text2;
        this.text3 = text3;
        this.text4 = text4;
        this.accent = accent;
        this.accentText = accentText;
        this.accentSoft = accentSoft;
        this.accentLine = accentLine;
        this.accentInk = accentInk;
        this.ink = ink;
        this.inkText = inkText;
        this.ok = ok;
        this.okSoft = okSoft;
        this.warn = warn;
        this.warnSoft = warnSoft;
        this.danger = danger;
        this.dangerSoft = dangerSoft;
        this.info = info;
        this.infoSoft = infoSoft;
        this.switchOff = switchOff;
        this.ripple = ripple;
        this.layerOriginal = layerOriginal;
        this.layerPhonetic = layerPhonetic;
        this.layerTranslation = layerTranslation;
    }

    static SettingsPalette forTheme(String theme) {
        return THEME_LIGHT.equals(theme) ? LIGHT : DARK;
    }

    String theme() {
        return dark ? THEME_DARK : THEME_LIGHT;
    }
}
