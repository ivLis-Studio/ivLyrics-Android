#!/usr/bin/env python3
"""Exercise complete AI requests, cache restoration and errors with an in-memory provider.

Uses the production repository entry point and provider adapter. No network/device calls.
"""
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "repository-completion"
work.mkdir(parents=True, exist_ok=True)
handler = work / "Handler.java"
handler.write_text('''package android.os;
import java.util.concurrent.ConcurrentLinkedQueue;
public final class Handler {
    private static final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { queue.add(task); return true; }
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
fixture = work / "RepositoryCompletionRegression.java"
fixture.write_text((ROOT / "tests/RepositoryCompletionRegression.java.in").read_text())
production_files = [SHARED / name for name in (
    "LyricsLine.java", "LyricsResult.java", "LyricsDiskCache.java", "TrackSnapshot.java",
    "AiLyricsSettings.java", "AiLyricsRepository.java",
)]
files = production_files + [handler, clock, ROOT / "tests/android/os/Looper.java", fixture]
test_classpath = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", test_classpath, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", test_classpath,
                         "kr.ivlis.ivlyricsandroid.RepositoryCompletionRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
report = "Production AI entry point, synthetic provider transport/settings/lyrics. No network/device calls.\n"
for file in production_files:
    report += f"{file.name} SHA256 {hashlib.sha256(file.read_bytes()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
