#!/usr/bin/env python3
"""Build fourteen regional Sentry Post previews, separate from village generation."""
import argparse
import gzip
import io
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from build_armory_preview import ROOT, rect, state, encode
from build_sentry_post_preview import build as plains
from create_flat_sandbox import read_payload

REGIONS={
 'desert':dict(wood='acacia',frame='cut_sandstone',wall='smooth_sandstone',base='sandstone',roof='sandstone',banner='yellow',shape='flat'),
 'savanna':dict(wood='acacia',frame='stripped_acacia_log',wall='terracotta',base='cobblestone',roof='acacia',banner='orange',shape='hip'),
 'taiga':dict(wood='spruce',frame='stripped_spruce_log',wall='spruce_planks',base='cobblestone',roof='spruce',banner='green',shape='steep'),
 'snowy':dict(wood='spruce',frame='stripped_spruce_log',wall='white_terracotta',base='stone_bricks',roof='spruce',banner='blue',shape='steep'),
 'ocean':dict(wood='oak',frame='stripped_spruce_log',wall='oak_planks',base='oak_planks',roof='spruce',banner='cyan',shape='gable'),
 'jungle':dict(wood='jungle',frame='stripped_jungle_log',wall='jungle_planks',base='mossy_cobblestone',roof='jungle',banner='green',shape='hip'),
 'swamp':dict(wood='dark_oak',frame='dark_oak_log',wall='mud_bricks',base='mud_bricks',roof='dark_oak',banner='brown',shape='gable'),
}


def decode(blob):
    stream=io.BytesIO(gzip.decompress(blob));assert stream.read(3)==b'\x0a\x00\x00'
    tag=read_payload(stream,10);palette=tag['palette'][1][1]
    def block(i):
        b=palette[i];return state(b['Name'][1],**{k:v[1] for k,v in b.get('Properties',(10,{}))[1].items()})
    return {tuple(b['pos'][1][1]):block(b['state'][1]) for b in tag['blocks'][1][1]}


def build(region,lodge=False):
    pal=REGIONS[region];blob,row=plains(lodge);source=decode(blob)
    w,_,d=row['size'];height=11
    air=state('air');grid={(x,y,z):air for x in range(w) for y in range(1,height) for z in range(d)}
    def put(x,y,z,block,**props):grid[x,y,z]=state(block,**props)
    # Reuse the tested floor reservations and low furniture, then author regional roofs.
    for p,s in source.items():
        x,y,z=p;name=s[0].removeprefix('minecraft:');props=dict(s[1])
        if y>=4 and name!='lantern': continue
        if name=='stripped_oak_log':
            name=pal['frame']
            if not name.endswith('_log'):props={}
        elif name=='white_terracotta':name=pal['wall']
        elif name in ('cobblestone','andesite'):name=pal['base']
        elif name=='cobblestone_slab':name=('sandstone' if region=='desert' else 'stone_brick' if region=='snowy' else 'mud_brick' if region=='swamp' else 'cobblestone')+'_slab'
        elif name.startswith('oak_'):name=pal['wood']+name[3:]
        elif name=='green_wall_banner':name=pal['banner']+'_wall_banner'
        put(x,y,z,name,**props)
    def timber(x,y,z,axis='y'):put(x,y,z,pal['frame'],**({'axis':axis} if pal['frame'].endswith('_log') else {}))
    def roof(bounds,shape):
        x1,z1,x2,z2=bounds
        for x,z in sorted(rect(*bounds)):
            edge=min(x-x1,x2-x)
            base=5 if lodge else 6
            if shape=='hip':top=base+min(edge,z-z1,z2-z)*.5
            elif shape=='flat':top=base+.5
            elif shape=='steep':top=base+edge
            else:top=base+edge*.5
            # Union occupied half blocks; solid backing seals all raised courses.
            lo=base-1 if shape=='flat' else max(base-1,int(top)-(2 if shape=='steep' else 1))
            for y in range(lo,int(top)):
                put(x,y,z,pal['roof']+'_slab',type='double',waterlogged=False)
            if top%1:put(x,int(top),z,pal['roof']+'_slab',type='bottom',waterlogged=False)
            if shape=='flat' and (x in (x1,x2) or z in (z1,z2)):
                put(x,base,z,'sandstone_slab',type='double',waterlogged=False)
        return bounds
    # Lower veranda, then main roof. The roof union never removes a lower support.
    if lodge:
        roof((0,1,7,4),'flat' if region=='desert' else 'hip')
        roof((0,3,7,10),pal['shape'])
        posts=((1,1),(6,1),(1,4),(6,4),(1,9),(6,9))
        walls=rect(1,4,6,9)-rect(2,5,5,8)
    else:
        roof((0,1,8,9),pal['shape'])
        posts=((1,2),(7,2),(1,8),(7,8))
        walls=rect(1,8,7,8)|{(1,7),(7,7)}
    # Close wall tops to their roof underside; preserve doors/windows below Y=4.
    for x,z in walls|set(posts):
        y=4
        while grid.get((x,y,z),air)==air:
            if (x,z) in posts:timber(x,y,z)
            else:put(x,y,z,pal['wall'])
            y+=1
            assert y<height,(region,'missing roof',x,z)
    if not lodge:
        for z in (2,7):
            for x in range(1,8):
                if grid[x,5,z]==air:timber(x,5,z,'x')
    else:
        for x in range(1,7):
            if grid[x,4,5]==air:timber(x,4,5,'x')
    # Wind screens remain outside the reserved watch and walking cells.
    if region in ('taiga','snowy') and not lodge:
        for x in (1,7):
            for z in range(3,8):
                for y in range(1,4):put(x,y,z,pal['base'] if y==1 else pal['wall'])
        if region=='taiga':
            for x in (1,7):put(x,2,5,'glass_pane',north=True,south=True,east=False,west=False,waterlogged=False)
    if region=='snowy':
        # Cover a broader arrival landing without placing posts in the walking lane.
        if lodge:
            for x,z in rect(7,1,10,4):put(x,4,z,'spruce_slab',type='double',waterlogged=False)
            for z in (1,4):
                for y in range(1,4):timber(10,y,z)
            row['openSky']=[p for p in row['openSky'] if p[2]==0 or p[2]>4]
        else:
            for x,z in rect(5,0,7,1):put(x,4,z,'spruce_slab',type='double',waterlogged=False)
        # A dusting on full roof surfaces; no authored snow in circulation cells.
        for x in range(w):
            for z in range(d):
                top=max((y for xx,y,zz in grid if xx==x and zz==z and grid[xx,y,zz]!=air),default=-1)
                if top>=4 and dict(grid[x,top,z][1]).get('type')=='double':put(x,top+1,z,'snow',layers=1)
    # Raised regional platforms have a continuous outer fence with only the entrance gap.
    raised=region in ('ocean','jungle','swamp');dx,dy,dz=(1,1,2) if raised else (0,0,0)
    rails=[]
    if raised:
        grid={(x+dx,y+dy,z+dz):s for (x,y,z),s in grid.items()}
        w+=2;d+=3;height+=1
        for x in range(w):
            for z in range(d):
                for y in range(2,height):grid.setdefault((x,y,z),air)
                grid[x,1,z]=state(pal['wood']+'_planks' if region!='jungle' else 'mossy_cobblestone')
                grid[x,0,z]=state('stripped_spruce_log',axis='x') if region=='ocean' else state(pal['base'])
        ex=row['entrance'][0]+dx
        perimeter=(rect(0,0,w-1,d-1)-rect(1,1,w-2,d-2))-{(ex,0),(ex+1,0)}
        for x,z in sorted(perimeter):
            grid[x,2,z]=state(pal['wood']+'_fence',north=(x,z-1) in perimeter,south=(x,z+1) in perimeter,west=(x-1,z) in perimeter,east=(x+1,z) in perimeter,waterlogged=False)
            rails.append([x,2,z])
        row['standing'] += [[row['entrance'][0]+i,1,z] for i in (0,1) for z in (-2,-1)]
    for key in ('anchor','entrance'):
        row[key]=[row[key][0]+dx,row[key][1]+dy,row[key][2]+dz]
    row['entrance'][2]=0
    for key in ('doors','targets','walkTargets','traffic','standing','openSky','lanterns'):
        row[key]=[[x+dx,y+dy,z+dz] for x,y,z in row[key]]
    # External waypoints stay north of the new platform boundary.
    row['walkTargets'][-1]=[row['entrance'][0],1+dy,-3]
    row['traffic'][0]=[row['entrance'][0],1+dy,-3]
    door_cells={tuple(p) for p in row['doors']}
    for p in row['standing']:
        x,y,z=p
        assert grid.get((x,y-1,z),air)!=air,(region,'floor',p)
        if tuple(p) not in door_cells:
            for h in range(3):assert grid[x,y+h,z]==air,(region,'route',p,grid[x,y+h,z])
    for x,y,z in row['lanterns']:
        hanging=dict(grid[x,y,z][1])['hanging']=='true'
        assert grid[x,y+1 if hanging else y-1,z]!=air,(region,'light support',x,y,z)
    height=max(y for (x,y,z),s in grid.items() if s!=air)+1
    grid={p:s for p,s in grid.items() if p[1]<height}
    assert all(0<=x<w and 0<=y<height and 0<=z<d for x,y,z in grid)
    layout='gateway_lodge' if lodge else 'roadside_shelter'
    row.update(id=region+'-'+layout,region=region,layout=layout,template=f'vggpreview:sentry_post/{region}/{layout}',
        size=[w,height,d],floorY=1+dy,shift=[dx,dy,dz],rails=rails,wetPlot=region in ('ocean','swamp'),
        details=[dict(pos=list(p),block=s[0]) for p,s in sorted(grid.items()) if s[0].endswith(('_door','_trapdoor','_wall_banner','_fence')) or s[0] in ('minecraft:lantern','minecraft:glass_pane','minecraft:snow')])
    return encode(grid,[w,height,d]),row


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--regions',nargs='+',choices=REGIONS,default=list(REGIONS))
    parser.add_argument('--output',type=Path,default=ROOT/'build/generated/regional-sentry-post-preview')
    parser.add_argument('--zip',type=Path,default=ROOT/'build/distributions/villager-guard-gunners-regional-sentry-post-preview.zip')
    args=parser.parse_args()
    if len(set(args.regions))!=len(args.regions):parser.error('Regions must be unique')
    layouts=[build(r,l) for r in args.regions for l in (False,True)]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output.parent) as tmp:
        stage=Path(tmp)
        for blob,row in layouts:
            p=stage/f'data/vggpreview/structures/sentry_post/{row["region"]}/{row["layout"]}.nbt';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(blob)
        (stage/'data/vggpreview/sentry_post_manifest.json').write_text(json.dumps([r for _,r in layouts],indent=2)+'\n')
        (stage/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Villager Guard Gunners: regional Sentry Post previews'}})+'\n')
        if args.output.exists():shutil.rmtree(args.output)
        shutil.copytree(stage,args.output)
    args.zip.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.zip,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(args.output.rglob('*')):
            if p.is_file():z.writestr(zipfile.ZipInfo(str(p.relative_to(args.output))),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    print(args.zip)

if __name__=='__main__':main()
