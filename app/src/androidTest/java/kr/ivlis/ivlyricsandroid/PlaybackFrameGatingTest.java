package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

/** Paint both paths with real fonts/Canvas; a skipped frame must match a fresh draw exactly. */
public final class PlaybackFrameGatingTest {
    @Test public void skippedLyricFramesMatchFreshCanvasIncludingReverseSeeksAndLateTiming() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            LyricsView gated = new LyricsView(context);
            LyricsView reference = new LyricsView(context);
            size(gated); size(reference);
            int skipped = 0;
            for (boolean timed : new boolean[]{false, true}) {
                for (String granularity : new String[]{"line", "character", "word"}) {
                    LyricsResult result = result(timed);
                    for (LyricsView view : new LyricsView[]{gated, reference}) {
                        view.setKaraokeDisplayGranularity(granularity);
                        view.setResult(result);
                        view.setTrackDuration(24000L);
                    }
                    Bitmap last = paint(gated);
                    for (int n = 0; n < 500; n++) {
                        // Most samples stay on one line; crossings, backward seeks and jumps
                        // must invalidate instead of reusing an old painted frame.
                        double position = n < 340 ? 1100 + n * 4.25 : n < 420 ? 6200 + (n - 340) * 16.5 : 1100 + (n - 420) * 4.25;
                        boolean skip = reusable(gated, position);
                        gated.setPlaybackFrame(position, System.nanoTime());
                        reference.setPlaybackFrame(position, System.nanoTime());
                        if (!skip) { last.recycle(); last = paint(gated); }
                        Bitmap fresh = paint(reference);
                        if (skip) {
                            assertTrue("different skipped frame: " + timed + "/" + granularity + "/" + position,
                                    last.sameAs(fresh));
                            skipped++;
                        }
                        fresh.recycle();
                    }
                    last.recycle();
                }
            }
            assertTrue("must exercise the optimization", skipped > 100);
            android.util.Log.i("FrameGating", "lyric pixel-identical skipped frames=" + skipped);
        });
    }

    @Test public void previewSkipsStaticRowsButKeepsOverflowAndKaraoke() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            MainLyricPreviewView view = new MainLyricPreviewView(context);
            view.setExternallyDriven(true);
            size(view);
            List<MainLyricPreviewView.PreviewLine> rows = Collections.singletonList(
                    new MainLyricPreviewView.PreviewLine("Plain line", true));
            view.setPreview(rows, 1200, 1000, 4000, true);
            Bitmap first = paint(view);
            assertTrue(reusable(view, 1700));
            view.setPlaybackFrame(1700, System.nanoTime());
            Bitmap fresh = paint(view);
            assertTrue(first.sameAs(fresh));
            first.recycle(); fresh.recycle();
            view.setPreview(Collections.singletonList(new MainLyricPreviewView.PreviewLine(
                    "This long line must keep sliding across the entire viewport and never stop too soon", true)),
                    1700, 1000, 4000, true);
            paint(view).recycle();
            assertFalse(reusable(view, 1800));
            view.setPreview(Collections.singletonList(new MainLyricPreviewView.PreviewLine(
                    "Karaoke", true, Collections.singletonList(new LyricsLine.Syllable("Karaoke", 1000, 4000)))),
                    1700, 1000, 4000, true);
            paint(view).recycle();
            assertFalse(reusable(view, 1800));
        });
    }

    @Test public void untimedStackedPartsWakeAtTheirParentEnd() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            LyricsView view = new LyricsView(context);
            size(view);
            List<LyricsLine.VocalPart> parts = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                parts.add(new LyricsLine.VocalPart("" + i, "lead", "", "vocal", "Voice " + i,
                        i < 2 ? Collections.emptyList() : Collections.singletonList(
                                new LyricsLine.Syllable("Voice " + i, 1000, 9000))));
            }
            view.setKaraokeDisplayGranularity("line");
            view.setAutoInstrumentalBreakEnabled(false);
            view.setResult(new LyricsResult(Collections.singletonList(new LyricsLine(
                    1000, 5000, "Four voices", Collections.emptyList(), "", "vocal", parts)), "Test", "", true));
            view.setPlaybackFrame(5000, System.nanoTime());
            paint(view).recycle();
            assertTrue("settled before the effective end", reusable(view, 5000));
            assertFalse("untimed parts stop following the anchor at parent end + 1", reusable(view, 5016));
        });
    }

    private static boolean reusable(View view, double position) {
        try {
            Method method = view.getClass().getDeclaredMethod("canReusePlaybackFrame", double.class);
            method.setAccessible(true);
            return (boolean) method.invoke(view, position);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static LyricsResult result(boolean timed) {
        List<LyricsLine> lines = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long start = 1000 + i * 4000;
            List<LyricsLine.Syllable> syllables = timed ? Arrays.asList(
                    new LyricsLine.Syllable("Sing ", start, start + 1500),
                    new LyricsLine.Syllable("along", start + 1500, start + 3000)) : Collections.emptyList();
            lines.add(new LyricsLine(start, start + 3000, "Sing along", syllables,
                    "", "vocal", Collections.emptyList(), "발음 " + i, "번역 " + i, ""));
        }
        return new LyricsResult(lines, "Test", "", timed);
    }

    private static void size(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(840, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 480, 840);
    }
    private static Bitmap paint(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}
