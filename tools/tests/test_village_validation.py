import importlib.util
import json
from pathlib import Path
import tempfile
import subprocess
import sys
import unittest

spec = importlib.util.spec_from_file_location('validation', Path(__file__).parents[1] / 'validate_village_expansion.py')
validation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validation)


class EvidenceParsingTest(unittest.TestCase):
    def test_stage_preserves_output_and_nonzero_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with (root / 'stage.log').open('w') as stream:
                code = validation.execute_stage(
                    [sys.executable, '-c', 'print("retained"); raise SystemExit(7)'], root, stream)
            self.assertEqual(7, code)
            self.assertIn('retained', (root / 'stage.log').read_text())

    def test_stage_timeout_terminates_its_process(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with (root / 'stage.log').open('w') as stream:
                with self.assertRaises(subprocess.TimeoutExpired):
                    validation.execute_stage([sys.executable, '-c', 'import time; time.sleep(60)'],
                                             root, stream, timeout=0.2)

    def test_partial_performance_evidence_is_retained(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / 'build/reports/example-performance'
            reports.mkdir(parents=True)
            (reports / 'partial.jfr').write_bytes(b'partial recording')
            run = root / 'retained'
            validation.retain_performance_reports(root, run)
            (reports / 'partial.jfr').write_bytes(b'next run')
            self.assertEqual(b'partial recording', (run / 'example-performance/partial.jfr').read_bytes())

    def test_timeout_reaps_detached_gradle_style_child_without_stopping_unrelated_process(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = ('import subprocess, sys, time\n'
                      'child = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"], start_new_session=True)\n'
                      'print(child.pid, flush=True)\n'
                      'time.sleep(60)\n')
            unrelated = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'],
                                         start_new_session=True)
            try:
                with (root / 'stage.log').open('w') as stream:
                    with self.assertRaises(subprocess.TimeoutExpired):
                        validation.execute_stage([sys.executable, '-c', script], root, stream, timeout=1)
                child = int((root / 'stage.log').read_text().splitlines()[0])
                self.assertIsNone(unrelated.poll())
                # A terminated orphan can briefly remain a zombie until init reaps it.
                state = subprocess.run(['ps', '-o', 'stat=', '-p', str(child)],
                                       text=True, capture_output=True, check=False).stdout.strip()
                self.assertTrue(not state or state.startswith('Z'), f'Child survived timeout: {state}')
            finally:
                unrelated.terminate()
                unrelated.wait(timeout=5)

    def test_requires_one_positive_native_completion(self):
        self.assertEqual(248, validation.game_test_count('All 248 required tests passed :)'))
        for log in ('BUILD SUCCESSFUL', 'All 0 required tests passed',
                    'All 2 required tests passed\nAll 2 required tests passed',
                    'All 2 required tests passed\n1 required tests failed',
                    'All 2 required tests passed\nBUILD FAILED'):
            with self.subTest(log=log), self.assertRaises(ValueError):
                validation.game_test_count(log)

    def test_jvm_evidence_rejects_empty_failed_and_skipped_runs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                validation.junit_count(root)
            file = root / 'TEST-example.xml'
            for attributes in ('tests="0"', 'tests="2" failures="1"',
                               'tests="2" errors="1"', 'tests="2" skipped="1"'):
                file.write_text(f'<testsuite {attributes}/>')
                with self.subTest(attributes=attributes), self.assertRaises(ValueError):
                    validation.junit_count(root)
            file.write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
            self.assertEqual(2, validation.junit_count(root))

    def test_source_identity_detects_additions_deletions_and_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'src').mkdir()
            file = root / 'src/test.java'
            file.write_text('before')
            before = validation.source_identity(root)
            file.write_text('after')
            self.assertNotEqual(before, validation.source_identity(root))
            file.unlink()
            self.assertNotEqual(before, validation.source_identity(root))
            file.write_text('before')
            (root / 'src/new.java').write_text('new')
            self.assertNotEqual(before, validation.source_identity(root))

    def test_manifest_rejects_stale_artifact_source_and_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'src').mkdir()
            source = root / 'src/example.java'
            source.write_text('validated source')
            artifact = root / 'candidate.jar'
            artifact.write_bytes(b'candidate')
            evidence = root / 'evidence'
            evidence.mkdir()
            (evidence / 'junit').mkdir()
            (evidence / 'junit/TEST-example.xml').write_text('<testsuite tests="2"/>')
            stages = []
            for name, _ in validation.STAGES:
                log = 'BUILD SUCCESSFUL\n'
                stage = {'name': name, 'exit_code': 0}
                if name != 'evidence-gates':
                    log += 'All 2 required tests passed :)\n'
                    stage['tests'] = 2
                (evidence / (name + '.log')).write_text(log)
                stages.append(stage)
            data = {'schema': 1, 'automated_status': 'passed', 'release_ready': False,
                    'remaining_release_checks': validation.MANUAL,
                    'sources': validation.source_identity(root), 'jvm_tests': 2,
                    'artifact': {'path': 'candidate.jar', 'sha256': validation.digest(artifact)},
                    'stages': stages,
                    'evidence': {str(p.relative_to(evidence)): validation.digest(p)
                                 for p in evidence.rglob('*') if p.is_file()}}
            manifest = evidence / 'manifest.json'
            manifest.write_text(json.dumps(data))
            self.assertEqual(2, validation.verify(root, manifest)['jvm_tests'])
            for path in (source, artifact, evidence / 'correctness.log', evidence / 'junit/TEST-example.xml'):
                original = path.read_bytes()
                path.write_bytes(original + b'changed')
                with self.subTest(path=path), self.assertRaises(ValueError):
                    validation.verify(root, manifest)
                path.write_bytes(original)
            data['release_ready'] = True
            manifest.write_text(json.dumps(data))
            with self.assertRaises(ValueError):
                validation.verify(root, manifest)


if __name__ == '__main__':
    unittest.main()
