#!/usr/bin/env python3
"""Summarize unfiltered natural village piece inventories from packaged-game QA."""
import argparse
from collections import Counter
import json
from pathlib import Path


def summarize(path):
    assertions = path.with_name('assertions.json')
    if not assertions.is_file() or not json.loads(assertions.read_text()).get('passed'):
        raise ValueError(f'{path}: run acceptance checks did not pass')
    result = json.loads(path.read_text())
    if result.get('status') != 'observed' or not result.get('production'):
        raise ValueError(f'{path}: not an observed packaged-game result')
    samples = result.get('regionalNaturalSamples', [])
    if not samples:
        raise ValueError(f'{path}: no natural village inventory')
    if len(samples) != 25 or any(s.get('regionalPieces') != 0 for s in samples):
        raise ValueError(f'{path}: expected a complete 25-village survey without target selection')
    positions = {(s['chunkX'], s['chunkZ']) for s in samples}
    if len(positions) != len(samples):
        raise ValueError(f'{path}: repeated village in inventory')
    # Older target-search records have only the requested layout count. They
    # cannot support a full frequency estimate and must not become zeroes.
    if any('buildings' not in s for s in samples):
        raise ValueError(f'{path}: missing complete building inventory')
    buildings = [b for s in samples for b in s['buildings']]
    optional = [b for b in buildings if not b['entry'].startswith('villagerguardgunners:')]
    count = len(samples)
    return dict(run=str(path.parent), seed=result['seed'], villages=count,
                villageChunks=sorted(map(list, positions)),
                selectedBuildings=len(buildings), optionalBuildings=len(optional),
                averageBuildings=round(len(buildings)/count, 3),
                averageOptionalBuildings=round(len(optional)/count, 3),
                maximumBuildings=max(len(s['buildings']) for s in samples),
                villagesWithoutBuildings=sum(not s['buildings'] for s in samples),
                byEntry=dict(sorted(Counter(b['entry'] for b in buildings).items())),
                byType=dict(sorted(Counter(b['type'] for b in buildings).items())),
                terrainSiteChecks=result.get('terrainSiteChecks', []))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('results', type=Path, nargs='+')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = {'scope': 'Selected natural village pieces; not a physical access, crowding, staffing or loot audit',
              'runs': [summarize(p) for p in args.results]}
    text = json.dumps(report, indent=2)+'\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text)
    else:
        print(text, end='')


if __name__ == '__main__':
    main()
