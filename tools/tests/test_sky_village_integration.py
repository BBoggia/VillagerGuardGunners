import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_sky_village_integration import FAMILIES, integration, source
from test_regional_armory_integration import decode


class SkyVillageIntegrationTests(unittest.TestCase):
    def test_islands_preserve_rooms_duties_and_have_guarded_edges(self):
        for family in FAMILIES:
            for large in (False, True):
                with self.subTest(family=family, large=large):
                    original_blob, original_entry, original_row = source(family, large)
                    blob, entry, row = integration(family, large)
                    original, tag = decode(original_blob), decode(blob)
                    palette = tag['palette'][1][1]
                    blocks = {tuple(b['pos'][1][1]): b for b in tag['blocks'][1][1]}
                    self.assertEqual(len(blocks), len(tag['blocks'][1][1]))
                    self.assertEqual(row['size'], tag['size'][1][1])
                    for pos in blocks:
                        self.assertTrue(all(0 <= n < size for n,size in zip(pos,row['size'])))
                    for block in original['blocks'][1][1]:
                        pos = block['pos'][1][1]
                        state = original['palette'][1][1][block['state'][1]]
                        if state['Name'][1] in ('minecraft:air','minecraft:jigsaw'): continue
                        if pos == [original_row['connector'][0],0,0]: continue
                        actual = blocks[pos[0]+2,pos[1]+6,pos[2]]
                        self.assertEqual(state, palette[actual['state'][1]])
                        self.assertEqual(block.get('nbt'), actual.get('nbt'))
                    self.assertEqual(original_entry['duty_slots'], entry['duty_slots'])
                    self.assertEqual([original_row['anchor'][0]+2,original_row['anchor'][1]+6,original_row['anchor'][2]],row['anchor'])
                    self.assertEqual('skyvillages:skyvillage_houses',entry['target_pool'])
                    self.assertEqual('rigid',entry['projection'])
                    self.assertTrue(original_entry['terrain_safety'])
                    self.assertNotIn('terrain_safety', entry)
                    self.assertEqual('minecraft:empty',entry['processors'])
                    names = {p: palette[b['state'][1]]['Name'][1] for p,b in blocks.items()}
                    connectors = [p for p,name in names.items() if name == 'minecraft:jigsaw']
                    self.assertEqual([tuple(row['connector'])], connectors)
                    x,y,z = row['connector']; w,_,d = row['size']
                    self.assertEqual((6,0), (y,z))
                    self.assertEqual('minecraft:oak_planks',blocks[x,y,z]['nbt'][1]['final_state'][1])
                    for px in range(w):
                        for pz in range(d):
                            self.assertNotIn(names.get((px,6,pz)), (None,'minecraft:air'))
                            if px in (0,w-1) or pz in (0,d-1):
                                expected = 'minecraft:air' if pz == 0 and px in (x,x+1) else 'minecraft:oak_fence'
                                self.assertEqual(expected,names.get((px,7,pz),'minecraft:air'))
                    for slot in entry['duty_slots']:
                        point = tuple(a+b for a,b in zip(row['anchor'],slot['offset']))
                        self.assertIn(names.get(point,'minecraft:air'), ('minecraft:air','minecraft:structure_block'))


if __name__ == '__main__': unittest.main()
