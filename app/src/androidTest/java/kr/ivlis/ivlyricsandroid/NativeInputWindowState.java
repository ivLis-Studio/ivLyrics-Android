package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only API-35 InputDispatcher snapshot. Fail closed if its format changes. */
final class NativeInputWindowState {
    private static final Pattern DISPLAY = Pattern.compile("(?m)^  Display: (\\d+)\\s*$");
    private static final Pattern SIZE = Pattern.compile("logicalSize=(\\d+)x(\\d+)");
    private static final Pattern MATRIX_ROW = Pattern.compile(
            "(?m)^\\s+([-+\\d.eE]+)\\s+([-+\\d.eE]+)\\s+([-+\\d.eE]+)\\s*$");
    private static final Pattern REGION_RECT = Pattern.compile(
            "\\[(-?\\d+),\\s*(-?\\d+)\\]\\[(-?\\d+),\\s*(-?\\d+)\\]");
    final int width, height;
    private final double a, b, tx, c, d, ty;
    private final List<InputWindow> windows;

    private NativeInputWindowState(int width, int height, double[] matrix, List<InputWindow> windows) {
        this.width = width; this.height = height;
        a = matrix[0]; b = matrix[1]; tx = matrix[2];
        c = matrix[3]; d = matrix[4]; ty = matrix[5];
        this.windows = windows;
        if (Math.abs(a * d - b * c) < 0.000001) throw new AssertionError("Singular input display transform");
    }

    static NativeInputWindowState parse(String dump, int displayId) {
        int dispatcher = dump.indexOf("Input Dispatcher State:");
        if (dispatcher < 0) throw new AssertionError("Missing InputDispatcher state");
        String state = dump.substring(dispatcher);
        Matcher displays = DISPLAY.matcher(state);
        int start = -1, end = state.length();
        while (displays.find()) {
            if (start >= 0) { end = displays.start(); break; }
            if (Integer.parseInt(displays.group(1)) == displayId) start = displays.end();
        }
        if (start < 0) throw new AssertionError("Missing input display " + displayId);
        String display = state.substring(start, end);
        int windowStart = display.indexOf("    Windows:");
        if (windowStart < 0) throw new AssertionError("Missing native input windows");
        String header = display.substring(0, windowStart);
        Matcher size = SIZE.matcher(header), rows = MATRIX_ROW.matcher(header);
        if (!size.find()) throw new AssertionError("Missing logical input display size");
        double[] matrix = {1, 0, 0, 0, 1, 0};
        if (!header.contains("(IDENTITY)")) {
            for (int row = 0; row < 2; row++) {
                if (!rows.find()) throw new AssertionError("Missing native display transform");
                for (int col = 0; col < 3; col++) matrix[row * 3 + col] = Double.parseDouble(rows.group(col + 1));
            }
        }
        List<InputWindow> windows = new ArrayList<>();
        String windowSection = display.substring(windowStart);
        Matcher nextSection = Pattern.compile("(?m)^  \\S").matcher(windowSection);
        if (nextSection.find()) windowSection = windowSection.substring(0, nextSection.start());
        for (String line : windowSection.split("\n")) {
            if (!line.matches("\\s+\\d+: name=.*")) continue;
            String name = between(line, "name=", ", id=");
            int windowDisplay = Integer.parseInt(between(line, "displayId=", ", inputConfig="));
            if (windowDisplay != displayId) continue;
            String config = between(line, "inputConfig=", ", alpha=");
            String region = between(line, "touchableRegion=", ", ownerPid=");
            int uid = Integer.parseInt(between(line, "ownerUid=", ", dispatchingTimeout="));
            windows.add(new InputWindow(name, uid, config, region));
        }
        if (windows.isEmpty()) throw new AssertionError("No parsed input windows");
        return new NativeInputWindowState(Integer.parseInt(size.group(1)), Integer.parseInt(size.group(2)), matrix, windows);
    }

    private static String between(String value, String prefix, String suffix) {
        int start = value.indexOf(prefix);
        int end = start < 0 ? -1 : value.indexOf(suffix, start + prefix.length());
        if (end < 0) throw new AssertionError("Unknown input window format: " + value);
        return value.substring(start + prefix.length(), end);
    }

    InputWindow targetAt(float x, float y) {
        // InputDispatcher dumps regions in the physical, unrotated display space. Views
        // and injected pointer coordinates use the current logical display. Invert the
        // DISPLAY transform, not each window's separate screen-to-local transform.
        double determinant = a * d - b * c;
        double physicalX = (d * (x - tx) - b * (y - ty)) / determinant;
        double physicalY = (-c * (x - tx) + a * (y - ty)) / determinant;
        for (InputWindow window : windows) {
            if (window.config.contains("NOT_VISIBLE") || window.config.contains("NOT_TOUCHABLE")
                    || window.config.contains("SPY")) continue;
            Matcher rect = REGION_RECT.matcher(window.region);
            while (rect.find()) {
                if (physicalX >= Integer.parseInt(rect.group(1)) && physicalX < Integer.parseInt(rect.group(3))
                        && physicalY >= Integer.parseInt(rect.group(2)) && physicalY < Integer.parseInt(rect.group(4))) return window;
            }
            if (!window.region.equals("<empty>") && !REGION_RECT.matcher(window.region).find()) {
                throw new AssertionError("Unknown native touch region: " + window.region);
            }
        }
        return null;
    }

    boolean targets(float x, float y, int uid, String title) {
        InputWindow window = targetAt(x, y);
        return window != null && window.uid == uid && !window.config.contains("DROP_INPUT")
                && !window.config.contains("NO_INPUT_CHANNEL")
                && (window.name.equals(title) || window.name.endsWith(" " + title));
    }

    static final class InputWindow {
        final String name, config, region;
        final int uid;
        InputWindow(String name, int uid, String config, String region) {
            this.name = name; this.uid = uid; this.config = config; this.region = region;
        }
        @Override public String toString() { return name + " uid=" + uid + " " + config + " region=" + region; }
    }
}
