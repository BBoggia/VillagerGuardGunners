import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_regional_sentry_post_integration import integration, REGIONS, plains, regional
from test_regional_armory_integration import decode


class SentryPostIntegrationTests(unittest.TestCase):
    def test_preserved_furniture_and_single_anchor_connector(self):
        for region in ('plains',*REGIONS):
            for lodge in (False,True):
                with self.subTest(region=region,lodge=lodge):
                    original,old=plains(lodge) if region=='plains' else regional(region,lodge)
                    original=decode(original);blob,entry,row=integration(region,lodge);tag=decode(blob)
                    self.assertEqual((blob,entry,row),integration(region,lodge))
                    palette=tag['palette'][1][1];blocks={tuple(b['pos'][1][1]):b for b in tag['blocks'][1][1]}
                    names=lambda b:palette[b['state'][1]]['Name'][1]
                    self.assertEqual([*old['size'][:2],old['size'][2]+1],tag['size'][1][1])
                    for b in original['blocks'][1][1]:
                        x,y,z=b['pos'][1][1];actual=blocks[x,y,z+1]
                        if [x,y,z]==old['anchor']:
                            self.assertEqual('village_anchor:sentry_post',actual['nbt'][1]['metadata'][1])
                        else:
                            self.assertEqual(original['palette'][1][1][b['state'][1]],palette[actual['state'][1]])
                            self.assertEqual(b.get('nbt'),actual.get('nbt'))
                    self.assertEqual(1,sum(names(b)=='minecraft:structure_block' for b in blocks.values()))
                    connectors=[b for b in blocks.values() if names(b)=='minecraft:jigsaw'];self.assertEqual(1,len(connectors))
                    c=connectors[0];self.assertEqual(row['connector'],c['pos'][1][1]);self.assertEqual(0,row['connector'][2])
                    self.assertEqual(('mmv' if region in ('jungle','swamp') else 'minecraft')+':street',c['nbt'][1]['name'][1])
                    self.assertEqual('minecraft:empty',c['nbt'][1]['pool'][1])
                    self.assertEqual([dict(key='road_sentry',offset=[0,0,0],facing='north',kind='sentry',weapon_requirement='any')],entry['duty_slots'])
                    for key in ('anchor','entrance'):
                        x,y,z=old[key];self.assertEqual([x,y,z+1],row[key])
                    for key in ('walkTargets','standing','traffic','rails'):
                        self.assertEqual([[x,y,z+1] for x,y,z in old.get(key,[])],row.get(key,[]))
                    if 'shift' in old:
                        self.assertEqual([old['shift'][0],old['shift'][1],old['shift'][2]+1],row['shift'])
                    self.assertEqual(row['anchor'],row['service']);self.assertEqual(row['walkTargets'][2],row['work'])
                    x,f,_=row['entrance']
                    for dx in (0,1):
                        for y in range(f,f+3):
                            if [x+dx,y,0]!=row['connector']:
                                self.assertEqual('minecraft:air',names(blocks[x+dx,y,0]))
                    if f==2:
                        self.assertIn('_slab[type=bottom',row['finalState'])
                        self.assertEqual('bottom',palette[blocks[x+1,f-1,0]['state'][1]]['Properties'][1]['type'][1])
                    else:self.assertEqual('minecraft:air',row['finalState'])
                    self.assertEqual(1,entry['weight']);self.assertEqual((.5 if region in ('plains','desert','savanna','taiga','snowy') else .25),entry['selection_chance'])
                    self.assertEqual('sentry_post',entry['building_type'])


if __name__=='__main__':unittest.main()
