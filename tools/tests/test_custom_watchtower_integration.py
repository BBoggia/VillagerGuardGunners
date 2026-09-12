import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_custom_watchtower_integration import CTOV_PROFILES, integration
from build_regional_watchtower_integration import integration as regional
from build_custom_armory_integration import PROFILES
from test_regional_armory_integration import decode


class CustomWatchtowerIntegrationTests(unittest.TestCase):
    def test_ctov_preserves_geometry_garrison_and_duties(self):
        ids = set()
        for profile in CTOV_PROFILES:
            for layout in (False, True):
                with self.subTest(theme=profile.theme, layout=layout):
                    original_blob, old_entry, old = regional(profile.region, layout)
                    blob, entry, row = integration(profile, layout)
                    tag, original = decode(blob), decode(original_blob)
                    # The only NBT changes are the incoming connector identifiers.
                    for block in original['blocks'][1][1]:
                        if original['palette'][1][1][block['state'][1]]['Name'][1] == 'minecraft:jigsaw':
                            for key in ('name', 'target'):
                                block['nbt'][1][key] = (8, 'minecraft:street')
                    self.assertEqual(original, tag)
                    self.assertEqual(old_entry['duty_slots'], entry['duty_slots'])
                    for key in ('targets', 'entrance', 'anchor', 'size', 'connector', 'finalState', 'service', 'work', 'standing', 'walkTargets', 'stairs', 'deckPositions', 'initialDeckPositions', 'rails', 'slits'):
                        self.assertEqual(old[key], row[key])
                    self.assertEqual(profile.pool, entry['target_pool'])
                    self.assertEqual('rigid', entry['projection'])
                    self.assertNotIn('terrain_safety', entry)
                    self.assertEqual('minecraft:empty', entry['processors'])
                    self.assertEqual(1, entry['weight'])
                    self.assertEqual(.25, entry['selection_chance'])
                    self.assertEqual(row['entry'], entry['template'])
                    self.assertNotIn(row['entry'], ids); ids.add(row['entry'])
        self.assertEqual(12, len(ids))

    def test_only_reviewed_themes_and_reachable_pool_contracts(self):
        expected = {'plains', 'desert', 'taiga', 'snowy_igloo', 'swamp', 'jungle'}
        self.assertEqual(expected, {p.theme for p in CTOV_PROFILES})
        for profile in CTOV_PROFILES:
            suffix = 'roads' if profile.theme == 'jungle' else 'terminator'
            self.assertEqual(f'ctov:village/{profile.theme}/{suffix}', profile.pool)
            self.assertEqual('minecraft:street', profile.connector)
        with self.assertRaises(ValueError):
            integration(CTOV_PROFILES[0], 'gatehouse_lookout')
        with self.assertRaises(ValueError):
            integration(next(p for p in PROFILES if p.provider != 'ctov'), False)


if __name__ == '__main__': unittest.main()
