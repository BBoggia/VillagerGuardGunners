#!/usr/bin/env python3
"""Build opt-in village entries from the regional architecture source."""
import gzip
import io
import json
import shutil
import zipfile
from pathlib import Path
from village_building_pack_balance import write_builtin_balance

from build_regional_barracks import ROOT, build
from create_flat_sandbox import read_payload, write_payload


def integration(plan):
    blob, manifest = build(plan)
    stream = io.BytesIO(gzip.decompress(blob))
    assert stream.read(3) == b'\x0a\x00\x00'
    tag = read_payload(stream, 10)
    palette = tag['palette'][1][1]
    # Reserve an external connector apron. In particular, the Snowy lobby's
    # front doors occupy its original Z=0 and must never be replaced by a jigsaw.
    blocks = {}
    for block in tag['blocks'][1][1]:
        pos = list(block['pos'][1][1])
        pos[2] += 1
        block['pos'] = (9, (3, pos))
        blocks[tuple(pos)] = block
    tag['size'][1][1][2] += 1
    manifest['size'][2] += 1
    for key in ('guards', 'targets'):
        manifest[key] = [[x, y, z + 1] for x, y, z in manifest[key]]
    for key in ('sentry', 'entrance'):
        x, y, z = manifest[key]
        manifest[key] = [x, y, z + 1]

    def put(pos, name, properties, nbt=None):
        state = {'Name': (8, name)}
        if properties:
            state['Properties'] = (10, {k: (8, v) for k, v in properties.items()})
        if state not in palette:
            palette.append(state)
        block = {'pos': (9, (3, list(pos))), 'state': (3, palette.index(state))}
        if nbt:
            block['nbt'] = (10, {k: (8, v) for k, v in nbt.items()})
        blocks[tuple(pos)] = block

    region, layout = plan['region'], plan['layout']
    floor = plan['floorY']
    x = plan['basePlan']['connector'][0]
    raised = floor > 1
    connector = [x, floor - 1 if raised else floor, 0]
    final_state = 'minecraft:air'
    for dx in (0, 1):
        put((x+dx, floor-1, 0), 'minecraft:'+plan['palette']['wood']+'_planks', {})
    if raised:
        # Rotation-independent half step between the road and elevated floor.
        slab = 'minecraft:' + plan['palette']['wood'] + '_slab'
        for dx in (0, 1):
            put((x + dx, floor - 1, 0), slab, {'type': 'bottom', 'waterlogged': 'false'})
        final_state = slab + '[type=bottom,waterlogged=false]'
    name = 'mmv:street' if region in ('jungle', 'swamp') else 'minecraft:street'
    put(connector, 'minecraft:jigsaw', {'orientation': 'north_up'}, {
        'id': 'minecraft:jigsaw', 'joint': 'aligned', 'name': name,
        'target': name, 'pool': 'minecraft:empty', 'final_state': final_state,
    })
    anchor = plan['basePlan']['guards'][0]
    put((anchor[0],anchor[1],anchor[2]+1), 'minecraft:structure_block', {'mode': 'data'}, {
        'id': 'minecraft:structure_block', 'mode': 'DATA', 'metadata': 'village_anchor:barracks',
    })
    tag['blocks'] = (9, (10, [blocks[p] for p in sorted(blocks)]))
    output = io.BytesIO(b'\x0a\x00\x00')
    output.seek(3)
    write_payload(output, 10, tag)
    template = f'vggregional:regional/{region}/{layout}'
    namespace = 'joshie' if region == 'ocean' else 'mmv' if region in ('jungle', 'swamp') else 'minecraft'
    slots = []
    for key, pos, facing, kind in [
        ('center_muster', anchor, 'north', 'muster'),
        ('west_muster', plan['basePlan']['guards'][1], 'west', 'muster'),
        ('east_muster', plan['basePlan']['guards'][2], 'east', 'muster'),
        ('entry_sentry', plan['basePlan']['sentry'], 'north', 'sentry'),
    ]:
        slots.append(dict(key=key, offset=[a-b for a, b in zip(pos, anchor)],
                          facing=facing, kind=kind, weapon_requirement='any'))
    entry = dict(format=1, building_type='barracks', target_pool=f'{namespace}:village/{region}/'+('roads' if region == 'ocean' else 'terminators'),
                 template=template, processors='minecraft:empty', projection='rigid', weight=1, selection_chance=0.25, duty_slots=slots)
    if region in ('plains', 'desert', 'savanna', 'taiga', 'snowy'):
        entry['terrain_safety'] = True
        entry['selection_chance'] = 0.5
    manifest.update(template=template, entry=f'vggregional:barracks/{region}/{layout}',
                    connector=connector, connectorName=name, finalState=final_state, anchor=[anchor[0],anchor[1],anchor[2]+1],
                    targetPool=entry['target_pool'], integration=True)
    return gzip.compress(output.getvalue(), mtime=0), entry, manifest


def main():
    output = ROOT / 'build/generated/regional-barracks-integration'
    if output.exists():
        shutil.rmtree(output)
    manifests = []
    plans = json.loads((ROOT / 'src/structureDesign/resources/regional-barracks-details.json').read_text())['layouts']
    for plan in plans:
        blob, entry, manifest = integration(plan)
        manifests.append(manifest)
        group = 'tidal-towns' if plan['region'] == 'ocean' else 'mmv' if plan['region'] in ('jungle', 'swamp') else 'vanilla'
        pack = output / group
        for path, data in [
            (f'data/vggregional/structures/regional/{plan["region"]}/{plan["layout"]}.nbt', blob),
            (f'data/vggregional/village_buildings/barracks/{plan["region"]}/{plan["layout"]}.json',
             (json.dumps(entry, indent=2)+'\n').encode()),
        ]:
            target = pack / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15,
            'description': f'Villager Guard Gunners: regional Barracks integration ({group})'}})+'\n')
    write_builtin_balance(output / 'vanilla', 'barracks', ROOT)
    # QA metadata is separate, so installing a player pack never starts a fixture.
    (output / 'manifest.json').write_text(json.dumps(manifests, indent=2)+'\n')
    distributions = ROOT / 'build/distributions'
    distributions.mkdir(parents=True, exist_ok=True)
    for group in ('vanilla', 'tidal-towns', 'mmv'):
        target = distributions / f'villager-guard-gunners-regional-barracks-{group}.zip'
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted((output / group).rglob('*')):
                if path.is_file():
                    archive.writestr(zipfile.ZipInfo(path.relative_to(output / group).as_posix()),
                                     path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        print(target)


if __name__ == '__main__':
    main()
