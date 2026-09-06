#!/usr/bin/env python3
"""Exercise production module metadata code with deterministic host/main-loop stubs."""
import hashlib
import subprocess
from regression_runtime import ROOT, MODULE, REPORTS, java_tool

work = REPORTS / "module-metadata-fanout"
classes = work / "classes"
classes.mkdir(parents=True, exist_ok=True)
sources = {
    "android/os/Looper.java": '''package android.os;
public final class Looper { private static final Looper MAIN = new Looper();
 public static Looper getMainLooper() { return MAIN; } }''',
    "android/os/Handler.java": '''package android.os;
import java.util.ArrayDeque;
public final class Handler {
 private static final ArrayDeque<Runnable> TASKS = new ArrayDeque<>();
 public Handler(Looper ignored) {}
 public boolean post(Runnable action) { TASKS.add(action); return true; }
 public static int pending() { return TASKS.size(); }
 public static void drain() { int count = 0; while (!TASKS.isEmpty()) {
  if (++count > 1000) throw new AssertionError("runaway main delivery"); TASKS.remove().run(); } }
}''',
    "android/util/Log.java": '''package android.util;
public final class Log { public static int i(String tag, String message) { return 0; }
 public static int w(String tag, String message) { return 0; } }''',
    "android/media/MediaMetadata.java": "package android.media; public final class MediaMetadata {}",
    "android/media/session/MediaController.java": '''package android.media.session;
import android.media.MediaMetadata; import android.os.Handler;
public final class MediaController { public void registerCallback(Callback callback, Handler main) {}
 public static class Callback { public void onMetadataChanged(MediaMetadata metadata) {}
 public void onSessionDestroyed() {} } }''',
    "android/media/session/MediaSession.java": '''package android.media.session;
public final class MediaSession { public MediaController getController() { return new MediaController(); } }''',
    "kr/ivlis/ivlyricsandroid/TrackSnapshot.java": '''package kr.ivlis.ivlyricsandroid;
final class TrackSnapshot { final String mediaId, isrc; final long position;
 TrackSnapshot(String mediaId, String isrc, long position) { this.mediaId=mediaId; this.isrc=isrc; this.position=position; } }''',
    "kr/ivlis/ivlyricsandroid/NowPlayingService.java": '''package kr.ivlis.ivlyricsandroid;
import java.util.ArrayList; import java.util.List;
final class NowPlayingService {
 interface Listener { void changed(TrackSnapshot snapshot); }
 static final List<Listener> listeners = new ArrayList<>(); static TrackSnapshot current; static int enrichments;
 static void register(Listener listener) { listeners.add(listener); }
 static TrackSnapshot getLatestSnapshot() { return current; }
 static void publish(TrackSnapshot next) { current=next; for (Listener listener : listeners) listener.changed(next); }
 static void enrichEmbeddedIsrc(String expected, String isrc) {
  if (current != null && expected.equals(current.mediaId) && !isrc.isEmpty() && !isrc.equals(current.isrc)) {
   current = new TrackSnapshot(current.mediaId, isrc, current.position); enrichments++;
   for (Listener listener : listeners) listener.changed(current);
  }
 }
}''',
    "kr/ivlis/ivlyricsandroid/SpotifyNativeMetadataClient.java": '''package kr.ivlis.ivlyricsandroid;
final class SpotifyNativeMetadataClient { static int requests; static String latest;
 static void attach(Object client) {} static void request(String uri, boolean resolved) { requests++; latest=uri; } }''',
    "kr/ivlis/ivlyricsandroid/ModuleMetadataFanoutRegression.java": r'''package kr.ivlis.ivlyricsandroid;
import android.os.Handler;
import java.lang.reflect.*;
import java.util.*;
public final class ModuleMetadataFanoutRegression {
 static int checks;
 static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
 static Object field(Object owner, String name) throws Exception {
  Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
 }
 static Map<?, ?> staticMap(Class<?> type, String name) throws Exception {
  Field f=type.getDeclaredField(name); f.setAccessible(true); return (Map<?, ?>) f.get(null);
 }
 static byte[] gid(int value) { byte[] bytes=new byte[16]; bytes[14]=(byte)(value >>> 8); bytes[15]=(byte)value; return bytes; }
 static String uri(int value) { return "spotify:track:" + NativeTrackIdentity.base62(gid(value)); }
 static String isrc(int value) { return String.format(Locale.ROOT,"JPABC%07d",value); }
 static Proto track(int value) { return new Proto(gid(value), List.of(new Ext("ISRC",isrc(value)))); }
 public static void main(String[] args) throws Exception {
  Proto first=track(1);
  NativeTrackIdentity a=NativeTrackIdentity.fromProto(first);
  check(a.uri.equals(uri(1)) && a.isrc.equals(isrc(1)),"direct identity");
  Map<?, ?> bindings=staticMap(NativeTrackIdentity.class,"MEMBERS");
  Object protoMembers=bindings.get(Proto.class), byteMembers=bindings.get(Bytes.class);
  Object cachedField=((Map<?, ?>)field(protoMembers,"fields")).get("gid_");
  Object cachedMethod=field(byteMembers,"bytesMethod");
  for(int i=0;i<200;i++) {
   NativeTrackIdentity parsed=NativeTrackIdentity.fromProto(track(i+1));
   check(parsed.uri.equals(uri(i+1)),"fresh metadata is never replaced by cached object");
  }
  check(bindings.get(Proto.class)==protoMembers,"reuse per-class field bindings");
  check(((Map<?, ?>)field(protoMembers,"fields")).get("gid_")==cachedField,"reuse reflected Field");
  check(field(byteMembers,"bytesMethod")==cachedMethod,"reuse GID method discovery");
  check(NativeTrackIdentity.fromProto(new Proto(new byte[15],List.of(new Ext("isrc",isrc(1)))))==null,"malformed GID");
  check(NativeTrackIdentity.fromProto(new Proto(gid(1),List.of(new Ext("upc",isrc(1)))))==null,"non-ISRC identifier");
  check(NativeTrackIdentity.normalizedIsrc("jp-abc-0000001").equals(isrc(1)),"normalized ISRC");
  check(NativeTrackIdentity.canonicalUri("spotify:episode:"+uri(1).substring(14)).isEmpty(),"exclude episode");

  SpotifyMetadataBridge.onMetadataClient(new Object()); Handler.drain();
  List<Item> items=new ArrayList<>(); for(int i=1;i<=100;i++) items.add(new Item(4,track(i)));
  items.add(10,new Item(4,new Object())); // one invalid item must not suppress later valid entries
  items.add(new Item(2,new Object()));
  NowPlayingService.current=new TrackSnapshot(uri(1),"",10);
  SpotifyMetadataBridge.onMetadataBatch(new Batch(items));
  check(Handler.pending()==1,"100 valid batch entries schedule exactly one MAIN merge");
  NowPlayingService.current=new TrackSnapshot(uri(2),"",9000);
  Handler.drain();
  check(NowPlayingService.current.mediaId.equals(uri(2)) && NowPlayingService.current.isrc.equals(isrc(2)),"merge newest current track");
  check(NowPlayingService.current.position==9000,"preserve playback clock");
  int before=SpotifyNativeMetadataClient.requests;
  SpotifyMetadataBridge.onMetadataBatch(new Batch(items));
  check(Handler.pending()==0,"identical batch schedules no merge");
  check(SpotifyNativeMetadataClient.requests==before,"identical batch has no metadata-request side effects");
  for(int i=101;i<=150;i++) SpotifyMetadataBridge.onTrackMetadata(track(i));
  check(Handler.pending()==1,"separate parser burst coalesces"); Handler.drain();
  check(staticMap(SpotifyMetadataBridge.class,"ISRC").size()==150,"preserve unrelated-track cache entries");
  NowPlayingService.publish(new TrackSnapshot(uri(149),"",500)); Handler.drain();
  check(NowPlayingService.current.isrc.equals(isrc(149)),"future track resolves from cache without a new parser result");

  String alias=uri(500);
  NowPlayingService.current=new TrackSnapshot(alias,"",1200);
  check(SpotifyMetadataBridge.onRequestedTrackV4(alias,new V4(uri(1),List.of(new Id("isrc",isrc(1))))),"accept validated relinked envelope");
  check(Handler.pending()==1,"new alias queues merge even when canonical identity already cached"); Handler.drain();
  check(NowPlayingService.current.mediaId.equals(alias) && NowPlayingService.current.isrc.equals(isrc(1)),"relinked alias enrichment");
  SpotifyMetadataBridge.onRequestedTrackV4(alias,new V4(uri(1),List.of(new Id("isrc",isrc(1)))));
  check(Handler.pending()==0,"unchanged alias causes no publication");
  SpotifyMetadataBridge.onRequestedTrackV4(alias,new V4(uri(1),List.of(new Id("isrc",isrc(151))))); Handler.drain();
  check(NowPlayingService.current.isrc.equals(isrc(151)),"replacement metadata for same URI is not suppressed");
  for(int i=151;i<=450;i++) SpotifyMetadataBridge.onTrackMetadata(track(i)); Handler.drain();
  check(staticMap(SpotifyMetadataBridge.class,"ISRC").size()==256,"track cache remains bounded");
  for(int i=0;i<40;i++) {
   Object proxy=Proxy.newProxyInstance(new ClassLoader() {},new Class<?>[]{Runnable.class},(p,m,v)->null);
   try { NativeTrackIdentity.field(proxy,"missing"); } catch(NoSuchFieldException expected) {}
  }
  check(bindings.size()==32,"retained reflection classes remain bounded");
  check(NativeTrackIdentity.fromProto(first).uri.equals(uri(1)),"evicted bindings safely reload");
  System.out.println("MODULE_METADATA_FANOUT_PASSED checks="+checks+" batch=100 MAIN_posts=1 unchanged_posts=0");
 }
 public static final class Bytes { final byte[] value; Bytes(byte[] v){value=v;} public byte[] bytes(){return value;} }
 static final class Proto { final Bytes gid_; final List<Ext> externalId_; Proto(byte[] bytes,List<Ext> ids){gid_=new Bytes(bytes);externalId_=ids;} }
 static final class Ext { final String type_,id_; Ext(String type,String id){type_=type;id_=id;} }
 static final class Batch { final List<Item> items_; Batch(List<Item> items){items_=items;} }
 static final class Item { final int itemCase_; final Object item_; Item(int kind,Object value){itemCase_=kind;item_=value;} }
 static final class V4 { final String a; final List<Id> j; V4(String uri,List<Id> ids){a=uri;j=ids;} }
 static final class Id { final String a,b; Id(String type,String id){a=id;b=type;} }
}''',
}
files = []
for name, content in sources.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    files.append(path)
production = [MODULE / name for name in ("NativeTrackIdentity.java", "SpotifyMetadataBridge.java")]
subprocess.run([java_tool("javac"), "-d", str(classes), *map(str, production + files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(classes), "kr.ivlis.ivlyricsandroid.ModuleMetadataFanoutRegression"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = "Production module metadata code; deterministic synthetic host/main queue, no device timings.\n"
report += "".join(f"{p.relative_to(ROOT)} SHA256 {hashlib.sha256(p.read_bytes()).hexdigest()}\n" for p in production)
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
