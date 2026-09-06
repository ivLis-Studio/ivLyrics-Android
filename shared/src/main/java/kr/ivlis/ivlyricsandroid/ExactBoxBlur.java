package kr.ivlis.ivlyricsandroid;

/** Separable ARGB box blur retaining the original clamp edges and integer rounding. */
final class ExactBoxBlur {
    private ExactBoxBlur() {}

    static void blur(int[] pixels, int width, int height, int radius, int passes) {
        if (width <= 0 || height <= 0 || radius < 0 || passes <= 0) return;
        int[] scratch = new int[pixels.length];
        for (int pass = 0; pass < passes; pass++) {
            pass(pixels, scratch, width, height, radius);
        }
    }

    static void pass(int[] pixels, int[] scratch, int width, int height, int radius) {
        sweep(pixels, scratch, width, height, radius, true);
        sweep(scratch, pixels, width, height, radius, false);
    }

    private static void sweep(int[] source, int[] target, int width, int height, int radius, boolean horizontal) {
        int length = horizontal ? width : height;
        int lines = horizontal ? height : width;
        int stride = horizontal ? 1 : width;
        int count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * width : line;
            int a = 0, r = 0, g = 0, b = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int color = source[base + Math.max(0, Math.min(length - 1, offset)) * stride];
                a += color >>> 24; r += (color >>> 16) & 255;
                g += (color >>> 8) & 255; b += color & 255;
            }
            for (int index = 0; index < length; index++) {
                target[base + index * stride] = (a / count << 24) | (r / count << 16) | (g / count << 8) | b / count;
                int removed = source[base + Math.max(0, index - radius) * stride];
                int added = source[base + Math.min(length - 1, index + radius + 1) * stride];
                a += (added >>> 24) - (removed >>> 24);
                r += ((added >>> 16) & 255) - ((removed >>> 16) & 255);
                g += ((added >>> 8) & 255) - ((removed >>> 8) & 255);
                b += (added & 255) - (removed & 255);
            }
        }
    }
}
