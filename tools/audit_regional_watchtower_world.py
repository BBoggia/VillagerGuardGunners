#!/usr/bin/env python3
"""Check saved natural Watchtower approaches and occupied cells for water or lava."""
import argparse
import json
import math
import re
from pathlib import Path
from audit_regional_barracks_world import World, rotate, saved_world


def audit(run, manifest):
    result = json.loads((run / 'result.json').read_text())
    world = World(saved_world(run))
    rows = {r['entry']: r for r in json.loads(manifest.read_text())}
    report = []
    for check in result.get('regionalWatchtowerNaturalChecks', []):
        row = rows[check['entry']]
        local, connector = row['connector'], check['connector']

        def transform(pos):
            dx, dz = rotate(pos[0]-local[0], pos[2]-local[2], check['rotation'])
            return connector[0]+dx, connector[1]+pos[1]-local[1], connector[2]+dz

        dx, dz = rotate(0, -1, check['rotation'])
        approach = check.get('road', [connector[0]+dx, 0, connector[2]+dz])
        road = world.block(approach[0], math.floor(check['roadStandingY']-1e-6), approach[2])
        expected = re.search(r'Block\{([^}]+)\}', check['roadState']).group(1)
        if road['Name'] != expected:
            raise ValueError(f'Saved road differs from native observation: {road["Name"]} != {expected}')
        # Inspect body space above the raised floor; water below a dry boardwalk
        # or inside its supporting top trapdoor is not a flooded standing cell.
        cells = {transform(p) for p in row['standing'] + row['targets'] + [row['service'], row['work']]}
        wet = []
        for x, y, z in sorted(cells):
            for dy in (0, 1):
                pos = (x, y+dy, z)
                state = world.block(*pos)
                waterlogged = state.get('Properties', {}).get('waterlogged', (8, 'false'))[1] == 'true'
                if state['Name'] in ('minecraft:water', 'minecraft:lava') or waterlogged:
                    wet.append({'position': pos, 'state': state['Name']})
        report.append({'entry': check['entry'], 'connector': connector,
                       'checkedStandingCells': len(cells), 'wetStandingCells': wet})
    if not report:
        raise ValueError('No naturally generated regional Watchtowers were observed')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--manifest', type=Path, default=Path('build/generated/regional-watchtower-integration/manifest.json'))
    args = parser.parse_args()
    report = audit(args.run, args.manifest)
    print(json.dumps(report, indent=2))
    raise SystemExit(1 if any(row['wetStandingCells'] for row in report) else 0)
