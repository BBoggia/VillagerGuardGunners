#!/usr/bin/env python3
"""Build seven regional pairs of furnished, manual-placement Watchtower previews."""
import argparse
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from build_armory_preview import ROOT, rect, state, encode
from build_watchtower_preview import build as plains
from build_regional_sentry_post_preview import REGIONS, decode


def build(region, gatehouse=False):
    pal = REGIONS[region]
    blob, row = plains(gatehouse)
    source = decode(blob)
    w, _, d = row['size']
    air = state('air')
    grid = {(x,y,z):air for x in range(w) for y in range(1,23) for z in range(d)}
    def put(x,y,z,name,**props): grid[x,y,z] = state(name,**props)
    def timber(x,y,z,axis='y'):
        put(x,y,z,pal['frame'],**({'axis':axis} if pal['frame'].endswith('_log') else {}))
    # Preserve the tested lower room, stairs, deck, well rails and firing lintel.
    for p,s in source.items():
        if p[1] >= 9 or s[0] == 'minecraft:spruce_planks': continue
        name = s[0].removeprefix('minecraft:'); props = dict(s[1])
        if name == 'stripped_oak_log':
            name = pal['frame']
            if not name.endswith('_log'): props = {}
        elif name == 'white_terracotta': name = pal['wall']
        elif name in ('cobblestone','andesite'): name = pal['base']
        elif name == 'cobblestone_slab':
            name = ('sandstone' if region == 'desert' else 'stone_brick' if region == 'snowy' else 'mud_brick' if region == 'swamp' else 'cobblestone') + '_slab'
        elif name.startswith('oak_'): name = pal['wood'] + name[3:]
        elif name == 'green_wall_banner': name = pal['banner'] + '_wall_banner'
        put(*p,name,**props)
    # A solid-backed roof with a minimum Y=10 underside leaves the deck's
    # Y=6..8 circulation envelope clear and supports lanterns at Y=9.
    roof_bottom = {}
    for x,z in sorted(rect(0,0 if gatehouse else 1,w-1,d-1)):
        edge = min(x,w-1-x)
        if gatehouse: edge = min(x,abs(x-8),w-1-x)
        shape = pal['shape']
        if shape == 'flat': top = 11.5
        elif shape == 'steep': top = 11 + edge
        elif shape == 'hip': top = 11 + min(edge,z,d-1-z)*.5
        else: top = 11 + edge*.5
        bottom = int(top) - (2 if shape == 'steep' else 1)
        bottom = max(10,bottom)
        roof_bottom[x,z] = bottom
        for y in range(bottom,int(top)+1):
            put(x,y,z,pal['roof']+'_slab',type='bottom' if y==int(top) and top%1==0 and region!='snowy' else 'double',waterlogged=False)
        # Flat parapet and half-height upper courses sit on full backing.
        if shape == 'flat' and (x in (0,w-1) or z in (0 if gatehouse else 1,d-1)):
            put(x,12,z,'sandstone_slab',type='bottom',waterlogged=False)
    # Posts, crossbeams and closed gables connect the roof to the original frame.
    posts = {(x,z) for (x,y,z),s in source.items() if y==8 and s[0]=='minecraft:stripped_oak_log' and dict(s[1]).get('axis')=='y'}
    for x,z in posts:
        for y in range(9,roof_bottom[x,z]): timber(x,y,z)
    for z in (5,d-3):
        for x in range(1,w-1): timber(x,9,z,'x')
    for z in (1,d-2):
        for x in range(1,w-1):
            for y in range(9,roof_bottom[x,z]):
                if (x,z) in posts: timber(x,y,z)
                else: put(x,y,z,pal['wall'])
    for p,s in source.items():
        x,y,z = p
        if s[0] == 'minecraft:lantern' and y == 9:
            for cy in range(10,roof_bottom[x,z]): put(x,cy,z,'chain',axis='y',waterlogged=False)
            grid[p] = s
    if region == 'snowy':
        # Authored snow is confined to full roof surfaces; no snow in lanes.
        for x,z in roof_bottom:
            top = max(y for xx,y,zz in grid if xx==x and zz==z and grid[xx,y,zz]!=air)
            if dict(grid[x,top,z][1]).get('type') == 'double': put(x,top+1,z,'snow',layers=1)
    raised = region in ('ocean','jungle','swamp')
    dx,dy,dz = (1,1,2) if raised else (0,0,0)
    if raised:
        grid = {(x+dx,y+dy,z+dz):s for (x,y,z),s in grid.items()}
        w += 2; d += 3
        for x,z in rect(0,0,w-1,d-1):
            put(x,0,z,'stripped_spruce_log',axis='x') if region=='ocean' else put(x,0,z,pal['base'])
            put(x,1,z,pal['wood']+'_planks' if region!='jungle' else pal['base'])
            for y in range(2,24): grid.setdefault((x,y,z),air)
        ex = row['entrance'][0]+dx
        perimeter = (rect(0,0,w-1,d-1)-rect(1,1,w-2,d-2))-{(ex,0),(ex+1,0)}
        for x,z in sorted(perimeter):
            put(x,2,z,pal['wood']+'_fence',north=(x,z-1) in perimeter,south=(x,z+1) in perimeter,east=(x+1,z) in perimeter,west=(x-1,z) in perimeter,waterlogged=False)
        row['standing'] += [[row['entrance'][0]+i,1,z] for i in (0,1) for z in (-2,-1)]
    for key in ('anchor','entrance'):
        x,y,z = row[key]; row[key] = [x+dx,y+dy,z+dz]
    row['entrance'][2] = 0
    for key in ('initialDeckPositions','deckPositions','doors','stairs','standing','walkTargets','targets','rails','slits'):
        row[key] = [[x+dx,y+dy,z+dz] for x,y,z in row[key]]
    row['walkTargets'][-1] = [row['entrance'][0],1+dy,-3]
    if raised: row['rails'] += [[x,2,z] for x,z in sorted(perimeter)]
    door_cells = {tuple(p) for p in row['doors']}
    for x,y,z in row['standing']:
        assert grid.get((x,y-1,z),air)!=air, (region,'floor',x,y,z)
        if (x,y,z) not in door_cells:
            for h in range(3): assert grid[x,y+h,z]==air, (region,'lane',x,y+h,z)
    for x,y,z in row['stairs']:
        for h in (1,2): assert grid[x,y+h,z]==air, (region,'stair',x,y,z)
    for (x,y,z),s in grid.items():
        if s[0]=='minecraft:lantern': assert grid[x,y+1,z]!=air, (region,'lantern',x,y,z)
    height = max(y for (x,y,z),s in grid.items() if s!=air)+1
    grid = {p:s for p,s in grid.items() if p[1]<height}
    layout = row['id']
    row.pop('roofProfile')
    row.update(id=region+'-'+layout,region=region,layout=layout,
        template=f'vggpreview:watchtower/{region}/{layout}',size=[w,height,d],
        floorY=1+dy,shift=[dx,dy,dz],wetPlot=region in ('ocean','swamp'),roofShape=pal['shape'],
        details=[dict(pos=list(p),block=s[0]) for p,s in sorted(grid.items())
                 if s[0].endswith(('_door','_trapdoor','_wall_banner')) or s[0] in ('minecraft:lantern','minecraft:glass_pane','minecraft:snow')])
    return encode(grid,[w,height,d]),row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--regions',nargs='+',choices=REGIONS,default=list(REGIONS))
    parser.add_argument('--output',type=Path,default=ROOT/'build/generated/regional-watchtower-preview')
    parser.add_argument('--zip',type=Path,default=ROOT/'build/distributions/villager-guard-gunners-regional-watchtower-preview.zip')
    args = parser.parse_args()
    if len(set(args.regions))!=len(args.regions): parser.error('Regions must be unique')
    layouts = [build(r,g) for r in args.regions for g in (False,True)]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output.parent) as tmp:
        stage = Path(tmp)
        for blob,row in layouts:
            p = stage/f'data/vggpreview/structures/watchtower/{row["region"]}/{row["layout"]}.nbt'
            p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(blob)
        (stage/'data/vggpreview/watchtower_manifest.json').write_text(json.dumps([r for _,r in layouts],indent=2)+'\n')
        (stage/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Villager Guard Gunners: regional Watchtower previews'}})+'\n')
        if args.output.exists(): shutil.rmtree(args.output)
        shutil.copytree(stage,args.output)
    args.zip.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.zip,'w',zipfile.ZIP_DEFLATED) as archive:
        for p in sorted(args.output.rglob('*')):
            if p.is_file(): archive.writestr(zipfile.ZipInfo(str(p.relative_to(args.output))),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    print(args.zip)

if __name__=='__main__': main()
