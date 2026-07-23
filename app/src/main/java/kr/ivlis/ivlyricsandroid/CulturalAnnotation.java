package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class CulturalAnnotation {
    final int lineIndex;
    final String expression;
    final String note;

    CulturalAnnotation(int lineIndex, String expression, String note) {
        this.lineIndex = Math.max(0, lineIndex);
        this.expression = expression == null ? "" : expression.trim();
        this.note = compactNote(note);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CulturalAnnotation)) return false;
        CulturalAnnotation annotation = (CulturalAnnotation) other;
        return lineIndex == annotation.lineIndex
                && expression.equals(annotation.expression)
                && note.equals(annotation.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineIndex, expression, note);
    }

    static List<CulturalAnnotation> forLine(List<CulturalAnnotation> annotations, int lineIndex, String text) {
        if (annotations == null || annotations.isEmpty()) {
            return Collections.emptyList();
        }
        String source = text == null ? "" : text;
        List<CulturalAnnotation> matches = new ArrayList<>();
        for (CulturalAnnotation annotation : annotations) {
            if (annotation != null
                    && annotation.lineIndex == lineIndex
                    && !annotation.expression.isEmpty()
                    && source.contains(annotation.expression)
                    && !annotation.note.isEmpty()) {
                matches.add(annotation);
            }
        }
        matches.sort(Comparator
                .comparingInt((CulturalAnnotation value) -> source.indexOf(value.expression))
                .thenComparingInt(value -> value.expression.length()));
        return matches;
    }

    static String annotateText(String text, List<CulturalAnnotation> annotations) {
        String source = text == null ? "" : text;
        if (source.isEmpty() || annotations == null || annotations.isEmpty()) {
            return source;
        }
        List<Marker> markers = markers(source, annotations);
        StringBuilder result = new StringBuilder(source);
        for (int index = markers.size() - 1; index >= 0; index--) {
            Marker marker = markers.get(index);
            result.insert(marker.endOffset, "[" + marker.number + "]");
        }
        return result.toString();
    }

    static List<LyricsLine.Syllable> annotateSyllables(
            String text,
            List<LyricsLine.Syllable> syllables,
            List<CulturalAnnotation> annotations
    ) {
        if (syllables == null || syllables.isEmpty() || annotations == null || annotations.isEmpty()) {
            return syllables == null ? Collections.emptyList() : syllables;
        }
        String source = text == null ? "" : text;
        StringBuilder syllableText = new StringBuilder();
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable != null) {
                syllableText.append(syllable.text);
            }
        }
        String markerSource = source.equals(syllableText.toString())
                ? source
                : syllableText.toString();
        List<Marker> markers = markers(markerSource, annotations);
        if (markers.isEmpty()) {
            return syllables;
        }
        List<LyricsLine.Syllable> result = new ArrayList<>(syllables.size());
        int sourceOffset = 0;
        int markerIndex = 0;
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null) {
                continue;
            }
            StringBuilder value = new StringBuilder(syllable.text);
            int endOffset = sourceOffset + syllable.text.length();
            List<Marker> localMarkers = new ArrayList<>();
            while (markerIndex < markers.size() && markers.get(markerIndex).endOffset <= endOffset) {
                Marker marker = markers.get(markerIndex++);
                if (marker.endOffset > sourceOffset) {
                    localMarkers.add(marker);
                }
            }
            for (int index = localMarkers.size() - 1; index >= 0; index--) {
                Marker marker = localMarkers.get(index);
                value.insert(marker.endOffset - sourceOffset, "[" + marker.number + "]");
            }
            result.add(new LyricsLine.Syllable(value.toString(), syllable.startTimeMs, syllable.endTimeMs));
            sourceOffset = endOffset;
        }
        return result;
    }

    static String compactNote(String value) {
        String note = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (note.isEmpty()) {
            return "";
        }
        int sentenceEnd = firstSentenceEnd(note);
        if (sentenceEnd > 0) {
            note = note.substring(0, sentenceEnd);
        }
        if (note.length() > 72) {
            note = note.substring(0, 71).trim() + "…";
        }
        return note;
    }

    private static int firstSentenceEnd(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '.' || current == '!' || current == '?' || current == '。'
                    || current == '！' || current == '？') {
                return index + 1;
            }
        }
        return -1;
    }

    private static List<Marker> markers(String source, List<CulturalAnnotation> annotations) {
        List<Marker> markers = new ArrayList<>();
        int searchFrom = 0;
        for (int index = 0; index < annotations.size(); index++) {
            CulturalAnnotation annotation = annotations.get(index);
            if (annotation == null || annotation.expression.isEmpty()) {
                continue;
            }
            int start = source.indexOf(annotation.expression, searchFrom);
            if (start < 0) {
                start = source.indexOf(annotation.expression);
            }
            if (start < 0) {
                continue;
            }
            int end = start + annotation.expression.length();
            markers.add(new Marker(end, index + 1));
            searchFrom = end;
        }
        markers.sort(Comparator.comparingInt(value -> value.endOffset));
        return markers;
    }

    private static final class Marker {
        final int endOffset;
        final int number;

        Marker(int endOffset, int number) {
            this.endOffset = endOffset;
            this.number = number;
        }
    }
}
