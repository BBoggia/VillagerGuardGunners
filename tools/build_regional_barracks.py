#!/usr/bin/env python3
"""Build isolated architectural preview templates, never village pool entries."""
import argparse,gzip,io,json,math,shutil,tempfile,zipfile
from pathlib import Path
from create_flat_sandbox import write_payload,read_payload
ROOT=Path(__file__).resolve().parents[1]
def cells(r):
 x,z,w,d=r
 return {(a,b) for a in range(x,x+w) for b in range(z,z+d)}
def state(name,**properties):return (name if ':' in name else 'minecraft:'+name,tuple(sorted((k,str(v).lower()) for k,v in properties.items())))
def from_record(r):return state(r['block'],**r.get('properties',{}))
def build(p):
 b=p['basePlan'];W,D,H=p['size'];F=p['floorY'];pal=p['palette'];air=state('air')
 grid={(x,y,z):air for x in range(W) for y in range(F,H) for z in range(D)}
 def put(x,y,z,s):
  if not (0<=x<W and 0<=y<H and 0<=z<D):raise ValueError((p['id'],'out of bounds',x,y,z))
  grid[x,y,z]=s
 def column(x,z,lo,hi,s):
  for y in range(lo,hi):put(x,y,z,s)
 floor=set().union(*(cells(r) for r in b['floors']));walls=set().union(*(cells(r) for r in b['walls']))|set(map(tuple,b['posts']))
 for x,z in floor:
  put(x,F-1,z,state(pal['wood']+'_planks'))
  if F>1:
   if p['region']=='ocean':put(x,F-2,z,state('oak_log',axis='x'))
   elif (x,z) in walls:column(x,z,0,F-1,state(pal['base']))
 roof={(x,z):(lo,hi) for x,z,lo,hi in p['roofColumns']}
 enclosure=set()
 for v in p['volumes']:enclosure|=cells(v['rect'])
 frame=state(pal['frame'],**({'axis':'y'} if pal['frame'].endswith('_log') else {}))
 for x,z in walls:
  if (x,z) in enclosure:
   ceiling=math.ceil(roof.get((x,z),(F+4,F+5))[0]);column(x,z,F,max(F+3,ceiling),state(pal['wall']))
   base = pal['base']
   if (x*3+z*7)%11==0:base={'cobblestone':'mossy_cobblestone','stone_bricks':'mossy_stone_bricks','mossy_cobblestone':'cobblestone'}.get(base,base)
   put(x,F,z,state(base));put(x,F+3,z,frame)
  elif [x,z] in b['posts'] and (x,z) in roof:
   column(x,z,F,math.ceil(roof.get((x,z),(F+4,F+5))[0]),frame)
  else:put(x,F,z,state(p['rails']['block']))
 for v in p['volumes']:
  x,z,w,d=v['rect']
  for a,c in [(x,z),(x+w-1,z),(x,z+d-1),(x+w-1,z+d-1)]:
   column(a,c,F,math.ceil(roof.get((a,c),(v['wallTop'],0))[0]),frame)
 # Window jambs stay within authored wall cells; later openings and glass win.
 for win in p['windows']:
  x,z,w,d=win['rect']
  adjacent=[(x-1,z),(x+w,z)] if win['face'] in ['north','south'] else [(x,z-1),(x,z+d)]
  for a,c in adjacent:
   if (a,c) in walls:column(a,c,F,F+4,frame)
 # A closed half-block column seals roof slopes and intersections.
 owner={(x,z):key for x,z,top,key in p['roofTop']};rs={r['key']:r for r in p['roofs']}
 for (x,z),(lo,hi) in roof.items():
  r=rs[owner[x,z]]
  exposed=any(n not in roof for n in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)])
  surface=pal['trim']+'_slab' if exposed else r['surfaceBlock']
  for y in range(math.floor(lo),math.ceil(hi)):
   bottom=lo<=y;top=hi>=y+1
   kind='double' if bottom and top else 'bottom' if bottom else 'top'
   put(x,y,z,state(surface,type=kind,waterlogged=False))
  if r['parapetHeight'] and any(n not in roof for n in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)]):
   y=int(hi)
   if hi!=y:put(x,y,z,state(r['surfaceBlock'],type='double',waterlogged=False))
   else:put(x,y,z,state(r['surfaceBlock'],type='bottom',waterlogged=False))
 # Clear openings before applying detail; doors are actual two-block pairs.
 for o in b['openings']:
  for x,z in cells(o['rect']):
   for y in range(F,F+(2 if o['kind']=='doors' else 3)):put(x,y,z,air)
   if (x,z) in enclosure:put(x,F+(2 if o['kind']=='doors' else 3),z,frame)
 for win in p['windows']:
  for x,z in cells(win['rect']):
   for y in range(win['y'],win['y']+win['height']):put(x,y,z,from_record(win['material']))
 for door in p['doors']:
  for leaf in door['leaves']:
   x,y,z=leaf['pos']
   for n,half in enumerate(['lower','upper']):put(x,y+n,z,state(leaf['block'],facing=leaf['facing'],hinge=leaf['hinge'],half=half,open=False,powered=False))
 for f in p['furniture']:
  for r in f['blocks']:put(*r['pos'],from_record(r))
 for r in p['shelves']:put(*r['pos'],from_record(r['state']))
 for r in p['shutters']:
  x,y,z=r['pos']
  for dy in range(r['height']):put(x,y+dy,z,from_record(r['state']))
 for r in p['lights']:
  c=r['anchorColumn'];column(c['x'],c['z'],c['fromY'],c['toY']+1,frame)
 for beam in p['beams']:
  for r in beam['blocks']:put(*r['pos'],from_record(r))
 for r in p['lights']:put(*r['pos'],from_record(r['state']))
 banner=p['banner'];c=banner['supportColumn'];column(c['x'],c['z'],c['fromY'],c['toY'],frame);put(*banner['pos'],from_record(banner['state']))
 # Catch source marker/roof/detail collisions before serializing.
 for x,y,z in b['guards']+[b['sentry']]:
  assert grid.get((x,y,z),air)==air and grid.get((x,y+1,z),air)==air,(p['id'],'standing collision',(x,y,z))
  assert grid.get((x,y-1,z),air)!=air,(p['id'],'missing floor')
 for l in p['lights']:
  assert grid[tuple(l['pos'])][0]=='minecraft:lantern'
  assert grid[tuple(l['support'])][0] not in ['minecraft:air','minecraft:lantern']
 for win in p['windows']:
  for x,z in cells(win['rect']):
   for y in range(win['y'],win['y']+win['height']):assert grid[x,y,z]==from_record(win['material']),(p['id'],'glazing overwritten')
 if 'openCourt' in b:
  for x,z in cells(b['openCourt']):assert all(grid.get((x,y,z),air)==air for y in range(F,H)),(p['id'],'court obstruction')
 palette=[];indices={};blocks=[]
 for (x,y,z),s in sorted(grid.items()):
  if s not in indices:
   indices[s]=len(palette);entry={'Name':(8,s[0])}
   if s[1]:entry['Properties']=(10,{k:(8,v) for k,v in s[1]})
   palette.append(entry)
  blocks.append({'pos':(9,(3,[x,y,z])),'state':(3,indices[s])})
 tag={'DataVersion':(3,3465),'size':(9,(3,[W,H,D])),'palette':(9,(10,palette)),'blocks':(9,(10,blocks)),'entities':(9,(10,[]))}
 stream=io.BytesIO(b'\x0a\x00\x00');stream.seek(3);write_payload(stream,10,tag);payload=gzip.compress(stream.getvalue(),mtime=0)
 check=io.BytesIO(gzip.decompress(payload));assert check.read(3)==b'\x0a\x00\x00';assert read_payload(check,10)==tag
 return payload,dict(id=p['id'],template='vggpreview:regional/'+p['region']+'/'+p['layout'],size=[W,H,D],floorY=F,guards=b['guards'],sentry=b['sentry'],entrance=[b['connector'][0],F,1],targets=[[x,F,z] for x,z in b['targets']],lanterns=len(p['lights']),doorHalves=4*len(p['doors']),carpets=sum(s[0].endswith('_carpet') for s in grid.values()),blockCount=len(blocks))
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,default=ROOT/'src/structureDesign/resources/regional-barracks-details.json');parser.add_argument('--output',type=Path,default=ROOT/'build/generated/regional-barracks-preview');parser.add_argument('--zip',type=Path,default=ROOT/'build/distributions/villager-guard-gunners-regional-barracks-preview.zip');args=parser.parse_args()
 data=json.loads(args.source.read_text())
 expected={(r,l) for r in ['plains','desert','savanna','taiga','snowy','ocean','jungle','swamp'] for l in ['compact','courtyard']}
 if len(data['layouts'])!=16 or {(p['region'],p['layout']) for p in data['layouts']}!=expected:raise ValueError('Expected one compact and courtyard layout for each of the eight regions')
 args.output.parent.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory(dir=args.output.parent) as td:
  staged=Path(td);manifest=[]
  for p in data['layouts']:
   blob,record=build(p);manifest.append(record);target=staged/'data/vggpreview/structures/regional'/p['region']/(p['layout']+'.nbt');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(blob)
  (staged/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Villager Guard Gunners: regional Barracks architecture preview'}})+'\n')
  (staged/'data/vggpreview/regional_barracks_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
  if args.output.exists():shutil.rmtree(args.output)
  shutil.copytree(staged,args.output)
 args.zip.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(args.zip,'w',zipfile.ZIP_DEFLATED) as z:
  for p in sorted(args.output.rglob('*')):
   if p.is_file():z.write(p,p.relative_to(args.output))
 print(f'Built {len(manifest)} preview templates: {args.zip}')
if __name__=='__main__':main()
