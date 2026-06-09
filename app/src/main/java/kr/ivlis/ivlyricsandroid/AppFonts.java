package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Typeface;

final class AppFonts {
    private static Typeface regular;
    private static Typeface semiBold;
    private static Typeface bold;

    private AppFonts() {
    }

    static Typeface regular(Context context) {
        if (regular == null) {
            regular = load(context, "fonts/Pretendard-Regular.ttf", Typeface.NORMAL);
        }
        return regular;
    }

    static Typeface semiBold(Context context) {
        if (semiBold == null) {
            semiBold = load(context, "fonts/Pretendard-SemiBold.ttf", Typeface.BOLD);
        }
        return semiBold;
    }

    static Typeface bold(Context context) {
        if (bold == null) {
            bold = load(context, "fonts/Pretendard-Bold.ttf", Typeface.BOLD);
        }
        return bold;
    }

    static Typeface byWeight(Context context, String weight) {
        String normalized = AiLyricsSettings.normalizeTypographyWeight(weight);
        if (AiLyricsSettings.TYPO_WEIGHT_REGULAR.equals(normalized)) {
            return regular(context);
        }
        if (AiLyricsSettings.TYPO_WEIGHT_BOLD.equals(normalized)) {
            return bold(context);
        }
        return semiBold(context);
    }

    private static Typeface load(Context context, String assetPath, int style) {
        try {
            return Typeface.createFromAsset(context.getAssets(), assetPath);
        } catch (Exception ignored) {
            return Typeface.create("sans", style);
        }
    }
}
