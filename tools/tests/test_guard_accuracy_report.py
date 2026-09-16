import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('accuracy_report', Path(__file__).parents[1] / 'report_guard_accuracy.py')
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


def encounter(**changes):
    row = {'settings': {'targetKind': 'player', 'gun': 'ak47', 'distance': 16,
                        'strafe': False, 'blockAlly': False},
           'projectiles': 3, 'hitProjectiles': 2, 'hitEvents': 2, 'headshotEvents': 2,
           'killed': True, 'ticks': 12, 'firstFireTick': 8, 'allyDamageEventAmount': 0,
           'sampledFireDecisions': {}}
    row.update(changes)
    return row


class AccuracyReportTest(unittest.TestCase):
    def test_no_fire_is_not_reported_as_zero_accuracy_or_a_kill(self):
        row = encounter(projectiles=0, hitProjectiles=0, hitEvents=0,
                        headshotEvents=0, killed=False, firstFireTick=-1,
                        sampledFireDecisions={'UNSAFE_SHOT': 4})
        result = report.summarize([row])[0]
        self.assertIsNone(result['observedTargetHitFraction'])
        self.assertIsNone(result['medianKillTicksFromFirstShot'])
        self.assertEqual(result['timedOut'], 1)
        self.assertEqual(result['sampledFireDecisions'], {'UNSAFE_SHOT': 4})

    def test_unresolved_projectiles_and_censored_trials_remain_distinct(self):
        result = report.summarize([encounter(), encounter(killed=False, ticks=160)])[0]
        self.assertEqual(result['projectiles'], 6)
        self.assertEqual(result['withoutObservedTargetHit'], 2)
        self.assertEqual(result['kills'], 1)
        self.assertEqual(result['timedOut'], 1)
        self.assertEqual(result['medianKillTicksFromFirstShot'], 4)

    def test_rejects_impossible_event_counts(self):
        with self.assertRaises(ValueError):
            report.summarize([encounter(hitProjectiles=4)])
        with self.assertRaises(ValueError):
            report.summarize([encounter(headshotEvents=3)])


if __name__ == '__main__':
    unittest.main()
