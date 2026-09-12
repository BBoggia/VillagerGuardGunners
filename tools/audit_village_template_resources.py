#!/usr/bin/env python3
"""Audit supplied Minecraft/mod jars without loading optional mod classes.

Usage: python3 tools/audit_village_template_resources.py --output audit.json jar ...
The output feeds generate_village_template_profiles.py. No local-note path is required.
"""
from pathlib import Path
import argparse,json,zipfile,gzip,io,hashlib,collections
from create_flat_sandbox import read_payload
from run_village_compatibility import plain_nbt
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output',required=True,type=Path)
parser.add_argument('jars',nargs='+',type=Path)
args=parser.parse_args()
paths=args.jars
records={}; pools={};structures={};tags={}
for p in paths:
 with zipfile.ZipFile(p) as z:
  for n in z.namelist():
   parts=n.split('/')
   if len(parts)<4 or parts[0]!='data':continue
   ns=parts[1]
   if n.endswith('.json') and parts[2:4]==['worldgen','template_pool']:pools.setdefault(ns+':'+('/'.join(parts[4:])[:-5]),[]).append({'jar':str(p),'value':json.loads(z.read(n))})
   if n.endswith('.json') and parts[2:4]==['worldgen','structure']:structures.setdefault(ns+':'+('/'.join(parts[4:])[:-5]),[]).append({'jar':str(p),'value':json.loads(z.read(n))})
   if n.endswith('.json') and parts[2:5]==['tags','worldgen','structure']:tags.setdefault(ns+':'+('/'.join(parts[5:])[:-5]),[]).append({'jar':str(p),'value':json.loads(z.read(n))})
   if not(n.endswith('.nbt') and parts[2]=='structures' and (ns!='minecraft' or parts[3]=='village')):continue
   raw=z.read(n);s=io.BytesIO(gzip.decompress(raw) if raw.startswith(b'\x1f\x8b') else raw);assert s.read(1)==b'\x0a';read_payload(s,8);d=plain_nbt((10,read_payload(s,10)))
   palettes=d.get('palettes',[d.get('palette',[])])
   bed_sets=[];doors=[];blocks=collections.Counter();jigsaws=[];connectors=[];invalid=0
   for palette in palettes:
    beds=[]
    for b in d.get('blocks',[]):
     if not 0<=b['state']<len(palette):invalid+=1;continue
     state=palette[b['state']];name=state['Name'];props=state.get('Properties',{});blocks[name]+=1
     if name.endswith('_bed') and props.get('part')=='head':beds.append(b['pos'])
     if name.endswith('_door') and not name.endswith('trapdoor') and props.get('half')=='lower':doors.append({'pos':b['pos'],'facing':props.get('facing')})
     if name=='minecraft:jigsaw' and b.get('nbt'):
      jigsaws.append(b['nbt'])
      if 'entrance' in b['nbt'].get('name',''):connectors.append(b['pos'])
    bed_sets.append({tuple(b) for b in beds})
   beds=sorted(set.intersection(*bed_sets)) if bed_sets else []
   entities=[e.get('nbt',{}).get('id','') for e in d.get('entities',[]) if isinstance(e,dict)]
   if any(not isinstance(e,dict) for e in d.get('entities',[])):invalid+=1
   rec={'jar':str(p),'resource':n,'sha256':hashlib.sha256(raw).hexdigest(),'size':d.get('size'),'invalid_states':invalid, 'beds':beds,'connectors':sorted(set(map(tuple,connectors))),'doors':list({tuple(d['pos']):d for d in doors}.values()),'entities':entities,'blocks':dict(blocks),'jigsaws':list({json.dumps(j,sort_keys=True):j for j in jigsaws}.values())}
   records.setdefault(ns+':'+('/'.join(parts[3:])[:-4]),[]).append(rec)
args.output.write_text(json.dumps({'templates':records,'pools':pools,'structures':structures,'tags':tags},indent=2)+'\n')
