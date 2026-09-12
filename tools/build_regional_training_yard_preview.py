#!/usr/bin/env python3
"""Build seven regional pairs of manual-placement Training Yard prototypes."""
import argparse
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from build_armory_preview import ROOT, rect, state, encode
from build_training_yard_preview import build as plains
from build_regional_sentry_post_preview import REGIONS, decode


def build(region, courtyard=False):
    pal=REGIONS[region];blob,row=plains(courtyard);source=decode(blob)
    w,_,d=row['size'];air=state('air')
    grid={(x,y,z):air for x in range(w) for y in range(1,16) for z in range(d)}
    def put(x,y,z,name,**props):grid[x,y,z]=state(name,**props)
    def timber(x,y,z,axis='y'):
        put(x,y,z,pal['frame'],**({'axis':axis} if pal['frame'].endswith('_log') else {}))
    def masonry(name):
        if region=='desert':return 'cut_sandstone' if name=='stone_bricks' else 'sandstone'
        if region=='savanna':return 'terracotta' if name!='stone_bricks' else 'cut_red_sandstone'
        if region=='swamp':return 'mud_bricks'
        if region=='snowy':return 'stone_bricks'
        if region=='jungle':return 'mossy_cobblestone' if name=='cobblestone' else 'stone_bricks'
        return name
    def slab(name):
        return ('sandstone' if region=='desert' else 'red_sandstone' if region=='savanna'
                else 'mud_brick' if region=='swamp' else 'stone_brick' if region=='snowy'
                else 'mossy_cobblestone' if region=='jungle' else name.removesuffix('_slab'))+'_slab'

    source_roof={(x,z) for x,_,z in row['roof']}
    # Keep the tested lower layout. Replace roofs, upper frames and hanging chains.
    for (x,y,z),s in source.items():
        name=s[0].removeprefix('minecraft:');props=dict(s[1])
        if name.startswith('spruce_') or name=='chain':continue
        if y>=4 and (x,z) in source_roof and name in ('stripped_oak_log','white_terracotta'):continue
        if name=='stripped_oak_log':
            name=pal['frame']
            if not name.endswith('_log'):props={}
        elif name=='white_terracotta':name=pal['wall']
        elif name in ('cobblestone','andesite','stone_bricks'):
            name=pal['wood']+'_planks' if y==0 and region in ('ocean','swamp') else masonry(name)
        elif name in ('cobblestone_slab','stone_brick_slab'):name=slab(name)
        elif name in ('packed_mud','coarse_dirt') and region=='ocean':name='oak_planks'
        elif name=='packed_mud' and region=='desert':name='smooth_sandstone'
        elif name=='coarse_dirt' and region=='desert':name='cut_sandstone'
        elif name.startswith('oak_'):name=pal['wood']+name[3:]
        elif name=='green_wall_banner':name=pal['banner']+'_wall_banner'
        put(x,y,z,name,**props)

    # Author regional roof silhouettes, closing the union at the pavilion junction.
    roof={}
    def cover(bounds,axis,shape):
        x1,z1,x2,z2=bounds
        for x,z in sorted(rect(*bounds)):
            across=min(z-z1,z2-z) if axis=='z' else min(x-x1,x2-x)
            if shape=='flat':top=5
            elif shape=='hip':top=5+min(x-x1,x2-x,z-z1,z2-z)//2
            elif shape=='steep':top=5+across
            else:top=5+(across+1)//2
            roof[x,z]=max(roof.get((x,z),0),top)
    cover((0,1 if courtyard else 0,w-1,7 if courtyard else 6),'z',pal['shape'])
    if courtyard:cover((0,5,6,14),'x',pal['shape'])
    posts={(x,z) for x in ((1,6,13,16) if courtyard else (1,9,12)) for z in ((2,6) if courtyard else (1,5))}
    shell=rect(1,5,5,13)-rect(2,6,4,12) if courtyard else set()
    if courtyard:posts|={(x,z) for x in (1,5) for z in (5,10,13)}
    if region in ('taiga','snowy'):
        # The service passage gets a supported lean-to; the courtyard adds a rear walk.
        for x,z in rect(13 if courtyard else 9,6,w-1,18 if courtyard else 15):roof[x,z]=max(roof.get((x,z),0),6)
        posts|={(w-1,z) for z in ((12,18) if courtyard else (8,15))}
        if courtyard:
            for x,z in rect(2,16,16,18):roof[x,z]=max(roof.get((x,z),0),6)
            posts.add((2,18))
        # Wind screens never cross the front cross-route or the south firing face.
        screens=rect(2,2,12,2) if courtyard else rect(4,1,8,1)
        screens|=rect(w-1,3 if courtyard else 2,w-1,5 if courtyard else 4)
        if not courtyard:screens|=rect(1,2,1,5)
        if region=='snowy':
            for x,z in screens:
                if grid.get((x,0,z),air)==air:put(x,0,z,pal['base'])
                for y in range(1,4):
                    # Keep the frame visually continuous where a screen meets a post.
                    if (x,z) not in posts:put(x,y,z,pal['base'] if y==1 else pal['wall'])
            if courtyard:
                for x in (4,9):put(x,2,2,'glass_pane',north=False,south=False,east=True,west=True,waterlogged=False)
            else:
                for x in (5,7):put(x,2,1,'glass_pane',north=False,south=False,east=True,west=True,waterlogged=False)
        # The larger shade roof still leaves the target court open to weather.
    for (x,z),top in sorted(roof.items()):
        nearby=[roof.get(p,top) for p in ((x-1,z),(x+1,z),(x,z-1),(x,z+1))]
        # Never lower backing onto the lanterns at Y=4.
        for y in range(max(5,min(nearby)-1),top+1):put(x,y,z,pal['roof']+'_slab',type='double',waterlogged=False)
        if region=='desert':
            boundary=any(p not in roof for p in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)))
            if boundary:put(x,top+1,z,'sandstone_slab',type='bottom',waterlogged=False)
        elif region=='snowy':put(x,top+1,z,'snow',layers=1)
        else:
            higher=[(roof.get((x+dx,z+dz),top),facing) for dx,dz,facing in ((0,-1,'north'),(0,1,'south'),(-1,0,'west'),(1,0,'east'))]
            peak,facing=max(higher)
            if peak>top:put(x,top,z,pal['roof']+'_stairs',facing=facing,half='bottom',shape='straight',waterlogged=False)
            else:put(x,top,z,pal['roof']+'_slab',type='bottom',waterlogged=False)
    # Continuous eaves, posts and closed pavilion gables join roofs to the ground.
    for x,z in posts:
        if grid.get((x,0,z),air)==air:put(x,0,z,pal['base'])
        for y in range(1,roof[x,z]):timber(x,y,z)
    for z in ((2,6) if courtyard else (1,5)):
        for x in range(1,w):timber(x,4,z,'x')
    for x,z in shell:
        for y in range(3,roof[x,z]):
            if grid.get((x,y,z),air)==air:put(x,y,z,pal['wall'])
    # Lanterns hang from solid roof courses or the retained rear bracket.
    for (x,y,z),s in source.items():
        if s[0]!='minecraft:lantern':continue
        if (x,z) in roof:
            for cy in range(y+1,roof[x,z]):
                if grid[x,cy,z]==air:put(x,cy,z,'chain',axis='y',waterlogged=False)
        grid[x,y,z]=s

    if region=='jungle':
        # Small bamboo insets complement the jungle timber without changing its frame.
        if courtyard:
            for x in (3,4):put(x,3,5,'bamboo_mosaic')
        else:put(1,3,3,'bamboo_mosaic')

    raised=region in ('ocean','jungle','swamp');dx,dy,dz=(1,1,2) if raised else (0,0,0)
    rails=[]
    if raised:
        grid={(x+dx,y+dy,z+dz):s for (x,y,z),s in grid.items()};w+=2;d+=3
        for x,z in rect(0,0,w-1,d-1):
            put(x,0,z,'stripped_spruce_log',axis='x') if region=='ocean' else put(x,0,z,pal['base'])
            grid.setdefault((x,1,z),state(pal['wood']+'_planks' if region!='jungle' else pal['base']))
            for y in range(2,17):grid.setdefault((x,y,z),air)
        ex=row['entrance'][0]+dx
        perimeter=(rect(0,0,w-1,d-1)-rect(1,1,w-2,d-2))-{(ex,0),(ex+1,0)}
        for x,z in sorted(perimeter):
            put(x,2,z,pal['wood']+'_fence',north=(x,z-1) in perimeter,south=(x,z+1) in perimeter,east=(x+1,z) in perimeter,west=(x-1,z) in perimeter,waterlogged=False)
            rails.append([x,2,z])
        for key in ('standing','circulation'):row[key]+=[[row['entrance'][0]+i,1,z] for i in (0,1) for z in (-2,-1)]
    for key in ('anchor','entrance'):
        x,y,z=row[key];row[key]=[x+dx,y+dy,z+dz]
    row['entrance'][2]=0
    for key in ('initialPositions','dutyPositions','targetBlocks','doors','standing','circulation','field','walkTargets','traffic','lightSamples'):
        row[key]=[[x+dx,y+dy,z+dz] for x,y,z in row[key]]
    row['walkTargets'][-1]=row['traffic'][0]=row['traffic'][-1]=[row['entrance'][0],1+dy,-3]
    # No authored snow, partial support, or furnishings in reserved walking cells.
    doors={tuple(p) for p in row['doors']}
    for x,y,z in row['standing']:
        assert grid.get((x,y-1,z),air)!=air,(region,'floor',x,y,z)
        if (x,y,z) not in doors:
            for h in range(3):assert grid[x,y+h,z]==air,(region,'clearance',x,y+h,z,grid[x,y+h,z])
    for p,s in grid.items():
        if s[0]=='minecraft:lantern':assert grid.get((p[0],p[1]+1,p[2]),air)!=air,(region,'lamp',p)
    height=max(y for (x,y,z),s in grid.items() if s!=air)+1
    grid={p:s for p,s in grid.items() if p[1]<height}
    assert all(0<=x<w and 0<=y<height and 0<=z<d for x,y,z in grid)
    layout=row['id']
    row.update(id=region+'-'+layout,region=region,layout=layout,template=f'vggpreview:training_yard/{region}/{layout}',
        size=[w,height,d],floorY=1+dy,shift=[dx,dy,dz],rails=rails,wetPlot=region in ('ocean','swamp'),
        roof=[[x+dx,y+dy,z+dz] for (x,z),y in sorted(roof.items())],roofShape=pal['shape'],
        details=[dict(pos=list(p),block=s[0]) for p,s in sorted(grid.items()) if s[0].endswith(('_door','_trapdoor','_wall_banner')) or s[0] in ('minecraft:lantern','minecraft:glass_pane','minecraft:target','minecraft:snow')])
    return encode(grid,[w,height,d]),row


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--regions',nargs='+',choices=REGIONS,default=list(REGIONS))
    parser.add_argument('--output',type=Path,default=ROOT/'build/generated/regional-training-yard-preview')
    parser.add_argument('--zip',type=Path,default=ROOT/'build/distributions/villager-guard-gunners-regional-training-yard-preview.zip')
    args=parser.parse_args()
    if len(set(args.regions))!=len(args.regions):parser.error('Regions must be unique')
    rows=[build(r,c) for r in args.regions for c in (False,True)]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output.parent) as tmp:
        stage=Path(tmp)
        for blob,row in rows:
            p=stage/f'data/vggpreview/structures/training_yard/{row["region"]}/{row["layout"]}.nbt'
            p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(blob)
        (stage/'data/vggpreview/training_yard_manifest.json').write_text(json.dumps([r for _,r in rows],indent=2)+'\n')
        (stage/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Villager Guard Gunners: regional Training Yard previews'}})+'\n')
        if args.output.exists():shutil.rmtree(args.output)
        shutil.copytree(stage,args.output)
    args.zip.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.zip,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(args.output.rglob('*')):
            if p.is_file():z.writestr(zipfile.ZipInfo(str(p.relative_to(args.output))),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    print(args.zip)

if __name__=='__main__':main()
