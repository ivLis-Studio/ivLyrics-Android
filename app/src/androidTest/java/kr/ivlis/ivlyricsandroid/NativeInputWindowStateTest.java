package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import static org.junit.Assert.*;

/** Parser contracts, separate from FirstUseUiRegressionTest's actual native interaction. */
public final class NativeInputWindowStateTest {
    private static final int APP = 10209;
    private static final String TITLE = "kr.ivlis.ivlyricsandroid/FirstUseHarnessActivity";

    @Test public void portraitTaskbarAndDisjointTouchRegions() {
        NativeInputWindowState state = NativeInputWindowState.parse(dump(720, 1560, "(ROT_0) (IDENTITY)",
                window("abc Taskbar", 10179, "NOT_FOCUSABLE | TRUSTED_OVERLAY", "[0,1400][720,1450][0,1500][720,1560]")
                + window("def " + TITLE, APP, "0x0", "[0,0][720,1560]")), 0);
        assertTrue(state.targets(360, 1380, APP, TITLE));
        assertFalse(state.targets(360, 1420, APP, TITLE));
        assertTrue(state.targets(360, 1480, APP, TITLE));
        assertEquals(10179, state.targetAt(360, 1520).uid);
    }

    @Test public void rotatedScaledInputUsesDisplayTransformNotWindowTransform() {
        NativeInputWindowState state = NativeInputWindowState.parse(dump(1560, 720,
                "(ROT_270) (SCALE ROTATE TRANSLATE)\n            0 0.5 -20\n            -0.5 0 720\n            0 0 1",
                window("abc Taskbar", 10179, "NOT_FOCUSABLE", "[0,40][300,3160]")
                + "    transform (ROT_270) (SCALE ROTATE TRANSLATE)\n        0 0.5 -20\n        -0.5 0 150\n        0 0 1\n"
                + window("def " + TITLE, APP, "0x0", "[0,40][1440,3160]")), 0);
        assertTrue(state.targets(848, 550, APP, TITLE));
        assertFalse(state.targets(848, 584.5f, APP, TITLE));
        assertEquals(10179, state.targetAt(848, 584.5f).uid);
        assertTrue(state.targets(500, 100, APP, TITLE));
    }

    @Test public void skipsNonTargetWindowsButNotSameUidNonFocusableOverlay() {
        NativeInputWindowState state = NativeInputWindowState.parse(dump(720, 1560, "(ROT_0) (IDENTITY)",
                window("spy", 1000, "SPY | TRUSTED_OVERLAY", "[0,0][720,1560]")
                + window("hidden", 1000, "NOT_VISIBLE", "[0,0][720,1560]")
                + window("decor", 1000, "NOT_TOUCHABLE", "[0,0][720,1560]")
                + window("abc first-use-input-blocker", APP, "NOT_FOCUSABLE", "[100,100][500,500]")
                + window("def " + TITLE, APP, "0x0", "[0,0][720,1560]")), 0);
        assertFalse(state.targets(300, 300, APP, TITLE));
        assertTrue(state.targets(300, 700, APP, TITLE));
        assertEquals(APP, state.targetAt(300, 300).uid);
    }

    @Test public void failsClosedForUnknownSnapshotsAndDropInput() {
        assertThrows(AssertionError.class, () -> NativeInputWindowState.parse("", 0));
        assertThrows(AssertionError.class, () -> NativeInputWindowState.parse(
                dump(720, 1560, "unknown", window("def " + TITLE, APP, "0x0", "[0,0][720,1560]")), 0));
        NativeInputWindowState state = NativeInputWindowState.parse(dump(720, 1560, "(ROT_0) (IDENTITY)",
                window("def " + TITLE, APP, "DROP_INPUT", "[0,0][720,1560]")), 0);
        assertFalse(state.targets(300, 700, APP, TITLE));
        assertNull(state.targetAt(-1, 700));
    }

    private static String dump(int width, int height, String transform, String windows) {
        return "Input Dispatcher State:\n  Display: 0\n    logicalSize=" + width + "x" + height
                + "\n        transform " + transform + "\n    Windows:\n" + windows
                + "  Global monitors on display 0:\n    <none>\nInput Manager Service (Java) State:\n"
                + "  Gesture Monitors:\n    0: name='not an input window', inputChannelToken=ignored\n";
    }

    private static String window(String name, int uid, String config, String region) {
        return "      0: name=" + name + ", id=1, displayId=0, inputConfig=" + config
                + ", alpha=1, frame=[0,0][720,1560], globalScale=1, applicationInfo.name=, touchableRegion="
                + region + ", ownerPid=100, ownerUid=" + uid + ", dispatchingTimeout=5000ms\n";
    }
}
