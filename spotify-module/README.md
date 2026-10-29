# ivLyrics Android — Spotify 모듈

한국어 · [English](#english) · [독립 앱 안내](../README.md)

Spotify 안에서 ivLyrics 가사를 보고 싶은 분을 위한 LSPosed/LSPatch 모듈입니다. 모듈 이름은 **ivLyrics for Spotify**이며, 재생 화면의 가사 카드와 전체 가사 페이지를 바꿉니다. 별도 앱에서 가사를 보려면 [ivLyrics Android 독립 앱](../README.md)을 사용하세요.

[모듈 다운로드](https://github.com/ivLis-Studio/ivLyrics-Android/releases/latest) · [Discord 도움받기](https://discord.gg/2fu36fUzdE)

## 받을 파일과 준비물

릴리스의 Assets에서 **`ivLyrics-LSPatch-v<버전>.apk`**를 받으세요. `ivLyrics-Android-v<버전>-release.apk`는 독립 앱입니다. 모듈 APK에는 Spotify나 미리 패치한 Spotify가 들어 있지 않으며, 별도 가사 앱 실행 아이콘도 제공하지 않습니다.

| 설치 방식 | 필요한 환경 |
| --- | --- |
| LSPosed | 루팅과 동작하는 LSPosed 환경, Spotify에 대한 모듈 활성화가 필요합니다. 모듈 APK의 최소 조건은 Android 8.0이며 로더·Spotify 조건이 추가로 적용됩니다. |
| LSPatch | Android 9 이상, LSPatch 관리자와 패치할 Spotify APK가 필요합니다. 루팅 없이 호스트 APK를 패치하는 방식입니다. |

현재 모듈이 인식하는 Spotify 버전은 **9.1.80.2221 / 9.1.42.2058**뿐입니다. 다른 버전에서는 가사 UI 연결을 적용하지 않습니다. 이 Spotify APK는 ivLyrics 릴리스에 포함되지 않으므로 직접 적법하게 확보해야 합니다. 버전이 일치해도 모든 기기·OS·로더에서의 동작을 보장하지는 않습니다.

실기기 기준 구성은 루팅된 LSPosed입니다. [공식 LSPatch](https://github.com/LSPosed/LSPatch)는 보관 상태인 레거시 프로젝트입니다. 최신 Android나 Spotify와의 호환성을 전제로 설치하지 마세요.

## LSPatch로 설치하기

1. [공식 LSPatch 릴리스](https://github.com/LSPosed/LSPatch/releases)의 관리자 `manager.apk`, 위 ivLyrics 모듈 APK, 지원 버전의 Spotify APK를 준비하세요. 관리자의 설치·사용은 [LSPatch 안내](https://github.com/LSPosed/LSPatch#usage)를 참고하세요.
2. 관리자에서 새 패치를 만들고 **Spotify APK**를 대상으로 선택하세요. ivLyrics 모듈 APK 자체를 패치 대상으로 고르지 마세요.
3. 모듈을 포함하는 패치 옵션에서 내려받은 **`ivLyrics-LSPatch-v<버전>.apk`를 추가**하세요. 관리자에 따라 내장/통합 방식 또는 설치된 모듈을 참조하는 방식이 있으므로 선택한 방식의 안내를 따르세요.
4. 패치를 완료하고 관리자가 만든 **Spotify APK**를 설치하세요. ivLyrics 릴리스의 모듈 파일과 이 결과 파일은 서로 다릅니다.
5. 설치한 Spotify를 완전히 종료한 뒤 다시 열고 로그인해 음악을 재생하세요.

> 패치된 Spotify의 서명은 기존 설치본과 다를 수 있어 덮어쓰기가 거부될 수 있습니다. 기존 Spotify를 제거해야 하는지 먼저 확인하세요. 제거하면 Spotify의 로컬 설정·다운로드뿐 아니라 그 안에 보관된 모듈 설정·캐시도 사라질 수 있습니다. 이후 업데이트에도 같은 패치 서명 키를 유지해야 합니다.

## 이미 LSPosed를 사용하고 있나요?

1. Spotify를 강제 종료하고 `ivLyrics-LSPatch-v<버전>.apk`를 설치하세요.
2. LSPosed에서 **ivLyrics for Spotify**를 켜고 범위(scope)에 **Spotify (`com.spotify.music`)**를 선택하세요.
3. 로더가 요구하는 재시작을 완료하고 Spotify를 여세요. 이 방식은 Spotify APK를 다시 패치할 필요가 없습니다.

독립 앱 설치와 알림 접근·플로팅 아이콘 권한은 모듈에 필요하지 않습니다. 모듈은 Spotify 안의 재생 정보와 메타데이터를 사용하므로 독립 앱의 Spotify Client ID·Secret 첫 설정도 요구하지 않습니다.

## 첫 곡과 번역 설정

음악을 재생한 뒤 Spotify 재생 화면의 가사 카드에서 전체 가사를 여세요. Spotify 자체 가사가 없는 음악에도 ivLyrics 가사 카드를 추가할 수 있지만, 실제 가사 데이터가 있어야 내용이 표시됩니다. 팟캐스트·에피소드와 팟캐스트에 포함된 곡은 대상이 아닙니다.

가사 화면의 설정에서 **현재 재생 곡 설정**을 열거나 제목·아티스트 영역을 길게 눌러 언어·가사 제공자·수동 검색·싱크를 조절하세요. 공통 기능과 비용·데이터 안내는 [Android README](../README.md#번역과-발음-설정하기)를 참고하세요.

- Bing·Google·DeepL·Local AI는 **번역 전용**입니다. 발음을 생성하려면 지원하는 AI 제공자의 연결 정보·모델과 발음 표시를 설정하세요.
- **Local AI를 먼저 쓰려면 온라인 제공자보다 앞에 배치**하세요. 켜는 것만으로 우선하지 않으며, 기기 내 가사 번역만 원하면 다른 온라인 번역 제공자를 모두 꺼야 합니다.
- Local AI에는 Android 9 이상·64비트 ARM 프로세스·모델 다운로드와 충분한 메모리가 필요합니다. LSPatch 등 호스트 환경에서 엔진 로딩이 실패할 수 있습니다. 가사 조회·모델 다운로드·다른 온라인 기능까지 오프라인이 되는 것은 아닙니다.
- 모듈의 설정·캐시·모델은 Spotify 쪽 저장 공간에 보관되며 독립 앱과 자동 공유되지 않습니다.
- 직접 가진 LRC/TXT 파일은 **설정 → 현재 재생 곡 설정 → 가사 제공자 → 가사 파일 불러오기**에서 현재 곡에 추가할 수 있습니다. 온라인 가사보다 우선 표시되며, 같은 메뉴에서 삭제할 수 있습니다. [파일 형식과 저장 안내](../README.md#내-lrc-파일로-가사-추가하기)를 참고하세요.

### Spotify 9.1.80.2221에서 사용할 수 있는 기능

- 곡 제목 위 한 줄 가사도 ivLyrics를 사용합니다. Spotify의 **Lyrics · On/Off** 설정으로 표시 여부를 바꾸고, 가사를 누르면 전체 가사를 엽니다.
- 지원 곡의 전체 가사 오른쪽 아래에는 Spotify 기본 SingAlong의 마이크 버튼이 나타납니다. **OFF → LOW → HIGH → OFF**로 바뀌며 화면을 터치하면 나타났다가 3초 뒤 숨습니다. Spotify가 해당 곡을 지원해야 하며 온라인·기기 내 재생·오디오 품질 조건을 따릅니다. LOW 오디오 품질에서는 사용할 수 없습니다. 기능 플래그나 곡 지원 여부를 해제하는 기능은 아닙니다.

이 두 기능을 9.1.42.2058이나 독립 앱에서도 사용할 수 있다는 뜻은 아닙니다.

## 업데이트와 문제 해결

**Spotify를 강제 종료한 뒤 업데이트**하세요. LSPosed는 같은 서명의 새 모듈 APK를 설치하고 활성화·범위를 확인한 뒤 재시작하세요. LSPatch에서 모듈을 내장했다면 새 모듈로 Spotify를 **다시 패치**해야 합니다. 설치된 모듈을 참조하는 방식은 해당 모듈을 업데이트하고 관리자 설정을 확인하세요. Spotify 자체를 업데이트할 때도 지원 버전인지 확인하고, LSPatch라면 패치를 다시 적용해야 합니다.

| 이런 문제가 있나요? | 확인할 내용 |
| --- | --- |
| 설치했는데 변화가 없습니다 | 독립 앱이 아닌 모듈 APK인지, Spotify 버전이 정확한지, 로더 활성화·범위 또는 패치에 모듈 포함이 완료됐는지 확인하세요. 모듈 설치만으로 패치되지는 않습니다. |
| 앱이 설치되지 않거나 업데이트가 거부됩니다 | 기존 설치본과 새 파일의 서명을 확인하세요. 모듈 자체의 서명과 패치된 Spotify의 서명은 별개입니다. 로컬 debug 빌드와 배포 모듈의 키가 다를 수도 있습니다. |
| 가사가 없거나 잘못 나옵니다 | 인터넷·선택한 가사 제공자·수동 검색을 확인하고 현재 곡 캐시를 새로 불러오세요. |
| 마이크 버튼이 없습니다 | 9.1.80.2221과 Spotify의 곡·재생 조건을 확인하세요. 지원 확인 중이거나 미지원 곡에서는 숨습니다. |
| Local AI가 실패합니다 | 프로세스의 64비트 지원, 모델 다운로드와 메모리를 확인하고 테스트하세요. 로컬 번역만 선택했다면 온라인 제공자로 자동 해결되지 않습니다. |
| Spotify가 열리지 않습니다 | 모듈/로더 활성화를 해제해 다시 실행하고 로더 로그를 확인하세요. |

[Discord](https://discord.gg/2fu36fUzdE)에 모듈·Spotify·Android 버전, 기기, **LSPatch/LSPosed와 패치 방식**, 재현 순서와 오류를 남겨 주세요. 인증 정보는 가려 주세요. [Android README의 크레딧과 면책 조항](../README.md#면책-조항)도 적용됩니다.

## English

This **ivLyrics Android module**, shown in the loader as **ivLyrics for Spotify**, replaces Spotify's lyrics card and full lyrics page. For a separate lyrics app, use the [standalone Android app](../README_EN.md).

Download **`ivLyrics-LSPatch-v<version>.apk`** from the [latest release](https://github.com/ivLis-Studio/ivLyrics-Android/releases/latest). It is a module, with no standalone lyrics launcher. It contains neither Spotify nor a prepatched Spotify APK. `ivLyrics-Android-v<version>-release.apk` is the separate app.

### Requirements and installation

Only Spotify **9.1.80.2221 / 9.1.42.2058** are recognized. Other versions are left without UI hooks. Obtain a supported Spotify APK lawfully yourself; it is not shipped here. Recognition does not guarantee every device, OS, or loader combination. The physical-device baseline is rooted LSPosed. [Official LSPatch](https://github.com/LSPosed/LSPatch) is archived and is a legacy route, without an assurance of compatibility with current Android or Spotify.

For **LSPatch** (Android 9+, no root):

1. Get `manager.apk` from [official LSPatch releases](https://github.com/LSPosed/LSPatch/releases), the ivLyrics module APK, and a supported Spotify APK. Follow the [manager's instructions](https://github.com/LSPosed/LSPatch#usage).
2. Start a new patch and select the **Spotify APK as the target**.
3. Add **`ivLyrics-LSPatch-v<version>.apk` as a module**. Follow your manager's instructions for embedded/integrated modules or references to installed modules.
4. Complete the patch and install the **resulting Spotify APK**, which is different from the module download.
5. Force-stop that Spotify app, reopen it, sign in, and play music.

A patched Spotify APK may have a different signature from your existing install, preventing an in-place update. Check before removing Spotify: removal can erase its local settings/downloads and the module settings/cache stored inside it. Keep the same patch signing key for future updates.

For **rooted LSPosed**: force-stop Spotify, install the module APK, enable **ivLyrics for Spotify**, and select **`com.spotify.music`** as its scope. Complete any restart required by the loader. This route does not require repatching Spotify. The module APK requires Android 8.0+, with additional loader/Spotify requirements.

The standalone app, its notification/overlay permissions, and its Client ID/Secret setup are not required for the module. Playback and metadata come from Spotify itself.

### Your first song and settings

Play a music track, then open full lyrics from Spotify's lyrics card. The module can add a card even without Spotify-hosted lyrics, but ivLyrics still needs lyric data. Episodes and podcast-embedded tracks are excluded.

Open **Current track settings** or long-press the title/artist area for language, providers, manual search, and timing. See the [Android guide](../README_EN.md#translation-and-pronunciation) for common settings, API costs, and data use.

Bing, Google, DeepL, and Local AI are **translation-only**; pronunciation needs a configured AI provider and model. **Move Local AI ahead of online providers to try it first; disable all other online translation providers for local-only lyric translation.** Enabling it alone does not change priority. Local AI needs Android 9+, a 64-bit ARM process, a downloaded model, and enough memory; engine loading may fail under LSPatch or another host configuration. Lyric lookup, downloads, and other online features still use the internet. Module settings, caches, and models are stored within Spotify and are separate from the standalone app.

On **9.1.80.2221**, the lyric preview above the title uses ivLyrics and follows Spotify's **Lyrics · On/Off** option. Tap it for full lyrics. Supported songs can also show Spotify's native SingAlong microphone button at the lower right of full lyrics: **OFF → LOW → HIGH → OFF**. It appears on entry/touch and hides after three idle seconds. Spotify's track support, online mode, local playback, and audio-quality restrictions apply; LOW audio quality is excluded. It does not unlock feature flags or unsupported tracks. These features are not promised for 9.1.42.2058 or the standalone app.

To use your own LRC/TXT file, open **Settings → Current track settings → Lyrics Providers → Import lyrics file**. It takes priority over online lyrics and can be removed in the same menu. See the [file format and storage guide](../README_EN.md#add-lyrics-from-your-own-lrc-file); module imports are stored inside Spotify, separately from the standalone app.

### Updates and help

Force-stop Spotify before updating. With LSPosed, install the same-signed module update, verify enablement/scope, and restart. With an embedded LSPatch module, **repatch Spotify with the new module**. If the loader references an installed module, update it and check the manager configuration. Spotify updates also need a supported version and, for LSPatch, a fresh patch.

If nothing changes, check the exact Spotify version and loader activation/scope or module inclusion in the patch. For install conflicts, distinguish the module APK's signing key from the patched host's key; local debug builds may differ from published modules. For missing lyrics, try another provider/manual search. For a missing microphone button, check track/playback eligibility. If Spotify will not open, disable the module/loader activation and check loader logs.

Ask on [Discord](https://discord.gg/2fu36fUzdE), including module/Spotify/Android versions, device, loader and patch mode, reproduction steps, and errors, with credentials redacted. The [Android credits and disclaimer](../README_EN.md#disclaimer) apply here too.

<details>
<summary>Integration behavior, development, and verification references</summary>

Both products use `:shared` for lyrics, ivSync, translation, caching, and rendering. The module preserves Spotify's package identity and uses its login; LSPatch itself repackages/signs the host, whereas rooted LSPosed does not require replacing the host APK.

Music tracks get at most one lyrics card, including when Spotify omits its lyrics card or reports no lyrics. Episodes and podcast-embedded tracks stay excluded.

On 9.1.80.2221, the inline preview shares the card's lyrics/translations, merges simultaneous vocals into one row, follows ivLyrics alignment, and scrolls long rows with playback timing. The native Lyrics On/Off menu remains available without Spotify-hosted lyrics and preserves its saved state. Canvas/video-player restrictions still apply; DJ/automix music can show the preview while DJ speech stays hidden. The Now Playing audio/video switch is hidden. See [INLINE_MAPPING.md](INLINE_MAPPING.md) for native menu gates and regression coverage.

The SingAlong button is beside the full-page provider credits. It stays hidden while eligibility is being checked or when the track is unsupported, and follows Spotify's native feature requirements.

Build from the repository root with `./build.sh :spotify-module:assembleRelease`; output is `spotify-module/build/outputs/apk/release/spotify-module-release.apk`. Releases publish it as `ivLyrics-LSPatch-${tag}.apk`. Both APKs take their version from root `gradle.properties`. Publishing requires the existing `IVLYRICS_RELEASE_*` signing configuration; local module builds without it use the debug key. Keep the same key for module updates. See [architecture and signing](../ARCHITECTURE.md#build-and-verify), [common verification](../tests/README.md), and [embedded/inline device checks](../tests/EMBEDDED_INLINE_SMOKE.md).

[ARCHITECTURE.md](../ARCHITECTURE.md) also covers host interfaces, resource context, private WebKit, build outputs, and editing boundaries. No application-source copying or replacement build step is used. These developer/test documents remain separate from the installation guide.

</details>
