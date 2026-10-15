package kr.ivlis.ivlyricsandroid;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** First-use copy, kept together so all 22 locales share the same short, purposeful flow. */
final class FirstUseI18n {
    static final String[] KEYS = {
            "onboarding.welcome_title", "onboarding.welcome_desc", "onboarding.dark_mode",
            "onboarding.permission_title", "onboarding.permission_desc", "onboarding.permission_hint",
            "onboarding.permission_status_enabled", "onboarding.permission_status_required",
            "onboarding.spotify_title", "onboarding.spotify_desc", "onboarding.spotify_help",
            "button.save_start", "first_language.title_format", "first_language.hint",
            "first_language.pronunciation_ai_provider_hint", "first_language.translation_provider_hint",
            "first_language.original", "first_language.apply"
    };

    private FirstUseI18n() {}

    static void apply(Map<String, Map<String, String>> languages) {
        String[][] rows = {
                {"ko", "언어와 화면", "편한 언어와 화면을 골라주세요. 나중에 설정에서 바꿀 수 있어요.", "어두운 화면",
                        "재생 정보 접근", "Spotify에서 재생 중인 곡을 읽으려면 Android 알림 접근 권한이 필요해요.", "Android 설정에서 ivLyrics의 알림 접근을 허용한 뒤 돌아와주세요.", "알림 접근이 허용됐어요", "알림 접근이 필요해요",
                        "연결하고 시작해요", "Spotify 곡 정보와 앨범 이미지를 불러올 개인 API 정보를 입력해주세요.", "API 정보 발급 안내", "연결하고 시작", "%s 가사", "이 언어의 노래에 적용할 항목을 골라주세요.",
                        "발음을 보려면 설정에서 AI 제공자를 연결해주세요.", "번역을 보려면 설정에서 번역 제공자를 켜주세요.", "원문만", "적용"},
                {"en", "Language and display", "Choose your language and appearance. You can change these later in Settings.", "Dark appearance",
                        "Playback access", "Android notification access lets ivLyrics read the song playing in Spotify.", "Allow notification access for ivLyrics in Android settings, then come back here.", "Notification access allowed", "Notification access needed",
                        "Connect and start", "Enter your own Spotify API credentials to load song details and album artwork.", "How to get API credentials", "Connect and start", "%s lyrics", "Choose what to show for songs in this language.",
                        "Connect an AI provider in Settings to show pronunciation.", "Enable a translation provider in Settings to show translations.", "Original only", "Apply"},
                {"zh-CN", "语言与显示", "选择习惯的语言和外观，之后可在设置中更改。", "深色外观",
                        "读取播放信息", "需要 Android 通知访问权限，ivLyrics 才能读取 Spotify 正在播放的歌曲。", "在 Android 设置中允许 ivLyrics 访问通知，然后返回这里。", "已允许访问通知", "需要通知访问权限",
                        "连接并开始", "输入自己的 Spotify API 凭据，以获取歌曲信息和专辑封面。", "如何获取 API 凭据", "连接并开始", "%s歌词", "选择这类语言的歌曲要显示哪些内容。",
                        "请在设置中连接 AI 提供商以显示发音。", "请在设置中启用翻译提供商以显示翻译。", "仅原文", "应用"},
                {"zh-TW", "語言與顯示", "選擇習慣的語言與外觀，之後可在設定中更改。", "深色外觀",
                        "讀取播放資訊", "需要 Android 通知存取權限，ivLyrics 才能讀取 Spotify 正在播放的歌曲。", "在 Android 設定中允許 ivLyrics 存取通知，然後返回這裡。", "已允許存取通知", "需要通知存取權限",
                        "連接並開始", "輸入自己的 Spotify API 憑證，以取得歌曲資訊與專輯封面。", "如何取得 API 憑證", "連接並開始", "%s歌詞", "選擇這個語言的歌曲要顯示哪些內容。",
                        "請在設定中連接 AI 供應商以顯示發音。", "請在設定中啟用翻譯供應商以顯示翻譯。", "僅原文", "套用"},
                {"ja", "言語と表示", "使いやすい言語と外観を選んでください。後から設定で変更できます。", "ダーク表示",
                        "再生情報へのアクセス", "Spotifyで再生中の曲を読み取るには、Androidの通知へのアクセスが必要です。", "Androidの設定でivLyricsの通知へのアクセスを許可してから、ここに戻ってください。", "通知へのアクセスは許可済みです", "通知へのアクセスが必要です",
                        "接続して始める", "曲の情報やアルバム画像を取得するため、ご自身のSpotify API認証情報を入力してください。", "API認証情報の取得方法", "接続して始める", "%sの歌詞", "この言語の曲で表示する項目を選んでください。",
                        "発音を表示するには、設定でAIプロバイダーを接続してください。", "翻訳を表示するには、設定で翻訳プロバイダーを有効にしてください。", "原文のみ", "適用"},
                {"hi", "भाषा और रूप", "अपनी भाषा और रूप चुनें। इन्हें बाद में सेटिंग में बदल सकते हैं।", "गहरा रूप",
                        "प्लेबैक की पहुँच", "Spotify में चल रहा गाना पढ़ने के लिए ivLyrics को Android सूचनाओं की पहुँच चाहिए।", "Android सेटिंग में ivLyrics को सूचनाओं की पहुँच दें, फिर यहाँ लौटें।", "सूचनाओं की पहुँच दी गई है", "सूचनाओं की पहुँच चाहिए",
                        "जुड़ें और शुरू करें", "गाने की जानकारी और एल्बम कवर पाने के लिए अपने Spotify API क्रेडेंशियल दर्ज करें।", "API क्रेडेंशियल कैसे पाएँ", "जुड़ें और शुरू करें", "%s के बोल", "इस भाषा के गानों के लिए क्या दिखाना है, चुनें।",
                        "उच्चारण दिखाने के लिए सेटिंग में AI प्रदाता जोड़ें।", "अनुवाद दिखाने के लिए सेटिंग में अनुवाद प्रदाता चालू करें।", "केवल मूल", "लागू करें"},
                {"es", "Idioma y apariencia", "Elige tu idioma y apariencia. Puedes cambiarlos después en Ajustes.", "Apariencia oscura",
                        "Acceso a la reproducción", "El acceso a las notificaciones de Android permite leer la canción que suena en Spotify.", "Permite a ivLyrics acceder a las notificaciones en los ajustes de Android y vuelve aquí.", "Acceso a notificaciones permitido", "Se necesita acceso a notificaciones",
                        "Conecta y empieza", "Introduce tus credenciales de la API de Spotify para cargar información y portadas.", "Cómo obtener credenciales de API", "Conectar y empezar", "Letra en %s", "Elige qué mostrar en las canciones de este idioma.",
                        "Conecta un proveedor de IA en Ajustes para mostrar la pronunciación.", "Activa un proveedor de traducción en Ajustes para mostrar traducciones.", "Solo original", "Aplicar"},
                {"fr", "Langue et apparence", "Choisissez votre langue et l’apparence. Vous pourrez les modifier dans les réglages.", "Apparence sombre",
                        "Accès à la lecture", "L’accès aux notifications Android permet de lire le morceau en cours dans Spotify.", "Autorisez l’accès aux notifications pour ivLyrics dans les réglages Android, puis revenez ici.", "Accès aux notifications autorisé", "Accès aux notifications nécessaire",
                        "Connecter et démarrer", "Saisissez vos identifiants d’API Spotify pour charger les détails des morceaux et les pochettes.", "Obtenir des identifiants d’API", "Connecter et démarrer", "Paroles en %s", "Choisissez les éléments à afficher pour les chansons dans cette langue.",
                        "Connectez un fournisseur d’IA dans les réglages pour afficher la prononciation.", "Activez un fournisseur de traduction dans les réglages pour afficher les traductions.", "Original uniquement", "Appliquer"},
                {"ar", "اللغة والمظهر", "اختر لغتك والمظهر المناسب لك. يمكنك تغييرهما لاحقًا في الإعدادات.", "المظهر الداكن",
                        "الوصول إلى التشغيل", "يتيح الوصول إلى إشعارات Android قراءة الأغنية التي تعمل في Spotify.", "اسمح لـ ivLyrics بالوصول إلى الإشعارات في إعدادات Android، ثم عد إلى هنا.", "الوصول إلى الإشعارات مسموح", "يلزم الوصول إلى الإشعارات",
                        "اتصل وابدأ", "أدخل بيانات اعتماد Spotify API الخاصة بك لتحميل تفاصيل الأغاني وصور الألبومات.", "كيفية الحصول على بيانات API", "اتصل وابدأ", "كلمات باللغة %s", "اختر ما تريد عرضه لأغاني هذه اللغة.",
                        "اربط موفّر ذكاء اصطناعي في الإعدادات لعرض النطق.", "فعّل موفّر ترجمة في الإعدادات لعرض الترجمات.", "الأصل فقط", "تطبيق"},
                {"fa", "زبان و ظاهر", "زبان و ظاهر دلخواهتان را انتخاب کنید. بعداً می‌توانید آن‌ها را در تنظیمات تغییر دهید.", "ظاهر تیره",
                        "دسترسی به پخش", "دسترسی به اعلان‌های Android امکان خواندن آهنگ در حال پخش در Spotify را می‌دهد.", "در تنظیمات Android دسترسی به اعلان‌ها را برای ivLyrics مجاز کنید و سپس برگردید.", "دسترسی به اعلان‌ها مجاز است", "دسترسی به اعلان‌ها لازم است",
                        "اتصال و شروع", "اطلاعات API شخصی Spotify خود را برای دریافت جزئیات آهنگ و تصویر آلبوم وارد کنید.", "روش دریافت اطلاعات API", "اتصال و شروع", "متن ترانه به %s", "انتخاب کنید برای آهنگ‌های این زبان چه چیزی نمایش داده شود.",
                        "برای نمایش تلفظ، در تنظیمات یک ارائه‌دهنده هوش مصنوعی متصل کنید.", "برای نمایش ترجمه، در تنظیمات یک ارائه‌دهنده ترجمه فعال کنید.", "فقط متن اصلی", "اعمال"},
                {"de", "Sprache und Darstellung", "Wähle Sprache und Darstellung. Du kannst beides später in den Einstellungen ändern.", "Dunkle Darstellung",
                        "Zugriff auf Wiedergabe", "Mit Android-Benachrichtigungszugriff kann ivLyrics den aktuellen Spotify-Titel erkennen.", "Erlaube ivLyrics den Benachrichtigungszugriff in den Android-Einstellungen und kehre zurück.", "Benachrichtigungszugriff erlaubt", "Benachrichtigungszugriff erforderlich",
                        "Verbinden und starten", "Gib deine eigenen Spotify-API-Zugangsdaten ein, um Titeldetails und Albumcover zu laden.", "API-Zugangsdaten erhalten", "Verbinden und starten", "Songtext auf %s", "Wähle, was bei Liedern in dieser Sprache angezeigt wird.",
                        "Verbinde in den Einstellungen einen KI-Anbieter, um die Aussprache anzuzeigen.", "Aktiviere in den Einstellungen einen Übersetzungsanbieter, um Übersetzungen anzuzeigen.", "Nur Original", "Anwenden"},
                {"ru", "Язык и оформление", "Выберите язык и оформление. Позже их можно изменить в настройках.", "Тёмное оформление",
                        "Доступ к воспроизведению", "Доступ к уведомлениям Android позволяет читать сведения о текущей песне в Spotify.", "Разрешите ivLyrics доступ к уведомлениям в настройках Android и вернитесь сюда.", "Доступ к уведомлениям разрешён", "Нужен доступ к уведомлениям",
                        "Подключение и запуск", "Введите свои данные API Spotify для загрузки сведений о песнях и обложек альбомов.", "Как получить данные API", "Подключить и начать", "Текст песни: %s", "Выберите, что показывать для песен на этом языке.",
                        "Подключите поставщика ИИ в настройках для отображения произношения.", "Включите поставщика перевода в настройках для отображения переводов.", "Только оригинал", "Применить"},
                {"sv", "Språk och utseende", "Välj språk och utseende. Du kan ändra dem senare i inställningarna.", "Mörkt utseende",
                        "Åtkomst till uppspelning", "Åtkomst till Android-aviseringar låter ivLyrics läsa låten som spelas i Spotify.", "Tillåt aviseringsåtkomst för ivLyrics i Android-inställningarna och kom sedan tillbaka.", "Aviseringsåtkomst tillåten", "Aviseringsåtkomst behövs",
                        "Anslut och börja", "Ange dina egna Spotify-API-uppgifter för att hämta låtinformation och omslag.", "Så får du API-uppgifter", "Anslut och börja", "Låttext på %s", "Välj vad som ska visas för låtar på det här språket.",
                        "Anslut en AI-leverantör i inställningarna för att visa uttal.", "Aktivera en översättningsleverantör i inställningarna för att visa översättningar.", "Endast original", "Tillämpa"},
                {"pt", "Idioma e aparência", "Escolha seu idioma e aparência. Você pode alterá-los depois nas configurações.", "Aparência escura",
                        "Acesso à reprodução", "O acesso às notificações do Android permite ler a música em reprodução no Spotify.", "Permita o acesso às notificações para o ivLyrics nas configurações do Android e volte aqui.", "Acesso às notificações permitido", "É necessário acesso às notificações",
                        "Conectar e começar", "Insira suas credenciais da API do Spotify para carregar detalhes e capas dos álbuns.", "Como obter credenciais de API", "Conectar e começar", "Letra em %s", "Escolha o que mostrar nas músicas deste idioma.",
                        "Conecte um provedor de IA nas configurações para mostrar a pronúncia.", "Ative um provedor de tradução nas configurações para mostrar traduções.", "Somente original", "Aplicar"},
                {"bn", "ভাষা ও চেহারা", "আপনার ভাষা ও চেহারা বেছে নিন। পরে সেটিংসে এগুলো বদলাতে পারবেন।", "গাঢ় চেহারা",
                        "প্লেব্যাকে প্রবেশাধিকার", "Spotify-এ চলা গান পড়তে ivLyrics-এর Android বিজ্ঞপ্তিতে প্রবেশাধিকার প্রয়োজন।", "Android সেটিংসে ivLyrics-কে বিজ্ঞপ্তিতে প্রবেশাধিকার দিয়ে এখানে ফিরে আসুন।", "বিজ্ঞপ্তিতে প্রবেশাধিকার দেওয়া হয়েছে", "বিজ্ঞপ্তিতে প্রবেশাধিকার প্রয়োজন",
                        "সংযুক্ত করে শুরু করুন", "গানের তথ্য ও অ্যালবামের ছবি পেতে আপনার নিজের Spotify API পরিচয়তথ্য লিখুন।", "API পরিচয়তথ্য পাওয়ার উপায়", "সংযুক্ত করে শুরু করুন", "%s গানের কথা", "এই ভাষার গানের জন্য কী দেখাবেন তা বেছে নিন।",
                        "উচ্চারণ দেখাতে সেটিংসে একটি AI প্রদানকারী যুক্ত করুন।", "অনুবাদ দেখাতে সেটিংসে একটি অনুবাদ প্রদানকারী চালু করুন।", "শুধু মূল", "প্রয়োগ করুন"},
                {"cs", "Jazyk a vzhled", "Vyberte si jazyk a vzhled. Později je můžete změnit v nastavení.", "Tmavý vzhled",
                        "Přístup k přehrávání", "Přístup k oznámením Androidu umožní číst skladbu přehrávanou ve Spotify.", "V nastavení Androidu povolte aplikaci ivLyrics přístup k oznámením a vraťte se sem.", "Přístup k oznámením povolen", "Je potřeba přístup k oznámením",
                        "Připojit a začít", "Zadejte vlastní přihlašovací údaje Spotify API pro načtení podrobností a obalů alb.", "Jak získat údaje API", "Připojit a začít", "Text písně: %s", "Vyberte, co zobrazovat u skladeb v tomto jazyce.",
                        "Pro zobrazení výslovnosti připojte v nastavení poskytovatele AI.", "Pro zobrazení překladů zapněte v nastavení poskytovatele překladů.", "Pouze originál", "Použít"},
                {"it", "Lingua e aspetto", "Scegli lingua e aspetto. Puoi cambiarli in seguito nelle impostazioni.", "Aspetto scuro",
                        "Accesso alla riproduzione", "L’accesso alle notifiche Android consente di leggere il brano in riproduzione su Spotify.", "Consenti a ivLyrics l’accesso alle notifiche nelle impostazioni Android, poi torna qui.", "Accesso alle notifiche consentito", "È necessario l’accesso alle notifiche",
                        "Connetti e inizia", "Inserisci le tue credenziali API di Spotify per caricare dettagli e copertine degli album.", "Come ottenere le credenziali API", "Connetti e inizia", "Testo in %s", "Scegli cosa mostrare per i brani in questa lingua.",
                        "Connetti un provider IA nelle impostazioni per mostrare la pronuncia.", "Attiva un provider di traduzione nelle impostazioni per mostrare le traduzioni.", "Solo originale", "Applica"},
                {"th", "ภาษาและรูปแบบ", "เลือกภาษาและรูปแบบที่ถนัด แล้วเปลี่ยนภายหลังได้ในการตั้งค่า", "รูปแบบมืด",
                        "การเข้าถึงการเล่น", "การเข้าถึงการแจ้งเตือนของ Android ช่วยให้ ivLyrics อ่านเพลงที่กำลังเล่นใน Spotify ได้", "อนุญาตให้ ivLyrics เข้าถึงการแจ้งเตือนในการตั้งค่า Android แล้วกลับมาที่นี่", "อนุญาตการเข้าถึงการแจ้งเตือนแล้ว", "ต้องเข้าถึงการแจ้งเตือน",
                        "เชื่อมต่อแล้วเริ่มใช้", "ป้อนข้อมูลรับรอง Spotify API ของคุณเพื่อโหลดข้อมูลเพลงและปกอัลบั้ม", "วิธีรับข้อมูลรับรอง API", "เชื่อมต่อแล้วเริ่มใช้", "เนื้อเพลงภาษา%s", "เลือกสิ่งที่ต้องการแสดงสำหรับเพลงภาษานี้",
                        "เชื่อมต่อผู้ให้บริการ AI ในการตั้งค่าเพื่อแสดงคำอ่าน", "เปิดผู้ให้บริการแปลในการตั้งค่าเพื่อแสดงคำแปล", "ต้นฉบับเท่านั้น", "ใช้"},
                {"vi", "Ngôn ngữ và giao diện", "Chọn ngôn ngữ và giao diện. Bạn có thể đổi sau trong Cài đặt.", "Giao diện tối",
                        "Quyền đọc nhạc đang phát", "Quyền truy cập thông báo Android cho phép ivLyrics đọc bài hát đang phát trên Spotify.", "Cho phép ivLyrics truy cập thông báo trong cài đặt Android, rồi quay lại đây.", "Đã cho phép truy cập thông báo", "Cần quyền truy cập thông báo",
                        "Kết nối và bắt đầu", "Nhập thông tin xác thực API Spotify của riêng bạn để tải thông tin bài hát và ảnh bìa.", "Cách lấy thông tin xác thực API", "Kết nối và bắt đầu", "Lời bài hát tiếng %s", "Chọn nội dung hiển thị cho các bài hát bằng ngôn ngữ này.",
                        "Kết nối nhà cung cấp AI trong Cài đặt để hiển thị phát âm.", "Bật nhà cung cấp dịch trong Cài đặt để hiển thị bản dịch.", "Chỉ bản gốc", "Áp dụng"},
                {"id", "Bahasa dan tampilan", "Pilih bahasa dan tampilan. Anda dapat mengubahnya nanti di Pengaturan.", "Tampilan gelap",
                        "Akses pemutaran", "Akses notifikasi Android memungkinkan ivLyrics membaca lagu yang diputar di Spotify.", "Izinkan akses notifikasi untuk ivLyrics di pengaturan Android, lalu kembali ke sini.", "Akses notifikasi diizinkan", "Akses notifikasi diperlukan",
                        "Hubungkan dan mulai", "Masukkan kredensial API Spotify milik Anda untuk memuat detail lagu dan sampul album.", "Cara mendapatkan kredensial API", "Hubungkan dan mulai", "Lirik bahasa %s", "Pilih yang akan ditampilkan untuk lagu dalam bahasa ini.",
                        "Hubungkan penyedia AI di Pengaturan untuk menampilkan pelafalan.", "Aktifkan penyedia terjemahan di Pengaturan untuk menampilkan terjemahan.", "Hanya asli", "Terapkan"},
                {"ms", "Bahasa dan paparan", "Pilih bahasa dan rupa. Anda boleh menukarnya kemudian dalam Tetapan.", "Rupa gelap",
                        "Akses main balik", "Akses pemberitahuan Android membolehkan ivLyrics membaca lagu yang dimainkan di Spotify.", "Benarkan akses pemberitahuan untuk ivLyrics dalam tetapan Android, kemudian kembali ke sini.", "Akses pemberitahuan dibenarkan", "Akses pemberitahuan diperlukan",
                        "Sambung dan mula", "Masukkan kelayakan API Spotify anda sendiri untuk memuatkan butiran lagu dan kulit album.", "Cara mendapatkan kelayakan API", "Sambung dan mula", "Lirik bahasa %s", "Pilih perkara yang hendak dipaparkan untuk lagu dalam bahasa ini.",
                        "Sambungkan penyedia AI dalam Tetapan untuk memaparkan sebutan.", "Aktifkan penyedia terjemahan dalam Tetapan untuk memaparkan terjemahan.", "Asal sahaja", "Gunakan"},
                {"tr", "Dil ve görünüm", "Dilinizi ve görünümü seçin. Daha sonra Ayarlar’dan değiştirebilirsiniz.", "Koyu görünüm",
                        "Çalma bilgisine erişim", "Android bildirim erişimi, ivLyrics’in Spotify’da çalan şarkıyı okumasını sağlar.", "Android ayarlarında ivLyrics için bildirim erişimine izin verin, ardından buraya dönün.", "Bildirim erişimine izin verildi", "Bildirim erişimi gerekiyor",
                        "Bağlan ve başla", "Şarkı ayrıntıları ve albüm kapakları için kendi Spotify API kimlik bilgilerinizi girin.", "API kimlik bilgileri nasıl alınır", "Bağlan ve başla", "%s şarkı sözleri", "Bu dildeki şarkılar için nelerin gösterileceğini seçin.",
                        "Telaffuzu göstermek için Ayarlar’da bir yapay zekâ sağlayıcısı bağlayın.", "Çevirileri göstermek için Ayarlar’da bir çeviri sağlayıcısını etkinleştirin.", "Yalnızca özgün", "Uygula"}
        };
        if (rows.length != AppI18n.UI_LANGUAGES.size()) throw new IllegalStateException("Missing first-use locale");
        for (String[] row : rows) {
            if (row.length != KEYS.length + 1) throw new IllegalStateException("Invalid first-use locale: " + row[0]);
            Map<String, String> copy = new LinkedHashMap<>(languages.get(row[0]));
            for (int index = 0; index < KEYS.length; index++) copy.put(KEYS[index], row[index + 1]);
            languages.put(row[0], Collections.unmodifiableMap(copy));
        }
    }
}
