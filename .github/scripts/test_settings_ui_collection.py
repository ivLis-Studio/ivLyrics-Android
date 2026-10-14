"""The screenshot collector must preserve failed-test status and scoped output."""
import os
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
                root / "gradlew": """import os, pathlib, time, sys
source = pathlib.Path(os.environ['IVLYRICS_TEST_SOURCE'])
source.mkdir()
(source / 'native-window.png').write_bytes(b'complete-native-png')
(source / 'incomplete.png.tmp').write_bytes(b'unfinished')
for _ in range(500):
    if pathlib.Path(os.environ['IVLYRICS_TEST_READY']).exists():
        for path in source.iterdir(): path.unlink()
        source.rmdir()  # Simulate UTP uninstalling the target app.
        sys.exit(int(os.environ['IVLYRICS_TEST_EXIT']))
    time.sleep(0.01)
sys.exit(99)
""",
                binary / "adb": """import os, pathlib, shutil, sys
source = pathlib.Path(os.environ['IVLYRICS_TEST_SOURCE'])
if sys.argv[1:3] == ['shell', 'ls']:
    if source.exists():
        print('\\n'.join(path.name for path in source.iterdir()))
    else: sys.exit(1)
elif sys.argv[1] == 'pull':
    path = source / pathlib.PurePosixPath(sys.argv[2]).name
    if not path.exists(): sys.exit(1)
    shutil.copyfile(path, sys.argv[3])
    pathlib.Path(os.environ['IVLYRICS_TEST_READY']).touch()
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
            self.assertFalse(source.exists(), "The capture must survive app-scoped data removal")
            self.assertEqual([path.name for path in output.iterdir()], ["native-window.png"])

    def test_successful_instrumentation_capture_survives_uninstall(self):
        self.collect(0)

    def test_failed_instrumentation_stays_failed_after_capture(self):
        self.collect(7)


if __name__ == "__main__":
    unittest.main()
