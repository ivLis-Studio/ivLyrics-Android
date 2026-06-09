# ivLyrics Android

한국어 | [English](README_EN.md)

Spotify에서 재생 중인 곡을 감지하고, ivLyrics 커뮤니티 싱크 데이터와 LRCLIB 가사를 이용해 Android에서 노래방 스타일 가사를 보여주는 앱입니다.

ivLyrics Android는 현재 듣고 있는 곡의 제목, 아티스트, 앨범, 재생 시간을 확인한 뒤 Spotify Web API로 ISRC와 앨범 이미지를 가져옵니다. 이후 ivLyrics 싱크 데이터와 LRCLIB 가사를 맞춰 글자 단위 싱크, 멀티 보컬 색상, 번역, 발음, 후리가나를 표시합니다.

## 주요 기능

- Spotify에서 재생 중인 곡 자동 감지
- Spotify Web API 기반 ISRC, 곡 정보, 고해상도 앨범 이미지 불러오기
- ivLyrics 커뮤니티 싱크 데이터 기반 노래방 가사
- LRCLIB 직접 불러오기 및 검색 fallback
- 글자 단위 채워짐, 통통 튀는 애니메이션, 멀티 보컬 색상 표시
- 원어, 발음, 번역, 일본어 후리가나 표시
- 곡 언어별 번역/발음 설정
- 메인 플레이어와 전체 가사 페이지
- 가로 화면 전용 플레이어 + 가사 분할 레이아웃
- Spotify 화면에서 ivLyrics로 바로 이동하는 플로팅 아이콘
- 현재 곡 또는 전체 곡의 가사 캐시 삭제

## 설치 전 확인

ivLyrics Android를 사용하려면 다음이 필요합니다.

- Android 8.0 이상 기기
- Spotify 앱
- 인터넷 연결
- Spotify Developer 계정
- 직접 발급한 Spotify Client ID와 Client Secret
- Android 알림 접근 권한
- 플로팅 아이콘을 사용하려면 다른 앱 위에 표시 권한

Spotify API 키는 앱 안에만 저장됩니다. 앱은 공용 토큰 서버를 사용하지 않으며, 사용자가 직접 만든 Spotify Developer 앱의 Client ID와 Client Secret으로 토큰을 발급받습니다.

## 설치

1. GitHub Releases에서 최신 APK를 다운로드합니다.
2. Android 기기에 APK를 설치합니다.
3. 설치 중 보안 안내가 나오면, APK를 받은 앱에 대해 "알 수 없는 앱 설치"를 허용합니다.
4. ivLyrics Android를 실행하고 첫 설정을 진행합니다.

최신 APK는 [Releases](https://github.com/ivLis-Studio/ivLyrics-Android/releases)에서 받을 수 있습니다.

## 처음 설정

앱을 처음 실행하면 순서대로 설정을 진행합니다.

### 1. 앱 언어 선택

사용할 앱 언어를 선택합니다. 이 설정은 나중에 설정 화면에서 다시 바꿀 수 있습니다.

### 2. 미디어 인식 권한 허용

ivLyrics Android는 Android의 미디어 알림을 통해 Spotify에서 현재 재생 중인 곡을 감지합니다.

권한 화면이 열리면 ivLyrics Android의 알림 접근 권한을 켜 주세요. 권한이 꺼져 있으면 현재 곡을 읽을 수 없습니다.

### 3. Spotify API 등록

ivLyrics Android는 Spotify 계정으로 로그인하지 않습니다. 대신 사용자가 직접 만든 Spotify Developer 앱의 Client ID와 Client Secret을 사용합니다.

Spotify Developer Dashboard에서 다음 순서대로 진행하세요.

1. [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)에 접속합니다.
2. Spotify 계정으로 로그인합니다.
3. `Create app`을 누릅니다.
4. 아래 값을 입력합니다.

| 항목 | 입력값 |
| --- | --- |
| App name | `trackinfo` |
| App description | `trackinfo` |
| Redirect URIs | `https://localhost/` |
| APIs used | `Web API` |

5. 동의 체크박스를 선택합니다.
6. `Save`를 누릅니다.
7. 생성된 앱의 `Client ID`를 복사합니다.
8. `View client secret`을 눌러 `Client Secret`을 복사합니다.
9. ivLyrics Android에 두 값을 붙여넣고 확인합니다.

Spotify 앱 이름에는 `ivLyrics` 또는 `ivlyrics`라고 적지 마세요. 개인이 직접 발급하는 조회용 앱이므로, 예시처럼 `trackinfo`를 사용하는 편이 혼동이 적습니다.

앱은 입력한 값으로 실제 토큰 발급이 가능한지 확인한 뒤 다음 단계로 넘어갑니다. 잘못된 값이면 다시 입력하라는 안내가 표시됩니다.

## 사용 방법

1. Spotify에서 음악을 재생합니다.
2. ivLyrics Android를 엽니다.
3. 곡이 감지되면 앨범 이미지, 제목, 아티스트, 가사가 표시됩니다.
4. 메인 화면 하단의 가사를 위로 올리면 전체 가사 페이지가 열립니다.
5. 전체 가사 페이지를 아래로 끌어내리면 메인 플레이어로 돌아갑니다.

가사 페이지에서는 현재 줄이 화면 중앙에 오도록 부드럽게 이동하며, 글자 단위 싱크가 있는 경우 노래방처럼 가사가 채워집니다. 싱크 데이터가 없는 곡은 LRCLIB의 줄 단위 가사로 표시됩니다.

## 가사 페이지 팁

- 곡 제목 또는 아티스트를 한 번 누르면 Spotify 앱으로 이동합니다.
- 가사 페이지에서 곡 제목과 아티스트 영역을 세 번 빠르게 누르면 가사 설정 메뉴가 열립니다.
- 가사를 누르면 해당 위치로 이동합니다.
- 재생바를 드래그하면 원하는 구간으로 이동합니다.
- 싱크가 맞지 않으면 메뉴에서 오프셋을 조절할 수 있습니다.
- LRCLIB 결과가 잘못 잡힌 경우 메뉴에서 수동 검색으로 다른 가사를 선택할 수 있습니다.

## 번역, 발음, 후리가나

ivLyrics Android는 곡 언어를 자동으로 감지하고, 언어별로 번역과 발음 표시 여부를 따로 저장합니다.

예를 들어 일본어 곡에서는 번역과 발음을 켜고, 영어 곡에서는 번역만 켜고, 스페인어 곡에서는 둘 다 끄는 식으로 사용할 수 있습니다. 자동 감지된 언어가 마음에 들지 않으면 가사 설정 메뉴에서 곡 언어를 직접 바꿀 수 있습니다.

일본어 곡은 옵션을 켜면 한자 위에 후리가나를 표시할 수 있습니다.

번역과 발음 데이터는 캐시됩니다. 한 번 생성된 데이터는 앱을 다시 열어도 유지되며, 필요하면 설정에서 현재 곡 또는 전체 곡의 캐시를 지울 수 있습니다.

## 설정에서 바꿀 수 있는 것

- 앱 언어
- 발음/번역 출력 언어
- 곡 언어별 번역, 발음, 후리가나 설정
- 메인 화면 하단 가사 표시 조합
- 글자 크기, 굵기, 폰트 관련 표시 설정
- 보컬 파트별 색상
- 배경 효과
- 가짜 노래방 효과
- 통통 튀는 글자 애니메이션
- 전주, 간주, 후주 표시
- 화면 꺼짐 방지
- Spotify 플로팅 아이콘
- 현재 곡 캐시 삭제
- 전체 가사 캐시 삭제

## 문제 해결

### 곡이 감지되지 않아요

- Spotify에서 음악이 실제로 재생 중인지 확인하세요.
- Android 설정에서 ivLyrics Android의 알림 접근 권한이 켜져 있는지 확인하세요.
- Spotify 알림이 표시되지 않는 상태라면 Spotify를 다시 열고 재생을 다시 시작해 보세요.

### 가사나 앨범 이미지가 불러와지지 않아요

- Spotify Client ID와 Client Secret이 올바른지 확인하세요.
- Spotify Developer Dashboard에서 Web API를 선택했는지 확인하세요.
- 인터넷 연결을 확인하세요.
- 설정에서 Spotify API 정보를 다시 저장해 보세요.

### 가사가 다른 곡으로 잡혀요

- 가사 페이지에서 곡 제목/아티스트 영역을 세 번 눌러 메뉴를 엽니다.
- LRCLIB 수동 검색을 실행합니다.
- 맞는 가사를 선택합니다.
- 필요하면 현재 곡 캐시를 삭제한 뒤 다시 불러오세요.

### 싱크가 조금 밀려요

- 가사 설정 메뉴에서 싱크 오프셋을 조절하세요.
- 10ms, 50ms, 100ms 단위로 미세 조정할 수 있습니다.

### 플로팅 아이콘이 보이지 않아요

- Android 설정에서 다른 앱 위에 표시 권한을 허용했는지 확인하세요.
- 플로팅 아이콘은 Spotify 재생 화면에서만 표시됩니다.
- Spotify가 백그라운드이거나 다른 화면에 있으면 표시되지 않을 수 있습니다.

## 데이터와 저작권

ivLyrics Android는 Spotify, LRCLIB, 가사 제공자와 공식 제휴된 앱이 아닙니다.

가사, 번역, 발음, 앨범 이미지, 싱크 데이터의 권리는 각 원 저작권자와 제공자에게 있습니다. 앱은 사용자의 기기에서 현재 재생 중인 곡을 기준으로 공개 API와 커뮤니티 데이터를 불러와 표시합니다.

이 앱을 사용할 때는 Spotify API 약관, LRCLIB 이용 조건, 각 국가의 저작권법을 준수해야 합니다.

## 크레딧

- ivLyrics sync-data 제작자
- LRCLIB
- Spotify Web API
- Pretendard

싱크 제작자 정보가 있는 곡은 가사 페이지 상단에 작게 표시됩니다. 제작자 이름을 누르면 해당 프로필 페이지를 앱 안에서 확인할 수 있습니다.
