package kr.ivlis.ivlyricsandroid;

import java.util.Map;

final class OpenAIConnectionI18n {
    private OpenAIConnectionI18n() {}

    static void add(Map<String, String> strings, String language) {
        String[] values;
        switch (language) {
            case "ko":
                values = new String[]{"추가 OpenAI 호환 제공자", "추가 OpenAI 호환 제공자", "기본 연결이 실패하면 아래의 활성화된 제공자를 순서대로 시도합니다.", "커스텀 제공자 추가", "제공자 이름", "제공자 삭제", "저장", "위로 이동", "아래로 이동", "Sonar는 모델 목록 API가 없어 기본 지원 목록을 표시합니다."};
                break;
            case "ja":
                values = new String[]{"追加のOpenAI互換プロバイダー", "追加のOpenAI互換プロバイダー", "基本の接続が失敗すると、以下の有効なプロバイダーを順番に試します。", "カスタムプロバイダーを追加", "プロバイダー名", "プロバイダーを削除", "保存", "上に移動", "下に移動", "Sonarにはモデル一覧APIがないため、内蔵の対応モデル一覧を表示します。"};
                break;
            case "zh-CN":
                values = new String[]{"其他兼容 OpenAI 的提供商", "其他兼容 OpenAI 的提供商", "如果主连接失败，将按顺序尝试下方已启用的提供商。", "添加自定义提供商", "提供商名称", "删除提供商", "保存", "上移", "下移", "Sonar 没有模型列表 API，将显示内置的支持模型列表。"};
                break;
            case "zh-TW":
                values = new String[]{"其他相容 OpenAI 的供應商", "其他相容 OpenAI 的供應商", "如果主要連線失敗，將依序嘗試下方已啟用的供應商。", "新增自訂供應商", "供應商名稱", "刪除供應商", "儲存", "上移", "下移", "Sonar 沒有模型清單 API，將顯示內建的支援模型清單。"};
                break;
            case "de":
                values = new String[]{"Weitere OpenAI-kompatible Anbieter", "Weitere OpenAI-kompatible Anbieter", "Wenn die primäre Verbindung fehlschlägt, werden die unten aktivierten Anbieter der Reihe nach versucht.", "Benutzerdefinierten Anbieter hinzufügen", "Anbietername", "Anbieter entfernen", "Speichern", "Nach oben verschieben", "Nach unten verschieben", "Sonar hat keine API für Modelllisten. Die integrierte Liste unterstützter Modelle wird angezeigt."};
                break;
            case "fr":
                values = new String[]{"Fournisseurs compatibles OpenAI supplémentaires", "Fournisseurs compatibles OpenAI supplémentaires", "Si la connexion principale échoue, les fournisseurs activés ci-dessous sont essayés dans l’ordre.", "Ajouter un fournisseur personnalisé", "Nom du fournisseur", "Supprimer le fournisseur", "Enregistrer", "Déplacer vers le haut", "Déplacer vers le bas", "Sonar ne dispose pas d’API de liste des modèles. La liste intégrée des modèles pris en charge est affichée."};
                break;
            case "es":
                values = new String[]{"Proveedores adicionales compatibles con OpenAI", "Proveedores adicionales compatibles con OpenAI", "Si falla la conexión principal, se probarán en orden los proveedores activados de abajo.", "Añadir proveedor personalizado", "Nombre del proveedor", "Eliminar proveedor", "Guardar", "Mover arriba", "Mover abajo", "Sonar no tiene una API de lista de modelos. Se muestra la lista integrada de modelos compatibles."};
                break;
            case "it":
                values = new String[]{"Altri provider compatibili con OpenAI", "Altri provider compatibili con OpenAI", "Se la connessione principale non riesce, vengono provati in ordine i provider attivati qui sotto.", "Aggiungi provider personalizzato", "Nome del provider", "Rimuovi provider", "Salva", "Sposta su", "Sposta giù", "Sonar non dispone di un’API per l’elenco dei modelli. Viene mostrato l’elenco integrato dei modelli supportati."};
                break;
            case "pt":
                values = new String[]{"Provedores adicionais compatíveis com OpenAI", "Provedores adicionais compatíveis com OpenAI", "Se a conexão principal falhar, os provedores ativados abaixo serão tentados em ordem.", "Adicionar provedor personalizado", "Nome do provedor", "Remover provedor", "Salvar", "Mover para cima", "Mover para baixo", "O Sonar não tem uma API de lista de modelos. A lista integrada de modelos compatíveis será exibida."};
                break;
            case "ru":
                values = new String[]{"Дополнительные поставщики, совместимые с OpenAI", "Дополнительные поставщики, совместимые с OpenAI", "Если основное подключение не сработает, включённые поставщики ниже будут опробованы по порядку.", "Добавить своего поставщика", "Название поставщика", "Удалить поставщика", "Сохранить", "Вверх", "Вниз", "У Sonar нет API списка моделей. Показан встроенный список поддерживаемых моделей."};
                break;
            case "sv":
                values = new String[]{"Ytterligare OpenAI-kompatibla leverantörer", "Ytterligare OpenAI-kompatibla leverantörer", "Om den primära anslutningen misslyckas provas de aktiverade leverantörerna nedan i ordning.", "Lägg till egen leverantör", "Leverantörens namn", "Ta bort leverantör", "Spara", "Flytta upp", "Flytta ner", "Sonar saknar ett API för modellistor. Den inbyggda listan över modeller som stöds visas."};
                break;
            case "cs":
                values = new String[]{"Další poskytovatelé kompatibilní s OpenAI", "Další poskytovatelé kompatibilní s OpenAI", "Pokud hlavní připojení selže, postupně se vyzkouší níže zapnutí poskytovatelé.", "Přidat vlastního poskytovatele", "Název poskytovatele", "Odebrat poskytovatele", "Uložit", "Posunout nahoru", "Přesunout dolů", "Sonar nemá API pro seznam modelů. Zobrazuje se vestavěný seznam podporovaných modelů."};
                break;
            case "tr":
                values = new String[]{"Ek OpenAI uyumlu sağlayıcılar", "Ek OpenAI uyumlu sağlayıcılar", "Birincil bağlantı başarısız olursa aşağıdaki etkin sağlayıcılar sırayla denenir.", "Özel sağlayıcı ekle", "Sağlayıcı adı", "Sağlayıcıyı kaldır", "Kaydet", "Yukarı Taşı", "Aşağı Taşı", "Sonar'ın model listesi API'si yoktur. Yerleşik desteklenen modeller listesi gösterilir."};
                break;
            case "id":
                values = new String[]{"Penyedia tambahan yang kompatibel dengan OpenAI", "Penyedia tambahan yang kompatibel dengan OpenAI", "Jika koneksi utama gagal, penyedia aktif di bawah akan dicoba secara berurutan.", "Tambah penyedia kustom", "Nama penyedia", "Hapus penyedia", "Simpan", "Pindahkan ke Atas", "Pindahkan ke Bawah", "Sonar tidak memiliki API daftar model. Daftar bawaan model yang didukung ditampilkan."};
                break;
            case "ms":
                values = new String[]{"Penyedia tambahan yang serasi dengan OpenAI", "Penyedia tambahan yang serasi dengan OpenAI", "Jika sambungan utama gagal, penyedia yang diaktifkan di bawah akan dicuba mengikut turutan.", "Tambah penyedia tersuai", "Nama penyedia", "Alih keluar penyedia", "Simpan", "Alih ke atas", "Alih ke bawah", "Sonar tiada API senarai model. Senarai terbina dalam bagi model yang disokong dipaparkan."};
                break;
            case "vi":
                values = new String[]{"Nhà cung cấp tương thích OpenAI bổ sung", "Nhà cung cấp tương thích OpenAI bổ sung", "Nếu kết nối chính thất bại, các nhà cung cấp đã bật bên dưới sẽ được thử lần lượt.", "Thêm nhà cung cấp tùy chỉnh", "Tên nhà cung cấp", "Xóa nhà cung cấp", "Lưu", "Di chuyển lên", "Di chuyển xuống", "Sonar không có API danh sách mô hình. Danh sách mô hình được hỗ trợ tích hợp sẵn sẽ được hiển thị."};
                break;
            case "th":
                values = new String[]{"ผู้ให้บริการเพิ่มเติมที่เข้ากันได้กับ OpenAI", "ผู้ให้บริการเพิ่มเติมที่เข้ากันได้กับ OpenAI", "หากการเชื่อมต่อหลักล้มเหลว จะลองผู้ให้บริการที่เปิดใช้ด้านล่างตามลำดับ", "เพิ่มผู้ให้บริการแบบกำหนดเอง", "ชื่อผู้ให้บริการ", "ลบผู้ให้บริการ", "บันทึก", "ย้ายขึ้น", "ย้ายลง", "Sonar ไม่มี API สำหรับรายการโมเดล จึงแสดงรายการโมเดลที่รองรับซึ่งมีอยู่ในแอป"};
                break;
            case "ar":
                values = new String[]{"مزوّدون إضافيون متوافقون مع OpenAI", "مزوّدون إضافيون متوافقون مع OpenAI", "إذا فشل الاتصال الأساسي، تُجرَّب المزوّدات المفعّلة أدناه بالترتيب.", "إضافة مزوّد مخصّص", "اسم المزوّد", "إزالة المزوّد", "حفظ", "تحريك للأعلى", "تحريك للأسفل", "لا يوفّر Sonar واجهة API لقائمة النماذج. تُعرض القائمة المدمجة للنماذج المدعومة."};
                break;
            case "fa":
                values = new String[]{"ارائه‌دهندگان اضافی سازگار با OpenAI", "ارائه‌دهندگان اضافی سازگار با OpenAI", "اگر اتصال اصلی ناموفق باشد، ارائه‌دهندگان فعال زیر به‌ترتیب امتحان می‌شوند.", "افزودن ارائه‌دهندهٔ سفارشی", "نام ارائه‌دهنده", "حذف ارائه‌دهنده", "ذخیره", "انتقال به بالا", "انتقال به پایین", "Sonar برای فهرست مدل‌ها API ندارد. فهرست داخلی مدل‌های پشتیبانی‌شده نمایش داده می‌شود."};
                break;
            case "hi":
                values = new String[]{"अतिरिक्त OpenAI-संगत प्रदाता", "अतिरिक्त OpenAI-संगत प्रदाता", "मुख्य कनेक्शन विफल होने पर नीचे दिए गए सक्रिय प्रदाताओं को क्रम से आज़माया जाएगा।", "कस्टम प्रदाता जोड़ें", "प्रदाता का नाम", "प्रदाता हटाएँ", "सहेजें", "ऊपर ले जाएँ", "नीचे ले जाएँ", "Sonar में मॉडल सूची API नहीं है। समर्थित मॉडलों की अंतर्निहित सूची दिखाई जा रही है।"};
                break;
            case "bn":
                values = new String[]{"অতিরিক্ত OpenAI-সামঞ্জস্যপূর্ণ প্রদানকারী", "অতিরিক্ত OpenAI-সামঞ্জস্যপূর্ণ প্রদানকারী", "মূল সংযোগ ব্যর্থ হলে নিচের সক্রিয় প্রদানকারীদের ক্রমানুসারে চেষ্টা করা হবে।", "কাস্টম প্রদানকারী যোগ করুন", "প্রদানকারীর নাম", "প্রদানকারী সরান", "সংরক্ষণ করুন", "উপরে সরান", "নিচে সরান", "Sonar-এর মডেল তালিকা API নেই। সমর্থিত মডেলগুলোর অন্তর্নির্মিত তালিকা দেখানো হচ্ছে।"};
                break;
            default:
                values = new String[]{"Additional OpenAI-compatible providers", "Additional OpenAI-compatible providers", "If the primary connection fails, try the enabled providers below in order.", "Add custom provider", "Provider name", "Remove provider", "Save", "Move Up", "Move Down", "Sonar has no model list API. Showing its built-in supported models."};
        }
        String[] keys = {"openai.connections", "openai.connection", "openai.connections_desc", "openai.add_connection", "openai.connection_name", "openai.remove_connection", "openai.save_connection", "openai.move_up", "openai.move_down", "status.models_builtin_sonar"};
        for (int i = 0; i < keys.length; i++) strings.put(keys[i], values[i]);
    }
}
