#!/usr/bin/env python3
"""Run production Spotify quality/provider hooks with counted synthetic host bindings."""
import hashlib
import subprocess
from regression_runtime import ROOT, REPORTS, java_tool

work = REPORTS / "module-hook-cache"
classes = work / "classes"
classes.mkdir(parents=True, exist_ok=True)
module = ROOT / "spotify-module/src/main/java/dev/ivlyrics/spotify"
sources = {
"android/os/Looper.java": '''package android.os;
public final class Looper { static final Looper MAIN=new Looper(); static boolean main=true;
 public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return main?MAIN:null;} }''',
"android/os/Handler.java": '''package android.os;
import java.util.ArrayDeque;
public final class Handler { static final ArrayDeque<Runnable> TASKS=new ArrayDeque<>();
 public Handler(Looper ignored){} public boolean post(Runnable r){TASKS.add(r);return true;}
 public boolean postDelayed(Runnable r,long delay){return true;} public void removeCallbacks(Runnable r){TASKS.remove(r);}
 public static void drain(){int count=0;while(!TASKS.isEmpty()){if(++count>1000)throw new AssertionError("runaway tasks");TASKS.remove().run();}} }''',
"android/os/SystemClock.java": "package android.os; public final class SystemClock { public static long elapsedRealtime(){return 1000;} }",
"android/util/Log.java": "package android.util; public final class Log { public static int w(String t,String m){return 0;} }",
"android/media/session/MediaSession.java": '''package android.media.session;
public final class MediaSession { final MediaController controller; public MediaSession(MediaController c){controller=c;}
 public MediaController getController(){return controller;} }''',
"android/media/session/MediaController.java": '''package android.media.session;
import android.os.Handler;
public final class MediaController { public Callback callback; public PlaybackInfo info=new PlaybackInfo(1);
 public void registerCallback(Callback c,Handler h){callback=c;} public void unregisterCallback(Callback c){if(callback==c)callback=null;}
 public PlaybackInfo getPlaybackInfo(){return info;} public void route(int type){info=new PlaybackInfo(type);if(callback!=null)callback.onAudioInfoChanged(info);}
 public static class Callback { public void onAudioInfoChanged(PlaybackInfo i){} public void onSessionDestroyed(){} }
 public static final class PlaybackInfo { public static final int PLAYBACK_TYPE_LOCAL=1,PLAYBACK_TYPE_REMOTE=2;
  final int type; public PlaybackInfo(int t){type=t;} public int getPlaybackType(){return type;} } }''',
"de/robv/android/xposed/XC_MethodHook.java": '''package de.robv.android.xposed;
public class XC_MethodHook { protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
 public static final class Unhook { public void unhook(){} }
 public static final class MethodHookParam { public Object thisObject; public boolean hasThrowable(){return false;} public Object getResult(){return null;} } }''',
"de/robv/android/xposed/XposedBridge.java": '''package de.robv.android.xposed;
import java.lang.reflect.Method; import java.util.Set;
public final class XposedBridge { public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> c,XC_MethodHook h){return Set.of();}
 public static XC_MethodHook.Unhook hookMethod(Method m,XC_MethodHook h){return new XC_MethodHook.Unhook();} }''',
"p/xky0.java": "package p; public final class xky0 { public final boolean a; public xky0(boolean b){a=b;} }",
"p/pgk0.java": '''package p;
public final class pgk0 { public int presentReads,getReads; public boolean fail; public final Object value;
 public pgk0(Object v){value=v;} public boolean c(){presentReads++;if(fail)throw new IllegalStateException();return value!=null;}
 public Object b(){getReads++;return value;} }''',
"com/spotify/player/model/PlaybackQuality.java": '''package com.spotify.player.model;
public final class PlaybackQuality { public enum Level {LOW,HIGH,UNKNOWN} public int reads; public final Level level;
 public PlaybackQuality(Level l){level=l;} public Level bitrateLevel(){reads++;return level;} }''',
"com/spotify/player/model/AutoValue_PlayerState.java": '''package com.spotify.player.model;
public final class AutoValue_PlayerState { private final long timestamp; private final p.pgk0 playbackQuality;
 public AutoValue_PlayerState(long t,p.pgk0 q){timestamp=t;playbackQuality=q;} public p.pgk0 playbackQuality(){return playbackQuality;} }''',
"com/spotify/player/model/ContextTrack.java": '''package com.spotify.player.model;
public final class ContextTrack { private final String uri; public ContextTrack(String u){uri=u;} public String uri(){return uri;} }''',
"p/yca0.java": "package p; public final class yca0 { public final fzi0 a; public final yh c; public yca0(fzi0 pool,yh mapper){a=pool;c=mapper;} }",
"p/yh.java": "package p; public final class yh { public final int a; public Object c; public yh(int branch,Object repo){a=branch;c=repo;} }",
"p/eda0.java": "package p; public final class eda0 { public final Object d; public eda0(Object service){d=service;} }",
"p/gea0.java": "package p; public final class gea0 {}",
"p/vja0.java": "package p; public final class vja0 {}",
"p/fzi0.java": '''package p; public final class fzi0 { public int reads; public boolean ready=true; public final o131 state=new o131();
 public o131 c(){reads++;if(!ready)throw new IllegalStateException();return state;} }''',
"p/o131.java": '''package p; public final class o131 { public int reads; public final io.reactivex.rxjava3.core.Flowable source=new io.reactivex.rxjava3.core.Flowable();
 public io.reactivex.rxjava3.core.Flowable e(){reads++;return source;} }''',
"io/reactivex/rxjava3/core/Flowable.java": "package io.reactivex.rxjava3.core; public final class Flowable {}",
"dev/ivlyrics/spotify/ModuleHookCacheRegression.java": r'''package dev.ivlyrics.spotify;
import android.os.Handler;
import android.media.session.*;
import com.spotify.player.model.*;
import java.lang.reflect.*;
public final class ModuleHookCacheRegression {
 static int checks; static boolean allowed,known; static String reason="";
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static Object get(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 static Object member(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static void set(Class<?> type,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 static p.pgk0 optional(PlaybackQuality.Level level){return new p.pgk0(new PlaybackQuality(level));}
 public static void main(String[] args)throws Exception{
  ClassLoader loader=ModuleHookCacheRegression.class.getClassLoader();
  SpotifyKaraokePlayback.install(loader,false);
  Class<?> bindingType=Class.forName("dev.ivlyrics.spotify.SpotifyKaraokePlayback$Binding");
  Constructor<?> ctor=bindingType.getDeclaredConstructor(ClassLoader.class);ctor.setAccessible(true);Object binding=ctor.newInstance(loader);
  Method quality=bindingType.getDeclaredMethod("readQuality",Object.class,Object.class);quality.setAccessible(true);
  Method settings=bindingType.getDeclaredMethod("readSettings",Object.class);settings.setAccessible(true);
  MediaController controller=new MediaController();SpotifyKaraokePlayback.onMediaSession(new MediaSession(controller));
  SpotifyKaraokePlayback.setListener((k,a,r)->{known=k;allowed=a;reason=r;});Handler.drain();
  settings.invoke(binding,new p.xky0(false));
  p.pgk0 high=optional(PlaybackQuality.Level.HIGH);
  quality.invoke(binding,new AutoValue_PlayerState(10,high),null);Handler.drain();
  check(known&&allowed&&reason.isEmpty(),"native local online high-quality playback allowed");
  for(int i=0;i<500;i++) quality.invoke(binding,new AutoValue_PlayerState(10+i,high),high);
  Handler.drain();
  check(high.presentReads==1&&high.getReads==1&&((PlaybackQuality)high.value).reads==1,"500 repeated immutable optionals decode once");
  p.pgk0 stale=optional(PlaybackQuality.Level.LOW);
  quality.invoke(binding,new AutoValue_PlayerState(508,stale),null);Handler.drain();
  check(stale.presentReads==0&&allowed,"older snapshot rejected before optional reflection");
  p.pgk0 low=optional(PlaybackQuality.Level.LOW);
  quality.invoke(binding,new AutoValue_PlayerState(509,low),null);Handler.drain();
  check(known&&!allowed&&reason.equals("LOW_QUALITY"),"different state at equal timestamp must update quality");
  quality.invoke(binding,new AutoValue_PlayerState(509,high),null);Handler.drain();
  check(allowed,"equal timestamp can transition back using a different optional");
  settings.invoke(binding,new p.xky0(true));Handler.drain();check(!allowed&&reason.equals("OFFLINE"),"offline gate preserved");
  settings.invoke(binding,new p.xky0(false));controller.route(2);Handler.drain();check(!allowed&&reason.equals("REMOTE"),"Connect gate preserved");
  controller.route(1);Handler.drain();check(allowed,"local route resumes eligibility");
  p.pgk0 empty=new p.pgk0(null);quality.invoke(binding,new AutoValue_PlayerState(510,empty),null);Handler.drain();
  check(!known&&!allowed&&reason.equals("WAITING"),"missing quality remains closed");
  p.pgk0 bad=optional(PlaybackQuality.Level.HIGH);bad.fail=true;
  quality.invoke(binding,new AutoValue_PlayerState(511,bad),null);Handler.drain();check(!allowed,"unreadable quality remains closed");
  bad.fail=false;quality.invoke(binding,new AutoValue_PlayerState(511,bad),null);Handler.drain();check(allowed,"failed binding read can retry same optional");
  SpotifyKaraokePlayback.setListener(null);Handler.drain();check(controller.callback==null,"page close releases controller callback");

  p.fzi0 pool=new p.fzi0();p.eda0 repo=new p.eda0(new p.gea0());p.yh mapper=new p.yh(26,repo);p.yca0 provider=new p.yca0(pool,mapper);
  String uri="spotify:track:0000000000000000000001";
  ContextTrack track=new ContextTrack(uri);
  SpotifyKaraokeEligibility.onCardProvider(provider,track);
  Object dependencies=get(SpotifyKaraokeEligibility.class,"providerDependencies");
  Object bindings=get(SpotifyKaraokeEligibility.class,"providerBinding");
  for(int i=0;i<500;i++)SpotifyKaraokeEligibility.onCardProvider(provider,track);
  check(pool.reads==1&&pool.state.reads==1,"500 repeated provider events reuse native player source");
  check(get(SpotifyKaraokeEligibility.class,"providerDependencies")==dependencies,"reuse validated provider dependencies");
  check(get(SpotifyKaraokeEligibility.class,"providerBinding")==bindings,"reuse reflection bindings");
  check(SpotifyKaraokeEligibility.playerStateSource()==pool.state.source,"cached source remains native source");
  check(get(SpotifyKaraokeEligibility.class,"repository")==repo,"expected repository captured");
  p.eda0 replacement=new p.eda0(new p.vja0());mapper.c=replacement;
  SpotifyKaraokeEligibility.onCardProvider(provider,track);
  check(get(SpotifyKaraokeEligibility.class,"repository")==replacement,"mutable merged mapper repository revalidated");
  check(get(SpotifyKaraokeEligibility.class,"providerDependencies")!=dependencies,"repository replacement invalidates dependencies");
  p.fzi0 nextPool=new p.fzi0();p.eda0 nextRepo=new p.eda0(new p.gea0());
  p.yca0 next=new p.yca0(nextPool,new p.yh(26,nextRepo));
  SpotifyKaraokeEligibility.onCardProvider(next,null);
  check(SpotifyKaraokeEligibility.playerStateSource()==nextPool.state.source,"replacement provider recaptures source without a native card");
  check(get(SpotifyKaraokeEligibility.class,"repository")==nextRepo,"constructor capture has no Spotify lyrics availability gate");
  SpotifyKaraokeEligibility.onCardProvider(new p.yca0(new p.fzi0(),new p.yh(1,repo)),track);
  check(get(SpotifyKaraokeEligibility.class,"repository")==nextRepo,"other merged branch rejected");
  SpotifyKaraokeEligibility.onCardProvider(provider,new ContextTrack("spotify:episode:0000000000000000000001"));
  check(get(SpotifyKaraokeEligibility.class,"repository")==nextRepo,"non-song context does not replace current dependencies");
  p.fzi0 delayedPool=new p.fzi0();delayedPool.ready=false;p.yca0 delayed=new p.yca0(delayedPool,new p.yh(26,repo));
  SpotifyKaraokeEligibility.onCardProvider(delayed,track);
  check(get(SpotifyKaraokeEligibility.class,"repository")==repo&&SpotifyKaraokeEligibility.playerStateSource()==null,"support survives unavailable optional player source");
  delayedPool.ready=true;SpotifyKaraokeEligibility.onCardProvider(delayed,track);
  check(SpotifyKaraokeEligibility.playerStateSource()==delayedPool.state.source,"missing player source retried on next event");
  SpotifyKaraokeEligibility.stop();check(get(SpotifyKaraokeEligibility.class,"listener")==null,"page stop releases eligibility listener");
  System.out.println("MODULE_HOOK_CACHE_PASSED checks="+checks+" repeated_quality=500 decode_calls=3 repeated_provider=500 source_reads=1");
 }
}''',
}
files = []
for name, content in sources.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    files.append(path)
production = [module / name for name in ("SpotifyKaraokePlayback.java", "SpotifyKaraokeEligibility.java")]
subprocess.run([java_tool("javac"), "-d", str(classes), *map(str, production + files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(classes), "dev.ivlyrics.spotify.ModuleHookCacheRegression"],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
report="Production Spotify hook code; counted synthetic bindings, no device timings.\n"
report += "".join(f"{p.relative_to(ROOT)} SHA256 {hashlib.sha256(p.read_bytes()).hexdigest()}\n" for p in production)
report += result.stdout
(work / "result.txt").write_text(report)
print(report,end="")
raise SystemExit(result.returncode)
