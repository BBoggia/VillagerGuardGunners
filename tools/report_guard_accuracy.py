#!/usr/bin/env python3
"""Summarize controlled encounters without calling unobserved bullets misses."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
from statistics import median


def summarize(rows):
    groups = defaultdict(list)
    for row in rows:
        s = row['settings']
        if not 0 <= row['hitProjectiles'] <= row['projectiles']:
            raise ValueError('Invalid unique projectile hit count')
        if not 0 <= row['headshotEvents'] <= row['hitEvents']:
            raise ValueError('Invalid headshot count')
        groups[(s['targetKind'], s['gun'], s['distance'], s['strafe'], s['blockAlly'])].append(row)
    result = []
    for (target, gun, distance, strafe, blocked), trials in groups.items():
        shots = sum(r['projectiles'] for r in trials)
        hits = sum(r['hitProjectiles'] for r in trials)
        hit_events = sum(r['hitEvents'] for r in trials)
        headshots = sum(r['headshotEvents'] for r in trials)
        kills = [r for r in trials if r['killed']]
        decisions = defaultdict(int)
        for row in trials:
            for key, count in row.get('sampledFireDecisions', {}).items():
                decisions[key] += count
        result.append({'target': target, 'gun': gun, 'distance': distance,
                       'scriptedStrafing': strafe, 'allyBlocksLane': blocked,
                       'trials': len(trials), 'kills': len(kills),
                       'timedOut': len(trials) - len(kills), 'projectiles': shots,
                       'hitProjectiles': hits, 'withoutObservedTargetHit': shots - hits,
                       'observedTargetHitFraction': hits / shots if shots else None,
                       'headshotEvents': headshots, 'hitEvents': hit_events,
                       'headshotFractionOfHitEvents': headshots / hit_events if hit_events else None,
                       'medianKillTicksFromEncounterStart': median(r['ticks'] for r in kills) if kills else None,
                       'medianKillTicksFromFirstShot': median(r['ticks'] - r['firstFireTick'] for r in kills) if kills else None,
                       'allyDamageEventAmount': sum(r['allyDamageEventAmount'] for r in trials),
                       'sampledFireDecisions': dict(decisions)})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    manifest = json.loads((args.directory / 'manifest.json').read_text())
    rows = [json.loads(line) for line in (args.directory / 'encounters.jsonl').read_text().splitlines()]
    report = {'runtimePassed': manifest.get('passed', False), 'encounters': len(rows),
              'groups': summarize(rows),
              'limitations': [
                  'Controlled fixed-range fights with no armor/effects; mobs have AI disabled.',
                  'Player movement is positioned by the fixture; this is not human-input or network-latency coverage.',
                  'Fights stop at death or timeout. Bullets without an observed hit may still be in flight at cleanup.',
                  'Headshots count uncanceled TaCZ Pre events; damage is recorded separately using LivingDamageEvent.',
                  'Damage event totals can include overkill and are not identical to health removed.',
                  'Game ticks and wall time are recorded separately. Kill ticks include acquisition/draw time.',
                  'Three short trials per condition are baseline observations, not a reliable population hit-rate estimate.'
              ]}
    (args.directory / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
