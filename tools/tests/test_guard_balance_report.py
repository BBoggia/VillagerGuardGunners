import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('guard_balance_report',Path(__file__).resolve().parents[1]/'report_guard_balance.py')
report=importlib.util.module_from_spec(spec);spec.loader.exec_module(report)


def encounter(**changes):
    row={'projectiles':10,'hitProjectiles':2,'killed':True,'ticks':40,'firstShotTick':10,
         'headshots':1,'damage':24,'canceledRounds':3,'safety':{'CLEAR':10,'PROTECTED_ENTITY_IN_PATH':3},
         'invalidAim':0,'forbiddenShots':0,'allyDamage':0,'tickMillis':[1,2,3,4],
         'settings':{'id':'test','motion':'stationary','gun':'tacz:ak47','guards':1},
         'rounds':10,'ammoRemaining':21}
    return row|changes


class GuardBalanceReportTest(unittest.TestCase):
    def test_refused_rounds_are_not_misses_or_denominator_shots(self):
        r=report.aggregate([encounter()])
        self.assertEqual(.2,r['observedTargetHitFraction'])
        self.assertEqual(3,r['canceledRounds'])
        self.assertEqual(8,r['withoutObservedTargetHit'])

    def test_no_shots_has_no_hit_fraction(self):
        r=report.aggregate([encounter(projectiles=0,hitProjectiles=0,killed=False,firstShotTick=-1)])
        self.assertIsNone(r['observedTargetHitFraction'])
        self.assertIsNone(r['medianKillTicks'])
        self.assertEqual(1,r['timeouts'])

    def test_timeouts_are_retained_without_becoming_successful_kill_times(self):
        r=report.aggregate([encounter(),encounter(killed=False,ticks=160)])
        self.assertEqual((1,1),(r['kills'],r['timeouts']))
        self.assertEqual(40,r['medianKillTicks'])
        self.assertEqual(30,r['medianKillTicksAfterFirstShot'])

    def test_invariant_failures_and_damage_are_not_lost_when_aggregating(self):
        r=report.aggregate([encounter(allyDamage=2,invalidAim=1),encounter(forbiddenShots=3)])
        self.assertEqual((2,1,3),(r['allyDamage'],r['invalidAim'],r['forbiddenShots']))
        self.assertEqual(6,r['safety']['PROTECTED_ENTITY_IN_PATH'])

    def test_no_fire_is_a_failure_when_firing_was_required(self):
        row=encounter(projectiles=0,hitProjectiles=0,rounds=0,ammoRemaining=31)
        self.assertIn('test: expected firing was not exercised',report.acceptance_failures([row]))
        row['settings']['motion']='owner'
        self.assertEqual([],report.acceptance_failures([row]))

    def test_pellets_do_not_count_as_separate_rounds_or_ammo(self):
        row=encounter(projectiles=20,rounds=2,ammoRemaining=29)
        self.assertEqual([],report.acceptance_failures([row]))
        self.assertEqual(2,report.aggregate([row])['ammoUsed'])
        row['ammoRemaining']=28
        self.assertTrue(report.acceptance_failures([row]))

    def test_game_mode_switch_must_exercise_then_stop_the_burst(self):
        row=encounter(projectiles=1,rounds=1,ammoRemaining=30)
        row['settings']['motion']='creative-switch'
        self.assertEqual([],report.acceptance_failures([row]))
        row.update(projectiles=2,rounds=2,ammoRemaining=29)
        self.assertIn('test: burst must stop after the game-mode change',report.acceptance_failures([row]))

    def test_player_override_must_not_change_resolved_mob_policy(self):
        row=encounter(projectiles=1)
        row['settings'].update(target='husk',override=True,error=3,point='CENTER_MASS')
        event={'event':'projectile','scenario':'test','tick':8,'configuredError':0,'point':'EYE_LEVEL'}
        self.assertEqual([],report.event_failures([row],[event]))
        event['configuredError']=3
        self.assertTrue(report.event_failures([row],[event]))

    def test_transition_needs_firing_evidence_on_both_sides(self):
        row=encounter(projectiles=1)
        row['settings'].update(motion='live-config',target='player',override=True,error=3,point='CENTER_MASS')
        event={'event':'projectile','scenario':'test','tick':8,'configuredError':0,'point':'CENTER_MASS'}
        self.assertIn('test: live settings transition was not exercised',report.event_failures([row],[event]))


if __name__=='__main__':unittest.main()
