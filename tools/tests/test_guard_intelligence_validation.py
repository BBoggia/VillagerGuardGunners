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


class VillageEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        for scenario in validation.VILLAGE_SCENARIOS:
            report = {
                'schema': 1, 'scenario': scenario, 'mode': 'baseline',
                'fixtureValid': True, 'fixtureFailure': None, 'metrics': {},
                'settings': {'automaticCoverage': scenario != 'village_default',
                             'homesConnected': scenario != 'village_divided',
                             'squadMembers': 2, 'initialAffiliatedGuards': 5,
                             'villagers': 36 if scenario == 'village_crowd' else 4},
                'samples': [{'tick': tick, 'guards': {
                    name: {'squadMember': name.startswith('squad_'),
                           'affiliated': name != 'newcomer', 'lease': None}
                    for name in validation.VILLAGE_GUARDS}} for tick in range(1, 601)],
            }
            (self.directory / f'{scenario}.json').write_text(json.dumps(report))

    def mutate(self, update, scenario='village_default'):
        path = self.directory / f'{scenario}.json'
        report = json.loads(path.read_text())
        update(report)
        path.write_text(json.dumps(report))

    def read(self):
        return validation.read_reports(self.directory, 'baseline', 'village')

    def test_observations_do_not_claim_future_acceptance(self):
        self.assertEqual(set(self.read()), validation.VILLAGE_SCENARIOS)
        self.assertNotIn('acceptance', self.read()['village_default'])

    def test_village_baseline_cannot_be_promoted_to_acceptance(self):
        with self.assertRaises(ValueError):
            validation.read_reports(self.directory, 'acceptance', 'village')

    def test_configuration_must_match_the_scenario(self):
        self.mutate(lambda report: report['settings'].update(automaticCoverage=True))
        with self.assertRaises(ValueError): self.read()

    def test_excluded_participants_cannot_receive_coverage(self):
        self.mutate(lambda report: report['samples'][20]['guards']['resident_west'].update(lease={'destination': 'unexpected'}))
        with self.assertRaises(ValueError): self.read()

    def test_membership_must_survive_the_observation_window(self):
        self.mutate(lambda report: report['samples'][400]['guards']['squad_a'].update(squadMember=False))
        with self.assertRaises(ValueError): self.read()

    def test_newcomer_can_join_through_normal_discovery_before_receiving_coverage(self):
        def joined(report):
            for row in report['samples'][300:]:
                row['guards']['newcomer'].update(affiliated=True, lease={'destination': 'home'})
        self.mutate(joined, 'village_coverage')
        self.assertIn('village_coverage', self.read())

    def test_newcomer_cannot_receive_coverage_before_affiliation(self):
        self.mutate(lambda report: report['samples'][30]['guards']['newcomer'].update(lease={'destination': 'home'}),
                    'village_coverage')
        with self.assertRaises(ValueError): self.read()

    def test_missing_participants_or_truncated_windows_cannot_pass(self):
        self.mutate(lambda report: report['samples'][30]['guards'].pop('hold_position'))
        with self.assertRaises(ValueError): self.read()
        self.mutate(lambda report: report['samples'].pop())
        with self.assertRaises(ValueError): self.read()


if __name__ == '__main__':
    unittest.main()
