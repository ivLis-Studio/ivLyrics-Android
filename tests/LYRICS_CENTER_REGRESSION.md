Run from the repository root:

```sh
python3 tests/run_lyrics_center_regression.py
```

The runner requires local JDK 17 or newer and writes generated Java classes and `result.txt`
to `build/reports/regressions/lyrics-center`. It does not build an APK, read a
device, access an account, or download dependencies.

The subject consists of the current `LyricsView.java` declarations extracted
verbatim on each run: `DisplayIndexMapping`, `remapDisplayCenter`, display identity,
`updateAnimatedCenter`, easing, `layoutAt`, `offsetFromAnchor`, `distanceBetween`,
`RowReflow` and its scheduling/progress/cleanup methods. The actual
`setPlaybackPosition`, `setResult`, `onSizeChanged`, and frame-cache invalidation
methods are also executed to verify that they discard stale row offsets.
The source and extracted declaration SHA-256 values are recorded in the report.
There is no duplicate implementation of the remap or animation algorithm in the
test. A small fixture supplies row identities, a controllable clock, block heights,
OS animation preferences, and the fields those methods read. Unrelated font-cache,
prewarm and View invalidation calls are inert fixtures. The onDraw center-coordinate assembly combines
the production distance functions using the same anchor/fraction relationship.

Cases cover prelude deletion, virtual interlude insertion/deletion, fractional
manual centers, stale fling cancellation, changed interlude identity, seek
monotonicity, and continuity at a fixed animation time after a seek crosses an
inserted slot. The physical reproduction's 12.200–18.494-second virtual break is
represented by its row identities and the final four milliseconds of the 300ms
lookahead. The negative control deliberately retains the old index and must
reproduce a one-block jump; the mapped state must remain at/below the viewport
center while approaching it without a reversal.

For interlude insertion, the test records previous row baselines, invokes the
production scheduler, and combines the actual centering and reflow functions on
every millisecond. It checks first-frame continuity, monotonic movement and opacity,
and cleanup at the centering deadline for 80ms, 160ms and 300ms durations, both
from the beginning and partway through a transition. Separate cases exercise
forward, backward and explicit smooth seeks, hard and soft result updates, resize,
and reduced-motion settings. The subpixel center-snap boundary is included so a
remaining row offset cannot pull a completed row past its final center.

The final short-interlude case uses the minimum accepted 501ms duration. At
201ms, the 300ms lookahead retargets centering to the next lyric while insertion
compensation remains active. Each frame calls the production centering update,
reflow preparation and remaining-factor functions before combining row geometry
and offset. This case requires the surviving row to move monotonically from its
previous baseline to the next lyric's center despite that change of target.

This verifies the production coordinate and easing contract with fixed row
heights. It does **not** execute the complete Android View, font measurement,
Canvas rendering, real audio timing, or the device UI. The physical playback
check remains a separate validation step.
