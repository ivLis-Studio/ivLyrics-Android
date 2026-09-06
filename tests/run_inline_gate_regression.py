#!/usr/bin/env python3
"""Verify the production native inline gates against Spotify 9.1.80's truth table."""
import subprocess
import hashlib

from regression_runtime import ROOT, REPORTS, java_tool

source = ROOT / "spotify-module/src/main/java/dev/ivlyrics/spotify/SpotifyInlineLyrics.java"
text = source.read_text()
start = text.index("    static final class Gates {")
cursor = text.index("{", start) + 1
depth = 1
while depth:
    depth += (text[cursor] == "{") - (text[cursor] == "}")
    cursor += 1
gates = text[start:cursor]
work = REPORTS / "inline-gates"
work.mkdir(parents=True, exist_ok=True)
test = work / "InlineGateRegression.java"
test.write_text("public final class InlineGateRegression {\n" + gates + r'''
    public static void main(String[] args) {
        int assertions = 0;
        for (int bits = 0; bits < 128; bits++) {
            boolean canvas = (bits & 1) != 0, enabled = (bits & 2) != 0;
            boolean mixing = (bits & 4) != 0, automobile = (bits & 8) != 0;
            boolean automobileAllowed = (bits & 16) != 0, videoPlayer = (bits & 32) != 0;
            boolean spotifyHasLyrics = (bits & 64) != 0;
            boolean playback = Gates.playbackAllows(automobile, automobileAllowed, videoPlayer);
            boolean actual = Gates.show(Gates.layoutAllows(canvas, mixing), playback, enabled);
            // Preserve cp5 case 2 and jm7 case 1's player restrictions while replacing
            // only Spotify's lyrics-availability dependency with independent ivLyrics.
            boolean expected = !canvas && enabled && !mixing && (!automobile || automobileAllowed) && !videoPlayer;
            if (actual != expected) throw new AssertionError("Native restriction case " + bits);
            boolean nativeVisible = expected && spotifyHasLyrics;
            if (spotifyHasLyrics && actual != nativeVisible) throw new AssertionError("Native parity " + bits);
            assertions++;
        }
        if (!Gates.show(true, true, true) || Gates.show(true, true, false)) {
            throw new AssertionError("Changing the user's existing option must toggle inline visibility");
        }
        System.out.println("INLINE_GATES_PASSED cases=" + assertions);
    }
}
''')
subprocess.run([java_tool("javac"), "-d", str(work), str(test)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "InlineGateRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = "Production inline gates, synthetic native flags, no account/device access.\n"
report += f"SpotifyInlineLyrics.java SHA256 {hashlib.sha256(source.read_bytes()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
