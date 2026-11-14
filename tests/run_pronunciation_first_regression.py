#!/usr/bin/env python3
"""Pronunciation-first value swap, unit timing split and AI response handling with synthetic lyrics; no network."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "pronunciation-first"
work.mkdir(parents=True, exist_ok=True)
fixture = work / "PronunciationFirstRegression.java"
fixture.write_text((ROOT / "tests/PronunciationFirstRegression.java.in").read_text())
files = [SHARED / name for name in (
    "PronunciationFirst.java", "PronunciationSlots.java", "PronunciationRequests.java", "LyricsTimingUpgrade.java", "LrcRedLyricsProvider.java", "LyricsProviderSettings.java",
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
# With synced pronunciation on, karaoke pronunciation is made per sung unit; timing for those lines is derived locally.
repository = (SHARED / "AiLyricsRepository.java").read_text()
assert "Map<Integer, List<String>> slots = pronunciation && session.baseResult.karaoke && settings.syncedPronunciationEnabled" in repository, \
    "plain line pronunciation unless synced pronunciation is on"
assert "if (!requests.get(index).text.trim().isEmpty() && combined.get(index).trim().isEmpty()) missing.add(index);" in repository, \
    "lines without exact slots fall back to the line request"
assert "restorePronunciationSlots(trackKey, pronunciationCacheKey);" in repository, "cached pronunciation brings its slots back"
assert "if (!derived.containsKey(item.key)) items.add(item);" in repository, "only lines without slots ask AI for unit timing"
assert "PronunciationRequests.batches(items, PronunciationSlots::chunks)" in repository, "unit pronunciation as small parallel requests"
assert "for (List<PronunciationSlots.Item> chunk : PronunciationSlots.chunks(missing)) repairs.add(new SlotJob(chunk, true));" in repository, \
    "a request's misses are asked again at once"
assert "for (List<PronunciationFirst.Item> chunk : PronunciationRequests.batches(items, chunks)) first.add(new TimedJob(chunk, false));" in repository, \
    "pronunciation timing as small parallel requests"
assert "Executors.newFixedThreadPool(PronunciationRequests.PARALLEL_REQUESTS)" in repository
# Synced pronunciation is a setting; pronunciation-first, its toggle and the fill belong to it.
settings_source = (SHARED / "AiLyricsSettings.java").read_text()
assert "return prefs.getBoolean(KEY_SYNCED_PRONUNCIATION, false);" in settings_source
assert "localAiIdleMinutes(),\n                syncedPronunciationEnabled()\n        );" in settings_source, "the snapshot carries it"
assert activity.count("&& syncedPronunciationOn()") == 3, "pronunciation-first, the fill and the toggle need synced pronunciation"
assert 'settingsConditional(pronunciationFillSwitch,\n                () -> aiLyricsSettings == null || aiLyricsSettings.syncedPronunciationEnabled(),' in activity
# Pronunciation-first reads 200 ms ahead on the lyric clock, outside the sync offsets.
assert activity.count("+ pronunciationLeadMs;") == 2 and "- pronunciationLeadMs;" in activity
assert "pronunciationLeadMs = display != null && display != result ? PRONUNCIATION_FIRST_LEAD_MS : 0L;" in activity
assert "static final long PRONUNCIATION_FIRST_LEAD_MS = 200L;" in activity

raise SystemExit(result.returncode)
