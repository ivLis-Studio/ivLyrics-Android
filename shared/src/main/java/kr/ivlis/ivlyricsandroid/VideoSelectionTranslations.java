package kr.ivlis.ivlyricsandroid;

import java.util.Map;

final class VideoSelectionTranslations {
    private VideoSelectionTranslations() {}
    static void apply(String language, Map<String, String> target) {
        String[] values = switch (language) {
            case "ko" -> new String[]{"배경 영상 선택", "커뮤니티 영상", "YouTube URL 입력", "올바른 YouTube URL 또는 영상 ID를 입력하세요.", "등록된 영상이 없습니다.", "영상 목록을 불러오지 못했습니다."};
            case "en" -> new String[]{"Choose background video", "Community videos", "Enter YouTube URL", "Enter a valid YouTube URL or video ID.", "No videos available.", "Could not load videos."};
            case "ja" -> new String[]{"背景動画を選択", "コミュニティ動画", "YouTube URLを入力", "有効なYouTube URLまたは動画IDを入力してください。", "動画がありません。", "動画を読み込めませんでした。"};
            case "zh-CN" -> new String[]{"选择背景视频", "社区视频", "输入 YouTube 链接", "请输入有效的 YouTube 链接或视频 ID。", "没有可用视频。", "无法加载视频。"};
            case "zh-TW" -> new String[]{"選擇背景影片", "社群影片", "輸入 YouTube 網址", "請輸入有效的 YouTube 網址或影片 ID。", "沒有可用影片。", "無法載入影片。"};
            case "hi" -> new String[]{"पृष्ठभूमि वीडियो चुनें", "समुदाय के वीडियो", "YouTube URL दर्ज करें", "मान्य YouTube URL या वीडियो ID दर्ज करें।", "कोई वीडियो उपलब्ध नहीं है।", "वीडियो लोड नहीं हो सके।"};
            case "es" -> new String[]{"Elegir vídeo de fondo", "Vídeos de la comunidad", "Introducir URL de YouTube", "Introduce una URL o un ID de vídeo válido de YouTube.", "No hay vídeos disponibles.", "No se pudieron cargar los vídeos."};
            case "fr" -> new String[]{"Choisir la vidéo de fond", "Vidéos de la communauté", "Saisir une URL YouTube", "Saisissez une URL ou un identifiant de vidéo YouTube valide.", "Aucune vidéo disponible.", "Impossible de charger les vidéos."};
            case "ar" -> new String[]{"اختيار فيديو الخلفية", "فيديوهات المجتمع", "إدخال رابط YouTube", "أدخل رابط YouTube أو معرّف فيديو صالحًا.", "لا تتوفر فيديوهات.", "تعذّر تحميل الفيديوهات."};
            case "fa" -> new String[]{"انتخاب ویدیوی پس‌زمینه", "ویدیوهای انجمن", "وارد کردن نشانی YouTube", "نشانی یا شناسه معتبر ویدیوی YouTube را وارد کنید.", "ویدیویی موجود نیست.", "بارگیری ویدیوها ممکن نشد."};
            case "de" -> new String[]{"Hintergrundvideo auswählen", "Community-Videos", "YouTube-URL eingeben", "Gib eine gültige YouTube-URL oder Video-ID ein.", "Keine Videos verfügbar.", "Videos konnten nicht geladen werden."};
            case "ru" -> new String[]{"Выбрать фоновое видео", "Видео сообщества", "Ввести ссылку YouTube", "Введите действительную ссылку или ID видео YouTube.", "Нет доступных видео.", "Не удалось загрузить видео."};
            case "sv" -> new String[]{"Välj bakgrundsvideo", "Communityvideor", "Ange YouTube-URL", "Ange en giltig YouTube-URL eller ett video-ID.", "Inga videor tillgängliga.", "Kunde inte läsa in videor."};
            case "pt" -> new String[]{"Escolher vídeo de fundo", "Vídeos da comunidade", "Inserir URL do YouTube", "Insira uma URL ou um ID de vídeo válido do YouTube.", "Não há vídeos disponíveis.", "Não foi possível carregar os vídeos."};
            case "bn" -> new String[]{"পটভূমির ভিডিও বেছে নিন", "কমিউনিটির ভিডিও", "YouTube URL লিখুন", "সঠিক YouTube URL বা ভিডিও ID লিখুন।", "কোনো ভিডিও নেই।", "ভিডিও লোড করা যায়নি।"};
            case "cs" -> new String[]{"Vybrat video na pozadí", "Komunitní videa", "Zadat adresu YouTube", "Zadejte platnou adresu YouTube nebo ID videa.", "Nejsou dostupná žádná videa.", "Videa se nepodařilo načíst."};
            case "it" -> new String[]{"Scegli video di sfondo", "Video della community", "Inserisci URL YouTube", "Inserisci un URL o un ID video YouTube valido.", "Nessun video disponibile.", "Impossibile caricare i video."};
            case "th" -> new String[]{"เลือกวิดีโอพื้นหลัง", "วิดีโอจากชุมชน", "ป้อน URL ของ YouTube", "ป้อน URL ของ YouTube หรือรหัสวิดีโอที่ถูกต้อง", "ไม่มีวิดีโอ", "โหลดวิดีโอไม่สำเร็จ"};
            case "vi" -> new String[]{"Chọn video nền", "Video cộng đồng", "Nhập URL YouTube", "Nhập URL YouTube hoặc ID video hợp lệ.", "Không có video.", "Không thể tải video."};
            case "id" -> new String[]{"Pilih video latar", "Video komunitas", "Masukkan URL YouTube", "Masukkan URL YouTube atau ID video yang valid.", "Tidak ada video.", "Gagal memuat video."};
            case "ms" -> new String[]{"Pilih video latar", "Video komuniti", "Masukkan URL YouTube", "Masukkan URL YouTube atau ID video yang sah.", "Tiada video tersedia.", "Video tidak dapat dimuatkan."};
            case "tr" -> new String[]{"Arka plan videosu seç", "Topluluk videoları", "YouTube URL’si gir", "Geçerli bir YouTube URL’si veya video kimliği girin.", "Video bulunamadı.", "Videolar yüklenemedi."};
            default -> null;
        };
        if (values == null) return;
        String[] keys = {"video.choose", "video.community", "video.url", "video.invalid", "video.empty", "video.failed"};
        for (int index = 0; index < keys.length; index++) target.put(keys[index], values[index]);
    }
}
