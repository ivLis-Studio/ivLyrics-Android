#!/usr/bin/env python3
"""Compare offset reads, idle rendering and loading geometry with frozen production code."""
from pathlib import Path
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, java_tool, json_jar

BASELINE = "02ebb13f030650d50b7c5d59c7571b1c413f4d63"


def source(name, baseline):
    if baseline:
        return subprocess.check_output([
            "git", "show", f"{BASELINE}:shared/src/main/java/kr/ivlis/ivlyricsandroid/{name}.java"
        ], cwd=ROOT, text=True)
    return (SHARED / f"{name}.java").read_text()


def declaration(value, signature):
    assert value.count(signature) == 1, signature
    start = value.index(signature)
    cursor = value.index("{", start) + 1
    depth = 1
    while depth:
        depth += (value[cursor] == "{") - (value[cursor] == "}")
        cursor += 1
    return value[start:cursor]


def offsets(baseline):
    value = source("AiLyricsSettings", baseline)
    declarations = "\n".join(declaration(value, signature) for signature in (
        "int trackSyncOffsetMs(String trackKey)",
        "int trackVideoSyncOffsetMs(String trackKey)",
        "int bluetoothSyncOffsetMs(String deviceKey)",
        "private int trackOffsetMs(" if baseline else "private synchronized int trackOffsetMs(",
    ))
    if not baseline:
        declarations += declaration(value, "private static final class OffsetTable {")
        declarations += "private final Map<String, OffsetTable> cachedOffsetTables = new LinkedHashMap<>();"
    return "static final class " + ("BaselineOffsets" if baseline else "CandidateOffsets") + " extends OffsetFields {" + declarations + "}"


def preview(baseline):
    value = source("MainLyricPreviewView", baseline)
    declarations = "\n".join(declaration(value, signature) for signature in (
        "void setPreview(",
        "void setPlaybackPosition(",
        "private long estimatedPositionMs()",
        "private void drawLoadingLine(",
        "private KaraokeMotion.Values karaokeBounce(",
        "private void configureTextPaint(",
    ))
    if not baseline:
        declarations += declaration(value, "private LinearGradient loadingShader(")
    on_draw = declaration(value, "protected void onDraw(Canvas canvas)")
    start = on_draw.index("boolean animationsEnabled =" if baseline else "frameAnimationsEnabled =")
    end = on_draw.index("float left =", start)
    declarations += "void beginFrame() {" + on_draw[start:end] + "}"
    return "static final class " + ("BaselinePreview" if baseline else "CandidatePreview") + " extends PreviewFields {" + declarations + "}"


def empty_renderer(baseline):
    value = source("LyricsView", baseline)
    declarations = "\n".join(declaration(value, signature) for signature in (
        "void setPlaybackPosition(",
        "private void drawEmpty(Canvas canvas)",
    ))
    return "static final class " + ("BaselineEmpty" if baseline else "CandidateEmpty") + " extends EmptyFields {" + declarations + "}"


work = REPORTS / "render-idle"
work.mkdir(parents=True, exist_ok=True)
test = work / "RenderIdleRegression.java"
template = Path(__file__).with_name("RenderIdleRegression.java.in").read_text()
test.write_text(template.replace("// INSERT_PRODUCTION_DECLARATIONS", "\n".join(
    factory(baseline) for factory in (offsets, preview, empty_renderer) for baseline in (True, False)
)))
dependency = str(json_jar())
subprocess.run([java_tool("javac"), "-cp", dependency, "-d", str(work), str(test)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work) + ":" + dependency,
                         "RenderIdleRegression"], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
report = (f"Frozen baseline: {BASELINE}\nProduction methods with synthetic Android collaborators; actual JSON parser.\n"
          + result.stdout)
(work / "result.txt").write_text(report)
print(report, end="")
if result.returncode:
    raise SystemExit(result.returncode)

# Keep precision-sensitive preview measurement checks on the established CI entry point.
MEASUREMENT_BASELINE = "3f238d1a503b2b400cbebd369857428ab837ae83"
def measurement_renderer(baseline):
    value = subprocess.check_output(["git", "show", f"{MEASUREMENT_BASELINE}:shared/src/main/java/kr/ivlis/ivlyricsandroid/MainLyricPreviewView.java"], cwd=ROOT, text=True) if baseline else source("MainLyricPreviewView", False)
    signatures = ("private void drawPlainRubyText(", "private void drawRubyText(",
                  "private int charIndexForCodePointOffset(", "private static List<String> splitChars(",
                  "private static final class TextSegment {", "private static final class RubyAnnotation {")
    body = "\n".join(declaration(value, signature) for signature in signatures)
    if baseline:
        draw = declaration(value, "private void drawKaraokeLine(")
        width = draw[draw.index("        float rowWidth = 0f;"):draw.index("            int rowSave =", draw.index("        float rowWidth = 0f;"))]
        body += "private float karaokeRowWidth(PreviewLine line, List<TextSegment> segments) {" + width + "} return rowWidth; }"
    else:
        body += declaration(value, "private float karaokeRowWidth(")
    bridge = '''
        final class PreviewLine {
            final String text, kind = "vocal";
            final List<RubyAnnotation> annotations;
            final boolean continuousShaping;
            float cachedShapedTextSize = Float.NaN, cachedShapedWidth;
            Typeface cachedShapedTypeface;
            PreviewLine(String text, String ruby) { this.text = text; continuousShaping = SCRIPT_INITIALIZER; annotations = List.of(new RubyAnnotation(2, 2, ruby)); }
            boolean hasRuby() { return true; }
            List<RubyAnnotation> rubyAnnotations() { return annotations; }
        }
        PreviewLine line; String key;
        TextSegment segment; String segmentRuby;
        PreviewLine widthLine; String widthText; int widthGeneration = -999;
        List<TextSegment> widthSegments;
        float rowWidth(String text, int generation, float textSize) {
            if (!text.equals(widthText)) { widthLine = new PreviewLine(text, ""); widthText = text; widthSegments = null; }
            if (widthSegments == null || widthGeneration != generation) {
                widthSegments = new ArrayList<>() {
                    @Override public Iterator<TextSegment> iterator() {
                        Iterator<TextSegment> iterator = super.iterator();
                        return new Iterator<>() { public boolean hasNext() { return iterator.hasNext(); }
                            public TextSegment next() { visitedSegments++; return iterator.next(); } };
                    }
                };
                for (int i = 0; i < 6; i++) widthSegments.add(new TextSegment("x", (i + 1.37f) * (generation + 1), 0, 1000, i, 1));
                widthGeneration = generation;
            }
            textPaint.setTypeface(font); textPaint.setTextSize(textSize);
            return karaokeRowWidth(widthLine, widthSegments);
        }
        Typeface typefaceForLine(PreviewLine ignored) { return font; }
        void plain(Canvas canvas, float x, float baseline, float size, int alpha, String text, String reading) {
            String nextKey = text + "|" + reading;
            if (!nextKey.equals(key)) { line = new PreviewLine(text, reading); key = nextKey; }
            drawPlainRubyText(canvas, line, x, baseline, size, alpha);
        }
        void karaoke(Canvas canvas, float x, float baseline, float size, float fill, boolean primary, String reading) {
            if (!reading.equals(segmentRuby)) { segment = new TextSegment("漢字", 25f, 0, 1000, 0, 2, reading); segmentRuby = reading; }
            textPaint.setTypeface(font);
            drawRubyText(canvas, segment, x, baseline, "vocal", size, fill, primary);
        }
    '''
    bridge = bridge.replace("SCRIPT_INITIALIZER", "false" if baseline else "TimedSyllableNormalizer.requiresContinuousShaping(text)")
    return "static final class " + ("BaselineMeasurements" if baseline else "CandidateMeasurements") + " extends Fields {" + body + bridge + "}"

measurement = work / "PreviewMeasurementsRegression.java"
normalizer = source("TimedSyllableNormalizer", False)
script = "\n".join(declaration(normalizer, signature) for signature in (
    "static boolean requiresContinuousShaping(String text)", "private static boolean isArabicScriptCodePoint(int codePoint)"))
script = script.replace('String value = text == null ? "" : text;', 'scans++; String value = text == null ? "" : text;')
measurement.write_text(Path(__file__).with_name("PreviewMeasurementsRegression.java.in").read_text().replace("// INSERT_SCRIPT_DECLARATIONS", script).replace(
    "// INSERT_MEASUREMENT_DECLARATIONS", "\n".join(measurement_renderer(baseline) for baseline in (True, False))))
subprocess.run([java_tool("javac"), "-d", str(work), str(measurement)], check=True)
measured = subprocess.run([java_tool("java"), "-cp", str(work), "PreviewMeasurementsRegression"], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
print(measured.stdout, end="")
(work / "measurements-result.txt").write_text(measured.stdout)
raise SystemExit(measured.returncode)
