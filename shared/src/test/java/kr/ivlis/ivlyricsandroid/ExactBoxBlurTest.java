package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.assertArrayEquals;

public class ExactBoxBlurTest {
    @Test public void matchesOriginalIntegerKernelIncludingEdgesAndRepeatedPasses() {
        Random random = new Random(73021);
        for (int width : new int[] {1, 2, 7, 48, 220}) {
            for (int height : new int[] {1, 3, 17, 48}) {
                for (int radius : new int[] {4, 7, 9, 17}) {
                    for (int passes : new int[] {2, 5, 9}) {
                        int[] pixels = new int[width * height];
                        for (int i = 0; i < pixels.length; i++) pixels[i] = random.nextInt();
                        int[] expected = pixels.clone();
                        original(expected, width, height, radius, passes);
                        ExactBoxBlur.blur(pixels, width, height, radius, passes);
                        assertArrayEquals(width + "x" + height + " radius=" + radius + " passes=" + passes, expected, pixels);
                    }
                }
            }
        }
    }
    private static void original(int[] pixels, int width, int height, int radius, int passes) {
        int[] tmp = new int[pixels.length];
        for (int pass = 0; pass < passes; pass++) {
            for (int axis = 0; axis < 2; axis++) {
                int[] src = axis == 0 ? pixels : tmp, dst = axis == 0 ? tmp : pixels;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int a=0, r=0, g=0, b=0, count=0;
                    for (int d=-radius; d<=radius; d++) {
                        int px=axis==0?Math.max(0,Math.min(width-1,x+d)):x;
                        int py=axis==1?Math.max(0,Math.min(height-1,y+d)):y;
                        int c=src[py*width+px];
                        a+=c>>>24; r+=(c>>>16)&255; g+=(c>>>8)&255; b+=c&255; count++;
                    }
                    dst[y*width+x]=(a/count<<24)|(r/count<<16)|(g/count<<8)|b/count;
                }
            }
        }
    }
}
