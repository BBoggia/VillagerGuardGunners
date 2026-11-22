import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run_guard_firing_qa as qa


class CompatibilityMetadataTest(unittest.TestCase):
    def test_reads_normal_and_loader_mods_without_changing_the_jars(self):
        for loader in (False, True):
            with self.subTest(loader=loader), tempfile.TemporaryDirectory() as directory:
                payload = io.BytesIO()
                with zipfile.ZipFile(payload, 'w') as inner:
                    inner.writestr('META-INF/mods.toml', '[[mods]]\nmodId="taczexpands"\nversion="1.1.4"\n')
                jar = Path(directory) / 'compat.jar'
                if loader:
                    with zipfile.ZipFile(jar, 'w') as outer:
                        outer.writestr('META-INF/mod.jar', payload.getvalue())
                else:
                    jar.write_bytes(payload.getvalue())
                before = jar.read_bytes()
                self.assertEqual({'taczexpands'}, qa.compatibility_mod_ids(jar))
                self.assertEqual(before, jar.read_bytes())

    def test_empty_loader_cannot_be_accepted_as_a_mod(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / 'empty.jar'
            with zipfile.ZipFile(jar, 'w'):
                pass
            self.assertEqual(set(), qa.compatibility_mod_ids(jar))
            payload = io.BytesIO()
            with zipfile.ZipFile(payload, 'w'):
                pass
            with zipfile.ZipFile(jar, 'w') as outer:
                outer.writestr('META-INF/mod.jar', payload.getvalue())
            self.assertEqual(set(), qa.compatibility_mod_ids(jar))


if __name__ == '__main__':
    unittest.main()
