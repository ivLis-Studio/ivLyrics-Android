package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.IdentityHashMap;

/** Paired settings actions wrap into a column before labels or switches get squeezed. */
final class SettingsControlRow extends LinearLayout {
    private final IdentityHashMap<View, LayoutParams> horizontalParams = new IdentityHashMap<>();

    SettingsControlRow(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int required = 0;
        int visible = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (!horizontalParams.containsKey(child)) {
                horizontalParams.put(child, new LayoutParams((LayoutParams) child.getLayoutParams()));
            }
            if (child.getVisibility() == GONE) continue;
            LayoutParams original = horizontalParams.get(child);
            child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int natural = Math.max(child.getMinimumWidth(), original.width);
            if (!(child instanceof TextView)) natural = Math.max(natural, child.getMeasuredWidth());
            if (child instanceof TextView) {
                TextView text = (TextView) child;
                for (String line : text.getText().toString().split("\n")) {
                    natural = Math.max(natural, (int) Math.ceil(text.getPaint().measureText(line))
                            + text.getCompoundPaddingLeft() + text.getCompoundPaddingRight());
                }
            }
            // Allow long prose to wrap inside each action, but reserve a usable column.
            required += Math.max(SettingsAppearance.dp(getContext(), 48),
                    Math.min(natural, SettingsAppearance.dp(getContext(), 200)))
                    + original.leftMargin + original.rightMargin;
            visible++;
        }
        boolean stacked = visible > 1 && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED
                && required > available;
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
        int shown = 0;
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            LayoutParams original = horizontalParams.get(child);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            params.width = stacked ? ViewGroup.LayoutParams.MATCH_PARENT : original.width;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.weight = stacked ? 0f : original.weight;
            params.leftMargin = stacked ? 0 : original.leftMargin;
            params.rightMargin = stacked ? 0 : original.rightMargin;
            params.topMargin = stacked && shown > 0 ? SettingsAppearance.dp(getContext(), 8) : original.topMargin;
            if (child.getVisibility() != GONE) shown++;
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
