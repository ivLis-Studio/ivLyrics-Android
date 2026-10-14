#!/usr/bin/env python3
"""Run the production appearance persistence, cloud sync and native-lyric filtering without an SDK."""
import re
import subprocess
from regression_runtime import SHARED, REPORTS, java_tool, json_jar, classpath

def declaration(source, signature):
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]

settings = (SHARED / "AiLyricsSettings.java").read_text()
renderer = (SHARED / "LyricsView.java").read_text()
constants = "\n".join("static final String " + value for value in re.findall(
    r'^    (?:private )?static final String (KEY_\w+ = "[^"]*";)', settings, re.M))
cloud = settings[settings.index("    private static final Set<String> CLOUD_SETTING_KEYS"):
                 settings.index("    static final List<Provider> PROVIDERS")]
methods = "\n".join(declaration(settings, signature) for signature in (
    "JSONObject exportCloudSettings()", "synchronized void importCloudSettings(",
    "String videoFallback()", "String titleTapAction()", "String videoAspect()",
    "boolean videoEdgeToEdge()", "boolean landscapeShowOriginal()", "boolean landscapeShowArtwork()",
    "boolean showLyricsNotFound()", "void setAppearanceChoice(", "void setAppearanceToggle("))
methods += "\n" + "\n".join(declaration(settings, signature) for signature in (
    "void setVinylTrackChanges(", "void setVinylRotationSpeed(", "void setVinylSimplifiedTitle("))
filtering = declaration(renderer, "private List<DrawGroup> visibleLyricGroups(")
work = REPORTS / "appearance"
work.mkdir(parents=True, exist_ok=True)
fixture = r'''
package kr.ivlis.ivlyricsandroid;
import java.util.*;
import org.json.*;
public final class AppearanceRegression {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("check " + checks); checks++; }
    static class SharedPreferences {
        Map<String,Object> data = new LinkedHashMap<>();
        Map<String,?> getAll() { return data; }
        String getString(String key,String fallback) { return (String)data.getOrDefault(key,fallback); }
        boolean getBoolean(String key,boolean fallback) { return (Boolean)data.getOrDefault(key,fallback); }
        Editor edit() { return new Editor(); }
        class Editor {
            Editor putString(String key,String value) { data.put(key,value); return this; }
            Editor putBoolean(String key,boolean value) { data.put(key,value); return this; }
            Editor putInt(String key,int value) { data.put(key,value); return this; }
            Editor putFloat(String key,float value) { data.put(key,value); return this; }
            void apply() {}
        }
    }
    static class Settings {
        SharedPreferences prefs = new SharedPreferences();
        Object cachedSnapshot;
        // CONSTANTS
        // CLOUD
        // SETTINGS
    }
    static class DrawGroup {
        final boolean supplement;
        DrawGroup(boolean value) { supplement=value; }
    }
    static class Renderer {
        boolean originalVisible=true;
        // FILTER
    }
    public static void main(String[] args) throws Exception {
        Settings settings = new Settings();
        check(settings.videoFallback().equals("gradient-background"));
        check(settings.videoAspect().equals("fill"));
        check(settings.titleTapAction().equals("player"));
        check(!settings.videoEdgeToEdge());
        check(settings.landscapeShowOriginal() && settings.landscapeShowArtwork() && settings.showLyricsNotFound());
        settings.prefs.edit().putString(Settings.KEY_BACKGROUND_MODE,"video-background").putInt(Settings.KEY_BACKGROUND_BRIGHTNESS,30).apply();
        settings.setAppearanceChoice(AppearanceOptions.FALLBACK,"solid-background");
        settings.setAppearanceChoice(AppearanceOptions.TITLE_ACTION,"context");
        settings.setAppearanceChoice(AppearanceOptions.VIDEO_ASPECT,"fit");
        settings.setAppearanceToggle(AppearanceOptions.VIDEO_EDGES,true);
        settings.setAppearanceToggle(AppearanceOptions.LANDSCAPE_ORIGINAL,false);
        settings.setAppearanceToggle(AppearanceOptions.LANDSCAPE_ARTWORK,false);
        settings.setAppearanceToggle(AppearanceOptions.EMPTY_NOTICE,false);
        settings.setVinylTrackChanges(false);
        settings.setVinylRotationSpeed(175);
        settings.setVinylSimplifiedTitle(true);
        settings.prefs.edit().putString("api_keys","private").apply();
        JSONObject exported = settings.exportCloudSettings();
        check(!exported.has("api_keys"));
        Settings restored = new Settings();
        restored.importCloudSettings(new JSONObject(exported.toString()));
        check(restored.videoFallback().equals("solid-background"));
        check(restored.titleTapAction().equals("context"));
        check(restored.videoAspect().equals("fit"));
        check(restored.videoEdgeToEdge());
        check(!restored.landscapeShowOriginal() && !restored.landscapeShowArtwork() && !restored.showLyricsNotFound());
        check(restored.prefs.getString(Settings.KEY_BACKGROUND_MODE,"").equals("video-background"));
        check(restored.prefs.data.get(Settings.KEY_BACKGROUND_BRIGHTNESS).equals(30));
        check(restored.prefs.data.get(RecordRotation.CHANGE_KEY).equals(false));
        check(restored.prefs.data.get(RecordRotation.SPEED_KEY).equals(175));
        check(restored.prefs.data.get(RecordRotation.SIMPLE_TITLE_KEY).equals(true));
        restored.setAppearanceChoice(AppearanceOptions.VIDEO_ASPECT,"stretch");
        check(restored.videoAspect().equals("stretch"));
        Renderer renderer = new Renderer();
        DrawGroup original = new DrawGroup(false), pronunciation = new DrawGroup(true), translation = new DrawGroup(true);
        List<DrawGroup> groups = new ArrayList<>(Arrays.asList(original,pronunciation,translation));
        check(renderer.visibleLyricGroups(new ArrayList<>(groups)).size()==3);
        renderer.originalVisible=false;
        check(renderer.visibleLyricGroups(new ArrayList<>(groups)).equals(Arrays.asList(pronunciation,translation)));
        renderer.originalVisible=true;
        check(renderer.visibleLyricGroups(new ArrayList<>(groups)).equals(groups));
        System.out.println("Appearance persistence/cloud/filtering regression: "+checks+" checks passed");
    }
}
'''
fixture = fixture.replace("// CONSTANTS", constants).replace("// CLOUD", cloud).replace("// SETTINGS", methods).replace("// FILTER", filtering)
target = work / "AppearanceRegression.java"
target.write_text(fixture)
dependencies = classpath(work, json_jar())
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-cp", dependencies, "-d", str(work),
                str(SHARED / "AppearanceOptions.java"), str(SHARED / "RecordRotation.java"), str(target)], check=True)
result = subprocess.run([java_tool("java"), "-cp", dependencies, "kr.ivlis.ivlyricsandroid.AppearanceRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
if result.returncode:
    raise SystemExit(result.returncode)

# Execute the exact HTML/JS emitted by the native video view, with a synthetic iframe API.
video = (SHARED / "YouTubeBackgroundView.java").read_text()
html_class = ('import java.util.Locale; public class VideoHtml { int videoScalePercent=100; String videoAspect="fill";'
              + declaration(video, "private String buildHtml(")
              + 'public static void main(String[] args) { System.out.print(new VideoHtml().buildHtml("fixture")); }}')
html_source = work / "VideoHtml.java"
html_source.write_text(html_class)
subprocess.run([java_tool("javac"), "-d", str(work), str(html_source)], check=True)
html = subprocess.check_output([java_tool("java"), "-cp", str(work), "VideoHtml"], text=True)
(work / "video.html").write_text(html)
inline_script = re.search(r"<script>(.*?)</script>", html, re.S).group(1)
(work / "video.js").write_text(inline_script)
subprocess.run(["node", str(__import__("pathlib").Path(__file__).with_name("video_appearance_regression.cjs")), str(work / "video.js")], check=True)

