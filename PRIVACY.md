# ivLyrics Privacy Policy

Effective November 28, 2026 · ivLyrics for Android (kr.ivlis.ivlyricsandroid), by ivLis STUDIO

[한국어](#개인정보-처리방침)

## At a glance

- **ivLyrics has no ads or tracking of its own.** It has no advertising, analytics, telemetry or crash-reporting SDKs. Embedded content and online services handle requests under their own policies.
- **You can start without an account.** Search for a song by title and artist, or connect Spotify to follow the song you are playing.
- **Online features send the data they need.** Lyric services receive song details. Online translation and AI can also receive lyrics, including text from a lyric file you imported.

## What stays on your device

- The app keeps settings, imported LRC and text lyrics, lyric and timing caches, language settings, translations, pronunciation, song research, background-video choices and timing offsets in its own storage. This includes the audio device label or local identifier used to remember Bluetooth timing offsets. Downloaded AI models also stay in app storage.
- Your Spotify Client Secret, AI API keys and Pollinations access token are stored in encrypted app preferences protected by Android Keystore. Your Spotify Client ID and the temporary Spotify Web API token are kept in private app preferences.
- The app keeps a short diagnostic log on your device while it runs. Web content can store cookies and site data in Android WebView.

You can clear lyric and AI caches in Settings, remove an imported lyric from the song’s settings, remove a downloaded model, and clear saved credentials. Clearing app storage in Android Settings or uninstalling removes app-local data. Files you originally selected from your own storage remain there.

## What is sent, and to whom

### Song information for lyrics, timing and videos

To find lyrics, the app sends the title and artist you enter or the song details from Spotify. Depending on the service, the request can include album, duration, Spotify track ID, ISRC (the recording’s identifier) or a provider’s catalog ID.

- **ivLyrics services** (lyrics.api.ivl.is and ivlis.kr) supply community timing, the OpenDB index and background-video matches. OpenDB is downloaded and checked on the device. A timing request can also submit song details for storage in the recording catalog. The catalog entry is keyed to the recording, not to you. To find a background video, the ivLyrics video service searches for a matching YouTube video with the song title and artist and can save the match for that recording.
- **Lyric providers** include LRCLIB, lrc.red, Open Lyrics, LyricsPlus and Unison, as enabled in Settings. Open Lyrics connects to several public lyric servers published on GitHub. LyricsPlus can use mirrors. You can also enter a custom LRCLIB server for playback lookups. When a recording needs word timing, the app can ask lrc.red to generate it using the recording’s ISRC.
- **Creator credits** can include public contributor information. The app can look up a contributor’s supporter status through discord.ivl.is and open their public profile through ivLyrics. These requests use the contributor’s identifier.

### Spotify and notification access (optional)

If you connect Spotify, Android’s notification access lets ivLyrics find Spotify’s media session. The app reads playback state and song metadata, including track details and artwork, to follow the song, line up lyrics and provide playback controls. It uses Spotify notifications to refresh the session. It ignores notifications from other apps and does not send notification text or messages.

The Spotify Client ID and Client Secret you enter go directly to Spotify Accounts for an app access token. Spotify’s Web API receives track IDs or song searches to return catalog details such as ISRC and artwork. Artwork can also load from Spotify’s image service or the URL supplied by the media session. Spotify’s own app handles playback and your Spotify account.

### Translation, pronunciation and AI features (optional)

- **Online translation and AI** send lyric text, requested languages and any song details the feature needs directly to the enabled provider. Options include Google Translate, Microsoft Bing Translate, DeepL, Google Gemini, OpenAI, Anthropic, Groq, OpenRouter, Perplexity, Pollinations and configured AI endpoints. When an enabled provider fails, another enabled provider can receive the same request. API keys and tokens go to the configured service to authenticate requests, including model lists and connection tests. Bing’s translation connection can reuse cookies and session values returned by Bing.
- **Song stories and research** send the song’s title, artist, album, recording identifiers when available, lyric text and requested language to the configured AI provider. The provider may search the web. The app caches results locally and can load thumbnails or images from YouTube or sites included in the results.
- **AI direction in Motion Lyrics**, when available with your AI settings, can send song title and artist, words, lyric timing, translation excerpts, language and visual or musical cues to your configured provider. The result is cached on your device.
- **Pollinations connection** can use an API key or an external browser authorization. The app exchanges a short-lived authorization code with Pollinations and uses the resulting token for the selected features and to check the connection.
- **Local AI** runs lyric translation on supported Android devices. The model download contacts Hugging Face and its download servers. Translation using the local model happens on the device. If online providers are enabled, they can still receive translation requests according to your provider order.
- **Pronunciation Lab and Japanese furigana** process text on your device. Pronunciation Lab downloads its code and language resources through jsDelivr. Furigana downloads the Kuromoji tokenizer and dictionary from jsDelivr or unpkg.

### Background videos, fonts and links

Optional music-video backgrounds use YouTube’s embedded player. YouTube receives requests for the selected video and related resources, and may handle viewing and browser data under its own policy. On supported devices, its player can send the app’s package name, version, signing certificate and a device verification token to Google to verify the app and device. Motion Lyrics may fetch fonts from Google Fonts or jsDelivr and request cover artwork. Public creator profiles open in a limited in-app browser at lyrics.ivl.is. Other links, such as research sources and provider setup pages, can open in your browser or the relevant app.

### Network data, retention and deletion

Online services see the IP address used for the connection. Embedded providers such as YouTube may use it to estimate your general location. Requests can also include ordinary technical details such as browser or app version, and language where needed. Every connection from the Google Play version uses HTTPS, including a custom LRCLIB server. The GitHub version can also reach a self-hosted LRCLIB server over HTTP, which sends the lookup without transport encryption, so choose a server you trust.

ivLyrics keeps app settings and imports until you remove them or clear app storage. The app can prune caches and lets you clear them yourself. The ivLyrics timing service can retain recording catalog entries for future lookups. Other services handle their own requests, logs and retention under their own policies. Clearing local data does not delete copies that those services retain. Use the contact below for questions about data held by ivLyrics, or contact the relevant provider about data it holds.

## Versions from Google Play and GitHub

The Google Play version provides lyric search, optional Spotify playback following and the display features above. The standalone GitHub version adds these features:

- **APK updates.** It can check GitHub Releases for the latest version and download an APK when you choose to update. GitHub receives the request and app version. Installing the update uses Android’s permission to install apps from this source.
- **Optional Discord sign-in.** Discord and ivLyrics services handle sign-in, including your Discord ID, display name, picture, and authorization and session data. You can manage creator privacy and, where available, back up settings and submit video timing. A cloud backup sends supported settings, app version and a generated backup device ID. It excludes API keys and Spotify credentials. A video submission sends track and video details, including the video’s timing offset, and is associated with your signed-in account. Shared timing can be displayed to other users. You can delete an existing cloud settings backup through the cloud settings controls.
- **Optional Spotify shortcut.** An Android accessibility service detects when the Spotify playback screen is in front. The display-over-other-apps permission lets ivLyrics show a shortcut bubble on that screen. Screen detection for the shortcut happens on the device.

## Children

ivLyrics is made for a general music audience. It is not directed at children, and we do not knowingly collect personal information from children. Lyrics and videos can contain explicit material.

## Changes

We will post changes to this privacy policy on this page.

## Contact

For privacy questions or requests, open an issue in the [ivLyrics Android issue tracker](https://github.com/ivLis-Studio/ivLyrics-Android/issues). Issues are public. Keep API keys, tokens and other private details out of your post.

---

# 개인정보 처리방침

시행일 2026년 11월 28일 · ivLis STUDIO의 Android용 ivLyrics (kr.ivlis.ivlyricsandroid)

## 한눈에 보기

- **ivLyrics 자체 광고나 추적은 없어요.** 광고, 사용 분석, 원격 측정, 오류 보고용 SDK를 쓰지 않아요. 앱에 삽입된 콘텐츠와 온라인 서비스에는 각 서비스의 정책이 적용돼요.
- **계정 없이 시작할 수 있어요.** 곡 제목과 아티스트로 검색하거나, Spotify를 연결해 지금 듣는 곡의 가사를 볼 수 있어요.
- **온라인 기능은 필요한 정보만 보내요.** 가사 서비스에는 곡 정보를 보내요. 온라인 번역이나 AI 기능을 쓰면 직접 가져온 파일의 가사도 해당 서비스로 갈 수 있어요.

## 내 기기에 저장되는 것

- 설정, 직접 가져온 LRC·텍스트 가사, 가사·싱크 캐시, 언어 설정, 번역, 발음, 곡 이야기, 배경 영상 선택과 싱크 보정값은 앱 저장소에 저장돼요. Bluetooth 기기별 싱크를 기억하는 데 쓰는 오디오 기기 이름이나 로컬 식별자, 다운로드한 AI 모델도 여기에 저장돼요.
- Spotify Client Secret, AI API 키, Pollinations 액세스 토큰은 Android Keystore로 보호되는 암호화된 앱 설정에 저장돼요. Spotify Client ID와 임시 Spotify Web API 토큰은 앱 전용 설정에 저장돼요.
- 앱이 실행되는 동안 짧은 진단 기록을 기기에 보관해요. 웹 콘텐츠는 Android WebView에 쿠키와 사이트 데이터를 저장할 수 있어요.

설정에서 가사·AI 캐시를 지우거나, 곡 설정에서 가져온 가사를 삭제할 수 있어요. 내려받은 모델과 저장한 인증 정보도 지울 수 있어요. Android 설정에서 앱 저장 공간을 지우거나 앱을 삭제하면 앱 내부 데이터가 삭제돼요. 내 저장소에서 선택한 원본 파일은 그대로 남아요.

## 무엇을 어디로 보내나요

### 가사·싱크·영상을 찾기 위한 곡 정보

가사를 찾을 때 입력한 제목과 아티스트 또는 Spotify에서 읽은 곡 정보를 보내요. 서비스에 따라 앨범, 재생 시간, Spotify 트랙 ID, 녹음 식별 코드인 ISRC 또는 제공자의 곡 식별자를 함께 보낼 수 있어요.

- **ivLyrics 서비스**(lyrics.api.ivl.is, ivlis.kr)에서 커뮤니티 싱크, OpenDB 목록과 배경 영상 정보를 받아요. OpenDB 목록은 내려받아 기기에서 확인해요. 싱크를 조회할 때 곡 정보도 함께 보내 녹음별 목록에 저장할 수 있어요. 이 목록은 사용자가 아닌 녹음 식별자를 기준으로 관리해요. 배경 영상을 찾을 때는 ivLyrics 영상 서비스가 곡 제목과 아티스트로 어울리는 YouTube 영상을 찾고, 찾은 영상 정보를 그 곡에 연결해 저장할 수 있어요.
- **가사 제공자**는 설정에서 켠 LRCLIB, lrc.red, Open Lyrics, LyricsPlus, Unison 등이에요. Open Lyrics는 GitHub에 공개된 여러 가사 서버에 연결해요. LyricsPlus는 미러 서버를 이용할 수 있어요. 재생 중 가사 조회에 사용할 LRCLIB 서버를 직접 입력할 수도 있어요. 단어 싱크가 필요한 경우에는 lrc.red에 ISRC를 보내 생성을 요청할 수 있어요.
- **싱크 제작자 정보**에는 공개 프로필 정보가 포함될 수 있어요. 제작자의 식별자로 discord.ivl.is에서 후원 등급을 확인하고 ivLyrics의 공개 프로필을 열 수 있어요.

### Spotify와 알림 접근 (선택)

Spotify를 연결하면 Android 알림 접근 권한으로 Spotify 미디어 세션을 찾아요. 재생 상태, 곡 정보와 앨범 이미지를 읽어 현재 곡에 맞춰 가사를 표시하고 재생을 제어해요. Spotify 알림은 세션 정보를 새로 확인하는 데 쓰고, 다른 앱의 알림은 무시해요. 알림 본문이나 메시지는 전송하지 않아요.

입력한 Spotify Client ID와 Client Secret은 앱 액세스 토큰을 받기 위해 Spotify의 인증 서버로 직접 보내요. Spotify Web API에 트랙 ID나 곡 검색어를 보내 ISRC와 앨범 이미지 같은 정보를 받아요. 이미지는 Spotify 이미지 서버나 미디어 세션에서 알려 준 주소에서 불러올 수도 있어요. 음악 재생과 Spotify 계정 관리는 Spotify 앱에서 이루어져요.

### 번역·발음·AI 기능 (선택)

- **온라인 번역과 AI**를 쓰면 가사, 언어 설정과 기능에 필요한 곡 정보를 켜 둔 제공자에게 직접 보내요. Google 번역, Microsoft Bing 번역, DeepL, Google Gemini, OpenAI, Anthropic, Groq, OpenRouter, Perplexity, Pollinations, 직접 설정한 AI 서버 등을 쓸 수 있어요. 한 제공자가 실패하면 같은 요청을 다음으로 켜 둔 제공자에게 보낼 수 있어요. API 키나 토큰은 요청, 모델 목록 조회, 연결 테스트 등을 인증하기 위해 지정한 서비스로 보내요. Bing에 요청할 때는 Bing에서 받은 쿠키와 세션 값을 다시 사용할 수 있어요.
- **곡 이야기와 조사 기능**은 곡 제목·아티스트·앨범, 알 수 있는 곡 식별자, 가사와 출력 언어를 설정한 AI 제공자에게 보내요. 제공자가 웹을 검색할 수 있고, 앱은 결과를 기기에 캐시해요. YouTube나 조사 결과에 포함된 사이트에서 썸네일이나 이미지를 불러올 수도 있어요.
- **모션 가사의 AI 연출**을 사용할 수 있는 설정에서는 곡 제목·아티스트, 가사 단어와 싱크, 번역 일부, 언어, 시각적·음악적 특징을 설정한 AI 제공자에게 보낼 수 있어요. 결과는 기기에 캐시해요.
- **Pollinations 연결**에는 API 키나 외부 브라우저 인증을 쓸 수 있어요. 앱은 유효 시간이 짧은 인증 코드를 Pollinations에 보내 토큰을 받아요. 이 토큰은 선택한 기능과 연결 상태 확인에 사용해요.
- **로컬 AI**는 지원하는 Android 기기에서 가사를 번역해요. 모델을 받을 때는 Hugging Face와 다운로드 서버에 연결해요. 로컬 모델로 번역할 때 가사는 기기에서 처리돼요. 온라인 제공자가 켜져 있으면 설정한 순서에 따라 온라인 서비스에도 번역을 요청할 수 있어요.
- **Pronunciation Lab과 일본어 후리가나**는 텍스트를 기기에서 처리해요. Pronunciation Lab의 코드와 언어 자료는 jsDelivr에서 받고, 후리가나에 쓰는 Kuromoji 분석기와 사전은 jsDelivr 또는 unpkg에서 받아요.

### 배경 영상·글꼴·링크

선택한 뮤직비디오 배경은 YouTube의 삽입 플레이어로 재생해요. YouTube에는 선택한 영상과 관련 리소스 요청이 전달돼요. YouTube는 자체 정책에 따라 시청 관련 정보와 브라우저 데이터를 처리할 수 있어요. 지원하는 기기에서는 앱과 기기를 확인하기 위해 앱 패키지 이름, 버전, 서명 인증서와 기기 검증 토큰이 YouTube 플레이어에서 Google로 전송될 수 있어요. 모션 가사는 Google Fonts 또는 jsDelivr에서 글꼴을 받거나 앨범 이미지를 불러올 수 있어요. 공개 제작자 프로필은 앱 내 브라우저에서 lyrics.ivl.is로 열어요. 조사 출처나 제공자 설정 페이지 등 다른 링크는 브라우저나 관련 앱에서 열 수 있어요.

### 연결 정보, 보관 및 삭제

온라인 서비스에는 연결에 쓰인 IP 주소가 전달돼요. YouTube 같은 삽입 콘텐츠 제공자는 IP 주소로 대략적인 위치를 추정할 수 있어요. 요청에는 브라우저나 앱 버전, 필요한 경우 언어 같은 일반적인 연결 정보도 포함될 수 있어요. Google Play 버전은 직접 지정한 LRCLIB 서버를 포함해 모든 연결에 HTTPS를 사용해요. GitHub 버전은 직접 운영하는 LRCLIB 서버에 HTTP로도 연결할 수 있는데, 이때는 조회 정보가 전송 중에 암호화되지 않으니 신뢰하는 서버를 사용해 주세요.

앱 설정과 가져온 가사는 직접 삭제하거나 앱 저장 공간을 지울 때까지 기기에 보관돼요. 캐시는 앱에서 자동 정리될 수 있고 직접 지울 수도 있어요. ivLyrics 싱크 서비스는 다음 조회에 쓸 곡 정보를 저장할 수 있어요. 외부 서비스는 자체 정책에 따라 요청과 로그를 처리하고 보관해요. 기기에서 데이터를 지워도 외부 서비스에 남은 정보는 삭제되지 않아요. ivLyrics가 보관하는 정보는 아래 연락처로, 외부 서비스가 보관하는 정보는 해당 제공자에게 문의해 주세요.

## Google Play 버전과 GitHub 버전

Google Play 버전에서는 가사 검색, 선택 사항인 Spotify 연결, 위에서 설명한 가사 표시 기능을 쓸 수 있어요. GitHub에서 배포하는 독립 앱에는 다음 기능이 추가돼요.

- **APK 업데이트.** GitHub Releases에서 최신 버전을 확인할 수 있고, 업데이트를 선택하면 APK를 내려받아요. GitHub에는 요청과 앱 버전이 전달돼요. 업데이트를 설치할 때는 이 출처의 앱 설치를 허용하는 Android 권한이 사용돼요.
- **Discord 로그인 (선택).** Discord와 ivLyrics 서비스가 Discord ID, 표시 이름, 프로필 사진과 인증·세션 정보를 처리해요. 제작자 공개 설정을 관리하고, 이용 조건에 따라 설정을 백업하거나 영상 싱크를 제출할 수 있어요. 클라우드 백업에는 지원되는 설정, 앱 버전과 백업용으로 생성한 기기 ID가 전송돼요. API 키와 Spotify 인증 정보는 백업에 들어가지 않아요. 영상 싱크를 제출하면 곡·영상 정보와 영상 시작 위치가 로그인한 계정과 함께 처리돼요. 공유된 싱크는 다른 사용자에게 표시될 수 있어요. 클라우드 설정 메뉴에서 저장된 백업을 삭제할 수 있어요.
- **Spotify 바로가기 (선택).** Android 접근성 서비스로 Spotify 재생 화면이 앞에 있는지 확인해요. 다른 앱 위에 표시 권한으로 해당 화면에 바로가기 아이콘을 띄워요. 바로가기에 필요한 화면 감지는 기기에서 이루어져요.

## 어린이

ivLyrics는 일반 음악 청취자를 위한 앱이에요. 어린이를 대상으로 하지 않으며, 어린이의 개인정보를 고의로 수집하지 않아요. 가사와 영상에는 자극적인 표현이 포함될 수 있어요.

## 변경 사항

개인정보 처리방침이 바뀌면 이 페이지에 새 내용을 게시해요.

## 문의

개인정보 관련 문의나 요청은 [ivLyrics Android 이슈 페이지](https://github.com/ivLis-Studio/ivLyrics-Android/issues)에 남겨 주세요. 이슈는 공개돼요. API 키, 토큰이나 다른 개인정보는 게시하지 말아 주세요.
