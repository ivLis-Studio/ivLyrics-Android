#!/usr/bin/env bash
# android-emulator-runner executes script lines separately. Keep failure capture in one shell.
set -uo pipefail

mkdir -p build/reports/settings-ui
ivlyrics_ui_remote=/sdcard/Android/data/kr.ivlis.ivlyricsandroid/files/ui-regressions

# sys.boot_completed precedes first-boot package/IME initialization. Leave it
# time to settle, keep the software keyboard enabled, and avoid competing with
# the emulator's software renderer for every host CPU during instrumentation.
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent KEYCODE_MENU
adb shell settings put secure show_ime_with_hard_keyboard 1
sleep 20

# The unified test platform uninstalls the target APK after instrumentation, which
# removes its scoped external files. Collect finished PNGs while tests are running.
ivlyrics_ui_collect() {
  local ivlyrics_ui_file
  while IFS= read -r ivlyrics_ui_file; do
    ivlyrics_ui_file="${ivlyrics_ui_file%$'\r'}"
    [[ "$ivlyrics_ui_file" == *.png && "$ivlyrics_ui_file" != */* ]] || continue
    [[ -f "build/reports/settings-ui/$ivlyrics_ui_file" ]] && continue
    adb pull "$ivlyrics_ui_remote/$ivlyrics_ui_file" "build/reports/settings-ui/$ivlyrics_ui_file" >/dev/null 2>&1 || true
  done < <(adb shell ls "$ivlyrics_ui_remote" 2>/dev/null || true)
}

./gradlew :app:connectedDebugAndroidTest --console=plain --max-workers=2 &
ivlyrics_ui_gradle_pid=$!
while kill -0 "$ivlyrics_ui_gradle_pid" 2>/dev/null; do
  ivlyrics_ui_collect
  sleep 5
done
ivlyrics_ui_status=0
wait "$ivlyrics_ui_gradle_pid" || ivlyrics_ui_status=$?
ivlyrics_ui_collect
if ! compgen -G 'build/reports/settings-ui/*.png' >/dev/null; then
  echo "Native UI screenshots were not produced before the failure." >&2
fi
exit "$ivlyrics_ui_status"
