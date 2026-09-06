Run from the repository root after the existing Android Java compile:

```sh
python3 tests/run_provider_attribution_regression.py
```

The runner compiles the current production `AiLyricsRepository`, `LyricsResult`,
`LyricsLine` and `LyricsDiskCache`, using the repository’s `shared` Android compile output
for their unchanged dependencies. The JSON test dependency is pinned to a version
and SHA-256 digest; its first run downloads it from Maven Central into
`build/test-dependencies`. The report and generated test classes are written to
`build/reports/regressions/provider-attribution`.

Synthetic in-memory settings identify Gemini as the representative provider;
synthetic keyless results identify Google Translate, and a fallback state sequence
replaces a failed Gemini attempt with OpenAI ChatGPT. The actual session methods,
result builder, cache serializer/deserializer, cache rebase and typed provider
event dispatcher are invoked. No real provider request is made, so this verifies
attribution propagation rather than provider network availability.

The checks cover:

- Separate translation and pronunciation attribution, including empty streaming
  placeholders and a reset between provider attempts.
- Per-task and combined cache results, new cache serialization roundtrips and
  preservation of lyric provider/selection/ivSync identity.
- Legacy cache entries without provider metadata: keep translated text, discard
  the old incorrect AI label/detail, and do not infer a provider from settings.
- Actual loading-label and request-generation guard declarations extracted from
  `BaseLyricsActivity`: task-specific names, generic unrelated TMI text, and rejection
  after a track, generation or source-result change.

The deterministic Handler/Looper are test fixtures outside every app source set.
The test uses constructor-free allocation only for synthetic settings objects;
it never reads preferences, credentials, accounts, device data or cache files.
This is separate from the physical-device UI validation.
