#!/usr/bin/env python3
"""Run the production updater's asset selectors on local release-name fixtures.

JDK only: no Android build, dependency download, network, account, or device.
The hazard cases document why the release collector must require both APKs.
"""
from pathlib import Path
import hashlib
import subprocess

from regression_runtime import SHARED, REPORTS, java_tool


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


source = SHARED / "UpdateChecker.java"
text = source.read_text()
selectors = [declaration(text, signature) for signature in (
    "private Asset findBestApkAsset(List<Asset> assets)",
    "private Asset findVersionAsset(List<Asset> assets)",
    "private static final class Asset",
)]
work = REPORTS / "release-assets"
work.mkdir(parents=True, exist_ok=True)
test = work / "ReleaseAssetRegression.java"
test.write_text("import java.util.*;\npublic final class ReleaseAssetRegression {\n"
                + "\n".join(selectors) + r'''
    private int assertions;
    private static Asset asset(String name) { return new Asset(name, "fixture:" + name, 1L); }
    private void equal(String label, String expected, Asset actual) {
        assertions++;
        String name = actual == null ? null : actual.name;
        if (!Objects.equals(expected, name)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + name);
        }
    }
    private void apk(String label, String expected, String... names) {
        List<Asset> assets = new ArrayList<>();
        for (String name : names) assets.add(asset(name));
        equal(label, expected, findBestApkAsset(assets));
    }
    private void permutations(List<Asset> assets, int at, String android, String version) {
        if (at == assets.size()) {
            equal("Android release wins every asset order", android, findBestApkAsset(assets));
            equal("single Android version manifest", version, findVersionAsset(assets));
            return;
        }
        for (int index = at; index < assets.size(); index++) {
            Collections.swap(assets, at, index);
            permutations(assets, at + 1, android, version);
            Collections.swap(assets, at, index);
        }
    }
    private void run() {
        for (String tag : new String[]{"v1.3.5", "v1.3.6", "v12.10.30",
                "v1.3.6-rc.1", "v1.3.6-release-candidate"}) {
            String android = "ivLyrics-Android-" + tag + "-release.apk";
            String module = "ivLyrics-LSPatch-" + tag.replace('-', '_') + ".apk";
            String debug = "ivLyrics-Android-" + tag + "-debug.apk";
            String version = "ivLyrics-Android-" + tag + "-version.json";
            permutations(new ArrayList<>(Arrays.asList(asset(android), asset(module),
                    asset(version), asset("SHA256SUMS.txt"))), 0, android, version);
            apk("unsigned alternative cannot shadow signed Android", android,
                    module, "ivLyrics-Android-" + tag + "-release-unsigned.apk", android);
            apk(tag.contains("-release") ? "HAZARD: release in tag makes debug APK a release"
                    : "debug alternative cannot shadow signed Android",
                    tag.contains("-release") ? debug : android, debug, module, android);
            apk("signature sidecar is not an APK", android, module + ".asc", android);
            apk("case insensitive APK/release matching", android.toUpperCase(Locale.ROOT),
                    module, android.toUpperCase(Locale.ROOT));
            // These are existing legacy behaviors, not safe publication configurations.
            apk("HAZARD: module-only release is an updater fallback", module, version, module);
            apk("HAZARD: unsigned Android leaves module as fallback", module,
                    "ivLyrics-Android-" + tag + "-release-unsigned.apk", module, version);
            apk("HAZARD: missing signed Android selects debug or module",
                    tag.contains("-release") ? debug : module, debug, module);
            apk("HAZARD: '-release' anywhere in module name wins if first",
                    "ivLyrics-LSPatch-" + tag + "-release.apk",
                    "ivLyrics-LSPatch-" + tag + "-release.apk", android);
            apk("HAZARD: '-release' inside tag also matches",
                    "ivLyrics-LSPatch-" + tag + "-release-candidate.apk",
                    "ivLyrics-LSPatch-" + tag + "-release-candidate.apk", android);
        }
        apk("no APK assets", null, "version.json", "SHA256SUMS.txt", "bundle.apks");
        apk("all unsigned assets excluded", null, "app-release-unsigned.apk", "APP-UNSIGNED.APK");
        equal("plain version.json remains supported", "version.json",
                findVersionAsset(Arrays.asList(asset("version.json"))));
        System.out.println("PASS: " + assertions + " assertions against production selectors");
        System.out.println("Required release invariant: signed Android -release.apk and module APK both present;");
        System.out.println("module filename (including tag) must not contain '-release'.");
        System.out.println("Legacy hazards verified: missing/unsigned Android can select module; '-release' in a tag can make debug win.");
        System.out.println("version.json does not restrict APK selection, and multiple '-release' assets are order dependent.");
    }
    public static void main(String[] args) { new ReleaseAssetRegression().run(); }
}
''')
subprocess.run([java_tool("javac"), "-d", str(work), str(test)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "ReleaseAssetRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
report = "Production Java asset selectors; local filename fixtures only.\n"
report += f"UpdateChecker.java SHA256 {hashlib.sha256(source.read_bytes()).hexdigest()}\n"
report += f"findBestApkAsset SHA256 {hashlib.sha256(selectors[0].encode()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
