#!/usr/bin/env python3
"""Build two furnished Plains integrated Watchtower previews as native NBT."""
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from build_armory_preview import ROOT, rect, state, encode


def build(gatehouse=False):
    name = 'gatehouse_lookout' if gatehouse else 'compact_tower'
    width, height, depth = (17, 14, 15) if gatehouse else (11, 14, 13)
    air = state('air')
    grid = {(x,y,z): air for x in range(width) for y in range(1,height) for z in range(depth)}

    def put(x,y,z,block,**props):
        assert 0 <= x < width and 0 <= y < height and 0 <= z < depth, (x,y,z)
        grid[x,y,z] = state(block,**props)

    def fill(cells,y,block,**props):
        for x,z in cells: put(x,y,z,block,**props)

    def post(x,z,lo,hi):
        for y in range(lo,hi+1): put(x,y,z,'stripped_oak_log',axis='y')

    def beam(x1,x2,y,z):
        fill(rect(x1,z,x2,z),y,'stripped_oak_log',axis='x')

    def stair(x,y,z,facing):
        for below in range(1,y): put(x,below,z,'oak_planks')
        put(x,y,z,'oak_stairs',facing=facing,half='bottom',shape='straight',waterlogged=False)

    bay = rect(3,3,4,4)
    if gatehouse:
        floor = rect(1,1,15,13) | rect(10,0,11,0)
        shell = rect(1,2,6,12) - rect(2,3,5,11)
        deck = rect(1,1,15,13)
        holes = rect(10,7,11,11) | rect(7,9,9,11)
        stairs = [(x,y,z,'south') for x in (10,11) for y,z in ((1,7),(2,8),(3,9))]
        stairs += [(x,y,z,'north') for x in (7,8) for y,z in ((4,9),(5,8))]
        middle = rect(7,10,11,11)
        lower = rect(10,0,11,6) | rect(4,4,11,5) | rect(4,4,5,11) | bay
        upper = rect(7,2,14,5) | rect(4,4,5,11) | rect(4,6,8,7) | rect(13,2,14,12) | bay
        well_rails = rect(6,9,6,11) | rect(7,12,11,12) | rect(12,7,12,12) | rect(10,6,11,6)
        doors = [(6,1,4,'east','left'),(6,1,5,'east','right')]
        corners = [(1,2),(6,2),(1,12),(6,12),(1,7)]
        upper_posts = [(1,1),(9,1),(15,1),(1,7),(15,7),(1,13),(6,13),(12,13),(15,13)]
        profile = [9,10,11,12,13,12,11,10,10,11,12,12,12,11,10,9,9]
        roof_span = (0,14)
        table = [3,1,11]
        benches = [(2,1,z) for z in range(7,10)] + [(2,6,10),(2,6,11)]
        entrance = [10,1,0]
        # Ground bay, turning landing, deck, east gallery, return downstairs, outside.
        walk = [[4,1,3],[8,4,10],[4,6,3],[13,6,10],[4,1,11],[10,1,-3]]
        lamps = [(4,4,6),(10,4,2),(4,9,5),(13,9,5),(5,9,11)]
    else:
        floor = rect(1,1,9,11) | rect(5,0,6,0)
        shell = rect(1,2,9,11) - rect(2,3,8,10)
        deck = rect(1,1,9,11)
        holes = rect(7,5,8,8)
        stairs = [(x,y,z,'north') for x in (7,8) for y,z in ((1,8),(2,7),(3,6),(4,5),(5,4))]
        middle = set()
        lower = rect(5,0,6,10) | rect(3,4,6,5) | rect(5,9,8,10) | rect(4,9,5,10) | bay
        upper = rect(5,2,8,3) | rect(5,3,6,4) | rect(2,4,5,5) | rect(4,5,5,10) | bay
        well_rails = rect(6,5,6,8) | rect(7,9,8,9)
        doors = [(5,1,2,'north','left'),(6,1,2,'north','right')]
        corners = [(1,2),(9,2),(1,11),(9,11),(1,6),(9,6)]
        upper_posts = [(1,1),(9,1),(1,6),(9,6),(1,11),(5,11),(9,11)]
        profile = [9,10,11,12,13,13,13,12,11,10,9]
        roof_span = (1,12)
        table = [3,1,10]
        benches = [(2,1,z) for z in range(6,9)] + [(2,6,9),(2,6,10)]
        entrance = [5,1,0]
        walk = [[4,1,3],[7,1,9],[4,6,3],[4,6,10],[4,1,10],[5,1,-3]]
        lamps = [(4,4,6),(6,4,1),(4,9,5),(5,9,10)]

    # Ground foundations and the framed ready room.
    for x,z in floor:
        block = 'oak_planks' if (x,z) not in shell and z > 2 and (not gatehouse or x < 6) else 'cobblestone'
        if block == 'cobblestone' and (x+2*z)%11 == 0: block = 'andesite'
        put(x,0,z,block)
    for y in range(1,5): fill(shell,y,'cobblestone' if y == 1 else 'white_terracotta')
    for x,z in corners: post(x,z,1,4)
    for z in ((2,12) if gatehouse else (2,11)):
        beam(1,6 if gatehouse else 9,4,z)
    # Exterior framing carries the larger porch and deck.
    if gatehouse:
        for x,z in ((9,2),(12,2),(15,2),(15,7),(15,13),(12,13),(6,13),(1,13)):
            post(x,z,1,5)
        beam(9,15,4,2)
        for z in (6,13): beam(6,15,5,z)
    else:
        for x in (1,9): post(x,1,1,5)
        beam(1,9,4,1)
    fill(deck-holes,5,'oak_planks')
    for x,z in deck:
        if x in (1,width-2) or z in (1,depth-2): put(x,5,z,'stripped_oak_log',axis='x' if z in (1,depth-2) else 'z')
    for x,y,z,facing in stairs: stair(x,y,z,facing)
    for y in range(1,4): fill(middle,y,'oak_planks')
    if gatehouse:
        # Solid division closes the unusable space beside the two flights.
        for y in range(1,8): fill(rect(9,7,9,9),y,'oak_planks')
    for x,y,z,facing,hinge in doors:
        for dy,half in ((0,'lower'),(1,'upper')):
            put(x,y+dy,z,'oak_door',facing=facing,half=half,hinge=hinge,open=False,powered=False)
    # Shuttered lower windows; no inventory blocks or villager workstations.
    for z in ((5,9) if gatehouse else (4,9)):
        put(1,2,z,'glass_pane',north=True,south=True,east=False,west=False,waterlogged=False)
        put(0,2,z,'oak_trapdoor',facing='west',half='bottom',open=True,powered=False,waterlogged=False)
    back = 12 if gatehouse else 11
    for x in (3,4): put(x,2,back,'glass_pane',north=False,south=False,east=True,west=True,waterlogged=False)
    put(2,2,2,'oak_planks')
    put(2,3,1,'green_wall_banner',facing='north')
    for x,y,z in benches: put(x,y,z,'oak_slab',type='bottom',waterlogged=False)
    put(*table,'crafting_table')

    # Ordinary perimeter and well rails are 1.5-high fences. The northward
    # firing slit uses a half-block sill under a lintel, too short for a guard.
    perimeter = (deck - rect(2,2,width-3,depth-3)) - rect(1,1,width-2,1)
    rails = perimeter | well_rails
    for x,z in rails: put(x,6,z,'oak_fence',north=False,south=False,east=False,west=False,waterlogged=False)
    for x,z in upper_posts: post(x,z,6,9)
    for x in range(2,width-2):
        if (x,1) not in upper_posts: put(x,6,1,'cobblestone_slab',type='bottom',waterlogged=False)
    beam(1,width-2,8,1)
    # Upper frame and gabled roofs: backing closes every raised roof course.
    for z in (5,depth-3): beam(1,width-2,9,z)
    for x,z in upper_posts:
        for y in range(9,profile[x]): put(x,y,z,'stripped_oak_log',axis='y')
    for z in (1,depth-2):
        for x in range(1,width-1):
            for y in range(9,profile[x]):
                if (x,z) not in upper_posts: put(x,y,z,'white_terracotta')
    for x,y in enumerate(profile):
        for z in range(roof_span[0],roof_span[1]+1):
            put(x,y-1,z,'spruce_planks')
            left = profile[max(0,x-1)]
            right = profile[min(width-1,x+1)]
            if y >= left and y >= right: put(x,y,z,'spruce_slab',type='bottom',waterlogged=False)
            else: put(x,y,z,'spruce_stairs',facing='east' if right>left else 'west',half='bottom',shape='straight',waterlogged=False)
    for x,y,z in lamps:
        if y == 9:
            for cy in range(y+1,profile[x]-1): put(x,cy,z,'chain',axis='y',waterlogged=False)
        put(x,y,z,'lantern',hanging=True,waterlogged=False)

    door_cells = {(x,y,z) for x,y,z,_,_ in doors}
    standing = {(x,1,z) for x,z in lower} | {(x,6,z) for x,z in upper} | {(x,4,z) for x,z in middle}
    for x,y,z in standing:
        assert grid.get((x,y-1,z),air) != air, ('unsupported floor',name,x,y,z)
        for dy in range(2 if (x,y,z) in door_cells else 3):
            if (x,y,z) in door_cells: continue
            assert grid[x,y+dy,z] == air, ('blocked reserved lane',name,x,y+dy,z,grid[x,y+dy,z])
    for x,y,z,_ in stairs:
        for dy in (1,2): assert grid[x,y+dy,z] == air, ('stair headroom',name,x,y,z)
    for x,y,z in lamps: assert grid.get((x,y+1,z),air) != air, ('unsupported light',name,x,y,z)
    assert sum(s[0]=='minecraft:crafting_table' for s in grid.values()) == 1
    assert not any(s[0] in {'minecraft:chest','minecraft:barrel','minecraft:jigsaw','minecraft:structure_block','villagerguardgunners:armory'} or s[0].endswith('_bed') for s in grid.values())
    details = [dict(pos=list(p),block=s[0]) for p,s in sorted(grid.items())
               if s[0] in {'minecraft:lantern','minecraft:oak_door','minecraft:oak_trapdoor','minecraft:glass_pane','minecraft:green_wall_banner'}]
    manifest = dict(id=name,template='vggpreview:watchtower/plains/'+name,size=[width,height,depth],
        anchor=[4,1,3],initialDeckPositions=[[4,6,3],[3,6,3]],
        deckPositions=[[5,6,3],[3,6,3]],entrance=entrance,
        doors=[list(p[:3]) for p in doors],stairs=[list(p[:3]) for p in stairs],
        standing=[list(p) for p in sorted(standing)],walkTargets=walk,
        targets=[[4,1,3],[5,6,3],[4,1,table[2]]],
        details=details,rails=[[x,6,z] for x,z in sorted(rails) if (x,z) not in upper_posts],
        slits=[[x,6,1] for x in range(2,width-2) if (x,1) not in upper_posts],
        roofProfile=profile,scope='architecture preview; no automatic garrison, duties or village generation')
    return encode(grid,[width,height,depth]), manifest


def main():
    layouts = [build(),build(True)]
    output = ROOT/'build/generated/watchtower-preview'
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as tmp:
        staged = Path(tmp)
        for blob,row in layouts:
            target = staged/f'data/vggpreview/structures/watchtower/plains/{row["id"]}.nbt'
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(blob)
        (staged/'data/vggpreview/watchtower_manifest.json').write_text(json.dumps([row for _,row in layouts],indent=2)+'\n')
        (staged/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Villager Guard Gunners: Plains Watchtower previews'}})+'\n')
        if output.exists(): shutil.rmtree(output)
        shutil.copytree(staged,output)
    archive = ROOT/'build/distributions/villager-guard-gunners-watchtower-preview.zip'
    archive.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(output.rglob('*')):
            if p.is_file(): z.writestr(zipfile.ZipInfo(str(p.relative_to(output))),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    print(archive)


if __name__ == '__main__': main()
