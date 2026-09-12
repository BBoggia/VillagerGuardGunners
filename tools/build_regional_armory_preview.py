#!/usr/bin/env python3
"""Build furnished regional Armory previews outside village generation."""
import argparse
import math
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from build_armory_preview import ROOT, state, encode
from draw_regional_armories import REGIONS, author, validate


def build(region, yard):
    p = author(region, yard)
    validate(p)
    W, D, H = p['size']
    F = p['floorY']
    pal = p['palette']
    air = state('air')
    grid = {(x, y, z): air for x in range(W) for y in range(F, H) for z in range(D)}

    def put(pos, block, **props):
        x, y, z = pos
        assert 0 <= x < W and 0 <= y < H and 0 <= z < D, (p['id'], 'bounds', pos)
        grid[x, y, z] = state(block, **props)

    def timber(pos, axis='y'):
        put(pos, pal['frame'], **({'axis': axis} if pal['frame'].endswith('_log') else {}))

    for f in p['floorSchedule']:
        x, y, z = f['pos']
        if F > 1:
            put((x, 0, z), f['underside'], **({'axis': 'x'} if f['underside'].endswith('_log') else {}))
        if f['top'] == F - .5:
            put((x, y, z), pal['wood'] + '_slab', type='bottom', waterlogged=False)
        else:
            put((x, y, z), f['material'])
    door_cells = {(l['pos'][0], l['pos'][2]) for d in p['doors'] for l in d['leaves']}
    for x, z in p['walls'] | p['posts'] | door_cells:
        top = max((r['lo'] for r in p['roofColumns'] if (r['x'], r['z']) == (x, z)), default=F + 4)
        for y in range(F, math.ceil(top)):
            if (x, z) in p['posts'] | p['frameColumns']:
                timber((x, y, z))
            else:
                put((x, y, z), pal['base'] if y == F else pal['wall'],
                    **({'axis': 'x'} if y == F and pal['base'].endswith('_log') else {}))
    # Full wall ties remain above the three-block route reservation.
    for b in p['beams']:
        for x in range(b['start'][0], b['end'][0] + 1):
            timber((x, b['start'][1], b['start'][2]), 'x')
    # Union actual occupied half-blocks, keeping each canopy at its own height.
    # At a wall/post/beam junction, a double roof slab seals the support's end.
    roof_masks = {}
    for r in p['roofColumns']:
        for half_y in range(round(r['lo'] * 2), round(r['hi'] * 2)):
            pos = (r['x'], half_y // 2, r['z'])
            roof_masks[pos] = roof_masks.get(pos, 0) | (1 << (half_y % 2))
    for pos, mask in roof_masks.items():
        if grid.get(pos, air) != air:
            mask = 3
        put(pos, pal['roof'] + '_slab', type={1: 'bottom', 2: 'top', 3: 'double'}[mask], waterlogged=False)
    for d in p['doors']:
        for leaf in d['leaves']:
            x, y, z = leaf['pos']
            for dy, half in enumerate(('lower', 'upper')):
                put((x, y + dy, z), leaf['block'], facing=leaf['facing'], hinge=leaf['hinge'],
                    half=half, open=False, powered=False)
            if region == 'snowy':
                # Sheltered thresholds open for approaching groups on either side.
                for dz in (-1, 1):
                    assert grid[x, y, z+dz] == air, (p['id'], 'occupied pressure plate')
                    put((x, y, z+dz), 'spruce_pressure_plate', powered=False)
    for w in p['windows']:
        for pos in w['cells']:
            put(pos, 'glass_pane', north=w['face'] in ('east', 'west'), south=w['face'] in ('east', 'west'),
                east=w['face'] in ('north', 'south'), west=w['face'] in ('north', 'south'), waterlogged=False)
    for s in p['shutters']:
        put(s['pos'], s['block'], facing=s['face'], open=True, half=s['half'], powered=False, waterlogged=False)
    for x, z in p['rails']:
        # Native fence connections are supplied as well as recomputed on placement.
        neighbours = p['rails'] | p['posts'] | p['walls']
        put((x, F, z), pal['wood'] + '_fence', waterlogged=False,
            north=(x, z-1) in neighbours, south=(x, z+1) in neighbours,
            west=(x-1, z) in neighbours, east=(x+1, z) in neighbours)
    for f in p['furniture']:
        for block in f['blocks']:
            props = dict(block['properties'])
            if block['material'].endswith('_slab'):
                props['waterlogged'] = False
            put(block['pos'], block['material'], **props)
    put(p['anchor'], 'villagerguardgunners:armory', facing='north')
    for lamp in p['lights']:
        put(lamp['pos'], 'lantern', hanging=True, waterlogged=False)
    put(p['banner']['pos'], pal['banner'] + '_wall_banner', facing='north')

    standing = set().union(*p['zones'].values()) | {(p['sentry'][0], p['sentry'][2])}
    for x, z in standing:
        assert grid.get((x, F-1, z), air) != air, (p['id'], 'floor', x, z)
        if (x, z) in door_cells:
            continue
        for y in range(F, F+3):
            assert grid[x, y, z] == air or grid[x, y, z][0].endswith(('_wall_banner', '_pressure_plate')), (p['id'], 'route', x, y, z, grid[x,y,z])
    for x, z in p['court']:
        assert all(grid[x, y, z] == air for y in range(F, H)), (p['id'], 'open court', x, z)
    for block in ('villagerguardgunners:armory', 'minecraft:crafting_table'):
        assert sum(s[0] == block for s in grid.values()) == 1, (p['id'], block)
    assert not any(s[0] in {'minecraft:chest', 'minecraft:barrel', 'minecraft:jigsaw',
                           'minecraft:structure_block', 'minecraft:structure_void'} for s in grid.values())

    def pair(zone):
        # Two adjacent interior destinations, avoiding the raised entrance half-step.
        cells = p['zones'][zone]
        candidates = [(x,z) for x,z in sorted(cells, key=lambda c: (c[1], c[0])) if (x+1,z) in cells]
        x,z = candidates[len(candidates)//2]
        return [[x,F,z],[x+1,F,z]]

    work = pair('work bay' if yard else 'sorting')
    supply = pair('service')
    targets = work + supply
    if region == 'snowy': targets += pair('lobby')
    if region == 'ocean':
        # Sample the turning bay, which has its own shelter and lamp.
        if yard: targets.append([15,F,14])
    layout = 'supply_yard' if yard else 'compact'
    details = [{'pos': list(pos), 'block': s[0], 'properties': dict(s[1])}
               for pos,s in sorted(grid.items()) if s[0].endswith(('_door','_trapdoor','_wall_banner','_pressure_plate'))
               or s[0] in ('minecraft:lantern','minecraft:glass_pane')]
    manifest = dict(id=region+'-'+layout, region=region, layout=layout, floorY=F,
        template=f'vggpreview:armory/{region}/{layout}', size=[W,H,D], anchor=p['anchor'], sentry=p['sentry'],
        entrance=[p['entry'][0],F,0], roadFeetY=1,
        doors=[l['pos'] for d in p['doors'] for l in d['leaves']],
        targets=targets, walkTargets=[work,supply],
        standing=[[x,F,z] for x,z in sorted(standing) if z != 0 or F == 1],
        thresholds=[dict(pos=[x,F-1,z],top=p['threshold']['top']) for x,z in p['threshold']['cells']],
        openSky=[[x,F,z] for x,z in sorted(p['court'])], lanterns=[l['pos'] for l in p['lights']],
        details=details, storeBounds=next(v['bounds'] for v in p['volumes'] if v['key']=='store'),
        scope='architecture preview; no village entries, generated stock, or garrison')
    return encode(grid,[W,H,D]), manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--regions', nargs='+', choices=REGIONS, default=list(REGIONS))
    parser.add_argument('--output', type=Path, default=ROOT/'build/generated/regional-armory-preview')
    parser.add_argument('--zip', type=Path, default=ROOT/'build/distributions/villager-guard-gunners-regional-armory-preview.zip')
    args = parser.parse_args()
    if len(args.regions) != len(set(args.regions)): parser.error('Regions must be unique')
    layouts = [build(r, y) for r in args.regions for y in (False,True)]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output.parent) as td:
        staged = Path(td)
        for blob,row in layouts:
            target = staged/f'data/vggpreview/structures/armory/{row["region"]}/{row["layout"]}.nbt'
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(blob)
        (staged/'data/vggpreview/armory_manifest.json').write_text(json.dumps([m for _,m in layouts],indent=2)+'\n')
        (staged/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,
            'description':'Villager Guard Gunners: regional Armory architecture previews'}})+'\n')
        if args.output.exists(): shutil.rmtree(args.output)
        shutil.copytree(staged,args.output)
    args.zip.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.zip,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(args.output.rglob('*')):
            if p.is_file(): z.writestr(zipfile.ZipInfo(str(p.relative_to(args.output))),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    print(f'Built {len(layouts)} furnished Armory previews: {args.zip}')


if __name__ == '__main__': main()
