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
    final String speakerColor;
    final String kind;
    final List<VocalPart> vocalParts;
    final String pronunciationText;
    final String translationText;
    final String furiganaText;

    LyricsLine(long startTimeMs, long endTimeMs, String text, List<Syllable> syllables) {
        this(startTimeMs, endTimeMs, text, syllables, "", "vocal", Collections.emptyList(), "", "", "");
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
        this(startTimeMs, endTimeMs, text, syllables, speaker, kind, vocalParts, "", "", "");
    }

    LyricsLine(
            long startTimeMs,
            long endTimeMs,
            String text,
            List<Syllable> syllables,
            String speaker,
            String speakerColor,
            String kind,
            List<VocalPart> vocalParts
    ) {
        this(startTimeMs, endTimeMs, text, syllables, speaker, speakerColor, kind, vocalParts, "", "", "");
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
            String translationText,
            String furiganaText
    ) {
        this(startTimeMs, endTimeMs, text, syllables, speaker, "", kind, vocalParts, pronunciationText, translationText, furiganaText);
    }

    LyricsLine(
            long startTimeMs,
            long endTimeMs,
            String text,
            List<Syllable> syllables,
            String speaker,
            String speakerColor,
            String kind,
            List<VocalPart> vocalParts,
            String pronunciationText,
            String translationText,
            String furiganaText
    ) {
        this.startTimeMs = Math.max(0L, startTimeMs);
        this.endTimeMs = Math.max(this.startTimeMs, endTimeMs);
        this.text = text == null ? "" : text;
        this.syllables = syllables == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(syllables));
        this.speaker = speaker == null ? "" : speaker;
        this.speakerColor = speakerColor == null ? "" : speakerColor;
        this.kind = kind == null || kind.trim().isEmpty() ? "vocal" : kind.trim();
        this.vocalParts = vocalParts == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(vocalParts));
        this.pronunciationText = pronunciationText == null ? "" : pronunciationText;
        this.translationText = translationText == null ? "" : translationText;
        this.furiganaText = furiganaText == null ? "" : furiganaText;
    }

    boolean isTimed() {
        return startTimeMs > 0 || endTimeMs > startTimeMs;
    }

    LyricsLine withSupplements(String pronunciation, String translation) {
        return withSupplements(pronunciation, translation, furiganaText);
    }

    LyricsLine withSupplements(String pronunciation, String translation, String furigana) {
        return new LyricsLine(
                startTimeMs,
                endTimeMs,
                text,
                syllables,
                speaker,
                speakerColor,
                kind,
                vocalParts,
                pronunciation,
                translation,
                furigana
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
        final String speakerColor;
        final String kind;
        final String text;
        final List<Syllable> syllables;
        final long startTimeMs;
        final long endTimeMs;
        final String pronunciationText;
        final String translationText;
        final String furiganaText;

        VocalPart(String id, String role, String speaker, String kind, String text, List<Syllable> syllables) {
            this(id, role, speaker, kind, text, syllables, "", "", "");
        }

        VocalPart(String id, String role, String speaker, String speakerColor, String kind, String text, List<Syllable> syllables) {
            this(id, role, speaker, speakerColor, kind, text, syllables, "", "", "");
        }

        VocalPart(
                String id,
                String role,
                String speaker,
                String kind,
                String text,
                List<Syllable> syllables,
                String pronunciationText,
                String translationText,
                String furiganaText
        ) {
            this(id, role, speaker, "", kind, text, syllables, pronunciationText, translationText, furiganaText);
        }

        VocalPart(
                String id,
                String role,
                String speaker,
                String speakerColor,
                String kind,
                String text,
                List<Syllable> syllables,
                String pronunciationText,
                String translationText,
                String furiganaText
        ) {
            this.id = id == null ? "" : id;
            this.role = role == null ? "" : role;
            this.speaker = speaker == null ? "" : speaker;
            this.speakerColor = speakerColor == null ? "" : speakerColor;
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
            this.furiganaText = furiganaText == null ? "" : furiganaText;
        }

        VocalPart withSupplements(String pronunciation, String translation) {
            return withSupplements(pronunciation, translation, furiganaText);
        }

        VocalPart withSupplements(String pronunciation, String translation, String furigana) {
            return new VocalPart(
                    id,
                    role,
                    speaker,
                    speakerColor,
                    kind,
                    text,
                    syllables,
                    pronunciation,
                    translation,
                    furigana
            );
        }
    }
}
