package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Request bookkeeping between the app and the Pronunciation Lab page, without a WebView. */
public final class PronunciationLabDispatcherTest {
    private final List<String> scripts = new ArrayList<>();
    private RuntimeException evaluateFailure;
    private Runnable afterEvaluate;
    private final PronunciationLabDispatcher dispatcher = new PronunciationLabDispatcher(script -> {
        if (evaluateFailure != null) throw evaluateFailure;
        scripts.add(script);
        Runnable hook = afterEvaluate;
        afterEvaluate = null;
        if (hook != null) hook.run();
    });

    private static final class Answer implements PronunciationLabDispatcher.Listener {
        int calls;
        List<String> lines;
        PronunciationLabProtocol.Failure failure;

        @Override public void onFinished(List<String> lines, PronunciationLabProtocol.Failure failure) {
            calls++;
            this.lines = lines;
            this.failure = failure;
        }
    }

    private static PronunciationLabProtocol.Request request(String... lines) {
        return new PronunciationLabProtocol.Request(Arrays.asList(lines), "ja", "ko", "translation");
    }

    private void post(String json) {
        dispatcher.handle(PronunciationLabProtocol.parse(json));
    }

    @Test public void requestsWaitForReadyAndGoInOrder() {
        Answer first = new Answer();
        Answer second = new Answer();
        dispatcher.submit("p1", request("猫"), first);
        dispatcher.submit("p2", request("夢", "♪"), second);
        assertTrue(scripts.isEmpty());
        assertFalse(dispatcher.isReady());
        post("{\"type\":\"ready\"}");
        assertTrue(dispatcher.isReady());
        assertEquals(Arrays.asList(
                PronunciationLabProtocol.convertScript("p1", request("猫")),
                PronunciationLabProtocol.convertScript("p2", request("夢", "♪"))), scripts);
        post("{\"type\":\"ready\"}");
        assertEquals("a second ready sends nothing twice", 2, scripts.size());

        Answer third = new Answer();
        dispatcher.submit("p3", request("a"), third);
        assertEquals("a ready page takes requests at once", 3, scripts.size());
        assertEquals(3, dispatcher.size());
    }

    @Test public void answersReachTheirOwnRequestExactlyOnce() {
        Answer first = new Answer();
        Answer second = new Answer();
        post("{\"type\":\"ready\"}");
        dispatcher.submit("p1", request("猫"), first);
        dispatcher.submit("p2", request("夢", "♪"), second);
        post("{\"type\":\"result\",\"id\":\"p2\",\"lines\":[\"유메\",\"♪\"],\"version\":\"1.0.2\"}");
        assertEquals(0, first.calls);
        assertEquals(1, second.calls);
        assertEquals(Arrays.asList("유메", "♪"), second.lines);
        assertNull(second.failure);

        post("{\"type\":\"error\",\"id\":\"p1\",\"code\":\"PRONUNCIATION_LAB_LOAD_FAILED\",\"message\":\"offline\"}");
        assertEquals(1, first.calls);
        assertNull(first.lines);
        assertEquals(PronunciationLabProtocol.LOAD_FAILED, first.failure.code);
        assertTrue(first.failure.getMessage().contains("offline"));

        // Late or repeated answers (a cancelled or finished id) change nothing.
        post("{\"type\":\"result\",\"id\":\"p1\",\"lines\":[\"네코\"]}");
        post("{\"type\":\"error\",\"id\":\"p2\",\"code\":\"PRONUNCIATION_LAB_ABORTED\"}");
        post("{\"type\":\"result\",\"id\":\"unknown\",\"lines\":[]}");
        assertEquals(1, first.calls);
        assertEquals(1, second.calls);
        assertEquals(0, dispatcher.size());
    }

    @Test public void answersWithTheWrongLineCountFail() {
        Answer answer = new Answer();
        post("{\"type\":\"ready\"}");
        dispatcher.submit("p1", request("a", "b"), answer);
        post("{\"type\":\"result\",\"id\":\"p1\",\"lines\":[\"a\"]}");
        assertEquals(1, answer.calls);
        assertEquals(PronunciationLabProtocol.FAILED, answer.failure.code);
    }

    @Test public void messagesWithoutAnAnswerAreIgnored() {
        Answer answer = new Answer();
        post("{\"type\":\"ready\"}");
        dispatcher.submit("p1", request("a"), answer);
        post("{\"type\":\"line\",\"id\":\"p1\",\"index\":0,\"text\":\"a\"}");
        post("{\"type\":\"progress\",\"id\":\"p1\"}");
        post("not json");
        dispatcher.handle(null);
        assertEquals(0, answer.calls);
        assertEquals(1, dispatcher.size());
    }

    @Test public void answersForRequestsThePageNeverReceivedAreIgnored() {
        Answer answer = new Answer();
        dispatcher.submit("p1", request("a"), answer);
        post("{\"type\":\"result\",\"id\":\"p1\",\"lines\":[\"a\"]}");
        assertEquals(0, answer.calls);
        assertEquals(1, dispatcher.size());
    }

    @Test public void cancelTellsThePageOnlyAboutRequestsItHas() {
        Answer queued = new Answer();
        dispatcher.submit("p1", request("a"), queued);
        PronunciationLabProtocol.Failure aborted =
                new PronunciationLabProtocol.Failure(PronunciationLabProtocol.ABORTED, "cancelled");
        assertTrue(dispatcher.cancel("p1", aborted));
        assertEquals(1, queued.calls);
        assertEquals(aborted, queued.failure);
        assertTrue("a queued request never reaches the page", scripts.isEmpty());
        post("{\"type\":\"ready\"}");
        assertTrue(scripts.isEmpty());

        Answer sent = new Answer();
        dispatcher.submit("p2", request("a"), sent);
        PronunciationLabProtocol.Failure timeout =
                new PronunciationLabProtocol.Failure(PronunciationLabProtocol.TIMEOUT, "90 s");
        assertTrue(dispatcher.cancel("p2", timeout));
        assertEquals(PronunciationLabProtocol.cancelScript("p2"), scripts.get(scripts.size() - 1));
        assertEquals(timeout, sent.failure);
        assertFalse("a finished request cannot be cancelled again", dispatcher.cancel("p2", timeout));
        assertEquals(1, sent.calls);
    }

    @Test public void cancelStillEndsTheRequestWhenThePageFails() {
        Answer sent = new Answer();
        post("{\"type\":\"ready\"}");
        dispatcher.submit("p1", request("a"), sent);
        evaluateFailure = new IllegalStateException("destroyed WebView");
        try {
            dispatcher.cancel("p1", new PronunciationLabProtocol.Failure(PronunciationLabProtocol.ABORTED, ""));
            fail("the page failure reaches the repository");
        } catch (IllegalStateException expected) {
            assertEquals(1, sent.calls);
            assertEquals(0, dispatcher.size());
        }
    }

    @Test public void resetFailsEveryRequestAndWaitsForTheNextPage() {
        Answer queued = new Answer();
        Answer sent = new Answer();
        dispatcher.submit("p1", request("a"), queued);
        post("{\"type\":\"ready\"}");
        dispatcher.submit("p2", request("b"), sent);
        int scriptsBefore = scripts.size();
        PronunciationLabProtocol.Failure gone =
                new PronunciationLabProtocol.Failure(PronunciationLabProtocol.UNAVAILABLE, "renderer crashed");
        dispatcher.reset(gone);
        assertEquals(gone, queued.failure);
        assertEquals(gone, sent.failure);
        assertEquals(1, queued.calls);
        assertEquals(1, sent.calls);
        assertFalse(dispatcher.isReady());
        assertEquals(0, dispatcher.size());
        assertEquals("a gone page gets no cancel scripts", scriptsBefore, scripts.size());

        Answer next = new Answer();
        dispatcher.submit("p3", request("c"), next);
        assertEquals("the next page must post ready first", scriptsBefore, scripts.size());
        post("{\"type\":\"ready\"}");
        assertEquals(PronunciationLabProtocol.convertScript("p3", request("c")), scripts.get(scripts.size() - 1));
    }

    @Test public void aRepeatedIdEndsTheOlderRequest() {
        Answer older = new Answer();
        Answer newer = new Answer();
        post("{\"type\":\"ready\"}");
        dispatcher.submit("p1", request("a"), older);
        dispatcher.submit("p1", request("b"), newer);
        assertEquals(1, older.calls);
        assertEquals(PronunciationLabProtocol.ABORTED, older.failure.code);
        post("{\"type\":\"result\",\"id\":\"p1\",\"lines\":[\"B\"]}");
        assertEquals(Arrays.asList("B"), newer.lines);
        assertEquals(1, older.calls);
    }

    @Test public void aRequestCancelledWhileTheQueueIsSentIsSkipped() {
        Answer first = new Answer();
        Answer second = new Answer();
        dispatcher.submit("p1", request("a"), first);
        dispatcher.submit("p2", request("b"), second);
        // Sending p1 runs code that cancels p2 before the flush reaches it.
        afterEvaluate = () -> dispatcher.cancel("p2", new PronunciationLabProtocol.Failure(PronunciationLabProtocol.ABORTED, ""));
        post("{\"type\":\"ready\"}");
        assertEquals(1, scripts.size());
        assertTrue(scripts.get(0).contains("\"p1\""));
        assertEquals(1, second.calls);
        assertEquals(PronunciationLabProtocol.ABORTED, second.failure.code);
        assertEquals(0, first.calls);
        assertEquals(1, dispatcher.size());
    }
}
