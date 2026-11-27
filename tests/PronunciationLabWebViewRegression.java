package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;

/** Runs the production Pronunciation Lab page bridge against separate module/host assets and a scripted WebView. */
public final class PronunciationLabWebViewRegression {
    private static final String PAGE = "<!doctype html><html data-pronunciation-lab-build=\"fixture\"><script>/* 夢 네코 */</script></html>";
    private static final String READY = "{\"type\":\"ready\"}";
    private static int assertions;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static PronunciationLabProtocol.Request request(String... lines) {
        return new PronunciationLabProtocol.Request(Arrays.asList(lines), "ja", "ko", "translation");
    }

    private static String result(String id, String... lines) {
        StringBuilder json = new StringBuilder("{\"type\":\"result\",\"id\":").append(PronunciationLabProtocol.quote(id)).append(",\"lines\":[");
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) json.append(',');
            json.append(PronunciationLabProtocol.quote(lines[index]));
        }
        return json.append("],\"version\":\"1.0.2\"}").toString();
    }

    private static String error(String id, String code, String message) {
        return "{\"type\":\"error\",\"id\":" + PronunciationLabProtocol.quote(id) + ",\"code\":\"" + code
                + "\",\"message\":" + PronunciationLabProtocol.quote(message) + "}";
    }

    private static String last(WebView view) {
        return view.scripts.get(view.scripts.size() - 1);
    }

    private static String idOf(String script) {
        int start = script.indexOf("convert(\"") + "convert(\"".length();
        return script.substring(start, script.indexOf('"', start));
    }

    private static final class Answer implements PronunciationLabRepository.Callback {
        final List<List<String>> lines = new ArrayList<>();
        final List<Exception> errors = new ArrayList<>();

        @Override public void onFinished(List<String> values, Exception error) {
            if (error != null) errors.add(error);
            else lines.add(values);
        }

        int calls() { return lines.size() + errors.size(); }

        PronunciationLabProtocol.Failure failure() {
            check(!errors.isEmpty(), "a failure was delivered");
            return (PronunciationLabProtocol.Failure) errors.get(errors.size() - 1);
        }
    }

    private static final class Fixture {
        /** Spotify's process Application, whose assets are Spotify's APK: the page is not there. */
        final Context host = new Context();
        /** The module-wrapped context, whose assets are the module APK's. */
        final Context module = new Context();
        final PronunciationLabRepository repository;

        Fixture(boolean withPage) {
            Handler.clear();
            WebView.throwCreate = WebView.throwLoad = WebView.throwEvaluate = false;
            WebView.processContext = host;
            if (withPage) addPage();
            repository = new PronunciationLabRepository(module);
        }

        void addPage() { module.assets.files.put(PronunciationLabRepository.PAGE_ASSET, PAGE); }

        WebView view() throws Exception { return (WebView) get(repository, "webView"); }

        String start(Answer answer, String... lines) {
            String id = repository.start(request(lines), answer);
            Handler.drain();
            return id;
        }
    }

    private static void moduleAssetsReadyQueueAndOnePage() throws Exception {
        Fixture fixture = new Fixture(true);
        int instances = WebView.instances.size();
        Answer first = new Answer();
        String id = fixture.start(first, "夢ならばどれほどよかったでしょう", "");
        WebView view = fixture.view();
        check(view != null && view.context == fixture.module, "the page's WebView gets the module context");
        check(fixture.module.assets.reads == 1 && fixture.host.assets.reads == 0,
                "the page is read from the module's assets, never the host application's");
        check(PAGE.equals(view.html), "the page keeps its UTF-8 text");
        check("https://appassets.androidplatform.net/pronunciation-lab/".equals(view.baseUrl)
                && "text/html".equals(view.mimeType) && "UTF-8".equals(view.encoding), "HTTPS app-assets origin, explicit encoding");
        check(view.settings.javascript && view.settings.domStorage && !view.settings.fileAccess && !view.settings.contentAccess
                && !view.settings.fileFromFile && !view.settings.universalFromFile, "JavaScript and DOM storage, no file or content access");
        check("ivLyricsPronunciationLabNative".equals(view.bridgeName), "the bridge has the page's name");
        Method postMessage = view.bridge.getClass().getDeclaredMethod("postMessage", String.class);
        check(postMessage.isAnnotationPresent(JavascriptInterface.class) && Modifier.isPublic(postMessage.getModifiers()),
                "postMessage(String) is exposed to the page");
        check(view.navigate(), "the page cannot navigate away while it holds the bridge");
        check(view.scripts.isEmpty() && Handler.delayed() == 1, "the request waits for ready, with its timeout running");

        view.post(READY);
        Handler.drain();
        check(view.scripts.size() == 1 && view.scripts.get(0).equals(
                PronunciationLabProtocol.convertScript(id, request("夢ならばどれほどよかったでしょう", ""))), "ready sends the queued request");
        view.post(result(id, "유메나라바 도레호도 요캇타데쇼", ""));
        Handler.drain();
        check(first.lines.size() == 1 && first.lines.get(0).equals(Arrays.asList("유메나라바 도레호도 요캇타데쇼", ""))
                && first.errors.isEmpty(), "the page's lines reach the request");
        check(Handler.delayed() == 0, "an answered request leaves no timeout");

        Answer second = new Answer();
        String next = fixture.start(second, "猫");
        check(fixture.view() == view && WebView.instances.size() == instances + 1, "one page serves later requests");
        check(idOf(last(view)).equals(next), "a ready page takes a request at once");
        view.post(result(next, "네코"));
        Handler.drain();
        check(second.lines.get(0).equals(Collections.singletonList("네코")) && fixture.module.assets.reads == 1,
                "later requests reuse the page and its HTML");
    }

    private static void sharedRepositoryKeepsTheApplicationContext() throws Exception {
        Context activity = new Context();
        Context moduleApplication = new Context();
        activity.application = moduleApplication;
        PronunciationLabRepository shared = PronunciationLabRepository.shared(activity);
        check(get(shared, "context") == moduleApplication, "the process's page keeps the application context, never an Activity");
        check(PronunciationLabRepository.shared(new Context()) == shared && PronunciationLabRepository.shared(null) == shared,
                "one repository per process");
    }

    private static void concurrentRequestsAndErrorCodes() throws Exception {
        Fixture fixture = new Fixture(true);
        Answer japanese = new Answer();
        Answer russian = new Answer();
        Answer cat = new Answer();
        String a = fixture.start(japanese, "夢");
        String b = fixture.start(russian, "Привет");
        WebView view = fixture.view();
        view.post(READY);
        Handler.drain();
        check(view.scripts.size() == 2 && idOf(view.scripts.get(0)).equals(a) && idOf(view.scripts.get(1)).equals(b),
                "queued requests go in order");
        String c = fixture.start(cat, "猫");
        view.post(error(b, PronunciationLabProtocol.UNSUPPORTED_SOURCE, "[Pronunciation Lab] Cannot read lyrics in language ru"));
        view.post(result(c, "네코"));
        view.post(result(a, "유메"));
        Handler.drain();
        check(japanese.lines.get(0).equals(Collections.singletonList("유메")) && cat.lines.get(0).equals(Collections.singletonList("네코")),
                "answers in any order reach their own request");
        PronunciationLabProtocol.Failure failure = russian.failure();
        check(failure.code.equals(PronunciationLabProtocol.UNSUPPORTED_SOURCE) && failure.getMessage().contains("language ru")
                && failure.userMessage("ko").equals(AppI18n.t("ko", "error.pronunciation_lab_unsupported_source")),
                "a page error keeps its code, its detail for the log and a localized message");
        view.post(result(b, "late"));
        Handler.drain();
        check(russian.calls() == 1 && Handler.delayed() == 0, "each request ends once and leaves no timeout");
    }

    private static void timeoutCancelsInThePageAndKeepsAStartedPage() throws Exception {
        Fixture fixture = new Fixture(true);
        Answer slow = new Answer();
        String id = fixture.start(slow, "夢");
        WebView view = fixture.view();
        view.post(READY);
        Handler.drain();
        Handler.timeOut();
        check(slow.calls() == 1 && slow.failure().code.equals(PronunciationLabProtocol.TIMEOUT), "the timeout fails the request");
        check(slow.failure().userMessage("en").equals("Pronunciation Lab is temporarily unavailable. Try again later."),
                "a timeout reads as the provider being unavailable");
        check(last(view).equals(PronunciationLabProtocol.cancelScript(id)), "the page is told to stop the request");
        check(fixture.view() == view && !view.destroyed, "a started page stays, so its downloads serve the next request");
        view.post(result(id, "유메"));
        Handler.drain();
        check(slow.calls() == 1, "a late answer for a timed-out request is ignored");
        check(PronunciationLabRepository.REQUEST_TIMEOUT_MS == 90_000L, "90 s per request");
    }

    private static void aPageThatNeverStartsIsReplaced() throws Exception {
        Fixture fixture = new Fixture(true);
        Answer answer = new Answer();
        fixture.start(answer, "夢");
        WebView broken = fixture.view();
        Handler.timeOut();
        check(answer.failure().code.equals(PronunciationLabProtocol.TIMEOUT), "a request on a silent page times out");
        check(fixture.view() == null && broken.destroyed && broken.scripts.isEmpty(), "a page that never said ready is dropped");
        Answer retry = new Answer();
        String id = fixture.start(retry, "猫");
        WebView fresh = fixture.view();
        check(fresh != null && fresh != broken, "the next request loads a new page");
        broken.post(READY);
        broken.post(result(id, "stale"));
        Handler.drain();
        check(fresh.scripts.isEmpty() && retry.calls() == 0, "messages from the dropped page reach nothing");
        fresh.post(READY);
        Handler.drain();
        fresh.post(result(id, "네코"));
        Handler.drain();
        check(retry.lines.get(0).equals(Collections.singletonList("네코")), "the new page answers");
    }

    private static void rendererLossFailsRequestsAndRecovers() throws Exception {
        Fixture fixture = new Fixture(true);
        Answer inFlight = new Answer();
        Answer queuedBehind = new Answer();
        String id = fixture.start(inFlight, "夢");
        WebView old = fixture.view();
        old.post(READY);
        Handler.drain();
        fixture.start(queuedBehind, "猫");
        check(old.crash(true), "a dead renderer is handled, so the app or Spotify keeps running");
        check(inFlight.failure().code.equals(PronunciationLabProtocol.UNAVAILABLE) && inFlight.failure().getMessage().contains("crashed")
                && queuedBehind.failure().code.equals(PronunciationLabProtocol.UNAVAILABLE), "its requests fail at once");
        check(Handler.delayed() == 0, "their timeouts stop");
        Handler.drain();
        check(old.destroyed && fixture.view() == null, "the dead page is destroyed");
        int scripts = old.scripts.size();
        old.post(result(id, "stale"));
        Handler.drain();
        check(inFlight.calls() == 1 && old.scripts.size() == scripts, "the dead page's late messages reach nothing");

        Answer next = new Answer();
        String nextId = fixture.start(next, "猫");
        WebView fresh = fixture.view();
        check(fresh != null && fresh != old && fresh.scripts.isEmpty(), "the next request loads a new page and waits for its ready");
        fresh.post(READY);
        Handler.drain();
        check(idOf(last(fresh)).equals(nextId) && fixture.module.assets.reads == 1, "the new page takes the request");
        check(fresh.crash(false) && next.failure().getMessage().contains("stopped"), "a renderer the system stopped is handled too");
        Handler.drain();
        check(old.crash(true), "a report from an already dropped page is still handled");
    }

    private static void pageLoadErrors() throws Exception {
        Fixture fixture = new Fixture(true);
        Answer answer = new Answer();
        fixture.start(answer, "夢");
        WebView view = fixture.view();
        view.fail(false);
        check(answer.calls() == 0 && fixture.view() == view, "a failed subresource is the page's to report (LOAD_FAILED)");
        view.fail(true);
        Handler.drain();
        check(answer.failure().code.equals(PronunciationLabProtocol.UNAVAILABLE) && view.destroyed && fixture.view() == null,
                "a failed page load fails its requests and drops the page");
    }

    private static void startFailuresRetryCleanly() throws Exception {
        Fixture missing = new Fixture(false);
        Answer refused = new Answer();
        missing.start(refused, "夢");
        check(refused.failure().code.equals(PronunciationLabProtocol.UNAVAILABLE) && missing.view() == null && Handler.delayed() == 0,
                "a missing page asset fails at once, without a timeout");
        missing.addPage();
        Answer retry = new Answer();
        String id = missing.start(retry, "猫");
        missing.view().post(READY);
        Handler.drain();
        missing.view().post(result(id, "네코"));
        Handler.drain();
        check(retry.lines.size() == 1, "the asset is read again on the next request");

        Fixture noWebView = new Fixture(true);
        WebView.throwCreate = true;
        Answer noPackage = new Answer();
        noWebView.start(noPackage, "夢");
        WebView.throwCreate = false;
        check(noPackage.failure().code.equals(PronunciationLabProtocol.UNAVAILABLE) && noPackage.failure().getMessage().contains("WebView")
                && noWebView.view() == null && Handler.delayed() == 0, "a device without WebView fails the request at once");
        Answer later = new Answer();
        noWebView.start(later, "猫");
        check(noWebView.view() != null && later.calls() == 0, "WebView creation is tried again later");

        Fixture setupFails = new Fixture(true);
        WebView.throwLoad = true;
        Answer setup = new Answer();
        setupFails.start(setup, "夢");
        WebView.throwLoad = false;
        Handler.drain();
        check(setup.failure().code.equals(PronunciationLabProtocol.UNAVAILABLE) && setupFails.view() == null
                && WebView.instances.get(WebView.instances.size() - 1).destroyed, "a failed page setup releases its WebView");

        Fixture scriptFails = new Fixture(true);
        Answer one = new Answer();
        Answer two = new Answer();
        scriptFails.start(one, "夢");
        scriptFails.start(two, "猫");
        WebView view = scriptFails.view();
        WebView.throwEvaluate = true;
        view.post(READY);
        Handler.drain();
        WebView.throwEvaluate = false;
        check(one.failure().code.equals(PronunciationLabProtocol.UNAVAILABLE) && two.failure().code.equals(PronunciationLabProtocol.UNAVAILABLE)
                && view.destroyed && scriptFails.view() == null && Handler.delayed() == 0, "a page that rejects scripts fails every request");
    }

    private static void cancelFromAnyThread() throws Exception {
        Fixture fixture = new Fixture(true);
        Answer queued = new Answer();
        String queuedId = fixture.start(queued, "夢");
        fixture.repository.cancel(queuedId);
        Handler.drain();
        WebView view = fixture.view();
        check(queued.failure().code.equals(PronunciationLabProtocol.ABORTED) && view.scripts.isEmpty(),
                "a request cancelled before ready never reaches the page");
        view.post(READY);
        Handler.drain();
        check(view.scripts.isEmpty(), "and is not sent once the page starts");
        Answer sent = new Answer();
        String sentId = fixture.start(sent, "猫");
        fixture.repository.cancel(sentId);
        Handler.drain();
        check(sent.failure().code.equals(PronunciationLabProtocol.ABORTED) && last(view).equals(PronunciationLabProtocol.cancelScript(sentId)),
                "a sent request is cancelled in the page");
        view.post(error(sentId, PronunciationLabProtocol.ABORTED, "[Pronunciation Lab] Request cancelled"));
        Handler.drain();
        check(sent.calls() == 1 && Handler.delayed() == 0, "the page's own ABORTED answer is ignored");
        fixture.repository.cancel(sentId);
        Handler.drain();
        check(sent.calls() == 1, "cancelling a finished request does nothing");
    }

    private static void blockingCallsWaitOnWorkerThreads() throws Exception {
        Fixture fixture = new Fixture(true);
        try {
            fixture.repository.convert(request("夢"));
            check(false, "the main thread must not wait for its own answer");
        } catch (IllegalStateException expected) {
            check(true, "the main thread never waits for the page");
        }
        ExecutorService worker = Executors.newSingleThreadExecutor();
        check(worker.submit(() -> fixture.repository.convert(new PronunciationLabProtocol.Request(
                Collections.emptyList(), "ja", "ko", "translation"))).get(5, TimeUnit.SECONDS).isEmpty()
                && fixture.view() == null, "no lines need no page");

        Future<List<String>> answer = worker.submit(() -> fixture.repository.convert(request("猫")));
        check(Handler.awaitPost(5000), "the worker hands its request to the main thread");
        Handler.drain();
        WebView view = fixture.view();
        view.post(READY);
        Handler.drain();
        view.post(result(idOf(last(view)), "네코"));
        Handler.drain();
        check(answer.get(5, TimeUnit.SECONDS).equals(Collections.singletonList("네코")), "the waiting worker gets the page's lines");

        Future<List<String>> unsupported = worker.submit(() -> fixture.repository.convert(request("Привет")));
        check(Handler.awaitPost(5000), "a second request");
        Handler.drain();
        view.post(error(idOf(last(view)), PronunciationLabProtocol.UNSUPPORTED_SOURCE, "ru"));
        Handler.drain();
        try {
            unsupported.get(5, TimeUnit.SECONDS);
            check(false, "a page error reaches the worker");
        } catch (ExecutionException failed) {
            check(failed.getCause() instanceof PronunciationLabProtocol.Failure
                    && ((PronunciationLabProtocol.Failure) failed.getCause()).code.equals(PronunciationLabProtocol.UNSUPPORTED_SOURCE),
                    "a page error is thrown to the worker with its code");
        }

        Future<List<String>> interrupted = worker.submit(() -> fixture.repository.convert(request("夢")));
        check(Handler.awaitPost(5000), "a third request");
        Handler.drain();
        String id = idOf(last(view));
        worker.shutdownNow();
        try {
            interrupted.get(5, TimeUnit.SECONDS);
            check(false, "an interrupted worker stops waiting");
        } catch (ExecutionException stopped) {
            check(stopped.getCause() instanceof InterruptedException, "an interrupted worker stops waiting");
        }
        check(Handler.awaitPost(5000), "the interrupted worker cancels its request");
        Handler.drain();
        check(last(view).equals(PronunciationLabProtocol.cancelScript(id)) && Handler.delayed() == 0,
                "an interrupted caller cancels its request in the page");
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainThread();
        moduleAssetsReadyQueueAndOnePage();
        sharedRepositoryKeepsTheApplicationContext();
        concurrentRequestsAndErrorCodes();
        timeoutCancelsInThePageAndKeepsAStartedPage();
        aPageThatNeverStartsIsReplaced();
        rendererLossFailsRequestsAndRecovers();
        pageLoadErrors();
        startFailuresRetryCleanly();
        cancelFromAnyThread();
        blockingCallsWaitOnWorkerThreads();
        System.out.println("PASS " + assertions + " assertions: module assets, HTTPS origin and bridge, ready queue, one page,"
                + " concurrent ids, error codes, timeout and cancel, page/renderer loss and recovery, worker-thread calls");
    }
}
