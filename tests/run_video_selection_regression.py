#!/usr/bin/env python3
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar
work = REPORTS / 'video-selection'
work.mkdir(parents=True, exist_ok=True)
cp = classpath(work, json_jar(), compiled_classes('shared'), android_jar())
sources = [SHARED / name for name in ('YouTubeVideoSelection.java', 'YouTubeBackgroundRepository.java', 'VideoSelectionTranslations.java')]
sources.append(ROOT / 'tests/kr/ivlis/ivlyricsandroid/VideoSelectionRegression.java')
subprocess.run([java_tool('javac'), '-cp', cp, '-d', str(work), *map(str, sources)], check=True)
subprocess.run([java_tool('java'), '-cp', cp, 'kr.ivlis.ivlyricsandroid.VideoSelectionRegression'], check=True)
