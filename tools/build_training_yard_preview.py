#!/usr/bin/env python3
"""Build furnished Plains Training Yard previews as native Minecraft structures."""
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from build_armory_preview import ROOT, rect, state, encode


def build(courtyard=False):
    name = 'drill_courtyard' if courtyard else 'practice_yard'
    width, height, depth = (17, 9, 19) if courtyard else (13, 8, 17)
    air = state('air')
    grid = {(x,y,z): air for x in range(width) for y in range(1,height) for z in range(depth)}

    def put(x,y,z,block,**props):
        assert 0 <= x < width and 0 <= y < height and 0 <= z < depth, (name,x,y,z)
        grid[x,y,z] = state(block,**props)

    def fill(cells,y,block,**props):
        for x,z in cells: put(x,y,z,block,**props)

    if courtyard:
        floor = rect(1,1,15,17) | rect(14,0,15,0) | {(16,z) for z in (2,6,16)}
        route = rect(14,0,15,17) | rect(2,3,15,4) | rect(3,5,4,17) | rect(3,16,15,17)
        bay = rect(7,5,12,6); field = rect(7,7,12,13)
        assembly = rect(2,14,5,17)
        room = rect(2,6,4,12)
        shell = rect(1,5,5,13) - room
        back = rect(6,14,13,15)
        sides = rect(6,7,6,13) | rect(13,7,13,13)
        posts = {(x,z) for x in (1,6,13,16) for z in (2,6)}
        doors = [(3,1,5,'north','left'),(4,1,5,'north','right'),
                 (3,1,13,'south','right'),(4,1,13,'south','left')]
        benches = [(2,1,z) for z in range(7,10)]
        table = [2,1,11]; anchor = [10,1,6]; duties = [[11,1,6],[8,1,6]]
        targets = [[8,2,12],[11,2,12]]; entrance = [14,1,0]
        lamps = [(2,4,3),(9,4,3),(14,4,3),(2,4,10),(14,4,16)]
        mast = (16,16)
        # Follow the whole loop in short native walking legs, including both doors.
        walk = [[11,1,6],[14,1,12],[14,1,17],[3,1,16],[3,1,11],[3,1,6],[14,1,3],[14,1,-3]]
        traffic = [[14,1,-3],[14,1,12],[14,1,17],[3,1,16],[3,1,11],[3,1,6],[14,1,3],[14,1,-3]]
        roof = {(x,z): [5,6,7,8,7,6,5][z-1] for x,z in rect(0,1,16,7)}
        for x,z in rect(0,5,6,14): roof[x,z] = max(roof.get((x,z),0),[5,6,7,7,7,6,5][x])
    else:
        floor = rect(1,1,11,15) | rect(10,0,11,0) | {(12,z) for z in (1,5,11)}
        route = rect(10,0,11,12) | rect(3,2,11,3) | {(3,4)}
        bay = rect(3,4,8,5); field = rect(3,6,8,12)
        assembly = set(); room = set(); shell = set()
        back = rect(2,13,9,14)
        sides = rect(2,7,2,12) | rect(9,7,9,12)
        posts = {(x,z) for x in (1,9,12) for z in (1,5)}
        doors = []; benches = [(2,1,1),(3,1,1),(10,1,14),(11,1,14)]
        table = [2,1,4]; anchor = [6,1,5]; duties = [[7,1,5],[4,1,5]]
        targets = [[4,2,11],[7,2,11]]; entrance = [10,1,0]
        lamps = [(2,4,2),(10,4,2),(10,4,11)]; mast = (12,11)
        walk = [[7,1,5],[10,1,7],[10,1,12],[10,1,3],[3,1,3],[3,1,4],[10,1,3],[10,1,-3]]
        traffic = [[10,1,-3],[10,1,3],[10,1,7],[10,1,12],[10,1,7],[3,1,3],[10,1,3],[10,1,-3]]
        roof = {(x,z): [5,6,7,7,7,6,5][z] for x,z in rect(0,0,12,6)}

    for x,z in floor:
        material = 'packed_mud' if (x,z) in field or (x,z) in assembly else 'cobblestone'
        if (x,z) in bay or (x,z) in room: material = 'oak_planks'
        if material == 'cobblestone' and (x+2*z)%7 == 0: material = 'andesite'
        if material == 'packed_mud' and (x*3+z)%9 == 0: material = 'coarse_dirt'
        put(x,0,z,material)
    # Substantial backstop, low stone returns, and a framed weather shelter.
    for y in range(1,5):
        for x,z in back: put(x,y,z,'andesite' if (x+z+y)%7==0 else 'stone_bricks' if y==4 else 'cobblestone')
        fill(shell,y,'cobblestone' if y==1 else 'white_terracotta')
    fill(back,5,'stone_brick_slab',type='bottom',waterlogged=False)
    for y in range(1,4): fill(sides,y,'cobblestone' if y<3 else 'andesite')
    fill(sides,4,'cobblestone_slab',type='bottom',waterlogged=False)
    for x,z in posts:
        for y in range(1,roof[x,z]): put(x,y,z,'stripped_oak_log',axis='y')
    beam_zs = (2,6) if courtyard else (1,5)
    for z in beam_zs: fill(rect(1,z,width-1,z),4,'stripped_oak_log',axis='x')
    if courtyard:
        for x,z in ((1,5),(5,5),(1,13),(5,13),(1,10),(5,10)):
            for y in range(1,roof[x,z]): put(x,y,z,'stripped_oak_log',axis='y')
        for z in (5,13): fill(rect(1,z,5,z),4,'stripped_oak_log',axis='x')
        for z in (8,11):
            put(1,2,z,'glass_pane',north=True,south=True,east=False,west=False,waterlogged=False)
            put(0,2,z,'oak_trapdoor',facing='west',half='bottom',open=True,powered=False,waterlogged=False)
        # A narrow supported notice panel beside, rather than over, the doorway.
        put(2,2,5,'oak_planks')
        put(1,3,1,'green_wall_banner',facing='north')
    else:
        put(1,3,0,'green_wall_banner',facing='north')
        put(1,2,3,'stripped_oak_log',axis='y');put(1,1,3,'stripped_oak_log',axis='y')
        put(1,3,3,'oak_planks')
    for x,y,z,facing,hinge in doors:
        for dy,half in ((0,'lower'),(1,'upper')):
            put(x,y+dy,z,'oak_door',facing=facing,hinge=hinge,half=half,open=False,powered=False)
        put(x,y+2,z,'white_terracotta')
    for x,y,z in benches: put(x,y,z,'oak_slab',type='bottom',waterlogged=False)
    put(*table,'crafting_table')
    for x,y,z in targets:
        put(x,y-1,z,'stripped_oak_log',axis='y');put(x,y,z,'target',power=0)

    # Joined roof height map. Full backing closes rising courses and the L junction.
    for (x,z),y in sorted(roof.items()):
        neighbors = [roof.get(p,y) for p in ((x-1,z),(x+1,z),(x,z-1),(x,z+1))]
        for by in range(max(4,min(neighbors)-1),y): put(x,by,z,'spruce_planks')
        # Resolve the slope from the combined roof, including the pavilion junction.
        higher = [(roof.get((x+dx,z+dz),y),facing) for dx,dz,facing in ((0,-1,'north'),(0,1,'south'),(-1,0,'west'),(1,0,'east'))]
        peak,facing = max(higher)
        if peak>y: put(x,y,z,'spruce_stairs',facing=facing,half='bottom',shape='straight',waterlogged=False)
        else: put(x,y,z,'spruce_slab',type='bottom',waterlogged=False)
    # Close the pavilion's gable ends beneath the joined roof.
    for x,z in shell:
        for y in range(4,roof[x,z]):
            if grid[x,y,z] == air: put(x,y,z,'white_terracotta')
    # Rear lamp's independent mast and horizontal bracket stay outside the route.
    for y in range(1,6): put(mast[0],y,mast[1],'stripped_oak_log',axis='y')
    fill(rect(mast[0]-2,mast[1],mast[0],mast[1]),5,'stripped_oak_log',axis='x')
    for x,y,z in lamps:
        if (x,z) in roof:
            for by in range(y+1,roof[x,z]):
                if grid[x,by,z] == air: put(x,by,z,'chain',axis='y',waterlogged=False)
        put(x,y,z,'lantern',hanging=True,waterlogged=False)

    door_cells = {(x,y,z) for x,y,z,_,_ in doors}
    standing = {(x,1,z) for x,z in route|bay|assembly|room
                if (x,1,z) not in {(x,y,z) for x,y,z in benches} and [x,1,z]!=table}
    for x,y,z in standing:
        assert grid.get((x,y-1,z),air)!=air, ('unsupported',name,x,y,z)
        if (x,y,z) not in door_cells:
            for dy in range(3): assert grid[x,y+dy,z]==air, ('blocked reservation',name,x,y+dy,z,grid[x,y+dy,z])
    for x,y,z in lamps: assert grid[x,y+1,z]!=air, ('unsupported lamp',name,x,y,z)
    details = [dict(pos=list(p),block=s[0]) for p,s in sorted(grid.items())
               if s[0] in {'minecraft:lantern','minecraft:oak_door','minecraft:oak_trapdoor','minecraft:glass_pane','minecraft:green_wall_banner','minecraft:target'}]
    manifest = dict(id=name,template='vggpreview:training_yard/plains/'+name,size=[width,height,depth],
        anchor=anchor,initialPositions=[anchor,[anchor[0]-1,1,anchor[2]]],dutyPositions=duties,entrance=entrance,
        targetBlocks=targets,doors=[list(p[:3]) for p in doors],standing=[list(p) for p in sorted(standing)],
        circulation=[[x,1,z] for x,z in sorted(route)],field=[[x,1,z] for x,z in sorted(field)],
        walkTargets=walk,traffic=traffic,lightSamples=duties+[[3,1,11] if courtyard else [3,1,4]],
        details=details,roof=[[x,y,z] for (x,z),y in sorted(roof.items())],
        scope='manual architecture preview; no automatic garrison, duties, practice fire or village generation')
    return encode(grid,[width,height,depth]),manifest


def main():
    layouts = [build(),build(True)]
    output = ROOT/'build/generated/training-yard-preview'
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as tmp:
        staged = Path(tmp)
        for blob,row in layouts:
            target = staged/f'data/vggpreview/structures/training_yard/plains/{row["id"]}.nbt'
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(blob)
        (staged/'data/vggpreview/training_yard_manifest.json').write_text(json.dumps([r for _,r in layouts],indent=2)+'\n')
        (staged/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Villager Guard Gunners: Plains Training Yard previews'}})+'\n')
        if output.exists(): shutil.rmtree(output)
        shutil.copytree(staged,output)
    archive=ROOT/'build/distributions/villager-guard-gunners-training-yard-preview.zip'
    archive.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(output.rglob('*')):
            if p.is_file(): z.writestr(zipfile.ZipInfo(str(p.relative_to(output))),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    print(archive)


if __name__=='__main__': main()
