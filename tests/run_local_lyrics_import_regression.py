#!/usr/bin/env python3
"""Replay production picker/result/lifecycle code for standalone and Spotify host contexts."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool

work = REPORTS / "local-lyrics-import"
work.mkdir(parents=True, exist_ok=True)
source = (SHARED / "BaseLyricsActivity.java").read_text()

def declaration(signature):
    if source.count(signature) != 1:
        raise ValueError(signature)
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]

fixture = (ROOT / "tests/LocalLyricsImportRegression.java.in").read_text()
for marker, signature in {
    "CHOOSE_METHOD": "private void chooseLocalLyrics()",
    "RESULT_METHOD": "protected void onActivityResult(int requestCode, int resultCode, Intent data)",
    "OBSERVE_METHOD": "private void observeLocalLyricsChanges()",
    "CHANGED_METHOD": "void onLocalLyricsChanged(String key)",
    "STOP_OBSERVE_METHOD": "private void stopObservingLocalLyricsChanges()",
}.items():
    fixture = fixture.replace("// " + marker, declaration(signature))
for marker, prefix in (("SAVE_PENDING", "outState.put"), ("RESTORE_PENDING", "localLyricsImport")):
    lines = [line.strip() for line in source.splitlines() if line.strip().startswith(prefix)
             and '"local_lyrics.import_' in line]
    if len(lines) != 2:
        raise ValueError("Pending picker state must save and restore both identity and duration")
    fixture = fixture.replace("// " + marker, "\n        ".join(lines))

stubs = {
    "Context.java": """package android.content;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public abstract Context getApplicationContext();
 public abstract SharedPreferences getSharedPreferences(String name,int mode);
 public abstract ContentResolver getContentResolver();
}
""",
    "ContentResolver.java": """package android.content;
public abstract class ContentResolver {
 public abstract java.io.InputStream openInputStream(android.net.Uri uri);
 public abstract android.database.Cursor query(android.net.Uri uri,String[] projection,String selection,String[] args,String sort);
}
""",
    "Intent.java": """package android.content;
public final class Intent {
 public static final String ACTION_OPEN_DOCUMENT="android.intent.action.OPEN_DOCUMENT",CATEGORY_OPENABLE="android.intent.category.OPENABLE";
 private String action,type,category; private android.net.Uri data;
 public Intent(){} public Intent(String action){this.action=action;}
 public Intent addCategory(String value){category=value;return this;} public Intent setType(String value){type=value;return this;}
 public String getAction(){return action;} public String getType(){return type;} public boolean hasCategory(String value){return value.equals(category);}
 public Intent setData(android.net.Uri value){data=value;return this;} public android.net.Uri getData(){return data;}
}
""",
    "ActivityNotFoundException.java": "package android.content; public final class ActivityNotFoundException extends RuntimeException {}",
    "Uri.java": """package android.net;
public final class Uri { private final String value; private Uri(String value){this.value=value;}
 public static Uri parse(String value){return new Uri(value);} public String toString(){return value;}
 public String getLastPathSegment(){return value.substring(value.lastIndexOf('/')+1);}
}
""",
    "Cursor.java": "package android.database; public interface Cursor extends AutoCloseable { boolean moveToFirst(); String getString(int column); void close(); }",
    "OpenableColumns.java": "package android.provider; public final class OpenableColumns { public static final String DISPLAY_NAME=\"_display_name\"; }",
    "Bundle.java": """package android.os;
public final class Bundle {
 private final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public void putString(String key,String value){values.put(key,value);} public String getString(String key){return (String)values.get(key);}
 public void putLong(String key,long value){values.put(key,value);} public long getLong(String key){return (Long)values.getOrDefault(key,0L);}
}
""",
    "Handler.java": """package android.os;
public final class Handler {
 private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> tasks=new java.util.concurrent.ConcurrentLinkedQueue<>();
 public boolean post(Runnable task){tasks.add(task);return true;}
 public static void drain(){Runnable task;while((task=tasks.poll())!=null)task.run();}
}
""",
}
files = []
for name, content in stubs.items():
    path = work / name
    path.write_text(content)
    files.append(path)
test = work / "LocalLyricsImportRegression.java"
test.write_text(fixture)
files += [test] + [SHARED / name for name in ("LocalLyricsStore.java", "LrcParser.java", "LyricsLine.java", "LyricsResult.java", "TrackSnapshot.java")]
cp = classpath(work, compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", cp, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.LocalLyricsImportRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=20)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
