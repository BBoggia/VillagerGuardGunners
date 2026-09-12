#!/usr/bin/env python3
"""Build the isolated furnished compact and supply-yard Plains Armory previews."""
import gzip
import io
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from create_flat_sandbox import read_payload, write_payload

ROOT = Path(__file__).resolve().parents[1]


def rect(x1, z1, x2, z2):
    return {(x, z) for x in range(x1, x2 + 1) for z in range(z1, z2 + 1)}


def state(name, **properties):
    return (name if ':' in name else 'minecraft:' + name,
            tuple(sorted((k, str(v).lower()) for k, v in properties.items())))


def build():
    width, height, depth = 13, 11, 13
    air = state('air')
    grid = {(x, y, z): air for x in range(width) for y in range(1, height) for z in range(depth)}

    def put(x, y, z, name, **props):
        if not (0 <= x < width and 0 <= y < height and 0 <= z < depth):
            raise ValueError(('outside envelope', x, y, z))
        grid[x, y, z] = state(name, **props)

    def post(x, z, lo, hi):
        for y in range(lo, hi + 1): put(x, y, z, 'stripped_oak_log', axis='y')

    floors = rect(1,3,11,11) | rect(4,1,9,2) | rect(5,0,6,0)
    walls = rect(1,3,11,11) - rect(2,4,10,10)
    profile = [4,5,6,7,8,9,9,9,8,7,6,5,4]
    for x,z in floors:
        material = 'cobblestone' if (x,z) in walls else 'oak_planks'
        if (x,z) in {(1,7),(11,10)}: material = 'mossy_cobblestone'
        if x in (5,6) and 0 <= z <= 4: material = 'andesite' if (x+z)%3 == 0 else 'cobblestone'
        if (x,z) in {(9,4),(10,4),(10,5)}: material = 'spruce_planks'
        put(x,0,z,material)
    for x,z in walls:
        for y in range(1, profile[x]):
            put(x,y,z,'cobblestone' if y == 1 else 'white_terracotta')
    for x,z in [(1,3),(11,3),(1,11),(11,11),(1,6),(11,6),(1,9),(11,9),(4,3),(7,3)]:
        post(x,z,1,profile[x]-1)
    # A small repaired side panel, clear of windows and load-bearing posts.
    put(11,2,4,'oak_planks');put(11,3,4,'oak_planks')
    for z in (3,11):
        for x in range(1,12): put(x,3,z,'stripped_oak_log',axis='x')
        post(6,z,4,8)
    # Lower porch first: only actual intersecting main-roof blocks replace it.
    for x in range(3,11):
        put(x,5,1,'spruce_stairs',facing='south',half='bottom',shape='straight',waterlogged=False)
        put(x,5,2,'spruce_planks')
        put(x,6,2,'spruce_stairs',facing='south',half='bottom',shape='straight',waterlogged=False)
    for x in (4,9): post(x,1,1,4)
    for x in range(4,10): put(x,5,1,'stripped_oak_log',axis='x')
    for x,y in enumerate(profile):
        for z in range(2,13):
            if y-1 >= 4: put(x,y-1,z,'spruce_planks')
            if x in (5,7): put(x,y,z,'spruce_slab',type='bottom',waterlogged=False)
            elif x == 6: put(x,y,z,'spruce_planks') # Closes the underside of the raised ridge cap.
            else: put(x,y,z,'spruce_stairs',facing='east' if x < 6 else 'west',half='bottom',shape='straight',waterlogged=False)
    for z in (6,9):
        # Keep beam ends below the sloping roof; short brackets join the wall posts.
        for x in range(2,11): put(x,5,z,'stripped_oak_log',axis='x')
        for x,facing in [(2,'west'),(10,'east')]:
            put(x,4,z,'oak_stairs',facing=facing,half='top',shape='straight',waterlogged=False)
    for x,hinge in [(5,'left'),(6,'right')]:
        for y,half in [(1,'lower'),(2,'upper')]:
            put(x,y,3,'oak_door',facing='north',half=half,hinge=hinge,open=False,powered=False)
    for x,zs,shutter_x,facing in [(1,(4,5),0,'west'),(11,(7,8),12,'east')]:
        for z in zs:
            put(x,2,z,'glass_pane',north=True,south=True,east=False,west=False,waterlogged=False)
            put(shutter_x,2,z,'oak_trapdoor',facing=facing,half='bottom',open=True,powered=False,waterlogged=False)
    put(2,1,4,'oak_slab',type='bottom',waterlogged=False)
    put(10,1,4,'crafting_table');put(10,1,5,'spruce_slab',type='top',waterlogged=False)
    for z in range(6,10):
        put(2,1,z,'stripped_oak_log' if z in (6,9) else 'oak_planks', **({'axis':'y'} if z in (6,9) else {}))
        put(2,2,z,'spruce_slab',type='bottom',waterlogged=False)
    put(9,1,10,'villagerguardgunners:armory',facing='north')
    lanterns = [(7,4,1),(6,4,6),(9,4,9)]
    for x,y,z in lanterns: put(x,y,z,'lantern',hanging=True,waterlogged=False)
    put(10,3,2,'green_wall_banner',facing='north')

    route = rect(5,0,6,9)|rect(5,7,10,9)|rect(5,7,7,10)|rect(4,4,6,6)|rect(7,4,9,6)
    service = rect(8,7,10,9)
    for x,z in route | service | {(8,2)}:
        assert grid.get((x,0,z),air) != air
        for y in range(1,4):
            if (x,z) in {(5,3),(6,3)}: continue
            assert grid[x,y,z] == air, ('route obstructed',x,y,z,grid[x,y,z])
    for x,y,z in lanterns:
        assert grid[x,y+1,z][0] == 'minecraft:stripped_oak_log'
    assert sum(s[0]=='villagerguardgunners:armory' for s in grid.values()) == 1
    assert sum(s[0]=='minecraft:crafting_table' for s in grid.values()) == 1
    assert not any(s[0] in {'minecraft:chest','minecraft:barrel','minecraft:jigsaw','minecraft:structure_block'} for s in grid.values())
    manifest=dict(id='compact',template='vggpreview:armory/plains/compact',size=[width,height,depth],
                  anchor=[9,1,10],sentry=[8,1,2],entrance=[5,1,0],
                  doors=[[5,1,3],[6,1,3]],
                  targets=[[5,1,5],[8,1,5],[9,1,9],[8,1,10]],
                  walkTargets=[[[7,1,5],[8,1,5]],[[8,1,9],[9,1,9]]],
                  standing=[[x,1,z] for x,z in sorted(route|service|{(8,2)})],
                  openSky=[],lanterns=[list(p) for p in lanterns],roofProfile=profile,
                  scope='architecture preview; no village entries, generated stock, or garrison')
    return encode(grid,[width,height,depth]),manifest


def build_supply_yard():
    # Native preview dimensions; this does not alter the village-layout compiler limits.
    width,height,depth=16,11,17
    air=state('air')
    grid={(x,y,z):air for x in range(width) for y in range(1,height) for z in range(depth)}

    def put(x,y,z,name,**props):
        if not (0<=x<width and 0<=y<height and 0<=z<depth):
            raise ValueError(('outside envelope',x,y,z))
        grid[x,y,z]=state(name,**props)

    def post(x,z,hi):
        for y in range(1,hi+1): put(x,y,z,'stripped_oak_log',axis='y')

    walls=rect(1,8,14,15)-rect(2,9,13,14)
    floors=rect(1,8,14,15)|rect(1,3,5,7)|rect(6,2,13,7)|rect(7,0,8,1)|{(14,6)}
    profile=[5,6,7,8,9,9,8,7,6,5]
    # Surface heights in half-block units, descending smoothly toward the court.
    workshop=[13,13,12,11,10,9,9]
    for x,z in floors:
        material='oak_planks'
        if (x,z) in walls or (6<=x<=13 and z<=7) or z<2: material='cobblestone'
        if (x,z) in {(1,5),(1,14),(14,14),(13,5)}: material='mossy_cobblestone'
        if x in (7,8) and 7<=z<=9 and (x+z)%3==0: material='andesite'
        if (x,z) in {(2,3),(2,4),(3,4)}: material='spruce_planks'
        put(x,0,z,material)
    for x,z in walls:
        for y in range(1,profile[z-7]): put(x,y,z,'cobblestone' if y==1 else 'white_terracotta')
    for x,z in [(1,8),(14,8),(1,15),(14,15),(1,11),(14,11),(1,14),(14,14),(6,8),(9,8),(6,15),(10,15)]:
        post(x,z,profile[z-7]-1)
    for x in (1,14):
        for z in range(8,16): put(x,3,z,'stripped_oak_log',axis='z')
        post(x,12,8)
    for z in (8,15):
        for x in range(1,15): put(x,3,z,'stripped_oak_log',axis='x')
    put(6,2,15,'oak_planks');put(7,2,15,'oak_planks')
    # Workshop wall and open frontage; no posts across the two-row rear connection.
    for z in range(3,8):
        for y in range(1,6): put(1,y,z,'cobblestone' if y==1 else 'white_terracotta')
    for z in (3,5,7): post(1,z,5)
    for z in (3,5): post(5,z,3)
    post(14,6,3)
    # Lower workshop, then frontage, then main roof: precedence applies per occupied block.
    for x,top in enumerate(workshop):
        y=(top-1)//2
        for z in range(2,9):
            if y-1>=4: put(x,y-1,z,'spruce_planks')
            if top%2: put(x,y,z,'spruce_slab',type='bottom',waterlogged=False)
            else: put(x,y,z,'spruce_planks')
    for x in range(5,15):
        put(x,4,6,'spruce_stairs',facing='south',half='bottom',shape='straight',waterlogged=False)
        put(x,4,7,'spruce_planks')
        put(x,5,7,'spruce_stairs',facing='south',half='bottom',shape='straight',waterlogged=False)
    for z,y in enumerate(profile,7):
        for x in range(16):
            put(x,y-1,z,'spruce_planks')
            if z in (11,12): put(x,y,z,'spruce_slab',type='bottom',waterlogged=False)
            else: put(x,y,z,'spruce_stairs',facing='south' if z<11 else 'north',half='bottom',shape='straight',waterlogged=False)
    # Wall ties stay at least three blocks above standing floors. Workshop posts
    # meet the roof directly; raising them into its surface would expose log ends.
    for z in (11,14):
        for x in range(1,15): put(x,5,z,'stripped_oak_log',axis='x')
    for x,hinge in [(7,'left'),(8,'right')]:
        for y,half in [(1,'lower'),(2,'upper')]:
            put(x,y,8,'oak_door',facing='north',half=half,hinge=hinge,open=False,powered=False)
    for x,zs,sx,facing in [(1,(10,11),0,'west'),(14,(11,12),15,'east')]:
        for z in zs:
            put(x,2,z,'glass_pane',north=True,south=True,east=False,west=False,waterlogged=False)
            put(sx,2,z,'oak_trapdoor',facing=facing,half='bottom',open=True,powered=False,waterlogged=False)
    put(2,1,3,'stripped_oak_log',axis='y')
    put(2,1,4,'crafting_table')
    put(3,1,4,'spruce_slab',type='top',waterlogged=False)
    for z in (10,11): put(2,1,z,'spruce_slab',type='top',waterlogged=False)
    for x in (3,4): put(x,1,9,'oak_slab',type='bottom',waterlogged=False)
    for x in range(2,6):
        put(x,1,14,'stripped_oak_log' if x in (2,5) else 'oak_planks',**({'axis':'y'} if x in (2,5) else {}))
        put(x,2,14,'spruce_slab',type='bottom',waterlogged=False)
    put(10,1,14,'villagerguardgunners:armory',facing='north')
    for z in (4,5): put(13,1,z,'cobblestone_slab',type='bottom',waterlogged=False)
    put(13,1,3,'cobblestone')
    lanterns=[(3,4,5),(10,4,7),(5,4,11),(11,4,14),(13,2,3)]
    for x,y,z in lanterns: put(x,y,z,'lantern',hanging=(y==4),waterlogged=False)
    put(15,3,13,'green_wall_banner',facing='east')

    route=(rect(7,0,8,14)|rect(7,9,13,10)|rect(9,11,11,13)|rect(12,9,13,14)
           |rect(2,5,4,7)|rect(2,6,13,7)|rect(3,10,5,12)|rect(6,2,12,5)|{(11,2)})
    door_cells={(7,8),(8,8)}
    for x,z in route:
        assert grid.get((x,0,z),air)!=air,('unsupported route',x,z)
        for y in range(1,4):
            if (x,z) in door_cells: continue
            assert grid[x,y,z]==air,('route obstructed',x,y,z,grid[x,y,z])
    court=rect(7,2,10,5)
    for x,z in court:
        assert all(grid[x,y,z]==air for y in range(1,height)),('court covered',x,z)
    for x,y,z in lanterns:
        support=(x,y+1,z) if y==4 else (x,y-1,z)
        s=grid[support];props=dict(s[1])
        # Bottom stairs and bottom slabs have a full underside; native attachment
        # survival is also checked for every rotation in the packaged client.
        full=s[0] in {'minecraft:stripped_oak_log','minecraft:spruce_planks','minecraft:cobblestone'}
        underside=(s[0]=='minecraft:spruce_stairs' and props.get('half')=='bottom'
                   or s[0]=='minecraft:spruce_slab' and props.get('type')=='bottom')
        assert full or (y==4 and underside),('unsupported lamp',support,s)
    for block in ('villagerguardgunners:armory','minecraft:crafting_table'):
        assert sum(s[0]==block for s in grid.values())==1
    assert not any(s[0] in {'minecraft:chest','minecraft:barrel','minecraft:jigsaw','minecraft:structure_block'} for s in grid.values())
    manifest=dict(id='supply_yard',template='vggpreview:armory/plains/supply_yard',size=[width,height,depth],
                  anchor=[10,1,14],sentry=[11,1,2],entrance=[7,1,0],doors=[[7,1,8],[8,1,8]],
                  targets=[[3,1,6],[4,1,7],[8,1,7],[8,1,10],[10,1,12],[12,1,14],[4,1,11]],
                  walkTargets=[[[3,1,6],[4,1,6]],[[10,1,12],[12,1,12]]],
                  standing=[[x,1,z] for x,z in sorted(route)],openSky=[[x,1,z] for x,z in sorted(court)],
                  lanterns=[list(p) for p in lanterns],roofProfile=profile,
                  scope='architecture preview; no village entries, generated stock, or garrison')
    return encode(grid,[width,height,depth]),manifest


def encode(grid,size):
    palette=[];indices={};blocks=[]
    for (x,y,z),s in sorted(grid.items()):
        if s not in indices:
            indices[s]=len(palette);entry={'Name':(8,s[0])}
            if s[1]: entry['Properties']=(10,{k:(8,v) for k,v in s[1]})
            palette.append(entry)
        block={'pos':(9,(3,[x,y,z])),'state':(3,indices[s])}
        if s[0].startswith('minecraft:') and s[0].endswith('_wall_banner'): block['nbt']=(10,{'id':(8,'minecraft:banner')})
        blocks.append(block)
    tag={'DataVersion':(3,3465),'size':(9,(3,size)),
         'palette':(9,(10,palette)),'blocks':(9,(10,blocks)),'entities':(9,(10,[]))}
    stream=io.BytesIO(b'\x0a\x00\x00');stream.seek(3);write_payload(stream,10,tag)
    check=io.BytesIO(stream.getvalue());check.read(3);assert read_payload(check,10)==tag
    return gzip.compress(stream.getvalue(),mtime=0)


def main():
    layouts=[build(),build_supply_yard()];output=ROOT/'build/generated/armory-preview'
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as tmp:
        staged=Path(tmp)
        for blob,manifest in layouts:
            target=staged/f'data/vggpreview/structures/armory/plains/{manifest["id"]}.nbt'
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(blob)
        (staged/'data/vggpreview/armory_manifest.json').write_text(json.dumps([m for _,m in layouts],indent=2)+'\n')
        (staged/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Villager Guard Gunners: Plains Armory previews'}})+'\n')
        if output.exists(): shutil.rmtree(output)
        shutil.copytree(staged,output)
    archive=ROOT/'build/distributions/villager-guard-gunners-armory-preview.zip';archive.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(output.rglob('*')):
            if p.is_file(): z.writestr(zipfile.ZipInfo(str(p.relative_to(output))),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    print(archive)


if __name__ == '__main__': main()
