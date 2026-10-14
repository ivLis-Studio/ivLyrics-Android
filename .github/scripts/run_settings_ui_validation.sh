#!/usr/bin/env bash
# android-emulator-runner executes script lines separately. Keep failure capture in one shell.
set -uo pipefail

ivlyrics_ui_status=0
./gradlew :app:connectedDebugAndroidTest --console=plain || ivlyrics_ui_status=$?
mkdir -p build/reports/settings-ui
adb pull /sdcard/Android/data/kr.ivlis.ivlyricsandroid/files/ui-regressions build/reports/settings-ui \
  || echo "Native UI screenshots were not produced before the failure." >&2
exit "$ivlyrics_ui_status"
