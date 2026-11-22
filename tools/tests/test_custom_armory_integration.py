import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_custom_armory_integration import PROFILES, integration
from build_regional_armory_integration import integration as regional
from test_regional_armory_integration import decode


class CustomArmoryIntegrationTests(unittest.TestCase):
    def test_adapters_preserve_furnished_buildings_and_functional_metadata(self):
        ids = set()
        for profile in PROFILES:
            for yard in (False, True):
                with self.subTest(provider=profile.provider, theme=profile.theme, yard=yard):
                    old_blob, old_entry, old = regional(profile.region, yard)
                    blob, entry, row = integration(profile, yard)
                    tag, original = decode(blob), decode(old_blob)
                    palette = tag['palette'][1][1]
                    blocks = {tuple(b['pos'][1][1]): b for b in tag['blocks'][1][1]}
                    self.assertEqual(len(blocks), len(tag['blocks'][1][1]))
                    self.assertEqual(row['size'], tag['size'][1][1])
                    self.assertNotIn(row['entry'], ids); ids.add(row['entry'])
                    delta = (1, 6, 0) if profile.provider == 'sky-villages' else (0, 0, 0)
                    for b in original['blocks'][1][1]:
                        pos = b['pos'][1][1]
                        state = original['palette'][1][1][b['state'][1]]
                        # Only the connector and the sky island's apron/skirt
                        # change; all furnished rooms retain their exact states.
                        if pos[2] == 0 or state['Name'][1] == 'minecraft:air': continue
                        actual = blocks[tuple(n+d for n,d in zip(pos,delta))]
                        self.assertEqual(state, palette[actual['state'][1]])
                        self.assertEqual(b.get('nbt'), actual.get('nbt'))
                    for key in ('anchor', 'sentry', 'service', 'entrance'):
                        self.assertEqual([n+d for n,d in zip(old[key], delta)], row[key])
                    for key in ('doors','targets','standing','openSky','lanterns'):
                        self.assertEqual([[n+d for n,d in zip(pos,delta)] for pos in old[key]],row[key])
                    self.assertEqual(old_entry['duty_slots'], entry['duty_slots'])
                    self.assertEqual(profile.pool, entry['target_pool'])
                    self.assertEqual('rigid', entry['projection'])
                    self.assertNotIn('terrain_safety', entry)
                    self.assertEqual(.5 if profile.provider == 'sky-villages' else .25, entry['selection_chance'])
                    self.assertEqual(5 if profile.provider == 'sky-villages' else 1, entry['weight'])
                    connectors = [b for b in blocks.values() if palette[b['state'][1]]['Name'][1] == 'minecraft:jigsaw']
                    self.assertEqual(1, len(connectors))
                    self.assertEqual(row['connector'], connectors[0]['pos'][1][1])
                    self.assertEqual(profile.connector, connectors[0]['nbt'][1]['name'][1])
                    self.assertEqual('minecraft:empty', connectors[0]['nbt'][1]['pool'][1])
                    for pos in blocks:
                        self.assertTrue(all(0 <= n < size for n,size in zip(pos,row['size'])))
        self.assertEqual(14, len(ids))

    def test_sky_has_floor_connector_solid_skirt_and_only_two_open_railing_lanes(self):
        profile = next(p for p in PROFILES if p.provider == 'sky-villages')
        for yard in (False, True):
            blob, _, row = integration(profile, yard); tag = decode(blob)
            palette = tag['palette'][1][1]
            names = {tuple(b['pos'][1][1]): palette[b['state'][1]]['Name'][1] for b in tag['blocks'][1][1]}
            x, y, _ = row['connector']; w, _, d = row['size']
            self.assertEqual(row['service'][1]-1, y)
            self.assertEqual('minecraft:oak_planks', row['finalState'])
            for px in range(w):
                for pz in range(d):
                    self.assertNotIn(names.get((px,y,pz)), (None, 'minecraft:air'))
                    if px in (0,w-1) or pz in (0,d-1):
                        expected = 'minecraft:air' if pz == 0 and px in (x,x+1) else 'minecraft:oak_fence'
                        self.assertEqual(expected, names.get((px,y+1,pz), 'minecraft:air'))


if __name__ == '__main__': unittest.main()
