"""Offline release metadata checks; APK bytes are synthetic hash fixtures."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import generate_release_metadata as release


REPOSITORY = Path(__file__).resolve().parents[2]


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.original_cwd = Path.cwd()
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.addCleanup(os.chdir, self.original_cwd)
        os.chdir(self.directory.name)
        for project, package_name, version_name, version_code in (
            ("app", "kr.ivlis.ivlyricsandroid", "1.3.6", 66),
            ("spotify-module", "dev.ivlyrics.spotify.module", "1.0.5", 6),
        ):
            Path(project).mkdir()
            Path(project, "build.gradle").write_text(
                "android { defaultConfig {\n"
                f'    applicationId "{package_name}"\n'
                f'    versionName "{version_name}"\n'
                f"    versionCode {version_code}\n"
                "} }\n",
                encoding="utf-8",
            )
        Path("release-apks").mkdir()
        self.payloads = {
            "ivLyrics-Android-v1.3.6-release.apk": b"standalone APK fixture",
            "ivLyrics-LSPatch-v1.3.6.apk": b"Spotify module APK fixture",
        }
        for name, payload in self.payloads.items():
            Path("release-apks", name).write_bytes(payload)
        Path(".github").mkdir()
        Path(".github/release-notes-template.md").write_text(
            (REPOSITORY / ".github/release-notes-template.md").read_text(encoding="utf-8"),
            encoding="utf-8",
        )

    def assets_by_product(self):
        return {asset["product"]: asset for asset in release.apk_assets("release-apks")}

    def test_products_keep_independent_package_and_version(self):
        assets = self.assets_by_product()
        self.assertEqual({"standalone", "spotify-module"}, set(assets))
        standalone, module = assets["standalone"], assets["spotify-module"]
        self.assertEqual("kr.ivlis.ivlyricsandroid", standalone["packageName"])
        self.assertEqual(("1.3.6", 66), (standalone["versionName"], standalone["versionCode"]))
        self.assertEqual("dev.ivlyrics.spotify.module", module["packageName"])
        self.assertEqual(("1.0.5", 6), (module["versionName"], module["versionCode"]))
        self.assertIn("LSPosed/LSPatch", module["label"])
        self.assertNotIn("-release", module["name"].lower())

    def test_existing_asset_fields_and_checksums_are_preserved(self):
        for asset in release.apk_assets("release-apks"):
            with self.subTest(asset=asset["name"]):
                payload = self.payloads[asset["name"]]
                self.assertEqual(str(Path("release-apks", asset["name"])), asset["path"])
                self.assertEqual(len(payload), asset["size"])
                self.assertEqual(hashlib.sha256(payload).hexdigest(), asset["sha256"])

    def test_default_gradle_version_remains_standalone(self):
        self.assertEqual({"versionName": "1.3.6", "versionCode": 66}, release.read_gradle_version())
        self.assertEqual({"versionName": "1.0.5", "versionCode": 6},
                         release.read_gradle_version("spotify-module/build.gradle"))

    def test_unknown_asset_does_not_inherit_either_product_identity(self):
        Path("release-apks/other.apk").write_bytes(b"other")
        asset = self.assets_by_product()["unknown"]
        self.assertEqual("", asset["packageName"])
        self.assertEqual("", asset["versionName"])
        self.assertIsNone(asset["versionCode"])

    def test_download_descriptions_distinguish_products_in_both_languages(self):
        assets = list(self.assets_by_product().values())
        for language, app_phrase, module_phrase in (
            ("ko", "독립 Android 앱", "Spotify용 LSPosed/LSPatch 모듈"),
            ("en", "Standalone Android app", "Spotify module for LSPosed/LSPatch"),
        ):
            with self.subTest(language=language):
                lines = release.asset_downloads(assets, language)
                app_line = next(line for line in lines if "ivLyrics-Android-" in line)
                module_line = next(line for line in lines if "ivLyrics-LSPatch-" in line)
                self.assertIn(app_phrase, app_line)
                self.assertIn(module_phrase, module_line)
                self.assertIn("v1.3.6 (66)", app_line)
                self.assertIn("v1.0.5 (6)", module_line)
                self.assertNotIn("v1.3.6", module_line.split(": ", 1)[1])

    def test_legacy_asset_records_still_render_and_keep_signature_description(self):
        records = [{"name": "ivLyrics-LSPatch-v1.3.6.apk"},
                   {"name": "ivLyrics-Android-v1.3.6-release-unsigned.apk"}]
        lines = release.asset_downloads(records, "en")
        self.assertIn("Spotify module for LSPosed/LSPatch", lines[0])
        self.assertIn("Unsigned release APK", lines[1])

    def test_main_writes_one_android_version_manifest_with_both_products(self):
        commits = [{"hash": "abc1234", "subject": "build: include Spotify module APK",
                    "body": "", "files": []}]
        with mock.patch.dict(os.environ, {"RELEASE_TAG": "v1.3.6"}, clear=True), \
                mock.patch.multiple(release,
                    resolve_commit=mock.Mock(return_value="a" * 40),
                    previous_tag=mock.Mock(return_value="v1.3.5"),
                    resolve_range_ref=mock.Mock(return_value="v1.3.6"),
                    git_diff_stat=mock.Mock(return_value=""),
                    release_commits=mock.Mock(return_value=commits),
                    run_git=mock.Mock(side_effect=AssertionError("unexpected git invocation"))), \
                mock.patch.object(release.urllib.request, "urlopen",
                                  side_effect=AssertionError("network forbidden")), \
                contextlib.redirect_stdout(io.StringIO()):
            release.main()

        manifests = list(Path("release-metadata").glob("*version.json"))
        self.assertEqual(["ivLyrics-Android-v1.3.6-version.json"], [path.name for path in manifests])
        metadata = json.loads(manifests[0].read_text(encoding="utf-8"))
        self.assertEqual(("1.3.6", 66), (metadata["versionName"], metadata["versionCode"]))
        self.assertEqual(set(self.payloads), {asset["name"] for asset in metadata["apks"]})
        self.assertEqual({"standalone", "spotify-module"}, {asset["product"] for asset in metadata["apks"]})
        notes = Path("release-metadata/release-notes.md").read_text(encoding="utf-8")
        self.assertIn("Spotify용 LSPosed/LSPatch 모듈", notes)
        self.assertIn("Spotify module for LSPosed/LSPatch", notes)
        for payload in self.payloads.values():
            self.assertIn(hashlib.sha256(payload).hexdigest(), notes)


if __name__ == "__main__":
    unittest.main()
