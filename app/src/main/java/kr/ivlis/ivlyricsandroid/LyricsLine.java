package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LyricsLine {
    final long startTimeMs;
    final long endTimeMs;
    final String text;
    final List<Syllable> syllables;
    final String speaker;
    final String kind;
    final List<VocalPart> vocalParts;
    final String pronunciationText;
    final String translationText;

    LyricsLine(long startTimeMs, long endTimeMs, String text, List<Syllable> syllables) {
        this(startTimeMs, endTimeMs, text, syllables, "", "vocal", Collections.emptyList(), "", "");
    }

    LyricsLine(
            long startTimeMs,
            long endTimeMs,
            String text,
            List<Syllable> syllables,
            String speaker,
            String kind,
            List<VocalPart> vocalParts
    ) {
        this(startTimeMs, endTimeMs, text, syllables, speaker, kind, vocalParts, "", "");
    }

    LyricsLine(
            long startTimeMs,
            long endTimeMs,
            String text,
            List<Syllable> syllables,
            String speaker,
            String kind,
            List<VocalPart> vocalParts,
            String pronunciationText,
            String translationText
    ) {
        this.startTimeMs = Math.max(0L, startTimeMs);
        this.endTimeMs = Math.max(this.startTimeMs, endTimeMs);
        this.text = text == null ? "" : text;
        this.syllables = syllables == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(syllables));
        this.speaker = speaker == null ? "" : speaker;
        this.kind = kind == null || kind.trim().isEmpty() ? "vocal" : kind.trim();
        this.vocalParts = vocalParts == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(vocalParts));
        this.pronunciationText = pronunciationText == null ? "" : pronunciationText;
        this.translationText = translationText == null ? "" : translationText;
    }

    boolean isTimed() {
        return startTimeMs > 0 || endTimeMs > startTimeMs;
    }

    LyricsLine withSupplements(String pronunciation, String translation) {
        return new LyricsLine(
                startTimeMs,
                endTimeMs,
                text,
                syllables,
                speaker,
                kind,
                vocalParts,
                pronunciation,
                translation
        );
    }

    static final class Syllable {
        final String text;
        final long startTimeMs;
        final long endTimeMs;

        Syllable(String text, long startTimeMs, long endTimeMs) {
            this.text = text == null ? "" : text;
            this.startTimeMs = Math.max(0L, startTimeMs);
            this.endTimeMs = Math.max(this.startTimeMs, endTimeMs);
        }
    }

    static final class VocalPart {
        final String id;
        final String role;
        final String speaker;
        final String kind;
        final String text;
        final List<Syllable> syllables;
        final long startTimeMs;
        final long endTimeMs;
        final String pronunciationText;
        final String translationText;

        VocalPart(String id, String role, String speaker, String kind, String text, List<Syllable> syllables) {
            this(id, role, speaker, kind, text, syllables, "", "");
        }

        VocalPart(
                String id,
                String role,
                String speaker,
                String kind,
                String text,
                List<Syllable> syllables,
                String pronunciationText,
                String translationText
        ) {
            this.id = id == null ? "" : id;
            this.role = role == null ? "" : role;
            this.speaker = speaker == null ? "" : speaker;
            this.kind = kind == null || kind.trim().isEmpty() ? "vocal" : kind.trim();
            this.text = text == null ? "" : text;
            this.syllables = syllables == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(syllables));
            this.startTimeMs = this.syllables.isEmpty() ? 0L : this.syllables.get(0).startTimeMs;
            this.endTimeMs = this.syllables.isEmpty()
                    ? this.startTimeMs
                    : this.syllables.get(this.syllables.size() - 1).endTimeMs;
            this.pronunciationText = pronunciationText == null ? "" : pronunciationText;
            this.translationText = translationText == null ? "" : translationText;
        }

        VocalPart withSupplements(String pronunciation, String translation) {
            return new VocalPart(
                    id,
                    role,
                    speaker,
                    kind,
                    text,
                    syllables,
                    pronunciation,
                    translation
            );
        }
    }
}
