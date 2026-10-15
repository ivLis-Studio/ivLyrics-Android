"""The screenshot collector must preserve failed-test status and scoped output."""
import os
import fnmatch
import re
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("run_settings_ui_validation.sh")


class SettingsUiCollectionTests(unittest.TestCase):
    def collect(self, status):
        with tempfile.TemporaryDirectory(prefix="ivlyrics-ui-collector-") as temp:
            root = Path(temp)
            binary = root / "bin"
            binary.mkdir()
            source = root / "scoped-output"
            ready = root / "collected"
            environment = os.environ.copy()
            environment.update(PATH=str(binary) + os.pathsep + environment["PATH"],
                               IVLYRICS_TEST_SOURCE=str(source), IVLYRICS_TEST_READY=str(ready),
                               IVLYRICS_TEST_EXIT=str(status))
            scripts = {
                root / "gradlew": """import os, pathlib, time, sys, shutil
source = pathlib.Path(os.environ['IVLYRICS_TEST_SOURCE'])
for name, image in [('ui-regressions', 'native-window.png'), ('first-use-screenshots', 'first-use-window.png')]:
    directory = source / name
    directory.mkdir(parents=True)
    (directory / image).write_bytes(b'complete-native-png')
    (directory / 'incomplete.png.tmp').write_bytes(b'unfinished')
for _ in range(500):
    ready = pathlib.Path(os.environ['IVLYRICS_TEST_READY'])
    if (ready / 'native-window.png').exists() and (ready / 'first-use-window.png').exists():
        shutil.rmtree(source)  # Simulate UTP uninstalling the target app.
        sys.exit(int(os.environ['IVLYRICS_TEST_EXIT']))
    time.sleep(0.01)
sys.exit(99)
""",
                binary / "adb": """import os, pathlib, shutil, sys
source = pathlib.Path(os.environ['IVLYRICS_TEST_SOURCE'])
if sys.argv[1:3] == ['shell', 'ls']:
    source = pathlib.Path(os.environ['IVLYRICS_TEST_SOURCE']) / pathlib.PurePosixPath(sys.argv[3]).name
    if source.exists():
        print('\\n'.join(path.name for path in source.iterdir()))
    else: sys.exit(1)
elif sys.argv[1] == 'pull':
    remote = pathlib.PurePosixPath(sys.argv[2])
    path = source / remote.parent.name / remote.name
    if not path.exists(): sys.exit(1)
    shutil.copyfile(path, sys.argv[3])
    ready = pathlib.Path(os.environ['IVLYRICS_TEST_READY'])
    ready.mkdir(exist_ok=True)
    (ready / path.name).touch()
elif sys.argv[1:3] in (['shell', 'input'], ['shell', 'settings'], ['shell', 'wm']):
    pass  # Ephemeral-emulator wake/keyboard preflight.
else: sys.exit(2)
""",
                binary / "sleep": "import time\ntime.sleep(0.02)\n",
            }
            for path, body in scripts.items():
                path.write_text(f"#!{sys.executable}\n" + body)
                path.chmod(0o755)
            result = subprocess.run(["bash", str(SCRIPT)], cwd=root, env=environment,
                                    capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, status, result.stdout + result.stderr)
            output = root / "build/reports/settings-ui"
            self.assertEqual((output / "native-window.png").read_bytes(), b"complete-native-png")
            self.assertEqual((output / "first-use/first-use-window.png").read_bytes(), b"complete-native-png")
            self.assertFalse(source.exists(), "The capture must survive app-scoped data removal")
            self.assertEqual(sorted(str(path.relative_to(output)) for path in output.rglob("*") if path.is_file()),
                             ["first-use/first-use-window.png", "native-window.png"])

    def test_successful_instrumentation_capture_survives_uninstall(self):
        self.collect(0)

    def test_failed_instrumentation_stays_failed_after_capture(self):
        self.collect(7)

    def test_first_use_changes_trigger_native_workflow(self):
        root = SCRIPT.parents[2]
        workflow = (root / ".github/workflows/android-ui-validation.yml").read_text()
        changed = ["shared/src/main/java/kr/ivlis/ivlyricsandroid/FirstUseI18n.java",
                   "tests/run_first_use_regression.py",
                   "app/src/androidTest/java/kr/ivlis/ivlyricsandroid/FirstUseUiRegressionTest.java",
                   "app/src/debug/java/kr/ivlis/ivlyricsandroid/FirstUseHarnessActivity.java",
                   ".github/scripts/run_settings_ui_validation.sh"]
        for event in ("push", "pull_request"):
            remainder = workflow.split("  " + event + ":\n", 1)[1]
            section = re.split(r"\n(?=  [a-z_]+:|[a-z])", remainder, maxsplit=1)[0]
            patterns = re.findall(r"^      - '([^']+)'", section, re.M)
            for path in changed:
                self.assertTrue(any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns), (event, path))
        for name in ("android-ui-validation.yml", "android-ci.yml", "android-release.yml"):
            self.assertIn("python3 tests/run_first_use_regression.py", (root / ".github/workflows" / name).read_text())

    def test_native_suite_has_no_class_filter(self):
        command = next(line for line in SCRIPT.read_text().splitlines() if line.startswith("./gradlew "))
        self.assertIn(":app:connectedDebugAndroidTest", command)
        self.assertNotIn("InstrumentationRunnerArguments.class", command)


if __name__ == "__main__":
    unittest.main()
