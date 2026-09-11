#!/usr/bin/env python3
"""Exercise production frame ranges and pooled layouts with long overlapping vocals."""
import re
import subprocess
from pathlib import Path

from regression_runtime import ROOT, SHARED, REPORTS, java_tool

BASELINE = "1c8314c7468dc09cf3dbce5079e4a33703d9f1ac"
source = (SHARED / "LyricsView.java").read_text()
baseline = subprocess.check_output([
    "git", "show", f"{BASELINE}:shared/src/main/java/kr/ivlis/ivlyricsandroid/LyricsView.java"
], cwd=ROOT, text=True)


def declaration(value, signature):
    assert value.count(signature) == 1, signature
    start = value.index(signature)
    cursor = value.index("{", start) + 1
    depth = 1
    while depth:
        depth += (value[cursor] == "{") - (value[cursor] == "}")
        cursor += 1
    return value[start:cursor]


def renderer(value, name):
    radius = re.search(r"private static final int VISIBLE_RADIUS = \d+;", value).group(0)
    draw = declaration(value, "protected void onDraw(Canvas canvas)")
    start = draw.index("int anchorIndex =")
    end = draw.index("FrameLineLayouts layouts =", start)
    bounds = draw[start:end]
    declarations = "\n".join(declaration(value, signature) for signature in (
        "private static final class LineLayout {",
        "private static final class FrameLineLayouts extends ArrayList<LineLayout> {",
    ))
    return "static final class " + name + " {\n" + radius + "\n" + declarations + "\n" + r"""
        private final FrameLineLayouts pool = new FrameLineLayouts();
        int[] range(int count, float animatedCenterIndex, boolean manualScrollActive,
                int activeDisplayFirstSingingCacheValue) {
            List<DisplayLine> displayLines = Collections.nCopies(count, null);
    """ + bounds + r"""
            return new int[]{firstIndex, lastIndex};
        }
        void frame(int first, int last) {
            pool.beginFrame();
            for (int index = first; index <= last; index++) {
                pool.addValues(index, new DisplayLine(index), true, false, index,
                        Collections.singletonList(new DrawGroup()), 24f);
            }
            pool.finishFrame();
        }
        int size() { return pool.size(); }
        int indexAt(int index) { return pool.get(index).index; }
        Object[] slots() { return pool.toArray(); }
        boolean allReleased() {
            for (LineLayout entry : pool.entries) {
                if (entry != null && (entry.displayLine != null || !entry.groups.isEmpty())) return false;
            }
            return true;
        }
        boolean releasedAfter(int size) {
            for (int index = size; index < pool.entries.length; index++) {
                LineLayout entry = pool.entries[index];
                if (entry != null && (entry.displayLine != null || !entry.groups.isEmpty())) return false;
            }
            return true;
        }
        void clear() { pool.clearReferences(); }
    }
    """


work = REPORTS / "frame-layout-pool"
work.mkdir(parents=True, exist_ok=True)
test = work / "FrameLayoutPoolRegression.java"
template = Path(__file__).with_name("FrameLayoutPoolRegression.java.in").read_text()
test.write_text(template.replace("// INSERT_PRODUCTION_DECLARATIONS",
        renderer(baseline, "Baseline") + renderer(source, "Candidate")))
subprocess.run([java_tool("javac"), "-d", str(work), str(test)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "FrameLayoutPoolRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
report = (f"Frozen baseline: {BASELINE}\n"
          "Production onDraw range, LineLayout and FrameLineLayouts; synthetic row data only.\n"
          + result.stdout)
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
