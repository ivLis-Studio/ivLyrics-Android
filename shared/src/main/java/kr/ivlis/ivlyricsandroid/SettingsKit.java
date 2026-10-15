package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Views of the redesigned settings screen (ivLyrics settings lab, Android): grouped cards, rows,
 * segmented controls, radio cards, chips, sliders with a numeric field and list actions. Every view
 * keeps at least a 48dp touch target, grows with the system font instead of clipping, and follows
 * the layout direction. Colours come from one {@link SettingsPalette}.
 */
final class SettingsKit {
    static final String GROUP_TAG = "settings-kit-group";
    static final int TONE_INFO = 0;
    static final int TONE_WARN = 1;
    static final int TONE_DANGER = 2;
    static final int TONE_OK = 3;
    static final int TONE_NEUTRAL = 4;
    static final int TONE_ACCENT = 5;
    static final int BUTTON_NORMAL = 0;
    static final int BUTTON_PRIMARY = 1;
    static final int BUTTON_DANGER = 2;
    static final int ACTION_NORMAL = 0;
    static final int ACTION_DANGER = 1;
    static final int ACTION_LINK = 2;

    final Context context;
    final SettingsPalette p;
    final Typeface regular;
    final Typeface semi;
    final Typeface bold;
    /** Called after any control built here changed a value, e.g. to redraw a live preview. */
    Runnable onChange;

    SettingsKit(Context context, SettingsPalette palette) {
        this.context = context;
        this.p = palette;
        this.regular = AppFonts.regular(context);
        this.semi = AppFonts.semiBold(context);
        this.bold = AppFonts.bold(context);
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    float fontScale() {
        return context.getResources().getConfiguration().fontScale;
    }

    void changed() {
        if (onChange != null) onChange.run();
    }

    // ------------------------------------------------------------------ drawables

    GradientDrawable rect(int fill, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    GradientDrawable rect(int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable drawable = rect(fill, radiusDp);
        if (strokeDp > 0f) drawable.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return drawable;
    }

    GradientDrawable oval(int fill, int sizePx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        drawable.setSize(sizePx, sizePx);
        return drawable;
    }

    /** Touch feedback over content, clipped to the same rounded rectangle. */
    Drawable pressable(Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(p.ripple), content, rect(0xFFFFFFFF, radiusDp));
    }

    SvgIcon icon(String name, int color, float sizeDp) {
        SvgIcon icon = new SvgIcon(iconPath(name), color, dp(sizeDp), 1.7f);
        icon.setAutoMirrored(MIRRORED.containsKey(name));
        return icon;
    }

    static ColorStateList states(int disabled, int checked, int normal) {
        return new ColorStateList(new int[][]{
                {-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}},
                new int[]{disabled, checked, normal});
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    // ------------------------------------------------------------------ text

    TextView text(CharSequence value, float sizeSp, int color, Typeface typeface) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return view;
    }

    TextView largeTitle(String value) {
        TextView view = text(value, 28f, p.text, semi);
        view.setLetterSpacing(-0.02f);
        view.setLineSpacing(dp(2), 1f);
        view.setPaddingRelative(dp(4), dp(6), dp(4), dp(14));
        heading(view);
        return view;
    }

    LinearLayout sectionHeader(String title, String subtitle) {
        LinearLayout header = vertical();
        header.setPaddingRelative(dp(4), 0, dp(4), dp(9));
        TextView titleView = text(title, 17f, p.text, semi);
        titleView.setLineSpacing(dp(2), 1f);
        heading(titleView);
        header.addView(titleView, matchWrap());
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = text(subtitle, 13f, p.text3, regular);
            sub.setLineSpacing(dp(3), 1f);
            header.addView(sub, topMargin(matchWrap(), dp(5)));
        }
        return header;
    }

    TextView groupLabel(String value) {
        TextView view = text(value, 13f, p.text3, semi);
        view.setPaddingRelative(dp(6), 0, dp(6), dp(7));
        return view;
    }

    TextView rowLabel(String value) {
        TextView view = text(value, 15.5f, p.text, semi);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    TextView rowDescription(String value) {
        TextView view = text(value, 13f, p.text3, regular);
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    static void heading(View view) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) view.setAccessibilityHeading(true);
    }

    CharSequence labelWithDescription(String title, String description, float titleSp, float descriptionSp) {
        if (description == null || description.trim().isEmpty()) return title;
        SpannableString text = new SpannableString(title + "\n" + description);
        int start = title.length() + 1;
        text.setSpan(new ForegroundColorSpan(p.text3), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(descriptionSp / titleSp), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new FontSpan(regular), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    /** A typeface for part of a label (the description under a switch or action title). */
    static final class FontSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        FontSpan(Typeface typeface) {
            this.typeface = typeface;
        }

        @Override public void updateDrawState(TextPaint paint) {
            paint.setTypeface(typeface);
        }

        @Override public void updateMeasureState(TextPaint paint) {
            paint.setTypeface(typeface);
        }
    }

    // ------------------------------------------------------------------ containers

    LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    /** A card of rows separated by hairlines; it disappears when every row is hidden. */
    LinearLayout group() {
        LinearLayout group = vertical();
        group.setTag(GROUP_TAG);
        group.setBackground(rect(p.surface, 12f, p.line, 1f));
        group.setClipToOutline(true);
        GradientDrawable divider = new GradientDrawable();
        divider.setColor(p.line);
        divider.setSize(1, Math.max(1, dp(1)));
        group.setDividerDrawable(divider);
        group.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return group;
    }

    FlowLayout flow(float horizontalGapDp, float verticalGapDp) {
        return new FlowLayout(context, dp(horizontalGapDp), dp(verticalGapDp));
    }

    /** Rows separated by hairlines without a card of their own (inside an expanded card). */
    LinearLayout dividedList() {
        LinearLayout list = vertical();
        GradientDrawable divider = new GradientDrawable();
        divider.setColor(p.line);
        divider.setSize(1, Math.max(1, dp(1)));
        list.setDividerDrawable(divider);
        list.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return list;
    }

    ColumnsLayout columns(int maxColumns, float minColumnDp, float gapDp) {
        float scale = Math.max(1f, Math.min(2f, fontScale()));
        return new ColumnsLayout(context, maxColumns, Math.round(dp(minColumnDp) * scale), dp(gapDp));
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams params, int margin) {
        params.topMargin = margin;
        return params;
    }

    // ------------------------------------------------------------------ rows

    /** Label and description above a full-width control. */
    LinearLayout stackRow(String label, String description, View control) {
        LinearLayout row = vertical();
        row.setPaddingRelative(dp(16), dp(14), dp(16), dp(15));
        TextView labelView = rowLabel(label);
        row.addView(labelView, matchWrap());
        if (description != null && !description.trim().isEmpty()) {
            row.addView(rowDescription(description), topMargin(matchWrap(), dp(4)));
        }
        if (control != null) {
            if (control.getId() == View.NO_ID) control.setId(View.generateViewId());
            labelView.setLabelFor(control.getId());
            row.addView(control, topMargin(matchWrap(), dp(12)));
        }
        return row;
    }

    /** Label and description beside a compact control; the control moves under the text when space runs out. */
    AdaptiveRow inlineRow(String label, String description, View control) {
        AdaptiveRow row = new AdaptiveRow(context, dp(150), dp(14));
        row.setPaddingRelative(dp(16), dp(12), dp(16), dp(12));
        row.setMinimumHeight(dp(56));
        LinearLayout texts = vertical();
        TextView labelView = rowLabel(label);
        texts.addView(labelView, matchWrap());
        if (description != null && !description.trim().isEmpty()) {
            texts.addView(rowDescription(description), topMargin(matchWrap(), dp(4)));
        }
        row.addView(texts);
        if (control != null) {
            if (control.getId() == View.NO_ID) control.setId(View.generateViewId());
            labelView.setLabelFor(control.getId());
            row.addView(control);
        }
        return row;
    }

    /** An on/off row: title, description and the switch are one touch target. */
    Switch toggle(String title, String description) {
        Switch view = new Switch(context);
        view.setText(labelWithDescription(title, description, 15.5f, 13f));
        view.setTextColor(p.text);
        view.setTextSize(15.5f);
        view.setTypeface(semi);
        view.setLineSpacing(dp(3), 1f);
        view.setPaddingRelative(dp(16), dp(13), dp(16), dp(13));
        view.setMinHeight(dp(56));
        view.setBackground(pressable(rect(0x00000000, 0f), 0f));
        styleSwitch(view);
        view.setMinimumHeight(dp(56));
        return view;
    }

    void styleSwitch(Switch view) {
        view.setShowText(false);
        view.setSplitTrack(false);
        view.setSwitchMinWidth(dp(44));
        view.setSwitchPadding(dp(16));
        // Native drawables keep their own circular geometry; only their colours are tinted.
        view.setThumbTintList(states(withAlpha(p.text4, 0x99), p.accent, p.text3));
        view.setTrackTintList(states(p.line2, withAlpha(p.accent, 0x80), p.dark ? 0xFF3C3C43 : 0xFFD4D4D9));
        view.setSingleLine(false);
        view.setMaxLines(Integer.MAX_VALUE);
        view.setEllipsize(null);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setMinimumWidth(dp(48));
    }

    /** A list action: full-width text with a chevron, or an external-link mark for links. */
    Button actionRow(String label, String description, int kind) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setText(description == null || description.trim().isEmpty() ? label
                : labelWithDescription(label, description, 16f, 13f));
        button.setTextSize(16f);
        button.setTypeface(regular);
        button.setLineSpacing(dp(2), 1f);
        button.setTextColor(kind == ACTION_DANGER ? p.danger : p.accentText);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        button.setSingleLine(false);
        button.setMaxLines(Integer.MAX_VALUE);
        button.setEllipsize(null);
        button.setPaddingRelative(dp(16), dp(13), dp(16), dp(13));
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setCompoundDrawablePadding(dp(12));
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                icon(kind == ACTION_LINK ? "external" : "right", p.text4, 18f), null);
        button.setBackground(pressable(rect(0x00000000, 0f), 0f));
        return button;
    }

    /** A bordered button for actions inside rows and cards. */
    Button button(String label, int kind, String iconName) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setText(label);
        button.setTextSize(14.5f);
        button.setTypeface(semi);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(false);
        button.setMaxLines(Integer.MAX_VALUE);
        button.setEllipsize(null);
        button.setPaddingRelative(dp(14), dp(10), dp(14), dp(10));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        int textColor;
        Drawable background;
        if (kind == BUTTON_PRIMARY) {
            textColor = p.inkText;
            background = rect(p.ink, 8f);
        } else if (kind == BUTTON_DANGER) {
            textColor = p.danger;
            background = rect(0x00000000, 8f, withAlpha(p.danger, 0x4D), 1f);
        } else {
            textColor = p.text;
            background = rect(p.surface2, 8f, p.line2, 1f);
        }
        button.setTextColor(textColor);
        button.setBackground(pressable(background, 8f));
        if (iconName != null) {
            button.setCompoundDrawablePadding(dp(7));
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconName, textColor, 17f), null, null, null);
        }
        return button;
    }

    /** A drop-down field with one graphical chevron. */
    Button picker(String value) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setText(value);
        button.setTextSize(15.5f);
        button.setTypeface(regular);
        button.setTextColor(p.text);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        button.setSingleLine(false);
        button.setMaxLines(Integer.MAX_VALUE);
        button.setEllipsize(null);
        button.setPaddingRelative(dp(14), dp(11), dp(12), dp(11));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setCompoundDrawablePadding(dp(10));
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon("down", p.text3, 18f), null);
        button.setBackground(pressable(rect(p.surface2, 8f, p.line2, 1f), 8f));
        return button;
    }

    ImageButton iconButton(String iconName, String description, float iconDp) {
        ImageButton button = new ImageButton(context);
        button.setImageDrawable(icon(iconName, p.text, iconDp));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription(description);
        button.setBackground(pressable(rect(0x00000000, 10f), 10f));
        button.setPadding(0, 0, 0, 0);
        button.setMinimumWidth(dp(48));
        button.setMinimumHeight(dp(48));
        button.setFocusable(true);
        return button;
    }

    EditText field(String hint, boolean multiLine, boolean secret) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setHintTextColor(p.text4);
        input.setTextColor(p.text);
        input.setTextSize(15.5f);
        input.setTypeface(regular);
        input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        input.setGravity(multiLine ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
        input.setSingleLine(!multiLine);
        int type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (multiLine) {
            type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            input.setMinLines(2);
            input.setMaxLines(4);
        }
        if (secret) type |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
        input.setInputType(type);
        input.setBackground(fieldBackground());
        input.setPaddingRelative(dp(14), dp(12), dp(14), dp(12));
        input.setMinHeight(dp(multiLine ? 76 : 48));
        input.setMinimumHeight(dp(multiLine ? 76 : 48));
        input.setMinWidth(dp(48));
        input.setMinimumWidth(dp(48));
        return input;
    }

    Drawable fieldBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, rect(p.surface, 8f, p.accentLine, 1.5f));
        states.addState(new int[]{}, rect(p.surface2, 8f, p.line2, 1f));
        return states;
    }

    /** A secret field with a show/hide toggle that keeps the field's own input type flags. */
    View secretField(EditText input, String showLabel, String hideLabel) {
        LinearLayout row = horizontal();
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton eye = iconButton("eye", showLabel, 20f);
        eye.setImageDrawable(icon("eye", p.text3, 20f));
        eye.setOnClickListener(view -> {
            int type = input.getInputType();
            boolean hidden = (type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
            int selection = input.getSelectionEnd();
            input.setInputType(hidden
                    ? (type & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : (type & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setTypeface(regular);
            if (selection >= 0) input.setSelection(Math.min(selection, input.length()));
            eye.setImageDrawable(icon(hidden ? "eyeOff" : "eye", p.text3, 20f));
            eye.setContentDescription(hidden ? hideLabel : showLabel);
        });
        LinearLayout.LayoutParams eyeParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        eyeParams.setMarginStart(dp(6));
        row.addView(eye, eyeParams);
        return row;
    }

    LinearLayout note(CharSequence value, int tone) {
        LinearLayout note = horizontal();
        note.setGravity(Gravity.TOP);
        int fill = tone == TONE_WARN ? p.warnSoft : tone == TONE_DANGER ? p.dangerSoft : p.infoSoft;
        int accent = tone == TONE_WARN ? p.warn : tone == TONE_DANGER ? p.danger : p.info;
        note.setBackground(rect(fill, 8f, withAlpha(accent, 0x30), 1f));
        note.setPaddingRelative(dp(12), dp(11), dp(12), dp(11));
        ImageView mark = new ImageView(context);
        mark.setImageDrawable(icon(tone == TONE_INFO ? "info" : "warn", accent, 17f));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(17), dp(17));
        markParams.topMargin = dp(1);
        note.addView(mark, markParams);
        TextView body = text(value, 13.5f, p.text, regular);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bodyParams.setMarginStart(dp(10));
        note.addView(body, bodyParams);
        return note;
    }

    /** Wraps a status text with the lab's small neutral dot. */
    LinearLayout statusLine(TextView status) {
        LinearLayout line = horizontal();
        line.setPaddingRelative(dp(16), dp(14), dp(16), dp(14));
        line.setMinimumHeight(dp(48));
        View dot = new View(context);
        dot.setBackground(oval(p.text4, dp(6)));
        line.addView(dot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        status.setTextColor(p.text2);
        status.setTextSize(14f);
        status.setTypeface(regular);
        status.setLineSpacing(dp(3), 1f);
        status.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(dp(12));
        line.addView(status, params);
        return line;
    }

    TextView pill(String value, int tone) {
        TextView pill = text(value, 12f, p.text2, semi);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPaddingRelative(dp(7), dp(4), dp(7), dp(4));
        setPillTone(pill, tone);
        return pill;
    }

    void setPillTone(TextView pill, int tone) {
        int fill;
        int color;
        if (tone == TONE_OK) {
            fill = p.okSoft;
            color = p.ok;
        } else if (tone == TONE_ACCENT) {
            fill = p.accentSoft;
            color = p.accentText;
        } else if (tone == TONE_DANGER) {
            fill = p.dangerSoft;
            color = p.danger;
        } else {
            fill = p.surface3;
            color = p.text2;
        }
        pill.setTextColor(color);
        pill.setBackground(rect(fill, 4f));
    }

    View spacer(int heightPx) {
        View view = new View(context);
        view.setMinimumHeight(heightPx);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        return view;
    }

    /** Marks a row that only applies while the row above is on (the lab's indented child row). */
    void indent(View row) {
        row.setPaddingRelative(row.getPaddingStart() + dp(20), row.getPaddingTop(), row.getPaddingEnd(), row.getPaddingBottom());
    }

    // ------------------------------------------------------------------ choices

    static final class Option {
        final String value;
        final String label;
        final String description;
        Drawable icon;
        View visual;

        Option(String value, String label) {
            this(value, label, null);
        }

        Option(String value, String label, String description) {
            this.value = value;
            this.label = label;
            this.description = description;
        }

        Option icon(Drawable drawable) {
            icon = drawable;
            return this;
        }

        Option visual(View view) {
            visual = view;
            return this;
        }
    }

    interface ChoiceListener {
        void onChoice(String value);
    }

    /** A single choice drawn as a segmented control or radio cards. */
    final class Choice {
        final ViewGroup view;
        final List<Option> options;
        final List<View> items = new ArrayList<>();
        final boolean cards;
        String selected;

        Choice(ViewGroup view, List<Option> options, boolean cards) {
            this.view = view;
            this.options = options;
            this.cards = cards;
        }

        void select(String value) {
            selected = value;
            for (int index = 0; index < items.size(); index++) {
                View item = items.get(index);
                boolean on = options.get(index).value.equals(value);
                item.setSelected(on);
                if (cards) {
                    paintCard(item, options.get(index), on);
                } else {
                    paintSegment((TextView) item, options.get(index), on);
                }
            }
        }

        void setEnabled(boolean enabled) {
            for (View item : items) item.setEnabled(enabled);
            view.setAlpha(enabled ? 1f : 0.45f);
        }
    }

    Choice segmented(List<Option> options, String selected, ChoiceListener listener) {
        SegmentedLayout layout = new SegmentedLayout(context, dp(2));
        layout.setBackground(rect(p.surface2, 9f, p.line2, 1f));
        layout.setPadding(dp(2), dp(2), dp(2), dp(2));
        Choice choice = new Choice(layout, options, false);
        for (Option option : options) {
            TextView item = text(option.label, 14f, p.text3, semi);
            item.setGravity(Gravity.CENTER);
            item.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            item.setPaddingRelative(dp(10), dp(9), dp(10), dp(9));
            item.setMinHeight(dp(44));
            item.setMinimumHeight(dp(44));
            item.setLineSpacing(dp(2), 1f);
            item.setContentDescription(option.label);
            item.setFocusable(true);
            item.setClickable(true);
            item.setCompoundDrawablePadding(dp(6));
            radioRole(item);
            item.setOnClickListener(view -> {
                if (option.value.equals(choice.selected)) return;
                choice.select(option.value);
                if (listener != null) listener.onChoice(option.value);
                changed();
            });
            choice.items.add(item);
            layout.addView(item);
        }
        choice.select(selected);
        return choice;
    }

    private void paintSegment(TextView item, Option option, boolean on) {
        item.setTextColor(on ? p.text : p.text3);
        item.setBackground(on
                ? pressable(rect(p.raise, 7f, p.line2, 1f), 7f)
                : pressable(rect(0x00000000, 7f), 7f));
        if (option.icon instanceof SvgIcon) ((SvgIcon) option.icon).setColor(on ? p.text : p.text3);
        if (option.icon != null) {
            item.setCompoundDrawablesRelativeWithIntrinsicBounds(option.icon, null, null, null);
        }
    }

    Choice cards(List<Option> options, String selected, int maxColumns, ChoiceListener listener) {
        ColumnsLayout grid = columns(maxColumns, 120f, 8f);
        Choice choice = new Choice(grid, options, true);
        for (Option option : options) {
            LinearLayout card = vertical();
            card.setPaddingRelative(dp(12), dp(11), dp(12), dp(11));
            card.setMinimumHeight(dp(48));
            card.setFocusable(true);
            card.setClickable(true);
            card.setContentDescription(option.description == null ? option.label : option.label + ", " + option.description);
            radioRole(card);
            if (option.visual != null) {
                LinearLayout.LayoutParams visualParams = matchWrap();
                visualParams.bottomMargin = dp(9);
                card.addView(option.visual, visualParams);
            }
            LinearLayout titleRow = horizontal();
            titleRow.setGravity(Gravity.TOP);
            TextView title = text(option.label, 14f, p.text, semi);
            title.setLineSpacing(dp(2), 1f);
            titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ImageView check = new ImageView(context);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            checkParams.setMarginStart(dp(8));
            titleRow.addView(check, checkParams);
            card.addView(titleRow, matchWrap());
            if (option.description != null && !option.description.trim().isEmpty()) {
                TextView description = text(option.description, 12.5f, p.text3, regular);
                description.setLineSpacing(dp(2), 1f);
                card.addView(description, topMargin(matchWrap(), dp(5)));
            }
            card.setOnClickListener(view -> {
                if (option.value.equals(choice.selected)) return;
                choice.select(option.value);
                if (listener != null) listener.onChoice(option.value);
                changed();
            });
            choice.items.add(card);
            grid.addView(card);
        }
        choice.select(selected);
        return choice;
    }

    private void paintCard(View card, Option option, boolean on) {
        card.setBackground(pressable(on
                ? rect(p.surface, 9f, p.accent, 2f)
                : rect(p.surface, 9f, p.line2, 1f), 9f));
        LinearLayout titleRow = (LinearLayout) ((LinearLayout) card).getChildAt(option.visual == null ? 0 : 1);
        ((ImageView) titleRow.getChildAt(1)).setImageDrawable(new CheckMark(on, p, dp(18)));
        if (option.visual instanceof ImageView && ((ImageView) option.visual).getDrawable() instanceof SvgIcon) {
            ((SvgIcon) ((ImageView) option.visual).getDrawable()).setColor(on ? p.accentText : p.text2);
        }
    }

    private static void radioRole(View view) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(RadioButton.class.getName());
                info.setCheckable(true);
                info.setChecked(host.isSelected());
            }
        });
    }

    /** A checkable chip; its touch target stays 48dp while the drawn chip is smaller. */
    CheckBox chip(String label, Drawable leading) {
        CheckBox chip = new CheckBox(context);
        chip.setButtonDrawable((Drawable) null);
        chip.setText(label);
        chip.setTextSize(14f);
        chip.setTypeface(semi);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(false);
        chip.setPaddingRelative(dp(12), dp(9), dp(13), dp(9));
        chip.setMinHeight(dp(48));
        chip.setMinimumHeight(dp(48));
        chip.setMinWidth(dp(48));
        chip.setMinimumWidth(dp(48));
        chip.setTextColor(states(p.text4, p.text, p.text3));
        if (leading != null) {
            chip.setCompoundDrawablePadding(dp(7));
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(leading, null, null, null);
        }
        paintChip(chip, false);
        return chip;
    }

    /** Owner chips (the provider tried first for a lyrics type) are filled with the state colour. */
    void paintChip(CheckBox chip, boolean owner) {
        StateListDrawable states = new StateListDrawable();
        if (owner) {
            states.addState(new int[]{android.R.attr.state_checked}, rect(p.accent, 8f));
        } else {
            states.addState(new int[]{android.R.attr.state_checked}, rect(p.surface3, 8f, p.line3, 1f));
        }
        states.addState(new int[]{}, rect(0x00000000, 8f, p.line2, 1f));
        // An InsetDrawable reports its insets as padding, so keep the chip's own padding.
        int start = chip.getPaddingStart();
        int top = chip.getPaddingTop();
        int end = chip.getPaddingEnd();
        int bottom = chip.getPaddingBottom();
        chip.setBackground(new InsetDrawable(pressable(states, 8f), 0, dp(4), 0, dp(4)));
        chip.setPaddingRelative(start, top, end, bottom);
        chip.setTextColor(owner ? states(p.text4, p.accentInk, p.text3) : states(p.text4, p.text, p.text3));
        Drawable[] drawables = chip.getCompoundDrawablesRelative();
        if (drawables[0] instanceof SvgIcon) {
            ((SvgIcon) drawables[0]).setColor(chip.isChecked() ? (owner ? p.accentInk : p.text) : p.text3);
        }
    }

    /** A static (read-only) capability chip. */
    TextView tag(String label, Drawable leading, boolean owner) {
        TextView tag = text(label, 13.5f, owner ? p.accentInk : p.text2, semi);
        tag.setGravity(Gravity.CENTER);
        tag.setPaddingRelative(dp(10), dp(8), dp(11), dp(8));
        tag.setMinHeight(dp(36));
        tag.setBackground(owner ? rect(p.accent, 8f) : rect(0x00000000, 8f, p.line2, 1f));
        if (leading != null) {
            if (leading instanceof SvgIcon) ((SvgIcon) leading).setColor(owner ? p.accentInk : p.text3);
            tag.setCompoundDrawablePadding(dp(6));
            tag.setCompoundDrawablesRelativeWithIntrinsicBounds(leading, null, null, null);
        }
        return tag;
    }

    /** A small square of a lyric layer colour, used by the layer chips and the typography rows. */
    Drawable layerMark(int color, float sizeDp) {
        GradientDrawable mark = rect(color, 2f);
        mark.setSize(dp(sizeDp), dp(sizeDp));
        return mark;
    }

    Drawable swatchMark(int color, float sizeDp) {
        GradientDrawable mark = oval(color, dp(sizeDp));
        mark.setStroke(Math.max(1, dp(1)), p.line3);
        return mark;
    }

    static final int VISUAL_ALBUM = 0;
    static final int VISUAL_BLUR = 1;
    static final int VISUAL_VIDEO = 2;
    static final int VISUAL_SOLID = 3;

    /** The small picture on a background-mode card (album cover, blurred gradient, video, solid). */
    View modeVisual(int type, int solidColor) {
        View view = new View(context);
        view.setBackground(modeDrawable(type, solidColor));
        view.setMinimumHeight(dp(52));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    Drawable modeDrawable(int type, int solidColor) {
        if (type == VISUAL_SOLID) return rect(solidColor, 6f);
        if (type == VISUAL_VIDEO) return new VideoVisual(dp(6), dp(10));
        if (type == VISUAL_BLUR) {
            GradientDrawable base = rect(0xFF182026, 6f);
            GradientDrawable warm = rect(0x00000000, 6f);
            warm.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            warm.setColors(new int[]{0xCCC8925A, 0x00C8925A});
            warm.setGradientCenter(0.3f, 0.4f);
            warm.setGradientRadius(dp(72));
            GradientDrawable cool = rect(0x00000000, 6f);
            cool.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            cool.setColors(new int[]{0xD94D8480, 0x004D8480});
            cool.setGradientCenter(0.75f, 0.65f);
            cool.setGradientRadius(dp(72));
            return new android.graphics.drawable.LayerDrawable(new Drawable[]{base, warm, cool});
        }
        GradientDrawable album = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFC8925A, 0xFF8C8160, 0xFF4D7472, 0xFF1E2A31});
        album.setCornerRadius(dp(6));
        return album;
    }

    /** A tonearm glyph above a tonearm card's label. */
    ImageView armVisual(String iconName) {
        ImageView view = new ImageView(context);
        SvgIcon glyph = new SvgIcon(iconPath(iconName), p.text2, dp(30), 2.2f);
        view.setImageDrawable(glyph);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setBackground(rect(p.surface2, 6f));
        view.setMinimumHeight(dp(44));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /** Dark film stripes with a play mark, the lab's picture for the video background. */
    static final class VideoVisual extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF bounds = new RectF();
        private final float radius;
        private final float stripe;

        VideoVisual(float radius, float stripe) {
            this.radius = radius;
            this.stripe = Math.max(1f, stripe);
        }

        @Override public void draw(Canvas canvas) {
            Rect area = getBounds();
            bounds.set(area);
            clip.reset();
            clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            int saved = canvas.save();
            canvas.clipPath(clip);
            for (float x = area.left; x < area.right; x += stripe) {
                paint.setColor(((int) ((x - area.left) / stripe)) % 2 == 0 ? 0xFF1B1F2B : 0xFF222838);
                canvas.drawRect(x, area.top, Math.min(area.right, x + stripe), area.bottom, paint);
            }
            float size = Math.min(area.width(), area.height()) * 0.32f;
            Path play = new Path();
            play.moveTo(area.exactCenterX() - size * 0.4f, area.exactCenterY() - size * 0.5f);
            play.lineTo(area.exactCenterX() + size * 0.55f, area.exactCenterY());
            play.lineTo(area.exactCenterX() - size * 0.4f, area.exactCenterY() + size * 0.5f);
            play.close();
            paint.setColor(0xD9FFFFFF);
            canvas.drawPath(play, paint);
            canvas.restoreToCount(saved);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ numbers

    interface RangeListener {
        void onValue(int value, boolean commit);
    }

    /** A slider with a numeric field. Values are integers; {@code divisor} formats e.g. 35 as 0.35. */
    final class Range {
        final LinearLayout view;
        final SeekBar seekBar;
        final EditText input;
        final TextView unitView;
        final LinearLayout chip;
        final int min;
        final int max;
        final int step;
        final int divisor;
        RangeListener listener;
        private boolean updating;
        private boolean tracking;

        Range(int min, int max, int step, int divisor, String unit, String label) {
            this.min = min;
            this.max = max;
            this.step = Math.max(1, step);
            this.divisor = Math.max(1, divisor);
            view = horizontal();
            seekBar = new SeekBar(context);
            seekBar.setMax(Math.max(1, (max - min) / this.step));
            seekBar.setProgressDrawable(sliderTrack());
            GradientDrawable thumb = oval(0xFFFFFFFF, dp(22));
            thumb.setStroke(Math.max(1, dp(1)), p.dark ? 0x59000000 : 0x38000000);
            seekBar.setThumb(thumb);
            seekBar.setSplitTrack(false);
            seekBar.setPaddingRelative(dp(12), 0, dp(12), 0);
            seekBar.setContentDescription(label);
            seekBar.setMinimumHeight(dp(48));
            view.addView(seekBar, new LinearLayout.LayoutParams(0, dp(48), 1f));

            chip = horizontal();
            chip.setBackground(rect(p.surface2, 8f, p.line2, 1f));
            input = new EditText(context);
            input.setBackground(null);
            input.setTextColor(p.text);
            input.setTextSize(15.5f);
            input.setTypeface(semi);
            input.setSingleLine(true);
            input.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            input.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            input.setInputType(this.divisor > 1
                    ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    : InputType.TYPE_CLASS_NUMBER);
            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
            input.setSelectAllOnFocus(true);
            input.setContentDescription(label);
            input.setMinWidth(dp(56));
            input.setMinimumWidth(dp(56));
            input.setMinHeight(dp(48));
            input.setMinimumHeight(dp(48));
            boolean hasUnit = unit != null && !unit.isEmpty();
            input.setPaddingRelative(dp(10), 0, hasUnit ? dp(3) : dp(12), 0);
            chip.addView(input, wrap());
            unitView = text(hasUnit ? unit : "", 12.5f, p.text3, regular);
            unitView.setPaddingRelative(0, 0, dp(10), 0);
            unitView.setVisibility(hasUnit ? View.VISIBLE : View.GONE);
            unitView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            chip.addView(unitView, wrap());
            LinearLayout.LayoutParams chipParams = wrap();
            chipParams.setMarginStart(dp(12));
            view.addView(chip, chipParams);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (updating) return;
                    int value = Range.this.min + progress * Range.this.step;
                    if (!input.hasFocus()) input.setText(format(value));
                    if (!fromUser || listener == null) return;
                    listener.onValue(value, false);
                    // Keys and accessibility actions move the slider without a touch gesture.
                    if (!tracking) listener.onValue(value, true);
                    changed();
                }

                @Override public void onStartTrackingTouch(SeekBar bar) {
                    tracking = true;
                    if (input.hasFocus()) input.clearFocus();
                }

                @Override public void onStopTrackingTouch(SeekBar bar) {
                    tracking = false;
                    if (listener != null) listener.onValue(value(), true);
                    changed();
                }
            });
            input.setOnEditorActionListener((view, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    commitInput();
                }
                return false;
            });
            input.setOnFocusChangeListener((view, focused) -> {
                chip.setBackground(focused ? rect(p.surface, 8f, p.accentLine, 1.5f) : rect(p.surface2, 8f, p.line2, 1f));
                if (!focused) commitInput();
            });
            input.setText(format(min));
        }

        int value() {
            return min + seekBar.getProgress() * step;
        }

        void setValue(int value) {
            int next = snap(value);
            updating = true;
            seekBar.setProgress((next - min) / step);
            if (!input.hasFocus()) input.setText(format(next));
            updating = false;
        }

        void setEnabled(boolean enabled) {
            seekBar.setEnabled(enabled);
            input.setEnabled(enabled);
            view.setAlpha(enabled ? 1f : 0.45f);
        }

        String format(int value) {
            if (divisor == 1) return String.valueOf(value);
            String text = String.format(Locale.ROOT, "%.2f", value / (float) divisor);
            while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }

        int snap(int value) {
            int clamped = Math.max(min, Math.min(max, value));
            return min + Math.round((clamped - min) / (float) step) * step;
        }

        private void commitInput() {
            String raw = input.getText().toString().trim().replace(',', '.');
            int target;
            try {
                target = Math.round(Float.parseFloat(raw) * divisor);
            } catch (NumberFormatException error) {
                input.setText(format(value()));
                return;
            }
            int next = snap(target);
            boolean differs = next != value();
            setValue(next);
            String formatted = format(next);
            if (!formatted.contentEquals(input.getText())) input.setText(formatted);
            if (differs && listener != null) {
                listener.onValue(next, false);
                listener.onValue(next, true);
                changed();
            }
        }
    }

    Range range(int min, int max, int step, int divisor, String unit, String label) {
        return new Range(min, max, step, divisor, unit, label);
    }

    private Drawable sliderTrack() {
        GradientDrawable track = rect(p.surface4, 2f);
        GradientDrawable fill = rect(p.accent, 2f);
        android.graphics.drawable.ClipDrawable progress =
                new android.graphics.drawable.ClipDrawable(fill, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers =
                new android.graphics.drawable.LayerDrawable(new Drawable[]{track, progress});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        for (int index = 0; index < 2; index++) {
            layers.setLayerGravity(index, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
            layers.setLayerHeight(index, dp(4));
        }
        return layers;
    }

    /** A number with − and + buttons (the lab's stepper) around an existing input. */
    LinearLayout stepper(EditText input, int min, int max, int step, String label) {
        LinearLayout box = horizontal();
        box.setBackground(rect(p.surface2, 8f, p.line2, 1f));
        ImageButton minus = iconButton("minus", label + " -" + step, 18f);
        ImageButton plus = iconButton("plus", label + " +" + step, 18f);
        input.setBackground(null);
        input.setGravity(Gravity.CENTER);
        input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        input.setTextSize(15.5f);
        input.setTypeface(semi);
        input.setTextColor(p.text);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setContentDescription(label);
        input.setMinWidth(dp(72));
        input.setMinimumWidth(dp(72));
        input.setMinHeight(dp(48));
        input.setMinimumHeight(dp(48));
        input.setPadding(dp(6), 0, dp(6), 0);
        minus.setOnClickListener(view -> bump(input, -step, min, max));
        plus.setOnClickListener(view -> bump(input, step, min, max));
        box.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        box.addView(divider(true), new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(28)));
        box.addView(input, wrap());
        box.addView(divider(true), new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(28)));
        box.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return box;
    }

    private void bump(EditText input, int delta, int min, int max) {
        int current;
        try {
            current = Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException error) {
            current = min;
        }
        int next = Math.max(min, Math.min(max, current + delta));
        input.setText(String.valueOf(next));
        input.setSelection(input.length());
        changed();
    }

    View divider(boolean vertical) {
        View line = new View(context);
        line.setBackgroundColor(p.line2);
        return line;
    }

    // ------------------------------------------------------------------ navigation

    LinearLayout homeItem(String iconName, String title, String summary) {
        LinearLayout item = horizontal();
        item.setPaddingRelative(dp(14), dp(11), dp(12), dp(11));
        item.setMinimumHeight(dp(64));
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackground(pressable(rect(0x00000000, 0f), 0f));
        ImageView tile = new ImageView(context);
        tile.setImageDrawable(icon(iconName, p.text2, 18f));
        tile.setScaleType(ImageView.ScaleType.CENTER);
        tile.setBackground(rect(p.surface3, 7f));
        tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(tile, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout texts = vertical();
        TextView titleView = text(title, 16f, p.text, semi);
        titleView.setLineSpacing(dp(2), 1f);
        texts.addView(titleView, matchWrap());
        if (summary != null && !summary.isEmpty()) {
            TextView summaryView = text(summary, 13f, p.text3, regular);
            summaryView.setSingleLine(true);
            summaryView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(summaryView, topMargin(matchWrap(), dp(4)));
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dp(14));
        textParams.setMarginEnd(dp(8));
        item.addView(texts, textParams);
        ImageView chevron = new ImageView(context);
        chevron.setImageDrawable(icon("right", p.text4, 18f));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        return item;
    }

    LinearLayout searchResult(String iconName, CharSequence title, String subtitle) {
        LinearLayout item = horizontal();
        item.setPaddingRelative(dp(10), dp(9), dp(10), dp(9));
        item.setMinimumHeight(dp(56));
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackground(pressable(rect(0x00000000, 8f), 8f));
        ImageView tile = new ImageView(context);
        tile.setImageDrawable(icon(iconName, p.text3, 16f));
        tile.setScaleType(ImageView.ScaleType.CENTER);
        tile.setBackground(rect(p.surface2, 7f, p.line, 1f));
        tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(tile, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout texts = vertical();
        TextView titleView = text(title, 15.5f, p.text, regular);
        titleView.setLineSpacing(dp(2), 1f);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, matchWrap());
        TextView subtitleView = text(subtitle, 12.5f, p.text3, regular);
        subtitleView.setLineSpacing(dp(2), 1f);
        texts.addView(subtitleView, topMargin(matchWrap(), dp(3)));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dp(12));
        item.addView(texts, textParams);
        return item;
    }

    CharSequence highlight(String value, String query) {
        SpannableString text = new SpannableString(value);
        if (query == null || query.isEmpty()) return text;
        String lower = value.toLowerCase(Locale.ROOT);
        int start = lower.indexOf(query.toLowerCase(Locale.ROOT));
        if (start >= 0) {
            int end = Math.min(value.length(), start + query.length());
            text.setSpan(new ForegroundColorSpan(p.accentText), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new FontSpan(semi), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    // ------------------------------------------------------------------ layouts

    /** Lays children out in lines, wrapping like text; mirrors for right-to-left languages. */
    static final class FlowLayout extends ViewGroup {
        private final int horizontalGap;
        private final int verticalGap;

        FlowLayout(Context context, int horizontalGap, int verticalGap) {
            super(context);
            this.horizontalGap = horizontalGap;
            this.verticalGap = verticalGap;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int mode = MeasureSpec.getMode(widthSpec);
            int limit = mode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE / 4
                    : Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
            int lineWidth = 0;
            int lineHeight = 0;
            int total = 0;
            int widest = 0;
            boolean any = false;
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child.getVisibility() == GONE) continue;
                child.measure(MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int width = child.getMeasuredWidth();
                if (lineWidth > 0 && lineWidth + horizontalGap + width > limit) {
                    total += lineHeight + verticalGap;
                    widest = Math.max(widest, lineWidth);
                    lineWidth = 0;
                    lineHeight = 0;
                }
                lineWidth += (lineWidth > 0 ? horizontalGap : 0) + width;
                lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
                any = true;
            }
            if (any) {
                total += lineHeight;
                widest = Math.max(widest, lineWidth);
            }
            int width = mode == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec)
                    : widest + getPaddingLeft() + getPaddingRight();
            if (mode == MeasureSpec.AT_MOST) width = Math.min(width, MeasureSpec.getSize(widthSpec));
            setMeasuredDimension(width, resolveSize(total + getPaddingTop() + getPaddingBottom(), heightSpec));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int limit = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            int y = getPaddingTop();
            int index = 0;
            int count = getChildCount();
            while (index < count) {
                int end = index;
                int lineWidth = 0;
                int lineHeight = 0;
                for (; end < count; end++) {
                    View child = getChildAt(end);
                    if (child.getVisibility() == GONE) continue;
                    int childWidth = child.getMeasuredWidth();
                    if (lineWidth > 0 && lineWidth + horizontalGap + childWidth > limit) break;
                    lineWidth += (lineWidth > 0 ? horizontalGap : 0) + childWidth;
                    lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
                }
                int x = 0;
                for (int position = index; position < end; position++) {
                    View child = getChildAt(position);
                    if (child.getVisibility() == GONE) continue;
                    int childWidth = child.getMeasuredWidth();
                    int childHeight = child.getMeasuredHeight();
                    int childLeft = rtl ? width - getPaddingRight() - x - childWidth : getPaddingLeft() + x;
                    int childTop = y + (lineHeight - childHeight) / 2;
                    child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                    x += childWidth + horizontalGap;
                }
                y += lineHeight + verticalGap;
                index = end == index ? end + 1 : end;
            }
        }
    }

    /** Equal-width columns that drop to fewer columns when a column would get too narrow. */
    static final class ColumnsLayout extends ViewGroup {
        private final int maxColumns;
        private final int minColumnWidth;
        private final int gap;
        private int columns = 1;

        ColumnsLayout(Context context, int maxColumns, int minColumnWidth, int gap) {
            super(context);
            this.maxColumns = Math.max(1, maxColumns);
            this.minColumnWidth = minColumnWidth;
            this.gap = gap;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            List<View> visible = visibleChildren(this);
            int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                    ? minColumnWidth * maxColumns + gap * (maxColumns - 1) + getPaddingLeft() + getPaddingRight()
                    : MeasureSpec.getSize(widthSpec);
            int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            columns = maxColumns;
            while (columns > 1 && (available - gap * (columns - 1)) / columns < minColumnWidth) columns--;
            int columnWidth = Math.max(0, (available - gap * (columns - 1)) / columns);
            int total = measureRows(visible, columns, columnWidth, gap);
            setMeasuredDimension(width, resolveSize(total + getPaddingTop() + getPaddingBottom(), heightSpec));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            layoutRows(this, visibleChildren(this), columns, gap);
        }
    }

    /** Segmented options: one row when every label fits, otherwise two columns or a single column. */
    static final class SegmentedLayout extends ViewGroup {
        private final int gap;
        private int columns = 1;

        SegmentedLayout(Context context, int gap) {
            super(context);
            this.gap = gap;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            List<View> visible = visibleChildren(this);
            int count = Math.max(1, visible.size());
            boolean unspecified = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED;
            int widest = 0;
            for (View child : visible) widest = Math.max(widest, minimumWidth(child));
            int width = unspecified
                    ? widest * count + gap * (count - 1) + getPaddingLeft() + getPaddingRight()
                    : MeasureSpec.getSize(widthSpec);
            int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            columns = count;
            if (!fits(visible, available, count)) {
                columns = count >= 4 && count % 2 == 0 && fits(visible, available, 2) ? 2 : 1;
            }
            int columnWidth = Math.max(0, (available - gap * (columns - 1)) / columns);
            int total = measureRows(visible, columns, columnWidth, gap);
            setMeasuredDimension(width, resolveSize(total + getPaddingTop() + getPaddingBottom(), heightSpec));
        }

        private boolean fits(List<View> visible, int available, int columnCount) {
            int share = (available - gap * (columnCount - 1)) / columnCount;
            for (View child : visible) {
                if (minimumWidth(child) > share) return false;
            }
            return true;
        }

        private static int minimumWidth(View view) {
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                int widest = 0;
                for (String word : text.getText().toString().split("\\s+")) {
                    widest = Math.max(widest, (int) Math.ceil(text.getPaint().measureText(word)));
                }
                Drawable leading = text.getCompoundDrawablesRelative()[0];
                int icon = leading == null ? 0 : leading.getIntrinsicWidth() + text.getCompoundDrawablePadding();
                return widest + icon + text.getPaddingStart() + text.getPaddingEnd();
            }
            view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            return view.getMeasuredWidth();
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            layoutRows(this, visibleChildren(this), columns, gap);
        }
    }

    private static List<View> visibleChildren(ViewGroup group) {
        List<View> visible = new ArrayList<>();
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child.getVisibility() != View.GONE) visible.add(child);
        }
        return visible;
    }

    /** Measures rows of equal-width cells; cells in one row share the tallest cell's height. */
    private static int measureRows(List<View> visible, int columns, int columnWidth, int gap) {
        int total = 0;
        for (int start = 0; start < visible.size(); start += columns) {
            int end = Math.min(visible.size(), start + columns);
            int rowHeight = 0;
            for (int index = start; index < end; index++) {
                View child = visible.get(index);
                child.measure(View.MeasureSpec.makeMeasureSpec(columnWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            for (int index = start; index < end; index++) {
                visible.get(index).measure(View.MeasureSpec.makeMeasureSpec(columnWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.EXACTLY));
            }
            total += rowHeight + (start > 0 ? gap : 0);
        }
        return total;
    }

    private static void layoutRows(ViewGroup parent, List<View> visible, int columns, int gap) {
        boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int width = parent.getWidth();
        int y = parent.getPaddingTop();
        for (int start = 0; start < visible.size(); start += columns) {
            int end = Math.min(visible.size(), start + columns);
            int x = 0;
            int rowHeight = 0;
            for (int index = start; index < end; index++) {
                View child = visible.get(index);
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int childLeft = rtl ? width - parent.getPaddingRight() - x - childWidth : parent.getPaddingLeft() + x;
                child.layout(childLeft, y, childLeft + childWidth, y + childHeight);
                x += childWidth + gap;
                rowHeight = Math.max(rowHeight, childHeight);
            }
            y += rowHeight + gap;
        }
    }

    /**
     * The first child takes the remaining width; the others keep their natural size beside it. When the
     * first child would get narrower than {@code minMain}, the others move below it, aligned to the end.
     */
    static final class AdaptiveRow extends ViewGroup {
        private final int minMain;
        private final int gap;
        private boolean stacked;

        AdaptiveRow(Context context, int minMain, int gap) {
            super(context);
            this.minMain = minMain;
            this.gap = gap;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            boolean unspecified = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED;
            int width = unspecified ? Integer.MAX_VALUE / 4 : MeasureSpec.getSize(widthSpec);
            int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
            int trailingWidth = 0;
            int trailingHeight = 0;
            int trailingCount = 0;
            for (int index = 1; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child.getVisibility() == GONE) continue;
                child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                trailingWidth += (trailingCount > 0 ? gap : 0) + child.getMeasuredWidth();
                trailingHeight = Math.max(trailingHeight, child.getMeasuredHeight());
                trailingCount++;
            }
            View main = getChildCount() > 0 ? getChildAt(0) : null;
            int mainAvailable = available - (trailingCount > 0 ? trailingWidth + gap : 0);
            stacked = trailingCount > 0 && mainAvailable < minMain;
            int height;
            if (main == null || main.getVisibility() == GONE) {
                height = trailingHeight;
            } else if (!stacked) {
                main.measure(MeasureSpec.makeMeasureSpec(Math.max(0, mainAvailable), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height = Math.max(main.getMeasuredHeight(), trailingHeight);
            } else {
                main.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height = main.getMeasuredHeight() + gap + trailingHeight;
            }
            int measuredWidth = unspecified ? Math.min(width, (main == null ? 0 : main.getMeasuredWidth())
                    + trailingWidth + gap + getPaddingLeft() + getPaddingRight()) : width;
            int measuredHeight = Math.max(getSuggestedMinimumHeight(), height + getPaddingTop() + getPaddingBottom());
            setMeasuredDimension(measuredWidth, resolveSize(measuredHeight, heightSpec));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            int width = right - left;
            int innerTop = getPaddingTop();
            int innerHeight = bottom - top - getPaddingTop() - getPaddingBottom();
            View main = getChildCount() > 0 ? getChildAt(0) : null;
            int trailingTop;
            int trailingHeight = 0;
            int trailingWidth = 0;
            int trailingCount = 0;
            for (int index = 1; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child.getVisibility() == GONE) continue;
                trailingWidth += (trailingCount > 0 ? gap : 0) + child.getMeasuredWidth();
                trailingHeight = Math.max(trailingHeight, child.getMeasuredHeight());
                trailingCount++;
            }
            int x;
            if (main != null && main.getVisibility() != GONE) {
                int mainWidth = main.getMeasuredWidth();
                int mainHeight = main.getMeasuredHeight();
                int mainTop = stacked ? innerTop : innerTop + (innerHeight - mainHeight) / 2;
                int mainLeft = rtl ? width - getPaddingRight() - mainWidth : getPaddingLeft();
                main.layout(mainLeft, mainTop, mainLeft + mainWidth, mainTop + mainHeight);
                if (stacked) {
                    trailingTop = mainTop + mainHeight + gap;
                    x = width - getPaddingLeft() - getPaddingRight() - trailingWidth;
                } else {
                    trailingTop = innerTop;
                    x = mainWidth + gap;
                }
            } else {
                trailingTop = innerTop;
                x = 0;
            }
            int rowHeight = stacked ? trailingHeight : innerHeight;
            for (int index = 1; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child.getVisibility() == GONE) continue;
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int childLeft = rtl ? width - getPaddingRight() - x - childWidth : getPaddingLeft() + x;
                int childTop = trailingTop + (rowHeight - childHeight) / 2;
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                x += childWidth + gap;
            }
        }
    }

    // ------------------------------------------------------------------ drawables

    /** The radio mark of a card: an empty ring, or a filled circle with a check. */
    static final class CheckMark extends Drawable {
        private final boolean on;
        private final SettingsPalette palette;
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path check = SvgIcon.parsed("m6.5 12.5 3.6 3.6 7.4-8.2");

        CheckMark(boolean on, SettingsPalette palette, int size) {
            this.on = on;
            this.palette = palette;
            this.size = size;
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float radius = Math.min(bounds.width(), bounds.height()) / 2f;
            float stroke = Math.max(1f, radius / 9f);
            paint.setStyle(Paint.Style.FILL);
            if (on) {
                paint.setColor(palette.accent);
                canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint);
                int saved = canvas.save();
                float scale = radius * 2f / 24f;
                canvas.translate(bounds.exactCenterX() - radius, bounds.exactCenterY() - radius);
                canvas.scale(scale, scale);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.6f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(palette.accentInk);
                canvas.drawPath(check, paint);
                canvas.restoreToCount(saved);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setColor(palette.line3);
                canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius - stroke / 2f, paint);
            }
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A stroked 24×24 icon from SVG path data, the same data the settings lab draws. */
    static final class SvgIcon extends Drawable {
        private static final Map<String, Path> CACHE = new HashMap<>();
        private final Path path;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private boolean mirrored;

        SvgIcon(String data, int color, int size, float strokeWidth) {
            this.path = parsed(data);
            this.size = size;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        static synchronized Path parsed(String data) {
            Path cached = CACHE.get(data);
            if (cached == null) {
                cached = PathParser.parse(data);
                CACHE.put(data, cached);
            }
            return cached;
        }

        void setColor(int color) {
            if (paint.getColor() == color) return;
            paint.setColor(color);
            invalidateSelf();
        }

        void setStrokeWidth(float width) {
            paint.setStrokeWidth(width);
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            float scale = Math.min(bounds.width(), bounds.height()) / 24f;
            int saved = canvas.save();
            canvas.translate(bounds.exactCenterX(), bounds.exactCenterY());
            if (mirrored && getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) canvas.scale(-1f, 1f);
            canvas.scale(scale, scale);
            canvas.translate(-12f, -12f);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(saved);
        }

        @Override public boolean onLayoutDirectionChanged(int layoutDirection) {
            invalidateSelf();
            return mirrored;
        }

        @Override public void setAutoMirrored(boolean value) {
            mirrored = value;
        }

        @Override public boolean isAutoMirrored() {
            return mirrored;
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** SVG path data (M L H V C S Q T A Z, absolute and relative) to an android.graphics.Path. */
    static final class PathParser {
        private final String data;
        private int index;

        private PathParser(String data) {
            this.data = data == null ? "" : data;
        }

        static Path parse(String data) {
            Path path = new Path();
            try {
                new PathParser(data).run(path);
            } catch (RuntimeException ignored) {
                // Keep what was parsed: an icon must never take the settings screen down.
            }
            return path;
        }

        private void run(Path path) {
            float x = 0f;
            float y = 0f;
            float startX = 0f;
            float startY = 0f;
            float controlX = 0f;
            float controlY = 0f;
            char command = 0;
            char previous = 0;
            while (true) {
                skipSeparators();
                if (index >= data.length()) return;
                char next = data.charAt(index);
                if (isCommand(next)) {
                    command = next;
                    index++;
                } else if (command == 0) {
                    return;
                }
                boolean relative = Character.isLowerCase(command);
                char type = Character.toUpperCase(command);
                float ox = relative ? x : 0f;
                float oy = relative ? y : 0f;
                switch (type) {
                    case 'M': {
                        x = number() + ox;
                        y = number() + oy;
                        path.moveTo(x, y);
                        startX = x;
                        startY = y;
                        command = relative ? 'l' : 'L';
                        break;
                    }
                    case 'L': {
                        x = number() + ox;
                        y = number() + oy;
                        path.lineTo(x, y);
                        break;
                    }
                    case 'H': {
                        x = number() + ox;
                        path.lineTo(x, y);
                        break;
                    }
                    case 'V': {
                        y = number() + oy;
                        path.lineTo(x, y);
                        break;
                    }
                    case 'C': {
                        float x1 = number() + ox;
                        float y1 = number() + oy;
                        float x2 = number() + ox;
                        float y2 = number() + oy;
                        x = number() + ox;
                        y = number() + oy;
                        path.cubicTo(x1, y1, x2, y2, x, y);
                        controlX = x2;
                        controlY = y2;
                        break;
                    }
                    case 'S': {
                        float x1 = previous == 'C' || previous == 'S' ? 2f * x - controlX : x;
                        float y1 = previous == 'C' || previous == 'S' ? 2f * y - controlY : y;
                        float x2 = number() + ox;
                        float y2 = number() + oy;
                        x = number() + ox;
                        y = number() + oy;
                        path.cubicTo(x1, y1, x2, y2, x, y);
                        controlX = x2;
                        controlY = y2;
                        break;
                    }
                    case 'Q': {
                        float x1 = number() + ox;
                        float y1 = number() + oy;
                        x = number() + ox;
                        y = number() + oy;
                        path.quadTo(x1, y1, x, y);
                        controlX = x1;
                        controlY = y1;
                        break;
                    }
                    case 'T': {
                        float x1 = previous == 'Q' || previous == 'T' ? 2f * x - controlX : x;
                        float y1 = previous == 'Q' || previous == 'T' ? 2f * y - controlY : y;
                        x = number() + ox;
                        y = number() + oy;
                        path.quadTo(x1, y1, x, y);
                        controlX = x1;
                        controlY = y1;
                        break;
                    }
                    case 'A': {
                        float rx = number();
                        float ry = number();
                        float rotation = number();
                        boolean large = flag();
                        boolean sweep = flag();
                        float endX = number() + ox;
                        float endY = number() + oy;
                        arc(path, x, y, rx, ry, rotation, large, sweep, endX, endY);
                        x = endX;
                        y = endY;
                        break;
                    }
                    case 'Z': {
                        path.close();
                        x = startX;
                        y = startY;
                        command = 0;
                        break;
                    }
                    default:
                        return;
                }
                previous = type;
            }
        }

        private static void arc(Path path, float x0, float y0, float rxIn, float ryIn, float angle,
                                boolean large, boolean sweep, float x, float y) {
            if (x0 == x && y0 == y) return;
            double rx = Math.abs(rxIn);
            double ry = Math.abs(ryIn);
            if (rx == 0 || ry == 0) {
                path.lineTo(x, y);
                return;
            }
            double phi = Math.toRadians(angle % 360f);
            double cos = Math.cos(phi);
            double sin = Math.sin(phi);
            double dx = (x0 - x) / 2.0;
            double dy = (y0 - y) / 2.0;
            double x1 = cos * dx + sin * dy;
            double y1 = -sin * dx + cos * dy;
            double lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry);
            if (lambda > 1) {
                double scale = Math.sqrt(lambda);
                rx *= scale;
                ry *= scale;
            }
            double numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
            double denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1;
            double coefficient = denominator == 0 ? 0 : Math.sqrt(Math.max(0, numerator / denominator));
            if (large == sweep) coefficient = -coefficient;
            double cx1 = coefficient * rx * y1 / ry;
            double cy1 = coefficient * -ry * x1 / rx;
            double cx = cos * cx1 - sin * cy1 + (x0 + x) / 2.0;
            double cy = sin * cx1 + cos * cy1 + (y0 + y) / 2.0;
            double start = vectorAngle(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry);
            double extent = vectorAngle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry);
            if (!sweep && extent > 0) extent -= 2 * Math.PI;
            else if (sweep && extent < 0) extent += 2 * Math.PI;
            int segments = Math.max(1, (int) Math.ceil(Math.abs(extent) / (Math.PI / 2)));
            double delta = extent / segments;
            double handle = 4.0 / 3.0 * Math.tan(delta / 4);
            double fromX = x0;
            double fromY = y0;
            double theta = start;
            for (int segment = 0; segment < segments; segment++) {
                double cos1 = Math.cos(theta);
                double sin1 = Math.sin(theta);
                double theta2 = theta + delta;
                double cos2 = Math.cos(theta2);
                double sin2 = Math.sin(theta2);
                double toX = cos * rx * cos2 - sin * ry * sin2 + cx;
                double toY = sin * rx * cos2 + cos * ry * sin2 + cy;
                double d1x = -rx * sin1;
                double d1y = ry * cos1;
                double d2x = -rx * sin2;
                double d2y = ry * cos2;
                double c1x = fromX + handle * (cos * d1x - sin * d1y);
                double c1y = fromY + handle * (sin * d1x + cos * d1y);
                double c2x = toX - handle * (cos * d2x - sin * d2y);
                double c2y = toY - handle * (sin * d2x + cos * d2y);
                if (segment == segments - 1) {
                    toX = x;
                    toY = y;
                }
                path.cubicTo((float) c1x, (float) c1y, (float) c2x, (float) c2y, (float) toX, (float) toY);
                fromX = toX;
                fromY = toY;
                theta = theta2;
            }
        }

        private static double vectorAngle(double ux, double uy, double vx, double vy) {
            double dot = ux * vx + uy * vy;
            double length = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy);
            double value = Math.acos(Math.max(-1, Math.min(1, length == 0 ? 1 : dot / length)));
            return ux * vy - uy * vx < 0 ? -value : value;
        }

        private float number() {
            skipSeparators();
            int start = index;
            if (index < data.length() && (data.charAt(index) == '-' || data.charAt(index) == '+')) index++;
            boolean dot = false;
            boolean digits = false;
            while (index < data.length()) {
                char c = data.charAt(index);
                if (c >= '0' && c <= '9') {
                    digits = true;
                    index++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    index++;
                } else if ((c == 'e' || c == 'E') && digits) {
                    int next = index + 1;
                    if (next < data.length() && (data.charAt(next) == '-' || data.charAt(next) == '+')) next++;
                    if (next < data.length() && Character.isDigit(data.charAt(next))) {
                        index = next;
                        while (index < data.length() && Character.isDigit(data.charAt(index))) index++;
                    }
                    break;
                } else {
                    break;
                }
            }
            if (!digits) throw new IllegalArgumentException("Number expected at " + start);
            return Float.parseFloat(data.substring(start, index));
        }

        private boolean flag() {
            skipSeparators();
            char c = data.charAt(index);
            if (c != '0' && c != '1') throw new IllegalArgumentException("Arc flag expected at " + index);
            index++;
            return c == '1';
        }

        private void skipSeparators() {
            while (index < data.length() && (Character.isWhitespace(data.charAt(index)) || data.charAt(index) == ',')) {
                index++;
            }
        }

        private static boolean isCommand(char c) {
            return "MmLlHhVvCcSsQqTtAaZz".indexOf(c) >= 0;
        }
    }

    // ------------------------------------------------------------------ icon set (settings lab js/core.js, data/schema.js)

    private static final Map<String, String> ICONS = new HashMap<>();
    private static final Map<String, Boolean> MIRRORED = new HashMap<>();

    static {
        ICONS.put("search", "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm9 16-4.35-4.35");
        ICONS.put("close", "M6 6l12 12M18 6 6 18");
        ICONS.put("right", "m9 6 6 6-6 6");
        ICONS.put("left", "m15 6-6 6 6 6");
        ICONS.put("down", "m6 9 6 6 6-6");
        ICONS.put("up", "m6 15 6-6 6 6");
        ICONS.put("arrowLeft", "M19 12H5m6-6-6 6 6 6");
        ICONS.put("reset", "M4 12a8 8 0 1 0 2.4-5.7M4 4v4h4");
        ICONS.put("check", "m5 12.5 4.5 4.5L19 7.5");
        ICONS.put("eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12ZM12 9.2a2.8 2.8 0 1 0 0 5.6 2.8 2.8 0 0 0 0-5.6Z");
        ICONS.put("eyeOff", "M3 3l18 18M10.6 5.6c.5-.1.9-.1 1.4-.1 6 0 9.5 6.5 9.5 6.5a15.6 15.6 0 0 1-2.9 3.7M6.3 6.6A15.4 15.4 0 0 0 2.5 12S6 18.5 12 18.5a9 9 0 0 0 4.4-1.1M10 10.1a2.8 2.8 0 0 0 3.9 3.9");
        ICONS.put("info", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm0 5v.01M11 11.5h1v5h1");
        ICONS.put("warn", "M12 9v4m0 3.5v.01M10.3 3.9 1.9 18a2 2 0 0 0 1.7 3h16.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z");
        ICONS.put("sun", "M12 3v1.5M12 19.5V21M4.6 4.6l1.1 1.1M18.3 18.3l1.1 1.1M3 12h1.5M19.5 12H21M4.6 19.4l1.1-1.1M18.3 5.7l1.1-1.1M12 7.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z");
        ICONS.put("moon", "M20.5 13.2A8.5 8.5 0 1 1 10.8 3.5a6.6 6.6 0 0 0 9.7 9.7Z");
        ICONS.put("alignLeft", "M4 6h16M4 10h10M4 14h16M4 18h10");
        ICONS.put("alignCenter", "M4 6h16M7 10h10M4 14h16M7 18h10");
        ICONS.put("alignRight", "M4 6h16M10 10h10M4 14h16M10 18h10");
        ICONS.put("plus", "M12 5v14M5 12h14");
        ICONS.put("minus", "M5 12h14");
        ICONS.put("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3");
        ICONS.put("external", "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5");
        ICONS.put("copy", "M9 9h10v10H9zM5 15V5h10");
        ICONS.put("landscape", "M3.5 7h17v10h-17z");
        ICONS.put("portrait", "M8 3.5h8v17H8z");
        ICONS.put("square", "M5.5 5.5h13v13h-13z");
        ICONS.put("grip", "M9 6.5h.01M15 6.5h.01M9 12h.01M15 12h.01M9 17.5h.01M15 17.5h.01");
        ICONS.put("karaoke", "M12 14a4 4 0 0 0 4-4V5a4 4 0 1 0-8 0v5a4 4 0 0 0 4 4Zm-7-4a7 7 0 0 0 14 0M12 17v4M9 21h6");
        ICONS.put("synced", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Zm0-15v5l3 2");
        ICONS.put("plain", "M5 6h14M5 12h14M5 18h9");
        ICONS.put("translate", "M4 5h8M8 3v2M6 5c0 4 2 7 6 9M11 5c-.4 3.4-2.2 6.2-5.5 8M14 21l3.5-9L21 21M15.5 18h4");
        ICONS.put("pronunciation", "M11 5 6 9H3v6h3l5 4V5ZM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13");
        ICONS.put("menu.general", "M4 7h10M18 7h2M4 17h2M10 17h10M14 4v6M6 14v6");
        ICONS.put("menu.providers", "M9 18V5.5l11-2V16M6.5 20.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM17.5 18.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z");
        ICONS.put("menu.ai", "M12 3l1.2 3.8L17 8l-3.8 1.2L12 13l-1.2-3.8L7 8l3.8-1.2L12 3ZM5 14l.8 2.2L8 17l-2.2.8L5 20l-.8-2.2L2 17l2.2-.8L5 14Zm13-1 1 3 3 1-3 1-1 3-1-3-3-1 3-1 1-3Z");
        ICONS.put("menu.appearance", "M12 3a9 9 0 0 0 0 18h1.4a2.1 2.1 0 0 0 0-4.2h-1a1.7 1.7 0 0 1 0-3.4H15a6 6 0 0 0 0-12h-3ZM7.5 9h.01M9.5 6h.01M15 6.5h.01M17 10h.01");
        ICONS.put("menu.lyrics", "M5 7h8M5 12h6M5 17h8M16 8l4 4-4 4V8Z");
        ICONS.put("menu.player", "M8 3H3v5M16 3h5v5M8 21H3v-5M16 21h5v-5");
        ICONS.put("menu.system", "M4 7h6M14 7h6M4 17h10M18 17h2M10 4v6M14 14v6");
        ICONS.put("arm.s", "M18 4c1.5 4 .5 8-3 11l-4 4");
        ICONS.put("arm.straight", "M18 4 9 18");
        ICONS.put("arm.j", "M18 4v9c0 3-2 5-5 6");
        ICONS.put("arm.linear", "M4 6h16M12 6v10");
        MIRRORED.put("right", Boolean.TRUE);
        MIRRORED.put("left", Boolean.TRUE);
        MIRRORED.put("arrowLeft", Boolean.TRUE);
    }

    static String iconPath(String name) {
        String path = ICONS.get(name);
        return path == null ? name : path;
    }
}

