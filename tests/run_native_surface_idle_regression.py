#!/usr/bin/env python3
"""Spotify's native ivLyrics card and inline surfaces sleep while their window is hidden.

The production NativeSurface class (verbatim from IvLyricsBridge) runs against a fake main-thread
queue and vsync. A playing surface follows frames, a paused or not-shown one polls every 250 ms as
before, and a hidden window (Spotify in the background, the screen off) schedules nothing until the
window shows again, when the surface updates at once. No device or Spotify.
"""
from pathlib import Path
import subprocess

from regression_runtime import SHARED, REPORTS, java_tool


def declaration(source, signature):
    assert source.count(signature) == 1, signature
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


work = REPORTS / "native-surface-idle"
work.mkdir(parents=True, exist_ok=True)
stubs = {
    "android/content/Context.java": "package android.content;\npublic class Context { }\n",
    "android/graphics/Rect.java": """
package android.graphics;
public class Rect {
    public int left, top, right, bottom;
    public void offset(int dx, int dy) { left += dx; right += dx; top += dy; bottom += dy; }
}
""",
    "android/view/Choreographer.java": """
package android.view;
import java.util.ArrayList;
import java.util.List;
public final class Choreographer {
    public interface FrameCallback { void doFrame(long frameTimeNanos); }
    private static final Choreographer INSTANCE = new Choreographer();
    public final List<FrameCallback> callbacks = new ArrayList<>();
    public static Choreographer getInstance() { return INSTANCE; }
    public void postFrameCallback(FrameCallback callback) {
        if (callbacks.contains(callback)) throw new AssertionError("duplicate vsync request");
        callbacks.add(callback);
    }
    public void removeFrameCallback(FrameCallback callback) { callbacks.remove(callback); }
    /** One display frame: the callbacks requested before it run once. */
    public void frame(long frameTimeNanos) {
        List<FrameCallback> due = new ArrayList<>(callbacks);
        callbacks.clear();
        for (FrameCallback callback : due) callback.doFrame(frameTimeNanos);
    }
}
""",
    "android/view/View.java": """
package android.view;
import android.content.Context;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    /** The main thread's clock and its queue of (task, due time), shared by every view. */
    public static long now;
    public static final List<Object[]> QUEUE = new ArrayList<>();
    private final Context context;
    public boolean attached, shown = true;
    public int windowVisibility = VISIBLE;
    public int invalidations;
    public View(Context context) { this.context = context; }
    public Context getContext() { return context; }
    public boolean isAttachedToWindow() { return attached; }
    public boolean isShown() { return attached && shown; }
    public int getWindowVisibility() { return attached ? windowVisibility : GONE; }
    public boolean post(Runnable action) { return postDelayed(action, 0L); }
    public boolean postDelayed(Runnable action, long delay) { QUEUE.add(new Object[]{action, now + delay}); return true; }
    public boolean removeCallbacks(Runnable action) { QUEUE.removeIf(entry -> entry[0] == action); return true; }
    public void invalidate() { invalidations++; }
    public View getRootView() { return this; }
    public void getLocationOnScreen(int[] location) { location[0] = 0; location[1] = 0; }
    public boolean getGlobalVisibleRect(Rect rect) { return true; }
    public int getWidth() { return 100; }
    public int getHeight() { return 100; }
    protected void onAttachedToWindow() { }
    protected void onDetachedFromWindow() { }
    protected void onWindowVisibilityChanged(int visibility) { }
    /** As Android dispatches them: attaching reports the window's visibility after onAttachedToWindow. */
    public void dispatchAttachedToWindow() {
        attached = true;
        onAttachedToWindow();
        if (windowVisibility != GONE) onWindowVisibilityChanged(windowVisibility);
    }
    public void dispatchDetachedFromWindow() { onDetachedFromWindow(); attached = false; }
    public void dispatchWindowVisibilityChanged(int visibility) { windowVisibility = visibility; onWindowVisibilityChanged(visibility); }
    /** Runs, in order, every task due by the given time and returns how many ran. */
    public static int runUntil(long time) {
        int ran = 0;
        while (true) {
            Object[] next = null;
            for (Object[] entry : QUEUE) {
                if ((Long) entry[1] <= time && (next == null || (Long) entry[1] < (Long) next[1])) next = entry;
            }
            if (next == null) break;
            QUEUE.remove(next);
            now = Math.max(now, (Long) next[1]);
            ((Runnable) next[0]).run();
            ran++;
        }
        now = Math.max(now, time);
        return ran;
    }
}
""",
    "android/view/ViewGroup.java": """
package android.view;
import android.content.Context;
public class ViewGroup extends View {
    public ViewGroup(Context context) { super(context); }
    public int getChildCount() { return 0; }
    public View getChildAt(int index) { return null; }
    public void onDescendantInvalidated(View child, View target) { }
}
""",
    "android/widget/FrameLayout.java": """
package android.widget;
import android.content.Context;
import android.view.ViewGroup;
public class FrameLayout extends ViewGroup { public FrameLayout(Context context) { super(context); } }
""",
}
files = []
for name, content in stubs.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    files.append(path)
bridge = (SHARED / "IvLyricsBridge.java").read_text()
surface = declaration(bridge, "    private abstract static class NativeSurface extends FrameLayout {")
test = work / "NativeSurfaceIdleRegression.java"
test.write_text(Path(__file__).with_name("NativeSurfaceIdleRegression.java.in").read_text()
                .replace("// INSERT_NATIVE_SURFACE", surface))
files.append(test)
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "kr.ivlis.ivlyricsandroid.NativeSurfaceIdleRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=60)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)

