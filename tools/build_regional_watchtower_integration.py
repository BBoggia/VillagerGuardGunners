#!/usr/bin/env python3
"""Build optional village Watchtower packs from the furnished native previews."""
import gzip
import io
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from village_building_pack_balance import write_builtin_balance
from build_watchtower_preview import ROOT, build as plains
from build_regional_watchtower_preview import build as regional, REGIONS
from create_flat_sandbox import read_payload, write_payload


def integration(region, gatehouse):
    blob, row = plains(gatehouse) if region == 'plains' else regional(region, gatehouse)
    # Preview targets can share list objects with walking destinations. Each
    # coordinate schedule needs its own copy before applying the apron shift.
    row = json.loads(json.dumps(row))
    row['region'] = region
    row['layout'] = 'gatehouse_lookout' if gatehouse else 'compact_tower'
    row['id'] = region + '-' + row['layout']
    stream = io.BytesIO(gzip.decompress(blob)); assert stream.read(3) == b'\x0a\x00\x00'
    tag = read_payload(stream, 10)
    palette = tag['palette'][1][1]
    blocks = {}
    for b in tag['blocks'][1][1]:
        x,y,z = b['pos'][1][1]
        b['pos'] = (9,(3,[x,y,z+1])); blocks[x,y,z+1] = b
    tag['size'][1][1][2] += 1
    row['size'][2] += 1
    if 'shift' in row: row['shift'][2] += 1
    for key in ('anchor','entrance'):
        row[key][2] += 1
    for key in ('doors','targets','standing','initialDeckPositions','deckPositions','stairs','walkTargets','rails','slits'):
        for pos in row.get(key,[]): pos[2] += 1
    for detail in row.get('details',[]): detail['pos'][2] += 1

    def put(pos, name, properties=None, nbt=None):
        s={'Name':(8,name)}
        if properties: s['Properties']=(10,{k:(8,v) for k,v in properties.items()})
        if s not in palette: palette.append(s)
        b={'pos':(9,(3,list(pos))),'state':(3,palette.index(s))}
        if nbt: b['nbt']=(10,{k:(8,v) for k,v in nbt.items()})
        blocks[tuple(pos)] = b

    # One outward connector, outside all existing doors and threshold plates.
    F=row['anchor'][1]; x=row['entrance'][0]
    wood='oak' if region=='plains' else REGIONS[region]['wood']
    raised=F>1
    connector=[x,F-1 if raised else F,0]
    final='minecraft:air'
    for dx in (0,1):
        put((x+dx,F-1,0),'minecraft:'+wood+('_slab' if raised else '_planks'),
            {'type':'bottom','waterlogged':'false'} if raised else None)
    # The new apron is outside the preview's cleared volume. Clear its body
    # space explicitly so terrain or foliage cannot remain above the step.
    for dx in (0,1):
        for y in range(F,F+3): put((x+dx,y,0),'minecraft:air')
    if raised: final=f'minecraft:{wood}_slab[type=bottom,waterlogged=false]'
    name=('mmv' if region in ('jungle','swamp') else 'minecraft')+':street'
    put(connector,'minecraft:jigsaw',{'orientation':'north_up'},
        dict(id='minecraft:jigsaw',joint='aligned',name=name,target=name,pool='minecraft:empty',final_state=final))
    # Runtime finalizes this marker to clear air and claims its garrison once.
    put(row['anchor'],'minecraft:structure_block',{'mode':'data'},
        dict(id='minecraft:structure_block',mode='DATA',metadata='village_anchor:watchtower'))
    tag['blocks']=(9,(10,[blocks[p] for p in sorted(blocks)]))
    out=io.BytesIO(b'\x0a\x00\x00');out.seek(3);write_payload(out,10,tag)
    provider='joshie' if region=='ocean' else 'mmv' if region in ('jungle','swamp') else 'minecraft'
    template=f'vggwatchtowers:watchtower/{region}/{row["layout"]}'
    entry=dict(format=1,building_type='watchtower',target_pool=f'{provider}:village/{region}/'+('roads' if region=='ocean' else 'terminators'),
        template=template,processors='minecraft:empty',projection='rigid',weight=1,selection_chance=.25,
        duty_slots=[dict(key=key,offset=[dx,5,0],facing='north',kind='overwatch',weapon_requirement='ranged')
                    for key,dx in (('deck_center',1),('deck_west',-1))])
    if region in ('plains', 'desert', 'savanna', 'taiga', 'snowy'):
        entry['terrain_safety'] = True
        entry['selection_chance'] = 0.5
    row.update(template=template,entry=f'vggwatchtowers:watchtower/{region}/{row["layout"]}',buildingType='watchtower',
               connector=connector,connectorName=name,finalState=final,targetPool=entry['target_pool'],
               service=row['anchor'].copy(),work=row['walkTargets'][-2].copy(),integration=True,
               scope='optional natural village generation; two ranged lookout duties and existing garrison policy')
    return gzip.compress(out.getvalue(),mtime=0),entry,row


def main():
    output=ROOT/'build/generated/regional-watchtower-integration'
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as td:
        staged=Path(td);rows=[]
        for region in ('plains',*REGIONS):
            for gatehouse in (False,True):
                blob,entry,row=integration(region,gatehouse);rows.append(row)
                group='tidal-towns' if region=='ocean' else 'mmv' if region in ('jungle','swamp') else 'vanilla'
                pack=staged/group
                for relative,data in [(f'data/vggwatchtowers/structures/watchtower/{region}/{row["layout"]}.nbt',blob),
                    (f'data/vggwatchtowers/village_buildings/watchtower/{region}/{row["layout"]}.json',(json.dumps(entry,indent=2)+'\n').encode())]:
                    path=pack/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
                (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':f'Villager Guard Gunners: regional Watchtower integration ({group})'}})+'\n')
        write_builtin_balance(staged/'vanilla', 'watchtower', ROOT)
        (staged/'manifest.json').write_text(json.dumps(rows,indent=2)+'\n')
        if output.exists():shutil.rmtree(output)
        shutil.copytree(staged,output)
    for group in ('vanilla','tidal-towns','mmv'):
        archive=ROOT/f'build/distributions/villager-guard-gunners-regional-watchtowers-{group}.zip'
        archive.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
            for path in sorted((output/group).rglob('*')):
                if path.is_file():z.writestr(zipfile.ZipInfo(str(path.relative_to(output/group))),path.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
        print(archive)


if __name__=='__main__':main()
