import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_regional_watchtower_integration import integration, REGIONS, plains, regional
from test_regional_armory_integration import decode


class WatchtowerIntegrationTests(unittest.TestCase):
    def test_provider_pools_and_duties_match_the_authored_decks(self):
        for region in ('plains', *REGIONS):
            for gatehouse in (False, True):
                with self.subTest(region=region, gatehouse=gatehouse):
                    blob, entry, row = integration(region, gatehouse)
                    provider = 'joshie' if region == 'ocean' else 'mmv' if region in ('jungle', 'swamp') else 'minecraft'
                    pool = 'roads' if region == 'ocean' else 'terminators'
                    self.assertEqual(f'{provider}:village/{region}/{pool}', entry['target_pool'])
                    duties = [[a + d for a, d in zip(row['anchor'], slot['offset'])]
                              for slot in entry['duty_slots']]
                    self.assertEqual(row['deckPositions'], duties)
                    tag = decode(blob)
                    states = {tuple(b['pos'][1][1]): tag['palette'][1][1][b['state'][1]]['Name'][1]
                              for b in tag['blocks'][1][1]}
                    for point in states:
                        self.assertTrue(all(0 <= coordinate < size for coordinate, size in zip(point, row['size'])))
                    for x, y, z in duties:
                        self.assertEqual('minecraft:air', states[x, y, z])
                        self.assertEqual('minecraft:air', states[x, y + 1, z])
                        self.assertTrue(states[x, y - 1, z].endswith('_planks'))

    def test_preserved_furniture_and_single_anchor_connector(self):
        for region in ('plains',*REGIONS):
            for gatehouse in (False,True):
                with self.subTest(region=region,gatehouse=gatehouse):
                    original,old=plains(gatehouse) if region=='plains' else regional(region,gatehouse)
                    original=decode(original);blob,entry,row=integration(region,gatehouse);tag=decode(blob)
                    self.assertEqual((blob,entry,row),integration(region,gatehouse))
                    palette=tag['palette'][1][1];blocks={tuple(b['pos'][1][1]):b for b in tag['blocks'][1][1]}
                    names=lambda b:palette[b['state'][1]]['Name'][1]
                    self.assertEqual([*old['size'][:2],old['size'][2]+1],tag['size'][1][1])
                    for b in original['blocks'][1][1]:
                        x,y,z=b['pos'][1][1];actual=blocks[x,y,z+1]
                        if [x,y,z]==old['anchor']:
                            self.assertEqual('village_anchor:watchtower',actual['nbt'][1]['metadata'][1])
                        else:
                            self.assertEqual(original['palette'][1][1][b['state'][1]],palette[actual['state'][1]])
                            self.assertEqual(b.get('nbt'),actual.get('nbt'))
                    self.assertEqual(1,sum(names(b)=='minecraft:structure_block' for b in blocks.values()))
                    connectors=[b for b in blocks.values() if names(b)=='minecraft:jigsaw'];self.assertEqual(1,len(connectors))
                    c=connectors[0];self.assertEqual(row['connector'],c['pos'][1][1]);self.assertEqual(0,row['connector'][2])
                    self.assertEqual(('mmv' if region in ('jungle','swamp') else 'minecraft')+':street',c['nbt'][1]['name'][1])
                    self.assertEqual('minecraft:empty',c['nbt'][1]['pool'][1])
                    self.assertEqual([dict(key=k,offset=[x,5,0],facing='north',kind='overwatch',weapon_requirement='ranged') for k,x in (('deck_center',1),('deck_west',-1))],entry['duty_slots'])
                    for key in ('anchor','entrance'):
                        x,y,z=old[key];self.assertEqual([x,y,z+1],row[key])
                    for key in ('walkTargets','standing','rails','deckPositions','initialDeckPositions','stairs','slits'):
                        self.assertEqual([[x,y,z+1] for x,y,z in old.get(key,[])],row.get(key,[]))
                    if 'shift' in old:
                        self.assertEqual([old['shift'][0],old['shift'][1],old['shift'][2]+1],row['shift'])
                    self.assertEqual(row['anchor'],row['service']);self.assertEqual(row['walkTargets'][-2],row['work'])
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
                    self.assertEqual('watchtower',entry['building_type'])


if __name__=='__main__':unittest.main()
