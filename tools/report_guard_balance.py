#!/usr/bin/env python3
"""Summarize packaged combat observations; censored bullets are not asserted misses."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
from statistics import median


def percentile(values, fraction):
    if not values: return None
    ordered=sorted(values)
    return ordered[round((len(ordered)-1)*fraction)]


def ammo_used(row):
    # BalanceProbe starts each guard with a magazine plus a chambered round.
    settings=row['settings']
    initial=(18 if settings['gun'].endswith('glock_17') else 31)*settings['guards']
    return initial-row['ammoRemaining']


def acceptance_failures(rows):
    failures=[]
    no_fire={'owner','creative','spectator','ally-lane','ally-behind','near-wall'}
    for row in rows:
        settings=row['settings'];name=settings['id'];motion=settings['motion']
        if row['invalidAim'] or row['forbiddenShots'] or row['allyDamage']:
            failures.append(name+': safety/aim invariant')
        if ammo_used(row)!=row['rounds']:
            failures.append(name+': ammo consumption differs from emitted rounds')
        if motion in no_fire and row['projectiles']:
            failures.append(name+': protected firing')
        if motion not in no_fire and not row['projectiles']:
            failures.append(name+': expected firing was not exercised')
        if motion in ('creative-switch','spectator-switch') and row['rounds']!=1:
            failures.append(name+': burst must stop after the game-mode change')
    return failures


def event_failures(rows, events):
    failures=[];projectiles=defaultdict(list)
    for event in events:
        if event['event']=='projectile':projectiles[event['scenario']].append(event)
    for row in rows:
        s=row['settings'];name=s['id'];observed=projectiles[name]
        if len(observed)!=row['projectiles']:
            failures.append(name+': missing projectile evidence')
        for event in observed:
            error=s['error'];point=s['point']
            if s['target']!='player' and s['override']:
                error=0;point='EYE_LEVEL'
            if s['motion']=='live-config' and event['tick']<20:error=0
            if event['configuredError']!=error or event['point']!=point:
                failures.append(name+': resolved policy differs from requested settings')
                break
        if s['motion']=='live-config' and not (
                any(e['tick']<20 for e in observed) and any(e['tick']>=20 for e in observed)):
            failures.append(name+': live settings transition was not exercised')
        if s['motion'] in ('hold-fire','disable','target-switch') and not any(e['tick']>=60 for e in observed):
            failures.append(name+': firing did not resume after transition')
    return failures


def aggregate(rows):
    shots=sum(r['projectiles'] for r in rows)
    hits=sum(r['hitProjectiles'] for r in rows)
    kills=[r for r in rows if r['killed']]
    safety=defaultdict(int)
    for r in rows:
        for key,value in r['safety'].items():safety[key]+=value
    return {'trials':len(rows),'kills':len(kills),'timeouts':len(rows)-len(kills),
        'projectiles':shots,'rounds':sum(r['rounds'] for r in rows),'ammoUsed':sum(ammo_used(r) for r in rows),
        'hitProjectiles':hits,'withoutObservedTargetHit':shots-hits,
        'observedTargetHitFraction':hits/shots if shots else None,
        'headshotEvents':sum(r['headshots'] for r in rows),'damage':sum(r['damage'] for r in rows),
        'medianKillTicks':median(r['ticks'] for r in kills) if kills else None,
        'medianKillTicksAfterFirstShot':median(r['ticks']-r['firstShotTick'] for r in kills) if kills else None,
        'canceledRounds':sum(r['canceledRounds'] for r in rows),'safety':dict(safety),
        'invalidAim':sum(r['invalidAim'] for r in rows),'forbiddenShots':sum(r['forbiddenShots'] for r in rows),
        'allyDamage':sum(r['allyDamage'] for r in rows),
        'p95ObservedServerTickMillis':percentile([v for r in rows for v in r['tickMillis']],.95)}


def report(directory):
    directory=Path(directory)
    manifest=json.loads((directory/'manifest.json').read_text())
    rows=[json.loads(x) for x in (directory/'encounters.jsonl').read_text().splitlines()]
    if len(rows)!=manifest['expectedEncounters']:raise ValueError('Incomplete suite')
    groups=defaultdict(list)
    for row in rows:
        if not 0<=row['hitProjectiles']<=row['projectiles']:raise ValueError('Invalid projectile counts')
        s=row['settings']
        groups[(s['target'],s['gun'],s['motion'],s['guards'],s['distance'],s['error'],s['point'],s['attachment'])].append(row)
    result={'runtimePassed':manifest.get('passed',False),'mode':manifest['mode'],'suite':manifest['suite'],
            'overall':aggregate(rows),'groups':[]}
    for key,values in groups.items():
        result['groups'].append(dict(zip(['target','gun','motion','guards','distance','error','point','attachment'],key))|aggregate(values))
    events=[json.loads(x) for x in (directory/'events.jsonl').read_text().splitlines()]
    result['acceptanceFailures']=acceptance_failures(rows)+event_failures(rows,events)
    result['limitations']=[
        'Ordinary combat AI, scripted target paths, no armor, fixed equipment, Normal difficulty.',
        'A real connected client is used; scripted teleports do not measure human movement or network latency.',
        'Shots without an observed target hit include bullets still in flight at encounter cleanup.',
        'Kill-time medians include only kills; timeouts are reported separately.',
        'Three seeds per core/group condition are exploratory evidence, not a precise population estimate.',
        'Server tick observations include the test observer and local client workload; this is not a load benchmark.',
        'Live-config and command transition cases include an initial invulnerable target period and are excluded from accuracy comparisons.'
    ]
    (directory/'summary.json').write_text(json.dumps(result,indent=2)+'\n')
    return result


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('directory',type=Path);a=p.parse_args()
    result=report(a.directory)
    print(json.dumps(result,indent=2))
    return 0 if result['runtimePassed'] and not result['acceptanceFailures'] else 1


if __name__=='__main__':raise SystemExit(main())
