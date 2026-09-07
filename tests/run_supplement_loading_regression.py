#!/usr/bin/env python3
"""Exercise production AI callback ordering with a controllable main-thread queue.

Synthetic lyrics and settings only. No provider, account, network, or device calls.
"""
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "supplement-loading"
work.mkdir(parents=True, exist_ok=True)
handler = work / "Handler.java"
handler.write_text('''package android.os;
import java.util.concurrent.ConcurrentLinkedQueue;
public final class Handler {
    private static final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    public static volatile Runnable beforePost;
    public Handler(Looper looper) { }
    public boolean post(Runnable task) {
        Runnable hook = beforePost;
        if (hook != null) hook.run();
        queue.add(task);
        return true;
    }
    public boolean postDelayed(Runnable task, long delay) { return post(task); }
    public void removeCallbacks(Runnable task) { queue.removeIf(queued -> queued == task); }
    public static void drain() { Runnable task; while ((task = queue.poll()) != null) task.run(); }
}
''')
clock = work / "SystemClock.java"
clock.write_text('''package android.os;
public final class SystemClock {
    public static long uptimeMillis() { return 1000L; }
    public static long elapsedRealtime() { return 1000L; }
}
''')
def declaration(text, signature):
    if text.count(signature) != 1:
        raise ValueError(f"Expected one source declaration: {signature}")
    start = text.index(signature)
    cursor = text.index("{", start) + 1
    depth = 1
    while depth:
        depth += (text[cursor] == "{") - (text[cursor] == "}")
        cursor += 1
    return text[start:cursor]

activity = SHARED / "BaseLyricsActivity.java"
preview_loading = declaration(activity.read_text(), "private boolean isPreviewSupplementGenerating(int item)")
test_file = work / "SupplementLoadingRegression.java"
test_file.write_text((ROOT / "tests/SupplementLoadingRegression.java.in").read_text()
                     .replace("// INSERT_PREVIEW_LOADING_METHOD", preview_loading))
files = [SHARED / name for name in ("LyricsLine.java", "LyricsResult.java", "LyricsDiskCache.java", "AiLyricsRepository.java")]
files += [handler, clock, ROOT / "tests/android/os/Looper.java",
          test_file]
test_classpath = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", test_classpath, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", test_classpath,
                         "kr.ivlis.ivlyricsandroid.SupplementLoadingRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
report = "Production AI lifecycle, synthetic settings/lyrics, controlled main-thread queue. No provider/device calls.\n"
for file in files[:4] + [activity]:
    report += f"{file.name} SHA256 {hashlib.sha256(file.read_bytes()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
