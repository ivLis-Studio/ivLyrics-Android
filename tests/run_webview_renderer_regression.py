#!/usr/bin/env python3
"""A WebView renderer that dies must release its page, never take the app down.

Every WebView of a process shares one renderer; in the Spotify module that process is Spotify. A
WebView whose client does not handle onRenderProcessGone makes Android kill the process. This runs
the production motion lyrics stage, video background and in-app browser page (declarations taken
verbatim from BaseLyricsActivity) against minimal Android stubs: each handles the death, releases
the dead WebView and replaces it as documented, and stale messages of a dead page are dropped.
No device, network or Spotify.
"""
from pathlib import Path
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar


def declaration(source, signature):
    assert source.count(signature) == 1, signature
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


work = REPORTS / "webview-renderer"
work.mkdir(parents=True, exist_ok=True)
stubs = {
    "android/content/Context.java": """
package android.content;
import android.content.res.AssetManager;
import android.content.res.Resources;
import java.util.HashMap;
import java.util.Map;
public class Context {
    public static final int MODE_PRIVATE = 0;
    public final AssetManager assets = new AssetManager();
    public final Resources resources = new Resources();
    public final Map<String, SharedPreferences> preferences = new HashMap<>();
    public AssetManager getAssets() { return assets; }
    public Resources getResources() { return resources; }
    public Context getApplicationContext() { return this; }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return preferences.computeIfAbsent(name, key -> new MemoryPreferences());
    }
}
""",
    "android/content/SharedPreferences.java": """
package android.content;
public interface SharedPreferences {
    String getString(String key, String fallback);
    boolean getBoolean(String key, boolean fallback);
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        Editor putBoolean(String key, boolean value);
        void apply();
    }
}
""",
    "android/content/MemoryPreferences.java": """
package android.content;
import java.util.HashMap;
import java.util.Map;
public final class MemoryPreferences implements SharedPreferences {
    public final Map<String, Object> values = new HashMap<>();
    public String getString(String key, String fallback) { Object value = values.get(key); return value instanceof String ? (String) value : fallback; }
    public boolean getBoolean(String key, boolean fallback) { Object value = values.get(key); return value instanceof Boolean ? (Boolean) value : fallback; }
    public Editor edit() {
        return new Editor() {
            public Editor putString(String key, String value) { values.put(key, value); return this; }
            public Editor putBoolean(String key, boolean value) { values.put(key, value); return this; }
            public void apply() { }
        };
    }
}
""",
    "android/content/res/AssetManager.java": """
package android.content.res;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class AssetManager {
    public final Map<String, String> files = new HashMap<>();
    public InputStream open(String name) throws IOException {
        String value = files.get(name);
        if (value == null) throw new FileNotFoundException(name);
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
""",
    "android/content/res/Resources.java": """
package android.content.res;
import android.util.DisplayMetrics;
public class Resources {
    private final DisplayMetrics metrics = new DisplayMetrics();
    public Resources() { metrics.density = 2f; }
    public DisplayMetrics getDisplayMetrics() { return metrics; }
}
""",
    "android/util/DisplayMetrics.java": """
package android.util;
public class DisplayMetrics { public float density = 1f; }
""",
    "android/graphics/Color.java": """
package android.graphics;
public class Color {
    public static final int BLACK = 0xFF000000, TRANSPARENT = 0, WHITE = 0xFFFFFFFF;
    public static int argb(int alpha, int red, int green, int blue) { return (alpha << 24) | (red << 16) | (green << 8) | blue; }
    public static int rgb(int red, int green, int blue) { return argb(255, red, green, blue); }
}
""",
    "android/graphics/Shader.java": """
package android.graphics;
public class Shader { public enum TileMode { CLAMP, REPEAT, MIRROR, DECAL } }
""",
    "android/graphics/RenderEffect.java": """
package android.graphics;
public final class RenderEffect {
    public final float radiusX, radiusY;
    public final Shader.TileMode mode;
    private RenderEffect(float radiusX, float radiusY, Shader.TileMode mode) { this.radiusX = radiusX; this.radiusY = radiusY; this.mode = mode; }
    public static RenderEffect createBlurEffect(float radiusX, float radiusY, Shader.TileMode mode) { return new RenderEffect(radiusX, radiusY, mode); }
}
""",
    "android/graphics/drawable/Drawable.java": """
package android.graphics.drawable;
public class Drawable { }
""",
    "android/graphics/drawable/GradientDrawable.java": """
package android.graphics.drawable;
public class GradientDrawable extends Drawable {
    public int color;
    public float radius;
    public void setColor(int value) { color = value; }
    public void setCornerRadius(float value) { radius = value; }
}
""",
    "android/os/Build.java": """
package android.os;
public class Build {
    public static class VERSION { public static int SDK_INT = 26; }
    public static class VERSION_CODES { public static final int LOLLIPOP = 21, M = 23, P = 28, R = 30, S = 31; }
}
""",
    "android/os/Handler.java": """
package android.os;
import java.util.ArrayList;
import java.util.List;
public final class Handler {
    public static final List<Runnable> READY = new ArrayList<>(), DELAYED = new ArrayList<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { READY.add(task); return true; }
    public boolean postDelayed(Runnable task, long delay) { DELAYED.add(task); return true; }
    public void removeCallbacks(Runnable task) { READY.removeIf(queued -> queued == task); DELAYED.removeIf(queued -> queued == task); }
    public void removeCallbacksAndMessages(Object token) { READY.clear(); DELAYED.clear(); }
    public static void drain() { while (!READY.isEmpty()) READY.remove(0).run(); }
    /** Runs the delayed tasks queued so far once (a repeating task queues itself again). */
    public static void elapse() { List<Runnable> due = new ArrayList<>(DELAYED); DELAYED.clear(); for (Runnable task : due) task.run(); drain(); }
    public static void reset() { READY.clear(); DELAYED.clear(); }
}
""",
    "android/os/Looper.java": """
package android.os;
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return MAIN; }
}
""",
    "android/os/SystemClock.java": """
package android.os;
public final class SystemClock {
    public static long now = 1000L;
    public static long elapsedRealtime() { return now; }
    public static long uptimeMillis() { return now; }
}
""",
    "android/view/ViewParent.java": """
package android.view;
public interface ViewParent { }
""",
    "android/view/ViewPropertyAnimator.java": """
package android.view;
/** Ends at once: a fade lands on its final alpha and runs its end action. */
public class ViewPropertyAnimator {
    private final View view;
    private float alpha = Float.NaN;
    private Runnable end;
    ViewPropertyAnimator(View view) { this.view = view; }
    public ViewPropertyAnimator alpha(float value) { alpha = value; return this; }
    public ViewPropertyAnimator setDuration(long duration) { return this; }
    public ViewPropertyAnimator withEndAction(Runnable action) { end = action; return this; }
    public void start() {
        if (!Float.isNaN(alpha)) view.setAlpha(alpha);
        Runnable action = end;
        alpha = Float.NaN;
        end = null;
        if (action != null) action.run();
    }
    public void cancel() { alpha = Float.NaN; end = null; }
}
""",
    "android/view/View.java": """
package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.RenderEffect;
import android.graphics.drawable.Drawable;
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    public static final int OVER_SCROLL_NEVER = 2;
    public static final int LAYOUT_DIRECTION_LTR = 0, LAYOUT_DIRECTION_RTL = 1;
    public interface OnClickListener { void onClick(View view); }
    public interface OnLongClickListener { boolean onLongClick(View view); }
    private final Context context;
    private final ViewPropertyAnimator animator = new ViewPropertyAnimator(this);
    private int visibility = VISIBLE;
    private float alpha = 1f;
    private ViewGroup.LayoutParams layoutParams;
    private Object tag;
    private int paddingLeft, paddingTop, paddingRight, paddingBottom;
    ViewParent parent;
    public Drawable background;
    public int backgroundColor;
    public RenderEffect renderEffect;
    public OnClickListener clickListener;
    public boolean focusable, clickable, selected, focused;
    public float rotation;
    public CharSequence contentDescription;
    public View(Context context) { this.context = context; }
    public Context getContext() { return context; }
    public Resources getResources() { return context.getResources(); }
    public void setVisibility(int value) { visibility = value; }
    public int getVisibility() { return visibility; }
    public void setAlpha(float value) { alpha = value; }
    public float getAlpha() { return alpha; }
    public ViewPropertyAnimator animate() { return animator; }
    public void setBackground(Drawable value) { background = value; }
    public void setBackgroundColor(int color) { backgroundColor = color; }
    public void setRenderEffect(RenderEffect effect) { renderEffect = effect; }
    public void setOnClickListener(OnClickListener listener) { clickListener = listener; clickable = true; }
    public void setOnLongClickListener(OnLongClickListener listener) { }
    public void setHapticFeedbackEnabled(boolean enabled) { }
    public void setFocusable(boolean value) { focusable = value; }
    public void setClickable(boolean value) { clickable = value; }
    public void setSelected(boolean value) { selected = value; }
    public void setContentDescription(CharSequence value) { contentDescription = value; }
    public boolean requestFocus() { focused = true; return true; }
    public boolean isFocused() { return focused; }
    public boolean isInTouchMode() { return true; }
    public boolean performClick() { if (clickListener != null) clickListener.onClick(this); return clickListener != null; }
    public void setTag(Object value) { tag = value; }
    public Object getTag() { return tag; }
    public int getLayoutDirection() { return LAYOUT_DIRECTION_LTR; }
    public void setLayoutParams(ViewGroup.LayoutParams params) { layoutParams = params; }
    public ViewGroup.LayoutParams getLayoutParams() { return layoutParams; }
    public void setRotation(float value) { rotation = value; }
    public void bringToFront() { if (parent instanceof ViewGroup) ((ViewGroup) parent).moveToEnd(this); }
    public void setPadding(int left, int top, int right, int bottom) { paddingLeft = left; paddingTop = top; paddingRight = right; paddingBottom = bottom; }
    public int getPaddingLeft() { return paddingLeft; }
    public int getPaddingTop() { return paddingTop; }
    public int getPaddingRight() { return paddingRight; }
    public int getPaddingBottom() { return paddingBottom; }
    public void setHorizontalScrollBarEnabled(boolean value) { }
    public void setVerticalScrollBarEnabled(boolean value) { }
    public void setOverScrollMode(int mode) { }
    public ViewParent getParent() { return parent; }
    public WindowInsets onApplyWindowInsets(WindowInsets insets) { return insets; }
    protected void onDetachedFromWindow() { }
}
""",
    "android/view/ViewGroup.java": """
package android.view;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
public class ViewGroup extends View implements ViewParent {
    public final List<View> children = new ArrayList<>();
    public ViewGroup(Context context) { super(context); }
    public void addView(View child, LayoutParams params) { addView(child, -1, params); }
    public void addView(View child, int index, LayoutParams params) {
        if (child.parent != null) throw new IllegalStateException("The specified child already has a parent");
        child.parent = this;
        child.setLayoutParams(params);
        if (index < 0) children.add(child); else children.add(index, child);
    }
    public void removeView(View child) { if (children.remove(child)) child.parent = null; }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int index) { return children.get(index); }
    public int indexOfChild(View child) { return children.indexOf(child); }
    void moveToEnd(View child) { children.remove(child); children.add(child); }
    public void setClipChildren(boolean value) { }
    public void setClipToPadding(boolean value) { }
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1, WRAP_CONTENT = -2;
        public int width, height;
        public LayoutParams(int width, int height) { this.width = width; this.height = height; }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin, marginEnd;
        public MarginLayoutParams(int width, int height) { super(width, height); }
        public void setMargins(int left, int top, int right, int bottom) { leftMargin = left; topMargin = top; rightMargin = right; bottomMargin = bottom; }
        public void setMarginEnd(int value) { marginEnd = value; }
    }
}
""",
    "android/widget/FrameLayout.java": """
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
public class FrameLayout extends ViewGroup {
    public FrameLayout(Context context) { super(context); }
    public FrameLayout(Context context, AttributeSet attrs) { super(context); }
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public static final int UNSPECIFIED_GRAVITY = -1;
        public int gravity = UNSPECIFIED_GRAVITY;
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(int width, int height, int gravity) { super(width, height); this.gravity = gravity; }
    }
}
""",
    "android/widget/LinearLayout.java": """
package android.widget;
import android.content.Context;
import android.view.ViewGroup;
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0, VERTICAL = 1;
    public LinearLayout(Context context) { super(context); }
    public void setOrientation(int orientation) { }
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
    }
}
""",
    "android/widget/TextView.java": """
package android.widget;
import android.content.Context;
import android.view.View;
public class TextView extends View {
    private CharSequence text = "";
    public int textColor;
    public TextView(Context context) { super(context); }
    public void setText(CharSequence value) { text = value; }
    public CharSequence getText() { return text; }
    public void setTextColor(int color) { textColor = color; }
    public void setTextSize(int unit, float size) { }
    public void setGravity(int gravity) { }
    public void setIncludeFontPadding(boolean value) { }
    public void setSingleLine(boolean value) { }
}
""",
    "android/webkit/WebSettings.java": """
package android.webkit;
public class WebSettings {
    public static final int MIXED_CONTENT_ALWAYS_ALLOW = 0, MIXED_CONTENT_NEVER_ALLOW = 1, MIXED_CONTENT_COMPATIBILITY_MODE = 2;
    public boolean javascript, domStorage, safeBrowsing, overview, wideViewPort;
    public boolean fileAccess = true, contentAccess = true, fileFromFile = true, universalFromFile = true, zoom = true, gestureRequired = true;
    public int textZoom = 100, mixedContent = -1;
    public void setJavaScriptEnabled(boolean value) { javascript = value; }
    public void setDomStorageEnabled(boolean value) { domStorage = value; }
    public void setAllowFileAccess(boolean value) { fileAccess = value; }
    public void setAllowContentAccess(boolean value) { contentAccess = value; }
    public void setAllowFileAccessFromFileURLs(boolean value) { fileFromFile = value; }
    public void setAllowUniversalAccessFromFileURLs(boolean value) { universalFromFile = value; }
    public void setMixedContentMode(int value) { mixedContent = value; }
    public void setSafeBrowsingEnabled(boolean value) { safeBrowsing = value; }
    public void setLoadWithOverviewMode(boolean value) { overview = value; }
    public void setUseWideViewPort(boolean value) { wideViewPort = value; }
    public void setSupportZoom(boolean value) { zoom = value; }
    public void setTextZoom(int value) { textZoom = value; }
    public void setMediaPlaybackRequiresUserGesture(boolean value) { gestureRequired = value; }
}
""",
    "android/webkit/WebChromeClient.java": """
package android.webkit;
public class WebChromeClient { }
""",
    "android/webkit/RenderProcessGoneDetail.java": """
package android.webkit;
public class RenderProcessGoneDetail {
    private final boolean crashed;
    public RenderProcessGoneDetail(boolean crashed) { this.crashed = crashed; }
    public boolean didCrash() { return crashed; }
    public int rendererPriorityAtExit() { return 0; }
}
""",
    "android/webkit/WebViewClient.java": """
package android.webkit;
import android.graphics.Bitmap;
public class WebViewClient {
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
    public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
    public void onPageStarted(WebView view, String url, Bitmap favicon) { }
    public void onPageFinished(WebView view, String url) { }
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return null; }
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) { return null; }
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) { return false; }
}
""",
    "android/webkit/WebView.java": """
package android.webkit;
import android.content.Context;
import android.view.View;
import java.util.*;
public class WebView extends View {
    public static final List<WebView> instances = new ArrayList<>();
    public final WebSettings settings = new WebSettings();
    public final List<String> scripts = new ArrayList<>();
    public final Map<String, Object> bridges = new HashMap<>();
    public WebViewClient client;
    public WebChromeClient chromeClient;
    public String baseUrl, html, url;
    public boolean destroyed, paused;
    public int resumes, pauses;
    public WebView(Context context) { super(context); instances.add(this); }
    public WebSettings getSettings() { return settings; }
    public void addJavascriptInterface(Object bridge, String name) { bridges.put(name, bridge); }
    public void removeJavascriptInterface(String name) { bridges.remove(name); }
    public void setWebViewClient(WebViewClient value) { client = value; }
    public void setWebChromeClient(WebChromeClient value) { chromeClient = value; }
    public void loadDataWithBaseURL(String base, String data, String mime, String encoding, String history) { live(); baseUrl = base; html = data; }
    public void loadUrl(String value) { live(); url = value; }
    public void evaluateJavascript(String script, ValueCallback<String> callback) { live(); scripts.add(script); }
    public void stopLoading() { }
    public void clearHistory() { }
    public void onPause() { paused = true; pauses++; }
    public void onResume() { paused = false; resumes++; }
    public void destroy() { destroyed = true; }
    private void live() { if (destroyed) throw new IllegalStateException("a destroyed WebView was used"); }
    /** The shared renderer died: true when the client handled it (Android would otherwise kill the app). */
    public boolean gone(boolean crashed) { return client != null && client.onRenderProcessGone(this, new RenderProcessGoneDetail(crashed)); }
}
""",
}
files = []
for name, content in stubs.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    files.append(path)

activity = (SHARED / "BaseLyricsActivity.java").read_text()
browser = "\n".join(declaration(activity, signature) for signature in (
    "private void addInAppBrowserWebView()",
    "private void replaceDeadInAppBrowserWebView()",
    "private static final class AccessibleWebView extends WebView {",
    "private void detachFromParent(View view)",
))
# A dead browser page is replaced where it was created; nothing else creates or drops it.
assert activity.count("addInAppBrowserWebView();") == 2 and activity.count("replaceDeadInAppBrowserWebView();") == 1
video = (SHARED / "YouTubeBackgroundView.java").read_text()
settings = declaration(video, "void setBackgroundSettings(AiLyricsSettings.BackgroundSettings settings)")
# The blur is computed as before; it is only kept for a WebView made later.
assert "videoBlurRadius = effectiveBlur <= 0 ? 0f : Math.min(36f, Math.max(0f, effectiveBlur * 0.16f));" in settings
assert "RenderEffect.createBlurEffect(videoBlurRadius, videoBlurRadius, Shader.TileMode.CLAMP)" in declaration(
    video, "private void applyVideoBlur(WebView view)")

test = work / "WebViewRendererRegression.java"
test.write_text(Path(__file__).with_name("WebViewRendererRegression.java.in").read_text()
                .replace("// INSERT_BROWSER_DECLARATIONS", browser))
production = [SHARED / name for name in ("MotionLyricsModeView.java", "YouTubeBackgroundView.java", "WebViewSupport.java")]
files += production + [test]
test_classpath = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-cp", test_classpath, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", test_classpath, "kr.ivlis.ivlyricsandroid.WebViewRendererRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=60)
report = "Production WebView renderer-death handling; minimal Android stubs, no device/network.\n"
for path in production + [SHARED / "BaseLyricsActivity.java"]:
    report += f"{path.name} SHA256 {hashlib.sha256(path.read_bytes()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)

