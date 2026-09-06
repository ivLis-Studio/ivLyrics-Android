package android.os;
import java.util.ArrayDeque;
/** Deterministic test-only queue; never part of the module source set. */
public final class Handler {
 private static final ArrayDeque<Runnable> QUEUE = new ArrayDeque<>();
 public Handler(Looper looper) {}
 public boolean post(Runnable task) { QUEUE.add(task); return true; }
 public boolean postDelayed(Runnable task, long delay) { return post(task); }
 public void removeCallbacks(Runnable task) { QUEUE.removeIf(queued -> queued == task); }
 public static void drain() { Looper.testMainThread(true); while (!QUEUE.isEmpty()) QUEUE.remove().run(); }
}
