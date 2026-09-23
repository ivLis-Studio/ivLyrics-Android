#!/usr/bin/env python3
"""Exercise production model discovery and device authorization without Android SDK or accounts."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, classpath, java_tool, json_jar

work = REPORTS / "ai-models"
work.mkdir(parents=True, exist_ok=True)
dependencies = classpath(work, json_jar())
sources = [SHARED / name for name in ("AiProviderModels.java", "PollinationsAuthClient.java", "PaxsenixAiModels.java")]
sources.append(ROOT / "tests/kr/ivlis/ivlyricsandroid/AiModelsRegression.java")
activity = (SHARED / "BaseLyricsActivity.java").read_text()
methods = activity[activity.index("    private void updateAiModelPickerUi("):activity.index("    private void saveOpenAIConnections(")]
ui_source = work / "AiModelPickerRegression.java"
ui_source.write_text((ROOT / "tests/AiModelPickerRegression.java.in").read_text().replace("    // PRODUCTION_METHODS", methods))
sources.append(ui_source)
subprocess.run([java_tool("javac"), "-cp", dependencies, "-d", str(work), *map(str, sources)], check=True)
subprocess.run([java_tool("java"), "-cp", dependencies, "kr.ivlis.ivlyricsandroid.AiModelsRegression"], check=True)
subprocess.run([java_tool("java"), "-cp", dependencies, "kr.ivlis.ivlyricsandroid.AiModelPickerRegression"], check=True)
