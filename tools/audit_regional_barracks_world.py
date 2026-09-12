#!/usr/bin/env python3
"""Read saved natural-test chunks and report water at Barracks standing cells."""
import argparse
import gzip
import io
import json
import math
import re
import struct
import zlib
from pathlib import Path
from create_flat_sandbox import read_payload


def compound(raw):
    stream = io.BytesIO(raw)
    if stream.read(1) != b'\x0a':
        raise ValueError('Expected root compound')
    stream.read(struct.unpack('>H', stream.read(2))[0])
    return read_payload(stream, 10)


def saved_world(run):
    candidates = list((run / 'client/saves').glob('*/level.dat'))
    dedicated = run / 'server/world/level.dat'
    if dedicated.is_file():
        candidates.append(dedicated)
    if len(candidates) != 1:
        raise ValueError(f'{run}: expected exactly one saved client or dedicated world')
    return candidates[0].parent


class World:
    def __init__(self, directory):
        self.directory = directory
        self.chunks = {}

    def block(self, x, y, z):
        cx, cz = x >> 4, z >> 4
        if (cx, cz) not in self.chunks:
            path = self.directory / 'region' / f'r.{cx >> 5}.{cz >> 5}.mca'
            with path.open('rb') as file:
                file.seek(4 * ((cx & 31) + 32 * (cz & 31)))
                location = int.from_bytes(file.read(4), 'big')
                if not location >> 8:
                    raise ValueError(f'Unstored chunk {cx},{cz}')
                file.seek((location >> 8) * 4096)
                length = int.from_bytes(file.read(4), 'big')
                compression = file.read(1)[0]
                payload = file.read(length - 1)
            decoder = {1: gzip.decompress, 2: zlib.decompress, 3: lambda b: b}.get(compression)
            if decoder is None:
                raise ValueError(f'Unsupported region compression {compression}')
            self.chunks[cx, cz] = compound(decoder(payload))
        chunk = self.chunks[cx, cz]
        for section in chunk['sections'][1][1]:
            if section['Y'][1] != y >> 4 or 'block_states' not in section:
                continue
            states = section['block_states'][1]
            palette = states['palette'][1][1]
            index = 0
            if len(palette) > 1:
                bits = max(4, (len(palette)-1).bit_length())
                per_word = 64 // bits
                cell = (y & 15)*256 + (z & 15)*16 + (x & 15)
                index = (states['data'][1][cell // per_word] >> ((cell % per_word)*bits)) & ((1 << bits)-1)
            return {k: v[1] for k, v in palette[index].items()}
        return {'Name': 'minecraft:air'}


def rotate(x, z, rotation):
    return {'NONE': (x,z), 'CLOCKWISE_90': (-z,x), 'CLOCKWISE_180': (-x,-z),
            'COUNTERCLOCKWISE_90': (z,-x)}[rotation]


def audit(run, manifest):
    result = json.loads((run / 'result.json').read_text())
    world = World(saved_world(run))
    rows = {r['entry']: r for r in json.loads(manifest.read_text())}
    report = []
    for check in result.get('regionalNaturalChecks', []):
        row = rows[check['entry']]
        local = row['connector']
        origin = check['connector']
        positions = []
        for pos in row['guards'] + [row['sentry'], [local[0], local[1], local[2]-1]]:
            dx, dz = rotate(pos[0]-local[0], pos[2]-local[2], check['rotation'])
            positions.append((origin[0]+dx, origin[1]+pos[1]-local[1], origin[2]+dz))
        dx, dz = rotate(0, -1, check['rotation'])
        road = world.block(origin[0]+dx, math.floor(check['roadStandingY']-1e-6), origin[2]+dz)
        expected = re.search(r'Block\{([^}]+)\}', check['roadState']).group(1)
        if road['Name'] != expected:
            raise ValueError(f"Saved road state disagrees with native observation: {road['Name']} != {expected}")
        wet = []
        for pos in positions:
            for dy in (0,1,2):
                p = (pos[0],pos[1]+dy,pos[2]);state = world.block(*p)
                properties = state.get('Properties', {})
                if state['Name'] in ('minecraft:water','minecraft:lava') or properties.get('waterlogged', (8,'false'))[1] == 'true':
                    wet.append({'position': p, 'state': state['Name']})
        report.append({'entry':check['entry'], 'connector':origin, 'wetStandingCells':wet})
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--manifest', type=Path, default=Path('build/generated/regional-barracks-integration/manifest.json'))
    args = parser.parse_args()
    print(json.dumps(audit(args.run,args.manifest), indent=2))
