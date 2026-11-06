#!/usr/bin/env python3
"""Compile the production native v6 decoder/applier and run shared JS golden vectors."""
from pathlib import Path
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, java_tool, json_jar
work=REPORTS/'sync-v6'
work.mkdir(parents=True,exist_ok=True)
main=work/'SyncV6Regression.java'
main.write_text((ROOT/'tests/SyncV6Regression.java.in').read_text())
files=[SHARED/name for name in ['LyricsLine.java','TrackSnapshot.java','SyncDataApplier.java','SyncDataV6Codec.java']]
cp=classpath(work,json_jar(),android_jar())
subprocess.run([java_tool('javac'),'-encoding','UTF-8','-cp',cp,'-d',str(work),*map(str,files),str(main)],check=True)
subprocess.run([java_tool('java'),'-cp',cp,'kr.ivlis.ivlyricsandroid.SyncV6Regression',str(ROOT/'tests/fixtures/sync-v6-golden.json')],check=True)
