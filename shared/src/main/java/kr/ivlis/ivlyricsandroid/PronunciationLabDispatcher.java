package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Pronunciation Lab requests waiting for the page, without a WebView or threads: the repository
 * calls it on the main thread only. Requests wait until the page posts "ready", then go to the page
 * in the order they came, keyed by id. Each request ends exactly once: with the page's lines, its
 * error, a cancel or timeout, or when the page goes away.
 */
final class PronunciationLabDispatcher {
    /** Evaluates a script in the current page. */
    interface Page {
        void evaluate(String script);
    }

    interface Listener {
        /** Either {@code lines} (one per requested line) or {@code failure} is set. */
        void onFinished(List<String> lines, PronunciationLabProtocol.Failure failure);
    }

    private static final class Entry {
        final String id;
        final PronunciationLabProtocol.Request request;
        final Listener listener;
        boolean sent;

        Entry(String id, PronunciationLabProtocol.Request request, Listener listener) {
            this.id = id;
            this.request = request;
            this.listener = listener;
        }
    }

    private final Page page;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean ready;

    PronunciationLabDispatcher(Page page) {
        this.page = page;
    }

    /** The page posted "ready" since the last {@link #reset}. */
    boolean isReady() {
        return ready;
    }

    /** Requests that have not finished yet. */
    int size() {
        return entries.size();
    }

    /** Sends the request now when the page is ready, otherwise once it posts "ready". */
    void submit(String id, PronunciationLabProtocol.Request request, Listener listener) {
        Entry previous = entries.remove(id);
        if (previous != null) {
            // The page replaces a running request with the same id; the older caller gets no answer otherwise.
            previous.listener.onFinished(null,
                    new PronunciationLabProtocol.Failure(PronunciationLabProtocol.ABORTED, "Replaced by a newer request"));
        }
        Entry entry = new Entry(id, request, listener);
        entries.put(id, entry);
        if (ready) {
            send(entry);
        }
    }

    /** A message from the current page. */
    void handle(PronunciationLabProtocol.Message message) {
        if (message == null) {
            return;
        }
        switch (message.kind) {
            case READY:
                pageReady();
                return;
            case RESULT: {
                Entry entry = finishing(message.id);
                if (entry == null) return;
                if (message.lines.size() != entry.request.lines.size()) {
                    entry.listener.onFinished(null, new PronunciationLabProtocol.Failure(PronunciationLabProtocol.FAILED,
                            "Expected " + entry.request.lines.size() + " lines, got " + message.lines.size()));
                } else {
                    entry.listener.onFinished(message.lines, null);
                }
                return;
            }
            case ERROR: {
                Entry entry = finishing(message.id);
                if (entry != null) {
                    entry.listener.onFinished(null, new PronunciationLabProtocol.Failure(message.code, message.text));
                }
                return;
            }
            default:
                // LINE (streaming is never requested), UNKNOWN and MALFORMED messages carry no answer.
        }
    }

    /**
     * Ends a request with {@code reason}; the page is told to stop when it already has the request.
     * False when the request had already finished.
     */
    boolean cancel(String id, PronunciationLabProtocol.Failure reason) {
        Entry entry = entries.remove(id);
        if (entry == null) {
            return false;
        }
        try {
            if (entry.sent) {
                page.evaluate(PronunciationLabProtocol.cancelScript(id));
            }
        } finally {
            entry.listener.onFinished(null, reason);
        }
        return true;
    }

    /** The page is gone: every request fails with {@code reason}, and the next page must post "ready" again. */
    void reset(PronunciationLabProtocol.Failure reason) {
        ready = false;
        List<Entry> failed = new ArrayList<>(entries.values());
        entries.clear();
        for (Entry entry : failed) {
            entry.listener.onFinished(null, reason);
        }
    }

    private void pageReady() {
        if (ready) {
            return;
        }
        ready = true;
        for (Entry entry : new ArrayList<>(entries.values())) {
            // A listener may have cancelled a later entry; send only those still waiting.
            if (!entry.sent && entries.get(entry.id) == entry) {
                send(entry);
            }
        }
    }

    /** Removes the request a result or error answers; null for an id that is not waiting on the page. */
    private Entry finishing(String id) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.sent) {
            return null;
        }
        entries.remove(id);
        return entry;
    }

    private void send(Entry entry) {
        entry.sent = true;
        page.evaluate(PronunciationLabProtocol.convertScript(entry.id, entry.request));
    }
}
