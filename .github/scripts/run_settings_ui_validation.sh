#!/usr/bin/env bash
# android-emulator-runner executes script lines separately. Keep failure capture in one shell.
set -uo pipefail

mkdir -p build/reports/settings-ui
ivlyrics_ui_remote=/sdcard/Android/data/kr.ivlis.ivlyricsandroid/files/ui-regressions
ivlyrics_first_use_remote=/sdcard/Android/data/kr.ivlis.ivlyricsandroid/files/first-use-screenshots

# sys.boot_completed precedes first-boot package/IME initialization. Leave it
# time to settle, keep the software keyboard enabled, and avoid competing with
# the emulator's software renderer for every host CPU during instrumentation.
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent KEYCODE_MENU
adb shell settings put secure show_ime_with_hard_keyboard 1
# Some API 35 accessibility overlays request NOSENSOR and revert UiAutomation's rotation.
# This ephemeral emulator must honor the rotation used by the native landscape scenarios.
adb shell wm fixed-to-user-rotation enabled
sleep 20

# The unified test platform uninstalls the target APK after instrumentation, which
# removes its scoped external files. Collect finished PNGs while tests are running.
ivlyrics_ui_collect_dir() {
  local ivlyrics_ui_source="$1"
  local ivlyrics_ui_output="$2"
  local ivlyrics_ui_file
  mkdir -p "$ivlyrics_ui_output"
  while IFS= read -r ivlyrics_ui_file; do
    ivlyrics_ui_file="${ivlyrics_ui_file%$'\r'}"
    [[ "$ivlyrics_ui_file" == *.png && "$ivlyrics_ui_file" != */* ]] || continue
    [[ -f "$ivlyrics_ui_output/$ivlyrics_ui_file" ]] && continue
    adb pull "$ivlyrics_ui_source/$ivlyrics_ui_file" "$ivlyrics_ui_output/$ivlyrics_ui_file" >/dev/null 2>&1 || true
  done < <(adb shell ls "$ivlyrics_ui_source" 2>/dev/null || true)
}

ivlyrics_ui_collect() {
  ivlyrics_ui_collect_dir "$ivlyrics_ui_remote" build/reports/settings-ui
  ivlyrics_ui_collect_dir "$ivlyrics_first_use_remote" build/reports/settings-ui/first-use
}

# Run every androidTest class, including SettingsUiRegressionTest and FirstUseUiRegressionTest.
# Keep this unfiltered so new native scenarios are not silently excluded.
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
if ! compgen -G 'build/reports/settings-ui/first-use/*.png' >/dev/null; then
  echo "Native first-use screenshots were not produced before the failure." >&2
fi
exit "$ivlyrics_ui_status"
