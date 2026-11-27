#!/usr/bin/env python3
"""The standalone notification listener re-reads media sessions only for Spotify's notifications.

Notification access keeps NowPlayingService bound whenever it is granted, so every notification any
app posts or removes reaches it. Only Spotify's can change which Spotify session plays; another
app's notification used to re-read the sessions four times (once and in an 80/240/560 ms burst) and
republish an unchanged snapshot. This runs the production service against a stub listener base and
a counting session manager. No device or notification access is used.
"""
from pathlib import Path
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "notification-refresh"
work.mkdir(parents=True, exist_ok=True)
stubs = {
    "android/content/Context.java": """
package android.content;
import android.media.session.MediaSessionManager;
public class Context {
    public static final String MEDIA_SESSION_SERVICE = "media_session";
    public final MediaSessionManager sessions = new MediaSessionManager();
    public Object getSystemService(String name) { return MEDIA_SESSION_SERVICE.equals(name) ? sessions : null; }
    public ContentResolver getContentResolver() { return null; }
    public String getPackageName() { return "kr.ivlis.ivlyricsandroid"; }
    public Context getApplicationContext() { return this; }
}
""",
    "android/content/ComponentName.java": """
package android.content;
public final class ComponentName {
    public final String className;
    public ComponentName(Context context, Class<?> type) { className = type.getName(); }
    public static ComponentName unflattenFromString(String value) { return null; }
}
""",
    "android/media/session/MediaSessionManager.java": """
package android.media.session;
import android.content.ComponentName;
import java.util.Collections;
import java.util.List;
public final class MediaSessionManager {
    public int lookups;
    public List<MediaController> getActiveSessions(ComponentName listener) { lookups++; return Collections.emptyList(); }
}
""",
    "android/service/notification/StatusBarNotification.java": """
package android.service.notification;
public class StatusBarNotification {
    private final String packageName;
    public StatusBarNotification(String packageName) { this.packageName = packageName; }
    public String getPackageName() { return packageName; }
}
""",
    "android/service/notification/NotificationListenerService.java": """
package android.service.notification;
import android.content.Context;
public abstract class NotificationListenerService extends Context {
    public void onCreate() { }
    public void onDestroy() { }
    public void onListenerConnected() { }
    public void onNotificationPosted(StatusBarNotification sbn) { }
    public void onNotificationRemoved(StatusBarNotification sbn) { }
}
""",
}
files = []
for name, content in stubs.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    files.append(path)
production = SHARED / "NowPlayingService.java"
files += [production, ROOT / "tests/android/os/Handler.java", ROOT / "tests/android/os/Looper.java",
          ROOT / "tests/NotificationRefreshRegression.java"]
test_classpath = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-cp", test_classpath, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", test_classpath, "kr.ivlis.ivlyricsandroid.NotificationRefreshRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=60)
report = ("Production notification listener refresh; stub listener base and counting session manager.\n"
          f"{production.name} SHA256 {hashlib.sha256(production.read_bytes()).hexdigest()}\n" + result.stdout)
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)

