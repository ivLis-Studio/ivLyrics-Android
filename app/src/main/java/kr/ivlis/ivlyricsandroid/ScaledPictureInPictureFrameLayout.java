package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.ViewParent;
import android.widget.FrameLayout;

final class ScaledPictureInPictureFrameLayout extends FrameLayout {
    private int virtualWidthPx;
    private int virtualHeightPx;

    ScaledPictureInPictureFrameLayout(Context context, int virtualWidthPx, int virtualHeightPx) {
        super(context);
        this.virtualWidthPx = Math.max(1, virtualWidthPx);
        this.virtualHeightPx = Math.max(1, virtualHeightPx);
        setClipChildren(true);
        setClipToPadding(true);
    }

    void setVirtualSize(int virtualWidthPx, int virtualHeightPx) {
        int safeWidth = Math.max(1, virtualWidthPx);
        int safeHeight = Math.max(1, virtualHeightPx);
        if (this.virtualWidthPx == safeWidth && this.virtualHeightPx == safeHeight) {
            return;
        }
        this.virtualWidthPx = safeWidth;
        this.virtualHeightPx = safeHeight;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(virtualWidthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(virtualHeightPx, MeasureSpec.EXACTLY)
        );
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childCount = getChildCount();
        for (int index = 0; index < childCount; index++) {
            getChildAt(index).layout(0, 0, virtualWidthPx, virtualHeightPx);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float scale = contentScale(width, height);
        float dx = (width - virtualWidthPx * scale) * 0.5f;
        float dy = (height - virtualHeightPx * scale) * 0.5f;
        int save = canvas.save();
        canvas.clipRect(0, 0, width, height);
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
        invalidate();
        return super.invalidateChildInParent(location, dirty);
    }

    boolean copyScaledContentBoundsOnScreen(Rect outRect) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || outRect == null) {
            return false;
        }
        float scale = contentScale(width, height);
        float contentWidth = virtualWidthPx * scale;
        float contentHeight = virtualHeightPx * scale;
        float dx = (width - contentWidth) * 0.5f;
        float dy = (height - contentHeight) * 0.5f;
        int[] location = new int[2];
        getLocationOnScreen(location);
        outRect.set(
                Math.round(location[0] + dx),
                Math.round(location[1] + dy),
                Math.round(location[0] + dx + contentWidth),
                Math.round(location[1] + dy + contentHeight)
        );
        return !outRect.isEmpty();
    }

    private float contentScale(int width, int height) {
        return Math.min(width / (float) virtualWidthPx, height / (float) virtualHeightPx);
    }
}
