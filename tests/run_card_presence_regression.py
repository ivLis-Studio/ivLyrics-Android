#!/usr/bin/env python3
"""Check missing Spotify lyrics-card insertion against the production Java helper.

JDK only: no Android build, installed Spotify APK, account, device, or network.
The adapter's song eligibility uses synthetic reflection bindings, not host hooks.
"""
import hashlib
import subprocess

from regression_runtime import ROOT, REPORTS, java_tool


def declaration(text, signature):
    if text.count(signature) != 1:
        raise ValueError(f"Expected one source declaration: {signature}")
    start = text.index(signature)
    cursor = text.index("{", start) + 1
    depth = 1
    while depth:
        depth += (text[cursor] == "{") - (text[cursor] == "}")
        cursor += 1
    return text[start:cursor]


source = ROOT / "spotify-module/src/main/java/dev/ivlyrics/spotify/SpotifyLyricsCardPresence.java"
method = declaration(source.read_text(), "static List<?> withLyricsIfMissing(")
adapter = source.with_name("ComposeAdapter.java")
adapter_methods = [declaration(adapter.read_text(), signature) for signature in (
    "static Object createSongCard(boolean oldVersion, Object track)",
    "private static String value(Map<String, String> metadata, String key)",
)]
work = REPORTS / "card-presence"
work.mkdir(parents=True, exist_ok=True)
test = work / "CardPresenceRegression.java"
test.write_text("import java.util.*;\nimport java.lang.reflect.*;\npublic final class CardPresenceRegression {\n"
                + method + "\n" + "\n".join(adapter_methods) + r'''
    private int assertions;
    private static class LyricsCard { }
    private static final class LyricsCardSubtype extends LyricsCard { }
    private static final class LiveEvent { }
    private static final class Recommendation { }
    public static final class Track {
        final String uri;
        final Map<String, String> metadata;
        Track(String uri, Map<String, String> metadata) { this.uri = uri; this.metadata = metadata; }
        public String uri() { return uri; }
        public Map<String, String> metadata() { return metadata; }
    }
    public static final class Model {
        final String uri, availability, title, artist, image;
        public Model(Object emptyLyrics, String uri, String availability,
                     String title, String artist, String image) {
            this.uri = uri; this.availability = availability;
            this.title = title; this.artist = artist; this.image = image;
        }
    }
    public static final class SongCard extends LyricsCard {
        final Model model;
        public SongCard(Model model) { this.model = model; }
    }
    private static final class Availability {
        final Method uri = Track.class.getMethod("uri");
        final Method metadata = Track.class.getMethod("metadata");
        final Object emptyLyrics = new Object();
        final Constructor<?> model = Model.class.getConstructor(Object.class, String.class,
                String.class, String.class, String.class, String.class);
        final Constructor<?> card = SongCard.class.getConstructor(Model.class);
        Availability() throws ReflectiveOperationException { }
    }
    private static Availability availabilityFor(boolean oldVersion, Object track)
            throws ReflectiveOperationException {
        // Substitute reflection bindings only; song eligibility runs production code unchanged.
        return new Availability();
    }

    private void check(String label, boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private void sameItems(String label, List<?> expected, List<?> actual) {
        check(label + " size", expected.size() == actual.size());
        for (int index = 0; index < expected.size(); index++) {
            check(label + " item identity/order at " + index,
                    expected.get(index) == actual.get(index));
        }
    }
    private void missing(String label, List<Object> original, boolean immutable) {
        List<Object> source = new ArrayList<>(original);
        if (immutable) source = Collections.unmodifiableList(source);
        List<Object> snapshot = new ArrayList<>(source);
        LyricsCard candidate = new LyricsCard();
        List<?> result = withLyricsIfMissing(source, LyricsCard.class, candidate);
        check(label + " allocates when absent", result != source);
        check(label + " one card inserted", result.size() == source.size() + 1);
        check(label + " lyrics goes before other cards", result.get(0) == candidate);
        sameItems(label + " original list preserved", snapshot, source);
        sameItems(label + " existing cards remain ordered", snapshot,
                result.subList(1, result.size()));
        check(label + " repeated mapping is idempotent",
                withLyricsIfMissing(result, LyricsCard.class, new LyricsCard()) == result);
        check(label + " absent candidate preserves inserted list",
                withLyricsIfMissing(result, LyricsCard.class, null) == result);
    }
    private void existing(String label, List<Object> original, boolean immutable) {
        List<Object> source = new ArrayList<>(original);
        if (immutable) source = Collections.unmodifiableList(source);
        List<Object> snapshot = new ArrayList<>(source);
        List<?> result = withLyricsIfMissing(source, LyricsCard.class, new LyricsCard());
        check(label + " keeps existing list identity", result == source);
        sameItems(label + " existing Spotify layout is untouched", snapshot, result);
        check(label + " repeated mapping is idempotent",
                withLyricsIfMissing(result, LyricsCard.class, new LyricsCard()) == result);
        check(label + " absent candidate keeps original list",
                withLyricsIfMissing(source, LyricsCard.class, null) == source);
    }
    private void songEligibility() throws ReflectiveOperationException {
        for (boolean oldVersion : new boolean[]{false, true}) {
            for (String hasLyrics : new String[]{null, "false", "true"}) {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("title", "Synthetic song");
                metadata.put("artist_name", "Fixture artist");
                metadata.put("image_url", "fixture:image");
                if (hasLyrics != null) metadata.put("has_lyrics", hasLyrics);
                String label = "song eligibility, oldVersion=" + oldVersion + ", has_lyrics=" + hasLyrics;
                Track track = new Track("spotify:track:synthetic-song", metadata);
                Object candidate = createSongCard(oldVersion, track);
                check(label + " produces a song card independently", candidate instanceof SongCard);
                Model model = ((SongCard) candidate).model;
                check(label + " uses the event track URI", track.uri.equals(model.uri));
                check(label + " preserves title", "Synthetic song".equals(model.title));
                check(label + " preserves artist", "Fixture artist".equals(model.artist));
                check(label + " preserves image", "fixture:image".equals(model.image));
                List<?> result = withLyricsIfMissing(Collections.emptyList(), LyricsCard.class, candidate);
                check(label + " restores an absent server card", result.size() == 1 && result.get(0) == candidate);
                for (String uri : new String[]{null, "", "spotify:episode:synthetic-episode",
                        "spotify:ad:synthetic-ad", "spotify:local:synthetic-local"}) {
                    check(label + " rejects non-song URI " + uri,
                            createSongCard(oldVersion, new Track(uri, metadata)) == null);
                }
                metadata.put("parent_episode_uri", "spotify:episode:synthetic-parent");
                check(label + " rejects episode-backed track",
                        createSongCard(oldVersion, new Track(track.uri, metadata)) == null);
            }
            SongCard sparse = (SongCard) createSongCard(oldVersion,
                    new Track("spotify:track:next-song", Collections.emptyMap()));
            check("metadata defaults do not hide eligible song", sparse != null);
            check("new event never uses previous URI", "spotify:track:next-song".equals(sparse.model.uri));
            check("missing metadata defaults to empty strings", sparse.model.title.isEmpty()
                    && sparse.model.artist.isEmpty() && sparse.model.image.isEmpty());
        }
    }
    private void run() throws ReflectiveOperationException {
        Object live = new LiveEvent();
        Object recommendation = new Recommendation();
        LyricsCard nativeLyrics = new LyricsCard();
        LyricsCard subtype = new LyricsCardSubtype();
        for (boolean immutable : new boolean[]{false, true}) {
            String prefix = immutable ? "immutable " : "mutable ";
            missing(prefix + "empty source", Collections.emptyList(), immutable);
            missing(prefix + "live event only", Arrays.asList(live), immutable);
            missing(prefix + "ordered cards", Arrays.asList(live, recommendation), immutable);
            missing(prefix + "reversed cards", Arrays.asList(recommendation, live), immutable);
            missing(prefix + "repeated card identity", Arrays.asList(live, live), immutable);
            missing(prefix + "null item", Arrays.asList(null, live, null), immutable);
            existing(prefix + "lyrics only", Arrays.asList(nativeLyrics), immutable);
            existing(prefix + "lyrics first", Arrays.asList(nativeLyrics, live, recommendation), immutable);
            existing(prefix + "lyrics middle", Arrays.asList(live, nativeLyrics, recommendation), immutable);
            existing(prefix + "lyrics last", Arrays.asList(live, recommendation, nativeLyrics), immutable);
            existing(prefix + "lyrics subtype", Arrays.asList(live, subtype), immutable);
            existing(prefix + "already duplicate lyrics",
                    Arrays.asList(nativeLyrics, live, subtype, nativeLyrics), immutable);
            for (List<Object> original : Arrays.asList(Collections.<Object>emptyList(),
                    Arrays.asList(live, recommendation), Arrays.asList(null, live))) {
                List<Object> source = new ArrayList<>(original);
                if (immutable) source = Collections.unmodifiableList(source);
                check(prefix + "no candidate preserves source identity",
                        withLyricsIfMissing(source, LyricsCard.class, null) == source);
                check(prefix + "wrong candidate type preserves source identity",
                        withLyricsIfMissing(source, LyricsCard.class, new LiveEvent()) == source);
                sameItems(prefix + "no candidate preserves source items", original, source);
            }
        }
        songEligibility();
        System.out.println("PASS: " + assertions + " assertions against production card-presence helper");
        System.out.println("Missing Spotify lyrics cards are inserted first without changing existing item identity/order.");
        System.out.println("Existing lyrics cards, immutable lists, absent candidates, and repeated mapping are preserved.");
        System.out.println("Production song eligibility ignores has_lyrics and rejects non-song/episode-backed content.");
    }
    public static void main(String[] args) throws ReflectiveOperationException { new CardPresenceRegression().run(); }
}
''')
subprocess.run([java_tool("javac"), "-d", str(work), str(test)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "CardPresenceRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = "Production Java card-presence helper; synthetic card objects and local list fixtures only.\n"
report += f"SpotifyLyricsCardPresence.java SHA256 {hashlib.sha256(source.read_bytes()).hexdigest()}\n"
report += f"withLyricsIfMissing SHA256 {hashlib.sha256(method.encode()).hexdigest()}\n"
report += f"ComposeAdapter.java SHA256 {hashlib.sha256(adapter.read_bytes()).hexdigest()}\n"
report += f"createSongCard SHA256 {hashlib.sha256(adapter_methods[0].encode()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
