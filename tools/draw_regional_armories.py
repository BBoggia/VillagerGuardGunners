#!/usr/bin/env python3
"""Author and check architectural drawings only; never emit Minecraft templates."""

import argparse
from collections import deque
from html import escape
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/designs/armory'


def rect(x1, z1, x2, z2):
    return {(x, z) for x in range(x1, x2 + 1) for z in range(z1, z2 + 1)}


def edge(bounds):
    x1, z1, x2, z2 = bounds
    return rect(*bounds) - rect(x1 + 1, z1 + 1, x2 - 1, z2 - 1)


REGIONS = {
    'desert': dict(title='Desert', sizes=((13, 13, 9), (16, 17, 9)),
                   wood='acacia', wall='smooth_sandstone', frame='cut_sandstone',
                   base='sandstone', roof='sandstone', banner='yellow',
                   colors=('#ead6a5', '#b89963', '#dfc58d'), shape='flat', rise=0),
    'savanna': dict(title='Savanna', sizes=((15, 14, 10), (17, 18, 10)),
                    wood='acacia', wall='terracotta', frame='stripped_acacia_log',
                    base='cobblestone', roof='acacia', banner='orange',
                    colors=('#c98765', '#926646', '#b5683f'), shape='hip', rise=.5),
    'taiga': dict(title='Taiga', sizes=((13, 15, 12), (16, 18, 13)),
                  wood='spruce', wall='spruce_planks', frame='stripped_spruce_log',
                  base='cobblestone', roof='spruce', banner='green',
                  colors=('#b1946c', '#705136', '#665441'), shape='gable', rise=1),
    'snowy': dict(title='Snowy', sizes=((15, 17, 12), (17, 20, 12)),
                  wood='spruce', wall='white_terracotta', frame='stripped_spruce_log',
                  base='stone_bricks', roof='spruce', banner='blue',
                  colors=('#e5e1d5', '#775c44', '#665441'), shape='gable', rise=1),
    'ocean': dict(title='Deep ocean · Tidal Towns', sizes=((16, 16, 11), (18, 18, 11)),
                  wood='oak', wall='oak_planks', frame='stripped_spruce_log',
                  base='oak_log', roof='spruce', banner='cyan',
                  colors=('#c3a77a', '#76654e', '#6d6153'), shape='gable', rise=.5),
    'jungle': dict(title='Jungle · MMV', sizes=((15, 15, 12), (17, 18, 12)),
                   wood='jungle', wall='jungle_planks', frame='stripped_jungle_log',
                   base='mossy_cobblestone', roof='jungle', banner='green',
                   colors=('#c2a185', '#8c6654', '#97785a'), shape='hip', rise=1),
    'swamp': dict(title='Swamp · MMV', sizes=((15, 15, 11), (17, 18, 11)),
                  wood='dark_oak', wall='mud_bricks', frame='dark_oak_log',
                  base='mossy_cobblestone', roof='dark_oak', banner='brown',
                  colors=('#b2a28b', '#63503b', '#615342'), shape='gable', rise=.5),
}


def author(region, yard):
    pal = REGIONS[region]
    W, D, H = pal['sizes'][yard]
    F = 2 if region in ('ocean', 'jungle', 'swamp') else 1
    p = dict(id=f'{region}-{"supply-yard" if yard else "compact"}', region=region,
             layout='Supply yard' if yard else 'Compact storehouse', size=[W, D, H],
             floorY=F, palette=pal, floors=set(), walls=set(), posts=set(), rails=set(),
             doors=[], furniture=[], windows=[], shutters=[], lights=[], beams=[],
             roofs=[], zones={}, volumes=[], court=set(), refuge=set())

    def shell(key, bounds):
        p['volumes'].append(dict(key=key, bounds=list(bounds)))
        p['walls'] |= edge(bounds)
        p['floors'] |= rect(*bounds)

    def doors(x, z, key):
        p['doors'].append(dict(key=key, leaves=[
            dict(pos=[x, F, z], facing='north', hinge='left', block=pal['wood']+'_door'),
            dict(pos=[x+1, F, z], facing='north', hinge='right', block=pal['wood']+'_door')]))
        p['walls'] -= {(x, z), (x+1, z)}

    def roof(key, bounds, kind, axis, eave, rise=0, cap=None):
        if kind == 'lean' and rise == 0:
            kind = 'flat'
        p['roofs'].append(dict(key=key, bounds=list(bounds), kind=kind, axis=axis,
                               eave=eave, rise=rise, cap=H-.5 if cap is None else cap,
                               material=pal['roof'], parapet=.5 if region == 'desert' and key == 'main' else 0))

    def furnish(kind, x, z, height=1):
        material='crafting_table' if kind=='crafting' else pal['wood']+'_slab' if kind in ('seat','worktop') else pal['wood']+'_planks'
        properties={'type':'bottom' if kind=='seat' else 'top'} if kind in ('seat','worktop') else {}
        blocks=[dict(pos=[x,F,z],material=material,properties=properties)]
        if kind=='shelf':
            blocks.append(dict(pos=[x,F+1,z],material=pal['wood']+'_slab',properties={'type':'top'}))
        p['furniture'].append(dict(kind=kind, pos=[x, F, z], height=height, blocks=blocks))

    def zone(key, bounds):
        p['zones'][key] = rect(*bounds)

    def light(key, x, z, x1, x2):
        # Full horizontal beam with its ends tied to named wall/post columns.
        p['lights'].append(dict(key=key, pos=[x, F+3, z], support=[x, F+4, z]))
        p['beams'].append(dict(key=key, start=[x1, F+4, z], end=[x2, F+4, z], axis='x'))
        p['posts'] |= {(a, z) for a in (x1, x2) if (a, z) not in p['walls']}

    if not yard:
        x0 = 2 if region in ('savanna', 'ocean', 'jungle', 'swamp') else 1
        front = D-10
        bounds = [x0, front, x0+10, D-2]
        if region == 'snowy':
            bounds = [1, 7, 13, 15]
        x0, front, xend, back = bounds
        shell('store', bounds)
        door_x = x0+4 if region != 'snowy' else 7
        doors(door_x, front, 'store')
        p['entry'] = [door_x, 0]
        p['anchor'] = [xend-2, F, back-1]
        p['sentry'] = [door_x+3, F, 2]
        zone('aisle', [door_x, front+1, door_x+1, back-2])
        zone('entry landing', [door_x-1, front+1, door_x+1, front+3])
        zone('sorting', [xend-4, front+1, xend-2, front+3])
        zone('service', [xend-3, back-4, xend-1, back-2])
        zone('bypass', [door_x, back-4, xend-4, back-1])
        zone('service crossing', [door_x, back-4, xend-1, back-2])
        for z in range(front+3, back-1):
            furnish('shelf', x0+1, z, 2)
        if region == 'savanna':
            furnish('seat', xend-1, front-1)
        elif region in ('taiga','jungle','swamp'):
            furnish('seat', x0+1, front-1)
        else:
            furnish('seat', x0+1, front+1)
        furnish('crafting', xend-1, front+1)
        furnish('worktop', xend-1, front+2)
        if region == 'snowy':
            shell('lobby', [4, 3, 10, 7])
            # Shared wall must retain the inner opening after merging shells.
            p['walls'] -= {(7, 7), (8, 7)}
            doors(5, 3, 'outer lobby')
            p['entry'] = [5, 0]
            zone('lobby', [5, 4, 9, 6])
            zone('approach', [5, 0, 6, 2])
            p['sentry'] = [10, F, 2]
            p['floors'] |= rect(4, 1, 11, 2)
            roof('lobby', [3, 3, 11, 7], 'lean', 'z', F+5.5, .0)
            roof('porch', [3, 1, 12, 3], 'lean', 'z', F+5.5, .0)
            light('porch', 7, 2, 4, 11)
            light('lobby', 7, 5, 4, 10)
        else:
            zone('approach', [door_x, 0, door_x+1, front-1])
            p['floors'] |= rect(x0+(1 if region=='taiga' else 2), 1, xend-1, front-1)
            if region in ('savanna', 'jungle', 'swamp'):
                p['floors'] |= rect(x0, 1, xend, front-1)
            porch_z = 1 if front == 3 else 2
            light('porch', door_x+1, porch_z, x0+2, xend-1)
            if region == 'taiga':
                # Continue the main gable's profile over the deep porch.
                roof('porch', [x0-1, 1, xend+1, front], 'gable', 'x', F+5.5, pal['rise'])
            else:
                roof('porch', [x0+1, 1, xend, front], 'flat', 'z', F+5.5)
        if region == 'ocean':
            p['sentry'] = [3, F, 2]
            p['refuge'] = rect(10, 2, 12, 4)
            zone('deck crossing', [3, 4, 12, 5])
            # Widen porch shelter and put posts outside the refuge.
            p['beams'] = [b for b in p['beams'] if b['key'] != 'porch']
            p['lights'] = [l for l in p['lights'] if l['key'] != 'porch']
            p['posts'] = set()
            p['roofs'] = [r for r in p['roofs'] if r['key'] != 'porch']
            roof('porch', [1, 1, 14, 6], 'lean', 'z', F+5.5, .0)
            light('porch', 7, 2, 2, 13)
        roof('main', [x0-1, front-1, xend+1, D-1], pal['shape'], 'x',
             F+6 if region == 'desert' else F+5.5, pal['rise'])
        light('room', door_x+1, front+3, x0, xend)
        light('supply', xend-2, back-2, x0, xend)
        win_schedules = [('west', x0, front+1), ('east', xend, front+1)]
    else:
        front = D-9
        xend = 13 if region == 'ocean' else W-2
        bounds = [1, front, xend, D-2]
        shell('store', bounds)
        door_x = 6 if region == 'ocean' else 7
        if region == 'snowy':
            door_x = 8
        doors(door_x, front, 'store')
        p['entry'] = [door_x, 0]
        a = xend-4
        p['anchor'] = [a, F, D-3]
        p['sentry'] = [11 if W == 16 else 12, F, 2]
        zone('aisle', [door_x, front+1, door_x+1, D-4])
        zone('interior crossing', [door_x, front+1, xend-1, front+2])
        zone('service', [a-1, D-6, a+1, D-4])
        zone('bypass', [a+2, front+1, xend-1, D-3])
        zone('packing', [3, front+2, 5, front+4])
        for x in range(2, 6):
            furnish('shelf', x, D-3, 2)
        for z in (front+2, front+3):
            furnish('worktop', 2, z)
        for x in (3, 4):
            furnish('seat', x, front+1)
        for kind, x, z in [('crafting', 2, 4), ('worktop', 3, 4), ('crate', 2, 3)]:
            if region == 'taiga' and kind == 'crate':
                x, z = 4, 4
            furnish(kind, x, z)
        zone('work bay', [2, 5, 4, 7])
        zone('work crossing', [2, front-2, 7, front-1])
        zone('frontage', [6, front-2, xend-1, front-1])
        zone('approach', [door_x, 0, door_x+1, front-1])
        p['court'] = rect(7, 2, 10, 5)
        p['walls'] |= rect(1, 3, 1, front-1)
        p['floors'] |= rect(1, 1, W-2, D-2)
        if region == 'taiga':
            p['walls'] |= rect(1, 3, 4, 3)
        roof('workshop', [0, 2, 6, front], 'lean', 'x-reverse', F+5.5, 0 if region == 'desert' else .25)
        # Ocean's rear two canopy courses meet the main roof at equal heights.
        roof('frontage', [5, front-2, xend, front], 'lean', 'z',
             F+5 if region == 'ocean' else F+5.5, .5 if region == 'ocean' else 0)
        light('workshop', 3, 5, 1, 5)
        light('frontage', 10, front-2, 1, xend)
        if region == 'snowy':
            # The lobby occupies the former frontage: use the west crossing.
            shell('lobby', [5, 7, 11, 11])
            p['walls'] -= {(8, 11), (9, 11)}
            doors(6, 7, 'outer lobby')
            p['entry'] = [6, 0]
            zone('lobby', [6, 8, 10, 10])
            zone('approach', [6, 0, 7, 6])
            zone('work bay', [2, 7, 4, 9])
            zone('work crossing', [2, 5, 7, 6])
            p['zones'].pop('frontage')
            p['court'] = rect(8, 2, 11, 5)
            p['posts'] = set()
            p['lights'] = []
            p['beams'] = []
            p['roofs'] = []
            roof('workshop', [0, 2, 5, 10], 'lean', 'x-reverse', F+5.5, .5)
            roof('crossing', [2, 5, 7, 6], 'lean', 'z', F+5.5)
            roof('lobby', [4, 6, 12, 11], 'lean', 'z', F+5.5)
            roof('sentry', [12, 1, 15, 3], 'lean', 'z', F+5.5)
            light('workshop', 3, 7, 1, 5)
            light('crossing', 6, 6, 1, 8)
            light('lobby', 8, 9, 5, 11)
            light('sentry', 13, 1, 12, 15)
            p['rails'] |= rect(15, 4, 15, 9)
        else:
            # A small supported entrance light, clear of the court and sentry.
            roof('sentry', [11, 1, W-2, 2], 'lean', 'z', F+5.5)
            light('arrival', 12, 1, 11, W-2)
        if region == 'ocean':
            zone('eastern return', [14, 3, 15, 12])
            zone('deck crossing', [11, 5, 15, 6])
            p['refuge'] = rect(14, 13, 16, 15)
            roof('refuge', [14, 12, 17, 16], 'lean', 'z', F+5.5)
            light('refuge', 15, 15, 13, 17)
        roof('main', [0, front-1, xend+1, D-1], pal['shape'], 'z',
             F+6 if region == 'desert' else F+5.5, pal['rise'])
        light('room', 5, front+2, 1, xend)
        light('supply', a, D-3, 1, xend)
        win_schedules = [('west', 1, front+2), ('east', xend, front+3)]

    p['floors'] |= rect(p['entry'][0], 0, p['entry'][0]+1, 0)
    if F == 2:
        p['floors'] |= rect(0, 1, W-1, D-1)
        p['rails'] |= edge([0, 1, W-1, D-1]) - rect(p['entry'][0], 1, p['entry'][0]+1, 1)
    p['floors'] |= p['posts'] | p['rails']
    p['rails'] -= p['walls'] | p['posts']
    # Window pairs have external hinged shutters only when outside circulation.
    route = set().union(*p['zones'].values()) | p['refuge']
    for face, x, z in win_schedules:
        wy = F+2 if region == 'desert' else F+1
        win = dict(face=face, cells=[[x, wy, z], [x, wy, z+1]], height=1)
        p['windows'].append(win)
        out_x = x + (1 if face == 'east' else -1)
        for zz in (z-1, z+2):
            if (out_x, zz) not in route:
                p['shutters'].append(dict(pos=[out_x, wy, zz], support=[x, wy, zz],
                    face=face, block=pal['wood']+'_trapdoor', open=True, half='top'))
    if region == 'jungle':
        rear=next(v['bounds'][3] for v in p['volumes'] if v['key']=='store')
        ax=p['anchor'][0]
        p['windows'].append(dict(face='south', cells=[[ax-1,F+3,rear],[ax,F+3,rear]],height=1))
    # Banner backs onto the front wall, beside the doorway and clear of leaves.
    frontwall = next(v for v in p['volumes'] if v['key'] == 'store')['bounds']
    bx, bz = frontwall[2]-1, frontwall[1]-1
    if region == 'snowy':
        bx = frontwall[2]-1
    p['banner'] = dict(pos=[bx, F+2, bz], support=[bx, F+2, bz+1], facing='north', color=pal['banner'])
    # Metadata position is occupied furniture; there is no guard spawn cell.
    p['anchorFacing'] = 'north'
    p['sentryOffset'] = [p['sentry'][i]-p['anchor'][i] for i in range(3)]
    p['threshold'] = dict(cells=[[p['entry'][0]+dx, 0] for dx in (0, 1)],
                          top=F-.5 if F == 2 else F,
                          providerRoadHeight='unmeasured', extraIntegrationApron=1)
    p['frameColumns'] = set()
    for v in p['volumes']:
        x1,z1,x2,z2=v['bounds']
        p['frameColumns'] |= {(x1,z1),(x2,z1),(x1,z2),(x2,z2)}
    for d in p['doors']:
        x,_,z=d['leaves'][0]['pos']
        p['frameColumns'] |= {(x-1,z),(x+2,z)}
    for w in p['windows']:
        x,_,z=w['cells'][0]
        p['frameColumns'] |= {(x-1,z),(x+2,z)} if w['face'] in ('north','south') else {(x,z-1),(x,z+2)}
    p['frameColumns'] &= p['walls']
    indoors=set().union(*(rect(*v['bounds']) for v in p['volumes']))
    p['floorSchedule']=[]
    for x,z in sorted(p['floors']):
        material=pal['wood']+'_planks'
        if region=='desert': material='smooth_sandstone'
        elif yard and F==1 and (x,z) not in indoors: material='stone_bricks'
        top=p['threshold']['top'] if z==0 else F
        p['floorSchedule'].append(dict(pos=[x,F-1,z], top=top, material=material,
            underside=pal['base'] if F==2 else None))
    # Roof intervals retain every canopy; union only overlapping vertical ranges.
    p['roofColumns'] = []
    for r in p['roofs']:
        x1, z1, x2, z2 = r['bounds']
        for x, z in sorted(rect(*r['bounds'])):
            n = min(x-x1, x2-x) if r['axis'] == 'x' else min(z-z1, z2-z)
            if r['kind'] == 'hip':
                n = min(n, min(z-z1, z2-z)+2) if r['axis'] == 'x' else min(n, min(x-x1, x2-x)+2)
            if r['kind'] == 'lean':
                n = x2-x if r['axis'] == 'x-reverse' else z-z1
            top = min(r['cap'], r['eave'] + math.floor(n*r['rise']*2)/2)
            # One-block minimum thickness backs each half/full-block riser.
            low = top-max(1, r['rise']+.5)
            p['roofColumns'].append(dict(x=x, z=z, lo=low, hi=top, roof=r['key']))
            if r['parapet'] and (x, z) in edge(r['bounds']):
                p['roofColumns'].append(dict(x=x, z=z, lo=top, hi=top+r['parapet'], roof=r['key']))
    return p


def reachable(walk, start):
    if start not in walk:
        return set()
    found = {start}
    queue = deque(found)
    while queue:
        x, z = queue.popleft()
        for q in ((x-1,z), (x+1,z), (x,z-1), (x,z+1)):
            if q in walk and q not in found:
                found.add(q)
                queue.append(q)
    return found


def validate(p):
    W, D, H = p['size']
    F = p['floorY']
    obstacles = p['walls'] | p['posts'] | p['rails']
    obstacles |= {(f['pos'][0], f['pos'][2]) for f in p['furniture']}
    obstacles.add((p['anchor'][0], p['anchor'][2]))
    standing = set().union(*p['zones'].values()) | p['court'] | p['refuge']
    sentry = (p['sentry'][0], p['sentry'][2])
    assert not (standing & obstacles), (p['id'], 'route obstruction', standing & obstacles)
    assert sentry not in standing | obstacles, (p['id'], 'sentry overlap', sentry)
    assert standing | {sentry} <= p['floors'], (p['id'], 'unsupported route')
    assert all(0 <= x < W and 0 <= z < D for x,z in p['floors'] | obstacles)
    for f in p['furniture']:
        assert (f['pos'][0],f['pos'][2]) not in p['walls'] | p['posts'] | p['rails']
        assert (f['pos'][0],f['pos'][2]) in p['floors']
    assert len({tuple(f['pos']) for f in p['furniture']}) == len(p['furniture'])
    assert sum(f['kind'] == 'crafting' for f in p['furniture']) == 1
    for r in p['roofColumns']:
        assert 0 <= r['x'] < W and 0 <= r['z'] < D and 0 <= r['lo'] < r['hi'] <= H, (p['id'], 'roof bounds', r)
        assert (r['x'],r['z']) not in p['court'], (p['id'], 'covered court', r)
        if (r['x'],r['z']) in standing | {sentry}:
            assert r['lo'] >= F+3, (p['id'], 'low roof', r)
    for l, b in zip(p['lights'], p['beams']):
        x, y, z = l['pos']
        assert (x,z) not in p['posts'] | p['walls'], (p['id'], 'lamp embedded in support', l)
        assert b['start'][0] <= x <= b['end'][0] and b['start'][2] == z
        assert l['support'] == [x, y+1, z] and y >= F+3
        for end in (b['start'], b['end']):
            assert (end[0],end[2]) in p['walls'] | p['posts'], (p['id'], 'beam support')
        for bx in range(b['start'][0],b['end'][0]+1):
            covering=[r['hi'] for r in p['roofColumns'] if r['x']==bx and r['z']==z]
            assert covering and max(covering)>=F+5, (p['id'], 'exposed beam', b)
        above = [r['hi'] for r in p['roofColumns'] if r['x'] == x and r['z'] == z]
        assert above and max(above) >= y+2, (p['id'], 'beam above roof', l)
    for s in p['shutters']:
        assert (s['pos'][0],s['pos'][2]) not in standing | {sentry}, (p['id'], 'shutter route')
        assert (s['support'][0],s['support'][2]) in p['walls'], (p['id'], 'shutter backing')
        assert s['support'] not in [c for w in p['windows'] for c in w['cells']]
    for w in p['windows']:
        for x,y,z in w['cells']:
            assert (x,z) in p['walls'] and F+1<=y<H
    door_cells={(l['pos'][0],l['pos'][2]) for d in p['doors'] for l in d['leaves']}
    assert not (door_cells & obstacles), (p['id'], 'door obstruction')
    assert door_cells <= p['floors']
    assert (p['banner']['support'][0],p['banner']['support'][2]) in p['walls'], (p['id'], 'banner support')
    walk = p['floors'] - obstacles - {sentry}
    found = reachable(walk, tuple(p['entry']))
    assert standing <= found, (p['id'], 'unreachable', standing-found)
    assert any((sentry[0]+dx,sentry[1]+dz) in found for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))
    bypass = reachable(walk - p['zones']['service'], tuple(p['entry']))
    assert p['zones']['bypass'] <= bypass, (p['id'], 'service blocks bypass')
    ax, _, az = p['anchor']
    assert p['zones']['service'] == rect(ax-1, az-3, ax+1, az-1)
    assert all(abs(v) <= 16 for v in p['sentryOffset']), (p['id'], 'duty offset')
    sheltered=set().union(*(c for key,c in p['zones'].items() if key not in ('approach','eastern return','deck crossing')))
    sheltered|={sentry,(p['anchor'][0],p['anchor'][2])}
    covered={(r['x'],r['z']) for r in p['roofColumns']}
    assert sheltered <= covered, (p['id'], 'unsheltered working space', sheltered-covered)
    # Geometric rotation of all reservations, not native block or door behavior.
    for turn in range(4):
        def rot(c):
            x,z = c
            for _ in range(turn):
                x,z = -z,x
            return x,z
        assert {rot(c) for c in standing} <= reachable({rot(c) for c in walk}, rot(tuple(p['entry'])))
    return dict(id=p['id'], standingCells=len(standing), openCourtCells=len(p['court']),
                plannedLights=len(p['lights']), planRotations=4, nativeValidation=False)


class SVG:
    def __init__(self, title, width=1500, height=1420):
        self.parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-labelledby="title desc">',
                      f'<title id="title">{escape(title)}</title>',
                      '<desc id="desc">Proposed Minecraft architecture: dimensioned plan, north and east elevations, roof and entrance sections. Not an in-game render.</desc>',
                      '<rect width="100%" height="100%" fill="#faf8f2"/>',
                      '<g font-family="Arial, sans-serif" fill="#263633">']

    def rect(self, x,y,w,h,fill,stroke='none', sw=.6, extra=''):
        self.parts.append(f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" {extra}/>')

    def text(self,x,y,text,size=15,anchor='start',color='#263633',weight='normal'):
        self.parts.append(f'<text x="{x:g}" y="{y:g}" font-size="{size}" text-anchor="{anchor}" fill="{color}" font-weight="{weight}">{escape(str(text))}</text>')

    def line(self,x1,y1,x2,y2,color='#687671',width=1,dash=''):
        self.parts.append(f'<line x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}" stroke="{color}" stroke-width="{width}" stroke-dasharray="{dash}"/>')

    def finish(self):
        return '\n'.join(self.parts + ['</g></svg>'])+'\n'


def furniture_interval(block):
    """Visible vertical extent of a scheduled full block or slab."""
    x, y, z = block['pos']
    lo, hi = 0, 1
    if block['material'].endswith('_slab'):
        lo, hi = {'bottom': (0, .5), 'top': (.5, 1), 'double': (0, 1)}[block['properties']['type']]
    return x, z, y+lo, y+hi


def geometry(p):
    """Colored vertical intervals for drawing; no NBT or game-state output."""
    F = p['floorY']
    wall, frame, roof_color = p['palette']['colors']
    out = []
    def add(x,z,lo,hi,color,kind):
        out.append((x,z,lo,hi,color,kind))
    door_cells = {(l['pos'][0],l['pos'][2]) for d in p['doors'] for l in d['leaves']}
    window_cells = {(x,z): y for w in p['windows'] for x,y,z in w['cells']}
    base_color='#c9b17d' if p['region']=='desert' else frame if p['region']=='ocean' else '#9a9687'
    for f in p['floorSchedule']:
        x,_,z=f['pos']
        if F>1: add(x,z,0,F-1,base_color,'base')
        color=wall if p['region']=='desert' else base_color if f['material']=='stone_bricks' else '#bca580'
        add(x,z,F-1,f['top'],color,'floor')
    for x,z in p['walls'] | p['posts'] | door_cells:
        tops = [r['lo'] for r in p['roofColumns'] if r['x']==x and r['z']==z]
        top = max(tops, default=F+4)
        is_post = (x,z) in p['posts']
        add(x,z,F,top,frame if is_post or (x,z) in p['frameColumns'] else wall,'wall')
        if not is_post and (x,z) not in door_cells:
            add(x,z,F,F+1,base_color,'base course')
        if (x,z) in door_cells:
            add(x,z,F,F+2,frame,'door')
        if (x,z) in window_cells:
            y = window_cells[x,z]
            add(x,z,y,y+1,'#9ecbd1','window')
    for x,z in p['rails']:
        add(x,z,F,F+1,'#8b795c','rail')
    for f in p['furniture']:
        for block in f['blocks']:
            add(*furniture_interval(block),frame,'furniture')
    x,y,z=p['anchor']
    add(x,z,y,y+1,'#6f8e63','armory')
    for r in p['roofColumns']:
        add(r['x'],r['z'],r['lo'],r['hi'],roof_color,'roof')
    for sh in p['shutters']:
        x,y,z = sh['pos']
        add(x,z,y,y+1,frame,'shutter')
    for b in p['beams']:
        for x in range(b['start'][0],b['end'][0]+1):
            add(x,b['start'][2],b['start'][1],b['start'][1]+1,frame,'beam')
    for l in p['lights']:
        x,y,z = l['pos']
        add(x,z,y,y+.75,'#edbc62','light')
    x,y,z = p['banner']['pos']
    add(x,z,y,y+1,{'yellow':'#c9a041','orange':'#c77741','green':'#597553','blue':'#6484a4','cyan':'#508b9a','brown':'#876747'}[p['banner']['color']],'banner')
    return out


def section_geometry(p):
    """Intersect shared geometry with the named cut inside the main roof bounds."""
    main=next(r for r in p['roofs'] if r['key']=='main')
    store=next(v['bounds'] for v in p['volumes'] if v['key']=='store')
    along_x=main['axis']=='x'
    cut=(store[1]+store[3])//2 if along_x else p['anchor'][0]
    start,end=(main['bounds'][0],main['bounds'][2]) if along_x else (main['bounds'][1],main['bounds'][3])
    solids=[]
    for x,z,lo,hi,color,tag in geometry(p):
        u,fixed=(x,z) if along_x else (z,x)
        if fixed==cut and start<=u<=end:
            solids.append((u,lo,hi,color,tag))
    routes=set().union(*(cells for name,cells in p['zones'].items()
                        if name not in ('sorting','packing','work bay')))
    clearance=sorted({x if along_x else z for x,z in routes
                      if (z if along_x else x)==cut and start<=(x if along_x else z)<=end})
    F=p['floorY']
    for u in clearance:
        assert not any(a==u and lo<F+3 and hi>F for a,lo,hi,_,_ in solids), (p['id'],'occupied section clearance',u)
    return dict(axis='z' if along_x else 'x',cut=cut,solids=solids,clearance=clearance)


def sheet(p):
    W,D,H = p['size']
    F = p['floorY']
    title = p['palette']['title']+' / '+p['layout']
    s = SVG(title)
    s.text(55,48,title,30,weight='bold')
    s.text(55,78,f'{W} × {D} × {H} blocks · feet Y={F} · one Village Armory · proposed architecture',17)
    s.line(55,95,1445,95)
    s.text(55,123,'01  FLOOR PLAN · north / road at top',18,weight='bold')
    ox,oz,k = 90,185,28
    def tile(c, color, label=None, inset=0):
        x,z = c
        s.rect(ox+x*k+inset,oz+z*k+inset,k-2*inset,k-2*inset,color,'#b7b6ab',.5)
        if label:
            s.text(ox+(x+.5)*k,oz+(z+.68)*k,label,13,'middle',weight='bold')
    for c in sorted(p['floors']): tile(c,'#e4ded1')
    for key,cells in p['zones'].items():
        color = '#cadbd6'
        if key in ('sorting','packing','work bay'): color='#ebdab7'
        if key == 'lobby': color='#bed4e1'
        for c in sorted(cells): tile(c,color)
    for c in sorted(p['court']): tile(c,'#faf8f2')
    for c in sorted(p['zones']['service']): tile(c,'#c6d7b5')
    for c in sorted(p['refuge']): tile(c,'#c7dce7')
    for c in sorted(p['walls']): tile(c,p['palette']['colors'][0])
    for c in sorted(p['frameColumns']): tile(c,p['palette']['colors'][1])
    for c in sorted(p['posts']): tile(c,p['palette']['colors'][1],'P')
    for c in sorted(p['rails']): tile(c,'#9b8a6f',inset=5)
    labels={'shelf':'E','crafting':'C','worktop':'B','seat':'b','crate':'K'}
    for f in p['furniture']: tile((f['pos'][0],f['pos'][2]),'#bda786',labels[f['kind']],2)
    tile((p['anchor'][0],p['anchor'][2]),'#6f8e63','A',2)
    tile((p['sentry'][0],p['sentry'][2]),'#f6dc9b','S',2)
    for win in p['windows']:
        for x,y,z in win['cells']: tile((x,z),'#9ecbd1','W',3)
    for d in p['doors']:
        for leaf in d['leaves']:
            x,_,z = leaf['pos']
            tile((x,z),'#f3eacb')
            xx=ox+(x+(0 if leaf['hinge']=='left' else 1))*k
            s.line(xx,oz+z*k,xx,oz+(z+1)*k,'#775536',3)
            s.line(ox+x*k,oz+z*k,ox+(x+1)*k,oz+z*k,'#775536',1,'3 3')
    for l in p['lights']:
        x,_,z=l['pos']
        s.text(ox+(x+.81)*k,oz+(z+.35)*k,'◇',13,'middle','#965d12')
    for x in range(W): s.text(ox+(x+.5)*k,oz-9,x,11,'middle')
    for z in range(D): s.text(ox-12,oz+(z+.65)*k,z,11,'end')
    s.line(ox,oz-32,ox+W*k,oz-32)
    s.text(ox+W*k/2,oz-39,f'{W} blocks across',12,'middle')
    s.text(ox+W*k+15,oz+D*k/2,f'{D} deep',12)
    for i, (color,text) in enumerate([
        ('#cadbd6','Clear routes · one square = one block'),
        ('#c6d7b5','3 × 3 service area · A occupied Armory'),
        ('#ebdab7','Work space · C crafting · B worktop · K crate'),
        ('#bed4e1','Lobby / refuge · S sentry · P roof post'),
        ('#bda786','E shelves · b seat · W glazing · ◇ lamp above')]):
        y=770+i*23
        s.rect(65,y-12,14,14,color,'#a6aca2')
        s.text(88,y,text,14)
    s.text(65,897,'Door edges show open leaves; dashed lines show closed leaves.',13)
    s.text(65,916,'Roof omitted in plan. High-level shutter cells are in the schedule.',13)

    geom = geometry(p)
    def elevation(kind, ox, baseline, label):
        s.text(790,baseline-H*25-25,label,18,weight='bold')
        # Half-block samples and nearest-surface selection give real occlusion.
        # Within a column, later intervals replace earlier decorative surfaces.
        pixels={}
        for x,z,lo,hi,color,tag in geom:
            u,dep=(x,z) if kind=='north' else (D-1-z,-x)
            for h in range(math.floor(lo*2),math.ceil(hi*2)):
                key=(u,h,dep)
                pixels[key]=(color,tag)
        visible={}
        for (u,h,dep),value in pixels.items():
            if (u,h) not in visible or dep<visible[u,h][0]:
                visible[u,h]=(dep,*value)
        for (u,h),(dep,color,tag) in sorted(visible.items()):
            s.rect(ox+u*25,baseline-(h+1)*12.5,25,12.5,color,'#faf8f2',.25)
        n=W if kind=='north' else D
        s.line(ox,baseline+2,ox+n*25,baseline+2)
        s.text(ox+n*12.5,baseline+22,f'{n} blocks · max envelope {H} high',12,'middle')
        s.line(ox-14,baseline,ox-14,baseline-H*25)
        for y in range(H+1):
            s.line(ox-18,baseline-y*25,ox-10,baseline-y*25)
            s.text(ox-23,baseline-y*25+4,y,10,'end')
        s.text(ox+n*25+10,baseline-F*25+4,f'feet Y={F}',11)
    elevation('north',815,480,'02  NORTH ELEVATION · arrival facade')
    elevation('east',815,880,'03  EAST ELEVATION · north / front at right')

    s.line(55,942,1445,942)
    s.text(55,978,'04  STORE SECTION · scheduled furniture included',18,weight='bold')
    section=section_geometry(p)
    along_x=section['axis']=='z'
    cut=section['cut']
    ss, base, origin = 23,1290,80
    for y in range(0,H+1,2):
        s.text(origin-12,base-y*ss+4,f'Y{y}',10,'end')
    for u in section['clearance']:
        s.rect(origin+u*ss,base-(F+3)*ss,ss,3*ss,'#cadbd6',extra='opacity=".55"')
    for u,lo,hi,color,tag in section['solids']:
        s.rect(origin+u*ss,base-hi*ss,ss,(hi-lo)*ss,color,'#faf8f2',.35)
        if tag=='armory':
            s.text(origin+(u+.5)*ss,base-(lo+.25)*ss,'A',12,'middle')
    for u in range((W if along_x else D)):
        s.text(origin+(u+.5)*ss,1305,u,10,'middle')
    s.text(70,1320,f'Cut {section["axis"].upper()}={cut} · green: 3-block clearance over reserved routes only',13)
    s.text(70,1341,'A: occupied Armory placeholder · native shapes and joins still need preview.',13)

    s.text(790,978,'05  ENTRANCE SECTION · approach sequence',18,weight='bold')
    s.text(790,1005,'Unfolded through offset doors' if p['region']=='snowy' else 'Two-cell-wide approach; exterior road elevation unmeasured',14)
    bx,by,sc=860,1220,30
    end=max(d['leaves'][0]['pos'][2] for d in p['doors'])+2
    for z in range(end+1):
        top=p['threshold']['top'] if z==0 else F
        s.rect(bx+z*sc,by-top*sc,sc,top*sc,'#b7aa90','#faf8f2')
    s.text(bx-15,by-F*sc,'road',13,'end')
    s.text(bx-15,by-F*sc+17,'TBD',13,'end')
    for d in p['doors']:
        z=d['leaves'][0]['pos'][2]
        s.rect(bx+z*sc,by-(F+2)*sc,sc,2*sc,'#bda27b','#75583a')
        s.rect(bx+z*sc,by-(F+3)*sc,sc,sc,p['palette']['colors'][1])
    s.line(bx,by-(F+3)*sc,bx+(end+1)*sc,by-(F+3)*sc,'#659080',1,'5 4')
    s.text(bx,by-(F+3)*sc-12,'3-block headroom target · doors 2 high',13)
    s.text(bx+(end+1)*sc/2,by+22,'Z=0 entrance margin → enclosed store',13,'middle')
    s.text(790,1280,f'Full walking surface Y={F}; first landing top Y={p["threshold"]["top"]:g}.',14)
    if p['region']=='snowy':
        s.text(790,1304,'Lobby: 5 wide × 3 deep; door pairs offset by 2 cells.',14)
    elif F==2:
        s.text(790,1304,'Full deck over finite underside; rail beside the lane, never across it.',14)
    else:
        s.text(790,1304,'Solid porch and apron; one future road connector.',14)
    s.text(790,1328,'Future connector apron adds 1 row outside this architecture.',14)
    s.line(55,1370,1445,1370)
    s.text(55,1395,'DESIGN ONLY · grid reachability ≠ Minecraft navigation · normal resupply can occur through exterior walls',14)
    return s.finish()


def serial(value):
    if isinstance(value,set): return [list(v) for v in sorted(value)]
    if isinstance(value,dict): return {k:serial(v) for k,v in value.items()}
    if isinstance(value,(list,tuple)): return [serial(v) for v in value]
    return value


def coordinates(plans):
    lines=['# Regional Armory coordinate schedule', '',
           'Generated architectural reservations. Read with the [drawing guide](../../regional-armory-drawings.md).', '',
           'Positions are `(X,Y,Z)` unless labeled X/Z. Bounds are inclusive; north is Z=0.',
           'Size order is width × depth × height. Floor Y is the standing surface, not the floor block.',
           'All door leaves face north, start closed, and have matching upper/lower halves.',
           'Both pairs in a Snowy lobby retain this orientation. Roof ranges describe material volumes,',
           'not finalized stair states. The detailed half-block roof columns are in [drawings.json](drawings.json).', '']
    def pos(v): return '('+', '.join(str(x) for x in v)+')'
    def bounds(c):
        return f'X={min(x for x,z in c)}..{max(x for x,z in c)}, Z={min(z for x,z in c)}..{max(z for x,z in c)}'
    for p in plans:
        lines += [f'## {p["palette"]["title"]} / {p["layout"]}', '',
                  f'[Drawing sheet]({p["id"]}.svg) · '+ ' × '.join(map(str,p['size']))+f' · standing Y={p["floorY"]}', '',
                  '| Reservation | Position or bounds |', '| --- | --- |',
                  f'| Armory, north-facing | {pos(p["anchor"])} |',
                  f'| Sentry | {pos(p["sentry"])}; anchor offset {pos(p["sentryOffset"])} |',
                  f'| First landing | {bounds(set(map(tuple,p["threshold"]["cells"])))}; surface Y={p["threshold"]["top"]} |']
        for v in p['volumes']:
            lines.append(f'| {v["key"]} shell | {bounds(rect(*v["bounds"]))} |')
        for name,c in p['zones'].items(): lines.append(f'| {name} | {bounds(c)} |')
        for name,c in [('Open court',p['court']),('Refuge',p['refuge'])]:
            if c: lines.append(f'| {name} | {bounds(c)} |')
        lines += ['', '| Door pair | West leaf / hinge | East leaf / hinge |', '| --- | --- | --- |']
        for d in p['doors']:
            a,b=d['leaves']
            lines.append(f'| {d["key"]} | {pos(a["pos"])} / left | {pos(b["pos"])} / right |')
        lines += ['', '| Furniture | Base positions |', '| --- | --- |']
        for kind in ('crafting','worktop','shelf','seat','crate'):
            items=[pos(f['pos']) for f in p['furniture'] if f['kind']==kind]
            if items: lines.append(f'| {kind} | {"; ".join(items)} |')
        lines += ['', '| Opening or decoration | Positions |', '| --- | --- |']
        for w in p['windows']:
            lines.append(f'| {w["face"]} glazing, one block high | '+ '; '.join(map(pos,w['cells']))+' |')
        for sh in p['shutters']:
            lines.append(f'| {sh["face"]} shutter | {pos(sh["pos"])}; solid backing {pos(sh["support"])} |')
        lines.append(f'| {p["banner"]["color"]} banner | {pos(p["banner"]["pos"])}; backing {pos(p["banner"]["support"])} |')
        lines += ['', '| Light | Lantern | Support beam endpoints |', '| --- | --- | --- |']
        for l,b in zip(p['lights'],p['beams']):
            lines.append(f'| {l["key"]} | {pos(l["pos"])} | {pos(b["start"])} → {pos(b["end"])} |')
        lines += ['', '| Roof | X/Z footprint | Shape / rise axis | Surface Y range |', '| --- | --- | --- | --- |']
        for r in p['roofs']:
            tops=[v['hi'] for v in p['roofColumns'] if v['roof']==r['key']]
            lines.append(f'| {r["key"]} | {bounds(rect(*r["bounds"]))} | {r["kind"]} / {r["axis"]} | {min(tops):g}..{max(tops):g} |')
        lines += ['', 'Post cells (X/Z): '+ '; '.join(pos(c) for c in sorted(p['posts']))+'.', '']
    return '\n'.join(lines)+'\n'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true',help='Validate drawings and compare without writing.')
    args=parser.parse_args()
    plans=[author(r,yard) for r in ('snowy','ocean','desert','savanna','taiga','jungle','swamp') for yard in (False,True)]
    reports=[validate(p) for p in plans]
    files={f'{p["id"]}.svg':sheet(p) for p in plans}
    files['coordinates.md']=coordinates(plans)
    files['drawings.json']=json.dumps(dict(status='proposed drawings; not native templates', coordinateOrder='size: width/depth/height; positions: x/y/z', plans=[serial(p) for p in plans]),indent=2)+'\n'
    links='\n'.join(f'<article><h2>{escape(p["palette"]["title"])} / {p["layout"]}</h2><a href="{p["id"]}.svg"><img loading="lazy" src="{p["id"]}.svg" alt="Dimensioned drawing for {p["id"]}"></a></article>' for p in plans)
    files['index.html']='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Regional Village Armory drawings</title><style>body{margin:0;background:#f3f0e8;color:#263633;font:16px system-ui}header,main{max-width:1500px;margin:auto;padding:24px}article{margin:0 0 48px}h1{font-size:32px}h2{font-size:21px}img{display:block;width:100%;height:auto;border:1px solid #d7d7cc}a{color:inherit}</style><header><h1>Regional Village Armory drawings</h1><p>Fourteen proposed layouts. Open a drawing for full-size coordinates and sections. These are architectural schematics, not Minecraft screenshots or validated templates.</p></header><main>'''+links+'</main></html>\n'
    for name,content in files.items():
        if name.endswith('.svg'): ET.fromstring(content)
        path=OUTPUT/name
        if args.check:
            assert path.exists() and path.read_text()==content, f'Stale drawing: {path}'
        else:
            OUTPUT.mkdir(parents=True,exist_ok=True)
            path.write_text(content)
    print(json.dumps(dict(drawings=len(plans), files=len(files), checks=reports),indent=2))


if __name__=='__main__':
    main()
