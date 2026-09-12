#!/usr/bin/env python3
"""Build finite Sky Villages islands for the four remaining defense families."""
import gzip
import io
import json
import shutil
import tempfile
import zipfile
from pathlib import Path

from build_regional_barracks_integration import ROOT, integration as barracks
from build_regional_sentry_post_integration import integration as sentries
from build_regional_watchtower_integration import integration as watchtowers
from build_regional_training_yard_integration import integration as training
from create_flat_sandbox import read_payload, write_payload

FAMILIES = {'barracks': ('vggbarracks', barracks),
            'sentry-posts': ('vggsentries', sentries),
            'watchtowers': ('vggwatchtowers', watchtowers),
            'training-yards': ('vggtrainingyards', training)}
POINTS = ('anchor', 'sentry', 'entrance', 'service', 'work', 'connector')
POINT_LISTS = ('guards', 'targets', 'doors', 'standing', 'openSky', 'lanterns',
              'walkTargets', 'traffic', 'initialDeckPositions', 'deckPositions',
              'stairs', 'rails', 'slits', 'initialPositions', 'dutyPositions',
              'targetBlocks', 'circulation', 'field', 'lightSamples', 'roof')


def source(family, large):
    if family == 'barracks':
        plans = json.loads((ROOT/'src/structureDesign/resources/regional-barracks-details.json').read_text())['layouts']
        plan = next(p for p in plans if p['region'] == 'plains' and
                    p['layout'] == ('courtyard' if large else 'compact'))
        return barracks(plan)
    return FAMILIES[family][1]('plains', large)


def integration(family, large):
    blob, entry, row = source(family, large)
    stream = io.BytesIO(gzip.decompress(blob))
    if stream.read(3) != b'\x0a\x00\x00':
        raise ValueError('Expected an unnamed structure compound')
    tag = read_payload(stream, 10)
    palette = tag['palette'][1][1]
    if row['connector'][1:] != [1, 0] or row['finalState'] != 'minecraft:air':
        raise ValueError('Sky island adapter requires the flat Plains entrance')
    delta = (2, 6, 0)
    def move(pos): return [n+d for n, d in zip(pos, delta)]
    blocks = {}
    for block in tag['blocks'][1][1]:
        block['pos'] = (9, (3, move(block['pos'][1][1])))
        blocks[tuple(block['pos'][1][1])] = block
    for key in POINTS:
        if key in row: row[key] = move(row[key])
    for key in POINT_LISTS:
        if isinstance(row.get(key), list): row[key] = [move(p) for p in row[key]]
    for item in row.get('details', []): item['pos'] = move(item['pos'])
    if 'floorY' in row: row['floorY'] += 6
    if 'roofProfile' in row: row['roofProfile'] = [y+6 for y in row['roofProfile']]
    row['size'] = [row['size'][0]+4, row['size'][1]+6, row['size'][2]+2]
    w, _, d = row['size']
    def put(pos, name, properties=None, nbt=None):
        state = {'Name': (8, name)}
        if properties: state['Properties'] = (10, {k: (8, v) for k,v in properties.items()})
        if state not in palette: palette.append(state)
        block = {'pos': (9, (3, list(pos))), 'state': (3, palette.index(state))}
        if nbt: block['nbt'] = (10, {k: (8, v) for k,v in nbt.items()})
        blocks[tuple(pos)] = block
    # Preserve every furnished block above the foundation. The wider skirt
    # catches side doors and keeps the perimeter fence away from circulation.
    for y in range(7):
        inset = (6-y)//2
        for x in range(inset, w-inset):
            for z in range(inset, d-inset):
                old = blocks.get((x,y,z))
                if old and palette[old['state'][1]]['Name'][1] != 'minecraft:air': continue
                put((x,y,z), 'minecraft:'+('grass_block' if y == 6 else 'dirt' if y == 5 else 'stone'))
    entrance = row['connector'][0]
    edge = {(x,z) for x in range(w) for z in range(d)
            if (x in (0,w-1) or z in (0,d-1)) and not (z == 0 and x in (entrance,entrance+1))}
    for x,z in edge:
        props = {direction: str((x+dx,z+dz) in edge).lower()
                 for direction,dx,dz in [('north',0,-1),('south',0,1),('west',-1,0),('east',1,0)]}
        props['waterlogged'] = 'false'
        put((x,7,z), 'minecraft:oak_fence', props)
    put(row['connector'], 'minecraft:air')
    row['connector'][1] = 6
    put((entrance+1,6,0), 'minecraft:oak_planks')
    row['finalState'] = 'minecraft:oak_planks'
    put(row['connector'], 'minecraft:jigsaw', {'orientation': 'north_up'},
        dict(id='minecraft:jigsaw', joint='aligned', name='skyvillages:house',
             target='skyvillages:house', pool='minecraft:empty', final_state=row['finalState']))
    layout = row.get('layout', 'courtyard' if large else 'compact')
    resource = f'{FAMILIES[family][0]}:compat/sky-villages/island/{layout}'
    # These adapters need their own terrain review (including floating islands).
    entry.pop('terrain_safety', None)
    entry.update(template=resource, target_pool='skyvillages:skyvillage_houses', weight=5, selection_chance=.5)
    row.update(id=f'sky-{family}-{layout}', layout=layout, template=resource, entry=resource,
               targetPool=entry['target_pool'], connectorName='skyvillages:house',
               provider='sky-villages', theme='island', region='plains',
               structure='skyvillages:skyvillage', dutySlots=entry['duty_slots'])
    tag['size'] = (9, (3, row['size'].copy()))
    tag['blocks'] = (9, (10, [blocks[p] for p in sorted(blocks)]))
    if 'blockCount' in row: row['blockCount'] = len(blocks)
    out = io.BytesIO(b'\x0a\x00\x00'); out.seek(3); write_payload(out, 10, tag)
    return gzip.compress(out.getvalue(), mtime=0), entry, row


def main():
    output = ROOT/'build/generated/sky-village-integration'
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as td:
        staged = Path(td); rows = []
        for family in FAMILIES:
            pack = staged/family
            for large in (False, True):
                blob, entry, row = integration(family, large); rows.append(row)
                namespace, path = entry['template'].split(':', 1)
                for relative, data in [(f'data/{namespace}/structures/{path}.nbt', blob),
                    (f'data/{namespace}/village_buildings/{path}.json', (json.dumps(entry, indent=2)+'\n').encode())]:
                    target = pack/relative; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
            (pack/'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15,
                'description': f'Villager Guard Gunners: Sky Villages {family}'}})+'\n')
        (staged/'manifest.json').write_text(json.dumps(rows, indent=2)+'\n')
        if output.exists(): shutil.rmtree(output)
        shutil.copytree(staged, output)
    for family in FAMILIES:
        archive = ROOT/f'build/distributions/villager-guard-gunners-{family}-sky-villages.zip'
        archive.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
            for path in sorted((output/family).rglob('*')):
                if path.is_file(): z.writestr(zipfile.ZipInfo(str(path.relative_to(output/family))), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        print(archive)


if __name__ == '__main__': main()
