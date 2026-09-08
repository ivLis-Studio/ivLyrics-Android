# Furigana module asset device regression

This opt-in smoke test runs the production `FuriganaRepository`, production bridge,
and real Kuromoji tokenizer in Android WebView. It installs and operates only the
separate `kr.ivlis.ivlyricsandroid.qa` application. It does not operate Spotify,
replace the LSPatch module, or read/change their preferences, accounts, or caches.

Run from the repository root with an explicitly selected connected device:

```sh
python3 tests/run_furigana_webview_device_smoke.py --serial DEVICE_SERIAL
```

The runner builds `:app:assembleQa`, creates a temporary test-host APK with
`assets/furigana/bridge.html` removed, and embeds the pristine QA APK as a separate
module resource archive. It signs only this test host with the local Android debug
key. The fixture loads the archive through Android's package-resource APIs and
overrides the context's assets/resources, like the Spotify module context.

The Activity verifies that the host Application cannot open the bridge while the
wrapped module context can. It then demonstrates that the previous
`file:///android_asset/furigana/bridge.html` navigation fails in real Chromium,
even with the wrapped context. Finally, the production repository must generate
nonempty ruby for two synthetic Japanese rows, preserve their original text,
timing and supplements, and return the same annotations from the warm cache.

If the device cannot resolve or reach the public CDN, keep the live-network
failure evidence and run the explicit fixture mode:

```sh
python3 tests/run_furigana_webview_device_smoke.py --serial DEVICE_SERIAL --cdn-fixture
```

This mode downloads the authentic `kuromoji@0.1.2` package on the host, verifies
the registry's SHA-512 integrity value, and places its unmodified JavaScript and
dictionary bytes in the temporary QA APK. A debug-only WebView client intercepts
requests to the two exact CDN hosts. The production bridge, repository, URL
construction, JavaScript execution, dictionary decompression, tokenizer and ruby
conversion still run normally. Malformed URLs or unrelated requests are not
intercepted. This mode verifies the actual engine but does not verify the device's
CDN connectivity. None of these fixture assets enters a production APK.

The QA process is stopped after each run. Reports under
`build/reports/regressions/furigana-webview-device/` identify the device, Android
and WebView versions, source and APK hashes, and whether CDN interception was
used. `device-live-cdn.log` and `device-cdn-fixture.log` remain separate. The test
does not exercise actual Spotify hooks, its lyrics Activity, or live playback;
those require a separate application-level device check.
