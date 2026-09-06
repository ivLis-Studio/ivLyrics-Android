package dev.ivlyrics.spotify;

import android.util.Log;
import android.view.View;
import android.view.ViewPropertyAnimator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Hides the Now Playing audio/video switch without changing playback or video availability. */
final class SpotifyVideoButton {
    private SpotifyVideoButton() {}

    static List<XC_MethodHook.Unhook> install(ClassLoader loader) {
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            Class<?> renderer = Class.forName("p.dh", false, loader);
            Class<?> button = Class.forName("p.ay6", false, loader);
            Class<?> updateContext = Class.forName("p.o9t", false, loader);
            Field branch = renderer.getField("a");
            Field view = renderer.getField("c");
            Field animation = button.getField("e");
            Field pending = button.getField("f");
            Method update = renderer.getDeclaredMethod("a", Object.class, updateContext);
            Method getView = renderer.getDeclaredMethod("getView");
            if (branch.getType() != int.class || !View.class.isAssignableFrom(button)) {
                throw new NoSuchFieldException("Invalid audio/video button mapping");
            }
            hooks.add(XposedBridge.hookMethod(update, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    if (param.hasThrowable() || branch.getInt(param.thisObject) != 5) return;
                    Object target = view.get(param.thisObject);
                    if (!button.isInstance(target)) return;
                    View buttonView = (View) target;
                    buttonView.setVisibility(View.GONE);
                    // Preserve native playback events emitted by binding; only suppress
                    // the hidden control's delayed label expansion and animation.
                    Object callback = pending.get(target);
                    if (callback instanceof Runnable) buttonView.removeCallbacks((Runnable) callback);
                    pending.set(target, null);
                    Object animator = animation.get(target);
                    if (animator instanceof ViewPropertyAnimator) {
                        ((ViewPropertyAnimator) animator).withEndAction(null).cancel();
                    }
                    animation.set(target, null);
                }
            }));
            hooks.add(XposedBridge.hookMethod(getView, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    if (param.hasThrowable() || branch.getInt(param.thisObject) != 5) return;
                    Object target = param.getResult();
                    if (button.isInstance(target)) ((View) target).setVisibility(View.GONE);
                }
            }));
            Log.i("ivLyricsPatch", "Now Playing video switch hidden");
            return hooks;
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            Log.e("ivLyricsPatch", "Video switch mapping unavailable: " + error.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }
}
