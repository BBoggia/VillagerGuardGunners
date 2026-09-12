import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('combat_validation', Path(__file__).parents[1] / 'validate_guard_intelligence.py')
validation = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validation)


class CombatEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        for scenario, contracts in validation.CONTRACTS.items():
            self.write(scenario, {
                'schema': 1, 'scenario': scenario, 'mode': 'baseline',
                'fixtureValid': True, 'fixtureFailure': None,
                'metrics': {}, 'samples': [{'tick': tick} for tick in range(1, 181)],
                'acceptance': {name: False for name in contracts},
            })

    def write(self, scenario, report):
        (self.directory / f'{scenario}.json').write_text(json.dumps(report))

    def change(self, **values):
        report = json.loads((self.directory / 'dry.json').read_text())
        report.update(values)
        self.write('dry', report)

    def test_valid_baseline_keeps_future_acceptance_failures_visible(self):
        result = validation.read_reports(self.directory, 'baseline')
        self.assertEqual(set(result), set(validation.CONTRACTS))
        self.assertFalse(any(result['dry']['acceptance'].values()))

    def test_acceptance_failure_requires_matching_behavior_assertions(self):
        observations = validation.read_reports(self.directory, 'baseline')
        log = 'failed! Planned combat behavior is not implemented:\n' * 4 + '4 required tests failed'
        validation.verify_acceptance_failure(log, 1, observations)
        for invalid in ('4 required tests failed', log.replace('4 required', '3 required'), ''):
            with self.subTest(log=invalid), self.assertRaises(ValueError):
                validation.verify_acceptance_failure(invalid, 1, observations)
        with self.assertRaises(ValueError):
            validation.verify_acceptance_failure(log, 0, observations)

    def test_missing_scenario_cannot_pass(self):
        (self.directory / 'wall.json').unlink()
        with self.assertRaises(ValueError):
            validation.read_reports(self.directory, 'baseline')

    def test_wrong_mode_cannot_reuse_a_baseline_as_acceptance(self):
        with self.assertRaises(ValueError):
            validation.read_reports(self.directory, 'acceptance')

    def test_invalid_fixture_cannot_pass_even_with_successful_gradle(self):
        self.change(fixtureValid=False, fixtureFailure='No native reload occurred')
        with self.assertRaises(ValueError):
            validation.read_reports(self.directory, 'baseline')

    def test_missing_future_contract_is_not_vacuous_success(self):
        self.change(acceptance={})
        with self.assertRaises(ValueError):
            validation.read_reports(self.directory, 'baseline')

    def test_duplicate_tick_cannot_replace_a_complete_observation(self):
        self.change(samples=[{'tick': 1}] * 180)
        with self.assertRaises(ValueError):
            validation.read_reports(self.directory, 'baseline')

    def test_truthy_strings_are_not_boolean_evidence(self):
        self.change(acceptance={name: 'false' for name in validation.CONTRACTS['dry']})
        with self.assertRaises(ValueError):
            validation.read_reports(self.directory, 'baseline')

    def test_identity_includes_docs_and_excludes_finder_and_python_cache(self):
        root = self.directory / 'repo'
        (root / 'docs').mkdir(parents=True)
        (root / 'src').mkdir()
        (root / 'tools/__pycache__').mkdir(parents=True)
        (root / 'docs/behavior.md').write_text('behavior')
        (root / 'src/.DS_Store').write_bytes(b'finder')
        (root / 'tools/__pycache__/test.pyc').write_bytes(b'cache')
        self.assertEqual(set(validation.source_identity(root)), {'docs/behavior.md'})


if __name__ == '__main__':
    unittest.main()
