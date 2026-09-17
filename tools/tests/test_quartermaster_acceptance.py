"""Acceptance runner must never mutate its released-world source or overwrite a run."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
import subprocess
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import run_quartermaster_acceptance as acceptance

class AcceptanceRunnerTests(unittest.TestCase):
    def test_copies_source_world_and_keeps_embedded_dependency(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);base=root/'base';(base/'mods').mkdir(parents=True);(base/'libraries').mkdir()
            with zipfile.ZipFile(base/'mods/kotlin.jar','w') as z:z.writestr('placeholder','library')
            jar=root/'addon.jar';jar.write_bytes(b'addon');observer=root/'observer.jar';observer.write_bytes(b'observer')
            source=root/'source';(source/'world').mkdir(parents=True);(source/'world/level.dat').write_bytes(b'legacy-world');(source/'legacy-state.snbt').write_text('{}')
            def metadata(path):return [{'mods':{}},{'mods':{'kotlinforforge':'4.12.0'}}]
            with patch.object(acceptance,'BASE',base),patch.object(acceptance.qa,'jar_metadata',metadata):
                acceptance.prepare(root/'run',jar,observer,source)
                self.assertTrue((root/'run/mods/kotlin.jar').is_file())
                (root/'run/world/level.dat').write_bytes(b'changed-copy')
                self.assertEqual(b'legacy-world',(source/'world/level.dat').read_bytes())
                with self.assertRaises(FileExistsError):acceptance.prepare(root/'run',jar,observer,source)
            settings=(root/'run/server.properties').read_text()
            self.assertIn('server-ip=127.0.0.1',settings)
            generator=json.loads(next(s.split('=',1)[1] for s in settings.splitlines() if s.startswith('generator-settings=')))
            self.assertEqual([],generator['structure_overrides'])

    def test_timeout_terminates_only_its_process_and_records_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory);(out/'mods').mkdir()
            process=Mock(returncode=-15)
            process.wait.side_effect=[subprocess.TimeoutExpired('isolated-server',1),-15]
            process.poll.return_value=None
            with patch.object(acceptance.subprocess,'Popen',return_value=process):
                with self.assertRaises(RuntimeError):acceptance.run_server(out,'performance',timeout=1)
            process.terminate.assert_called_once_with();process.kill.assert_not_called()
            manifest=json.loads((out/'performance-manifest.json').read_text())
            self.assertFalse(manifest['passed']);self.assertTrue(manifest['timedOut'])
            self.assertEqual(-15,manifest['exitCode'])

if __name__=='__main__':unittest.main()
