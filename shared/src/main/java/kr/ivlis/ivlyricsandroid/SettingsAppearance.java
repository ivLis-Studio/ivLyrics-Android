package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Native settings tokens shared by the standalone app and Spotify host. */
final class SettingsAppearance {
    static final int BACKGROUND = 0xFF0D0D11;
    static final int SURFACE = 0xFF151519;
    static final int CONTROL = 0xFF1B1B21;
    static final int BORDER = 0x12FFFFFF;
    static final int BORDER_STRONG = 0x24FFFFFF;
    static final int TEXT = 0xFFF1F0F4;
    static final int SECONDARY = 0xFF8F8E98;
    static final int MUTED = 0xFF777681;
    static final int ACCENT = 0xFFFF6B4A;
    static final int ACCENT_SOFT = 0x29FF6B4A;
    static final int MINT = 0xFF5FCE9B;
    static final int MINT_SOFT = 0x295FCE9B;
    static final String SECTION_TITLE = "settings-section-title";
    static final String SECTION_NOTE = "settings-section-note";
    static final String ROW_LIST = "settings-row-list";
    static final String PRIMARY_ACTION = "settings-primary-action";
    static final String SECONDARY_ACTION = "settings-secondary-action";

    private SettingsAppearance() {}

    static int dp(Context context, float value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    static GradientDrawable surface(Context context, int color, int radius, boolean strong) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, 1), strong ? BORDER_STRONG : BORDER);
        return drawable;
    }

    static void addDividers(LinearLayout container) {
        GradientDrawable divider = new GradientDrawable();
        divider.setColor(BORDER);
        divider.setSize(1, dp(container.getContext(), 1));
        container.setDividerDrawable(divider);
        container.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
    }

    static CharSequence switchLabel(Context context, String title, String description) {
        if (description == null || description.trim().isEmpty()) return title;
        SpannableString text = new SpannableString(title + "\n" + description);
        int start = title.length() + 1;
        text.setSpan(new ForegroundColorSpan(MUTED), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(12.5f / 15f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.NORMAL), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    static void styleSwitch(Switch view) {
        Context context = view.getContext();
        view.setShowText(false);
        view.setSplitTrack(false);
        view.setSwitchMinWidth(dp(context, 44));
        view.setSwitchPadding(dp(context, 14));
        // StateListDrawable keeps Android's checked, disabled, keyboard and accessibility behavior.
        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_checked}, circle(context, ACCENT, 20));
        thumb.addState(new int[]{}, circle(context, 0xFF8B8A93, 20));
        view.setThumbDrawable(thumb);
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, switchTrack(context, ACCENT_SOFT));
        track.addState(new int[]{}, switchTrack(context, CONTROL));
        view.setTrackDrawable(track);
        view.setThumbTintList(null);
        view.setTrackTintList(null);
        view.setMinHeight(dp(context, 44));
    }

    private static GradientDrawable circle(Context context, int color, int size) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setSize(dp(context, size), dp(context, size));
        return drawable;
    }

    private static GradientDrawable switchTrack(Context context, int color) {
        GradientDrawable drawable = surface(context, color, 100, color == CONTROL);
        drawable.setSize(dp(context, 44), dp(context, 26));
        return drawable;
    }

    static void styleSlider(SeekBar slider) {
        slider.setProgressTintList(ColorStateList.valueOf(ACCENT));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(CONTROL));
        slider.setThumbTintList(ColorStateList.valueOf(ACCENT));
        slider.setMinimumHeight(dp(slider.getContext(), 44));
    }

    static void styleControls(View root) {
        if (root == null) return;
        Context context = root.getContext();
        if (root instanceof Switch) {
            styleSwitch((Switch) root);
        } else if (root instanceof SeekBar) {
            styleSlider((SeekBar) root);
        } else if (root instanceof EditText) {
            root.setBackground(surface(context, CONTROL, 11, true));
            ((TextView) root).setTextColor(TEXT);
            ((EditText) root).setHintTextColor(MUTED);
        } else if (root instanceof TextView && (PRIMARY_ACTION.equals(root.getTag()) || SECONDARY_ACTION.equals(root.getTag()))) {
            boolean primary = PRIMARY_ACTION.equals(root.getTag());
            TextView button = (TextView) root;
            button.setTextColor(primary ? BACKGROUND : TEXT);
            button.setBackground(surface(context, primary ? TEXT : CONTROL, 11, !primary));
            button.setMinHeight(dp(context, 44));
            button.setTextSize(13.5f);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) styleControls(group.getChildAt(index));
        }
    }

    static Drawable chevron(Context context) {
        final int size = dp(context, 16);
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas) {
                paint.setColor(SECONDARY);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(context, 1.5f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                float x = getBounds().left;
                float y = getBounds().top;
                canvas.drawLine(x + size * .25f, y + size * .4f, x + size * .5f, y + size * .65f, paint);
                canvas.drawLine(x + size * .5f, y + size * .65f, x + size * .75f, y + size * .4f, paint);
            }
            @Override public int getIntrinsicWidth() { return size; }
            @Override public int getIntrinsicHeight() { return size; }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }
}
