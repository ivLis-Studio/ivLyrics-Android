package kr.ivlis.ivlyricsandroid;

/** Presentation-only first-use state. It never reads or writes language preferences. */
final class WelcomeGreetingRotation {
    static final long INTERVAL_MS = 3500L;

    interface Scheduler {
        void postDelayed(Runnable callback, long delayMs);
        void removeCallbacks(Runnable callback);
    }

    interface Renderer {
        void show(boolean korean, boolean animate);
    }

    private final Scheduler scheduler;
    private final Renderer renderer;
    private boolean korean;
    private boolean languageInteracted;
    private boolean active;
    private Runnable pending;

    WelcomeGreetingRotation(boolean korean, boolean languageInteracted, Scheduler scheduler, Renderer renderer) {
        this.korean = korean;
        this.languageInteracted = languageInteracted;
        this.scheduler = scheduler;
        this.renderer = renderer;
    }

    boolean isKorean() { return korean; }
    boolean hasLanguageInteraction() { return languageInteracted; }

    void setActive(boolean nextActive) {
        if (active == nextActive) return;
        active = nextActive;
        cancelPending();
        if (active && !languageInteracted) schedule();
        else renderer.show(korean, false);
    }

    void interactWithLanguage(String language) {
        languageInteracted = true;
        korean = "ko".equals(language);
        cancelPending();
        renderer.show(korean, false);
    }

    private void schedule() {
        pending = new Runnable() {
            @Override public void run() {
                // A removed callback must stay inert even if it was already dequeued.
                if (pending != this || !active || languageInteracted) return;
                pending = null;
                korean = !korean;
                renderer.show(korean, true);
                schedule();
            }
        };
        scheduler.postDelayed(pending, INTERVAL_MS);
    }

    private void cancelPending() {
        if (pending != null) scheduler.removeCallbacks(pending);
        pending = null;
    }
}
