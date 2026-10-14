#!/usr/bin/env bash
set -euo pipefail

ivlyrics_ui_avd_config="$HOME/.android/avd/ivlyrics-ui.avd/config.ini"
test -f "$ivlyrics_ui_avd_config"

# Keep the Pixel 5's approximately 393x851 dp viewport, with fewer physical
# pixels for the CPU-rendered CI display. Native touch/layout assertions remain.
sed -i -E \
  -e 's/^hw\.lcd\.width[[:space:]]*=.*/hw.lcd.width = 720/' \
  -e 's/^hw\.lcd\.height[[:space:]]*=.*/hw.lcd.height = 1560/' \
  -e 's/^hw\.lcd\.density[[:space:]]*=.*/hw.lcd.density = 293/' \
  "$ivlyrics_ui_avd_config"
