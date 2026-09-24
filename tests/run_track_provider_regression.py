#!/usr/bin/env python3
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar
work = REPORTS / 'track-provider'
work.mkdir(parents=True, exist_ok=True)
cp = classpath(work, json_jar(), compiled_classes('shared'), android_jar())
subprocess.run([java_tool('javac'), '-cp', cp, '-d', str(work), str(SHARED / 'LyricsProviderSettings.java'), str(SHARED / 'LyricsProviderSelectionPlan.java'), str(ROOT / 'tests/kr/ivlis/ivlyricsandroid/TrackProviderRegression.java')], check=True)
subprocess.run([java_tool('java'), '-cp', cp, 'kr.ivlis.ivlyricsandroid.TrackProviderRegression'], check=True)
