import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from report_village_compatibility import report
from run_village_compatibility import json_hash, sha, record_preflight, record_attempt


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.identity = {'case': {'name': 'sample', 'expectations': []},
                         'jars': {'providers': {'villagerguardgunners': '/candidate.jar'},
                                  'files': [{'source': '/candidate.jar', 'sha256': 'candidate'}]}}
        self.digest = json_hash(self.identity)
        record_preflight(self.root, [self.identity['case']], {'sample': [self.identity, self.digest]})

    def write(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value))

    def run_evidence(self, suffix='001', status='passed'):
        directory = self.root / 'runs' / ('sample-20260101T000000.000' + suffix + 'Z')
        record_attempt(self.root, 'sample', directory.name)
        self.write(directory / 'result.json', {'status': 'observed', 'production': True,
                                               'releaseCodeSourcesVerified': True})
        self.write(directory / 'world-evidence.json', {})
        self.write(directory / 'config-evidence.json', {})
        self.write(directory / 'manifest.json', {'status': status, 'identity': self.identity,
                   'identityHash': self.digest, 'exitCode': 0,
                   'evidence': {name: sha(directory / name) for name in
                                ['result.json', 'world-evidence.json', 'config-evidence.json']}})
        return directory

    def test_verified_candidate_passes(self):
        self.run_evidence()
        self.assertTrue(report(self.root, 'candidate')['allConfiguredCasesPassed'])

    def test_missing_case_and_wrong_candidate_never_pass(self):
        self.assertEqual(report(self.root, 'candidate')['cases'][0]['status'], 'missing')
        self.run_evidence()
        self.assertEqual(report(self.root, 'other')['cases'][0]['status'], 'candidate_mismatch')

    def test_changed_evidence_is_rejected(self):
        directory = self.run_evidence()
        self.write(directory / 'result.json', {'status': 'observed'})
        self.assertEqual(report(self.root, 'candidate')['cases'][0]['status'], 'changed_evidence')

    def test_newer_failure_is_not_hidden_by_old_pass(self):
        self.run_evidence()
        self.run_evidence('002', 'failed')
        self.assertEqual(report(self.root, 'candidate')['cases'][0]['status'], 'failed')

    def test_rechecks_assertions_instead_of_trusting_pass_label(self):
        directory = self.run_evidence()
        self.write(directory / 'result.json', {'status': 'observed', 'production': False,
                                               'releaseCodeSourcesVerified': True})
        manifest = json.loads((directory / 'manifest.json').read_text())
        manifest['evidence']['result.json'] = sha(directory / 'result.json')
        self.write(directory / 'manifest.json', manifest)
        self.assertEqual(report(self.root, 'candidate')['cases'][0]['status'], 'failed')

    def test_corrupt_or_missing_latest_manifest_never_falls_back(self):
        self.run_evidence()
        latest = self.run_evidence('002', 'failed')
        (latest / 'manifest.json').write_text('{')
        self.assertFalse(report(self.root, 'candidate')['allConfiguredCasesPassed'])
        (latest / 'manifest.json').unlink()
        self.assertFalse(report(self.root, 'candidate')['allConfiguredCasesPassed'])

    def test_deleted_latest_attempt_never_falls_back(self):
        import shutil
        self.run_evidence()
        latest = self.run_evidence('002', 'failed')
        shutil.rmtree(latest)
        self.assertFalse(report(self.root, 'candidate')['allConfiguredCasesPassed'])

    def test_filtered_preflight_retains_unrun_case(self):
        import copy
        other = copy.deepcopy(self.identity); other['case']['name'] = 'unrun'
        (self.root / 'matrix.json').unlink()
        cases = [self.identity['case'], other['case']]
        record_preflight(self.root, cases, {'sample': [self.identity, self.digest]})
        self.run_evidence()
        record_preflight(self.root, cases, {'sample': [self.identity, self.digest]})
        result = report(self.root, 'candidate')
        self.assertFalse(result['allConfiguredCasesPassed'])
        self.assertEqual(2, len(result['cases']))
        with self.assertRaises(ValueError):
            record_preflight(self.root, [self.identity['case']], {'sample': [self.identity, self.digest]})

    def test_missing_evidence_manifest_is_rejected(self):
        directory = self.run_evidence()
        manifest = json.loads((directory / 'manifest.json').read_text())
        manifest['evidence'] = {}
        self.write(directory / 'manifest.json', manifest)
        self.assertEqual(report(self.root, 'candidate')['cases'][0]['status'], 'unverified_evidence')


if __name__ == '__main__':
    unittest.main()
