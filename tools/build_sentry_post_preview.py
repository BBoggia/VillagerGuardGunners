#!/usr/bin/env python3
"""Build furnished Plains Sentry Post architecture previews, outside village pools."""
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from build_armory_preview import ROOT, rect, state, encode


def build(lodge=False):
    width,height,depth=(12,9,11) if lodge else (9,8,10)
    name='gateway_lodge' if lodge else 'roadside_shelter'
    air=state('air')
    grid={(x,y,z):air for x in range(width) for y in range(1,height) for z in range(depth)}
    def put(x,y,z,block,**props):
        assert 0<=x<width and 0<=y<height and 0<=z<depth,(x,y,z)
        grid[x,y,z]=state(block,**props)
    def post(x,z,top):
        for y in range(1,top+1): put(x,y,z,'stripped_oak_log',axis='y')
    def beam(x1,x2,y,z):
        for x in range(x1,x2+1): put(x,y,z,'stripped_oak_log',axis='x')
    def slab(x,z,material='cobblestone'):
        put(x,1,z,material+'_slab',type='bottom',waterlogged=False)
    def roof(profile,z1,z2):
        for x,y in enumerate(profile):
            for z in range(z1,z2+1):
                if y>4: put(x,y-1,z,'spruce_planks')
                if x in (len(profile)//2-1,len(profile)//2): put(x,y,z,'spruce_planks')
                else: put(x,y,z,'spruce_stairs',facing='east' if x<len(profile)//2 else 'west',half='bottom',shape='straight',waterlogged=False)
    floors=(rect(1,1,10,9)|rect(8,0,9,0)) if lodge else (rect(1,1,7,8)|rect(5,0,6,0))
    walls=(rect(1,4,6,9)-rect(2,5,5,8)) if lodge else (rect(1,8,7,8)|{(1,7),(7,7)})
    for x,z in floors:
        material='oak_planks' if (lodge and x<=6 and z>=4) or (not lodge and z>=5) else 'cobblestone'
        if (x,z) in walls: material='cobblestone'
        if (x+2*z)%13==0 and material=='cobblestone': material='andesite'
        put(x,0,z,material)
    for x,z in walls:
        for y in range(1,4): put(x,y,z,'cobblestone' if y==1 else 'white_terracotta')
    if lodge:
        profile=[4,5,6,7,7,6,5,4]
        for z in (4,9):
            for x in range(1,7):
                for y in range(4,profile[x]): put(x,y,z,'white_terracotta')
        for x,z in ((1,1),(6,1),(1,4),(6,4),(1,9),(6,9)): post(x,z,3 if z==1 else profile[x]-1)
        for z in (4,9): beam(1,6,3,z)
        beam(1,6,4,5)
        # Low veranda roof first, main gable second; full backing closes the joint.
        for x in range(8):
            put(x,4,1,'spruce_stairs',facing='south',half='bottom',shape='straight',waterlogged=False)
            put(x,4,2,'spruce_planks')
            for z in (2,3,4): put(x,5,z,'spruce_planks')
        roof(profile,3,10)
        for x,hinge in ((4,'left'),(5,'right')):
            for y,half in ((1,'lower'),(2,'upper')): put(x,y,4,'oak_door',facing='north',half=half,hinge=hinge,open=False,powered=False)
        for z in (6,7):
            put(1,2,z,'glass_pane',north=True,south=True,east=False,west=False,waterlogged=False)
            put(0,2,z,'oak_trapdoor',facing='west',half='bottom',open=True,powered=False,waterlogged=False)
            slab(2,z,'oak')
        for x in (3,4):
            put(x,2,9,'glass_pane',north=False,south=False,east=True,west=True,waterlogged=False)
            put(x,2,10,'oak_trapdoor',facing='south',half='bottom',open=True,powered=False,waterlogged=False)
        put(2,1,8,'crafting_table')
        for x in (2,3): slab(x,1)
        for z in range(4,8): slab(10,z)
        put(10,1,2,'cobblestone')
        lamps=[(5,3,1),(2,3,5),(10,2,2)]
        put(7,3,6,'green_wall_banner',facing='east')
        route=rect(8,0,9,8)|rect(4,2,9,3)|rect(4,4,5,8)|rect(3,5,5,7)|{(3,8)}
        bay=rect(2,2,3,3);anchor=[3,1,2];entry=[8,1,0];doors=[[4,1,4],[5,1,4]]
        targets=[[3,1,2],[4,1,6],[3,1,8]]
        walk=[[3,1,2],[4,1,6],[3,1,8],[8,1,-3]]
        traffic=[[8,1,-3],[8,1,7]]
        open_sky=rect(8,0,9,8)
    else:
        profile=[4,5,6,7,7,7,6,5,4]
        for x in range(1,8):
            for y in range(4,profile[x]): put(x,y,8,'white_terracotta')
        for x,z in ((1,2),(7,2),(1,8),(7,8)): post(x,z,4)
        for z in (2,7): beam(1,7,5,z)
        roof(profile,1,9)
        for x in (2,3): slab(x,2);slab(x,7,'oak')
        for z in range(3,7): slab(1,z)
        put(6,1,7,'crafting_table')
        for x in (2,6): put(x,2,8,'glass_pane',north=False,south=False,east=True,west=True,waterlogged=False)
        # A plain wood notice panel and one small side banner.
        put(4,2,8,'oak_planks')
        put(8,3,8,'green_wall_banner',facing='east')
        lamps=[(6,4,2),(4,4,7)]
        route=rect(5,0,6,6)|rect(2,5,6,6)
        bay=rect(2,3,4,4);anchor=[3,1,3];entry=[5,1,0];doors=[]
        targets=[[3,1,3],[4,1,6],[6,1,6]]
        walk=[[3,1,3],[4,1,6],[6,1,6],[5,1,-3]]
        traffic=[[5,1,-3],[5,1,6]]
        open_sky=set()
    for x,y,z in lamps: put(x,y,z,'lantern',hanging=not(lodge and x==10),waterlogged=False)
    door_cells={(p[0],p[2]) for p in doors}
    for x,z in route|bay:
        assert (x,z) in floors,('unsupported route',x,z)
        if (x,z) not in door_cells:
            for y in range(1,4): assert grid[x,y,z]==air,('blocked route',x,y,z,grid[x,y,z])
    assert not bay&route
    for x,y,z in lamps:
        support=grid[x,y-1 if lodge and x==10 else y+1,z]
        assert support!=air,('unsupported lamp',x,y,z)
    assert sum(s[0]=='minecraft:crafting_table' for s in grid.values())==1
    assert not any(s[0] in {'minecraft:jigsaw','minecraft:structure_block','minecraft:chest','minecraft:barrel','villagerguardgunners:armory'} or s[0].endswith('_bed') for s in grid.values())
    for x,z in open_sky: assert all(grid[x,y,z]==air for y in range(1,height))
    manifest=dict(id=name,template='vggpreview:sentry_post/plains/'+name,size=[width,height,depth],
        anchor=anchor,entrance=entry,doors=doors,targets=targets,walkTargets=walk,traffic=traffic,
        standing=[[x,1,z] for x,z in sorted(route|bay)],openSky=[[x,1,z] for x,z in sorted(open_sky)],
        lanterns=[list(p) for p in lamps],scope='architecture only; no garrison or village generation')
    return encode(grid,[width,height,depth]),manifest


def main():
    layouts=[build(),build(True)]
    output=ROOT/'build/generated/sentry-post-preview'
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as tmp:
        staged=Path(tmp)
        for blob,row in layouts:
            target=staged/f'data/vggpreview/structures/sentry_post/plains/{row["id"]}.nbt'
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(blob)
        (staged/'data/vggpreview/sentry_post_manifest.json').write_text(json.dumps([m for _,m in layouts],indent=2)+'\n')
        (staged/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Villager Guard Gunners: Plains Sentry Post previews'}})+'\n')
        if output.exists(): shutil.rmtree(output)
        shutil.copytree(staged,output)
    archive=ROOT/'build/distributions/villager-guard-gunners-sentry-post-preview.zip'
    archive.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(output.rglob('*')):
            if p.is_file(): z.writestr(zipfile.ZipInfo(str(p.relative_to(output))),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    print(archive)


if __name__=='__main__': main()
