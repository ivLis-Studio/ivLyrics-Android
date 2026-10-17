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
        applyTimingShare(language, target);
    }

    private static void applyTimingShare(String language, Map<String, String> target) {
        String[][] rows = {
                {"en", "Share video timing", "Video timing shared.", "Could not share video timing.", "Choose or adjust a video with timed lyrics and a valid ISRC before sharing."},
                {"ko", "영상 타이밍 공유", "영상 타이밍을 공유했습니다.", "영상 타이밍을 공유하지 못했습니다.", "공유하려면 시간 정보가 있는 가사와 유효한 ISRC가 있는 영상을 선택하거나 시간을 조정하세요."},
                {"ja", "動画のタイミングを共有", "動画のタイミングを共有しました。", "動画のタイミングを共有できませんでした。", "共有する前に、時間情報付きの歌詞と有効なISRCがある動画を選ぶか、タイミングを調整してください。"},
                {"zh-CN", "分享视频时间", "已分享视频时间。", "无法分享视频时间。", "分享前请选择视频或调整时间，并确保有带时间的歌词和有效的 ISRC。"},
                {"zh-TW", "分享影片時間", "已分享影片時間。", "無法分享影片時間。", "分享前請選擇影片或調整時間，並確認有含時間資訊的歌詞及有效的 ISRC。"},
                {"hi", "वीडियो की टाइमिंग साझा करें", "वीडियो की टाइमिंग साझा की गई।", "वीडियो की टाइमिंग साझा नहीं हो सकी।", "साझा करने से पहले समय सहित बोल और मान्य ISRC वाला वीडियो चुनें या उसकी टाइमिंग बदलें।"},
                {"es", "Compartir sincronización del vídeo", "Sincronización del vídeo compartida.", "No se pudo compartir la sincronización.", "Antes de compartir, elige o ajusta un vídeo con letra sincronizada e ISRC válido."},
                {"fr", "Partager le minutage de la vidéo", "Minutage de la vidéo partagé.", "Impossible de partager le minutage.", "Avant de partager, choisissez ou ajustez une vidéo avec des paroles synchronisées et un ISRC valide."},
                {"ar", "مشاركة توقيت الفيديو", "تمت مشاركة توقيت الفيديو.", "تعذّرت مشاركة توقيت الفيديو.", "قبل المشاركة، اختر فيديو أو اضبط توقيته مع كلمات موقّتة ورمز ISRC صالح."},
                {"fa", "اشتراک‌گذاری زمان‌بندی ویدیو", "زمان‌بندی ویدیو به اشتراک گذاشته شد.", "اشتراک‌گذاری زمان‌بندی ویدیو ممکن نشد.", "پیش از اشتراک‌گذاری، ویدیویی با متن زمان‌بندی‌شده و ISRC معتبر انتخاب یا زمان آن را تنظیم کنید."},
                {"de", "Video-Timing teilen", "Video-Timing geteilt.", "Video-Timing konnte nicht geteilt werden.", "Wähle vor dem Teilen ein Video mit zeitlich abgestimmtem Songtext und gültiger ISRC oder passe dessen Timing an."},
                {"ru", "Поделиться синхронизацией видео", "Синхронизация видео отправлена.", "Не удалось отправить синхронизацию видео.", "Перед отправкой выберите видео или настройте его время. Нужны синхронизированный текст и действительный ISRC."},
                {"sv", "Dela videons tidsinställning", "Videons tidsinställning har delats.", "Det gick inte att dela tidsinställningen.", "Välj eller justera en video med tidsatt låttext och giltig ISRC innan du delar."},
                {"pt", "Compartilhar sincronização do vídeo", "Sincronização do vídeo compartilhada.", "Não foi possível compartilhar a sincronização.", "Antes de compartilhar, escolha ou ajuste um vídeo com letra sincronizada e ISRC válido."},
                {"bn", "ভিডিওর সময় ভাগ করুন", "ভিডিওর সময় ভাগ করা হয়েছে।", "ভিডিওর সময় ভাগ করা যায়নি।", "ভাগ করার আগে সময়যুক্ত লিরিক্স ও সঠিক ISRC-সহ ভিডিও বেছে নিন বা তার সময় ঠিক করুন।"},
                {"cs", "Sdílet časování videa", "Časování videa bylo sdíleno.", "Časování videa se nepodařilo sdílet.", "Před sdílením vyberte video nebo upravte jeho časování. Je potřeba časovaný text a platné ISRC."},
                {"it", "Condividi la sincronizzazione del video", "Sincronizzazione del video condivisa.", "Impossibile condividere la sincronizzazione.", "Prima di condividere, scegli o regola un video con testo sincronizzato e ISRC valido."},
                {"th", "แชร์จังหวะเวลาวิดีโอ", "แชร์จังหวะเวลาวิดีโอแล้ว", "แชร์จังหวะเวลาวิดีโอไม่สำเร็จ", "ก่อนแชร์ ให้เลือกหรือปรับเวลาวิดีโอที่มีเนื้อเพลงพร้อมเวลาและ ISRC ที่ถูกต้อง"},
                {"vi", "Chia sẻ thời gian đồng bộ video", "Đã chia sẻ thời gian đồng bộ video.", "Không thể chia sẻ thời gian đồng bộ video.", "Trước khi chia sẻ, hãy chọn hoặc chỉnh video có lời bài hát kèm thời gian và ISRC hợp lệ."},
                {"id", "Bagikan pengaturan waktu video", "Pengaturan waktu video dibagikan.", "Pengaturan waktu video gagal dibagikan.", "Sebelum berbagi, pilih atau sesuaikan video dengan lirik bertanda waktu dan ISRC yang valid."},
                {"ms", "Kongsi pemasaan video", "Pemasaan video telah dikongsi.", "Pemasaan video tidak dapat dikongsi.", "Sebelum berkongsi, pilih atau laraskan video dengan lirik bermasa dan ISRC yang sah."},
                {"tr", "Video zamanlamasını paylaş", "Video zamanlaması paylaşıldı.", "Video zamanlaması paylaşılamadı.", "Paylaşmadan önce zamanlı şarkı sözleri ve geçerli ISRC içeren bir video seç veya zamanlamasını ayarla."}
        };
        String[] keys = {"video.share_timing", "video.timing_shared", "video.share_timing_failed", "video.cannot_share_timing"};
        for (String[] row : rows) {
            if (!language.equals(row[0])) continue;
            for (int index = 0; index < keys.length; index++) target.put(keys[index], row[index + 1]);
            return;
        }
    }
}
