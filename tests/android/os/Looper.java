package android.os;
/** Deterministic test-only Looper; never part of the module source set. */
public final class Looper {
 private static final Looper MAIN = new Looper();
 private static boolean main = true;
 public static Looper getMainLooper() { return MAIN; }
 public static Looper myLooper() { return main ? MAIN : null; }
 public static void testMainThread(boolean value) { main = value; }
}
