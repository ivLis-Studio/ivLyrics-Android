import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("collector", Path(__file__).with_name("collect_release_apks.py"))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class ReleaseCollectionTest(unittest.TestCase):
    def test_legacy_updater_can_only_recognize_android_as_release(self):
        for tag in ("v1.3.6", "v1.3.6-release-candidate", "v1.3.6-RELEASE.1"):
            android, module = collector.asset_names(tag)
            self.assertIn("-release", android.lower())
            self.assertNotIn("-release", module.lower())
            self.assertTrue(module.startswith("ivLyrics-LSPatch-"))

    def test_reject_unsafe_tags_before_writing_output(self):
        for tag in ("../v1", "v1/../v2", "v1\nother", "v1$(pwd)", "v1.0-unsigned"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                collector.asset_names(tag)

    def test_module_only_build_cannot_create_partial_asset_set(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            destination = root / "release-apks"
            with self.assertRaisesRegex(RuntimeError, "Missing signed release APK"):
                collector.collect("v1.3.6", destination, root, "unused-aapt", "unused-apksigner")
            self.assertFalse(destination.exists())

    def test_different_signers_rejected_before_any_copy(self):
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / "release-apks"
            with patch.object(collector, "inspect_apk", side_effect=[{"signers": ["a"]}, {"signers": ["b"]}]):
                with self.assertRaisesRegex(RuntimeError, "stable release signing key"):
                    collector.collect("v1.3.6", destination, Path(temp), "aapt", "apksigner")
            self.assertFalse(destination.exists())

    def test_actual_apk_inspection_rejects_wrong_package_permissions_and_debuggable(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "app.apk"
            apk.write_bytes(b"fixture")
            signer = "Signer #1 certificate SHA-256 digest: aabb"
            for badging, permissions in (
                ("package: name='wrong.package' versionCode='1'", ""),
                ("package: name='expected.package' versionCode='1'\napplication-debuggable", ""),
                ("package: name='expected.package' versionCode='1'", "uses-permission: name='unexpected.permission'"),
            ):
                with patch.object(collector, "output", side_effect=[signer, badging, permissions]):
                    with self.assertRaises(RuntimeError):
                        collector.inspect_apk(apk, "expected.package", set(), "aapt", "apksigner")

    def test_two_verified_apks_are_both_collected_with_checksums(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for _, project, filename, _, _ in collector.PRODUCTS:
                path = root / project / "build/outputs/apk/release" / filename
                path.parent.mkdir(parents=True)
                path.write_bytes(project.encode())
            def inspected(path, package, permissions, *tools):
                return {"packageName": package, "versionName": "1", "versionCode": 1, "signers": ["aabb"]}
            with patch.object(collector, "inspect_apk", side_effect=inspected):
                reports = collector.collect("v1.3.6", root / "out", root, "aapt", "apksigner")
            self.assertEqual([row["product"] for row in reports], ["standalone", "spotify-module"])
            self.assertEqual(len(list((root / "out").glob("*.apk"))), 2)
            self.assertTrue(all(len(row["sha256"]) == 64 for row in reports))
            self.assertFalse(list((root / "out").glob("*version.json")))

    def test_old_and_new_apksigner_output_formats(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "app.apk"
            apk.write_bytes(b"fixture")
            for label in ("Signer #1", "V2 Signer:"):
                with patch.object(collector, "output", side_effect=[
                    label + " certificate SHA-256 digest: aabb",
                    "package: name='expected.package' versionCode='1' versionName='1.0'", ""
                ]):
                    info = collector.inspect_apk(apk, "expected.package", set(), "aapt", "apksigner")
                    self.assertEqual(info["signers"], ["aabb"])


if __name__ == "__main__":
    unittest.main()
