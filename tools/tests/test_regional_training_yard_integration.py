import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_regional_training_yard_integration import integration, REGIONS, plains, regional
from test_regional_armory_integration import decode


class TrainingYardIntegrationTests(unittest.TestCase):
    def test_preview_preservation_and_single_runtime_marker(self):
        for region in ('plains', *REGIONS):
            for courtyard in (False, True):
                with self.subTest(region=region, courtyard=courtyard):
                    original, old = plains(courtyard) if region == 'plains' else regional(region, courtyard)
                    original = decode(original)
                    blob, entry, row = integration(region, courtyard)
                    self.assertEqual((blob, entry, row), integration(region, courtyard))
                    tag = decode(blob)
                    palette = tag['palette'][1][1]
                    blocks = {tuple(b['pos'][1][1]): b for b in tag['blocks'][1][1]}
                    name = lambda b: palette[b['state'][1]]['Name'][1]
                    self.assertEqual([*old['size'][:2], old['size'][2]+1], tag['size'][1][1])
                    self.assertEqual([], tag['entities'][1][1])
                    for b in original['blocks'][1][1]:
                        x, y, z = b['pos'][1][1]
                        actual = blocks[x, y, z+1]
                        if [x, y, z] == old['anchor']:
                            self.assertEqual('village_anchor:training_yard', actual['nbt'][1]['metadata'][1])
                        else:
                            self.assertEqual(original['palette'][1][1][b['state'][1]], palette[actual['state'][1]])
                            self.assertEqual(b.get('nbt'), actual.get('nbt'))
                    self.assertEqual(1, sum(name(b) == 'minecraft:structure_block' for b in blocks.values()))
                    self.assertEqual(2, sum(name(b) == 'minecraft:target' for b in blocks.values()))
                    self.assertEqual(1, sum(name(b) == 'minecraft:crafting_table' for b in blocks.values()))
                    for key in ('anchor', 'entrance'):
                        x, y, z = old[key]
                        self.assertEqual([x, y, z+1], row[key])
                    for key in ('initialPositions', 'dutyPositions', 'targetBlocks', 'standing', 'circulation',
                                'field', 'walkTargets', 'traffic', 'lightSamples', 'rails', 'roof', 'doors'):
                        self.assertEqual([[x,y,z+1] for x,y,z in old.get(key, [])], row.get(key, []), key)
                    self.assertEqual(row['anchor'], row['initialPositions'][0])
                    self.assertEqual([row['anchor'][0]-1, *row['anchor'][1:]], row['initialPositions'][1])
                    expected = [dict(key=k, offset=[dx,0,0], facing='south', kind='drill', weapon_requirement='ranged')
                                for k,dx in [('firing_center',1), ('firing_west',-2)]]
                    self.assertEqual(expected, entry['duty_slots'])
                    self.assertEqual(sorted(row['dutyPositions']), sorted([[row['anchor'][0]+d, *row['anchor'][1:]] for d in (1,-2)]))

    def test_road_aprons_and_provider_contracts(self):
        pools = set()
        for region in ('plains', *REGIONS):
            for courtyard in (False, True):
                blob, entry, row = integration(region, courtyard)
                tag = decode(blob); palette = tag['palette'][1][1]
                blocks = {tuple(b['pos'][1][1]): b for b in tag['blocks'][1][1]}
                name = lambda b: palette[b['state'][1]]['Name'][1]
                connectors = [b for b in blocks.values() if name(b) == 'minecraft:jigsaw']
                self.assertEqual(1, len(connectors))
                c = connectors[0]; x, f, _ = row['entrance']
                self.assertEqual(row['connector'], c['pos'][1][1])
                self.assertEqual([x, 1, 0], row['connector'])
                self.assertEqual('north_up', palette[c['state'][1]]['Properties'][1]['orientation'][1])
                provider = 'joshie' if region == 'ocean' else 'mmv' if region in ('jungle','swamp') else 'minecraft'
                self.assertEqual(('mmv' if provider == 'mmv' else 'minecraft')+':street', c['nbt'][1]['name'][1])
                self.assertEqual('minecraft:empty', c['nbt'][1]['pool'][1])
                self.assertEqual(f'{provider}:village/{region}/'+('roads' if region == 'ocean' else 'terminators'), entry['target_pool'])
                pools.add(entry['target_pool'])
                for dx in (0,1):
                    for y in range(f, f+3):
                        if [x+dx,y,0] != row['connector']:
                            self.assertEqual('minecraft:air', name(blocks[x+dx,y,0]))
                if f == 2:
                    self.assertIn('_slab[type=bottom,waterlogged=false]', row['finalState'])
                    self.assertEqual('bottom', palette[blocks[x+1,f-1,0]['state'][1]]['Properties'][1]['type'][1])
                else:
                    self.assertEqual('minecraft:air', row['finalState'])
                    self.assertTrue(name(blocks[x,0,0]).endswith('_planks'))
                self.assertEqual((1,(.5 if region in ('plains','desert','savanna','taiga','snowy') else .25),'rigid','training_yard'), (entry['weight'],entry['selection_chance'],entry['projection'],entry['building_type']))
        self.assertEqual(8, len(pools))


if __name__ == '__main__':
    unittest.main()
