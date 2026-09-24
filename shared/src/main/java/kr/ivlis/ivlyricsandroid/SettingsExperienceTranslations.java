package kr.ivlis.ivlyricsandroid;

import java.util.Map;

/** Shared PC/mobile settings vocabulary; values must match AppI18nStrings on iOS. */
final class SettingsExperienceTranslations {
    private SettingsExperienceTranslations() {}
    static void apply(String language, Map<String, String> target) {
        String[] values = switch (language) {
            case "ko" -> new String[]{"일반", "가사 제공자", "AI 제공자", "외관", "동작", "고급", "설정 검색...", "검색 결과가 없습니다", "설정은 자동으로 저장됩니다. 다시 생성하면 현재 곡에 적용됩니다."};
            case "en" -> new String[]{"General", "Lyrics Providers", "AI Providers", "Appearance", "Behavior", "Advanced", "Search settings...", "No results found", "Settings save automatically. Regenerate to apply them to the current song."};
            case "zh-CN" -> new String[]{"常规", "歌词提供商", "AI 提供商", "外观", "行为", "高级", "搜索设置...", "无搜索结果", "设置会自动保存。重新生成以应用到当前歌曲。"};
            case "zh-TW" -> new String[]{"一般", "歌詞提供者", "AI 提供者", "外觀", "動作", "進階", "搜尋設定...", "找不到搜尋結果", "設定會自動儲存。重新產生以套用至目前歌曲。"};
            case "ja" -> new String[]{"一般", "歌詞プロバイダー", "AIプロバイダー", "外観", "動作", "詳細", "設定を検索...", "検索結果がありません", "設定は自動保存されます。現在の曲に適用するには再生成してください。"};
            case "hi" -> new String[]{"सामान्य", "गीत प्रदाता", "AI प्रदाता", "दिखावट", "व्यवहार", "उन्नत", "सेटिंग्स खोजें...", "कोई परिणाम नहीं मिला", "सेटिंग अपने आप सहेजी जाती हैं। मौजूदा गीत पर लागू करने के लिए दोबारा बनाएँ।"};
            case "es" -> new String[]{"General", "Proveedores de letras", "Proveedores de IA", "Apariencia", "Comportamiento", "Avanzado", "Buscar configuración...", "No hay resultados", "Los ajustes se guardan automáticamente. Regenera para aplicarlos a la canción actual."};
            case "fr" -> new String[]{"Général", "Fournisseurs de paroles", "Fournisseurs d'IA", "Apparence", "Comportement", "Avancé", "Rechercher des paramètres...", "Aucun résultat", "Les réglages sont enregistrés automatiquement. Régénérez pour les appliquer au morceau actuel."};
            case "ar" -> new String[]{"عام", "موفرو الكلمات", "موفرو الذكاء الاصطناعي", "المظهر", "السلوك", "متقدم", "البحث في الإعدادات...", "لا توجد نتائج", "تُحفظ الإعدادات تلقائيًا. أعد الإنشاء لتطبيقها على الأغنية الحالية."};
            case "fa" -> new String[]{"عمومی", "ارائه‌دهندگان متن آهنگ", "ارائه‌دهندگان هوش مصنوعی", "ظاهر", "رفتار", "پیشرفته", "جستجوی تنظیمات...", "نتیجه‌ای یافت نشد", "تنظیمات خودکار ذخیره می‌شوند. برای اعمال روی آهنگ فعلی، دوباره تولید کنید."};
            case "de" -> new String[]{"Allgemein", "Songtext-Anbieter", "KI-Anbieter", "Aussehen", "Verhalten", "Erweitert", "Einstellungen suchen...", "Keine Ergebnisse", "Einstellungen werden automatisch gespeichert. Für den aktuellen Titel erneut generieren."};
            case "ru" -> new String[]{"Общие", "Провайдеры текстов", "Провайдеры ИИ", "Внешний вид", "Поведение", "Дополнительно", "Поиск настроек...", "Нет результатов", "Настройки сохраняются автоматически. Повторите генерацию для текущей песни."};
            case "sv" -> new String[]{"Allmänt", "Textleverantörer", "AI-leverantörer", "Utseende", "Beteende", "Avancerat", "Sökinställningar...", "Inga resultat hittades", "Inställningar sparas automatiskt. Generera på nytt för den aktuella låten."};
            case "pt" -> new String[]{"Geral", "Provedores de Letras", "Provedores de IA", "Aparência", "Comportamento", "Avançado", "Pesquisar configurações...", "Nenhum resultado encontrado", "As configurações são salvas automaticamente. Gere novamente para aplicá-las à música atual."};
            case "bn" -> new String[]{"সাধারণ", "লিরিক্স প্রদানকারী", "এআই প্রদানকারী", "চেহারা", "আচরণ", "উন্নত", "সেটিংস খুঁজুন...", "কোনো ফলাফল পাওয়া যায়নি", "সেটিংস স্বয়ংক্রিয়ভাবে সংরক্ষিত হয়। বর্তমান গানে প্রয়োগ করতে আবার তৈরি করুন।"};
            case "cs" -> new String[]{"Obecné", "Poskytovatelé textů", "Poskytovatelé AI", "Vzhled", "Chování", "Pokročilé", "Hledat nastavení...", "Žádné výsledky nenalezeny", "Nastavení se ukládají automaticky. Pro aktuální skladbu spusťte nové generování."};
            case "it" -> new String[]{"Generale", "Provider di testi", "Provider AI", "Aspetto", "Comportamento", "Avanzate", "Cerca impostazioni...", "Nessun risultato", "Le impostazioni si salvano automaticamente. Rigenera per applicarle al brano attuale."};
            case "th" -> new String[]{"ทั่วไป", "ผู้ให้บริการเนื้อเพลง", "ผู้ให้บริการ AI", "รูปลักษณ์", "การทำงาน", "ขั้นสูง", "ค้นหาการตั้งค่า...", "ไม่พบผลลัพธ์", "บันทึกการตั้งค่าอัตโนมัติ สร้างใหม่เพื่อใช้กับเพลงปัจจุบัน"};
            case "vi" -> new String[]{"Chung", "Nhà cung cấp lời bài hát", "Nhà cung cấp AI", "Giao diện", "Hành vi", "Nâng cao", "Tìm kiếm cài đặt...", "Không tìm thấy kết quả", "Cài đặt được lưu tự động. Tạo lại để áp dụng cho bài hát hiện tại."};
            case "id" -> new String[]{"Umum", "Penyedia Lirik", "Penyedia AI", "Tampilan", "Perilaku", "Lanjutan", "Cari pengaturan...", "Tidak ada hasil", "Pengaturan tersimpan otomatis. Buat ulang untuk menerapkannya pada lagu saat ini."};
            case "ms" -> new String[]{"Umum", "Penyedia Lirik", "Penyedia AI", "Penampilan", "Gelagat", "Lanjutan", "Cari tetapan...", "Tiada keputusan carian", "Tetapan disimpan secara automatik. Jana semula untuk lagu semasa."};
            case "tr" -> new String[]{"Genel", "Söz Sağlayıcıları", "Yapay Zeka Sağlayıcıları", "Görünüm", "Davranış", "Gelişmiş", "Ayarlarda ara...", "Sonuç bulunamadı", "Ayarlar otomatik kaydedilir. Geçerli şarkıya uygulamak için yeniden oluşturun."};
            default -> null;
        };
        if (values == null) return;
        String[] keys = {"tab.general", "tab.providers", "tab.ai", "tab.appearance", "tab.lyrics", "tab.system", "settings.search", "settings.no_results", "settings.ai_autosave"};
        for (int i = 0; i < keys.length; i++) target.put(keys[i], values[i]);
    }
}
