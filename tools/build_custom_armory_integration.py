#!/usr/bin/env python3
"""Optional Armory adapters for reviewed custom village road contracts."""
import gzip
import io
import json
import shutil
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from build_regional_armory_integration import integration as regional, ROOT
from create_flat_sandbox import read_payload, write_payload


@dataclass(frozen=True)
class Profile:
    provider: str
    theme: str
    region: str
    pool: str
    connector: str
    structure: str


PROFILES = tuple(Profile('ctov', theme, region,
    f'ctov:village/{theme}/' + ('roads' if theme == 'jungle' else 'terminator'),
    'minecraft:street', f'ctov:large/village_{theme}') for theme, region in (
        ('plains', 'plains'), ('desert', 'desert'), ('taiga', 'taiga'),
        ('snowy_igloo', 'snowy'), ('swamp', 'swamp'), ('jungle', 'jungle'))) + (
    Profile('sky-villages', 'island', 'plains', 'skyvillages:skyvillage_houses',
            'skyvillages:house', 'skyvillages:skyvillage'),)

# Kept for terrain-adapter development; excluded from player packs because
# native placement buried an entrance below a terrain-matching Clockwork road.
EXPERIMENTAL_PROFILES = (
    Profile('integrated-villages', 'clockwork', 'plains',
            'integrated_villages:clockwork_village/path/path', 'integrated_villages:path',
            'integrated_villages:clockwork_village'),
)


def shift_metadata(row, dx, dy, dz):
    def move(pos):
        for axis, delta in enumerate((dx, dy, dz)): pos[axis] += delta
    for key in ('anchor', 'sentry', 'entrance', 'service', 'connector'): move(row[key])
    for key in ('doors', 'targets', 'standing', 'openSky', 'lanterns'):
        for pos in row[key]: move(pos)
    for pair in row['walkTargets']:
        for pos in pair: move(pos)
    for key in ('details', 'thresholds'):
        for item in row.get(key, []): move(item['pos'])
    if 'storeBounds' in row:
        for axis, delta in enumerate((dx, dz, dx, dz)): row['storeBounds'][axis] += delta


def integration(profile, yard):
    blob, entry, row = regional(profile.region, yard)
    stream = io.BytesIO(gzip.decompress(blob))
    assert stream.read(3) == b'\x0a\x00\x00'
    tag = read_payload(stream, 10)
    palette = tag['palette'][1][1]
    blocks = {tuple(b['pos'][1][1]): b for b in tag['blocks'][1][1]}

    def put(pos, name, properties=None, nbt=None):
        state = {'Name': (8, name)}
        if properties: state['Properties'] = (10, {k: (8, v) for k, v in properties.items()})
        if state not in palette: palette.append(state)
        block = {'pos': (9, (3, list(pos))), 'state': (3, palette.index(state))}
        if nbt: block['nbt'] = (10, {k: (8, v) for k, v in nbt.items()})
        blocks[tuple(pos)] = block

    if profile.provider == 'sky-villages':
        # Native bridge connectors replace FLOOR blocks. Raise the complete
        # building onto a finite island, with a railed grass skirt at its edge.
        blocks = {}
        for block in tag['blocks'][1][1]:
            x, y, z = block['pos'][1][1]
            block['pos'] = (9, (3, [x + 1, y + 6, z]))
            blocks[x + 1, y + 6, z] = block
        shift_metadata(row, 1, 6, 0)
        row['size'] = [row['size'][0] + 2, row['size'][1] + 6, row['size'][2] + 1]
        w, _, d = row['size']
        for y in range(7):
            inset = (6-y)//2
            for x in range(inset, w-inset):
                for z in range(inset, d-inset):
                    pos = (x, y, z)
                    old = blocks.get(pos)
                    if old and palette[old['state'][1]]['Name'][1] != 'minecraft:air': continue
                    put(pos, 'minecraft:' + ('grass_block' if y == 6 else 'dirt' if y == 5 else 'stone'))
        entrance = row['connector'][0]
        for x in range(w):
            for z in range(d):
                if x not in (0, w-1) and z not in (0, d-1): continue
                if z == 0 and x in (entrance, entrance+1): continue
                put((x, 7, z), 'minecraft:oak_fence')
        put(row['connector'], 'minecraft:air')
        row['connector'][1] -= 1
        row['finalState'] = 'minecraft:oak_planks'
        put((entrance+1, 6, 0), 'minecraft:oak_planks')
        tag['size'] = (9, (3, row['size'].copy()))

    row['id'] = f'{profile.provider}-{profile.theme}-{row["layout"]}'
    path = f'compat/{profile.provider}/{profile.theme}/{row["layout"]}'
    # These adapters need their own terrain review (including floating islands).
    entry.pop('terrain_safety', None)
    entry['selection_chance'] = 0.25
    entry.update(target_pool=profile.pool, template=f'vggarmories:{path}')
    if profile.provider == 'sky-villages':
        # Native houses use weight 50; weight 1 with a quarter-chance made
        # Armories almost absent. Keep within VGG's existing weight limit.
        entry.update(weight=5, selection_chance=.5)
    row.update(template=entry['template'], entry=entry['template'], targetPool=profile.pool,
               connectorName=profile.connector, provider=profile.provider, theme=profile.theme,
               structure=profile.structure)
    put(row['connector'], 'minecraft:jigsaw', {'orientation': 'north_up'},
        dict(id='minecraft:jigsaw', joint='aligned', name=profile.connector,
             target=profile.connector, pool='minecraft:empty', final_state=row['finalState']))
    tag['blocks'] = (9, (10, [blocks[p] for p in sorted(blocks)]))
    out = io.BytesIO(b'\x0a\x00\x00'); out.seek(3); write_payload(out, 10, tag)
    return gzip.compress(out.getvalue(), mtime=0), entry, row


def main():
    output = ROOT/'build/generated/custom-armory-integration'
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as td:
        staged = Path(td); rows = []
        for profile in PROFILES:
            for yard in (False, True):
                blob, entry, row = integration(profile, yard); rows.append(row)
                pack = staged/profile.provider
                path = entry['template'].split(':', 1)[1]
                for relative, data in ((f'data/vggarmories/structures/{path}.nbt', blob),
                    (f'data/vggarmories/village_buildings/{path}.json', (json.dumps(entry, indent=2)+'\n').encode())):
                    target = pack/relative; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
                (pack/'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15,
                    'description': f'Villager Guard Gunners: custom village Armories ({profile.provider})'}})+'\n')
        (staged/'manifest.json').write_text(json.dumps(rows, indent=2)+'\n')
        if output.exists(): shutil.rmtree(output)
        shutil.copytree(staged, output)
    for provider in sorted({p.provider for p in PROFILES}):
        archive = ROOT/f'build/distributions/villager-guard-gunners-armories-{provider}.zip'
        archive.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
            for path in sorted((output/provider).rglob('*')):
                if path.is_file(): z.writestr(zipfile.ZipInfo(str(path.relative_to(output/provider))), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        print(archive)


if __name__ == '__main__': main()
