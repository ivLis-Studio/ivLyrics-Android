package kr.ivlis.ivlyricsandroid;

import java.util.Map;

final class LyricsToolsTranslationOverrides {
    private LyricsToolsTranslationOverrides() {}

    static void apply(String language, Map<String, String> target) {
        switch (language) {
            case "ko":
                put(target, new String[]{
                        "lyrics.background.title", "개별 배경",
                        "lyrics.background.desc", "이 곡에서만 사용할 배경 종류를 선택합니다. 블러, 밝기, 영상 배율 같은 세부 옵션은 기본 배경 설정을 따릅니다.",
                        "lyrics.background.override", "이 곡의 배경",
                        "lyrics.background.override_desc", "기본 설정 사용 · 개별 배경",
                        "lyrics.background.mode_desc", "이 곡에서만 사용할 배경 종류를 선택합니다. 블러, 밝기, 영상 배율 같은 세부 옵션은 기본 배경 설정을 따릅니다.",
                        "lyrics.background.reset", "기본 설정 사용",
                        "toast.track_background_saved", "개별 배경 · 설정이 저장되었습니다",
                        "toast.track_background_cleared", "개별 배경 · 기본 설정 사용"
                });
                break;
            case "en":
                put(target, new String[]{
                        "lyrics.background.title", "Track Background",
                        "lyrics.background.desc", "Choose the background type for this track only. Detail options like blur, brightness, and video scale follow the default background settings.",
                        "lyrics.background.override", "Background for This Track",
                        "lyrics.background.override_desc", "Use Default Settings · Track Background",
                        "lyrics.background.mode_desc", "Choose the background type for this track only. Detail options like blur, brightness, and video scale follow the default background settings.",
                        "lyrics.background.reset", "Use Default Settings",
                        "toast.track_background_saved", "Track Background · Settings saved",
                        "toast.track_background_cleared", "Track Background · Use Default Settings"
                });
                break;
            case "zh-CN":
                put(target, new String[]{
                        "lyrics.background.title", "单曲背景",
                        "lyrics.background.desc", "只为当前歌曲选择背景类型。模糊、亮度和视频缩放等详细选项会沿用默认背景设置。",
                        "lyrics.background.override", "此歌曲的背景",
                        "lyrics.background.override_desc", "使用默认设置 · 单曲背景",
                        "lyrics.background.mode_desc", "只为当前歌曲选择背景类型。模糊、亮度和视频缩放等详细选项会沿用默认背景设置。",
                        "lyrics.background.reset", "使用默认设置",
                        "toast.track_background_saved", "单曲背景 · 设置已保存",
                        "toast.track_background_cleared", "单曲背景 · 使用默认设置"
                });
                break;
            case "zh-TW":
                put(target, new String[]{
                        "lyrics.background.title", "單曲背景",
                        "lyrics.background.desc", "只為目前歌曲選擇背景類型。模糊、亮度與影片縮放等詳細選項會沿用預設背景設定。",
                        "lyrics.background.override", "此歌曲的背景",
                        "lyrics.background.override_desc", "使用預設設定 · 單曲背景",
                        "lyrics.background.mode_desc", "只為目前歌曲選擇背景類型。模糊、亮度與影片縮放等詳細選項會沿用預設背景設定。",
                        "lyrics.background.reset", "使用預設設定",
                        "toast.track_background_saved", "單曲背景 · 設定已儲存",
                        "toast.track_background_cleared", "單曲背景 · 使用預設設定"
                });
                break;
            case "ja":
                put(target, new String[]{
                        "lyrics.background.title", "個別背景",
                        "lyrics.background.desc", "この曲だけで使用する背景の種類を選択します。ぼかし、明るさ、動画倍率などの詳細オプションは既定の背景設定に従います。",
                        "lyrics.background.override", "この曲の背景",
                        "lyrics.background.override_desc", "既定設定を使用 · 個別背景",
                        "lyrics.background.mode_desc", "この曲だけで使用する背景の種類を選択します。ぼかし、明るさ、動画倍率などの詳細オプションは既定の背景設定に従います。",
                        "lyrics.background.reset", "既定設定を使用",
                        "toast.track_background_saved", "個別背景 · 設定が保存されました",
                        "toast.track_background_cleared", "個別背景 · 既定設定を使用"
                });
                break;
            case "hi":
                put(target, new String[]{
                        "lyrics.background.title", "अलग बैकग्राउंड",
                        "lyrics.background.desc", "सिर्फ इस ट्रैक के लिए बैकग्राउंड प्रकार चुनें। ब्लर, चमक और वीडियो स्केल जैसे विस्तृत विकल्प डिफ़ॉल्ट बैकग्राउंड सेटिंग्स का पालन करेंगे।",
                        "lyrics.background.override", "इस ट्रैक का बैकग्राउंड",
                        "lyrics.background.override_desc", "डिफ़ॉल्ट सेटिंग्स उपयोग करें · अलग बैकग्राउंड",
                        "lyrics.background.mode_desc", "सिर्फ इस ट्रैक के लिए बैकग्राउंड प्रकार चुनें। ब्लर, चमक और वीडियो स्केल जैसे विस्तृत विकल्प डिफ़ॉल्ट बैकग्राउंड सेटिंग्स का पालन करेंगे।",
                        "lyrics.background.reset", "डिफ़ॉल्ट सेटिंग्स उपयोग करें",
                        "toast.track_background_saved", "अलग बैकग्राउंड · सेटिंग्स सहेजी गईं",
                        "toast.track_background_cleared", "अलग बैकग्राउंड · डिफ़ॉल्ट सेटिंग्स उपयोग करें",
                        "lyrics.lrclib_search.title", "LRCLIB गीत खोजें",
                        "lyrics.lrclib_search.desc", "शीर्षक या कलाकार से खोजें",
                        "lyrics.lrclib_search.title_hint", "शीर्षक",
                        "lyrics.lrclib_search.artist_hint", "कलाकार",
                        "lyrics.lrclib_search.field_title", "शीर्षक",
                        "lyrics.lrclib_search.field_artist", "कलाकार",
                        "lyrics.lrclib_search.button", "LRCLIB में खोजें",
                        "lyrics.lrclib_search.ready", "शीर्षक या कलाकार से खोजें",
                        "lyrics.lrclib_search.empty_title", "खोज शब्द दर्ज करें।",
                        "lyrics.lrclib_search.loading", "खोज रहा है…",
                        "lyrics.lrclib_search.no_results", "कोई परिणाम नहीं मिला।",
                        "lyrics.lrclib_search.result_count_format", "%d परिणाम",
                        "lyrics.lrclib_search.selecting", "खोज रहा है · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "गीत खोजने में विफल: %s"
                });
                break;
            case "es":
                put(target, new String[]{
                        "lyrics.background.title", "Fondo individual",
                        "lyrics.background.desc", "Elige el tipo de fondo solo para esta canción. Las opciones de detalle como desenfoque, brillo y escala del video seguirán la configuración de fondo predeterminada.",
                        "lyrics.background.override", "Fondo de esta canción",
                        "lyrics.background.override_desc", "Usar configuración predeterminada · Fondo individual",
                        "lyrics.background.mode_desc", "Elige el tipo de fondo solo para esta canción. Las opciones de detalle como desenfoque, brillo y escala del video seguirán la configuración de fondo predeterminada.",
                        "lyrics.background.reset", "Usar configuración predeterminada",
                        "toast.track_background_saved", "Fondo individual · Configuración guardada",
                        "toast.track_background_cleared", "Fondo individual · Usar configuración predeterminada"
                });
                break;
            case "fr":
                put(target, new String[]{
                        "lyrics.background.title", "Arrière-plan du titre",
                        "lyrics.background.desc", "Choisissez le type d’arrière-plan uniquement pour ce titre. Les options détaillées comme le flou, la luminosité et l’échelle vidéo suivent les paramètres d’arrière-plan par défaut.",
                        "lyrics.background.override", "Arrière-plan de ce titre",
                        "lyrics.background.override_desc", "Utiliser les paramètres par défaut · Arrière-plan du titre",
                        "lyrics.background.mode_desc", "Choisissez le type d’arrière-plan uniquement pour ce titre. Les options détaillées comme le flou, la luminosité et l’échelle vidéo suivent les paramètres d’arrière-plan par défaut.",
                        "lyrics.background.reset", "Utiliser les paramètres par défaut",
                        "toast.track_background_saved", "Arrière-plan du titre · Paramètres enregistrés",
                        "toast.track_background_cleared", "Arrière-plan du titre · Utiliser les paramètres par défaut"
                });
                break;
            case "ar":
                put(target, new String[]{
                        "lyrics.background.title", "خلفية خاصة",
                        "lyrics.background.desc", "اختر نوع الخلفية لهذا المسار فقط. تستخدم الخيارات التفصيلية مثل التمويه والسطوع ومقياس الفيديو إعدادات الخلفية الافتراضية.",
                        "lyrics.background.override", "خلفية هذا المسار",
                        "lyrics.background.override_desc", "استخدام الإعدادات الافتراضية · خلفية خاصة",
                        "lyrics.background.mode_desc", "اختر نوع الخلفية لهذا المسار فقط. تستخدم الخيارات التفصيلية مثل التمويه والسطوع ومقياس الفيديو إعدادات الخلفية الافتراضية.",
                        "lyrics.background.reset", "استخدام الإعدادات الافتراضية",
                        "toast.track_background_saved", "خلفية خاصة · تم حفظ الإعدادات",
                        "toast.track_background_cleared", "خلفية خاصة · استخدام الإعدادات الافتراضية",
                        "lyrics.lrclib_search.title", "البحث عن كلمات LRCLIB",
                        "lyrics.lrclib_search.desc", "ابحث بالعنوان أو الفنان",
                        "lyrics.lrclib_search.title_hint", "العنوان",
                        "lyrics.lrclib_search.artist_hint", "الفنان",
                        "lyrics.lrclib_search.field_title", "العنوان",
                        "lyrics.lrclib_search.field_artist", "الفنان",
                        "lyrics.lrclib_search.button", "البحث في LRCLIB",
                        "lyrics.lrclib_search.ready", "ابحث بالعنوان أو الفنان",
                        "lyrics.lrclib_search.empty_title", "أدخل عبارة بحث.",
                        "lyrics.lrclib_search.loading", "جار البحث…",
                        "lyrics.lrclib_search.no_results", "لم يتم العثور على نتائج.",
                        "lyrics.lrclib_search.result_count_format", "%d نتيجة",
                        "lyrics.lrclib_search.selecting", "جار البحث · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "فشل البحث عن الكلمات: %s"
                });
                break;
            case "fa":
                put(target, new String[]{
                        "lyrics.background.title", "پس‌زمینه اختصاصی",
                        "lyrics.background.desc", "نوع پس‌زمینه را فقط برای این آهنگ انتخاب کنید. گزینه‌های جزئی مثل تاری، روشنایی و مقیاس ویدیو از تنظیمات پیش‌فرض پس‌زمینه پیروی می‌کنند.",
                        "lyrics.background.override", "پس‌زمینه این آهنگ",
                        "lyrics.background.override_desc", "استفاده از تنظیمات پیش‌فرض · پس‌زمینه اختصاصی",
                        "lyrics.background.mode_desc", "نوع پس‌زمینه را فقط برای این آهنگ انتخاب کنید. گزینه‌های جزئی مثل تاری، روشنایی و مقیاس ویدیو از تنظیمات پیش‌فرض پس‌زمینه پیروی می‌کنند.",
                        "lyrics.background.reset", "استفاده از تنظیمات پیش‌فرض",
                        "toast.track_background_saved", "پس‌زمینه اختصاصی · تنظیمات ذخیره شد",
                        "toast.track_background_cleared", "پس‌زمینه اختصاصی · استفاده از تنظیمات پیش‌فرض",
                        "lyrics.lrclib_search.title", "جستجوی متن در LRCLIB",
                        "lyrics.lrclib_search.desc", "جستجو بر اساس عنوان یا هنرمند",
                        "lyrics.lrclib_search.title_hint", "عنوان",
                        "lyrics.lrclib_search.artist_hint", "هنرمند",
                        "lyrics.lrclib_search.field_title", "عنوان",
                        "lyrics.lrclib_search.field_artist", "هنرمند",
                        "lyrics.lrclib_search.button", "جستجو در LRCLIB",
                        "lyrics.lrclib_search.ready", "جستجو بر اساس عنوان یا هنرمند",
                        "lyrics.lrclib_search.empty_title", "عبارت جستجو را وارد کنید.",
                        "lyrics.lrclib_search.loading", "در حال جستجو…",
                        "lyrics.lrclib_search.no_results", "نتیجه‌ای پیدا نشد.",
                        "lyrics.lrclib_search.result_count_format", "%d نتیجه",
                        "lyrics.lrclib_search.selecting", "در حال جستجو · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "جستجوی متن ترانه ناموفق بود: %s"
                });
                break;
            case "de":
                put(target, new String[]{
                        "lyrics.background.title", "Eigener Hintergrund",
                        "lyrics.background.desc", "Wähle den Hintergrundtyp nur für diesen Titel. Detailoptionen wie Unschärfe, Helligkeit und Videoskalierung folgen den Standard-Hintergrundeinstellungen.",
                        "lyrics.background.override", "Hintergrund für diesen Titel",
                        "lyrics.background.override_desc", "Standard verwenden · Eigener Hintergrund",
                        "lyrics.background.mode_desc", "Wähle den Hintergrundtyp nur für diesen Titel. Detailoptionen wie Unschärfe, Helligkeit und Videoskalierung folgen den Standard-Hintergrundeinstellungen.",
                        "lyrics.background.reset", "Standard verwenden",
                        "toast.track_background_saved", "Eigener Hintergrund · Einstellungen gespeichert",
                        "toast.track_background_cleared", "Eigener Hintergrund · Standard verwenden"
                });
                break;
            case "ru":
                put(target, new String[]{
                        "lyrics.background.title", "Фон трека",
                        "lyrics.background.desc", "Выберите тип фона только для этого трека. Подробные параметры, такие как размытие, яркость и масштаб видео, берутся из настроек фона по умолчанию.",
                        "lyrics.background.override", "Фон для этого трека",
                        "lyrics.background.override_desc", "Использовать настройки по умолчанию · Фон трека",
                        "lyrics.background.mode_desc", "Выберите тип фона только для этого трека. Подробные параметры, такие как размытие, яркость и масштаб видео, берутся из настроек фона по умолчанию.",
                        "lyrics.background.reset", "Использовать настройки по умолчанию",
                        "toast.track_background_saved", "Фон трека · Настройки сохранены",
                        "toast.track_background_cleared", "Фон трека · Использовать настройки по умолчанию",
                        "lyrics.lrclib_search.title", "Поиск текстов LRCLIB",
                        "lyrics.lrclib_search.desc", "Искать по названию или исполнителю",
                        "lyrics.lrclib_search.title_hint", "Название",
                        "lyrics.lrclib_search.artist_hint", "Исполнитель",
                        "lyrics.lrclib_search.field_title", "Название",
                        "lyrics.lrclib_search.field_artist", "Исполнитель",
                        "lyrics.lrclib_search.button", "Поиск в LRCLIB",
                        "lyrics.lrclib_search.ready", "Искать по названию или исполнителю",
                        "lyrics.lrclib_search.empty_title", "Введите запрос для поиска.",
                        "lyrics.lrclib_search.loading", "Поиск…",
                        "lyrics.lrclib_search.no_results", "Результаты не найдены.",
                        "lyrics.lrclib_search.result_count_format", "%d результатов",
                        "lyrics.lrclib_search.selecting", "Поиск · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "Не удалось найти тексты: %s"
                });
                break;
            case "sv":
                put(target, new String[]{
                        "lyrics.background.title", "Spårbakgrund",
                        "lyrics.background.desc", "Välj bakgrundstyp endast för det här spåret. Detaljer som oskärpa, ljusstyrka och videoskala följer de förvalda bakgrundsinställningarna.",
                        "lyrics.background.override", "Bakgrund för detta spår",
                        "lyrics.background.override_desc", "Använd standardinställningar · Spårbakgrund",
                        "lyrics.background.mode_desc", "Välj bakgrundstyp endast för det här spåret. Detaljer som oskärpa, ljusstyrka och videoskala följer de förvalda bakgrundsinställningarna.",
                        "lyrics.background.reset", "Använd standardinställningar",
                        "toast.track_background_saved", "Spårbakgrund · Inställningar sparade",
                        "toast.track_background_cleared", "Spårbakgrund · Använd standardinställningar",
                        "lyrics.lrclib_search.title", "Sök LRCLIB-texter",
                        "lyrics.lrclib_search.desc", "Sök efter titel eller artist",
                        "lyrics.lrclib_search.title_hint", "Titel",
                        "lyrics.lrclib_search.artist_hint", "Artist",
                        "lyrics.lrclib_search.field_title", "Titel",
                        "lyrics.lrclib_search.field_artist", "Artist",
                        "lyrics.lrclib_search.button", "Sök i LRCLIB",
                        "lyrics.lrclib_search.ready", "Sök efter titel eller artist",
                        "lyrics.lrclib_search.empty_title", "Ange en sökfråga.",
                        "lyrics.lrclib_search.loading", "Söker…",
                        "lyrics.lrclib_search.no_results", "Inga resultat hittades.",
                        "lyrics.lrclib_search.result_count_format", "%d resultat",
                        "lyrics.lrclib_search.selecting", "Söker · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "Det gick inte att söka efter texter: %s"
                });
                break;
            case "pt":
                put(target, new String[]{
                        "lyrics.background.title", "Fundo individual",
                        "lyrics.background.desc", "Escolha o tipo de fundo apenas para esta faixa. Opções detalhadas como desfoque, brilho e escala do vídeo seguem as configurações de fundo padrão.",
                        "lyrics.background.override", "Fundo desta faixa",
                        "lyrics.background.override_desc", "Usar configurações padrão · Fundo individual",
                        "lyrics.background.mode_desc", "Escolha o tipo de fundo apenas para esta faixa. Opções detalhadas como desfoque, brilho e escala do vídeo seguem as configurações de fundo padrão.",
                        "lyrics.background.reset", "Usar configurações padrão",
                        "toast.track_background_saved", "Fundo individual · Configurações salvas",
                        "toast.track_background_cleared", "Fundo individual · Usar configurações padrão"
                });
                break;
            case "bn":
                put(target, new String[]{
                        "lyrics.background.title", "আলাদা ব্যাকগ্রাউন্ড",
                        "lyrics.background.desc", "শুধু এই গানের জন্য ব্যাকগ্রাউন্ড ধরন বেছে নিন। ব্লার, উজ্জ্বলতা ও ভিডিও স্কেলের মতো বিস্তারিত অপশন ডিফল্ট ব্যাকগ্রাউন্ড সেটিং অনুসরণ করবে।",
                        "lyrics.background.override", "এই গানের ব্যাকগ্রাউন্ড",
                        "lyrics.background.override_desc", "ডিফল্ট সেটিং ব্যবহার করুন · আলাদা ব্যাকগ্রাউন্ড",
                        "lyrics.background.mode_desc", "শুধু এই গানের জন্য ব্যাকগ্রাউন্ড ধরন বেছে নিন। ব্লার, উজ্জ্বলতা ও ভিডিও স্কেলের মতো বিস্তারিত অপশন ডিফল্ট ব্যাকগ্রাউন্ড সেটিং অনুসরণ করবে।",
                        "lyrics.background.reset", "ডিফল্ট সেটিং ব্যবহার করুন",
                        "toast.track_background_saved", "আলাদা ব্যাকগ্রাউন্ড · সেটিংস সংরক্ষিত হয়েছে",
                        "toast.track_background_cleared", "আলাদা ব্যাকগ্রাউন্ড · ডিফল্ট সেটিং ব্যবহার করুন",
                        "lyrics.lrclib_search.title", "LRCLIB লিরিক্স খুঁজুন",
                        "lyrics.lrclib_search.desc", "শিরোনাম বা শিল্পী দিয়ে খুঁজুন",
                        "lyrics.lrclib_search.title_hint", "শিরোনাম",
                        "lyrics.lrclib_search.artist_hint", "শিল্পী",
                        "lyrics.lrclib_search.field_title", "শিরোনাম",
                        "lyrics.lrclib_search.field_artist", "শিল্পী",
                        "lyrics.lrclib_search.button", "LRCLIB-এ খুঁজুন",
                        "lyrics.lrclib_search.ready", "শিরোনাম বা শিল্পী দিয়ে খুঁজুন",
                        "lyrics.lrclib_search.empty_title", "অনুসন্ধান শব্দ লিখুন।",
                        "lyrics.lrclib_search.loading", "খোঁজা হচ্ছে…",
                        "lyrics.lrclib_search.no_results", "কোনো ফলাফল পাওয়া যায়নি।",
                        "lyrics.lrclib_search.result_count_format", "%dটি ফলাফল",
                        "lyrics.lrclib_search.selecting", "খোঁজা হচ্ছে · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "লিরিক্স খুঁজতে ব্যর্থ হয়েছে: %s"
                });
                break;
            case "it":
                put(target, new String[]{
                        "lyrics.background.title", "Sfondo del brano",
                        "lyrics.background.desc", "Scegli il tipo di sfondo solo per questo brano. Le opzioni dettagliate come sfocatura, luminosità e scala video seguono le impostazioni di sfondo predefinite.",
                        "lyrics.background.override", "Sfondo per questo brano",
                        "lyrics.background.override_desc", "Usa impostazioni predefinite · Sfondo del brano",
                        "lyrics.background.mode_desc", "Scegli il tipo di sfondo solo per questo brano. Le opzioni dettagliate come sfocatura, luminosità e scala video seguono le impostazioni di sfondo predefinite.",
                        "lyrics.background.reset", "Usa impostazioni predefinite",
                        "toast.track_background_saved", "Sfondo del brano · Impostazioni salvate",
                        "toast.track_background_cleared", "Sfondo del brano · Usa impostazioni predefinite",
                        "lyrics.lrclib_search.title", "Cerca testi LRCLIB",
                        "lyrics.lrclib_search.desc", "Cerca per titolo o artista",
                        "lyrics.lrclib_search.title_hint", "Titolo",
                        "lyrics.lrclib_search.artist_hint", "Artista",
                        "lyrics.lrclib_search.field_title", "Titolo",
                        "lyrics.lrclib_search.field_artist", "Artista",
                        "lyrics.lrclib_search.button", "Cerca in LRCLIB",
                        "lyrics.lrclib_search.ready", "Cerca per titolo o artista",
                        "lyrics.lrclib_search.empty_title", "Inserisci una ricerca.",
                        "lyrics.lrclib_search.loading", "Ricerca…",
                        "lyrics.lrclib_search.no_results", "Nessun risultato trovato.",
                        "lyrics.lrclib_search.result_count_format", "%d risultati",
                        "lyrics.lrclib_search.selecting", "Ricerca · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "Ricerca testi non riuscita: %s"
                });
                break;
            case "th":
                put(target, new String[]{
                        "lyrics.background.title", "พื้นหลังเฉพาะเพลง",
                        "lyrics.background.desc", "เลือกประเภทพื้นหลังสำหรับเพลงนี้เท่านั้น ตัวเลือกย่อยอย่างเบลอ ความสว่าง และสเกลวิดีโอจะใช้ตามการตั้งค่าพื้นหลังเริ่มต้น",
                        "lyrics.background.override", "พื้นหลังของเพลงนี้",
                        "lyrics.background.override_desc", "ใช้การตั้งค่าเริ่มต้น · พื้นหลังเฉพาะเพลง",
                        "lyrics.background.mode_desc", "เลือกประเภทพื้นหลังสำหรับเพลงนี้เท่านั้น ตัวเลือกย่อยอย่างเบลอ ความสว่าง และสเกลวิดีโอจะใช้ตามการตั้งค่าพื้นหลังเริ่มต้น",
                        "lyrics.background.reset", "ใช้การตั้งค่าเริ่มต้น",
                        "toast.track_background_saved", "พื้นหลังเฉพาะเพลง · บันทึกการตั้งค่าแล้ว",
                        "toast.track_background_cleared", "พื้นหลังเฉพาะเพลง · ใช้การตั้งค่าเริ่มต้น",
                        "lyrics.lrclib_search.title", "ค้นหาเนื้อเพลง LRCLIB",
                        "lyrics.lrclib_search.desc", "ค้นหาด้วยชื่อเพลงหรือศิลปิน",
                        "lyrics.lrclib_search.title_hint", "ชื่อเพลง",
                        "lyrics.lrclib_search.artist_hint", "ศิลปิน",
                        "lyrics.lrclib_search.field_title", "ชื่อเพลง",
                        "lyrics.lrclib_search.field_artist", "ศิลปิน",
                        "lyrics.lrclib_search.button", "ค้นหาใน LRCLIB",
                        "lyrics.lrclib_search.ready", "ค้นหาด้วยชื่อเพลงหรือศิลปิน",
                        "lyrics.lrclib_search.empty_title", "ป้อนคำค้นหา",
                        "lyrics.lrclib_search.loading", "กำลังค้นหา…",
                        "lyrics.lrclib_search.no_results", "ไม่พบผลลัพธ์",
                        "lyrics.lrclib_search.result_count_format", "%d ผลลัพธ์",
                        "lyrics.lrclib_search.selecting", "กำลังค้นหา · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "ค้นหาเนื้อเพลงไม่สำเร็จ: %s"
                });
                break;
            case "vi":
                put(target, new String[]{
                        "lyrics.background.title", "Nền riêng",
                        "lyrics.background.desc", "Chọn kiểu nền chỉ cho bài hát này. Các tùy chọn chi tiết như làm mờ, độ sáng và tỷ lệ video sẽ theo cài đặt nền mặc định.",
                        "lyrics.background.override", "Nền của bài hát này",
                        "lyrics.background.override_desc", "Dùng cài đặt mặc định · Nền riêng",
                        "lyrics.background.mode_desc", "Chọn kiểu nền chỉ cho bài hát này. Các tùy chọn chi tiết như làm mờ, độ sáng và tỷ lệ video sẽ theo cài đặt nền mặc định.",
                        "lyrics.background.reset", "Dùng cài đặt mặc định",
                        "toast.track_background_saved", "Nền riêng · Đã lưu cài đặt",
                        "toast.track_background_cleared", "Nền riêng · Dùng cài đặt mặc định",
                        "lyrics.lrclib_search.title", "Tìm lời bài hát LRCLIB",
                        "lyrics.lrclib_search.desc", "Tìm theo tên bài hát hoặc nghệ sĩ",
                        "lyrics.lrclib_search.title_hint", "Tiêu đề",
                        "lyrics.lrclib_search.artist_hint", "Nghệ sĩ",
                        "lyrics.lrclib_search.field_title", "Tiêu đề",
                        "lyrics.lrclib_search.field_artist", "Nghệ sĩ",
                        "lyrics.lrclib_search.button", "Tìm trong LRCLIB",
                        "lyrics.lrclib_search.ready", "Tìm theo tên bài hát hoặc nghệ sĩ",
                        "lyrics.lrclib_search.empty_title", "Nhập từ khóa tìm kiếm.",
                        "lyrics.lrclib_search.loading", "Đang tìm…",
                        "lyrics.lrclib_search.no_results", "Không tìm thấy kết quả.",
                        "lyrics.lrclib_search.result_count_format", "%d kết quả",
                        "lyrics.lrclib_search.selecting", "Đang tìm · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "Không thể tìm lời bài hát: %s"
                });
                break;
            case "id":
                put(target, new String[]{
                        "lyrics.background.title", "Latar Khusus",
                        "lyrics.background.desc", "Pilih jenis latar hanya untuk lagu ini. Opsi detail seperti blur, kecerahan, dan skala video mengikuti pengaturan latar default.",
                        "lyrics.background.override", "Latar untuk Lagu Ini",
                        "lyrics.background.override_desc", "Gunakan Pengaturan Default · Latar Khusus",
                        "lyrics.background.mode_desc", "Pilih jenis latar hanya untuk lagu ini. Opsi detail seperti blur, kecerahan, dan skala video mengikuti pengaturan latar default.",
                        "lyrics.background.reset", "Gunakan Pengaturan Default",
                        "toast.track_background_saved", "Latar Khusus · Pengaturan disimpan",
                        "toast.track_background_cleared", "Latar Khusus · Gunakan Pengaturan Default",
                        "lyrics.lrclib_search.title", "Cari Lirik LRCLIB",
                        "lyrics.lrclib_search.desc", "Cari berdasarkan judul atau artis",
                        "lyrics.lrclib_search.title_hint", "Judul",
                        "lyrics.lrclib_search.artist_hint", "Artis",
                        "lyrics.lrclib_search.field_title", "Judul",
                        "lyrics.lrclib_search.field_artist", "Artis",
                        "lyrics.lrclib_search.button", "Cari di LRCLIB",
                        "lyrics.lrclib_search.ready", "Cari berdasarkan judul atau artis",
                        "lyrics.lrclib_search.empty_title", "Masukkan kata kunci pencarian.",
                        "lyrics.lrclib_search.loading", "Mencari…",
                        "lyrics.lrclib_search.no_results", "Tidak ada hasil.",
                        "lyrics.lrclib_search.result_count_format", "%d hasil",
                        "lyrics.lrclib_search.selecting", "Mencari · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "Gagal mencari lirik: %s"
                });
                break;
            case "ms":
                put(target, new String[]{
                        "lyrics.background.title", "Latar Khusus",
                        "lyrics.background.desc", "Pilih jenis latar untuk lagu ini sahaja. Opsyen terperinci seperti kabur, kecerahan dan skala video mengikuti tetapan latar lalai.",
                        "lyrics.background.override", "Latar untuk Lagu Ini",
                        "lyrics.background.override_desc", "Guna Tetapan Lalai · Latar Khusus",
                        "lyrics.background.mode_desc", "Pilih jenis latar untuk lagu ini sahaja. Opsyen terperinci seperti kabur, kecerahan dan skala video mengikuti tetapan latar lalai.",
                        "lyrics.background.reset", "Guna Tetapan Lalai",
                        "toast.track_background_saved", "Latar Khusus · Tetapan telah disimpan",
                        "toast.track_background_cleared", "Latar Khusus · Guna Tetapan Lalai",
                        "lyrics.lrclib_search.title", "Cari Lirik LRCLIB",
                        "lyrics.lrclib_search.desc", "Cari mengikut tajuk atau artis",
                        "lyrics.lrclib_search.title_hint", "Tajuk",
                        "lyrics.lrclib_search.artist_hint", "Artis",
                        "lyrics.lrclib_search.field_title", "Tajuk",
                        "lyrics.lrclib_search.field_artist", "Artis",
                        "lyrics.lrclib_search.button", "Cari di LRCLIB",
                        "lyrics.lrclib_search.ready", "Cari mengikut tajuk atau artis",
                        "lyrics.lrclib_search.empty_title", "Masukkan kata carian.",
                        "lyrics.lrclib_search.loading", "Mencari…",
                        "lyrics.lrclib_search.no_results", "Tiada hasil ditemui.",
                        "lyrics.lrclib_search.result_count_format", "%d hasil",
                        "lyrics.lrclib_search.selecting", "Mencari · LRCLIB",
                        "lyrics.lrclib_search.loaded", "LRCLIB ✓",
                        "lyrics.lrclib_search.error_format", "Gagal mencari lirik: %s"
                });
                break;
            case "tr":
                put(target, new String[]{
                        "lyrics.background.title", "Parça Arka Planı",
                        "lyrics.background.desc", "Yalnızca bu parça için arka plan türünü seçin. Bulanıklık, parlaklık ve video ölçeği gibi ayrıntılı seçenekler varsayılan arka plan ayarlarını izler.",
                        "lyrics.background.override", "Bu Parçanın Arka Planı",
                        "lyrics.background.override_desc", "Varsayılan Ayarları Kullan · Parça Arka Planı",
                        "lyrics.background.mode_desc", "Yalnızca bu parça için arka plan türünü seçin. Bulanıklık, parlaklık ve video ölçeği gibi ayrıntılı seçenekler varsayılan arka plan ayarlarını izler.",
                        "lyrics.background.reset", "Varsayılan Ayarları Kullan",
                        "toast.track_background_saved", "Parça Arka Planı · Ayarlar kaydedildi",
                        "toast.track_background_cleared", "Parça Arka Planı · Varsayılan Ayarları Kullan"
                });
                break;
            case "cs":
                put(target, new String[]{
                        "lyrics.background.title", "Pozadí skladby",
                        "lyrics.background.desc", "Vyberte typ pozadí pouze pro tuto stopu. Možnosti detailů, jako je rozostření, jas a měřítko videa, se řídí výchozím nastavením pozadí.",
                        "lyrics.background.override", "Pozadí pro tuto skladbu",
                        "lyrics.background.override_desc", "Použít výchozí nastavení · Pozadí skladby",
                        "lyrics.background.mode_desc", "Vyberte typ pozadí pouze pro tuto stopu. Možnosti detailů, jako je rozostření, jas a měřítko videa, se řídí výchozím nastavením pozadí.",
                        "lyrics.background.reset", "Použít výchozí nastavení",
                        "toast.track_background_saved", "Pozadí skladby · Nastavení uloženo",
                        "toast.track_background_cleared", "Pozadí skladby · Použít výchozí nastavení"
                });
                break;
            default:
                throw new IllegalArgumentException("Unsupported lyrics tools language: " + language);
        }
        if (isLrclibFallbackLanguage(language)) {
            target.put("lyrics.lrclib_search.instrumental", target.get("repo.instrumental"));
            target.put("lyrics.lrclib_search.synced", target.get("lyrics_provider.synced"));
            target.put("lyrics.lrclib_search.plain", target.get("lyrics_provider.plain"));
            target.put("repo.detail.manual_lrclib", target.get("lyrics.lrclib_search.loaded"));
        }
    }

    private static boolean isLrclibFallbackLanguage(String language) {
        switch (language) {
            case "hi": case "ar": case "fa": case "ru": case "sv": case "bn":
            case "it": case "th": case "vi": case "id": case "ms":
                return true;
            default:
                return false;
        }
    }

    private static void put(Map<String, String> target, String[] values) {
        if (values.length % 2 != 0) {
            throw new IllegalStateException("Lyrics tools translation pairs are incomplete");
        }
        for (int index = 0; index < values.length; index += 2) {
            target.put(values[index], values[index + 1]);
        }
    }
}
