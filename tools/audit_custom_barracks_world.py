#!/usr/bin/env python3
"""Audit saved CTOV Barracks floors and natural entrance approaches."""
import argparse
import gzip
import io
import json
import math
import re
from pathlib import Path

from audit_regional_barracks_world import World, rotate, saved_world
from create_flat_sandbox import read_payload


def audit(run, manifest):
    result = json.loads((run / 'result.json').read_text())
    save = saved_world(run)
    world = World(save)
    rows = {r['entry']: r for r in json.loads(manifest.read_text())}
    report = []
    for check in result.get('customBarracksNaturalChecks', []):
        row = rows[check['entry']]
        namespace, path = row['template'].split(':', 1)
        # Inspect the exact template installed in this saved world.
        files = list((save / 'datapacks').glob(f'*/data/{namespace}/structures/{path}.nbt'))
        if len(files) != 1:
            raise ValueError(f'Expected one installed template for {row["entry"]}')
        stream = io.BytesIO(gzip.decompress(files[0].read_bytes()))
        if stream.read(3) != b'\x0a\x00\x00': raise ValueError('Invalid template root')
        tag = read_payload(stream, 10)
        palette = tag['palette'][1][1]
        names = {tuple(b['pos'][1][1]): palette[b['state'][1]]['Name'][1] for b in tag['blocks'][1][1]}
        floor = row['floorY']
        # Every authored plank-floor position with clear body space, including
        # carpeted rooms; furniture and structural walls are excluded.
        cells = {pos for pos, name in names.items() if pos[1] == floor
                 and (name in ('minecraft:air', 'minecraft:structure_block') or name.endswith('_carpet'))
                 and names.get((pos[0], floor+1, pos[2])) == 'minecraft:air'
                 and names.get((pos[0], floor-1, pos[2]), '').endswith('_planks')}
        cells.update(map(tuple, row['guards'] + row['targets'] + [row['sentry']]))
        local, connector = row['connector'], check['connector']

        def transform(pos):
            dx, dz = rotate(pos[0]-local[0], pos[2]-local[2], check['rotation'])
            return connector[0]+dx, connector[1]+pos[1]-local[1], connector[2]+dz

        approach = check['road']
        road = world.block(approach[0], math.floor(check['roadStandingY']-1e-6), approach[2])
        expected = re.search(r'Block\{([^}]+)\}', check['roadState']).group(1)
        if road['Name'] != expected:
            raise ValueError(f'Saved road differs from native observation: {road["Name"]} != {expected}')
        positions = {transform(p) for p in cells} | {tuple(approach)}
        wet = []
        for x, y, z in sorted(positions):
            for dy in (0, 1):
                state = world.block(x, y+dy, z)
                if state['Name'] in ('minecraft:water', 'minecraft:lava') or state.get('Properties', {}).get('waterlogged', (8, 'false'))[1] == 'true':
                    wet.append({'position': [x, y+dy, z], 'state': state['Name']})
        report.append({'entry': check['entry'], 'connector': connector,
                       'checkedStandingCells': len(positions), 'wetStandingCells': wet})
    if not report: raise ValueError('No naturally generated CTOV Barracks were observed')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--manifest', type=Path, default=Path('build/generated/custom-barracks-integration/manifest.json'))
    args = parser.parse_args()
    report = audit(args.run, args.manifest)
    print(json.dumps(report, indent=2))
    raise SystemExit(1 if any(row['wetStandingCells'] for row in report) else 0)
