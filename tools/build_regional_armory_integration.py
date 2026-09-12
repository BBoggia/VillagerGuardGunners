#!/usr/bin/env python3
"""Build optional village Armory packs from the furnished native previews."""
import gzip
import io
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from village_building_pack_balance import write_builtin_balance
from build_armory_preview import ROOT, build as plains_compact, build_supply_yard
from build_regional_armory_preview import build as regional
from draw_regional_armories import REGIONS
from create_flat_sandbox import read_payload, write_payload


def integration(region, yard):
    blob, row = (build_supply_yard() if yard else plains_compact()) if region == 'plains' else regional(region, yard)
    # Preview targets can share list objects with walking destinations. Each
    # coordinate schedule needs its own copy before applying the apron shift.
    row = json.loads(json.dumps(row))
    row['region'] = region
    row['layout'] = 'supply_yard' if yard else 'compact'
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
    for key in ('anchor','sentry','entrance'):
        row[key][2] += 1
    for key in ('doors','targets','standing','openSky','lanterns'):
        for pos in row[key]: pos[2] += 1
    for pair in row['walkTargets']:
        for pos in pair: pos[2] += 1
    for detail in row.get('details',[]): detail['pos'][2] += 1
    for threshold in row.get('thresholds',[]): threshold['pos'][2] += 1
    if 'storeBounds' in row:
        row['storeBounds'][1] += 1; row['storeBounds'][3] += 1

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
    if raised: final=f'minecraft:{wood}_slab[type=bottom,waterlogged=false]'
    name=('mmv' if region in ('jungle','swamp') else 'minecraft')+':street'
    put(connector,'minecraft:jigsaw',{'orientation':'north_up'},
        dict(id='minecraft:jigsaw',joint='aligned',name=name,target=name,pool='minecraft:empty',final_state=final))
    # The runtime must create the Armory: a preplaced empty block would prevent
    # generated identity and one-time stock initialization.
    put(row['anchor'],'minecraft:structure_block',{'mode':'data'},
        dict(id='minecraft:structure_block',mode='DATA',metadata='village_anchor:armory'))
    tag['blocks']=(9,(10,[blocks[p] for p in sorted(blocks)]))
    out=io.BytesIO(b'\x0a\x00\x00');out.seek(3);write_payload(out,10,tag)
    provider='joshie' if region=='ocean' else 'mmv' if region in ('jungle','swamp') else 'minecraft'
    template=f'vggarmories:armory/{region}/{row["layout"]}'
    entry=dict(format=1,building_type='armory',target_pool=f'{provider}:village/{region}/'+('roads' if region=='ocean' else 'terminators'),
        template=template,processors='minecraft:empty',projection='rigid',weight=1,selection_chance=.25,
        duty_slots=[dict(key='entry_sentry',offset=[s-a for s,a in zip(row['sentry'],row['anchor'])],
                         facing='north',kind='sentry',weapon_requirement='any')])
    if region in ('plains', 'desert', 'savanna', 'taiga', 'snowy'):
        entry['terrain_safety'] = True
        entry['selection_chance'] = 0.5
    row.update(template=template,entry=f'vggarmories:armory/{region}/{row["layout"]}',buildingType='armory',
               connector=connector,connectorName=name,finalState=final,targetPool=entry['target_pool'],
               service=[row['anchor'][0],F,row['anchor'][2]-2],integration=True)
    return gzip.compress(out.getvalue(),mtime=0),entry,row


def main():
    output=ROOT/'build/generated/regional-armory-integration'
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as td:
        staged=Path(td);rows=[]
        for region in ('plains',*REGIONS):
            for yard in (False,True):
                blob,entry,row=integration(region,yard);rows.append(row)
                group='tidal-towns' if region=='ocean' else 'mmv' if region in ('jungle','swamp') else 'vanilla'
                pack=staged/group
                for relative,data in [(f'data/vggarmories/structures/armory/{region}/{row["layout"]}.nbt',blob),
                    (f'data/vggarmories/village_buildings/armory/{region}/{row["layout"]}.json',(json.dumps(entry,indent=2)+'\n').encode())]:
                    path=pack/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
                (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':f'Villager Guard Gunners: regional Armory integration ({group})'}})+'\n')
        write_builtin_balance(staged/'vanilla', 'armory', ROOT)
        (staged/'manifest.json').write_text(json.dumps(rows,indent=2)+'\n')
        if output.exists():shutil.rmtree(output)
        shutil.copytree(staged,output)
    for group in ('vanilla','tidal-towns','mmv'):
        archive=ROOT/f'build/distributions/villager-guard-gunners-regional-armories-{group}.zip'
        archive.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
            for path in sorted((output/group).rglob('*')):
                if path.is_file():z.writestr(zipfile.ZipInfo(str(path.relative_to(output/group))),path.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
        print(archive)


if __name__=='__main__':main()
