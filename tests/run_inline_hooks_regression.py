#!/usr/bin/env python3
"""Exercise production Xposed inline hooks against synthetic Spotify 9.1.80 host shapes."""
import subprocess
from regression_runtime import ROOT, REPORTS, java_tool

work = REPORTS / "inline-hooks"
work.mkdir(parents=True, exist_ok=True)
files = {
"android/content/Context.java": 'package android.content; public class Context {}',
"android/os/Looper.java": 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
"android/os/Handler.java": 'package android.os; public class Handler { public Handler(Looper l){} public void post(Runnable r){r.run();} }',
"android/util/Log.java": 'package android.util; public class Log { public static int d(String t,String s){return 0;} public static int i(String t,String s){return 0;} public static int e(String t,String s){return 0;} public static int e(String t,String s,Throwable e){return 0;} }',
"android/view/View.java": '''package android.view;
public class View { private boolean attached; private final android.content.Context context;
 public View(android.content.Context c){context=c;} public android.content.Context getContext(){return context;}
 public void setTag(Object t){} public void setLayoutParams(ViewGroup.LayoutParams p){} public boolean isAttachedToWindow(){return attached;}
 protected void onAttachedToWindow(){} protected void onDetachedFromWindow(){} protected void onSizeChanged(int w,int h,int ow,int oh){}
 public void attach(){attached=true;onAttachedToWindow();} public void detach(){onDetachedFromWindow();attached=false;}
}''',
"android/view/ViewGroup.java": '''package android.view; import java.util.*;
public class ViewGroup extends View { public final List<View> children=new ArrayList<>();
 public ViewGroup(android.content.Context c){super(c);} public static class LayoutParams {public static final int MATCH_PARENT=-1,WRAP_CONTENT=-2; public LayoutParams(int w,int h){}}
 public void addView(View v,LayoutParams p){children.add(v);} public void removeView(View v){children.remove(v);} public void removeAllViews(){children.clear();}
}''',
"android/widget/FrameLayout.java": 'package android.widget; public class FrameLayout extends android.view.ViewGroup { public FrameLayout(android.content.Context c){super(c);} }',
"kr/ivlis/ivlyricsandroid/IvLyricsBridge.java": 'package kr.ivlis.ivlyricsandroid; public class IvLyricsBridge { public static android.view.View createInlinePreview(android.content.Context c){return new android.view.View(c);} }',
"dev/ivlyrics/spotify/ComposeAdapter.java": '''package dev.ivlyrics.spotify; public class ComposeAdapter {
 static int renders; static void prepareInline(Object c) throws ReflectiveOperationException {} public static void renderInline(Object c) throws ReflectiveOperationException {renders++;}
}''',
"de/robv/android/xposed/XC_MethodHook.java": '''package de.robv.android.xposed;
public class XC_MethodHook {
 public class Unhook { public void unhook(){} }
 public static class MethodHookParam { public Object thisObject; public Object[] args; Object result; Throwable error; boolean early;
  public Object getResult(){return result;} public void setResult(Object r){result=r;error=null;early=true;} public boolean hasThrowable(){return error!=null;} public void setThrowable(Throwable e){error=e;early=true;}
 }
 protected void beforeHookedMethod(MethodHookParam p) throws Throwable {} protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
}''',
"de/robv/android/xposed/XposedBridge.java": '''package de.robv.android.xposed;
import java.lang.reflect.*; import java.util.*;
public class XposedBridge {
 private static final Map<Method,XC_MethodHook> hooks=new HashMap<>();
 public static XC_MethodHook.Unhook hookMethod(Member m,XC_MethodHook h){hooks.put((Method)m,h);return h.new Unhook();}
 public interface Body {Object run() throws Throwable;}
 public static Object call(Method m,Object owner,Object[] args,Body body) throws Throwable {
  XC_MethodHook h=hooks.get(m); XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam(); p.thisObject=owner;p.args=args;
  if(h!=null)h.beforeHookedMethod(p);
  if(!p.early){try{p.result=body.run();}catch(Throwable t){p.error=t;}}
  if(h!=null)h.afterHookedMethod(p); if(p.error!=null)throw p.error; return p.result;
 }
}''',
"com/spotify/player/model/ContextTrack.java": '''package com.spotify.player.model; import java.util.*;
public class ContextTrack {private final String uri; private final Map<String,String> metadata;
 public ContextTrack(String u,Map<String,String> m){uri=u;metadata=m;} public String uri(){return uri;} public Map<String,String> metadata(){return metadata;}
}''',
"p/jm7.java": 'package p; public class jm7 {public int a;public boolean b,c,d;public Object invokeSuspend(Object x){return false;}}',
"p/cp5.java": 'package p; public class cp5 {public int a;public boolean b,c,d,e;public Object invokeSuspend(Object x){return false;}}',
"p/nba0.java": 'package p; public class nba0 {public int a;public Object c1(Object a,Object b,Object c,Object d,Object e){return null;}}',
"p/lba0.java": 'package p; public class lba0 {}',
"p/ryz.java": 'package p; public class ryz {}',
"p/x181.java": 'package p; public class x181 {public static final Object a=new Object();}',
"p/b9p0.java": 'package p; public class b9p0 {public Object apply(Object x){return null;}}',
"p/d2w0.java": 'package p; public class d2w0 {public static boolean e(com.spotify.player.model.ContextTrack t){return false;}}',
"dev/ivlyrics/spotify/InlineHooksRegression.java": r'''package dev.ivlyrics.spotify;
import java.lang.reflect.*; import java.util.*; import de.robv.android.xposed.*; import com.spotify.player.model.ContextTrack;
public class InlineHooksRegression {
 static int checks;
 static void check(boolean actual,String label){checks++;if(!actual)throw new AssertionError(label);}
 static Method method(Class<?> type,String name,Class<?>... args)throws Exception{return type.getDeclaredMethod(name,args);}
 static Object call(Method m,Object owner,XposedBridge.Body body)throws Throwable{return XposedBridge.call(m,owner,new Object[]{null},body);}
 static Method availability,visibility,menu,trackGate;
 static Object allow(ContextTrack t)throws Throwable{return XposedBridge.call(trackGate,null,new Object[]{t},()->Boolean.parseBoolean(t.metadata().get("has_lyrics")));}
 static Object menuFor(ContextTrack t)throws Throwable{return call(menu,new p.b9p0(),()->allow(t));}
 public static void main(String[] args)throws Throwable {
  check(SpotifyInlineLyrics.install(InlineHooksRegression.class.getClassLoader()).size()==5,"all hook mappings installed");
  availability=method(p.jm7.class,"invokeSuspend",Object.class);visibility=method(p.cp5.class,"invokeSuspend",Object.class);
  menu=method(p.b9p0.class,"apply",Object.class);trackGate=method(p.d2w0.class,"e",ContextTrack.class);
  p.jm7 nativePlayback=new p.jm7();nativePlayback.a=1; p.cp5 nativeLayout=new p.cp5();nativeLayout.a=2;
  for(int bits=0;bits<128;bits++) {
   nativePlayback.b=(bits&1)!=0;nativePlayback.d=(bits&2)!=0;nativePlayback.c=(bits&4)!=0;
   boolean nativeLyrics=(bits&8)!=0, expected=(!nativePlayback.b||nativePlayback.d)&&!nativePlayback.c;
   check(call(availability,nativePlayback,()->nativeLyrics&&(!nativePlayback.b||nativePlayback.d)&&!nativePlayback.c).equals(expected),"availability input "+bits);
   nativeLayout.b=(bits&16)!=0;nativeLayout.c=(bits&32)!=0;nativeLayout.e=(bits&64)!=0;nativeLayout.d=expected;
   check(call(visibility,nativeLayout,()->!nativeLayout.b&&nativeLayout.c&&nativeLayout.d&&!nativeLayout.e).equals(!nativeLayout.b&&nativeLayout.c&&expected),"DJ/layout input "+bits);
  }
  ContextTrack noLyrics=new ContextTrack("spotify:track:0123456789012345678901",Map.of());
  ContextTrack yesLyrics=new ContextTrack("spotify:track:1123456789012345678901",Map.of("has_lyrics","true"));
  check(allow(noLyrics).equals(false),"native lyrics consumers unchanged outside menu");
  check(menuFor(noLyrics).equals(true),"menu second gate accepts ivLyrics-only song");
  check(menuFor(yesLyrics).equals(true),"existing native song option retained");
  check(menuFor(new ContextTrack("spotify:episode:0123456789012345678901",Map.of("has_lyrics","true"))).equals(false),"episodes excluded");
  check(menuFor(new ContextTrack(noLyrics.uri(),Map.of("parent_episode_uri","spotify:episode:fixture"))).equals(false),"podcast embedded track excluded");
  check(call(menu,new p.b9p0(),()->{check(menuFor(noLyrics).equals(true),"nested menu inner");return allow(noLyrics);}).equals(true),"nested menu retains outer scope");
  check(allow(noLyrics).equals(false),"menu scope cleared");
  try{call(menu,new p.b9p0(),()->{throw new IllegalStateException("fixture");});throw new AssertionError("error swallowed");}catch(IllegalStateException expected){}
  check(allow(noLyrics).equals(false),"exception clears menu scope");
  nativePlayback.a=0; check(call(availability,nativePlayback,()->false).equals(false),"other merged availability branch untouched");nativePlayback.a=1;
  nativeLayout.a=1;check(call(visibility,nativeLayout,()->false).equals(false),"other merged layout branch untouched");nativeLayout.a=2;
  nativePlayback.b=false;nativePlayback.c=false;call(availability,nativePlayback,()->false);
  nativeLayout.b=false;nativeLayout.c=true;nativeLayout.d=true;nativeLayout.e=true;call(visibility,nativeLayout,()->false);
  android.view.ViewGroup host=(android.view.ViewGroup)SpotifyInlineLyrics.createView(new android.content.Context());host.attach();
  check(host.children.size()==1,"DJ song mounts inline even native lyrics absent");
  nativeLayout.c=false;call(visibility,nativeLayout,()->false);check(host.children.isEmpty(),"saved user off hides inline");
  nativeLayout.c=true;call(visibility,nativeLayout,()->false);check(host.children.size()==1,"user on remounts inline");
  nativePlayback.c=true;call(availability,nativePlayback,()->false);check(host.children.isEmpty(),"video still hides inline");
  nativePlayback.c=false;call(availability,nativePlayback,()->false);check(host.children.size()==1,"return to audio remounts inline");
  host.detach();check(host.children.isEmpty(),"detached host releases preview");host.attach();check(host.children.size()==1,"host attachment restores preview");
  p.nba0 renderer=new p.nba0();
  Method render=method(p.nba0.class,"c1",Object.class,Object.class,Object.class,Object.class,Object.class);
  Object unit=XposedBridge.call(render,renderer,new Object[]{null,new p.lba0(),null,new p.ryz(),0},()->{throw new AssertionError("native renderer executed");});
  check(unit==p.x181.a&&ComposeAdapter.renders==1,"empty native model uses ivLyrics composition");
  System.out.println("INLINE_HOOKS_PASSED assertions="+checks);
 }
}'''
}
for name, content in files.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
source = ROOT / "spotify-module/src/main/java/dev/ivlyrics/spotify"
subprocess.run([java_tool("javac"), "-d", str(work), *(str(work / name) for name in files),
                str(source / "SpotifyInlineLyrics.java"), str(source / "SpotifyInlineLyricsMenu.java")], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "dev.ivlyrics.spotify.InlineHooksRegression"],
                        capture_output=True, text=True)
report = "Production hooks with synthetic native/Android/Xposed fixtures; no device or account access.\n" + result.stdout + result.stderr
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
