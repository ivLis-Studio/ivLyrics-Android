package kr.ivlis.ivlyricsandroid;

import android.os.Handler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import sun.misc.Unsafe;

/**
 * The production AiLyricsRepository entry points with Pronunciation Lab in the provider order: an
 * in-memory OpenAI-compatible provider and a fake page stand in for the network and the WebView.
 */
public final class PronunciationLabDispatchRegression {
    private static final String LAB = AiLyricsSettings.Provider.PRONUNCIATION_LAB_ID;
    private static final Unsafe unsafe;
    private static final AtomicInteger connections = new AtomicInteger();
    private static volatile boolean rejectRequests;
    private static int assertions;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void put(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, LyricsResult> cache(AiLyricsRepository repository) throws Exception {
        Field field = AiLyricsRepository.class.getDeclaredField("cache");
        field.setAccessible(true);
        return (Map<String, LyricsResult>) field.get(repository);
    }

    /** The page, faked: records each request and answers like the bridge, or fails as configured. */
    private static final class FakeLab implements PronunciationLabProtocol.Converter {
        final List<PronunciationLabProtocol.Request> requests = new CopyOnWriteArrayList<>();
        volatile Exception failure;

        @Override public List<String> convert(PronunciationLabProtocol.Request request) throws Exception {
            requests.add(request);
            if (failure != null) throw failure;
            List<String> lines = new ArrayList<>();
            for (String line : request.lines) lines.add(line.isEmpty() ? "" : "랩 " + line);
            return lines;
        }
    }

    private static AiLyricsRepository repository(FakeLab lab) throws Exception {
        AiLyricsRepository repository = new AiLyricsRepository();
        put(repository, "pronunciationLab", lab);
        return repository;
    }

    private static AiLyricsSettings.Snapshot settings(boolean pronunciation, boolean translation,
            List<String> order, Map<String, Boolean> enabled) throws Exception {
        // Snapshot construction normally reads Android preferences; the fixture fills the fields directly
        // and keeps all production provider, rule and cache-key behavior.
        AiLyricsSettings.Snapshot settings =
                (AiLyricsSettings.Snapshot) unsafe.allocateInstance(AiLyricsSettings.Snapshot.class);
        for (Field field : AiLyricsSettings.Snapshot.class.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType() == String.class) field.set(settings, "");
            if (field.getType() == Map.class) field.set(settings, Collections.emptyMap());
            if (field.getType() == List.class) field.set(settings, Collections.emptyList());
        }
        AiLyricsSettings.Provider provider = AiLyricsSettings.aiProviderById("chatgpt");
        AiLyricsSettings.ProviderProfile profile = AiLyricsSettings.ProviderProfile.defaults(provider);
        put(profile, "apiKeys", "synthetic-key");
        put(profile, "model", "synthetic-model");
        put(profile, "baseUrl", "https://fixture.invalid/v1");
        put(settings, "provider", provider);
        put(settings, "uiLang", "ko");
        put(settings, "outputLang", "ko");
        put(settings, "pronunciationNotation", AiLyricsSettings.PRONUNCIATION_NOTATION_TRANSLATION);
        put(settings, "apiKeys", "synthetic-key");
        put(settings, "model", "synthetic-model");
        put(settings, "baseUrl", "https://fixture.invalid/v1");
        put(settings, "defaultRule", new AiLyricsSettings.LanguageRule("default", translation, pronunciation, "ko"));
        put(settings, "aiProviderOrder", order);
        put(settings, "aiProviderEnabled", enabled);
        put(settings, "providerProfiles", Map.of("chatgpt", profile));
        return settings;
    }

    private static AiLyricsSettings.Snapshot settings(boolean pronunciation, boolean translation,
            boolean ai, boolean lab, boolean labFirst) throws Exception {
        return settings(pronunciation, translation, labFirst ? List.of(LAB, "chatgpt") : List.of("chatgpt", LAB),
                Map.of("chatgpt", ai, LAB, lab));
    }

    private static TrackSnapshot track(String id) {
        return new TrackSnapshot("Fixture " + id, "Artist", "Album", "com.spotify.music",
                "spotify:track:01234567890123456789" + id, "", 10000, 1000, 1000, 1, true, null, "");
    }

    private static LyricsResult base(boolean karaoke) {
        return new LyricsResult(List.of(
                new LyricsLine(1000, 4000, "Source one", karaoke ? List.of(new LyricsLine.Syllable("Source ", 1000, 2500),
                        new LyricsLine.Syllable("one", 2500, 4000)) : List.of()),
                new LyricsLine(4000, 8000, "Source two", karaoke ? List.of(new LyricsLine.Syllable("Source ", 4000, 6000),
                        new LyricsLine.Syllable("two", 6000, 8000)) : List.of())), "fixture", "base", karaoke);
    }

    private static final class Observer implements InvocationHandler {
        volatile int completed, errors, metadata;
        volatile boolean finished;
        volatile LyricsResult result;
        volatile String error = "";
        final List<String> pronunciationProviders = new CopyOnWriteArrayList<>();
        final List<String> translationProviders = new CopyOnWriteArrayList<>();
        final List<String> logs = new CopyOnWriteArrayList<>();
        /** The pronunciation timing callbacks, by name. */
        final List<String> timing = new CopyOnWriteArrayList<>();

        AiLyricsRepository.Callback callback() {
            return (AiLyricsRepository.Callback) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{AiLyricsRepository.Callback.class}, this);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "onAiLyricsLoaded":
                    completed++;
                    result = (LyricsResult) args[1];
                    finished = true;
                    break;
                case "onAiLyricsPartialLoaded":
                    result = (LyricsResult) args[1];
                    finished = (boolean) args[4];
                    break;
                case "onAiLyricsError":
                    errors++;
                    error = (String) args[1];
                    finished = true;
                    break;
                case "onAiLyricsTaskError":
                    errors++;
                    error = (String) args[1];
                    finished = (boolean) args[4];
                    break;
                case "onAiLyricsProviderChanged":
                    ((boolean) args[1] ? pronunciationProviders : translationProviders).add((String) args[2]);
                    break;
                case "onAiLyricsLog":
                    logs.add((String) args[1]);
                    break;
                case "onAiMetadataTranslationLoaded":
                case "onAiMetadataTranslationError":
                    metadata++;
                    finished = true;
                    break;
                case "onTimedPronunciationStarted":
                case "onTimedPronunciationLoaded":
                case "onTimedPronunciationError":
                    timing.add(method.getName());
                    break;
                default:
                    break;
            }
            return null;
        }
    }

    private static final class Connection extends HttpURLConnection {
        private final boolean rejected = rejectRequests;
        private final ByteArrayOutputStream request = new ByteArrayOutputStream();

        Connection(URL url) { super(url); connections.incrementAndGet(); }
        @Override public void connect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { }
        @Override public int getResponseCode() { return rejected ? 401 : 200; }
        @Override public OutputStream getOutputStream() { return request; }
        @Override public InputStream getErrorStream() { return bytes("{\"error\":{\"message\":\"synthetic rejection\"}}"); }
        @Override public InputStream getInputStream() {
            String body = request.toString(StandardCharsets.UTF_8);
            String text = body.contains("PRONUNCIATION task")
                    ? "L0001\tReading one\nL0002\tReading two\n"
                    : "L0001\tMeaning one\nL0002\tMeaning two\n";
            return bytes("data: {\"choices\":[{\"delta\":{\"content\":" + JSONObject.quote(text) + "}}]}\n\ndata: [DONE]\n\n");
        }
        private static InputStream bytes(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    }

    private static void await(Observer observer) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!observer.finished && System.nanoTime() < deadline) {
            Handler.drain();
            Thread.sleep(2);
        }
        Handler.drain();
        check(observer.finished, "the request finishes");
    }

    private static void aiFirstThenLab() throws Exception {
        FakeLab lab = new FakeLab();
        AiLyricsRepository repository = repository(lab);
        try {
            Observer observer = new Observer();
            repository.loadSupplements(track("01"), base(false), settings(true, false, true, true, false), "en", false, observer.callback());
            await(observer);
            check(observer.completed == 1 && observer.errors == 0, "the AI provider answers");
            check(observer.result.lines.get(0).pronunciationText.equals("Reading one"), "AI pronunciation is used first");
            check(observer.result.pronunciationProviderLabel.equals("OpenAI ChatGPT"), "attributed to the AI provider");
            check(lab.requests.isEmpty(), "Pronunciation Lab is not asked while an AI provider answers");
            check(observer.pronunciationProviders.equals(List.of("OpenAI ChatGPT")), "and never shown as the provider");
            for (String key : cache(repository).keySet()) {
                check(!key.contains("pronunciationProvider="), "behind an AI provider the cache keys keep their earlier form: " + key);
            }
        } finally {
            repository.shutdown();
            Handler.drain();
        }
    }

    private static void labAfterAiFailure() throws Exception {
        FakeLab lab = new FakeLab();
        AiLyricsRepository repository = repository(lab);
        try {
            rejectRequests = true;
            Observer observer = new Observer();
            repository.loadSupplements(track("02"), base(false), settings(true, false, true, true, false), "en", false, observer.callback());
            await(observer);
            check(observer.completed == 1 && observer.errors == 0, "the fallback succeeds");
            check(lab.requests.size() == 1, "Pronunciation Lab is asked once, after the AI provider failed");
            PronunciationLabProtocol.Request request = lab.requests.get(0);
            check(request.lines.equals(List.of("Source one", "Source two")), "it gets the rows the AI provider got, in order");
            check(request.sourceLang.equals("en") && request.lang.equals("ko") && request.notation.equals("translation"),
                    "with the song language, the pronunciation language and the notation of the AI prompt");
            check(observer.result.lines.get(0).pronunciationText.equals("랩 Source one")
                    && observer.result.lines.get(1).pronunciationText.equals("랩 Source two"), "its lines become the pronunciation");
            check(observer.result.pronunciationProviderLabel.equals("Pronunciation Lab"), "attributed to Pronunciation Lab");
            check(observer.pronunciationProviders.equals(List.of("OpenAI ChatGPT", "Pronunciation Lab")),
                    "the loading label follows the fallback");
        } finally {
            rejectRequests = false;
            repository.shutdown();
            Handler.drain();
        }
    }

    private static void labAloneIsCachedUnderItsOwnKey() throws Exception {
        FakeLab lab = new FakeLab();
        AiLyricsRepository repository = repository(lab);
        try {
            AiLyricsSettings.Snapshot settings = settings(true, false, false, true, false);
            int requestsBefore = connections.get();
            Observer observer = new Observer();
            repository.loadSupplements(track("03"), base(false), settings, "ja", false, observer.callback());
            await(observer);
            check(observer.completed == 1 && lab.requests.size() == 1 && connections.get() == requestsBefore,
                    "without an AI provider Pronunciation Lab pronounces alone");
            check(lab.requests.get(0).sourceLang.equals("ja"), "the song language reaches the page");
            boolean task = false;
            boolean combined = false;
            for (String key : cache(repository).keySet()) {
                if (!key.contains("|pronunciationProvider=pronunciation-lab")) continue;
                if (key.contains("|task=pronunciation")) task = true;
                if (!key.contains("|task=")) combined = true;
            }
            check(task && combined, "its pronunciation and combined results have their own cache keys");
            Observer cached = new Observer();
            repository.loadSupplements(track("03"), base(false), settings, "ja", false, cached.callback());
            check(cached.completed == 1 && lab.requests.size() == 1, "the cached result is reused without asking the page");
        } finally {
            repository.shutdown();
            Handler.drain();
        }
    }

    private static void labFailuresAreLocalized() throws Exception {
        String[][] cases = {
                {PronunciationLabProtocol.UNSUPPORTED_SOURCE, AppI18n.t("ko", "error.pronunciation_lab_unsupported_source")},
                {PronunciationLabProtocol.UNSUPPORTED_TARGET, AppI18n.t("ko", "error.pronunciation_lab_unsupported_target")},
                {PronunciationLabProtocol.LOAD_FAILED, AppI18n.t("ko", "error.pronunciation_lab_load_failed")},
                {PronunciationLabProtocol.TIMEOUT, String.format(Locale.ROOT, AppI18n.t("ko", "error.provider_unavailable_format"), "Pronunciation Lab")},
        };
        int index = 0;
        for (String[] expected : cases) {
            FakeLab lab = new FakeLab();
            lab.failure = new PronunciationLabProtocol.Failure(expected[0], "detail for the log");
            AiLyricsRepository repository = repository(lab);
            try {
                Observer observer = new Observer();
                repository.loadSupplements(track("1" + index++), base(false), settings(true, false, false, true, false), "ru", false,
                        observer.callback());
                await(observer);
                check(observer.errors == 1 && observer.completed == 0 && observer.error.equals(expected[1]),
                        "a failure of the last provider shows its localized message: " + expected[0] + " -> " + observer.error);
            } finally {
                repository.shutdown();
                Handler.drain();
            }
        }
    }

    private static void offWithoutAiMeansNoPronunciation() throws Exception {
        FakeLab lab = new FakeLab();
        AiLyricsRepository repository = repository(lab);
        try {
            Observer observer = new Observer();
            repository.loadSupplements(track("04"), base(false), settings(true, false, false, false, false), "ja", false, observer.callback());
            check(observer.errors == 1 && observer.error.equals(AppI18n.t("ko", "error.translation_providers_failed")),
                    "with it off and no AI provider nothing can pronounce");
            check(lab.requests.isEmpty(), "and the page is not asked");
        } finally {
            repository.shutdown();
            Handler.drain();
        }
    }

    private static void translationAndTitlesNeverUseIt() throws Exception {
        FakeLab lab = new FakeLab();
        AiLyricsRepository repository = repository(lab);
        try {
            Observer translation = new Observer();
            repository.loadSupplements(track("05"), base(false), settings(false, true, true, true, true), "en", false, translation.callback());
            await(translation);
            check(translation.completed == 1 && translation.result.lines.get(0).translationText.equals("Meaning one"),
                    "translation goes to the AI provider even with Pronunciation Lab first");
            check(translation.result.lines.get(0).pronunciationText.isEmpty(), "and no pronunciation is made");
            check(translation.translationProviders.equals(List.of("OpenAI ChatGPT")),
                    "Pronunciation Lab is never tried as a translator: " + translation.translationProviders);

            AiLyricsSettings.Snapshot titles = settings(false, true, true, true, true);
            put(titles, "metadataTranslationEnabled", true);
            Observer metadata = new Observer();
            repository.loadMetadataTranslation(track("06"), titles, "en", true, metadata.callback());
            await(metadata);
            check(metadata.metadata == 1, "title translation finishes");
            check(lab.requests.isEmpty(), "Pronunciation Lab never translates lyrics, titles or artists");
            for (String log : metadata.logs) {
                check(!log.contains("provider=Pronunciation Lab"), "no title translation attempt goes to Pronunciation Lab: " + log);
            }
            check(metadata.logs.stream().anyMatch(log -> log.contains("provider=OpenAI ChatGPT")), "the AI provider was tried for the title");
        } finally {
            repository.shutdown();
            Handler.drain();
        }
    }

    private static void unitPronunciationFallsBackToItsLines() throws Exception {
        FakeLab lab = new FakeLab();
        AiLyricsRepository repository = repository(lab);
        try {
            rejectRequests = true;
            AiLyricsSettings.Snapshot settings = settings(true, false, true, true, false);
            put(settings, "syncedPronunciationEnabled", true);
            int requestsBefore = connections.get();
            Observer observer = new Observer();
            repository.loadSupplements(track("07"), base(true), settings, "en", false, observer.callback());
            await(observer);
            check(connections.get() > requestsBefore, "pronunciation by sung unit is asked of the AI provider only");
            check(observer.completed == 1 && lab.requests.size() == 1 && lab.requests.get(0).lines.equals(List.of("Source one", "Source two")),
                    "when no unit pronunciation comes back, the plain line fallback may use Pronunciation Lab");
            check(observer.result.lines.get(0).pronunciationText.equals("랩 Source one"), "its lines are used");
        } finally {
            rejectRequests = false;
            repository.shutdown();
            Handler.drain();
        }
    }

    private static void aCancelledWaitStopsTheFallback() throws Exception {
        FakeLab lab = new FakeLab();
        lab.failure = new InterruptedException("repository shut down");
        AiLyricsRepository repository = repository(lab);
        try {
            int requestsBefore = connections.get();
            Observer observer = new Observer();
            repository.loadSupplements(track("08"), base(false), settings(true, false, true, true, true), "en", false, observer.callback());
            await(observer);
            check(observer.errors == 1 && observer.completed == 0, "the task ends");
            check(connections.get() == requestsBefore, "an interrupted wait does not go on to the next provider");
        } finally {
            repository.shutdown();
            Handler.drain();
        }
    }

    private static void timingIsSkippedQuietlyWithoutAi() throws Exception {
        FakeLab lab = new FakeLab();
        AiLyricsRepository repository = repository(lab);
        try {
            // No AI provider: the selected one has no key and every AI provider is off.
            AiLyricsSettings.Snapshot settings = settings(true, false, false, true, false);
            put(settings, "apiKeys", "");
            Observer observer = new Observer();
            repository.loadSupplements(track("09"), base(true), settings, "en", false, observer.callback());
            await(observer);
            check(observer.completed == 1 && observer.result.karaoke
                    && observer.result.lines.get(0).pronunciationText.equals("랩 Source one"),
                    "Pronunciation Lab pronounces the karaoke lines");
            Observer timing = new Observer();
            String key = repository.loadTimedPronunciation(track("09"), observer.result, settings, "en", timing.callback());
            Thread.sleep(20);
            Handler.drain();
            check(!key.isEmpty() && timing.timing.isEmpty(),
                    "without an AI provider no timing starts and no timing failure is shown: " + timing.timing);
            check(timing.logs.stream().anyMatch(line -> line.contains("pronunciation timing skipped: no AI provider is ready")),
                    "the skipped timing is logged");
        } finally {
            repository.shutdown();
            Handler.drain();
        }
    }

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                if (!url.getHost().equals("fixture.invalid")) throw new AssertionError("Network request escaped the fixture: " + url);
                return new Connection(url);
            }
        });
        aiFirstThenLab();
        labAfterAiFailure();
        labAloneIsCachedUnderItsOwnKey();
        labFailuresAreLocalized();
        offWithoutAiMeansNoPronunciation();
        translationAndTitlesNeverUseIt();
        unitPronunciationFallsBackToItsLines();
        aCancelledWaitStopsTheFallback();
        timingIsSkippedQuietlyWithoutAi();
        System.out.println("PASS " + assertions + " assertions: AI first, Pronunciation Lab fallback and alone, request rows/languages,"
                + " own cache keys, localized failures, never translation/titles, unit fallback, interrupted wait,"
                + " quiet timing without AI");
    }
}
