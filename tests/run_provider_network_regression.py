#!/usr/bin/env python3
"""Run production provider network orchestration with synthetic HTTP and bitmap decoding."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "provider-network"
work.mkdir(parents=True, exist_ok=True)
stubs = {
    "Bitmap.java": """package android.graphics;
public final class Bitmap { public boolean isRecycled(){return false;} public int getWidth(){return 640;} public int getHeight(){return 640;} }
""",
    "BitmapFactory.java": """package android.graphics;
import java.util.concurrent.CountDownLatch;
public final class BitmapFactory {
 public static CountDownLatch entered, release;
 public static void reset(){entered=new CountDownLatch(1);release=new CountDownLatch(1);}
 public static Bitmap decodeStream(java.io.InputStream input){
  entered.countDown();try{release.await();}catch(InterruptedException error){Thread.currentThread().interrupt();}
  return new Bitmap();
 }
}
""",
    "Handler.java": """package android.os;
import java.util.concurrent.ConcurrentLinkedQueue;
public final class Handler {
 private static final ConcurrentLinkedQueue<Runnable> queue=new ConcurrentLinkedQueue<>();
 public Handler(Looper looper){} public boolean post(Runnable task){queue.add(task);return true;}
 public static void drain(){Runnable task;while((task=queue.poll())!=null)task.run();}
}
""",
    "Log.java": """package android.util;
public final class Log { public static int d(String tag,String text){return 0;} public static int i(String tag,String text){return 0;} }
""",
    "Base64.java": """package android.util;
public final class Base64 {
 public static final int NO_WRAP=2;
 public static String encodeToString(byte[] value,int flags){return java.util.Base64.getEncoder().encodeToString(value);}
}
""",
    "IvLyricsBridge.java": """package kr.ivlis.ivlyricsandroid;
public final class IvLyricsBridge { public static boolean isEmbedded(android.content.Context context){return false;} }
""",
}
files = []
for name, source in stubs.items():
    file = work / name
    file.write_text(source)
    files.append(file)
fixture = work / "ProviderNetworkRegression.java"
fixture.write_text((ROOT / "tests/ProviderNetworkRegression.java.in").read_text())
files += [SHARED / name for name in (
    "LyricsRepository.java", "LyricsPlusLyricsProvider.java", "RequestCancellation.java", "LatestLyricsRequest.java",
    "LyricsResult.java", "LyricsLine.java", "TrackSnapshot.java", "LyricsProviderSettings.java",
    "LocalLyricsStore.java", "LocalLyricsI18n.java",
)]
files += [ROOT / "tests/android/os/Looper.java", fixture]
cp = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", cp, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.ProviderNetworkRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
