#!/usr/bin/env python3
"""Pronunciation-first value swap, unit timing split and AI response handling with synthetic lyrics; no network."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "pronunciation-first"
work.mkdir(parents=True, exist_ok=True)
fixture = work / "PronunciationFirstRegression.java"
fixture.write_text((ROOT / "tests/PronunciationFirstRegression.java.in").read_text())
files = [SHARED / name for name in (
    "PronunciationFirst.java", "PronunciationSlots.java", "LyricsTimingUpgrade.java", "LrcRedLyricsProvider.java", "LyricsProviderSettings.java",
    "LyricsProviderSelectionPlan.java", "LyricsResult.java", "LyricsLine.java",
)] + [fixture]
cp = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-nowarn", "-cp", cp, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", cp, "kr.ivlis.ivlyricsandroid.PronunciationFirstRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
print(result.stdout, end="")
# The view draws a pronunciation fill row on the karaoke path without bounce, for line and voice rows.
view = (SHARED / "LyricsView.java").read_text()
assert "|| !karaokeBounceEffectEnabled || group.supplement)" in view, "supplement rows never bounce"
assert view.count("PronunciationFirst.Fill.matching(pronunciationFill.") == 2, "line and voice pronunciation rows fill"
assert "List<String> pieces = splitChars(unit.text);" in view, "fill rows wrap like plain pronunciation rows"
activity = (SHARED / "BaseLyricsActivity.java").read_text()
assert activity.count(".setPronunciationFill(fill);") == 3, "every lyric view receives the fill"
assert "return pronunciationFirstActive() || pronunciationFillActive();" in activity
# Karaoke pronunciation is made per sung unit; timing for those lines is derived locally.
repository = (SHARED / "AiLyricsRepository.java").read_text()
assert "Map<Integer, List<String>> slots = pronunciation && session.baseResult.karaoke" in repository
assert "if (!requests.get(index).text.trim().isEmpty() && combined.get(index).trim().isEmpty()) missing.add(index);" in repository, \
    "lines without exact slots fall back to the line request"
assert "restorePronunciationSlots(trackKey, pronunciationCacheKey);" in repository, "cached pronunciation brings its slots back"
assert "if (!derived.containsKey(item.key)) items.add(item);" in repository, "only lines without slots ask AI for unit timing"
assert "run.accept(PronunciationSlots.chunks(missing), true);" in repository, "one repair pass"

raise SystemExit(result.returncode)
