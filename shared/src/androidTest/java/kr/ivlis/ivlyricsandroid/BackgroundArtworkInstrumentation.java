package kr.ivlis.ivlyricsandroid;

import android.app.Instrumentation;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import java.util.Random;

/** Runs on real Android Bitmap storage, including its premultiplied alpha rounding. */
public final class BackgroundArtworkInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int cases = 0;
        try {
            Random random = new Random(53107);
            for (int width : new int[] {67, 220}) for (boolean opaque : new boolean[] {true, false}) {
                int height = width == 67 ? 43 : 220;
                int[] sourcePixels = new int[width * height];
                for (int i=0; i<sourcePixels.length; i++) sourcePixels[i] = random.nextInt() | (opaque ? 0xff000000 : 0);
                Bitmap source = Bitmap.createBitmap(sourcePixels, width, height, Bitmap.Config.ARGB_8888);
                for (int blur : new int[] {0, 30, 100}) {
                    Bitmap actual = BackgroundArtworkCache.prepare(source, blur);
                    float scale = 220f / Math.max(width, height);
                    int w = Math.max(48, Math.round(width * scale)), h = Math.max(48, Math.round(height * scale));
                    Bitmap expected = Bitmap.createScaledBitmap(source, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
                    int radius = Math.max(4, Math.round(5f + blur * .12f));
                    for (int pass=0; pass<Math.max(2,Math.min(9,2+blur/10)); pass++) originalPass(expected, radius);
                    int[] a = new int[w*h], b = new int[w*h];
                    expected.getPixels(a,0,w,0,0,w,h); actual.getPixels(b,0,w,0,0,w,h);
                    for (int i=0; i<a.length; i++) if(a[i]!=b[i]) throw new AssertionError("pixel mismatch width="+width+" opaque="+opaque+" blur="+blur+" pixel="+i);
                    expected.recycle(); actual.recycle(); cases++;
                }
                source.recycle();
            }
            result.putString("stream", "Bitmap pixel equality: " + cases + " opaque/translucent cases passed\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAILED after " + cases + " cases: " + error + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void originalPass(Bitmap bitmap, int radius) {
        int w=bitmap.getWidth(),h=bitmap.getHeight();
        int[] pixels=new int[w*h],tmp=new int[w*h];
        bitmap.getPixels(pixels,0,w,0,0,w,h);
        for(int axis=0;axis<2;axis++) {
            int[] src=axis==0?pixels:tmp,dst=axis==0?tmp:pixels;
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int a=0,r=0,g=0,b=0,n=0;
                for(int d=-radius;d<=radius;d++) {
                    int px=axis==0?Math.max(0,Math.min(w-1,x+d)):x, py=axis==1?Math.max(0,Math.min(h-1,y+d)):y;
                    int c=src[py*w+px]; a+=c>>>24;r+=(c>>>16)&255;g+=(c>>>8)&255;b+=c&255;n++;
                }
                dst[y*w+x]=(a/n<<24)|(r/n<<16)|(g/n<<8)|b/n;
            }
        }
        bitmap.setPixels(pixels,0,w,0,0,w,h);
    }
}
