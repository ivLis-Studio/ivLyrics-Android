<img width="2208" height="512" alt="ivLyrics 한국어 배너" src="https://github.com/ivLis-Studio/ivLyrics/blob/main/preview_readme_ko.png?raw=true" /><br>
PLATFORM : <a href="https://github.com/ivLis-Studio/ivLyrics">ivLyrics (PC)</a> | <a href="https://github.com/ivLis-Studio/ivLyrics-Android">Android</a> | <a href="https://github.com/ivLis-Studio/ivLyrics-IOS">iOS</a><br>
MODULE : <a href="https://github.com/ivLis-Studio/ivLyrics-overlay">Overlay</a> | <a href="https://github.com/ivLis-Studio/ivLyrics-helper">Helper</a>

# ivLyrics Android

한국어 | [English](README_EN.md)

Spotify로 듣는 음악을 가사와 함께 즐겨 보세요. ivLyrics Android는 재생 중인 곡에 맞춰 가사, 번역과 따라 읽기 위한 발음을 표시합니다. 별도 앱에서 크게 보거나, 모듈을 사용해 Spotify 안에서 가사를 볼 수 있습니다. 음악 재생은 Spotify가 담당합니다.

[최신 버전 다운로드](https://github.com/ivLis-Studio/ivLyrics-Android/releases/latest) · [릴리스 내역](https://github.com/ivLis-Studio/ivLyrics-Android/releases) · [Discord 도움받기](https://discord.gg/2fu36fUzdE)

## 어떤 방식으로 사용할까요?

GitHub Releases의 Assets에는 **서로 다른 두 APK**가 있습니다. 버전 번호가 같아도 용도가 다릅니다. 이 안내는 APK 직접 설치를 기준으로 합니다.

| 파일 이름 | 사용 방법 |
| --- | --- |
| `ivLyrics-Android-v<버전>-release.apk` | Spotify와 별도로 여는 **독립 앱**입니다. 루팅이나 패치가 필요하지 않습니다. 아래 설치 안내를 따라 주세요. |
| `ivLyrics-LSPatch-v<버전>.apk` | Spotify 안에 가사를 표시하는 **모듈**입니다. LSPosed에서 활성화하거나 LSPatch로 Spotify에 적용해야 합니다. [모듈 설치 안내](spotify-module/README.md)를 따라 주세요. |

모듈 APK에는 Spotify나 미리 패치한 Spotify가 포함되어 있지 않습니다. 모듈 APK만 설치해도 Spotify 가사가 바뀌지는 않습니다.

## 어떤 가사를 볼 수 있나요?

- ivLyrics 커뮤니티 싱크 데이터와 LRCLIB, Lyrically (Paxsenix), LyricsPlus, Unison 등의 가사를 사용하며, 제공자와 조회 순서를 선택할 수 있습니다.
- 원본 데이터에 따라 일반 가사, 줄 단위 싱크, 단어·글자 단위 노래방 가사를 표시합니다. 채워짐 효과, 보컬별 색상과 간주 표시를 조절할 수 있습니다.
- 번역과 발음을 따로 켜고 곡 언어별로 규칙을 저장할 수 있습니다. 일본어 후리가나도 지원합니다.
- 독립 앱에서는 전체 가사, 가로 화면, 화면 속 화면(PiP), LP 모드와 Spotify에서 돌아오는 플로팅 바로가기를 사용할 수 있습니다.

모든 곡에 가사나 정밀 싱크가 있는 것은 아닙니다. 가사·번역의 품질과 사용 가능 여부는 데이터와 제공자, 네트워크 상태에 따라 달라집니다.

## 독립 앱 설치하기

준비물은 **Android 8.0 이상**, 기기에 설치하고 로그인한 Spotify, 인터넷 연결, 본인이 관리하는 [Spotify 개발자 앱](https://developer.spotify.com/dashboard)의 **Client ID와 Client Secret**입니다. 현재 독립 앱의 첫 설정에는 두 값과 **알림 접근 권한**이 필요합니다. Spotify나 설치 도구에는 별도의 OS·계정 조건이 적용될 수 있습니다.

Spotify Web API의 현재 [개발 모드 규정](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)은 앱 소유자의 Premium을 요구하고 인증 사용자를 제한합니다. 개발자 앱을 만들기 전에 조건을 확인하세요.

1. [최신 릴리스](https://github.com/ivLis-Studio/ivLyrics-Android/releases/latest)에서 `ivLyrics-Android-v<버전>-release.apk`를 받으세요.
2. APK를 열어 설치하세요. Android가 요청하면 다운로드에 사용한 브라우저나 파일 앱의 **알 수 없는 앱 설치**를 허용하세요.
3. ivLyrics를 실행하고 첫 설정에서 언어·테마를 선택하세요.
4. 안내에 따라 Android 설정에서 ivLyrics의 **알림 접근**을 허용한 뒤 앱으로 돌아오세요. 재생 곡과 Spotify 미디어 제어를 읽기 위한 권한입니다.
5. Spotify 개발자 앱에서 **Web API**를 선택해 발급받은 Client ID와 Client Secret을 입력하고 **저장하고 시작**을 누르세요. 앱 안의 등록 안내를 펼쳐 볼 수 있습니다.

> 앱 안의 Redirect URI 안내에는 `https://localhost/` 예시가 남아 있습니다. 현재 Spotify는 [Redirect URI에 `localhost`를 허용하지 않습니다](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri). Dashboard에서 요구하는 URI는 공식 규정에 맞게 등록하세요. 독립 앱의 현재 연결은 Client Credentials 방식이며 브라우저 콜백을 사용하지 않습니다. 이 예시 때문에 등록을 완료하지 못하면 Discord에 오류를 남겨 주세요.

### Obtainium으로 버전 확인하기

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/ivLis-Studio/ivLyrics-Android">
  <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Obtainium에서 받기" height="60">
</a>

Obtainium을 사용한다면 위 버튼으로 저장소를 추가할 수 있습니다. 릴리스에 APK가 둘 있으므로 **독립 앱 파일인 `ivLyrics-Android-…-release.apk`가 선택되도록 필터를 확인**하세요. 모듈 사용자는 아래 앱 업데이트 대신 [모듈 업데이트 안내](spotify-module/README.md)를 따라 주세요.

## 첫 곡의 가사 보기

1. Spotify에서 음악을 재생한 뒤 ivLyrics로 돌아오세요.
2. 곡 제목과 아티스트가 표시되면 가사 미리보기를 눌러 전체 가사를 여세요. 앨범 커버로 LP 모드도 열 수 있습니다.
3. **설정 → 현재 재생 곡 설정**에서 곡 언어·가사 제공자·수동 검색·싱크·배경을 바꿀 수 있습니다. 곡 제목·아티스트 영역을 길게 눌러도 곡 설정 메뉴가 열립니다.
4. 가사 줄을 누르거나 재생바를 움직이면 해당 위치로 이동합니다. 실제 제어 가능 여부는 Spotify의 재생 상태와 계정 제한을 따릅니다.

Spotify 화면의 플로팅 바로가기는 선택 기능입니다. **설정 → 고급**에서 **다른 앱 위에 표시**와 **Spotify 화면 감지 접근성 서비스**를 허용하세요. 접근성 서비스는 Spotify 재생 화면이 앞에 있는지 감지하는 데 사용합니다. 두 권한은 기본 가사 감상에 필수인 알림 접근 권한과 별개이며, 아이콘은 Spotify 재생 화면에서만 나타납니다.

## 내 LRC 파일로 가사 추가하기

곡을 재생한 뒤 **설정 → 현재 재생 곡 설정 → 가사 제공자 → 가사 파일 불러오기**에서 `.lrc` 또는 `.txt` 파일을 선택하세요. UTF-8 또는 UTF-16로 저장한 1MB 이하의 파일을 지원합니다. LRC의 시간 정보는 줄 단위 싱크에 사용됩니다.

파일은 선택을 시작한 곡에 저장되어 온라인 가사보다 먼저 표시되며, 앱을 다시 열거나 가사 캐시를 지워도 유지됩니다. 같은 메뉴의 **이 곡의 로컬 가사 삭제**를 누르면 온라인 가사를 다시 불러옵니다. LSPatch·LSPosed 모듈에서도 사용할 수 있으며, 독립 앱과 모듈에 가져온 파일은 서로 공유되지 않습니다.

## 번역과 발음 설정하기

설정에서 번역·발음의 표시와 출력 언어를 선택하고, **AI 제공자**에서 서비스를 켜고 순서를 정하세요. 실패하면 다음으로 켜 둔 제공자를 시도합니다. 곡 언어별 규칙을 사용하면 일본어 곡은 번역과 발음을, 영어 곡은 번역만 표시할 수 있습니다.

| 사용하고 싶은 기능 | 필요한 설정 |
| --- | --- |
| 가사 뜻 번역 | Bing Translate·Google Translate는 별도 API 키 없이 사용합니다. DeepL은 API 키가 필요합니다. 모두 번역 전용입니다. |
| AI 번역과 발음 생성 | Gemini, OpenAI 호환 API, Claude 등 지원하는 AI 제공자의 연결 정보와 모델을 설정하세요. 발음 생성에는 준비된 AI 제공자가 필요합니다. |
| 기기 안에서 가사 번역 | **Local AI**를 켜고 모델을 받으세요. 발음·곡 제목·아티스트 번역에는 사용하지 않습니다. |
| 일본어 한자 읽기 | 일본어 후리가나 표시를 켜세요. 뜻 번역이나 AI 발음 표시와는 별도입니다. |

Local AI는 기본적으로 꺼져 있으며 **Android 9 이상과 64비트 ARM 실행 환경**이 필요합니다. 품질은 **로컬 라이트 / 로컬 미디엄 / 로컬 하이**, 다운로드 크기는 각각 약 1.13 / 1.47 / 1.91 GB입니다. 모바일 기본값은 라이트입니다. 실행 중에는 추가 메모리를 사용하므로 고사양 기기에서 제공자 테스트부터 해 보세요. 모듈에서는 Spotify 프로세스와 로더 환경에 따라 엔진을 불러오지 못할 수 있습니다.

### 비용과 데이터는 어떻게 되나요?

온라인 번역·AI 제공자에는 가사와 요청에 필요한 곡 정보가 전송됩니다. 서비스마다 API 요금, 할당량과 데이터 처리 정책이 다릅니다. API 키 없이 쓰는 번역도 외부 서비스이며 항상 사용 가능하다는 보장은 없습니다. 생성된 번역과 발음에는 오류가 있을 수 있습니다.

**Local AI를 켜는 것만으로 먼저 사용되지는 않습니다. 우선 사용하려면 AI 제공자에서 온라인 제공자보다 앞에 배치하세요.** 앞에 있는 온라인 제공자는 먼저 요청을 받을 수 있습니다. **기기 내 가사 번역만 원하면 다른 온라인 번역 제공자를 모두 꺼 주세요.** 로컬 추론은 기기에서 처리하지만 모델 다운로드, 가사 조회와 다른 온라인 기능은 인터넷을 사용합니다. 앱 전체의 오프라인 동작이나 외부 전송 차단을 보장하지 않습니다.

번역과 발음은 캐시되며 설정에서 AI 캐시, 현재 곡 또는 전체 가사 캐시를 지울 수 있습니다. 독립 앱과 Spotify 모듈은 설정·캐시·다운로드 모델을 따로 보관합니다.

## 취향에 맞게 조절하기

- **외관**에서 글꼴·정렬·배경을, **플레이어**에서 가로 화면·PiP·LP 모드를 조절하세요. 제목을 누르는 기본 동작은 Spotify 열기이며 설정에서 바꿀 수 있습니다.
- 싱크는 전체 곡·현재 곡·Bluetooth 기기별로 보정할 수 있습니다. 10ms·50ms·100ms 단위로 조절하며 **+는 가사를 더 일찍, −는 더 늦게** 표시합니다.
- Discord 계정을 연결한 월간 후원자는 **고급**에서 Android 설정을 클라우드에 저장할 수 있습니다. API 키·계정 정보·곡별 오프셋·다운로드 모델은 동기화하지 않습니다.

## 업데이트와 문제 해결

독립 앱은 **설정 → 고급 → 업데이트 확인**, [GitHub Releases](https://github.com/ivLis-Studio/ivLyrics-Android/releases/latest) 또는 Obtainium에서 새 버전을 확인하세요. 같은 제품의 서명된 APK로 업데이트해야 합니다. 서명이 다르다는 오류가 나면 즉시 삭제하지 말고 설치 출처를 확인하세요. 삭제하면 로컬 설정과 캐시가 사라질 수 있습니다. 모듈 업데이트에는 로더 재시작이나 재패치가 필요하므로 [별도 안내](spotify-module/README.md)를 참고하세요.

| 이런 문제가 있나요? | 먼저 확인해 주세요 |
| --- | --- |
| 첫 설정을 끝낼 수 없습니다 | Client ID·Secret과 Web API 선택, 개발자 계정 조건, 알림 접근 권한을 확인하세요. Android가 권한을 차단하면 시스템에 표시된 제한 설정 안내도 확인하세요. |
| 곡이 감지되지 않습니다 | Spotify에서 실제 음악을 재생하고 알림 접근을 확인한 뒤 재생을 다시 시작하세요. |
| 가사·앨범 이미지가 없습니다 | 인터넷과 Spotify API 저장 결과를 확인하세요. 현재 곡 설정에서 다른 가사 제공자나 LRCLIB 수동 검색을 시도하세요. |
| 가사가 다른 곡이거나 싱크가 밀립니다 | 수동 검색·현재 곡 캐시 삭제·곡별 또는 Bluetooth 오프셋을 사용하세요. 원본 데이터에 정밀 싱크가 없을 수도 있습니다. |
| 플로팅 바로가기가 없습니다 | 다른 앱 위에 표시와 Spotify 감지 접근성 권한을 확인하고 Spotify 재생 화면을 여세요. |
| 번역은 나오는데 발음이 없습니다 | 번역 전용 제공자와 발음 생성은 별개입니다. AI 제공자의 키·모델과 곡 언어별 발음 설정을 확인하세요. |
| Local AI가 실패하거나 403·429가 나옵니다 | 로컬 테스트·기기 조건·모델 다운로드를 확인하세요. 온라인 오류는 계정 권한·할당량을 확인하고 잠시 후 다시 시도하세요. |

도움이 필요하면 [Discord](https://discord.gg/2fu36fUzdE)에 **독립 앱인지 모듈인지**, 앱·Spotify 버전, 기기·Android 버전, 곡 링크, 제공자, 재현 순서와 오류 문구를 남겨 주세요. API 키·Client Secret·인증 토큰은 가려 주세요.

## 함께 만든 서비스

ivLyrics 커뮤니티의 싱크 제작자와 기여자, [LRCLIB](https://lrclib.net/), Lyrically (Paxsenix), [LyricsPlus](https://github.com/ibratabian17/lyricsplus), [Unison](https://github.com/better-lyrics/unison) 및 번역 제공자에게 감사합니다. 가사 화면의 제공자·제작자 표기도 확인해 주세요. Local AI는 Tencent Hy-MT2와 llama.cpp를 사용합니다. 글꼴·라이브러리의 기존 라이선스 고지는 앱 리소스에 유지되어 있습니다.

<details>
<summary>개발·빌드·검증 자료</summary>

독립 앱과 모듈의 구조·빌드·서명은 [ARCHITECTURE.md](ARCHITECTURE.md), 공통 검증은 [tests/README.md](tests/README.md), Spotify 통합과 설치 제약은 [모듈 안내](spotify-module/README.md)를 참고하세요. 가사·번역·화면의 공통 구현은 `shared`에 있습니다. 개발·테스트 문서는 별도로 유지됩니다.

</details>

## 면책 조항

> **비공식 프로젝트 안내**
>
> 본 프로젝트와 기여자는 Spotify 또는 그 계열사 및 자회사와 제휴, 권한 부여, 승인 또는 공식적인 연결 관계가 없습니다. ivLyrics Android는 Android에서 ivLyrics 경험을 제공하기 위해 자원봉사 팀이 개발한 독립적이고 비영리적인 비공식 앱과 Spotify용 모듈입니다.
>
> **상표권 안내**
>
> “Spotify”를 포함한 관련 명칭, 마크, 엠블럼 및 이미지는 해당 소유자의 등록 상표입니다. 식별 및 참조 목적으로 사용하며 상표권자와의 연관성을 시사하지 않습니다. 상표권을 침해하거나 소유자에게 피해를 줄 의도가 없습니다.
>
> **책임의 한계**
>
> 앱과 모듈은 “있는 그대로(AS IS)” 제공되며 사용에 따른 위험은 사용자에게 있습니다. 개발자와 기여자는 사용 또는 관련 거래로 발생하는 청구, 손해, 법적 결과나 기타 책임을 지지 않으며, 사용 결과에 대한 책임은 사용자에게 있습니다.
>
> **저작권 및 약관 준수**
>
> 본 프로젝트는 가사, 번역문, 영상 등 제3자 콘텐츠의 소유권을 주장하거나 라이선스를 부여하지 않습니다. 사용자는 관련 저작권법, 플랫폼 정책, API 이용약관과 현지 법령을 확인하고 준수해야 하며, 저장·복제·배포·송신·상업적 이용에 대한 책임을 집니다.
